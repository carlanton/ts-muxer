import java.nio.ByteBuffer;

public class Sample {
    ByteBuffer data;
    private final long pts;
    private final long dts;
    private final int streamIndex;
    private final int mpegTsStreamId;
    private final boolean isKeyFrame;
    private final Type type;

    public Sample(ByteBuffer data, long pts, long dts, int streamIndex, int mpegTsStreamId, boolean isKeyFrame, Type type) {
        this.data = data;
        this.pts = pts;
        this.dts = dts;
        this.streamIndex = streamIndex;
        this.mpegTsStreamId = mpegTsStreamId;
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

    public int streamIndex() {
        return streamIndex;
    }

    public int mpegTsStreamId() {
        return mpegTsStreamId;
    }

    public boolean isKeyFrame() {
        return isKeyFrame;
    }

    public Type getType() {
        return type;
    }

    @Override
    public String toString() {
        return "AVPacket{" +
                "data=" + data +
                ", pts=" + pts +
                ", dts=" + dts +
                ", streamIndex=" + streamIndex +
                ", mpegTsStreamId=" + mpegTsStreamId +
                '}';
    }

    public enum Type {
        H264, AAC_LC
    }
}
