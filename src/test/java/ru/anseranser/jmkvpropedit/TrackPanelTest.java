package ru.anseranser.jmkvpropedit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.JTextField;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Unit tests for the unified track panel — issue #12.
 *
 * <p>
 * Video, audio and subtitle tracks share one {@link TrackPanel} built from
 * {@link TrackSlot}s; the triplet of near-identical copies in
 * {@link JMkvpropedit} is gone. Every type-independent behavior runs for all
 * three {@link TrackType}s to prove the collapse kept add/remove, the
 * {@link TrackPanel#MAX_STREAMS} cap, combo/card synchronization and the
 * per-track settings panel identical to the original.
 * </p>
 */
class TrackPanelTest {

    private final MkvStrings mkvStrings = new MkvStrings();

    @ParameterizedTest
    @EnumSource(TrackType.class)
    void panelStartsWithNoTracksAndRemovalDisabled(TrackType type) {
        TrackPanel panel = new TrackPanel(type, mkvStrings);

        assertEquals(0, panel.trackCount());
        assertEquals(0, panel.combo().getItemCount());
        assertEquals(0, panel.cards().getComponentCount());
        assertTrue(panel.addButton().isEnabled());
        assertFalse(panel.removeButton().isEnabled());
    }

    @ParameterizedTest
    @EnumSource(TrackType.class)
    void startupTrackFillsThePanelButKeepsRemovalDisabled(TrackType type) {
        TrackPanel panel = new TrackPanel(type, mkvStrings);
        panel.addTrack();

        assertEquals(1, panel.trackCount());
        assertEquals(1, panel.combo().getItemCount());
        assertEquals(type.label() + " Track 1", panel.combo().getItemAt(0));
        assertEquals(1, panel.cards().getComponentCount());
        assertEquals(1, panel.slots().size());
        assertEquals(mkvStrings.getLangCodeList().indexOf("und"),
                panel.slots().get(0).cbLang.getSelectedIndex());
        assertFalse(panel.removeButton().isEnabled());
    }

    @ParameterizedTest
    @EnumSource(TrackType.class)
    void addButtonGrowsThePanelAndEnablesRemoval(TrackType type) {
        TrackPanel panel = new TrackPanel(type, mkvStrings);
        panel.addTrack();

        click(panel.addButton());

        assertEquals(2, panel.trackCount());
        assertEquals(2, panel.combo().getItemCount());
        assertEquals(1, panel.combo().getSelectedIndex());
        assertTrue(panel.removeButton().isEnabled());
    }

    @ParameterizedTest
    @EnumSource(TrackType.class)
    void comboSelectionShowsTheMatchingSettingsCard(TrackType type) {
        TrackPanel panel = new TrackPanel(type, mkvStrings);
        panel.addTrack();
        click(panel.addButton());

        panel.combo().setSelectedIndex(0);
        assertTrue(panel.slots().get(0).card.isVisible());
        assertFalse(panel.slots().get(1).card.isVisible());

        panel.combo().setSelectedIndex(1);
        assertTrue(panel.slots().get(1).card.isVisible());
        assertFalse(panel.slots().get(0).card.isVisible());
    }

    @Test
    void addButtonStopsAtMaxStreamsAndAStaleClickKeepsTheCap() {
        TrackPanel panel = new TrackPanel(TrackType.VIDEO, mkvStrings);
        panel.addTrack();

        while (panel.addButton().isEnabled()) {
            click(panel.addButton());
        }

        assertEquals(TrackPanel.MAX_STREAMS, panel.trackCount());
        assertEquals(TrackPanel.MAX_STREAMS, panel.combo().getItemCount());

        click(panel.addButton()); // stale click past the cap must not grow the panel
        assertEquals(TrackPanel.MAX_STREAMS, panel.trackCount());
        assertEquals(TrackPanel.MAX_STREAMS, panel.combo().getItemCount());
    }

    @ParameterizedTest
    @EnumSource(TrackType.class)
    void removingTheSelectedMiddleTrackKeepsComboCardsAndSlotsAligned(TrackType type) {
        TrackPanel panel = new TrackPanel(type, mkvStrings);
        panel.addTrack();
        click(panel.addButton());
        click(panel.addButton());

        panel.slots().get(0).txtName.setText("slot0");
        panel.slots().get(1).txtName.setText("slot1");
        panel.slots().get(2).txtName.setText("slot2");
        panel.combo().setSelectedIndex(1); // the MIDDLE track

        click(panel.removeButton());

        assertEquals(2, panel.trackCount());
        assertEquals(2, panel.combo().getItemCount());
        assertEquals(2, panel.cards().getComponentCount());
        assertEquals("slot0", panel.slots().get(0).txtName.getText());
        assertEquals("slot2", panel.slots().get(1).txtName.getText());
        assertEquals(type.label() + " Track 1", panel.combo().getItemAt(0));
        assertEquals(type.label() + " Track 2", panel.combo().getItemAt(1));
        assertEquals(1, panel.combo().getSelectedIndex());
        assertTrue(panel.removeButton().isEnabled());

        // Cross-reference both sides: card name -> component, component -> card.
        panel.cardLayout().show(panel.cards(), type.cardPrefix() + "[1]");
        assertTrue(panel.slots().get(1).card.isVisible());
        assertFalse(panel.slots().get(0).card.isVisible());
    }

    @ParameterizedTest
    @EnumSource(TrackType.class)
    void firstTrackIsRemovableOnlyWhileAnotherTrackExists(TrackType type) {
        TrackPanel panel = new TrackPanel(type, mkvStrings);
        panel.addTrack();

        assertFalse(panel.removeButton().isEnabled());

        click(panel.addButton()); // 1 -> 2 tracks enables Remove
        assertTrue(panel.removeButton().isEnabled());

        panel.slots().get(0).txtName.setText("slot0");
        panel.slots().get(1).txtName.setText("slot1");
        panel.combo().setSelectedIndex(0); // the FIRST track

        click(panel.removeButton());

        assertEquals(1, panel.trackCount());
        assertEquals("slot1", panel.slots().get(0).txtName.getText());
        assertFalse(panel.removeButton().isEnabled());

        click(panel.removeButton()); // stale click while disabled must not drop the last track
        assertEquals(1, panel.trackCount());
        assertEquals(1, panel.combo().getItemCount());
    }

    @Test
    void trackSettingsSnapshotReadsEveryControl() {
        TrackPanel panel = new TrackPanel(TrackType.AUDIO, mkvStrings);
        panel.addTrack();
        TrackSlot slot = panel.slots().get(0);

        slot.chbEdit.setSelected(true);
        slot.chbEnable.setSelected(true);
        slot.rbNoEnable.setSelected(true); // ButtonGroup ignores deselection; pick No (as a click would)
        slot.chbDefault.setSelected(true); // Yes stays selected
        slot.chbForced.setSelected(true);
        slot.rbNoForced.setSelected(true); // No (as a click would)
        slot.chbName.setSelected(true);
        slot.txtName.setText("My Audio {num}");
        slot.chbLang.setSelected(true);
        slot.cbLang.setSelectedIndex(mkvStrings.getLangCodeList().indexOf("eng"));
        slot.chbExtra.setSelected(true);
        slot.txtExtra.setText("--interaction-ffmpeg");
        slot.chbNumb.setSelected(true);
        slot.txtNumbStart.setText("7");
        slot.txtNumbPad.setText("2");

        List<CommandBuilder.TrackSettings> settings = panel.trackSettings();

        assertEquals(1, settings.size());
        CommandBuilder.TrackSettings s = settings.get(0);
        assertTrue(s.edit());
        assertTrue(s.setEnabled());
        assertFalse(s.enableValue());
        assertTrue(s.setDefault());
        assertTrue(s.defaultValue());
        assertTrue(s.setForced());
        assertFalse(s.forcedValue());
        assertTrue(s.setName());
        assertEquals("My Audio {num}", s.name());
        assertTrue(s.setLanguage());
        assertEquals("eng", s.language());
        assertTrue(s.setExtra());
        assertEquals("--interaction-ffmpeg", s.extra());
        assertTrue(s.numbering());
        assertEquals("7", s.numberStart());
        assertEquals("2", s.numberPad());
    }

    @Test
    void languageCodeIsResolvedOnlyWhenTheLanguageCheckboxIsSelected() {
        TrackPanel panel = new TrackPanel(TrackType.SUBTITLE, mkvStrings);
        panel.addTrack();
        TrackSlot slot = panel.slots().get(0);

        slot.chbEdit.setSelected(true);
        slot.cbLang.setSelectedIndex(mkvStrings.getLangCodeList().indexOf("eng"));

        CommandBuilder.TrackSettings withoutCheckbox = panel.trackSettings().get(0);
        assertFalse(withoutCheckbox.setLanguage());
        assertNull(withoutCheckbox.language());

        slot.chbLang.setSelected(true);

        CommandBuilder.TrackSettings withCheckbox = panel.trackSettings().get(0);
        assertTrue(withCheckbox.setLanguage());
        assertEquals("eng", withCheckbox.language());
    }

    @ParameterizedTest
    @EnumSource(TrackType.class)
    void editCheckboxEnablesTheSlotsControlsInTwoSteps(TrackType type) {
        TrackPanel panel = new TrackPanel(type, mkvStrings);
        panel.addTrack();
        TrackSlot slot = panel.slots().get(0);

        assertFalse(slot.chbEnable.isEnabled());
        assertFalse(slot.txtName.isEnabled());

        click(slot.chbEdit);

        assertTrue(slot.chbEdit.isSelected());
        assertTrue(slot.chbEnable.isEnabled());
        assertTrue(slot.chbDefault.isEnabled());
        assertTrue(slot.chbForced.isEnabled());
        assertTrue(slot.chbName.isEnabled());
        assertTrue(slot.chbLang.isEnabled());
        assertTrue(slot.chbExtra.isEnabled());
        // sub-controls stay off until their own checkbox fires
        assertFalse(slot.txtName.isEnabled());
        assertFalse(slot.chbNumb.isEnabled());
        assertFalse(slot.rbYesEnable.isEnabled());
        assertFalse(slot.cbLang.isEnabled());
        assertFalse(slot.txtExtra.isEnabled());

        click(slot.chbName);
        assertTrue(slot.txtName.isEnabled());
        assertTrue(slot.chbNumb.isEnabled());

        click(slot.chbNumb);
        assertTrue(slot.txtNumbStart.isEnabled());
        assertTrue(slot.txtNumbPad.isEnabled());
        assertTrue(slot.lblNumbExplain.isEnabled());

        click(slot.chbEnable);
        assertTrue(slot.rbYesEnable.isEnabled());
        assertTrue(slot.rbNoEnable.isEnabled());

        click(slot.chbLang);
        assertTrue(slot.cbLang.isEnabled());

        click(slot.chbExtra);
        assertTrue(slot.txtExtra.isEnabled());

        // Uncheck Edit: everything folds back, including the selected numbering's fields.
        click(slot.chbEdit);

        assertFalse(slot.chbEdit.isSelected());
        assertFalse(slot.chbEnable.isEnabled());
        assertFalse(slot.chbName.isEnabled());
        assertFalse(slot.txtName.isEnabled());
        assertFalse(slot.chbNumb.isEnabled());
        assertFalse(slot.txtNumbStart.isEnabled());
        assertFalse(slot.txtNumbPad.isEnabled());
        assertFalse(slot.rbYesEnable.isEnabled());
        assertFalse(slot.rbNoDef.isEnabled());
        assertFalse(slot.cbLang.isEnabled());
        assertFalse(slot.txtExtra.isEnabled());
    }

    @ParameterizedTest
    @EnumSource(TrackType.class)
    void numberingFieldsResetInvalidValuesOnFocusLost(TrackType type) {
        TrackPanel panel = new TrackPanel(type, mkvStrings);
        panel.addTrack();
        TrackSlot slot = panel.slots().get(0);

        slot.txtNumbStart.setText("-1");
        fireFocusLost(slot.txtNumbStart);
        assertEquals("1", slot.txtNumbStart.getText());

        slot.txtNumbStart.setText("abc");
        fireFocusLost(slot.txtNumbStart);
        assertEquals("1", slot.txtNumbStart.getText());

        slot.txtNumbPad.setText("-2");
        fireFocusLost(slot.txtNumbPad);
        assertEquals("1", slot.txtNumbPad.getText());
    }

    @ParameterizedTest
    @EnumSource(TrackType.class)
    void numberingExplainLabelNamesTheTrackType(TrackType type) {
        TrackPanel panel = new TrackPanel(type, mkvStrings);
        panel.addTrack();

        String text = panel.slots().get(0).lblNumbExplain.getText();
        assertTrue(text.contains("\"My " + type.nameExample() + " {num}\""), "got: " + text);
    }

    /* Harness plumbing */

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
}
