package ru.anseranser.jmkvpropedit;

import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

/**
 * One track slot: the settings card and every control it holds — the model
 * behind a single row of the track combo — issue #12.
 *
 * <p>
 * This replaces the parallel per-type arrays that used to live in
 * {@link JMkvpropedit} ({@code chbEditVideo[]}, {@code txtNameAudio[]},
 * {@code cbLangSubtitle[]}, ...): one slot object owns its own components, so
 * adding, removing and reading a track never has to shuffle twenty-three
 * arrays in lockstep. The card layout and enable/disable listeners are the
 * single copy of what was repeated for video, audio and subtitle.
 * </p>
 *
 * <p>
 * The original listeners looked the target components up by the track combo's
 * selected index; a slot's listeners now act on the slot's own components.
 * Both are equivalent for anything reachable through the UI: only the selected
 * slot's card is visible, so user interaction always comes from the selected
 * slot.
 * </p>
 */
public final class TrackSlot {

    /** The settings card shown in the {@link TrackPanel} CardLayout. */
    final JPanel card = new JPanel();

    final JCheckBox chbEdit = new JCheckBox("Edit this track:");
    final JCheckBox chbEnable = new JCheckBox("Enable track:");
    final JRadioButton rbYesEnable = new JRadioButton("Yes");
    final JRadioButton rbNoEnable = new JRadioButton("No");

    final JCheckBox chbDefault = new JCheckBox("Default track:");
    final JRadioButton rbYesDef = new JRadioButton("Yes");
    final JRadioButton rbNoDef = new JRadioButton("No");

    final JCheckBox chbForced = new JCheckBox("Forced track:");
    final JRadioButton rbYesForced = new JRadioButton("Yes");
    final JRadioButton rbNoForced = new JRadioButton("No");

    final JCheckBox chbName = new JCheckBox("Track name:");
    final JTextField txtName = new JTextField();

    final JCheckBox chbNumb = new JCheckBox("Numbering:");
    final JLabel lblNumbStart = new JLabel("Start");
    final JTextField txtNumbStart = new JTextField();
    final JLabel lblNumbPad = new JLabel("Padding");
    final JTextField txtNumbPad = new JTextField();
    final JLabel lblNumbExplain;

    final JCheckBox chbLang = new JCheckBox("Language:");
    final JComboBox<String> cbLang = new JComboBox<>();

    final JCheckBox chbExtra = new JCheckBox("Extra parameters:");
    final JTextField txtExtra = new JTextField();

    /**
     * Builds the card for one slot of the given track type: layout, initial
     * enabled states, language combo contents and all listeners — the single
     * copy of what was {@code addVideoTrack}/{@code addAudioTrack}/
     * {@code addSubtitleTrack} in the god class.
     *
     * @param type      the track type (supplies the numbering explain example)
     * @param mkvStrings language names/codes for the language combo
     */
    TrackSlot(TrackType type, MkvStrings mkvStrings) {
        lblNumbExplain = new JLabel(
                "      To use it, add {num} to the name (e.g. \"My " + type.nameExample()
                        + " {num}\"). Use {file_name} to use the file name as the name.");

        buildCard(mkvStrings);
        wireListeners();
    }

    /** Lays out the card: 2 columns, 9 rows, exactly like the original. */
    private void buildCard(MkvStrings mkvStrings) {
        GridBagLayout layout = new GridBagLayout();
        layout.columnWidths = new int[] { 0, 0, 0 };
        layout.rowHeights = new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0 };
        layout.columnWeights = new double[] { 0.0, 1.0, Double.MIN_VALUE };
        layout.rowWeights = new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE };
        card.setLayout(layout);

        // Row 0: Edit this track (the only checkbox enabled from the start).
        addCell(chbEdit, 0, 0, new Insets(0, 0, 10, 5), GridBagConstraints.NONE,
                GridBagConstraints.WEST);

        // Rows 1-3: flag checkboxes, each with its own Yes/No radio group.
        addFlagRow(chbEnable, rbYesEnable, rbNoEnable, 1);
        addFlagRow(chbDefault, rbYesDef, rbNoDef, 2);
        addFlagRow(chbForced, rbYesForced, rbNoForced, 3);

        // Row 4: track name.
        chbName.setEnabled(false);
        addCell(chbName, 0, 4, new Insets(0, 0, 5, 5), GridBagConstraints.NONE,
                GridBagConstraints.WEST);
        txtName.setEnabled(false);
        addCell(txtName, 1, 4, new Insets(0, 0, 5, 0), GridBagConstraints.HORIZONTAL,
                GridBagConstraints.CENTER);
        txtName.setColumns(10);

        // Row 5: numbering controls (checkbox + start/pad fields in one panel).
        JPanel numbControls = new JPanel();
        FlowLayout numbLayout = (FlowLayout) numbControls.getLayout();
        numbLayout.setAlignment(FlowLayout.LEFT);
        numbLayout.setVgap(0);
        addCell(numbControls, 1, 5, new Insets(0, 0, 5, 0), GridBagConstraints.BOTH,
                GridBagConstraints.CENTER);

        chbNumb.setEnabled(false);
        numbControls.add(chbNumb);
        numbControls.add(Box.createHorizontalStrut(10));

        lblNumbStart.setEnabled(false);
        numbControls.add(lblNumbStart);
        txtNumbStart.setText("1");
        txtNumbStart.setEnabled(false);
        txtNumbStart.setColumns(10);
        numbControls.add(txtNumbStart);

        numbControls.add(Box.createHorizontalStrut(5));

        lblNumbPad.setEnabled(false);
        numbControls.add(lblNumbPad);
        txtNumbPad.setText("1");
        txtNumbPad.setEnabled(false);
        txtNumbPad.setColumns(10);
        numbControls.add(txtNumbPad);

        // Row 6: the numbering explanation.
        lblNumbExplain.setEnabled(false);
        addCell(lblNumbExplain, 1, 6, new Insets(0, 0, 10, 0), GridBagConstraints.NONE,
                GridBagConstraints.NORTHWEST);

        // Row 7: language.
        chbLang.setEnabled(false);
        addCell(chbLang, 0, 7, new Insets(0, 0, 10, 5), GridBagConstraints.NONE,
                GridBagConstraints.WEST);
        cbLang.setEnabled(false);
        cbLang.setModel(new DefaultComboBoxModel<String>(mkvStrings.getLangNames()));
        cbLang.setSelectedIndex(mkvStrings.getLangCodeList().indexOf("und"));
        addCell(cbLang, 1, 7, new Insets(0, 0, 10, 0), GridBagConstraints.NONE,
                GridBagConstraints.WEST);

        // Row 8: extra parameters (no bottom gap, like the original).
        chbExtra.setEnabled(false);
        addCell(chbExtra, 0, 8, new Insets(0, 0, 0, 0), GridBagConstraints.NONE,
                GridBagConstraints.WEST);
        txtExtra.setEnabled(false);
        addCell(txtExtra, 1, 8, new Insets(0, 0, 0, 0), GridBagConstraints.HORIZONTAL,
                GridBagConstraints.CENTER);
        txtExtra.setColumns(10);

        /* Start of mouse events for right-click menu */

        Utils.addRCMenuMouseListener(txtName);
        Utils.addRCMenuMouseListener(txtNumbStart);
        Utils.addRCMenuMouseListener(txtNumbPad);
        Utils.addRCMenuMouseListener(txtExtra);

        /* End of mouse events for right-click menu */
    }

    /** Wires the enable/disable dance, one copy for every track type. */
    private void wireListeners() {
        chbEdit.addActionListener(e -> {
            boolean state = chbDefault.isEnabled();

            chbEnable.setEnabled(!state);
            chbDefault.setEnabled(!state);
            chbForced.setEnabled(!state);
            chbName.setEnabled(!state);
            chbLang.setEnabled(!state);
            chbExtra.setEnabled(!state);

            if (txtName.isEnabled() || chbName.isSelected()) {
                txtName.setEnabled(!state);
                chbNumb.setEnabled(!state);

                if (chbNumb.isSelected()) {
                    lblNumbStart.setEnabled(!state);
                    txtNumbStart.setEnabled(!state);
                    lblNumbPad.setEnabled(!state);
                    txtNumbPad.setEnabled(!state);
                    lblNumbExplain.setEnabled(!state);
                }
            }

            if (rbNoEnable.isEnabled() || chbEnable.isSelected()) {
                rbNoEnable.setEnabled(!state);
                rbYesEnable.setEnabled(!state);
            }

            if (rbNoDef.isEnabled() || chbDefault.isSelected()) {
                rbNoDef.setEnabled(!state);
                rbYesDef.setEnabled(!state);
            }

            if (rbNoForced.isEnabled() || chbForced.isSelected()) {
                rbNoForced.setEnabled(!state);
                rbYesForced.setEnabled(!state);
            }

            if (cbLang.isEnabled() || chbLang.isSelected()) {
                cbLang.setEnabled(!state);
            }

            if (txtExtra.isEnabled() || chbExtra.isSelected()) {
                chbExtra.setEnabled(!state);
                txtExtra.setEnabled(!state);
            }
        });

        chbEnable.addActionListener(e -> {
            boolean state = rbNoEnable.isEnabled();
            rbNoEnable.setEnabled(!state);
            rbYesEnable.setEnabled(!state);
        });

        chbDefault.addActionListener(e -> {
            boolean state = rbNoDef.isEnabled();
            rbNoDef.setEnabled(!state);
            rbYesDef.setEnabled(!state);
        });

        chbForced.addActionListener(e -> {
            boolean state = rbNoForced.isEnabled();
            rbNoForced.setEnabled(!state);
            rbYesForced.setEnabled(!state);
        });

        chbName.addActionListener(e -> {
            boolean state = chbNumb.isEnabled();
            chbNumb.setEnabled(!state);
            txtName.setEnabled(!state);

            if (chbNumb.isSelected()) {
                lblNumbStart.setEnabled(!state);
                txtNumbStart.setEnabled(!state);
                lblNumbPad.setEnabled(!state);
                txtNumbPad.setEnabled(!state);
                lblNumbExplain.setEnabled(!state);
            }
        });

        chbNumb.addActionListener(e -> {
            boolean state = txtNumbStart.isEnabled();
            lblNumbStart.setEnabled(!state);
            txtNumbStart.setEnabled(!state);
            lblNumbPad.setEnabled(!state);
            txtNumbPad.setEnabled(!state);
            lblNumbExplain.setEnabled(!state);
        });

        txtNumbStart.addFocusListener(validateNumberingOnFocusLost(txtNumbStart));
        txtNumbPad.addFocusListener(validateNumberingOnFocusLost(txtNumbPad));

        chbLang.addActionListener(e -> {
            boolean state = cbLang.isEnabled();
            cbLang.setEnabled(!state);
        });

        chbExtra.addActionListener(e -> {
            boolean state = txtExtra.isEnabled();
            txtExtra.setEnabled(!state);
        });
    }

    /** Negative or unparseable numbering values fall back to "1" on focus loss. */
    private static FocusListener validateNumberingOnFocusLost(JTextField field) {
        return new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                try {
                    if (Integer.parseInt(field.getText()) < 0) {
                        field.setText("1");
                    }
                } catch (NumberFormatException e1) {
                    field.setText("1");
                }
            }
        };
    }

    /**
     * Adds a flag row: the checkbox in column 0, a left-aligned panel with the
     * Yes/No radio group in column 1. Radios start disabled, Yes selected —
     * exactly like the original per-type copies.
     */
    private void addFlagRow(JCheckBox box, JRadioButton yes, JRadioButton no, int row) {
        box.setEnabled(false);
        addCell(box, 0, row, new Insets(0, 0, 5, 5), GridBagConstraints.NONE,
                GridBagConstraints.WEST);

        JPanel controls = new JPanel();
        FlowLayout layout = (FlowLayout) controls.getLayout();
        layout.setAlignment(FlowLayout.LEFT);
        layout.setVgap(0);
        addCell(controls, 1, row, new Insets(0, 0, 5, 0), GridBagConstraints.HORIZONTAL,
                GridBagConstraints.CENTER);

        yes.setEnabled(false);
        yes.setSelected(true);
        controls.add(yes);

        no.setEnabled(false);
        controls.add(no);

        ButtonGroup group = new ButtonGroup();
        group.add(yes);
        group.add(no);
    }

    /** Places one component on the card's grid. */
    private void addCell(Component component, int column, int row, Insets insets, int fill,
            int anchor) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = insets;
        gbc.fill = fill;
        gbc.anchor = anchor;
        gbc.gridx = column;
        gbc.gridy = row;
        card.add(component, gbc);
    }
}
