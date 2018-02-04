public class ContinuityCounter {
    private int videoCC = 15;
    private int audioCC = 15;

    public int incrementAndGetVideo() {
        videoCC = (videoCC + 1) & 0xF;
        return videoCC;
    }

    public int incrementAndGetAudio() {
        audioCC = (audioCC + 1) & 0xF;
        return audioCC;
    }
}
