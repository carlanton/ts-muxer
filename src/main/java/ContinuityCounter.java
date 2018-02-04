import java.nio.ByteBuffer;

public class ContinuityCounter {
    private int videoCC = 15;
    private int audioCC = 15;

    private int incrementAndGetVideo() {
        videoCC = (videoCC + 1) & 0xF;
        return videoCC;
    }

    private int incrementAndGetAudio() {
        audioCC = (audioCC + 1) & 0xF;
        return audioCC;
    }

    public void write(Sample.Type type, ByteBuffer buf) {
        int cc = type == Sample.Type.H264 ? incrementAndGetVideo() : incrementAndGetAudio();
        buf.put((byte) ((0x10 | cc) & 0xFF)); // payload indicator + CC
    }
}
