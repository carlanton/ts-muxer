import io.lindstrom.m3u8.model.MediaPlaylist;
import io.lindstrom.m3u8.model.MediaSegment;
import io.lindstrom.m3u8.parser.MediaPlaylistParser;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.mp4parser.Container;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TsMuxerHandler implements HttpHandler {
    private final Path basePath;
    private final NalUnitToByteStreamConverter nalUnitToByteStreamConverter;
    private final TsMuxer muxer;


    public TsMuxerHandler() throws IOException {
        basePath = Paths.get("media/bbb/");
        Container init = MP4Utils.readMp4(basePath.resolve("v-init.mp4"));
        nalUnitToByteStreamConverter = TsMuxer.createConverter(init);

        ByteBuffer patAndPmt = ByteBuffer.wrap(Files.readAllBytes(Paths.get("media/v6.ts")));
        patAndPmt.limit(188*2);

        muxer = new TsMuxer();
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (exchange.isInIoThread()) {
            exchange.dispatch(this);
            return;
        }

        if (exchange.getRequestPath().endsWith(".ts")) {
            mux(exchange);
        } else if (exchange.getRequestPath().endsWith(".m3u8")) {
            sendManifest(exchange);
        }
    }

    private void sendManifest(HttpServerExchange exchange) {
        List<MediaSegment> segments = IntStream.range(1, 16)
                .mapToObj(j -> MediaSegment.builder()
                        .duration(12)
                        .uri("v-" + j + ".ts")
                        .build())
                .collect(Collectors.toList());

        MediaPlaylist mediaPlaylist = MediaPlaylist.builder()
                .targetDuration(12)
                .version(5)
                .mediaSequence(1)
                .ongoing(false)
                .addAllMediaSegments(segments)
                .build();

        ByteBuffer data = new MediaPlaylistParser().writePlaylistAsByteBuffer(mediaPlaylist);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/x-mpegURL");
        exchange.getResponseSender().send(data);
    }

    private void mux(HttpServerExchange exchange) throws IOException {
        String segment = exchange.getRequestPath().substring(1).replace(".ts", ".mp4");
        Path sourceSegment = basePath.resolve(segment);
        Path audioSegment = basePath.resolve(segment.replace("v", "a"));

        ByteBuffer[] tsSegment = muxer.read(sourceSegment, audioSegment, nalUnitToByteStreamConverter);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "video/MP2T");
        exchange.getResponseSender().send(tsSegment);
    }


    public static void main(String[] args) throws IOException {
        Undertow.builder()
                .addHttpListener(8080, "127.0.0.1")
                .setHandler(new TsMuxerHandler())
                .build()
                .start();
    }
}
