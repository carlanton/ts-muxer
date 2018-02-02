import org.mp4parser.Container;
import org.mp4parser.boxes.iso14496.part12.MediaDataBox;
import org.mp4parser.boxes.iso14496.part12.TrackFragmentBaseMediaDecodeTimeBox;
import org.mp4parser.boxes.iso14496.part12.TrackFragmentBox;
import org.mp4parser.boxes.iso14496.part12.TrackRunBox;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class AAC {
    private static final int PROFILE = 2; // aac lc
    private static final int SAMPLE_FREQ_INDEX = 3; // 48kHz
    private static final int CHANNEL_CONFIGURATION = 2; // stereo

    public static void main(String[] args) throws IOException {
        Container container = TsMuxer.readMp4(Paths.get("a-with-init.mp4"));


        List<AVPacket> frames = new ArrayList<>();
        TrackFragmentBox traf = container.getBoxes(TrackFragmentBox.class, true).get(0);
        ByteBuffer data = container.getBoxes(MediaDataBox.class, true).get(0).getData();
        TrackRunBox trun = traf.getBoxes(TrackRunBox.class, true).get(0);

        long sampleDuration = traf.getTrackFragmentHeaderBox().getDefaultSampleDuration();
        long pts = traf.getBoxes(TrackFragmentBaseMediaDecodeTimeBox.class).get(0).getBaseMediaDecodeTime();
        long dts = pts;


        data.rewind();
        for (TrackRunBox.Entry entry : trun.getEntries()) {
            int size = (int) entry.getSampleSize();
            data.limit(data.position() + size);

            frames.add(new AVPacket(data.duplicate(), pts, dts, 0, 257, false));

            data.position(data.limit());

            pts += sampleDuration;
            dts = pts;
        }

        data.rewind();

        try (FileChannel channel = FileChannel.open(Paths.get("java.aac"), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {

            for (AVPacket frame : frames) {

                int size = frame.data().limit() - frame.data.position();
                int frameLength = 7 + size;

                ByteBuffer header = ByteBuffer.allocate(7);
                header.put((byte) 0xFF);
                header.put((byte) 0xF1);
                //header.put((byte) (((PROFILE - 1) << 6) | (0x3c & (SAMPLE_FREQ_INDEX << 2)) | (0xff & CHANNEL_CONFIGURATION) >>> 2));
                header.put((byte) (((PROFILE - 1) << 6) | (0x3c & (SAMPLE_FREQ_INDEX << 2))));
                header.put((byte) ((CHANNEL_CONFIGURATION << 6) | (0x1FFF & frameLength >>> 11)));
                header.put((byte) ((0xFF & (frameLength >> 3))));
                header.put((byte) (((0x07 & frameLength) << 5) | 0x1F));
                header.put((byte) 0xFC);
                header.flip();

                write(header, channel);
                write(frame.data.duplicate(), channel);
            }
        }
    }

    private static void write(ByteBuffer byteBuffer, WritableByteChannel writableByteChannel) throws IOException {
        while (byteBuffer.remaining() > 0)
            writableByteChannel.write(byteBuffer);
    }
}
