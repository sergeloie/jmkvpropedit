package ru.anseranser.jmkvpropedit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Process launching and output reading — issue #14.
 *
 * <p>
 * The {@link JMkvpropedit} god class used to start mkvpropedit, probe the
 * executable and drain process output itself (executeBatch's SwingWorker
 * body, isExecutableInPath and the StreamGobbler reader thread); this runner
 * isolates that process layer with no Swing dependencies: callers hand in a
 * command and an output consumer, so tests can substitute a fake InputStream
 * or a different command without opening a window.
 * </p>
 */
public final class ProcessRunner {

    private final String executable;
    private final Path optionsFile;

    /**
     * Runner for the default mkvpropedit recipe: options written to
     * {@code options.json} in the working folder, same as the former
     * god-class batch loop.
     */
    public ProcessRunner(String executable) {
        this(executable, Path.of("options.json"));
    }

    public ProcessRunner(String executable, Path optionsFile) {
        this.executable = executable;
        this.optionsFile = optionsFile;
    }

    /**
     * Runs the command with stdout and stderr merged; every output line
     * (decoded as explicit UTF-8) is handed to the consumer before this
     * method returns.
     *
     * <p>
     * The reader runs on its own thread while the caller blocks in
     * {@code waitFor}, so a chatty child can never fill the pipe and
     * deadlock; joining the reader after the exit guarantees the whole
     * output reaches the consumer before the next log separator. On
     * interruption the child is destroyed so a cancelled batch does not
     * leave an orphan running.
     * </p>
     *
     * @param command full command line as separate arguments
     * @param output receives each line including its trailing newline
     * @throws IOException when the command cannot be started
     * @throws InterruptedException when the waiting thread is interrupted;
     *                             the child has been destroyed by then
     */
    public static void run(List<String> command, Consumer<String> output)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        Process proc = pb.start();
        Thread reader = new Thread(() -> forwardOutput(proc.getInputStream(), output),
                "process-output-reader");
        reader.setDaemon(true);
        reader.start();

        try {
            proc.waitFor();
            // The reader stops at EOF; joining it guarantees the whole
            // process output reaches the log before the next separator
            // (replaces the old fixed sleep).
            reader.join();
        } catch (InterruptedException e) {
            proc.destroy();
            throw e;
        }
    }

    /**
     * Writes the given content to the options file, runs
     * {@code executable @optionsFile} and removes the file afterwards —
     * the per-file mkvpropedit recipe from the batch loop. The file is
     * removed even when the launch fails.
     *
     * @param content options file payload (options.json in production)
     * @param output receives each output line including its trailing newline
     * @throws IOException when the file cannot be written or the command
     *                     cannot be started
     * @throws InterruptedException when the waiting thread is interrupted
     */
    public void runWithOptionsFile(String content, Consumer<String> output)
            throws IOException, InterruptedException {
        try (PrintWriter optionsWriter = new PrintWriter(optionsFile.toFile(), "UTF-8")) {
            optionsWriter.print(content);
        }

        try {
            run(List.of(executable, "@" + optionsFile), output);
        } finally {
            optionsFile.toFile().delete();
        }
    }

    /**
     * Probes whether the configured executable can be started.
     *
     * <p>
     * The merged output is drained by the reader thread while
     * {@code waitFor} blocks, so the child can never block on a full pipe
     * (the old waitFor-before-read order could deadlock). No SwingWorker
     * here: the probe never touches Swing, so there is nothing to marshal —
     * and an isDone busy-wait would only spin the EDT.
     * </p>
     *
     * @return true when the process started, false when it cannot be found
     */
    public boolean isExecutableInPath() {
        try {
            run(List.of(executable), line -> {
                // discard the probe output
            });
            return true;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Reads the stream until EOF and hands each line (including its
     * trailing newline) to the consumer; run on the caller's own thread —
     * {@link #run} wraps it in the reader thread. The former
     * {@code StreamGobbler}.
     *
     * <p>
     * Decode the process output as explicit UTF-8 instead of the platform
     * default charset; try-with-resources closes the stream so the caller
     * finishes as soon as the process hits EOF. IO errors are reported
     * through the consumer, mirroring the old gobbler log line.
     * </p>
     *
     * @param is stream to drain, typically a process's merged output
     * @param output receives each line including its trailing newline
     */
    static void forwardOutput(InputStream is, Consumer<String> output) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;

            while ((line = br.readLine()) != null) {
                output.accept(line + "\n");
            }
        } catch (IOException e) {
            output.accept(e.toString());
            e.printStackTrace();
        }
    }
}
