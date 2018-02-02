import org.mp4parser.Container;
import org.mp4parser.boxes.iso14496.part12.MediaDataBox;
import org.mp4parser.boxes.iso14496.part12.TrackFragmentBox;
import org.mp4parser.boxes.iso14496.part12.TrackRunBox;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;

import static java.nio.file.StandardOpenOption.*;

/*
 * Based on https://github.com/FFmpeg/FFmpeg/blob/master/libavcodec/h264_mp4toannexb_bsf.c
 *
 * FFmpeg is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * FFmpeg is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with FFmpeg; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */
public class H264Mp4ToAnnexBFilter {
    private final ByteBuffer sps;
    private final ByteBuffer pps;
    private final int length_size;

    private boolean new_idr;
    private boolean idr_sps_seen;
    private boolean idr_pps_seen;

    public H264Mp4ToAnnexBFilter(ByteBuffer sps, ByteBuffer pps) {
        this.sps = sps;
        this.pps = pps;

        length_size = 4; // ???
        new_idr = true;
        idr_sps_seen = false;
        idr_pps_seen = false;

        //hexdump(pps.duplicate());
    }

    private static void hexdump(ByteBuffer byteBuffer) {
        while (byteBuffer.remaining() > 0) {
            System.out.printf("%02x\n", byteBuffer.get());
        }
    }

    private void copy(ByteBuffer out, boolean write_sps_pps, ByteBuffer in, int in_size) {
        int offset = out.position();

        if (write_sps_pps) {
            out.putInt(1);
            out.put(sps.duplicate());
            out.putInt(1);
            out.put(pps.duplicate());
        }

        if (offset == 0) {
            out.putInt(1);
        } else {
            out.put((byte) 0);
            out.put((byte) 0);
            out.put((byte) 1);
        }

        in.get(out.array(), out.position(), in_size);
        out.position(out.position() + in_size);
    }


    public ByteBuffer filter(ByteBuffer buf) {
        int size = buf.limit() - buf.position() + sps.limit() + pps.limit() + 8;
        ByteBuffer out = ByteBuffer.allocate(size);

        while (buf.remaining() > 0) {
            int unit_type;
            int nal_size = 0;

            for (int i = 0; i < length_size; i++) {
                nal_size = (nal_size << 8) | ( buf.get(buf.position() + i) & 0xFF);
            }

            buf.position(buf.position() + length_size);
            unit_type = buf.get(buf.position()) & 0x1f;

            System.out.println("unit_type = " + unit_type);

            if (unit_type == 7) {                                // Sequence parameter set
                throw new RuntimeException("unit_type = 7");
            } else if (unit_type == 8) {                         // Picture parameter set
                throw new RuntimeException("unit_type = 8");
            }

            if (!new_idr && unit_type == 5 && (buf.get(buf.position()+1) & 0x80) != 0) {
                    new_idr = true;
            }

            if (new_idr && unit_type == 5 && !idr_sps_seen && !idr_pps_seen) {
                copy(out, true, buf, nal_size);
                new_idr = false;
            } else if (new_idr && unit_type == 5 && idr_sps_seen && !idr_pps_seen) {
                throw new UnsupportedOperationException();
            } else {
                copy(out, false, buf, nal_size);
                if (!new_idr && unit_type == 1) {
                    new_idr  = true;
                    idr_sps_seen = false;
                    idr_pps_seen = false;
                }
            }
        }

        out.flip();

        return out;
    }

    public static void main(String[] args) throws IOException {
        Container container = TsMuxer.readMp4(Paths.get("live/v6.mp4"));
        H264Mp4ToAnnexBFilter bsf = TsMuxer.createFilter(container);

        ByteBuffer data = container.getBoxes(MediaDataBox.class, true).get(0).getData();

        TrackFragmentBox traf = container.getBoxes(TrackFragmentBox.class, true).get(0);
        TrackRunBox trun = traf.getBoxes(TrackRunBox.class, true).get(0);


        NalUnitToByteStreamConverter nalUnitToByteStreamConverter = new NalUnitToByteStreamConverter();
        nalUnitToByteStreamConverter.initialize(bsf.sps.duplicate(), bsf.pps.duplicate());

        boolean keyFrame = true;
        try (FileChannel channel = FileChannel.open(Paths.get("live/java.h264"), CREATE, WRITE, TRUNCATE_EXISTING)) {
            data.position(0);
            for (TrackRunBox.Entry entry : trun.getEntries()) {

                int size = (int) entry.getSampleSize();
                data.limit(data.position() + size);

                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                nalUnitToByteStreamConverter.convert(data, keyFrame, byteArrayOutputStream);

                ByteBuffer frame = ByteBuffer.wrap(byteArrayOutputStream.toByteArray());
                while (frame.remaining() > 0)
                    channel.write(frame);

                /*ByteBuffer frame = bsf.filter(data.asReadOnlyBuffer());
                while (frame.remaining() > 0)
                    channel.write(frame);
                */
                data.position(data.limit());
                keyFrame = false;
            }
        }


    }
}
