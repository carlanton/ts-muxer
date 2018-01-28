import org.mp4parser.IsoFile;
import org.mp4parser.boxes.iso14496.part12.MediaDataBox;
import org.mp4parser.boxes.iso14496.part12.TrackFragmentBaseMediaDecodeTimeBox;
import org.mp4parser.boxes.iso14496.part12.TrackFragmentBox;
import org.mp4parser.boxes.iso14496.part12.TrackRunBox;
import org.mp4parser.boxes.iso14496.part15.AvcConfigurationBox;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TsMuxer {
    private static final byte SYNC_BYTE = 0x47;
    private static final int TS_PACKET_SIZE = 188;
    private static final int SECTION_LENGTH = 1020;

    private int cc;

    public TsMuxer() {
        this.cc = 15;
    }

    public void writeSdt() {
        ByteBuffer data = ByteBuffer.allocate(SECTION_LENGTH);
        data.putShort((short) 0xff01);
        data.put((byte) 0xFF);
        for (int i = 0; i < 1; i++) {
            data.putShort((short) 0x1234);  // sid
            data.put((byte) 0xfc); /* currently no EIT info */

        }
    }


    public void writePacket(AVPacket pkt, FileChannel channel) throws IOException {
        //AVStream st = streams.get(pkt.streamIndex());
        int size = pkt.data().limit();
        ByteBuffer buf = pkt.data();
        final long delay = 0; // av_rescale(s->max_delay, 90000, AV_TIME_BASE) * 2;
        long dts = pkt.dts();
        long pts = pkt.pts();

        int streamId = pkt.mpegTsStreamId();

        /*
        // if (st->codecpar->codec_id == AV_CODEC_ID_H264)
        long state = -1;
        // int extradd = (pkt->flags & AV_PKT_FLAG_KEY) ? st->codecpar->extradata_size : 0;
        int extradd = 0;
        */

        ByteBuffer data = ByteBuffer.allocate(size + 6);
        data.put((byte) 0);
        data.put((byte) 0);
        data.put((byte) 0);
        data.put((byte) 1);
        data.put((byte) 0x09);
        data.put((byte) ((0xf0) & 0xff));

        data.position(6);
        data.put(pkt.data());
        data.flip();

        writePes(data, 256, pkt.isKeyFrame(), pkt.pts(), pkt.dts(), channel);
    }

    public void writePes(ByteBuffer payload, int pid, boolean key, long pts,
                                long dts, FileChannel channel) throws IOException {

        int val;
        boolean isStart = true;
        int headerLength, flags;
        int payloadSize = payload.limit();
        int len;
        int stuffingLength;

        while (payload.remaining() > 0) {
            ByteBuffer buf = ByteBuffer.allocate(188);

            boolean writePcr = false;

            buf.put(SYNC_BYTE);
            val = pid >> 8;
            if (isStart)
                val |= 0x40;

            buf.put((byte) (val & 0xFF));
            buf.put((byte) (pid & 0xFF));
            cc = (cc + 1) & 0xF;
            buf.put((byte) ((0x10 | cc) & 0XFF)); // payload indicator + CC

            if (key && isStart) {
                // set Random Access for key frames
                setAfFlag(buf, 0x40);
                writePcr = true; // if (ts_st->pid == ts_st->service->pcr_pid)
                moveToPayloadStart(buf);
            }

            if (writePcr) {
                setAfFlag(buf, 0x10);
                long pcr = dts * 300;
                int t = writePcrBits(buf, pcr);
                extendAf(buf, t);
                moveToPayloadStart(buf);
                // ??
            }

            if (isStart) {
                // write PES header
                buf.put((byte) 0);
                buf.put((byte) 0);
                buf.put((byte) 1);

                // todo: aac

                buf.put((byte) (0xe0 & 0xFF));
                headerLength = 0;
                flags = 0;

                //  if (pts != AV_NOPTS_VALUE)
                headerLength += 5;
                flags |= 0x80;
                // if (dts != AV_NOPTS_VALUE && pts != AV_NOPTS_VALUE && dts != pts)
                // todo: dts

                len = payloadSize + headerLength + 3;

                if (len > 0xffff) {
                    len = 0;
                }

                if (true) { // ts->omit_video_pes_length && st->codecpar->codec_type == AVMEDIA_TYPE_VIDEO
                    len = 0;
                }

                buf.put((byte) ((len >> 8) & 0xFF));
                buf.put((byte) (len & 0xFF));
                val = 0x80;
                buf.put((byte) (val & 0xFF));
                buf.put((byte) (flags & 0xFF));
                buf.put((byte) (headerLength & 0xFF));

                writePts(buf, flags >> 6, pts);

                // todo: dts

                isStart = false;
            }

            /* header size */
            headerLength = buf.position();

            /* data len */
            len = TS_PACKET_SIZE - headerLength;
            if (len > payloadSize)
                len = payloadSize;
            stuffingLength = TS_PACKET_SIZE - headerLength - len;

            if (stuffingLength > 0) {
                /* add stuffing with AFC */
                if ((buf.get(3) & 0x20) != 0) {
                    /* stuffing already present: increase its size */
                    throw new UnsupportedOperationException("stuffing mode not implemented");
                } else {
                    /* add stuffing */
                    // memmove(buf + 4 + stuffing_len, buf + 4, header_len - 4);
                    for (int i = 0; i < headerLength - 4; i++) {
                        buf.put(4 + stuffingLength + i, buf.get(4 + i));
                    }

                    buf.put(3, (byte) ((buf.get(3) | 0x20) & 0xff));
                    buf.put(4, (byte) ((stuffingLength - 1) & 0xFF));

                    if (stuffingLength >= 2) {
                        buf.put(5, (byte) 0);
                        // memset(buf + 6, 0xff, stuffing_len - 2);
                        for (int i = 0; i < stuffingLength - 2; i++) {
                            buf.put(6+i, (byte) 0xFF);
                        }
                    }
                }
            }

            payload.get(buf.array(), TS_PACKET_SIZE - len, len);
            buf.limit(buf.capacity()).rewind();

            while (buf.remaining() > 0) {
                channel.write(buf);
            }

            buf.rewind();

            payloadSize -= len;
        }
    }


    private static void extendAf(ByteBuffer buf, int n) {
        byte size = buf.get(4);
        size += n;
        size &= 0xFF;
        buf.put(4, size);
    }

    private static int writePcrBits(ByteBuffer buf, long pcr) {
        long pcr_low = pcr % 300, pcr_high = pcr / 300;
        buf.put((byte) ((pcr_high >> 25) & 0xFF));
        buf.put((byte) ((pcr_high >> 17) & 0xFF));
        buf.put((byte) ((pcr_high >>  9) & 0xFF));
        buf.put((byte) ((pcr_high >>  1) & 0xFF));
        buf.put((byte) ((pcr_high <<  7 | pcr_low >> 8 | 0x7e) & 0xFF));
        buf.put((byte) ((pcr_low) & 0xFF));
        return 6;
    }

    private static void writePts(ByteBuffer buf, int fourbits, long pts) {
        int val;
        val = (int) (fourbits << 4 | (((pts >> 30) & 0x07) << 1) | 1);
        buf.put((byte) (val & 0xFF));
        val  = (int) ((((pts >> 15) & 0x7fff) << 1) | 1);
        buf.put((byte) ((val >> 8) & 0xFF));
        buf.put((byte) (val & 0xFF));
        val  = (int) ((((pts) & 0x7fff) << 1) | 1);
        buf.put((byte) ((val >> 8) & 0xFF));
        buf.put((byte) (val & 0xFF));
    }

    private static void setAfFlag(ByteBuffer buf, int flag) {
        byte b = buf.get(3);

        if ((b & 0x20) == 0) {
            // no AF yet, set adaptation field flag
            buf.put(3, (byte) ((b | 0x20) & 0xFF));
            // 1 byte length, no flags
            buf.put(4, (byte) 1);
            buf.put(5, (byte) 0);
        }
        b = buf.get(5);
        buf.put(5, (byte) ((b | flag) & 0xFF));
    }

    private static void moveToPayloadStart(ByteBuffer pkt) {
        if ((pkt.get(3) & 0x20) != 0) {
            pkt.position(5 + pkt.get(4));
        } else {
            pkt.position(4);
        }
    }

    private static void printPacket(ByteBuffer tsPacket) {
        System.out.println(tsPacket);
        String x = DatatypeConverter.printHexBinary(tsPacket.array());
        List<String> asList = Arrays.asList(x.split("(?<=\\G.{2})"));
        for (int i = 0; i < asList.size(); i++) {
            String b = asList.get(i);
            System.out.print(b + " ");
            if (i % 4 == 3)
                System.out.println();
        }
        System.out.println();
    }



    public static List<AVPacket> mdatPayload() throws IOException {
        IsoFile isoFile = new IsoFile("s.mp4");

        List<AVPacket> frames = new ArrayList<>();
        TrackFragmentBox traf = isoFile.getBoxes(TrackFragmentBox.class, true).get(0);
        long sampleDuration = traf.getTrackFragmentHeaderBox().getDefaultSampleDuration();

        TrackRunBox trun = traf.getBoxes(TrackRunBox.class, true).get(0);

        MediaDataBox mdat = isoFile.getBoxes(MediaDataBox.class, true).get(0);
        isoFile.close();
        ByteBuffer data = mdat.getData();
        data.position(0);


        AvcConfigurationBox avcC = isoFile.getBoxes(AvcConfigurationBox.class, true).get(0);
        ByteBuffer sps = avcC.getSequenceParameterSets().get(0);
        ByteBuffer pps = avcC.getPictureParameterSets().get(0);
        H264Mp4ToAnnexBFilter bsf = new H264Mp4ToAnnexBFilter(sps, pps);

        long pts = traf.getBoxes(TrackFragmentBaseMediaDecodeTimeBox.class).get(0).getBaseMediaDecodeTime();
        long dts = pts;

        boolean keyFrame = true;

        for (TrackRunBox.Entry entry : trun.getEntries()) {
            int size = (int) entry.getSampleSize();
            /*
            ByteBuffer frame = data.slice();
            frame.limit(size);
            moveToPayloadStart(frame);
            data.position(data.position() + size);
            */

            byte[] xs = new byte[size];
            data.get(xs);
            ByteBuffer frame = ByteBuffer.wrap(xs);
            frame = bsf.filter(frame);
            frames.add(new AVPacket(frame, pts, dts, 0, 256, keyFrame));

            pts += sampleDuration;
            dts = pts;
            keyFrame = false;
        }

        return frames;
    }

    public static void main(String[] args) throws IOException {
        List<AVPacket> frames = mdatPayload();

        TsMuxer muxer = new TsMuxer();
        try (FileChannel channel = FileChannel.open(Paths.get("ts"), StandardOpenOption.WRITE,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (AVPacket packet : frames) {
                muxer.writePacket(packet, channel);
            }
        }
    }
}
