package ru.anseranser.jmkvpropedit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.filechooser.FileFilter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Characterization tests for the god class's UI wiring — issue #16 (final
 * integration).
 *
 * <p>
 * Before the final cleanup collapses {@link JMkvpropedit} into pure UI
 * shell (window, tabs, wiring, module calls), these tests freeze the
 * behaviors the cleanup must preserve: the General tab's title/numbering
 * enable chains, the chapters/tags source sections (including their
 * unchecked-with-mode quirk and per-section default texts), the txt/xml
 * chooser setup, the file-list extension gate and the widget-to-batch
 * command generation path. They mirror the framework-free harness checks so
 * {@code gradlew build} itself guards the refactor.
 * </p>
 *
 * <p>
 * The god class builds a {@link JFrame}, so the whole class skips on
 * headless JVMs (CI) via a {@code BeforeAll} assumption; everything below
 * the window layer stays covered by the module unit tests.
 * </p>
 */
class JMkvpropeditWiringTest {

    private JMkvpropedit window;

    @BeforeAll
    static void seedCommandLineAndRequireDisplay() throws Exception {
        // The constructor parses the static args array; main() sets it first.
        Field args = JMkvpropedit.class.getDeclaredField("argsArray");
        args.setAccessible(true);
        args.set(null, new String[0]);

        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(),
                "JMkvpropedit builds a JFrame; skip on headless JVMs");
    }

    @AfterEach
    void disposeWindow() throws Exception {
        if (window != null) {
            JFrame frame = get(window, "frmJMkvpropedit");
            frame.dispose();
            window = null;
        }
    }

    @Test
    void titleCheckboxTogglesTheTitleAndNumberingControls() throws Exception {
        window = newWindow();
        JCheckBox chbTitle = get(window, "chbTitleGeneral");
        JTextField txtTitle = get(window, "txtTitleGeneral");
        JCheckBox chbNumb = get(window, "chbNumbGeneral");

        assertFalse(txtTitle.isEnabled());
        assertFalse(chbNumb.isEnabled());

        click(chbTitle);
        assertTrue(txtTitle.isEnabled());
        assertTrue(chbNumb.isEnabled());

        click(chbTitle);
        assertFalse(txtTitle.isEnabled());
        assertFalse(chbNumb.isEnabled());
    }

    @Test
    void numberingCheckboxTogglesTheNumberingFields() throws Exception {
        window = newWindow();
        JCheckBox chbTitle = get(window, "chbTitleGeneral");
        JCheckBox chbNumb = get(window, "chbNumbGeneral");
        click(chbTitle); // the numbering checkbox itself is gated by the title

        JLabel lblStart = get(window, "lblNumbStartGeneral");
        JTextField txtStart = get(window, "txtNumbStartGeneral");
        JLabel lblPad = get(window, "lblNumbPadGeneral");
        JTextField txtPad = get(window, "txtNumbPadGeneral");
        JLabel lblExplain = get(window, "lblNumbExplainGeneral");

        click(chbNumb);

        assertTrue(lblStart.isEnabled());
        assertTrue(txtStart.isEnabled());
        assertTrue(lblPad.isEnabled());
        assertTrue(txtPad.isEnabled());
        assertTrue(lblExplain.isEnabled());

        click(chbNumb);

        assertFalse(lblStart.isEnabled());
        assertFalse(txtStart.isEnabled());
        assertFalse(lblPad.isEnabled());
        assertFalse(txtPad.isEnabled());
        assertFalse(lblExplain.isEnabled());
    }

    @Test
    void numberingFieldsResetInvalidValuesOnFocusLost() throws Exception {
        window = newWindow();
        JTextField start = get(window, "txtNumbStartGeneral");
        JTextField pad = get(window, "txtNumbPadGeneral");

        start.setText("-1");
        fireFocusLost(start);
        assertEquals("1", start.getText());

        start.setText("abc");
        fireFocusLost(start);
        assertEquals("1", start.getText());

        pad.setText("-3");
        fireFocusLost(pad);
        assertEquals("1", pad.getText());

        pad.setText("2");
        fireFocusLost(pad);
        assertEquals("2", pad.getText());
    }

    @Test
    void chaptersSectionWiresItsThreeModes() throws Exception {
        window = newWindow();
        JCheckBox toggle = get(window, "chbChapters");
        JComboBox<String> mode = get(window, "cbChapters");
        JTextField text = get(window, "txtChapters");
        JButton browse = get(window, "btnBrowseChapters");
        JComboBox<String> extension = get(window, "cbExtChapters");

        assertFalse(mode.isEnabled());
        assertFalse(text.isVisible());
        assertFalse(browse.isVisible());
        assertFalse(extension.isVisible());

        click(toggle);
        assertTrue(mode.isEnabled());
        // mode 0 (Remove): nothing to fill in, everything stays hidden
        assertFalse(text.isVisible());
        assertFalse(browse.isVisible());
        assertFalse(extension.isVisible());

        mode.setSelectedIndex(1); // From file: — fires the combo's listener
        assertTrue(text.isVisible());
        assertFalse(text.isEditable());
        assertTrue(browse.isVisible());
        assertFalse(extension.isVisible());
        assertEquals("", text.getText());

        mode.setSelectedIndex(2); // Match file name with suffix:
        assertTrue(text.isVisible());
        assertTrue(text.isEditable());
        assertEquals("-chapters", text.getText());
        assertFalse(browse.isVisible());
        assertTrue(extension.isVisible());

        mode.setSelectedIndex(0);
        assertFalse(text.isVisible());
        assertFalse(browse.isVisible());
        assertFalse(extension.isVisible());
    }

    @Test
    void uncheckingChaptersInFromFileModeKeepsTheRowVisibleButDisabled() throws Exception {
        window = newWindow();
        JCheckBox toggle = get(window, "chbChapters");
        JComboBox<String> mode = get(window, "cbChapters");
        JTextField text = get(window, "txtChapters");
        JButton browse = get(window, "btnBrowseChapters");

        click(toggle);
        mode.setSelectedIndex(1);
        click(toggle); // uncheck while mode 1 is active

        assertFalse(mode.isEnabled());
        // Preserved quirk: the mode branch wins over the "hide when off" path.
        assertTrue(text.isVisible());
        assertFalse(text.isEnabled());
        assertTrue(browse.isVisible());
        assertFalse(browse.isEnabled());
    }

    @Test
    void tagsSectionMirrorsChaptersWithItsOwnDefaultText() throws Exception {
        window = newWindow();
        JCheckBox toggle = get(window, "chbTags");
        JComboBox<String> mode = get(window, "cbTags");
        JTextField text = get(window, "txtTags");
        JButton browse = get(window, "btnBrowseTags");
        JComboBox<String> extension = get(window, "cbExtTags");

        assertFalse(mode.isEnabled());
        click(toggle);
        assertTrue(mode.isEnabled());

        mode.setSelectedIndex(2);
        assertEquals("-tags", text.getText());
        assertTrue(text.isEditable());
        assertTrue(extension.isVisible());
        assertFalse(browse.isVisible());

        mode.setSelectedIndex(1);
        assertEquals("", text.getText());
        assertFalse(text.isEditable());
        assertTrue(browse.isVisible());
        assertFalse(extension.isVisible());

        click(toggle); // uncheck: mode 1 keeps the row visible but disabled
        assertFalse(mode.isEnabled());
        assertTrue(text.isVisible());
        assertFalse(text.isEnabled());
    }

    @Test
    void textFileChooserExposesBothFiltersWithXmlSelected() throws Exception {
        window = newWindow();
        JFileChooser chooser = new JFileChooser();
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.resetChoosableFileFilters();

        Method configure = JMkvpropedit.class.getDeclaredMethod(
                "configureTextFileChooser", JFileChooser.class, String.class);
        configure.setAccessible(true);
        configure.invoke(window, chooser, "Select chapters file");

        List<String> descriptions = new java.util.ArrayList<>();
        for (FileFilter filter : chooser.getChoosableFileFilters()) {
            descriptions.add(filter.getDescription());
        }

        assertTrue(descriptions.stream().anyMatch(d -> d.contains("*.txt")), descriptions::toString);
        assertTrue(descriptions.stream().anyMatch(d -> d.contains("*.xml")), descriptions::toString);
        assertTrue(chooser.getFileFilter().getDescription().contains("*.xml"),
                chooser.getFileFilter().getDescription());
        assertEquals("Select chapters file", chooser.getDialogTitle());
    }

    @Test
    void fileListAcceptsMatroskaFilesOnlyWhenTheExtensionIsChecked() throws Exception {
        window = newWindow();
        DefaultListModel<String> model = get(window, "modelFiles");
        model.clear();

        call(window, "addFile", new File("clips/one.txt"), true);
        assertEquals(0, model.size(), model::toString);

        call(window, "addFile", new File("clips/two.mkv"), true);
        call(window, "addFile", new File("clips/three.MKV"), true);
        assertEquals(2, model.size(), model::toString);

        call(window, "addFile", new File("clips/notes.txt"), false);
        assertEquals(3, model.size(), model::toString);
    }

    @Test
    void commandGenerationWiresGeneralAndTrackSettingsIntoTheBatch() throws Exception {
        window = newWindow();

        @SuppressWarnings("unchecked")
        DefaultListModel<String> modelFiles = (DefaultListModel<String>) get(window, "modelFiles");
        modelFiles.addElement("C:\\clips\\first.mkv");

        JCheckBox chbTitle = get(window, "chbTitleGeneral");
        JTextField txtTitle = get(window, "txtTitleGeneral");
        click(chbTitle);
        txtTitle.setText("My Title");

        TrackPanel videoPanel = get(window, "videoPanel");
        videoPanel.addTrack();
        TrackSlot slot = videoPanel.slots().get(0);
        slot.chbEdit.setSelected(true);
        slot.chbName.setSelected(true);
        slot.txtName.setText("TrackName");
        slot.txtNumbStart.setText("1");
        slot.txtNumbPad.setText("1");

        call(window, "setCmdLine");

        @SuppressWarnings("unchecked")
        List<String> batch = (List<String>) get(window, "cmdLineBatch");
        assertEquals(1, batch.size(), batch::toString);
        String line = batch.get(0);
        assertTrue(line.contains("\"C:\\clips\\first.mkv\""), line);
        assertTrue(line.contains("--edit info"), line);
        assertTrue(line.contains("--set title=\"My Title\""), line);
        assertTrue(line.contains("--edit track:v1"), line);
        assertTrue(line.contains("--set name=\"TrackName\""), line);
    }

    /* Test plumbing */

    private static JMkvpropedit newWindow() throws Exception {
        return new JMkvpropedit();
    }

    /** Toggles the button like a click would, then fires its action listeners. */
    private static void click(AbstractButton button) {
        button.setSelected(!button.isSelected());
        ActionEvent e = new ActionEvent(button, ActionEvent.ACTION_PERFORMED, button.getActionCommand());

        for (ActionListener listener : button.getActionListeners()) {
            listener.actionPerformed(e);
        }
    }

    private static void fireFocusLost(JTextField field) {
        FocusEvent e = new FocusEvent(field, FocusEvent.FOCUS_LOST);

        for (FocusListener listener : field.getFocusListeners()) {
            listener.focusLost(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T get(Object target, String name) throws Exception {
        Field field = JMkvpropedit.class.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(target);
    }

    private static Object call(Object target, String name, Object... args) throws Exception {
        Class<?>[] types = new Class<?>[args.length];

        for (int i = 0; i < args.length; i++) {
            Class<?> type = args[i].getClass();

            if (type == Boolean.class) {
                type = boolean.class;
            } else if (type == Integer.class) {
                type = int.class;
            }

            types[i] = type;
        }

        Method method = JMkvpropedit.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }
}
