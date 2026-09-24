package ru.anseranser.jmkvpropedit;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.swing.AbstractButton;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JTextField;

/**
 * Framework-free verification harness for issue #3 (track numbering in the Opt
 * command variant and removal of the selected track), re-pointed at the unified
 * {@link TrackPanel} from issue #12.
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

        for (TrackType type : TrackType.values()) {
            checkOptVariantSubstitutesNumAndKeepsOptEscaping(type);
        }

        for (TrackType type : TrackType.values()) {
            checkRemoveDeletesSelectedTrack(type);
        }

        for (TrackType type : TrackType.values()) {
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
    private static void checkOptVariantSubstitutesNumAndKeepsOptEscaping(TrackType type) throws Exception {
        JMkvpropedit w = newWindow();
        TrackPanel panel = panel(w, type);
        panel.addTrack();
        TrackSlot slot = panel.slots().get(0);

        slot.chbEdit.setSelected(true);
        slot.chbName.setSelected(true);
        slot.chbNumb.setSelected(true);
        // Distinguishes escapeQuotes (A\"B\{num}) from escapeName (A####escaped__quotes#####B\\{num}).
        slot.txtName.setText("A\"B\\{num}");
        slot.txtNumbStart.setText("7");
        slot.txtNumbPad.setText("2");

        @SuppressWarnings("unchecked")
        DefaultListModel<String> modelFiles = (DefaultListModel<String>) get(w, "modelFiles");
        modelFiles.addElement("C:\\clips\\first.mkv");
        modelFiles.addElement("C:\\clips\\second.mkv");

        call(w, "setCmdLine" + type.label());

        String selector = String.valueOf(type.selector());
        String[] plain = (String[]) get(w, "cmdLine" + type.label());
        String[] opt = (String[]) get(w, "cmdLine" + type.label() + "Opt");

        String plainExpected0 = "--edit track:" + selector + "1 --set name=\"A\\\"B\\07\"";
        String optExpected0 = "--edit track:" + selector
                + "1 --set name=\"A####escaped__quotes#####B\\\\07\"";
        String optExpected1 = "--edit track:" + selector
                + "1 --set name=\"A####escaped__quotes#####B\\\\08\"";

        check(type.label() + ": plain variant numbering and escaping", plain[0].contains(plainExpected0),
                "got: " + plain[0]);
        check(type.label() + ": opt variant contains {num} substitution", opt[0].contains("B\\\\07"),
                "got: " + opt[0]);
        check(type.label() + ": opt variant keeps opt escaping", opt[0].contains(optExpected0),
                "got: " + opt[0]);
        check(type.label() + ": opt variant numbering follows the file index", opt[1].contains(optExpected1),
                "got: " + opt[1]);
        check(type.label() + ": plain escaping does not leak into the opt variant", !opt[0].contains("A\\\"B"),
                "got: " + opt[0]);
    }

    /**
     * AC2: Remove deletes the selected combo element (any position), shifting
     * combo labels and slot cards so all stay aligned.
     */
    private static void checkRemoveDeletesSelectedTrack(TrackType type) throws Exception {
        JMkvpropedit w = newWindow();
        TrackPanel panel = panel(w, type);
        panel.addTrack(); // startup-style first track (Remove stays disabled)
        fire(panel.addButton());
        fire(panel.addButton()); // 3 tracks, Remove enabled

        panel.slots().get(0).txtName.setText("slot0");
        panel.slots().get(1).txtName.setText("slot1");
        panel.slots().get(2).txtName.setText("slot2");

        JComboBox<String> combo = panel.combo();
        combo.setSelectedIndex(1); // select the MIDDLE track

        fire(panel.removeButton());

        check(type.label() + ": removing the selected track shrinks the combo", combo.getItemCount() == 2,
                "count=" + combo.getItemCount());
        check(type.label() + ": removing the selected track shrinks the card panel",
                panel.cards().getComponentCount() == 2,
                "cards=" + panel.cards().getComponentCount());
        check(type.label() + ": track counter follows the combo",
                panel.trackCount() == combo.getItemCount(),
                "n=" + panel.trackCount() + ", combo=" + combo.getItemCount());
        check(type.label() + ": the selected track is gone (slots shift left)",
                "slot0".equals(panel.slots().get(0).txtName.getText())
                        && "slot2".equals(panel.slots().get(1).txtName.getText()),
                "slots=" + panel.slots().get(0).txtName.getText() + ","
                        + panel.slots().get(1).txtName.getText());
        check(type.label() + ": combo labels are renumbered",
                "Track 1".equals(labelTail(combo.getItemAt(0))) && "Track 2".equals(labelTail(combo.getItemAt(1))),
                "labels=" + combo.getItemAt(0) + "," + combo.getItemAt(1));

        // Cross-reference both sides: card name -> slot component.
        panel.cardLayout().show(panel.cards(), type.cardPrefix() + "[1]");
        check(type.label() + ": card name maps to the shifted slot component",
                panel.slots().get(1).card.isVisible() && !panel.slots().get(0).card.isVisible(),
                "visible=" + panel.slots().get(0).card.isVisible() + ","
                        + panel.slots().get(1).card.isVisible());
        check(type.label() + ": remove stays enabled while tracks remain",
                panel.removeButton().isEnabled(),
                "enabled=false");
    }

    /**
     * AC3: the first track is removed when more than one track exists; with a
     * single track the Remove button is disabled and a stale click is a no-op.
     */
    private static void checkRemoveFirstTrackAndSingleTrackButtonState(TrackType type) throws Exception {
        JMkvpropedit w = newWindow();
        TrackPanel panel = panel(w, type);
        panel.addTrack();
        AbstractButton remove = panel.removeButton();

        check(type.label() + ": remove disabled with the startup single track", !remove.isEnabled(),
                "enabled=true");

        fire(panel.addButton()); // 1 -> 2 tracks, app enables Remove
        check(type.label() + ": remove enabled once a second track is added", remove.isEnabled(),
                "enabled=false");

        panel.slots().get(0).txtName.setText("slot0");
        panel.slots().get(1).txtName.setText("slot1");

        JComboBox<String> combo = panel.combo();
        combo.setSelectedIndex(0); // select the FIRST track

        fire(remove);

        check(type.label() + ": first track is removable when more than one exists", combo.getItemCount() == 1,
                "count=" + combo.getItemCount());
        check(type.label() + ": first-track removal shifts the survivor into slot 0",
                panel.trackCount() == 1 && "slot1".equals(panel.slots().get(0).txtName.getText()),
                "n=" + panel.trackCount() + ", slot0=" + panel.slots().get(0).txtName.getText());
        check(type.label() + ": remove disabled with a single track left", !remove.isEnabled(),
                "enabled=true");

        fire(remove); // stale click while disabled must not go below one track
        check(type.label() + ": stale remove click keeps the last track",
                combo.getItemCount() == 1 && panel.trackCount() == 1,
                "count=" + combo.getItemCount() + ", n=" + panel.trackCount());
    }

    /* Harness plumbing */

    private static JMkvpropedit newWindow() throws Exception {
        return new JMkvpropedit();
    }

    /** Reflects the god class's unified track panel field for a track type. */
    private static TrackPanel panel(JMkvpropedit w, TrackType type) throws Exception {
        String label = type.label();
        String fieldName = Character.toLowerCase(label.charAt(0)) + label.substring(1) + "Panel";
        return (TrackPanel) get(w, fieldName);
    }

    private static String labelTail(String label) {
        return label.substring(label.indexOf(' ') + 1);
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
