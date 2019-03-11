package am.ik.batch.mysqlbackup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StreamUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;

public class CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(CommandExecutor.class);

    private final OutputStream out;

    private final OutputStream err;

    public CommandExecutor(OutputStream out, OutputStream err) {
        this.out = out;
        this.err = err;
    }

    public CommandExecutor() {
        this(System.out, System.err);
    }

    public void exec(String... command) {
        log.debug("Execute {}", Arrays.toString(command));
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(Paths.get(System.getProperty("java.io.tmpdir")).toFile());
        try {
            Process process = processBuilder.start();
            try (InputStream stdout = process.getInputStream();
                 InputStream stderr = process.getErrorStream()) {
                Mono<Integer> o = Mono.fromCallable(() -> StreamUtils.copy(stdout, out)).subscribeOn(Schedulers.elastic());
                Mono<Integer> e = Mono.fromCallable(() -> StreamUtils.copy(stderr, err)).subscribeOn(Schedulers.elastic());
                Mono<Integer> exitCode = Mono.fromCallable(process::waitFor).subscribeOn(Schedulers.elastic());
                o.map(x -> "sizeof(stdout): " + x)
                    .concatWith(e.map(x -> "sizeof(stderr): " + x))
                    .concatWith(exitCode.flatMap(c -> c == 0 ? Mono.empty() : Mono.error(new IllegalStateException("Non zero exit code from process: " + c))))
                    .log(getClass().getName())
                    .blockLast(Duration.ofMinutes(1));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
