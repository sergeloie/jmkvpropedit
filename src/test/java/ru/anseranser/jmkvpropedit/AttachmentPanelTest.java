package ru.anseranser.jmkvpropedit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Unit tests for the unified attachment panel — issue #13.
 *
 * <p>
 * Add, replace and delete share one {@link AttachmentPanel} built from an
 * {@link AttachmentOperation} and an {@link AttachmentModel}; the triplet of
 * near-identical copies in {@link JMkvpropedit} is gone. These tests pin the
 * behaviors the original copies shared and the quirks they differed by:
 * validation messages, trim semantics per field, the selector radio dance,
 * the edit-mode button state machine, the MIME combos (issue #4) and the
 * command-line fragments all three operations used to assemble inside the
 * god class.
 * </p>
 */
class AttachmentPanelTest {

    private final MkvStrings mkvStrings = new MkvStrings();
    private final List<String> errors = new ArrayList<>();

    /* Construction */

    @ParameterizedTest
    @EnumSource(AttachmentOperation.class)
    void panelStartsWithAnEmptyTableAndOnlyAddEnabled(AttachmentOperation operation) {
        AttachmentPanel panel = panel(operation);

        assertEquals(operation, panel.operation());
        assertArrayEquals(operation.columns(), columnNames(panel));
        assertEquals(0, panel.model().getRowCount());
        assertBrowseMode(panel);
    }

    /* MIME combos (issue #4 behavior must survive the extraction) */

    @Test
    void allFourMimeCombosShareTheFreshResourceListWithoutTheArtifact() {
        List<String> before = new ArrayList<>(mkvStrings.getMimeTypeList());

        AttachmentPanel add = panel(AttachmentOperation.ADD);
        AttachmentPanel replace = panel(AttachmentOperation.REPLACE);
        AttachmentPanel delete = panel(AttachmentOperation.DELETE);

        List<String> addMime = comboItems(add.mimeCombo());
        List<String> replaceOriginal = comboItems(replace.originalCombo());
        List<String> replaceMime = comboItems(replace.mimeCombo());
        List<String> deleteValue = comboItems(delete.valueCombo());

        assertFalse(addMime.contains("_"), "corrupted mimetypes.txt artifact leaked into the combo");
        assertEquals("", addMime.get(0), "combos keep the leading empty item (auto-detect)");
        assertEquals(addMime, replaceOriginal);
        assertEquals(addMime, replaceMime);
        assertEquals(addMime, deleteValue);

        // The shared MkvStrings list must not be mutated by construction.
        assertEquals(before, new ArrayList<>(mkvStrings.getMimeTypeList()));
        // Every real mime made it in: 1 empty + the list minus empty/"_" artifacts.
        long real = before.stream().filter(m -> !m.isEmpty() && !"_".equals(m)).count();
        assertEquals(real + 1, addMime.size());
    }

    /* Add operation */

    @Test
    void addRejectsAnEmptyFileWithTheFormerMessage() {
        AttachmentPanel add = panel(AttachmentOperation.ADD);

        click(add.addButton());

        assertEquals(List.of("The file is mandatory for the attachment!"), errors);
        assertEquals(0, add.model().getRowCount());
    }

    @Test
    void addRowKeepsTheFileRawButTrimsNameAndDescriptionThenResetsTheFields() {
        AttachmentPanel add = panel(AttachmentOperation.ADD);

        add.fileField().setText("  C:\\cover.png  ");
        add.nameField().setText("  cover.png  ");
        add.descriptionField().setText("  Cover  ");
        add.mimeCombo().setSelectedIndex(1);
        String mime = add.mimeCombo().getSelectedItem().toString();

        click(add.addButton());

        assertArrayEquals(new String[] { "  C:\\cover.png  ", "cover.png", "Cover", mime },
                add.model().rows().get(0));
        assertEquals("", add.fileField().getText());
        assertEquals("", add.nameField().getText());
        assertEquals("", add.descriptionField().getText());
        assertEquals(0, add.mimeCombo().getSelectedIndex());
        assertTrue(errors.isEmpty());
    }

    @Test
    void clickingARowLoadsItIntoTheControlsAndEntersEditMode() {
        AttachmentPanel add = panel(AttachmentOperation.ADD);
        add.model().addRow(new String[] { "C:\\a.png", "a.png", "A", "image/png" });

        pressRow(add, 0);

        assertEditMode(add);
        assertEquals("C:\\a.png", add.fileField().getText());
        assertEquals("a.png", add.nameField().getText());
        assertEquals("A", add.descriptionField().getText());
        assertEquals("image/png", add.mimeCombo().getSelectedItem());
    }

    @Test
    void clickingTheTableWhileEditModeIsActiveIsIgnored() {
        AttachmentPanel add = panel(AttachmentOperation.ADD);
        add.model().addRow(new String[] { "C:\\a.png", "a.png", "", "" });
        add.model().addRow(new String[] { "C:\\b.png", "b.png", "", "" });

        pressRow(add, 0);
        // Table is disabled in edit mode: the guard must swallow the press.
        pressRow(add, 1);

        assertEditMode(add);
        assertEquals("C:\\a.png", add.fileField().getText());
    }

    @Test
    void editingTheSelectedRowTrimsTheFileAndLeavesEditMode() {
        AttachmentPanel add = panel(AttachmentOperation.ADD);
        add.model().addRow(new String[] { "C:\\a.png", "a.png", "", "" });
        pressRow(add, 0);

        add.fileField().setText("  C:\\b.png  ");
        add.nameField().setText("  b.png  ");
        add.descriptionField().setText("  B  ");
        click(add.editButton());

        assertArrayEquals(new String[] { "C:\\b.png", "b.png", "B", "" },
                add.model().rows().get(0));
        assertBrowseMode(add);
        assertEquals(-1, add.table().getSelectedRow());
        assertEquals("", add.fileField().getText());
        assertEquals("", add.nameField().getText());
        assertEquals("", add.descriptionField().getText());
        assertTrue(errors.isEmpty());
    }

    @Test
    void editRejectsAnEmptyFileAndStaysInEditMode() {
        AttachmentPanel add = panel(AttachmentOperation.ADD);
        add.model().addRow(new String[] { "C:\\a.png", "a.png", "", "" });
        pressRow(add, 0);

        add.fileField().setText("   ");
        click(add.editButton());

        assertEquals(List.of("The file is mandatory for the attachment!"), errors);
        assertArrayEquals(new String[] { "C:\\a.png", "a.png", "", "" },
                add.model().rows().get(0));
        assertEditMode(add);
    }

    @Test
    void removingTheSelectedRowResetsControlsAndLeavesEditMode() {
        AttachmentPanel add = panel(AttachmentOperation.ADD);
        add.model().addRow(new String[] { "C:\\a.png", "a.png", "", "" });
        pressRow(add, 0);

        click(add.removeButton());

        assertEquals(0, add.model().getRowCount());
        assertBrowseMode(add);
        assertEquals("", add.fileField().getText());
        assertEquals("", add.nameField().getText());
        assertEquals(0, add.mimeCombo().getSelectedIndex());
    }

    @Test
    void cancelLeavesEditModeWithoutChangingTheRow() {
        AttachmentPanel add = panel(AttachmentOperation.ADD);
        add.model().addRow(new String[] { "C:\\a.png", "a.png", "", "" });
        pressRow(add, 0);

        add.fileField().setText("C:\\other.png");
        click(add.cancelButton());

        assertArrayEquals(new String[] { "C:\\a.png", "a.png", "", "" },
                add.model().rows().get(0));
        assertBrowseMode(add);
        assertEquals(-1, add.table().getSelectedRow());
        assertEquals("", add.fileField().getText());
    }

    /* Replace operation */

    @Test
    void selectorRadiosSwapTheOriginalValueFieldForTheMimeCombo() {
        AttachmentPanel replace = panel(AttachmentOperation.REPLACE);

        assertTrue(replace.originalField().isVisible());
        assertFalse(replace.originalCombo().isVisible());
        assertEquals("", replace.originalField().getText());

        click(replace.idSelector());
        assertTrue(replace.originalField().isVisible());
        assertFalse(replace.originalCombo().isVisible());
        assertEquals("1", replace.originalField().getText());

        click(replace.mimeSelector());
        assertFalse(replace.originalField().isVisible());
        assertTrue(replace.originalCombo().isVisible());
        assertEquals(0, replace.originalCombo().getSelectedIndex());

        click(replace.nameSelector());
        assertTrue(replace.originalField().isVisible());
        assertFalse(replace.originalCombo().isVisible());
        assertEquals("", replace.originalField().getText());
    }

    @Test
    void replaceRejectsMissingOriginalOrReplacement() {
        AttachmentPanel replace = panel(AttachmentOperation.REPLACE);

        click(replace.addButton());
        assertEquals(List.of("The original value and replacement are mandatory for the attachment!"),
                errors);
        assertEquals(0, replace.model().getRowCount());

        errors.clear();
        replace.originalField().setText("cover");
        click(replace.addButton());
        assertEquals(List.of("The original value and replacement are mandatory for the attachment!"),
                errors);
        assertEquals(0, replace.model().getRowCount());
    }

    @Test
    void replaceRowPerSelectorKeepsTheExactTrimSemantics() {
        AttachmentPanel replace = panel(AttachmentOperation.REPLACE);
        replace.replacementField().setText("C:\\new.png");

        // Name selector: the original value is trimmed.
        click(replace.nameSelector());
        replace.originalField().setText("  cover  ");
        replace.nameField().setText("  cover2  ");
        replace.descriptionField().setText("  desc  ");
        click(replace.addButton());
        assertArrayEquals(new String[] { AttachmentSelector.NAME.label(), "cover", "C:\\new.png",
                "cover2", "desc", "" },
                replace.model().rows().get(0));

        // ID selector: the original value is stored raw, like the field text.
        click(replace.idSelector());
        replace.originalField().setText(" 3 ");
        replace.replacementField().setText("C:\\new2.png");
        click(replace.addButton());
        assertArrayEquals(new String[] { AttachmentSelector.ID.label(), " 3 ", "C:\\new2.png",
                "", "", "" },
                replace.model().rows().get(1));

        // MIME selector: the original value comes from the combo.
        click(replace.mimeSelector());
        String mime = replace.originalCombo().getItemAt(1);
        replace.originalCombo().setSelectedIndex(1);
        replace.replacementField().setText("C:\\new3.png");
        click(replace.addButton());
        assertArrayEquals(new String[] { AttachmentSelector.MIME_TYPE.label(), mime,
                "C:\\new3.png", "", "", "" },
                replace.model().rows().get(2));

        // Every successful add resets the controls back to the name selector.
        assertTrue(replace.nameSelector().isSelected());
        assertEquals("", replace.originalField().getText());
        assertEquals("", replace.replacementField().getText());
        assertTrue(replace.originalField().isVisible());
        assertFalse(replace.originalCombo().isVisible());
        assertTrue(errors.isEmpty());
    }

    @Test
    void selectingARowRestoresItsSelectorAndFields() {
        AttachmentPanel replace = panel(AttachmentOperation.REPLACE);
        replace.model().addRow(new String[] { AttachmentSelector.ID.label(), "7", "C:\\r.png",
                "name", "desc", "image/png" });

        pressRow(replace, 0);

        assertEditMode(replace);
        assertTrue(replace.idSelector().isSelected());
        assertTrue(replace.originalField().isVisible());
        assertFalse(replace.originalCombo().isVisible());
        assertEquals("7", replace.originalField().getText());
        assertEquals("C:\\r.png", replace.replacementField().getText());
        assertEquals("name", replace.nameField().getText());
        assertEquals("desc", replace.descriptionField().getText());
        assertEquals("image/png", replace.mimeCombo().getSelectedItem());
    }

    @Test
    void mimeRowSelectionKeepsTheFormerReplacementQuirk() {
        AttachmentPanel replace = panel(AttachmentOperation.REPLACE);
        // Replacement is a MIME present in the model: the original loaded the
        // REPLACEMENT (column 2) into the original-value combo, never the
        // original (column 1). Non-model values (file paths) are ignored by
        // the non-editable combo and the selection stays "" — same as before.
        replace.model().addRow(new String[] { AttachmentSelector.MIME_TYPE.label(), "image/png",
                "audio/mpeg", "n", "d", "image/png" });

        pressRow(replace, 0);

        assertTrue(replace.mimeSelector().isSelected());
        assertFalse(replace.originalField().isVisible());
        assertTrue(replace.originalCombo().isVisible());
        assertEquals("audio/mpeg", replace.originalCombo().getSelectedItem());
        assertEquals("audio/mpeg", replace.replacementField().getText());
        assertEquals("n", replace.nameField().getText());
    }

    @Test
    void replaceEditKeepsNameAndDescriptionRawThenLeavesEditMode() {
        AttachmentPanel replace = panel(AttachmentOperation.REPLACE);
        replace.model().addRow(new String[] { AttachmentSelector.NAME.label(), "cover",
                "C:\\old.png", "old", "", "" });
        pressRow(replace, 0);

        replace.nameField().setText("  spaced  ");
        replace.descriptionField().setText("  d  ");
        replace.replacementField().setText("C:\\new.png");
        click(replace.editButton());

        assertArrayEquals(new String[] { AttachmentSelector.NAME.label(), "cover", "C:\\new.png",
                "  spaced  ", "  d  ", "" },
                replace.model().rows().get(0));
        assertBrowseMode(replace);
        assertEquals(-1, replace.table().getSelectedRow());
        assertTrue(replace.nameSelector().isSelected());
        assertTrue(errors.isEmpty());
    }

    @Test
    void replaceRemoveAndCancelRestoreBrowseModeAndResetControls() {
        AttachmentPanel replace = panel(AttachmentOperation.REPLACE);
        replace.model().addRow(new String[] { AttachmentSelector.NAME.label(), "cover",
                "C:\\old.png", "old", "", "" });

        pressRow(replace, 0);
        click(replace.cancelButton());
        assertBrowseMode(replace);
        assertEquals(1, replace.model().getRowCount());
        assertEquals("", replace.replacementField().getText());
        assertEquals("", replace.originalField().getText());

        pressRow(replace, 0);
        click(replace.removeButton());
        assertEquals(0, replace.model().getRowCount());
        assertBrowseMode(replace);
        assertTrue(replace.nameSelector().isSelected());
    }

    @Test
    void replaceKeepsAnExistingSelectionWhenAddingARow() {
        AttachmentPanel replace = panel(AttachmentOperation.REPLACE);
        replace.model().addRow(new String[] { AttachmentSelector.NAME.label(), "cover",
                "C:\\old.png", "", "", "" });
        replace.table().setRowSelectionInterval(0, 0);

        replace.originalField().setText("cover");
        replace.replacementField().setText("C:\\new.png");
        click(replace.addButton());

        assertEquals(2, replace.model().getRowCount());
        assertEquals(0, replace.table().getSelectedRow());
    }

    @Test
    void replaceIdFieldClampsInvalidValuesOnFocusLost() {
        AttachmentPanel replace = panel(AttachmentOperation.REPLACE);
        click(replace.idSelector());

        replace.originalField().setText("0");
        fireFocusLost(replace.originalField());
        assertEquals("1", replace.originalField().getText());

        replace.originalField().setText("abc");
        fireFocusLost(replace.originalField());
        assertEquals("1", replace.originalField().getText());

        replace.originalField().setText("5");
        fireFocusLost(replace.originalField());
        assertEquals("5", replace.originalField().getText());

        // Not the ID selector: the field is left alone.
        click(replace.nameSelector());
        replace.originalField().setText("0");
        fireFocusLost(replace.originalField());
        assertEquals("0", replace.originalField().getText());
    }

    /* Delete operation */

    @Test
    void deleteRejectsAnEmptyValueWithTheFormerMessage() {
        AttachmentPanel delete = panel(AttachmentOperation.DELETE);

        click(delete.addButton());

        assertEquals(List.of("The value is mandatory for the attachment!"), errors);
        assertEquals(0, delete.model().getRowCount());
    }

    @Test
    void deleteRowPerSelectorKeepsTheExactTrimSemanticsAndClearsSelection() {
        AttachmentPanel delete = panel(AttachmentOperation.DELETE);
        delete.model().addRow(new String[] { AttachmentSelector.NAME.label(), "first" });
        delete.table().setRowSelectionInterval(0, 0);

        // Name: trimmed.
        delete.valueField().setText("  cover  ");
        click(delete.addButton());
        assertArrayEquals(new String[] { AttachmentSelector.NAME.label(), "cover" },
                delete.model().rows().get(1));

        // Unlike add/replace, a successful delete-add clears the table selection.
        assertEquals(-1, delete.table().getSelectedRow());

        // ID: raw field text.
        click(delete.idSelector());
        delete.valueField().setText(" 3 ");
        click(delete.addButton());
        assertArrayEquals(new String[] { AttachmentSelector.ID.label(), " 3 " },
                delete.model().rows().get(2));

        // MIME: the combo value.
        click(delete.mimeSelector());
        String mime = delete.valueCombo().getItemAt(1);
        delete.valueCombo().setSelectedIndex(1);
        click(delete.addButton());
        assertArrayEquals(new String[] { AttachmentSelector.MIME_TYPE.label(), mime },
                delete.model().rows().get(3));

        // Back to the name selector with an empty value field.
        assertTrue(delete.nameSelector().isSelected());
        assertEquals("", delete.valueField().getText());
        assertTrue(delete.valueField().isVisible());
        assertFalse(delete.valueCombo().isVisible());
        assertTrue(errors.isEmpty());
    }

    @Test
    void selectingADeleteRowRestoresItsSelectorAndValue() {
        AttachmentPanel delete = panel(AttachmentOperation.DELETE);
        delete.model().addRow(new String[] { AttachmentSelector.ID.label(), "9" });

        pressRow(delete, 0);

        assertEditMode(delete);
        assertTrue(delete.idSelector().isSelected());
        assertTrue(delete.valueField().isVisible());
        assertFalse(delete.valueCombo().isVisible());
        assertEquals("9", delete.valueField().getText());

        delete.model().addRow(new String[] { AttachmentSelector.MIME_TYPE.label(), "image/png" });

        // While in edit mode the table is disabled and a press is ignored
        // (the original guarded on !table.isEnabled()).
        pressRow(delete, 1);
        assertTrue(delete.idSelector().isSelected());

        click(delete.cancelButton());
        assertBrowseMode(delete);

        pressRow(delete, 1);

        assertTrue(delete.mimeSelector().isSelected());
        assertFalse(delete.valueField().isVisible());
        assertTrue(delete.valueCombo().isVisible());
        assertEquals("image/png", delete.valueCombo().getSelectedItem());
    }

    @Test
    void deleteEditRemoveAndCancelRestoreBrowseModeAndResetControls() {
        AttachmentPanel delete = panel(AttachmentOperation.DELETE);
        delete.model().addRow(new String[] { AttachmentSelector.NAME.label(), "cover" });

        pressRow(delete, 0);
        delete.valueField().setText("  cover2  ");
        click(delete.editButton());
        assertArrayEquals(new String[] { AttachmentSelector.NAME.label(), "cover2" },
                delete.model().rows().get(0));
        assertBrowseMode(delete);
        assertEquals(-1, delete.table().getSelectedRow());
        assertEquals("", delete.valueField().getText());

        pressRow(delete, 0);
        click(delete.removeButton());
        assertEquals(0, delete.model().getRowCount());
        assertBrowseMode(delete);
        assertTrue(delete.nameSelector().isSelected());

        delete.model().addRow(new String[] { AttachmentSelector.NAME.label(), "x" });
        pressRow(delete, 0);
        click(delete.cancelButton());
        assertBrowseMode(delete);
        assertEquals(1, delete.model().getRowCount());
        assertEquals("", delete.valueField().getText());
    }

    @Test
    void deleteIdFieldClampsInvalidValuesOnFocusLost() {
        AttachmentPanel delete = panel(AttachmentOperation.DELETE);
        click(delete.idSelector());

        delete.valueField().setText("-1");
        fireFocusLost(delete.valueField());
        assertEquals("1", delete.valueField().getText());

        delete.valueField().setText("x");
        fireFocusLost(delete.valueField());
        assertEquals("1", delete.valueField().getText());

        click(delete.nameSelector());
        delete.valueField().setText("0");
        fireFocusLost(delete.valueField());
        assertEquals("0", delete.valueField().getText());
    }

    /* Command lines (formerly setCmdLineAttachments* in the god class) */

    @Test
    void addCommandLineEmitsMetadataOnlyWhenPresent() {
        AttachmentPanel add = panel(AttachmentOperation.ADD);
        add.model().addRow(new String[] { "C:\\a.png", "", "", "" });
        add.model().addRow(
                new String[] { "C:\\b.png", "b.png", "B image", "image/png" });

        assertEquals(" --add-attachment \"C:\\a.png\""
                + " --attachment-name \"b.png\" --attachment-description \"B image\""
                + " --attachment-mime-type \"image/png\" --add-attachment \"C:\\b.png\"",
                add.cmdLine());
        assertEquals(" --add-attachment \"C:\\\\a.png\""
                + " --attachment-name \"b.png\" --attachment-description \"B image\""
                + " --attachment-mime-type \"image/png\" --add-attachment \"C:\\\\b.png\"",
                add.cmdLineOpt());
    }

    @Test
    void replaceCommandLineUsesTheSelectorPrefixOfEachRow() {
        AttachmentPanel replace = panel(AttachmentOperation.REPLACE);
        replace.model().addRow(
                new String[] { AttachmentSelector.NAME.label(), "cover", "C:\\new.png", "n", "", "" });
        replace.model().addRow(
                new String[] { AttachmentSelector.ID.label(), "1", "C:\\new.png", "", "", "" });
        replace.model().addRow(new String[] { AttachmentSelector.MIME_TYPE.label(), "image/png",
                "C:\\new.png", "", "", "" });

        assertEquals(" --attachment-name \"n\""
                + " --replace-attachment \"name:cover:C:\\new.png\""
                + " --replace-attachment \"1:C:\\new.png\""
                + " --replace-attachment \"mime-type:image/png:C:\\new.png\"",
                replace.cmdLine());
        // The ID original value is never escaped, as in the original builder.
        assertEquals(" --attachment-name \"n\""
                + " --replace-attachment \"name:cover:C:\\\\new.png\""
                + " --replace-attachment \"1:C:\\\\new.png\""
                + " --replace-attachment \"mime-type:image/png:C:\\\\new.png\"",
                replace.cmdLineOpt());
    }

    @Test
    void deleteCommandLineUsesTheSelectorPrefixOfEachRow() {
        AttachmentPanel delete = panel(AttachmentOperation.DELETE);
        delete.model().addRow(new String[] { AttachmentSelector.NAME.label(), "cover" });
        delete.model().addRow(new String[] { AttachmentSelector.ID.label(), "3" });
        delete.model().addRow(new String[] { AttachmentSelector.MIME_TYPE.label(), "image/png" });

        assertEquals(" --delete-attachment \"name:cover\""
                + " --delete-attachment \"3\""
                + " --delete-attachment \"mime-type:image/png\"",
                delete.cmdLine());
        assertEquals(" --delete-attachment \"name:cover\""
                + " --delete-attachment \"3\""
                + " --delete-attachment \"mime-type:image/png\"",
                delete.cmdLineOpt());
    }

    @Test
    void commandLinesOfAnEmptyModelAreEmpty() {
        assertEquals("", panel(AttachmentOperation.ADD).cmdLine());
        assertEquals("", panel(AttachmentOperation.ADD).cmdLineOpt());
        assertEquals("", panel(AttachmentOperation.REPLACE).cmdLine());
        assertEquals("", panel(AttachmentOperation.REPLACE).cmdLineOpt());
        assertEquals("", panel(AttachmentOperation.DELETE).cmdLine());
        assertEquals("", panel(AttachmentOperation.DELETE).cmdLineOpt());
    }

    @ParameterizedTest
    @EnumSource(AttachmentOperation.class)
    void resizeColumnsRunsAgainstTheTablesParentScrollPane(AttachmentOperation operation) {
        AttachmentPanel panel = panel(operation);

        assertDoesNotThrow(panel::resizeColumns);
    }

    /* Harness plumbing */

    private AttachmentPanel panel(AttachmentOperation operation) {
        return new AttachmentPanel(operation, mkvStrings, new AttachmentPanel.Host() {
            @Override
            public JFileChooser chooser() {
                throw new AssertionError("tests never open the file chooser");
            }

            @Override
            public Window dialogParent() {
                return null;
            }

            @Override
            public void logError(String message) {
                throw new AssertionError("unexpected drop error: " + message);
            }

            @Override
            public void showError(String message) {
                errors.add(message);
            }
        });
    }

    private static String[] columnNames(AttachmentPanel panel) {
        String[] names = new String[panel.table().getColumnCount()];
        for (int i = 0; i < names.length; i++) {
            names[i] = panel.table().getColumnName(i);
        }
        return names;
    }

    private static List<String> comboItems(JComboBox<String> combo) {
        DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) combo.getModel();
        List<String> items = new ArrayList<>();
        for (int i = 0; i < model.getSize(); i++) {
            items.add(model.getElementAt(i));
        }
        return items;
    }

    private static void assertEditMode(AttachmentPanel panel) {
        assertFalse(panel.table().isEnabled());
        assertFalse(panel.addButton().isEnabled());
        assertTrue(panel.editButton().isEnabled());
        assertTrue(panel.removeButton().isEnabled());
        assertTrue(panel.cancelButton().isEnabled());
    }

    private static void assertBrowseMode(AttachmentPanel panel) {
        assertTrue(panel.table().isEnabled());
        assertTrue(panel.addButton().isEnabled());
        assertFalse(panel.editButton().isEnabled());
        assertFalse(panel.removeButton().isEnabled());
        assertFalse(panel.cancelButton().isEnabled());
    }

    /** Selects a table row and fires the press listener like a mouse would. */
    private static void pressRow(AttachmentPanel panel, int row) {
        panel.table().setRowSelectionInterval(row, row);
        MouseEvent event = new MouseEvent(panel.table(), MouseEvent.MOUSE_PRESSED, 0, 0, 0, 0, 1, false);

        for (MouseListener listener : panel.table().getMouseListeners()) {
            listener.mousePressed(event);
        }
    }

    /** Fires the button's action listeners, like TrackPanelTest's click. */
    private static void click(AbstractButton button) {
        ActionEvent event = new ActionEvent(button, ActionEvent.ACTION_PERFORMED,
                button.getActionCommand());

        for (ActionListener listener : button.getActionListeners()) {
            listener.actionPerformed(event);
        }
    }

    /** Selects a radio and fires its listeners, like a user click would. */
    private static void click(JRadioButton radio) {
        radio.setSelected(true);
        click((AbstractButton) radio);
    }

    private static void fireFocusLost(JTextField field) {
        FocusEvent event = new FocusEvent(field, FocusEvent.FOCUS_LOST);

        for (FocusListener listener : field.getFocusListeners()) {
            listener.focusLost(event);
        }
    }
}
