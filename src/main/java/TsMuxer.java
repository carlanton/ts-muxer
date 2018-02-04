import org.mp4parser.Container;
import org.mp4parser.boxes.iso14496.part15.AvcConfigurationBox;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


public class TsMuxer {
    private static final byte SYNC_BYTE = 0x47;
    private static final int TS_PACKET_SIZE = 188;

    private static final Comparator<Sample> SAMPLE_COMPARATOR = Comparator.comparingLong(Sample::dts)
            .thenComparingInt(Sample::pid);

    private final ByteBuffer patAndPmt;

    private int videoCC;
    private int audioCC;

    public TsMuxer(ByteBuffer patAndPmt) {
        this.patAndPmt = patAndPmt;
        this.videoCC = 15;
        this.audioCC = 15;
    }

    public int writePacket(Sample pkt, WritableByteChannel channel) throws IOException {
        return writePes(pkt.data(), pkt.pid(), pkt.isKeyFrame(), pkt.pts(), pkt.dts(), pkt.getType(), channel);
    }

    private int writePes(ByteBuffer payload, int pid, boolean key, long pts,
                          long dts, Sample.Type type, WritableByteChannel channel) throws IOException {

        int val;
        boolean isStart = true;
        int headerLength, flags;
        int payloadSize = payload.limit();
        int len;
        int stuffingLength;

        int pkts = 0;

        while (payload.remaining() > 0) {
            ByteBuffer buf = ByteBuffer.allocate(188);

            boolean writePcr = false;

            buf.put(SYNC_BYTE);
            val = pid >> 8;
            if (isStart)
                val |= 0x40;

            buf.put((byte) (val & 0xFF));
            buf.put((byte) (pid & 0xFF));

            if (type == Sample.Type.H264) {
                videoCC = (videoCC + 1) & 0xF;
                buf.put((byte) ((0x10 | videoCC) & 0XFF)); // payload indicator + CC
            } else {
                audioCC = (audioCC + 1) & 0xF;
                buf.put((byte) ((0x10 | audioCC) & 0XFF)); // payload indicator + CC

            }
            if (key && isStart) {
                // set Random Access for key frames
                setAfFlag(buf, 0x40);

                if (type == Sample.Type.H264)
                    writePcr = true;
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

                if (type == Sample.Type.H264) {
                    buf.put((byte) 0xe0);
                } else if (type == Sample.Type.AAC_LC) {
                    buf.put((byte) 0xc0);
                }

                headerLength = 0;
                flags = 0;

                //  if (pts != AV_NOPTS_VALUE)
                headerLength += 5;
                flags |= 0x80; // only set pts
                // if (dts != AV_NOPTS_VALUE && pts != AV_NOPTS_VALUE && dts != pts)
                // todo: dts

                len = payloadSize + headerLength + 3;

                if (len > 0xffff) {
                    len = 0;
                }

                if (type == Sample.Type.H264) {
                    len = 0;
                }

                //System.out.println("len = " + len);

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

            pkts++;
        }

        return pkts;
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

    static NalUnitToByteStreamConverter createConverter(Container container) throws IOException {
        AvcConfigurationBox avcC = container.getBoxes(AvcConfigurationBox.class, true).get(0);
        ByteBuffer sps = avcC.getSequenceParameterSets().get(0);
        ByteBuffer pps = avcC.getPictureParameterSets().get(0);
        return new NalUnitToByteStreamConverter(sps, pps);
    }

    // lol
    synchronized ByteBuffer[] read(Path videoSegment, Path audioSegment, NalUnitToByteStreamConverter converter) throws IOException {
        List<Sample> avcSamples = MP4Utils.readVideo(videoSegment, converter);
        List<Sample> aacSamples = MP4Utils.readAudio(audioSegment);
        List<Sample> samples = TsMuxer.interleave(aacSamples, avcSamples);

        videoCC = 15;
        audioCC = 15;

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        writeSamples(samples, Channels.newChannel(byteArrayOutputStream));
        return new ByteBuffer[]{ patAndPmt.duplicate(), ByteBuffer.wrap(byteArrayOutputStream.toByteArray()) };
    }

    private void writeSamples(List<Sample> samples, WritableByteChannel channel) throws IOException {
        int pkts = 0;
        for (Sample sample : samples) {
            pkts += writePacket(sample, channel);
        }
    }

    private static List<Sample> interleave(List<Sample> audioSamples, List<Sample> videoSamples) {
        List<Sample> samples = new ArrayList<>(audioSamples.size() + videoSamples.size());
        samples.addAll(audioSamples);
        samples.addAll(videoSamples);
        samples.sort(SAMPLE_COMPARATOR);
        return samples;
    }

    public static void main(String[] args) throws IOException {
        //List<Sample> frames = parseMp4(Paths.get("v6-with-init.mp4"));

        ByteBuffer patAndPmt = ByteBuffer.wrap(Files.readAllBytes(Paths.get("media/v6.ts")));
        patAndPmt.limit(188*2);

        Path basePath = Paths.get("media/bbb/");
        Container init = MP4Utils.readMp4(basePath.resolve("v-init.mp4"));
        NalUnitToByteStreamConverter converter = TsMuxer.createConverter(init);

        TsMuxer muxer = new TsMuxer(patAndPmt);

        try (FileChannel channel = FileChannel.open(Paths.get("java.ts"), StandardOpenOption.WRITE,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

            List<Sample> aacSamples = MP4Utils.readAudio(basePath.resolve("a-1.mp4"));
            List<Sample> avcSamples = MP4Utils.readVideo(basePath.resolve("v-1.mp4"), converter);
            List<Sample> samples = TsMuxer.interleave(aacSamples, avcSamples);

            while (patAndPmt.remaining() > 0)
                channel.write(patAndPmt);

            muxer.writeSamples(samples, channel);
        }
    }
}
