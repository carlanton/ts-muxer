import java.io.IOException;
import java.nio.ByteBuffer;

@SuppressWarnings("ALL")
public class NalUnitToByteStreamConverter {
    private static final byte H264_SPS = 7;
    private static final byte H264_PPS = 8;
    private static final byte H264_AUD = 9;

    private final byte[] spsAndPps;

    private final int naluLengthSize = 4; // hmm

    private final byte[] naluStartCode = new byte[]{0x00, 0x00, 0x00, 0x01};
    private final boolean escapeData = true;


    public NalUnitToByteStreamConverter(ByteBuffer sps, ByteBuffer pps) {
        ByteBuffer spsPps = ByteBuffer.allocate(sps.limit() + pps.limit() + 2 * naluStartCode.length);
        spsPps.put(naluStartCode);
        spsPps.put(sps.duplicate());
        spsPps.put(naluStartCode);
        spsPps.put(pps.duplicate());
        spsPps.flip();
        this.spsAndPps = spsPps.array();
    }

    ByteBuffer convert(ByteBuffer sample, boolean isKeyFrame) throws IOException {
        int size = sample.limit() - sample.position() + 6 + (isKeyFrame ? spsAndPps.length : 0);

        ByteBuffer out = ByteBuffer.allocate(size);
        out.put(naluStartCode);
        out.put(H264_AUD);
        out.put((byte) 0x10);

        if (isKeyFrame) {
            out.put(spsAndPps);
        }

        while (sample.remaining() > 0) {
            int unit_type;
            int nalSize = 0;

            for (int i = 0; i < naluLengthSize; i++) {
                nalSize = (nalSize << 8) | (sample.get() & 0xFF);
            }

            unit_type = sample.get(sample.position()) & 0x1F;

            if (unit_type == H264_AUD || unit_type == H264_SPS || unit_type == H264_PPS) {
                throw new IOException("Found unexpected nal of type " + unit_type);
            }

            out.put(naluStartCode);
            sample.get(out.array(), out.position(), nalSize);
            out.position(out.position() + nalSize);
        }

        out.flip();
        return out;
    }
}
