import java.nio.ByteBuffer;

public class AVPacket {
    ByteBuffer data;
    private final long pts;
    private final long dts;
    private final int streamIndex;
    private final int mpegTsStreamId;
    private final boolean isKeyFrame;

    public AVPacket(ByteBuffer data, long pts, long dts, int streamIndex, int mpegTsStreamId, boolean isKeyFrame) {
        this.data = data;
        this.pts = pts;
        this.dts = dts;
        this.streamIndex = streamIndex;
        this.mpegTsStreamId = mpegTsStreamId;
        this.isKeyFrame = isKeyFrame;
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
}
