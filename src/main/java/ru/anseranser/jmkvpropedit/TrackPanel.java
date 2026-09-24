package ru.anseranser.jmkvpropedit;

import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * The unified track panel for video, audio and subtitle tracks — issue #12.
 *
 * <p>
 * One component replaces the three near-identical copies that used to live in
 * {@link JMkvpropedit}: the track combo, the add/remove buttons with the
 * {@link #MAX_STREAMS} cap, the CardLayout of per-track settings cards, the
 * add/remove/rename/renumber logic and the snapshot of every slot into
 * {@link CommandBuilder.TrackSettings}. All three track types use this exact
 * code path; only labels, the card prefix and the mkvpropedit selector come
 * from {@link TrackType}.
 * </p>
 *
 * <p>
 * Behavior is kept identical to the original triplet, including its quirks:
 * the startup track leaves Remove disabled, Remove refuses to drop the last
 * track, and a stale Add/Remove click past the cap or below one track is a
 * no-op.
 * </p>
 */
public final class TrackPanel extends JPanel {

    /** Hard cap on tracks per type, as in the original triplet. */
    public static final int MAX_STREAMS = 200;

    private static final long serialVersionUID = 1L;

    private final TrackType type;
    private final MkvStrings mkvStrings;
    private final List<TrackSlot> slots = new ArrayList<>();

    private final JComboBox<String> combo = new JComboBox<>();
    private final JButton btnAdd = new JButton("");
    private final JButton btnRemove = new JButton("");
    private final CardLayout cardLayout = new CardLayout(0, 0);
    private final JPanel cards = new JPanel(cardLayout);

    /**
     * Builds the panel for one track type: controls row (combo, add, remove)
     * above the CardLayout of settings cards, wired add/remove listeners.
     *
     * @param type      which track type this panel manages
     * @param mkvStrings shared language strings for the slots
     */
    public TrackPanel(TrackType type, MkvStrings mkvStrings) {
        this.type = type;
        this.mkvStrings = mkvStrings;

        setBorder(new EmptyBorder(10, 10, 10, 10));
        GridBagLayout layout = new GridBagLayout();
        layout.columnWidths = new int[] { 705, 0 };
        layout.rowHeights = new int[] { 30, 283, 0 };
        layout.columnWeights = new double[] { 1.0, Double.MIN_VALUE };
        layout.rowWeights = new double[] { 0.0, 1.0, Double.MIN_VALUE };
        setLayout(layout);

        JPanel controls = new JPanel();
        GridBagConstraints gbcControls = new GridBagConstraints();
        gbcControls.insets = new Insets(0, 0, 5, 0);
        gbcControls.fill = GridBagConstraints.BOTH;
        gbcControls.gridx = 0;
        gbcControls.gridy = 0;
        add(controls, gbcControls);
        controls.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
        controls.add(combo);

        styleIconButton(btnAdd, "/list-add.png", new Insets(0, 5, 0, 5));
        controls.add(btnAdd);

        btnRemove.setEnabled(false);
        styleIconButton(btnRemove, "/list-remove.png", new Insets(0, 0, 0, 0));
        controls.add(btnRemove);

        GridBagConstraints gbcCards = new GridBagConstraints();
        gbcCards.fill = GridBagConstraints.BOTH;
        gbcCards.gridx = 0;
        gbcCards.gridy = 1;
        add(cards, gbcCards);

        combo.addActionListener(e ->
                cardLayout.show(cards, type.cardPrefix() + "[" + combo.getSelectedIndex() + "]"));

        btnAdd.addActionListener(e -> {
            addTrack();

            combo.setSelectedIndex(combo.getItemCount() - 1);
            if (combo.getItemCount() == MAX_STREAMS) {
                btnAdd.setEnabled(false);
            }

            if (!btnRemove.isEnabled()) {
                btnRemove.setEnabled(true);
            }
        });

        btnRemove.addActionListener(e -> removeSelectedTrack());
    }

    /**
     * Adds the next track slot: builds its card, drops it into the CardLayout
     * and appends the combo item — the single copy of the original
     * {@code addVideoTrack}/{@code addAudioTrack}/{@code addSubtitleTrack}.
     * Past {@link #MAX_STREAMS} it is a no-op (the Add button is disabled by
     * then; a stale programmatic click stays capped, as before).
     */
    public void addTrack() {
        if (slots.size() < MAX_STREAMS) {
            int index = slots.size();
            TrackSlot slot = new TrackSlot(type, mkvStrings);
            slots.add(slot);
            cards.add(slot.card, type.cardPrefix() + "[" + index + "]");
            combo.addItem(type.label() + " Track " + (index + 1));
        }
    }

    /** The current number of tracks; always equals the combo's item count. */
    public int trackCount() {
        return slots.size();
    }

    /**
     * Reads every slot's widgets into plain
     * {@link CommandBuilder.TrackSettings}. The language code is resolved from
     * the slot's combo here, where the original resolved it while building the
     * argument (and only when the language checkbox is selected).
     */
    public List<CommandBuilder.TrackSettings> trackSettings() {
        List<String> langCodes = mkvStrings.getLangCodeList();
        List<CommandBuilder.TrackSettings> settings = new ArrayList<>(slots.size());

        for (TrackSlot slot : slots) {
            boolean setLanguage = slot.chbLang.isSelected();

            settings.add(new CommandBuilder.TrackSettings(slot.chbEdit.isSelected(),
                    slot.chbEnable.isSelected(), slot.rbYesEnable.isSelected(),
                    slot.chbDefault.isSelected(), slot.rbYesDef.isSelected(),
                    slot.chbForced.isSelected(), slot.rbYesForced.isSelected(),
                    slot.chbName.isSelected(), slot.txtName.getText(),
                    setLanguage, setLanguage ? langCodes.get(slot.cbLang.getSelectedIndex()) : null,
                    slot.chbExtra.isSelected(), slot.txtExtra.getText(),
                    slot.chbNumb.isSelected(), slot.txtNumbStart.getText(), slot.txtNumbPad.getText()));
        }

        return settings;
    }

    /**
     * Removes the selected track: drops its card, renumbers the following
     * cards and combo items and shifts the slots one position left, so combo
     * index, card name and slot stay aligned. The last track cannot be
     * removed.
     */
    private void removeSelectedTrack() {
        int index = combo.getSelectedIndex();

        if (index >= 0 && combo.getItemCount() > 1) {
            TrackSlot removed = slots.remove(index);
            cards.remove(removed.card);

            for (int i = index; i < slots.size(); i++) {
                cards.remove(slots.get(i).card);
                cards.add(slots.get(i).card, type.cardPrefix() + "[" + i + "]");
            }

            combo.removeItemAt(index);
            int keepSelected = Math.min(index, combo.getItemCount() - 1);
            for (int i = index; i < combo.getItemCount(); i++) {
                combo.insertItemAt(type.label() + " Track " + (i + 1), i);
                combo.removeItemAt(i + 1);
            }
            combo.setSelectedIndex(keepSelected);

            cardLayout.show(cards, type.cardPrefix() + "[" + combo.getSelectedIndex() + "]");
        }

        if (combo.getItemCount() < MAX_STREAMS && !btnAdd.isEnabled()) {
            btnAdd.setEnabled(true);
        }

        if (combo.getItemCount() == 1) {
            btnRemove.setEnabled(false);
        }
    }

    /** Icon-only button styling shared by Add/Remove, as in the original UI. */
    private static void styleIconButton(JButton button, String iconResource, Insets margin) {
        button.setIcon(new ImageIcon(TrackPanel.class.getResource(iconResource)));
        button.setMargin(margin);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setOpaque(false);
    }

    /* Package-private access for the god class, the tests and the harness. */

    TrackType type() {
        return type;
    }

    JComboBox<String> combo() {
        return combo;
    }

    JButton addButton() {
        return btnAdd;
    }

    JButton removeButton() {
        return btnRemove;
    }

    JPanel cards() {
        return cards;
    }

    CardLayout cardLayout() {
        return cardLayout;
    }

    List<TrackSlot> slots() {
        return Collections.unmodifiableList(slots);
    }
}
