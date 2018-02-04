import org.mp4parser.BasicContainer;
import org.mp4parser.BoxParser;
import org.mp4parser.Container;
import org.mp4parser.PropertyBoxParserImpl;
import org.mp4parser.boxes.iso14496.part12.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MP4Utils {
    private static final int SAMPLE_MAX_SIZE = 8000;
    private static final int PROFILE = 2;               // aac lc
    private static final int SAMPLE_FREQ_INDEX = 3;     // 48kHz
    private static final int CHANNEL_CONFIGURATION = 2; // stereo
    private static final int ADTS_SIZE = 7;

    private static final int VIDEO_PID = 256;
    private static final int AUDIO_PID = 257;

    private static long convertTime(long t, long timescale) {
        return t * 90000 / timescale % 8589934592L; // 2^33
    }

    public static List<Sample> readVideo(Path path, NalUnitToByteStreamConverter converter) throws IOException {
        Container container = readMp4(path);

        TrackFragmentBox traf = container.getBoxes(TrackFragmentBox.class, true).get(0);
        ByteBuffer data = container.getBoxes(MediaDataBox.class, true).get(0).getData();
        TrackRunBox trun = traf.getBoxes(TrackRunBox.class, true).get(0);
        SegmentIndexBox sidx = container.getBoxes(SegmentIndexBox.class).get(0);

        TrackFragmentBaseMediaDecodeTimeBox tfdt = traf.getBoxes(TrackFragmentBaseMediaDecodeTimeBox.class).get(0);

        long timescale = sidx.getTimeScale();
        long sampleDuration = convertTime(traf.getTrackFragmentHeaderBox().getDefaultSampleDuration(), timescale);
        long pts = convertTime(tfdt.getBaseMediaDecodeTime(), timescale);
        long dts = pts;

        data.rewind();

        List<Sample> samples = new ArrayList<>();


        boolean keyFrame = true;

        for (TrackRunBox.Entry entry : trun.getEntries()) {
            int size = (int) entry.getSampleSize();
            data.limit(data.position() + size);

            ByteBuffer frame = converter.convert(data, keyFrame);
            samples.add(new Sample(frame, pts, dts, VIDEO_PID, keyFrame, Sample.Type.H264));

            data.position(data.limit());

            pts += sampleDuration;
            dts = pts;
            keyFrame = false;
        }

        return samples;
    }

    public static List<Sample> readAudio(Path path) throws IOException {
        Container container = readMp4(path);

        TrackFragmentBox traf = container.getBoxes(TrackFragmentBox.class, true).get(0);
        ByteBuffer data = container.getBoxes(MediaDataBox.class, true).get(0).getData();
        TrackRunBox trun = traf.getBoxes(TrackRunBox.class, true).get(0);
        SegmentIndexBox sidx = container.getBoxes(SegmentIndexBox.class).get(0);
        TrackFragmentBaseMediaDecodeTimeBox tfdt = traf.getBoxes(TrackFragmentBaseMediaDecodeTimeBox.class).get(0);

        long timescale = sidx.getTimeScale();
        long sampleDuration = convertTime(traf.getTrackFragmentHeaderBox().getDefaultSampleDuration(), timescale);
        long pts = convertTime(tfdt.getBaseMediaDecodeTime(), timescale);
        long dts = pts;

        data.rewind();

        List<Sample> samples = new ArrayList<>();

        ByteBuffer sample = ByteBuffer.allocate(SAMPLE_MAX_SIZE);
        long samplePts = pts;
        long sampleDts = dts;

        for (TrackRunBox.Entry entry : trun.getEntries()) {
            int size = (int) entry.getSampleSize();
            data.limit(data.position() + size);

            if (sample.remaining() < size + ADTS_SIZE) {
                sample.flip();
                samples.add(new Sample(sample, samplePts, sampleDts, AUDIO_PID, true, Sample.Type.AAC_LC));
                sample = ByteBuffer.allocate(SAMPLE_MAX_SIZE);
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
            samples.add(new Sample(sample, samplePts, sampleDts, AUDIO_PID, true, Sample.Type.AAC_LC));
        }

        return samples;
    }

    private static void addAdts(int size, ByteBuffer data) {
        int frameLength = ADTS_SIZE + size;
        data.put((byte) 0xFF);
        data.put((byte) 0xF1);
        //header.put((byte) (((PROFILE - 1) << 6) | (0x3c & (SAMPLE_FREQ_INDEX << 2)) | (0xff & CHANNEL_CONFIGURATION) >>> 2));
        data.put((byte) (((PROFILE - 1) << 6) | (0x3c & (SAMPLE_FREQ_INDEX << 2))));
        data.put((byte) ((CHANNEL_CONFIGURATION << 6) | (0x1FFF & frameLength >>> 11)));
        data.put((byte) ((0xFF & (frameLength >> 3))));
        data.put((byte) (((0x07 & frameLength) << 5) | 0x1F));
        data.put((byte) 0xFC);
    }

    static Container readMp4(Path path) throws IOException {
        BasicContainer container;
        try (FileChannel channel = FileChannel.open(path)) {
            BoxParser boxParser = new PropertyBoxParserImpl();
            container = new BasicContainer();
            container.initContainer(channel, -1, boxParser);
        }
        return container;
    }
}
