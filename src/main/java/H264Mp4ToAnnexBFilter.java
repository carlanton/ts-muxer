import java.nio.ByteBuffer;

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
        int unit_type;
        int nal_size;
        int i;
        ByteBuffer out = ByteBuffer.allocate(buf.capacity() + sps.limit() + pps.limit() + 8);

        do {
            for (nal_size = 0, i = 0; i < length_size; i++) {
                nal_size = (nal_size << 8) | ( buf.get(buf.position() + i) & 0xFF);
            }

            buf.position(buf.position() + length_size);
            unit_type = buf.get(buf.position()) & 0x1f;
            System.out.println("unit_type = " + unit_type);

            if (unit_type == 7)
                throw new RuntimeException("unit_type = 7");
            else if (unit_type == 8) {
                throw new RuntimeException("unit_type = 8");
            }

            if (!new_idr && unit_type == 5) {
                if ((buf.get(buf.position()+1) & 0x80) != 0)
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

        } while (buf.remaining() > 0);

        out.flip();

        return out;
    }
}
