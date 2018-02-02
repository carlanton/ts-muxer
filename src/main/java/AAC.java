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
        for (TrackRunBox.Entry entry : trun.getEntries()) {
            int size = (int) entry.getSampleSize();
            data.limit(data.position() + size);

            ByteBuffer sample = addAdts(data);


            samples.add(new Sample(sample, pts, dts, 0, 257, true, Sample.Type.AAC_LC));

            data.position(data.limit());

            pts += sampleDuration;
            dts = pts;
        }

        List<Sample> xs = new ArrayList<>();
        Sample prev = null;
        for (Sample sample : samples) {
            if (prev == null) {
                prev = sample;
            } else if (prev.data.limit() + sample.data.limit() < 2912) {
                ByteBuffer combined = ByteBuffer.allocate(prev.data.limit() + sample.data.limit());
                combined.put(prev.data);
                combined.put(sample.data);
                combined.rewind();
                prev = new Sample(combined, prev.pts(), prev.dts(), prev.streamIndex(), prev.mpegTsStreamId(),
                        prev.isKeyFrame(), Sample.Type.AAC_LC);
            } else {
                xs.add(prev);
                prev = sample;
            }
        }
        if (prev != null)
            xs.add(prev);

        xs.forEach(System.out::println);

        return xs;
    }

    private static ByteBuffer addAdts(ByteBuffer data) {
        int size = data.limit() - data.position();
        int frameLength = 7 + size;

        ByteBuffer header = ByteBuffer.allocate(7 + size);
        header.put((byte) 0xFF);
        header.put((byte) 0xF1);
        //header.put((byte) (((PROFILE - 1) << 6) | (0x3c & (SAMPLE_FREQ_INDEX << 2)) | (0xff & CHANNEL_CONFIGURATION) >>> 2));
        header.put((byte) (((PROFILE - 1) << 6) | (0x3c & (SAMPLE_FREQ_INDEX << 2))));
        header.put((byte) ((CHANNEL_CONFIGURATION << 6) | (0x1FFF & frameLength >>> 11)));
        header.put((byte) ((0xFF & (frameLength >> 3))));
        header.put((byte) (((0x07 & frameLength) << 5) | 0x1F));
        header.put((byte) 0xFC);

        data.get(header.array(), header.position(), size);
        header.rewind();
        return header;
    }
}
