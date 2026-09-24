package ru.anseranser.jmkvpropedit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JTextField;

/**
 * Framework-free verification harness for issue #6 (options.json: valid JSON
 * for any paths and names, placeholder-based escaping gone from the JSON
 * generation pipeline, dead code removed from the file writer).
 *
 * <p>
 * The Gradle build files are out of scope for issue #6, so this harness is a
 * plain {@code main()} program instead of a JUnit test, and it lives outside
 * Gradle source sets ({@code src/testHarness}) so {@code gradlew build} keeps
 * working without a test framework. Compile and run it manually after a build:
 * </p>
 *
 * <pre>
 * javac -encoding UTF-8 -cp "build/classes/java/main" -d build/testHarness ^
 *       src/testHarness/java/ru/anseranser/jmkvpropedit/OptionsJsonChecks.java
 * java -Dfile.encoding=UTF-8 -cp "build/classes/java/main;build/testHarness;build/resources/main;build/install/jmkvpropedit/lib/*" ^
 *      ru.anseranser.jmkvpropedit.OptionsJsonChecks
 * </pre>
 *
 * <p>
 * The validity checks use a small strict RFC 8259 string-array parser written
 * here — no JSON library is added to the application. The parser rejects raw
 * control characters, bad escapes and unbalanced quotes, so "parses" means
 * "valid JSON". Exits with a non-zero status when any check fails.
 * </p>
 */
public final class OptionsJsonChecks {

    /** The quote marker from Utils.escapeName; must never reach options.json. */
    private static final String QUOTE_PLACEHOLDER = "####escaped__quotes#####";

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        // The constructor calls parseFiles(argsArray); main() normally sets it first.
        setStatic("argsArray", new String[0]);

        // Meta: prove the strict parser rejects the shapes the old writer produced,
        // otherwise the round-trip checks below would prove nothing.
        assertRejected("backslash before quote (legacy raw write)", "[\"A\\\\\"B\"]");
        assertRejected("raw control character (legacy raw write)", "[\"a\tb\"]");
        assertRejected("single backslash before a letter (legacy raw write)", "[\"C:\\clips\"]");

        checkCraftedRoundTrip("backslash before quote", "back\\slash\"quote");
        checkCraftedRoundTrip("control characters", "tab\there\nline\r\u0001\u001f\"q\\z");
        checkCraftedRoundTrip("unicode and emoji", "日本語-Ω-🧪-Юникод");

        checkEmptyArgsProduceEmptyArray();
        checkEndToEndPathNameAndTitle();

        System.out.println();
        System.out.println("passed=" + passed + ", failed=" + failed);
        System.exit(failed == 0 ? 0 : 1);
    }

    /**
     * AC1 (crafted): data with quotes, backslashes and control characters
     * survives the full Opt-line -> args -> options.json round trip and the
     * result parses as strict JSON with the original values.
     */
    private static void checkCraftedRoundTrip(String what, String data) {
        // Built exactly like production composes Opt command lines (escapeName
        // around quoted values), so the crack+write path under test is the real one.
        String line = "\"" + Utils.escapeName(data) + "\""
                + " --set name=\"" + Utils.escapeName(data) + "\"";

        List<String> parsed;
        try {
            parsed = parseStringArray(JMkvpropedit.optionsJson(JMkvpropedit.toOptArgs(line)));
        } catch (RuntimeException e) {
            check("crafted " + what + ": options.json is valid JSON", false,
                    e + ", data=" + flat(data));
            return;
        }

        List<String> expected = List.of(data, "--set", "name=" + data);
        check("crafted " + what + ": options.json is valid JSON", true, "");
        check("crafted " + what + ": arguments round-trip unchanged", parsed.equals(expected),
                "expected=" + expected + ", got=" + parsed);
    }

    /** AC: an argument-less options file is still a valid empty JSON array. */
    private static void checkEmptyArgsProduceEmptyArray() {
        String json = JMkvpropedit.optionsJson(new String[0]);

        try {
            List<String> parsed = parseStringArray(json);
            check("empty argument list becomes an empty JSON array", parsed.isEmpty(),
                    "json=" + flat(json));
        } catch (RuntimeException e) {
            check("empty argument list becomes an empty JSON array", false,
                    e + ", json=" + flat(json));
        }
    }

    /**
     * AC1/AC2 (end to end): a batch built from the UI — file path and track
     * name with quotes/backslashes/unicode, {num} substitution, unicode title —
     * produces valid options.json whose decoded arguments equal the original
     * input, and the quote placeholder never appears in the file.
     */
    private static void checkEndToEndPathNameAndTitle() throws Exception {
        JMkvpropedit w = newWindow();
        Field videoPanelField = JMkvpropedit.class.getDeclaredField("videoPanel");
        videoPanelField.setAccessible(true);
        TrackPanel videoPanel = (TrackPanel) videoPanelField.get(w);
        videoPanel.addTrack();
        TrackSlot slot = videoPanel.slots().get(0);

        slot.chbEdit.setSelected(true);
        slot.chbName.setSelected(true);
        slot.chbNumb.setSelected(true);
        slot.txtName.setText("A\"B\\{num}");
        slot.txtNumbStart.setText("7");
        slot.txtNumbPad.setText("2");

        ((JCheckBox) get(w, "chbTitleGeneral")).setSelected(true);
        ((JTextField) get(w, "txtTitleGeneral")).setText("Юникод \"кавычки\" \\ конец");

        String path = "C:\\кл\\\"quoted\"\\файл.mkv";
        @SuppressWarnings("unchecked")
        DefaultListModel<String> modelFiles = (DefaultListModel<String>) get(w, "modelFiles");
        modelFiles.addElement(path);

        call(w, "setCmdLine");

        @SuppressWarnings("unchecked")
        List<String[]> batchOpt = (List<String[]>) get(w, "cmdLineBatchOpt");
        String json = JMkvpropedit.optionsJson(batchOpt.get(0));

        List<String> parsed;
        try {
            parsed = parseStringArray(json);
            check("e2e: options.json for a tricky batch is valid JSON", true, "");
        } catch (RuntimeException e) {
            check("e2e: options.json for a tricky batch is valid JSON", false,
                    e + ", json=" + flat(json));
            return;
        }

        check("e2e: path with quotes, backslashes and unicode round-trips",
                parsed.contains(path), "args=" + parsed);
        check("e2e: track name keeps quote, backslash and {num} result",
                parsed.contains("name=A\"B\\07"), "args=" + parsed);
        check("e2e: title keeps quotes, backslash and unicode",
                parsed.contains("title=Юникод \"кавычки\" \\ конец"), "args=" + parsed);
        check("e2e: quote placeholder never reaches options.json",
                !json.contains(QUOTE_PLACEHOLDER), "json=" + flat(json));
    }

    /* Strict JSON string-array parser (the validator) */

    private static List<String> parseStringArray(String json) {
        Cursor c = new Cursor(json);
        c.skipWs();
        c.expect('[');
        List<String> out = new ArrayList<>();
        c.skipWs();
        if (c.peek() == ']') {
            c.next();
        } else {
            for (;;) {
                c.skipWs();
                out.add(parseString(c));
                c.skipWs();
                char sep = c.next();
                if (sep == ',') {
                    continue;
                }
                if (sep == ']') {
                    break;
                }
                throw new IllegalArgumentException(
                        "expected ',' or ']' at index " + (c.i - 1) + " in " + flat(json));
            }
        }
        c.skipWs();
        if (c.i != json.length()) {
            throw new IllegalArgumentException(
                    "trailing characters at index " + c.i + " in " + flat(json));
        }
        return out;
    }

    private static String parseString(Cursor c) {
        c.expect('"');
        StringBuilder sb = new StringBuilder();
        for (;;) {
            char ch = c.next();
            if (ch == '"') {
                return sb.toString();
            }
            if (ch == '\\') {
                char e = c.next();
                switch (e) {
                case '"', '\\', '/' -> sb.append(e);
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    if (c.i + 4 > c.s.length()) {
                        throw new IllegalArgumentException("truncated \\u escape");
                    }
                    int val = Integer.parseInt(c.s.substring(c.i, c.i + 4), 16);
                    c.i += 4;
                    sb.append((char) val);
                }
                default -> throw new IllegalArgumentException("invalid escape \\" + e);
                }
            } else if (ch < 0x20) {
                throw new IllegalArgumentException("unescaped control character " + (int) ch);
            } else {
                sb.append(ch);
            }
        }
    }

    private static final class Cursor {

        final String s;
        int i;

        Cursor(String s) {
            this.s = s;
        }

        void skipWs() {
            while (i < s.length()) {
                char ch = s.charAt(i);
                if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r') {
                    i++;
                } else {
                    break;
                }
            }
        }

        char next() {
            if (i >= s.length()) {
                throw new IllegalArgumentException("unexpected end of JSON");
            }
            return s.charAt(i++);
        }

        char peek() {
            if (i >= s.length()) {
                throw new IllegalArgumentException("unexpected end of JSON");
            }
            return s.charAt(i);
        }

        void expect(char e) {
            char g = next();
            if (g != e) {
                throw new IllegalArgumentException("expected '" + e + "', got '" + g + "'");
            }
        }
    }

    private static void assertRejected(String what, String json) {
        try {
            parseStringArray(json);
            check("validator rejects " + what, false, "accepted: " + flat(json));
        } catch (RuntimeException e) {
            check("validator rejects " + what, true, "");
        }
    }

    /* Harness plumbing */

    private static JMkvpropedit newWindow() throws Exception {
        return new JMkvpropedit();
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = JMkvpropedit.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setStatic(String name, Object value) throws Exception {
        Field field = JMkvpropedit.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static Object call(Object target, String name, Object... args) throws Exception {
        Method method = JMkvpropedit.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static String flat(String text) {
        return text.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
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
