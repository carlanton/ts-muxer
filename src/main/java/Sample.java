import java.nio.ByteBuffer;

public class Sample {
    private final ByteBuffer data;
    private final long pts;
    private final long dts;
    private final int pid;
    private final boolean isKeyFrame;
    private final Type type;

    public Sample(ByteBuffer data, long pts, long dts, int pid, boolean isKeyFrame, Type type) {
        this.data = data;
        this.pts = pts;
        this.dts = dts;
        this.pid = pid;
        this.isKeyFrame = isKeyFrame;
        this.type = type;
    }

    public ByteBuffer data() {
        return data;
    }

    public long dts() {
        return dts;
    }

    public long pts() {
        return pts;
    }

    public int pid() {
        return pid;
    }

    public boolean isKeyFrame() {
        return isKeyFrame;
    }

    public Type getType() {
        return type;
    }

    public enum Type {
        H264, AAC_LC
    }
}
