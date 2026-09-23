package ru.anseranser.jmkvpropedit;

import java.awt.CardLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.swing.AbstractButton;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Framework-free verification harness for issue #3 (track numbering in the Opt
 * command variant and removal of the selected track).
 *
 * <p>
 * The Gradle build files are out of scope for issue #3, so this harness is a
 * plain {@code main()} program instead of a JUnit test, and it lives outside
 * Gradle source sets ({@code src/testHarness}) so {@code gradlew build} keeps
 * working without a test framework. Compile and run it manually after a build:
 * </p>
 *
 * <pre>
 * javac -cp "build/classes/java/main" -d build/testHarness ^
 *       src/testHarness/java/ru/anseranser/jmkvpropedit/TrackManagementChecks.java
 * java -cp "build/classes/java/main;build/testHarness;build/resources/main;build/install/jmkvpropedit/lib/*" ^
 *      ru.anseranser.jmkvpropedit.TrackManagementChecks
 * </pre>
 *
 * <p>
 * Exits with a non-zero status when any check fails.
 * </p>
 */
public final class TrackManagementChecks {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        // The constructor calls parseFiles(argsArray); main() normally sets it first.
        setStatic("argsArray", new String[0]);

        for (String type : new String[] { "Video", "Audio", "Subtitle" }) {
            checkOptVariantSubstitutesNumAndKeepsOptEscaping(type);
        }

        for (String type : new String[] { "Video", "Audio", "Subtitle" }) {
            checkRemoveDeletesSelectedTrack(type);
        }

        for (String type : new String[] { "Video", "Audio", "Subtitle" }) {
            checkRemoveFirstTrackAndSingleTrackButtonState(type);
        }

        System.out.println();
        System.out.println("passed=" + passed + ", failed=" + failed);
        System.exit(failed == 0 ? 0 : 1);
    }

    /**
     * AC1: the Opt variant of the command applies its own {@code {num}}
     * substitution and keeps Opt-specific escaping (escapeName), while the
     * plain variant keeps plain escaping (escapeQuotes).
     */
    private static void checkOptVariantSubstitutesNumAndKeepsOptEscaping(String type) throws Exception {
        JMkvpropedit w = newWindow();
        call(w, "add" + type + "Track");

        JCheckBox edit = (JCheckBox) atIndex(w, "chbEdit" + type, 0);
        JCheckBox name = (JCheckBox) atIndex(w, "chbName" + type, 0);
        JCheckBox numb = (JCheckBox) atIndex(w, "chbNumb" + type, 0);
        JTextField txtName = (JTextField) atIndex(w, "txtName" + type, 0);
        JTextField start = (JTextField) atIndex(w, "txtNumbStart" + type, 0);
        JTextField pad = (JTextField) atIndex(w, "txtNumbPad" + type, 0);

        edit.setSelected(true);
        name.setSelected(true);
        numb.setSelected(true);
        // Distinguishes escapeQuotes (A\"B\{num}) from escapeName (A####escaped__quotes#####B\\{num}).
        txtName.setText("A\"B\\{num}");
        start.setText("7");
        pad.setText("2");

        @SuppressWarnings("unchecked")
        DefaultListModel<String> modelFiles = (DefaultListModel<String>) get(w, "modelFiles");
        modelFiles.addElement("C:\\clips\\first.mkv");
        modelFiles.addElement("C:\\clips\\second.mkv");

        call(w, "setCmdLine" + type);

        String selector = selector(type);
        String[] plain = (String[]) get(w, "cmdLine" + type);
        String[] opt = (String[]) get(w, "cmdLine" + type + "Opt");

        String plainExpected0 = "--edit track:" + selector + "1 --set name=\"A\\\"B\\07\"";
        String optExpected0 = "--edit track:" + selector
                + "1 --set name=\"A####escaped__quotes#####B\\\\07\"";
        String optExpected1 = "--edit track:" + selector
                + "1 --set name=\"A####escaped__quotes#####B\\\\08\"";

        check(type + ": plain variant numbering and escaping", plain[0].contains(plainExpected0),
                "got: " + plain[0]);
        check(type + ": opt variant contains {num} substitution", opt[0].contains("B\\\\07"),
                "got: " + opt[0]);
        check(type + ": opt variant keeps opt escaping", opt[0].contains(optExpected0),
                "got: " + opt[0]);
        check(type + ": opt variant numbering follows the file index", opt[1].contains(optExpected1),
                "got: " + opt[1]);
        check(type + ": plain escaping does not leak into the opt variant", !opt[0].contains("A\\\"B"),
                "got: " + opt[0]);
    }

    /**
     * AC2: Remove deletes the selected combo element (any position), shifting
     * combo labels, card names and slot arrays so all three stay aligned.
     */
    private static void checkRemoveDeletesSelectedTrack(String type) throws Exception {
        JMkvpropedit w = newWindow();
        call(w, "add" + type + "Track"); // startup-style first track (Remove stays disabled)
        fire(button(w, "btnAdd" + type));
        fire(button(w, "btnAdd" + type)); // 3 tracks, Remove enabled

        JTextField[] names = (JTextField[]) get(w, "txtName" + type);
        names[0].setText("slot0");
        names[1].setText("slot1");
        names[2].setText("slot2");

        @SuppressWarnings("unchecked")
        JComboBox<String> combo = (JComboBox<String>) get(w, "cb" + type);
        combo.setSelectedIndex(1); // select the MIDDLE track

        fire(button(w, "btnRemove" + type));

        check(type + ": removing the selected track shrinks the combo", combo.getItemCount() == 2,
                "count=" + combo.getItemCount());
        check(type + ": removing the selected track shrinks the card panel", cardPanel(w, type).getComponentCount() == 2,
                "cards=" + cardPanel(w, type).getComponentCount());
        check(type + ": track counter follows the combo", trackCount(w, type) == combo.getItemCount(),
                "n=" + trackCount(w, type) + ", combo=" + combo.getItemCount());
        check(type + ": the selected track is gone (slots shift left)",
                "slot0".equals(names[0].getText()) && "slot2".equals(names[1].getText()),
                "slots=" + names[0].getText() + "," + names[1].getText());
        check(type + ": combo labels are renumbered",
                "Track 1".equals(labelTail(combo.getItemAt(0))) && "Track 2".equals(labelTail(combo.getItemAt(1))),
                "labels=" + combo.getItemAt(0) + "," + combo.getItemAt(1));

        // Cross-reference both sides: card name -> component, slot component -> card.
        JPanel[] subPanels = (JPanel[]) get(w, "subPnl" + type);
        cardLayout(w, type).show(cardPanel(w, type), "subPnl" + type + "[1]");
        check(type + ": card name maps to the shifted slot component", subPanels[1].isVisible() && !subPanels[0].isVisible(),
                "visible=" + subPanels[0].isVisible() + "," + subPanels[1].isVisible());
        check(type + ": remove stays enabled while tracks remain", button(w, "btnRemove" + type).isEnabled(),
                "enabled=false");
    }

    /**
     * AC3: the first track is removed when more than one track exists; with a
     * single track the Remove button is disabled and a stale click is a no-op.
     */
    private static void checkRemoveFirstTrackAndSingleTrackButtonState(String type) throws Exception {
        JMkvpropedit w = newWindow();
        call(w, "add" + type + "Track");
        AbstractButton remove = button(w, "btnRemove" + type);

        check(type + ": remove disabled with the startup single track", !remove.isEnabled(), "enabled=true");

        fire(button(w, "btnAdd" + type)); // 1 -> 2 tracks, app enables Remove
        check(type + ": remove enabled once a second track is added", remove.isEnabled(), "enabled=false");

        JTextField[] names = (JTextField[]) get(w, "txtName" + type);
        names[0].setText("slot0");
        names[1].setText("slot1");

        @SuppressWarnings("unchecked")
        JComboBox<String> combo = (JComboBox<String>) get(w, "cb" + type);
        combo.setSelectedIndex(0); // select the FIRST track

        fire(remove);

        check(type + ": first track is removable when more than one exists", combo.getItemCount() == 1,
                "count=" + combo.getItemCount());
        check(type + ": first-track removal shifts the survivor into slot 0",
                trackCount(w, type) == 1 && "slot1".equals(names[0].getText()),
                "n=" + trackCount(w, type) + ", slot0=" + names[0].getText());
        check(type + ": remove disabled with a single track left", !remove.isEnabled(), "enabled=true");

        fire(remove); // stale click while disabled must not go below one track
        check(type + ": stale remove click keeps the last track", combo.getItemCount() == 1 && trackCount(w, type) == 1,
                "count=" + combo.getItemCount() + ", n=" + trackCount(w, type));
    }

    /* Harness plumbing */

    private static JMkvpropedit newWindow() throws Exception {
        return new JMkvpropedit();
    }

    private static String selector(String type) {
        switch (type) {
        case "Video":
            return "v";
        case "Audio":
            return "a";
        default:
            return "s";
        }
    }

    private static String labelTail(String label) {
        return label.substring(label.indexOf(' ') + 1);
    }

    private static int trackCount(JMkvpropedit w, String type) throws Exception {
        return ((Integer) get(w, "n" + type)).intValue();
    }

    private static JPanel cardPanel(JMkvpropedit w, String type) throws Exception {
        return (JPanel) get(w, "lyrdPnl" + type);
    }

    private static CardLayout cardLayout(JMkvpropedit w, String type) throws Exception {
        return (CardLayout) get(w, "lytLyrdPnl" + type);
    }

    private static AbstractButton button(JMkvpropedit w, String name) throws Exception {
        return (AbstractButton) get(w, name);
    }

    private static void fire(AbstractButton button) {
        ActionEvent e = new ActionEvent(button, ActionEvent.ACTION_PERFORMED, button.getActionCommand());
        for (ActionListener listener : button.getActionListeners()) {
            listener.actionPerformed(e);
        }
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = JMkvpropedit.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object atIndex(Object target, String arrayName, int index) throws Exception {
        return java.lang.reflect.Array.get(get(target, arrayName), index);
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
