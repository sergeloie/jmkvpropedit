package ru.anseranser.jmkvpropedit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for the process runner — issue #14: launching mkvpropedit,
 * reading its output and the executable probe used to live inside the
 * {@link JMkvpropedit} god class (executeBatch's SwingWorker body,
 * isExecutableInPath and the StreamGobbler reader thread);
 * {@link ProcessRunner} keeps that process layer isolated and free of Swing,
 * so every behavior here runs against a fake InputStream or a substituted
 * command — no window, no UI.
 */
class ProcessRunnerTest {

    @TempDir
    Path tempDir;

    private static String javaExecutable() {
        String exe = Utils.isWindows() ? "java.exe" : "java";
        return new File(new File(System.getProperty("java.home"), "bin"), exe).getPath();
    }

    @Test
    void fakeInputStreamLinesReachTheOutputConsumerInOrder() {
        byte[] data = "first line\nsecond line\n".getBytes(StandardCharsets.UTF_8);
        List<String> out = new ArrayList<>();

        ProcessRunner.forwardOutput(new ByteArrayInputStream(data), out::add);

        assertEquals(List.of("first line\n", "second line\n"), out);
    }

    @Test
    void fakeStreamIsDecodedAsExplicitUtf8() {
        String expected = "привет, мир";
        byte[] data = (expected + "\n").getBytes(StandardCharsets.UTF_8);
        List<String> out = new ArrayList<>();

        ProcessRunner.forwardOutput(new ByteArrayInputStream(data), out::add);

        assertEquals(List.of(expected + "\n"), out);
    }

    @Test
    void emptyStreamProducesNoOutputAndReturnsAtEof() {
        List<String> out = new ArrayList<>();

        ProcessRunner.forwardOutput(new ByteArrayInputStream(new byte[0]), out::add);

        assertTrue(out.isEmpty(), "no lines expected, got: " + out);
    }

    @Test
    void runForwardsTheMergedStderrOfARealJvmProcessBeforeReturning() throws Exception {
        List<String> out = new ArrayList<>();

        ProcessRunner.run(List.of(javaExecutable(), "-version"), out::add);

        assertFalse(out.isEmpty(),
                "java -version writes only to stderr; the merged stream must arrive before run() returns");
    }

    @Test
    void probeAcceptsTheJvmExecutableOnDisk() {
        assertTrue(new ProcessRunner(javaExecutable()).isExecutableInPath());
    }

    @Test
    void probeRejectsAMissingExecutable() {
        assertFalse(new ProcessRunner("jmkvpropedit-missing-probe.exe").isExecutableInPath());
    }

    @Test
    void optionsFileRunWritesTheContentRunsTheCommandAndRemovesTheFile() throws Exception {
        Path options = tempDir.resolve("options.json");
        ProcessRunner runner = new ProcessRunner(javaExecutable(), options);
        List<String> out = new ArrayList<>();

        runner.runWithOptionsFile("-version\n", out::add);

        assertFalse(Files.exists(options), "options file must be removed after the run");
        assertFalse(out.isEmpty(),
                "the command must have read the options file and produced output");
    }

    @Test
    void optionsFileRunRemovesTheFileWhenTheExecutableIsMissing() {
        Path options = tempDir.resolve("options.json");
        ProcessRunner runner = new ProcessRunner("jmkvpropedit-missing-probe.exe", options);
        List<String> out = new ArrayList<>();

        assertThrows(IOException.class, () -> runner.runWithOptionsFile("[]\n", out::add));

        assertFalse(Files.exists(options), "options file must be removed even when the launch fails");
    }
}
