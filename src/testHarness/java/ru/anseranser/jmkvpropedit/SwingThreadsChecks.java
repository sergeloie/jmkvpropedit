package ru.anseranser.jmkvpropedit;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/**
 * Framework-free verification harness for issue #5 (Swing threading: no
 * busy-wait for the SwingWorker on the EDT, Swing calls from background code
 * go through the EDT, the process-output reader thread terminates, and process
 * streams are decoded as explicit UTF-8).
 *
 * <p>
 * The Gradle build files are out of scope for issue #5, so this harness is a
 * plain {@code main()} program instead of a JUnit test, and it lives outside
 * Gradle source sets ({@code src/testHarness}) so {@code gradlew build} keeps
 * working without a test framework. Compile and run it manually after a build:
 * </p>
 *
 * <pre>
 * javac -encoding UTF-8 -cp "build/classes/java/main" -d build/testHarness ^
 *       src/testHarness/java/ru/anseranser/jmkvpropedit/SwingThreadsChecks.java
 * java -Dfile.encoding=ISO-8859-1 -cp "build/classes/java/main;build/testHarness;build/resources/main;lib/commons-io/commons-io-2.11.0.jar;lib/ini4j/ini4j-0.5.4.jar" ^
 *      ru.anseranser.jmkvpropedit.SwingThreadsChecks
 * </pre>
 *
 * <p>
 * The {@code -Dfile.encoding=ISO-8859-1} flag matters: it makes the platform
 * default charset disagree with UTF-8, so the "decoded as UTF-8" check only
 * passes when the reader asks for UTF-8 explicitly. Exits with a non-zero
 * status when any check fails.
 * </p>
 */
public final class SwingThreadsChecks {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        // The constructor calls parseFiles(argsArray); main() normally sets it first.
        setStatic("argsArray", new String[0]);

        System.out.println("Charset.defaultCharset() = " + Charset.defaultCharset()
                + " (expect ISO-8859-1 when run with -Dfile.encoding=ISO-8859-1)");
        System.out.println();

        checkGobblerAppendsOnEdt();
        checkGobblerDecodesUtf8();
        checkGobblerTerminatesAtEof();
        checkExecutableProbeResults();
        checkExecutableProbeDoesNotUseSwingWorker();
        checkBatchGoesThroughEdt();

        System.out.println();
        System.out.println("passed=" + passed + ", failed=" + failed);
        System.exit(failed == 0 ? 0 : 1);
    }

    /**
     * AC2: the reader thread must never touch the JTextArea directly; every
     * append/caret update has to be marshalled to the EDT.
     */
    private static void checkGobblerAppendsOnEdt() throws Exception {
        List<String> offEdt = Collections.synchronizedList(new ArrayList<>());
        JTextArea target = recordingArea(offEdt);
        byte[] data = "first line\nsecond line\n".getBytes(StandardCharsets.UTF_8);

        StreamGobbler gobbler = new StreamGobbler(new ByteArrayInputStream(data), target);
        gobbler.start();
        gobbler.join(TimeUnit.SECONDS.toMillis(5));
        flushEdt();

        check("gobbler touches the log only on the EDT",
                offEdt.isEmpty(), "off-EDT operations: " + offEdt);

        String text = target.getText();
        check("gobbler delivers every line to the log",
                text.contains("first line") && text.contains("second line"),
                "text=" + quote(text));
    }

    /**
     * AC4: process output is decoded as explicit UTF-8, not the platform
     * default charset. Meaningful only when the harness runs with
     * {@code -Dfile.encoding=ISO-8859-1} (see the class javadoc).
     */
    private static void checkGobblerDecodesUtf8() throws Exception {
        JTextArea target = new JTextArea();
        String expected = "привет, мир";
        byte[] data = (expected + "\n").getBytes(StandardCharsets.UTF_8);

        StreamGobbler gobbler = new StreamGobbler(new ByteArrayInputStream(data), target);
        gobbler.start();
        gobbler.join(TimeUnit.SECONDS.toMillis(5));
        flushEdt();

        check("process output is decoded as UTF-8, not the platform charset",
                target.getText().contains(expected),
                "defaultCharset=" + Charset.defaultCharset() + ", text=" + quote(target.getText()));
    }

    /**
     * AC3: the reader thread must finish on its own once the stream hits EOF
     * (the batch worker joins it before printing the next separator).
     */
    private static void checkGobblerTerminatesAtEof() throws Exception {
        StreamGobbler gobbler = new StreamGobbler(
                new ByteArrayInputStream("line\n".getBytes(StandardCharsets.UTF_8)), new JTextArea());
        gobbler.start();
        gobbler.join(TimeUnit.SECONDS.toMillis(5));

        check("reader thread terminates at end of stream", !gobbler.isAlive(),
                "gobbler still alive 5s after EOF");
    }

    /**
     * AC1/AC3: the executable probe still reports found/missing correctly. The
     * probe executable must be quiet: the old waitFor-before-read order
     * deadlocks as soon as the child writes more than the pipe buffer.
     */
    private static void checkExecutableProbeResults() throws Exception {
        JMkvpropedit w = newWindow();

        boolean found = (Boolean) call(w, "isExecutableInPath", "hostname");
        check("exe probe finds an executable on the PATH", found, "hostname was not found");

        boolean missing = (Boolean) call(w, "isExecutableInPath", "jmkvpropedit-missing-probe.exe");
        check("exe probe rejects a missing executable", !missing, "returned true for a missing exe");
    }

    /**
     * AC1: the probe must not spin up a SwingWorker just to busy-wait on
     * isDone from the EDT — it never touches Swing, so there is nothing a
     * worker could marshal.
     */
    private static void checkExecutableProbeDoesNotUseSwingWorker() throws Exception {
        JMkvpropedit w = newWindow();

        call(w, "isExecutableInPath", "hostname");

        Object worker = get(w, "worker");
        check("exe probe runs without a SwingWorker isDone busy-wait",
                worker == null, "worker field was touched: " + worker);
    }

    /**
     * AC1/AC2/AC5: a two-file batch run must keep every Swing call issued from
     * background code on the EDT (log appends, tab switch, button/tab enable
     * state), keep the EDT pumping events while the run lasts, and restore the
     * controls once the worker is done. The child process (java.exe choking on
     * the options.json) contributes its own stderr lines to the log, so a log
     * that is not longer than the headers alone means output was lost.
     */
    private static void checkBatchGoesThroughEdt() throws Exception {
        JMkvpropedit w = newWindow();
        List<String> offEdt = Collections.synchronizedList(new ArrayList<>());

        JTextArea out = recordingArea(offEdt);
        set(w, "txtOutput", out);

        JTabbedPane tabs = new JTabbedPane() {
            private static final long serialVersionUID = 1L;

            @Override
            public void setSelectedIndex(int index) {
                note(offEdt, "JTabbedPane.setSelectedIndex");
                super.setSelectedIndex(index);
            }

            @Override
            public void setEnabled(boolean enabled) {
                note(offEdt, "JTabbedPane.setEnabled");
                super.setEnabled(enabled);
            }
        };
        tabs.addTab("Input", new JLabel("input"));
        tabs.addTab("Output", new JLabel("output"));
        set(w, "pnlTabs", tabs);

        set(w, "btnProcessFiles", recordingButton(offEdt));
        set(w, "btnGenerateCmdLine", recordingButton(offEdt));

        ((JTextField) get(w, "txtMkvPropExe")).setText(javaExecutable());

        @SuppressWarnings("unchecked")
        DefaultListModel<String> modelFiles = (DefaultListModel<String>) get(w, "modelFiles");
        modelFiles.addElement("C:\\clips\\first.mkv");
        modelFiles.addElement("C:\\clips\\second.mkv");

        ((JCheckBox) get(w, "chbTitleGeneral")).setSelected(true);
        ((JTextField) get(w, "txtTitleGeneral")).setText("Batch test");

        call(w, "setCmdLine");

        // Setup above may have been recorded off the EDT; only the batch run counts.
        offEdt.clear();

        // Production calls executeBatch from a button listener on the EDT.
        Throwable[] startError = new Throwable[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                call(w, "executeBatch");
            } catch (Throwable t) {
                startError[0] = t;
            }
        });
        check("executeBatch starts cleanly on the EDT", startError[0] == null,
                String.valueOf(startError[0]));
        if (startError[0] != null) {
            return;
        }

        SwingWorker<?, ?> workerRef = (SwingWorker<?, ?>) get(w, "worker");
        check("batch runs in a background SwingWorker", workerRef != null, "worker==null");

        // AC5: an event posted to the EDT must still be processed during the run.
        CountDownLatch pump = new CountDownLatch(1);
        SwingUtilities.invokeLater(pump::countDown);
        boolean responsive = pump.await(2, TimeUnit.SECONDS);
        check("EDT stays responsive while the batch runs", responsive,
                "a posted event was not processed within 2s");

        boolean workerOk = workerRef != null;
        if (workerRef != null) {
            try {
                workerRef.get(60, TimeUnit.SECONDS);
            } catch (Throwable t) {
                workerOk = false;
                check("batch worker finishes without errors", false, t.toString());
            }
        }
        flushEdt(); // done() + queued log appends

        check("Swing calls issued from background code go through the EDT",
                offEdt.isEmpty(), "off-EDT operations: " + offEdt);

        JButton processBtn = (JButton) get(w, "btnProcessFiles");
        JButton generateBtn = (JButton) get(w, "btnGenerateCmdLine");
        // get() may unblock before SwingWorker posts done() to the EDT, so poll
        // for the restoration instead of assuming one flush is enough.
        boolean controls = false;
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
        while (!controls && System.currentTimeMillis() < deadline) {
            boolean[] state = new boolean[1];
            SwingUtilities.invokeAndWait(() ->
                    state[0] = processBtn.isEnabled() && generateBtn.isEnabled() && tabs.isEnabled());
            controls = state[0];
            if (!controls) {
                Thread.sleep(50);
            }
        }
        check("controls are re-enabled after the run", controls, "still disabled after done()");

        int[] selected = new int[1];
        SwingUtilities.invokeAndWait(() -> selected[0] = tabs.getSelectedIndex());
        check("batch switches to the output tab",
                selected[0] == tabs.getTabCount() - 1,
                "selected=" + selected[0] + ", tabs=" + tabs.getTabCount());

        if (!workerOk) {
            return;
        }

        String text = out.getText();

        @SuppressWarnings("unchecked")
        List<String> batch = (List<String>) get(w, "cmdLineBatch");
        String headerOnly = "File: C:\\clips\\first.mkv\n"
                + "Command line: " + batch.get(0) + "\n\n"
                + "--------------\n\n"
                + "File: C:\\clips\\second.mkv\n"
                + "Command line: " + batch.get(1) + "\n\n";

        check("both file headers reach the log",
                text.contains("File: C:\\clips\\first.mkv")
                        && text.contains("File: C:\\clips\\second.mkv"),
                "text=" + quote(text));

        int separator = text.indexOf("--------------");
        int secondFile = text.indexOf("File: C:\\clips\\second.mkv");
        check("files are separated in the log",
                separator > 0 && secondFile > separator,
                "separator=" + separator + ", secondFile=" + secondFile);

        check("child process output reaches the log in full",
                text.length() > headerOnly.length(),
                "log=" + text.length() + " chars, headers only=" + headerOnly.length()
                        + ", text=" + quote(text));
    }

    /* Harness plumbing */

    private static JMkvpropedit newWindow() throws Exception {
        return new JMkvpropedit();
    }

    private static String javaExecutable() {
        String exe = Utils.isWindows() ? "java.exe" : "java";
        return new File(new File(System.getProperty("java.home"), "bin"), exe).getPath();
    }

    private static void note(List<String> offEdt, String op) {
        if (!SwingUtilities.isEventDispatchThread()) {
            offEdt.add(op);
        }
    }

    private static JTextArea recordingArea(final List<String> offEdt) {
        return new JTextArea() {
            private static final long serialVersionUID = 1L;

            @Override
            public void setText(String text) {
                note(offEdt, "JTextArea.setText");
                super.setText(text);
            }

            @Override
            public void append(String text) {
                note(offEdt, "JTextArea.append");
                super.append(text);
            }

            @Override
            public void setCaretPosition(int position) {
                note(offEdt, "JTextArea.setCaretPosition");
                super.setCaretPosition(position);
            }
        };
    }

    private static JButton recordingButton(final List<String> offEdt) {
        return new JButton() {
            private static final long serialVersionUID = 1L;

            @Override
            public void setEnabled(boolean enabled) {
                note(offEdt, "JButton.setEnabled");
                super.setEnabled(enabled);
            }
        };
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
        });
    }

    private static String quote(String text) {
        String flat = text.replace("\r", "\\r").replace("\n", "\\n");
        return flat.length() > 300 ? flat.substring(0, 300) + "..." : flat;
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = JMkvpropedit.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = JMkvpropedit.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void setStatic(String name, Object value) throws Exception {
        Field field = JMkvpropedit.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static Object call(Object target, String name, Object... args) throws Exception {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = (args[i] instanceof File) ? File.class : args[i].getClass();
        }
        Method method = JMkvpropedit.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static void check(String what, boolean condition, String detail) {
        if (condition) {
            passed++;
            System.out.println("PASS  " + what);
        } else {
            failed++;
            System.out.println("FAIL  " + what + "  (" + detail + ")");
        }
    }
}
