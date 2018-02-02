import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class NalUnitToByteStreamConverter {
    private static final byte H264_AUD = 9;
    private static final byte H264_SPS = 7;
    private static final byte H264_PPS = 8;

    private int naluLengthSize = 4; // hmm

    private final byte[] naluStartCode = new byte[]{0x00, 0x00, 0x00, 0x01};
    private final byte AccessUnitDelimiterRbspAnyPrimaryPicType = (byte) ((0xF0) & 0xFF);
    private final boolean escapeData = true;

    private ByteArrayOutputStream stream;


    private void appendNalu(ByteBuffer nalu, boolean escapeData, OutputStream outputStream) throws IOException {
        if (escapeData) {
            throw new UnsupportedOperationException();
        } else {
            while (nalu.remaining() > 0) {
                outputStream.write(nalu.get());
            }
        }
    }

    void initialize(ByteBuffer sps, ByteBuffer pps) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        byteArrayOutputStream.write(naluStartCode);
        appendNalu(sps.duplicate(), !escapeData, byteArrayOutputStream);
        byteArrayOutputStream.write(naluStartCode);
        appendNalu(pps.duplicate(), !escapeData, byteArrayOutputStream);

        stream = byteArrayOutputStream;
    }

    void AddAccessUnitDelimiter(OutputStream outputStream) throws IOException {
        outputStream.write(H264_AUD);
        outputStream.write(AccessUnitDelimiterRbspAnyPrimaryPicType);
    }

    void convert(ByteBuffer sample, boolean isKeyFrame, OutputStream out) throws IOException {
        out.write(naluStartCode);
        AddAccessUnitDelimiter(out);
        if (isKeyFrame) {
            out.write(stream.toByteArray());
        }

        while (sample.remaining() > 0) {
            int unit_type;
            int nal_size = 0;

            for (int i = 0; i < naluLengthSize; i++) {
                nal_size = (nal_size << 8) | ( sample.get(sample.position() + i) & 0xFF);
            }

            sample.position(sample.position() + naluLengthSize);
            unit_type = sample.get(sample.position()) & 0x1f;

            switch (unit_type) {
                case H264_PPS:
                case H264_SPS:
                case H264_AUD:
                    break;

                default:
                    out.write(naluStartCode);
                    for (int i = 0; i < nal_size; i++) {
                        out.write(sample.get());
                    }
            }
        }

    }

}
