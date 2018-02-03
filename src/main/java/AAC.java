import org.mp4parser.Container;
import org.mp4parser.boxes.iso14496.part12.MediaDataBox;
import org.mp4parser.boxes.iso14496.part12.TrackFragmentBaseMediaDecodeTimeBox;
import org.mp4parser.boxes.iso14496.part12.TrackFragmentBox;
import org.mp4parser.boxes.iso14496.part12.TrackRunBox;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class AAC {
    private static final int PROFILE = 2;               // aac lc
    private static final int SAMPLE_FREQ_INDEX = 3;     // 48kHz
    private static final int CHANNEL_CONFIGURATION = 2; // stereo
    private static final int ADTS_SIZE = 7;

    public static List<Sample> read(Path path) throws IOException {
        Container container = TsMuxer.readMp4(path);

        List<Sample> samples = new ArrayList<>();
        TrackFragmentBox traf = container.getBoxes(TrackFragmentBox.class, true).get(0);
        ByteBuffer data = container.getBoxes(MediaDataBox.class, true).get(0).getData();
        TrackRunBox trun = traf.getBoxes(TrackRunBox.class, true).get(0);

        long sampleDuration = traf.getTrackFragmentHeaderBox().getDefaultSampleDuration();
        long pts = traf.getBoxes(TrackFragmentBaseMediaDecodeTimeBox.class).get(0).getBaseMediaDecodeTime();
        long dts = pts;

        data.rewind();

        ByteBuffer sample = ByteBuffer.allocate(2912);
        long samplePts = pts;
        long sampleDts = dts;

        for (TrackRunBox.Entry entry : trun.getEntries()) {
            int size = (int) entry.getSampleSize();
            data.limit(data.position() + size);

            if (sample.remaining() < size + ADTS_SIZE) {
                sample.flip();
                samples.add(new Sample(sample, samplePts, sampleDts, 0, 257, true, Sample.Type.AAC_LC));
                sample = ByteBuffer.allocate(2912);
                samplePts = pts;
                sampleDts = dts;
            }

            addAdts(size, sample);
            data.get(sample.array(), sample.position(), size);
            sample.position(sample.position() + size);

            data.position(data.limit());

            pts += sampleDuration;
            dts = pts;
        }

        if (sample.position() > 0) {
            sample.flip();
            samples.add(new Sample(sample, samplePts, sampleDts, 0, 257, true, Sample.Type.AAC_LC));
        }

        return samples;
    }

    private static void addAdts(int size, ByteBuffer data) {
        int frameLength = 7 + size;
        data.put((byte) 0xFF);
        data.put((byte) 0xF1);
        //header.put((byte) (((PROFILE - 1) << 6) | (0x3c & (SAMPLE_FREQ_INDEX << 2)) | (0xff & CHANNEL_CONFIGURATION) >>> 2));
        data.put((byte) (((PROFILE - 1) << 6) | (0x3c & (SAMPLE_FREQ_INDEX << 2))));
        data.put((byte) ((CHANNEL_CONFIGURATION << 6) | (0x1FFF & frameLength >>> 11)));
        data.put((byte) ((0xFF & (frameLength >> 3))));
        data.put((byte) (((0x07 & frameLength) << 5) | 0x1F));
        data.put((byte) 0xFC);
    }
}
