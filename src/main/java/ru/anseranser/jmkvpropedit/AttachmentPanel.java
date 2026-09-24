package ru.anseranser.jmkvpropedit;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableColumnModel;

/**
 * The unified attachments panel — one instance per attachment operation
 * (add / replace / delete), issue #13.
 *
 * <p>
 * One component replaces the three near-identical copies that used to live in
 * {@link JMkvpropedit}: the table over an {@link AttachmentModel}, the
 * per-operation controls (file/MIME fields, selector radios with the
 * text-or-combo card), the Add/Edit/Remove/Cancel edit-mode state machine and
 * the command-line fragments for mkvpropedit. All three operations use this
 * exact code path; only the row shape, the controls and the mandatory-field
 * message come from {@link AttachmentOperation}.
 * </p>
 *
 * <p>
 * Behavior is kept identical to the original triplet, including its quirks:
 * the Add file is stored raw but trimmed on Edit, replace Edit keeps name and
 * description raw, selecting a MIME-type replace row loads the
 * <em>replacement</em> into the original-value combo, delete-Add clears the
 * table selection while add/replace-Add keep it, and a failed mandatory check
 * leaves the panel in edit mode. The MIME combos keep issue #4's contract:
 * every combo gets its own fresh copy of the shared {@link MkvStrings} list.
 * </p>
 */
public final class AttachmentPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /**
     * The window-level services the panel needs from its embedder: the shared
     * file chooser for the Browse buttons, the dialog parent, and the two
     * error channels (drop errors go to the output area, mandatory-field
     * failures pop up a modal dialog).
     */
    public interface Host {

        /** The shared file chooser used by the Browse... buttons. */
        JFileChooser chooser();

        /** The parent window for the file chooser dialog. */
        Window dialogParent();

        /** Appends a dropped-file resolution error to the output area. */
        void logError(String message);

        /** Shows the modal error dialog of a failed mandatory-field check. */
        void showError(String message);
    }

    private final AttachmentOperation operation;
    private final Host host;
    private final MkvStrings mkvStrings;
    private final AttachmentModel model;

    private final JTable table = new JTable();

    private final JButton btnAdd = new JButton("Add");
    private final JButton btnEdit = new JButton("Edit");
    private final JButton btnRemove = new JButton("Remove");
    private final JButton btnCancel = new JButton("Cancel");

    /* Add + replace controls */
    private JTextField txtName;
    private JTextField txtDesc;
    private JComboBox<String> cbMime;

    /* Add-only controls */
    private JTextField txtFile;

    /* Replace-only controls */
    private JRadioButton rbName;
    private JRadioButton rbId;
    private JRadioButton rbMime;
    private JTextField txtOriginal;
    private JComboBox<String> cbOriginal;
    private JTextField txtReplacement;

    /* Delete-only controls */
    private JTextField txtValue;
    private JComboBox<String> cbValue;

    /**
     * Builds the panel for one attachment operation: table over the
     * operation's model above the operation's controls, wired listeners — the
     * single copy of what was triplicated in the god class.
     *
     * @param operation which attachment operation this panel serves
     * @param mkvStrings shared MIME type list for the combos
     * @param host      window-level services (chooser, dialogs, output)
     */
    public AttachmentPanel(AttachmentOperation operation, MkvStrings mkvStrings, Host host) {
        this.operation = operation;
        this.mkvStrings = mkvStrings;
        this.host = host;
        this.model = new AttachmentModel(operation);

        setLayout(new BorderLayout(0, 0));

        JScrollPane scroll = new JScrollPane();
        add(scroll, BorderLayout.CENTER);

        table.setShowGrid(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setModel(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setAutoscrolls(false);
        table.setFillsViewportHeight(true);
        scroll.setViewportView(table);

        JPanel controls = new JPanel();
        controls.setBorder(new EmptyBorder(5, 5, 5, 5));
        add(controls, BorderLayout.SOUTH);
        buildControls(controls);

        btnEdit.setEnabled(false);
        btnRemove.setEnabled(false);
        btnCancel.setEnabled(false);

        wireTableSelection();
        wireButtons();
    }

    /* Layout: one faithful port of each former sub-tab's controls. */

    private void buildControls(JPanel controls) {
        switch (operation) {
            case ADD -> buildAddControls(controls);
            case REPLACE -> buildReplaceControls(controls);
            case DELETE -> buildDeleteControls(controls);
        }
    }

    private void buildAddControls(JPanel controls) {
        GridBagLayout layout = new GridBagLayout();
        layout.columnWidths = new int[] { 0, 0, 0, 0 };
        layout.rowHeights = new int[] { 0, 0, 0, 0, 0, 0 };
        layout.columnWeights = new double[] { 0.0, 1.0, 0.0, Double.MIN_VALUE };
        layout.rowWeights = new double[] { 0.0, 0.0, 0.0, 1.0, 1.0, Double.MIN_VALUE };
        controls.setLayout(layout);

        addCell(controls, new JLabel("File:"), 0, 0, GridBagConstraints.WEST,
                new Insets(0, 0, 5, 5), GridBagConstraints.NONE);
        txtFile = new JTextField();
        txtFile.setEditable(false);
        txtFile.setColumns(10);
        addCell(controls, txtFile, 1, 0, GridBagConstraints.CENTER,
                new Insets(0, 0, 5, 5), GridBagConstraints.HORIZONTAL);
        JButton btnBrowseFile = new JButton("Browse...");
        addCell(controls, btnBrowseFile, 2, 0, GridBagConstraints.CENTER,
                new Insets(0, 0, 5, 0), GridBagConstraints.NONE);

        addCell(controls, new JLabel("Name:"), 0, 1, GridBagConstraints.WEST,
                new Insets(0, 0, 5, 5), GridBagConstraints.NONE);
        txtName = new JTextField();
        txtName.setColumns(10);
        addCell(controls, txtName, 1, 1, GridBagConstraints.CENTER,
                new Insets(0, 0, 5, 5), GridBagConstraints.HORIZONTAL);

        addCell(controls, new JLabel("Description:"), 0, 2, GridBagConstraints.EAST,
                new Insets(0, 0, 5, 5), GridBagConstraints.NONE);
        txtDesc = new JTextField();
        txtDesc.setColumns(10);
        addCell(controls, txtDesc, 1, 2, GridBagConstraints.CENTER,
                new Insets(0, 0, 5, 5), GridBagConstraints.HORIZONTAL);

        addCell(controls, new JLabel("MIME Type:"), 0, 3, GridBagConstraints.EAST,
                new Insets(0, 0, 5, 5), GridBagConstraints.NONE);
        cbMime = new JComboBox<>();
        cbMime.setModel(new DefaultComboBoxModel<String>(mimeComboItems()));
        addCell(controls, cbMime, 1, 3, GridBagConstraints.CENTER,
                new Insets(0, 0, 5, 5), GridBagConstraints.HORIZONTAL);

        JPanel bottom = new JPanel();
        addCell(controls, bottom, 1, 4, GridBagConstraints.CENTER,
                new Insets(0, 0, 0, 5), GridBagConstraints.BOTH);
        GridBagLayout bottomLayout = new GridBagLayout();
        bottomLayout.columnWidths = new int[] { 0, 0, 0, 0 };
        bottomLayout.rowHeights = new int[] { 0, 0 };
        bottomLayout.columnWeights = new double[] { 0.0, 0.0, 0.0, Double.MIN_VALUE };
        bottomLayout.rowWeights = new double[] { 0.0, Double.MIN_VALUE };
        bottom.setLayout(bottomLayout);
        addBottomButton(bottom, btnAdd, 0, new Insets(0, 0, 0, 5), GridBagConstraints.CENTER);
        addBottomButton(bottom, btnEdit, 1, new Insets(0, 0, 0, 5), GridBagConstraints.CENTER);
        addBottomButton(bottom, btnRemove, 2, new Insets(0, 0, 0, 5), GridBagConstraints.SOUTH);
        addBottomButton(bottom, btnCancel, 3, new Insets(0, 0, 0, 0), GridBagConstraints.CENTER);

        wireDropAndBrowse(txtFile, btnBrowseFile);
        Utils.addRCMenuMouseListener(txtFile);
        Utils.addRCMenuMouseListener(txtName);
        Utils.addRCMenuMouseListener(txtDesc);
    }

    private void buildReplaceControls(JPanel controls) {
        GridBagLayout layout = new GridBagLayout();
        layout.columnWidths = new int[] { 0, 0, 0, 0 };
        layout.rowHeights = new int[] { 0, 0, 0, 0, 0, 0, 0, 0 };
        layout.columnWeights = new double[] { 0.0, 1.0, 0.0, Double.MIN_VALUE };
        layout.rowWeights = new double[] { 1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, Double.MIN_VALUE };
        controls.setLayout(layout);

        addCell(controls, new JLabel("Type:"), 0, 0, GridBagConstraints.WEST,
                new Insets(0, 0, 5, 5), GridBagConstraints.NONE);
        JPanel selectorPanel = new JPanel();
        addCell(controls, selectorPanel, 1, 0, GridBagConstraints.CENTER,
                new Insets(0, 0, 5, 5), GridBagConstraints.BOTH);
        GridBagLayout selectorLayout = new GridBagLayout();
        selectorLayout.columnWidths = new int[] { 0, 0, 0 };
        selectorLayout.rowHeights = new int[] { 0, 0 };
        selectorLayout.columnWeights = new double[] { 0.0, 0.0, Double.MIN_VALUE };
        selectorLayout.rowWeights = new double[] { 0.0, Double.MIN_VALUE };
        selectorPanel.setLayout(selectorLayout);
        ButtonGroup selectorGroup = buildSelectorRadios(selectorPanel,
                new Insets(0, 0, 0, 5), new Insets(0, 0, 0, 5), new Insets(0, 0, 0, 0));

        addCell(controls, new JLabel("Original value:"), 0, 1, GridBagConstraints.WEST,
                new Insets(0, 0, 5, 5), GridBagConstraints.NONE);
        JPanel originalCard = new JPanel(new CardLayout(0, 0));
        addCell(controls, originalCard, 1, 1, GridBagConstraints.CENTER,
                new Insets(0, 0, 5, 5), GridBagConstraints.BOTH);
        txtOriginal = new JTextField();
        txtOriginal.setColumns(10);
        originalCard.add(txtOriginal, "txtOriginal");
        cbOriginal = new JComboBox<>();
        cbOriginal.setModel(new DefaultComboBoxModel<String>(mimeComboItems()));
        cbOriginal.setVisible(false);
        originalCard.add(cbOriginal, "cbOriginal");

        addCell(controls, new JLabel("Replacement:"), 0, 2, GridBagConstraints.WEST,
                new Insets(0, 0, 5, 5), GridBagConstraints.NONE);
        txtReplacement = new JTextField();
        txtReplacement.setEditable(false);
        txtReplacement.setColumns(10);
        addCell(controls, txtReplacement, 1, 2, GridBagConstraints.CENTER,
                new Insets(0, 0, 5, 5), GridBagConstraints.HORIZONTAL);
        JButton btnBrowseReplacement = new JButton("Browse....");
        addCell(controls, btnBrowseReplacement, 2, 2, GridBagConstraints.CENTER,
                new Insets(0, 0, 5, 0), GridBagConstraints.NONE);

        addCell(controls, new JLabel("Name:"), 0, 3, GridBagConstraints.WEST,
                new Insets(0, 0, 5, 5), GridBagConstraints.NONE);
        txtName = new JTextField();
        txtName.setColumns(10);
        addCell(controls, txtName, 1, 3, GridBagConstraints.CENTER,
                new Insets(0, 0, 5, 5), GridBagConstraints.HORIZONTAL);

        addCell(controls, new JLabel("Description:"), 0, 4, GridBagConstraints.WEST,
                new Insets(0, 0, 5, 5), GridBagConstraints.NONE);
        txtDesc = new JTextField();
        txtDesc.setColumns(10);
        addCell(controls, txtDesc, 1, 4, GridBagConstraints.CENTER,
                new Insets(0, 0, 5, 5), GridBagConstraints.HORIZONTAL);

        addCell(controls, new JLabel("MIME Type:"), 0, 5, GridBagConstraints.WEST,
                new Insets(0, 0, 5, 5), GridBagConstraints.NONE);
        cbMime = new JComboBox<>();
        cbMime.setModel(new DefaultComboBoxModel<String>(mimeComboItems()));
        addCell(controls, cbMime, 1, 5, GridBagConstraints.CENTER,
                new Insets(0, 0, 5, 5), GridBagConstraints.HORIZONTAL);

        JPanel bottom = new JPanel();
        GridBagConstraints gbcBottom = new GridBagConstraints();
        gbcBottom.anchor = GridBagConstraints.WEST;
        gbcBottom.insets = new Insets(0, 0, 0, 5);
        gbcBottom.fill = GridBagConstraints.VERTICAL;
        gbcBottom.gridx = 1;
        gbcBottom.gridy = 6;
        controls.add(bottom, gbcBottom);
        GridBagLayout bottomLayout = new GridBagLayout();
        bottomLayout.columnWidths = new int[] { 0, 0, 0, 0 };
        bottomLayout.rowHeights = new int[] { 0, 0 };
        bottomLayout.columnWeights = new double[] { 0.0, 0.0, 0.0, 0.0 };
        bottomLayout.rowWeights = new double[] { 0.0, Double.MIN_VALUE };
        bottom.setLayout(bottomLayout);
        addBottomButton(bottom, btnAdd, 0, new Insets(0, 0, 0, 5), GridBagConstraints.CENTER);
        addBottomButton(bottom, btnEdit, 1, new Insets(0, 0, 0, 5), GridBagConstraints.CENTER);
        addBottomButton(bottom, btnRemove, 2, new Insets(0, 0, 0, 5), GridBagConstraints.SOUTH);
        addBottomButton(bottom, btnCancel, 3, new Insets(0, 0, 0, 0), GridBagConstraints.CENTER);

        wireSelectorRadios(selectorGroup, txtOriginal, cbOriginal);
        wireDropAndBrowse(txtReplacement, btnBrowseReplacement);
        Utils.addRCMenuMouseListener(txtOriginal);
        Utils.addRCMenuMouseListener(txtReplacement);
        Utils.addRCMenuMouseListener(txtName);
        Utils.addRCMenuMouseListener(txtDesc);
    }

    private void buildDeleteControls(JPanel controls) {
        GridBagLayout layout = new GridBagLayout();
        layout.columnWidths = new int[] { 0, 0, 0 };
        layout.rowHeights = new int[] { 0, 0, 0, 0 };
        layout.columnWeights = new double[] { 0.0, 1.0, Double.MIN_VALUE };
        layout.rowWeights = new double[] { 1.0, 1.0, 1.0, Double.MIN_VALUE };
        controls.setLayout(layout);

        addCell(controls, new JLabel("Type:"), 0, 0, GridBagConstraints.CENTER,
                new Insets(0, 0, 5, 5), GridBagConstraints.NONE);
        JPanel selectorPanel = new JPanel();
        GridBagConstraints gbcSelector = new GridBagConstraints();
        gbcSelector.anchor = GridBagConstraints.WEST;
        gbcSelector.insets = new Insets(0, 0, 5, 0);
        gbcSelector.fill = GridBagConstraints.VERTICAL;
        gbcSelector.gridx = 1;
        gbcSelector.gridy = 0;
        controls.add(selectorPanel, gbcSelector);
        GridBagLayout selectorLayout = new GridBagLayout();
        selectorLayout.columnWidths = new int[] { 0, 0, 0 };
        selectorLayout.rowHeights = new int[] { 0, 0 };
        selectorLayout.columnWeights = new double[] { 0.0, 0.0, 0.0 };
        selectorLayout.rowWeights = new double[] { 0.0, Double.MIN_VALUE };
        selectorPanel.setLayout(selectorLayout);
        ButtonGroup selectorGroup = buildSelectorRadios(selectorPanel,
                new Insets(0, 0, 0, 5), new Insets(0, 0, 0, 5), new Insets(0, 0, 0, 0));

        addCell(controls, new JLabel("Value:"), 0, 1, GridBagConstraints.EAST,
                new Insets(0, 0, 5, 5), GridBagConstraints.NONE);
        JPanel valueCard = new JPanel(new CardLayout(0, 0));
        addCell(controls, valueCard, 1, 1, GridBagConstraints.CENTER,
                new Insets(0, 0, 5, 0), GridBagConstraints.BOTH);
        txtValue = new JTextField();
        txtValue.setColumns(10);
        valueCard.add(txtValue, "txtValue");
        cbValue = new JComboBox<>();
        cbValue.setVisible(false);
        cbValue.setModel(new DefaultComboBoxModel<String>(mimeComboItems()));
        valueCard.add(cbValue, "cbValue");

        JPanel bottom = new JPanel();
        addCell(controls, bottom, 1, 2, GridBagConstraints.CENTER,
                new Insets(0, 0, 0, 0), GridBagConstraints.BOTH);
        GridBagLayout bottomLayout = new GridBagLayout();
        bottomLayout.columnWidths = new int[] { 0, 0, 0, 0, 0, 0, 0, 0 };
        bottomLayout.rowHeights = new int[] { 0, 0 };
        bottomLayout.columnWeights = new double[] { 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE };
        bottomLayout.rowWeights = new double[] { 0.0, Double.MIN_VALUE };
        bottom.setLayout(bottomLayout);
        addBottomButton(bottom, btnAdd, 0, new Insets(0, 0, 5, 5), GridBagConstraints.CENTER);
        addBottomButton(bottom, btnEdit, 1, new Insets(0, 0, 5, 5), GridBagConstraints.CENTER);
        addBottomButton(bottom, btnRemove, 2, new Insets(0, 0, 5, 5), GridBagConstraints.SOUTH);
        addBottomButton(bottom, btnCancel, 3, new Insets(0, 0, 5, 5), GridBagConstraints.CENTER);

        wireSelectorRadios(selectorGroup, txtValue, cbValue);
        Utils.addRCMenuMouseListener(txtValue);
    }

    /** The name/ID/MIME radio trio shared by the replace and delete panels. */
    private ButtonGroup buildSelectorRadios(JPanel panel, Insets nameInsets, Insets idInsets,
            Insets mimeInsets) {
        rbName = new JRadioButton(AttachmentSelector.NAME.label());
        rbName.setSelected(true);
        addBottomButton(panel, rbName, 0, nameInsets, GridBagConstraints.CENTER);

        rbId = new JRadioButton(AttachmentSelector.ID.label());
        addBottomButton(panel, rbId, 1, idInsets, GridBagConstraints.CENTER);

        rbMime = new JRadioButton(AttachmentSelector.MIME_TYPE.label());
        addBottomButton(panel, rbMime, 2, mimeInsets, GridBagConstraints.CENTER);

        ButtonGroup group = new ButtonGroup();
        group.add(rbName);
        group.add(rbId);
        group.add(rbMime);
        return group;
    }

    /* Listeners: one copy of each behavior for all three operations. */

    private void wireSelectorRadios(ButtonGroup ignored, JTextField field, JComboBox<String> combo) {
        rbName.addActionListener(e -> {
            combo.setVisible(false);
            field.setVisible(true);
            field.setText("");
        });

        rbId.addActionListener(e -> {
            combo.setVisible(false);
            field.setVisible(true);
            field.setText("1");
        });

        rbMime.addActionListener(e -> {
            field.setVisible(false);
            combo.setVisible(true);
            combo.setSelectedIndex(0);
        });

        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                if (!rbId.isSelected()) {
                    return;
                }

                try {
                    int id = Integer.parseInt(field.getText());

                    if (id < 1) {
                        field.setText("1");
                    }
                } catch (NumberFormatException e1) {
                    field.setText("1");
                }
            }
        });
    }

    private void wireDropAndBrowse(JTextField field, JButton browse) {
        new FileDrop(field, files -> {
            try {
                if (!files[0].isDirectory()) {
                    field.setText(files[0].getCanonicalPath());
                }
            } catch (IOException e) {
                host.logError("Error: could not resolve dropped attachment: " + e + "\n");
            }
        });

        browse.addActionListener(e -> {
            JFileChooser fileChooser = host.chooser();
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fileChooser.setDialogTitle("Select attachment");
            fileChooser.setMultiSelectionEnabled(false);
            fileChooser.resetChoosableFileFilters();
            fileChooser.setAcceptAllFileFilterUsed(true);

            int open = fileChooser.showOpenDialog(host.dialogParent());

            if (open == JFileChooser.APPROVE_OPTION) {
                File f = fileChooser.getSelectedFile();

                if (f.exists()) {
                    try {
                        field.setText(f.getCanonicalPath());
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        });
    }

    private void wireTableSelection() {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (model.getRowCount() == 0 || !table.isEnabled()) {
                    return;
                }

                int selection = table.getSelectedRow();

                if (selection != -1) {
                    loadRow(selection);
                    enterEditMode();
                }
            }
        });
    }

    private void wireButtons() {
        btnAdd.addActionListener(e -> onAdd());
        btnEdit.addActionListener(e -> onEdit());
        btnRemove.addActionListener(e -> onRemove());
        btnCancel.addActionListener(e -> onCancel());
    }

    private void onAdd() {
        switch (operation) {
            case ADD -> {
                if (txtFile.getText().trim().isEmpty()) {
                    host.showError(operation.mandatoryMessage());
                    return;
                }

                model.addRow(new String[] { txtFile.getText(), txtName.getText().trim(),
                        txtDesc.getText().trim(), selectedMime() });
                afterRowChange();
                resetControls();
            }
            case REPLACE -> {
                SelectorValue selection = resolveSelector(txtOriginal, cbOriginal);

                if (selection.value().isEmpty() || txtReplacement.getText().isEmpty()) {
                    host.showError(operation.mandatoryMessage());
                    return;
                }

                model.addRow(new String[] { selection.label(), selection.value(),
                        txtReplacement.getText(), txtName.getText().trim(), txtDesc.getText().trim(),
                        selectedMime() });
                afterRowChange();
                resetControls();
            }
            case DELETE -> {
                SelectorValue selection = resolveSelector(txtValue, cbValue);

                if (selection.value().isEmpty()) {
                    host.showError(operation.mandatoryMessage());
                    return;
                }

                model.addRow(new String[] { selection.label(), selection.value() });
                afterRowChange();
                resetControls();
                table.clearSelection();
            }
        }
    }

    private void onEdit() {
        int selection = table.getSelectedRow();

        switch (operation) {
            case ADD -> {
                if (txtFile.getText().trim().isEmpty()) {
                    host.showError(operation.mandatoryMessage());
                    return;
                }

                model.setValueAt(txtFile.getText().trim(), selection, 0);
                model.setValueAt(txtName.getText().trim(), selection, 1);
                model.setValueAt(txtDesc.getText().trim(), selection, 2);
                model.setValueAt(selectedMime(), selection, 3);
                afterRowChange();
                resetControls();
                enterBrowseMode();
                table.clearSelection();
            }
            case REPLACE -> {
                SelectorValue selector = resolveSelector(txtOriginal, cbOriginal);

                if (selector.value().isEmpty() || txtReplacement.getText().isEmpty()) {
                    host.showError(operation.mandatoryMessage());
                    return;
                }

                model.setValueAt(selector.label(), selection, 0);
                model.setValueAt(selector.value(), selection, 1);
                model.setValueAt(txtReplacement.getText(), selection, 2);
                model.setValueAt(txtName.getText(), selection, 3);
                model.setValueAt(txtDesc.getText(), selection, 4);
                model.setValueAt(selectedMime(), selection, 5);
                afterRowChange();
                enterBrowseMode();
                table.clearSelection();
                resetControls();
            }
            case DELETE -> {
                SelectorValue selector = resolveSelector(txtValue, cbValue);

                if (selector.value().isEmpty()) {
                    host.showError(operation.mandatoryMessage());
                    return;
                }

                model.setValueAt(selector.label(), selection, 0);
                model.setValueAt(selector.value(), selection, 1);
                afterRowChange();
                enterBrowseMode();
                table.clearSelection();
                resetControls();
                table.clearSelection();
            }
        }
    }

    private void onRemove() {
        model.removeRow(table.getSelectedRow());

        switch (operation) {
            case ADD -> {
                resetControls();
                enterBrowseMode();
            }
            case REPLACE, DELETE -> {
                enterBrowseMode();
                table.clearSelection();
                resetControls();
            }
        }
    }

    private void onCancel() {
        switch (operation) {
            case ADD -> {
                resetControls();
                enterBrowseMode();
                table.clearSelection();
            }
            case REPLACE, DELETE -> {
                enterBrowseMode();
                table.clearSelection();
                resetControls();
            }
        }
    }

    private void loadRow(int selection) {
        switch (operation) {
            case ADD -> {
                txtFile.setText(model.valueAt(selection, 0));
                txtName.setText(model.valueAt(selection, 1));
                txtDesc.setText(model.valueAt(selection, 2));
                cbMime.setSelectedItem(model.valueAt(selection, 3));
            }
            case REPLACE -> {
                String type = model.valueAt(selection, 0);
                String orig = model.valueAt(selection, 1);
                String replace = model.valueAt(selection, 2);

                txtReplacement.setText(replace);

                if (type.equals(AttachmentSelector.NAME.label())) {
                    txtOriginal.setVisible(true);
                    cbOriginal.setVisible(false);
                    rbName.setSelected(true);
                    txtOriginal.setText(orig);
                } else if (type.equals(AttachmentSelector.ID.label())) {
                    txtOriginal.setVisible(true);
                    cbOriginal.setVisible(false);
                    rbId.setSelected(true);
                    txtOriginal.setText(orig);
                } else {
                    // Preserved quirk: the original loaded the REPLACEMENT
                    // column into the original-value combo, not the original.
                    txtOriginal.setVisible(false);
                    cbOriginal.setVisible(true);
                    rbMime.setSelected(true);
                    cbOriginal.setSelectedItem(replace);
                }

                txtName.setText(model.valueAt(selection, 3));
                txtDesc.setText(model.valueAt(selection, 4));
                cbMime.setSelectedItem(model.valueAt(selection, 5));
            }
            case DELETE -> {
                String type = model.valueAt(selection, 0);
                String value = model.valueAt(selection, 1);

                if (type.equals(AttachmentSelector.NAME.label())) {
                    rbName.setSelected(true);
                    cbValue.setVisible(false);
                    txtValue.setVisible(true);
                    txtValue.setText(value);
                } else if (type.equals(AttachmentSelector.ID.label())) {
                    rbId.setSelected(true);
                    cbValue.setVisible(false);
                    txtValue.setVisible(true);
                    txtValue.setText(value);
                } else {
                    rbMime.setSelected(true);
                    txtValue.setVisible(false);
                    cbValue.setVisible(true);
                    cbValue.setSelectedItem(value);
                }
            }
        }
    }

    private SelectorValue resolveSelector(JTextField field, JComboBox<String> combo) {
        if (rbName.isSelected()) {
            return new SelectorValue(AttachmentSelector.NAME.label(), field.getText().trim());
        } else if (rbId.isSelected()) {
            return new SelectorValue(AttachmentSelector.ID.label(), field.getText());
        }

        return new SelectorValue(AttachmentSelector.MIME_TYPE.label(),
                combo.getSelectedItem().toString());
    }

    private void enterEditMode() {
        table.setEnabled(false);
        btnAdd.setEnabled(false);
        btnRemove.setEnabled(true);
        btnEdit.setEnabled(true);
        btnCancel.setEnabled(true);
    }

    private void enterBrowseMode() {
        table.setEnabled(true);
        btnAdd.setEnabled(true);
        btnEdit.setEnabled(false);
        btnRemove.setEnabled(false);
        btnCancel.setEnabled(false);
    }

    private void afterRowChange() {
        Utils.adjustColumnPreferredWidths(table);
        table.revalidate();
    }

    private void resetControls() {
        switch (operation) {
            case ADD -> {
                txtFile.setText("");
                txtName.setText("");
                txtDesc.setText("");
                cbMime.setSelectedIndex(0);
            }
            case REPLACE -> {
                txtOriginal.setText("");
                txtReplacement.setText("");
                txtName.setText("");
                txtDesc.setText("");
                cbMime.setSelectedIndex(0);
                // setSelected does not fire the radio's listener, so the
                // card visibility is restored explicitly, like the original.
                rbName.setSelected(true);
                txtOriginal.setVisible(true);
                cbOriginal.setVisible(false);
            }
            case DELETE -> {
                rbName.setSelected(true);
                cbValue.setVisible(false);
                txtValue.setVisible(true);
                txtValue.setText("");
            }
        }
    }

    private String selectedMime() {
        return cbMime.getSelectedItem().toString();
    }

    /* Command lines: the former setCmdLineAttachments* trio of the god class. */

    /**
     * The plain mkvpropedit arguments for this operation's rows: {@code
     * --add-attachment}, {@code --replace-attachment} or {@code
     * --delete-attachment} with their metadata options, assembled exactly like
     * the original builders (including the unescaped ID originals).
     *
     * @return the plain command-line fragment, empty for no rows
     */
    public String cmdLine() {
        return buildCmdLines().plain();
    }

    /**
     * The Opt-file variant of {@link #cmdLine()} with
     * {@link Utils#escapeName} applied where the original applied it.
     *
     * @return the Opt command-line fragment, empty for no rows
     */
    public String cmdLineOpt() {
        return buildCmdLines().opt();
    }

    private CmdLines buildCmdLines() {
        StringBuilder plain = new StringBuilder();
        StringBuilder opt = new StringBuilder();

        for (int i = 0; i < model.getRowCount(); i++) {
            switch (operation) {
                case ADD -> appendAddArgs(i, plain, opt);
                case REPLACE -> appendReplaceArgs(i, plain, opt);
                case DELETE -> appendDeleteArgs(i, plain, opt);
            }
        }

        return new CmdLines(plain.toString(), opt.toString());
    }

    private void appendAddArgs(int row, StringBuilder plain, StringBuilder opt) {
        String file = model.valueAt(row, 0);
        appendMetaArgs(model.valueAt(row, 1), model.valueAt(row, 2), model.valueAt(row, 3),
                plain, opt);

        plain.append(" --add-attachment \"").append(file).append('"');
        opt.append(" --add-attachment \"").append(Utils.escapeName(file)).append('"');
    }

    private void appendReplaceArgs(int row, StringBuilder plain, StringBuilder opt) {
        String type = model.valueAt(row, 0);
        String orig = model.valueAt(row, 1);
        String replace = model.valueAt(row, 2);
        appendMetaArgs(model.valueAt(row, 3), model.valueAt(row, 4), model.valueAt(row, 5),
                plain, opt);

        switch (AttachmentSelector.fromLabel(type)) {
            case NAME -> {
                plain.append(" --replace-attachment \"name:").append(orig).append(':')
                        .append(replace).append('"');
                opt.append(" --replace-attachment \"name:").append(Utils.escapeName(orig))
                        .append(':').append(Utils.escapeName(replace)).append('"');
            }
            case ID -> {
                // The ID original value stays unescaped, like the original.
                plain.append(" --replace-attachment \"").append(orig).append(':')
                        .append(replace).append('"');
                opt.append(" --replace-attachment \"").append(orig).append(':')
                        .append(Utils.escapeName(replace)).append('"');
            }
            case MIME_TYPE -> {
                plain.append(" --replace-attachment \"mime-type:").append(orig).append(':')
                        .append(replace).append('"');
                opt.append(" --replace-attachment \"mime-type:").append(Utils.escapeName(orig))
                        .append(':').append(Utils.escapeName(replace)).append('"');
            }
        }
    }

    private void appendDeleteArgs(int row, StringBuilder plain, StringBuilder opt) {
        String type = model.valueAt(row, 0);
        String value = model.valueAt(row, 1);

        switch (AttachmentSelector.fromLabel(type)) {
            case NAME -> {
                plain.append(" --delete-attachment \"name:").append(value).append('"');
                opt.append(" --delete-attachment \"name:").append(Utils.escapeName(value))
                        .append('"');
            }
            case ID -> {
                plain.append(" --delete-attachment \"").append(value).append('"');
                opt.append(" --delete-attachment \"").append(value).append('"');
            }
            case MIME_TYPE -> {
                plain.append(" --delete-attachment \"mime-type:").append(value).append('"');
                opt.append(" --delete-attachment \"mime-type:").append(Utils.escapeName(value))
                        .append('"');
            }
        }
    }

    /**
     * The shared {@code --attachment-name/-description/-mime-type} preamble of
     * add and replace rows: emitted per field only when the field is
     * non-empty, as both original builders did.
     */
    private static void appendMetaArgs(String name, String desc, String mime,
            StringBuilder plain, StringBuilder opt) {
        if (!name.isEmpty()) {
            plain.append(" --attachment-name \"").append(name).append('"');
            opt.append(" --attachment-name \"").append(Utils.escapeName(name)).append('"');
        }

        if (!desc.isEmpty()) {
            plain.append(" --attachment-description \"").append(desc).append('"');
            opt.append(" --attachment-description \"").append(Utils.escapeName(desc)).append('"');
        }

        if (!mime.isEmpty()) {
            plain.append(" --attachment-mime-type \"").append(mime).append('"');
            opt.append(" --attachment-mime-type \"").append(Utils.escapeName(mime)).append('"');
        }
    }

    private record CmdLines(String plain, String opt) {
    }

    private record SelectorValue(String label, String value) {
    }

    /* Table sizing: the former resizeColumns of the god class. */

    /**
     * Resizes the columns to the operation's relative widths against the
     * table's parent (the scroll pane), like the window-resize handler did
     * for all three attachment tables.
     */
    void resizeColumns() {
        double[] colSizes = operation.columnSizes();
        TableColumnModel columnModel = table.getColumnModel();
        int[] colWidths = new int[colSizes.length];

        int parWidth = table.getParent().getWidth();

        int total = 0;
        for (int i = 0; i < colSizes.length; i++) {
            colWidths[i] = (int) (parWidth * colSizes[i]);
            total += colWidths[i];
        }

        colWidths[colWidths.length - 1] += parWidth - total;

        for (int i = 0; i < colSizes.length; i++) {
            columnModel.getColumn(i).setMinWidth(colWidths[i]);
            columnModel.getColumn(i).setPreferredWidth(colWidths[i]);
        }

        table.revalidate();
    }

    /**
     * Items for the attachment MIME combos (add mime / replace orig / replace
     * mime / delete value).
     *
     * <p>
     * Returns a fresh array so the shared {@link MkvStrings} resource list is
     * never mutated (the old {@code remove(0)} call dropped the first element
     * from every later combo — issue #4). Skips the corrupted {@code _}
     * artifact in {@code mimetypes.txt} and keeps a leading empty item: add/
     * replace treat an empty MIME as "omit --attachment-mime-type"
     * (auto-detect), while replace-orig/delete reject empty values in their
     * action listeners.
     * </p>
     */
    private String[] mimeComboItems() {
        List<String> items = new ArrayList<>();
        items.add("");

        for (String mime : mkvStrings.getMimeTypeList()) {
            if (mime.isEmpty() || "_".equals(mime)) {
                continue;
            }

            items.add(mime);
        }

        return items.toArray(new String[items.size()]);
    }

    /* Layout helpers */

    private void addCell(JPanel parent, java.awt.Component component, int column, int row,
            int anchor, Insets insets, int fill) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = anchor;
        gbc.insets = insets;
        gbc.fill = fill;
        gbc.gridx = column;
        gbc.gridy = row;
        parent.add(component, gbc);
    }

    private void addBottomButton(JPanel parent, java.awt.Component component, int column,
            Insets insets, int anchor) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = insets;
        gbc.anchor = anchor;
        gbc.gridx = column;
        gbc.gridy = 0;
        parent.add(component, gbc);
    }

    /* Package-private access for the god class, the tests and the harness. */

    AttachmentOperation operation() {
        return operation;
    }

    AttachmentModel model() {
        return model;
    }

    JTable table() {
        return table;
    }

    JButton addButton() {
        return btnAdd;
    }

    JButton editButton() {
        return btnEdit;
    }

    JButton removeButton() {
        return btnRemove;
    }

    JButton cancelButton() {
        return btnCancel;
    }

    /** The attachment source file field (add panel only). */
    JTextField fileField() {
        return txtFile;
    }

    /** The name field (add and replace panels). */
    JTextField nameField() {
        return txtName;
    }

    /** The description field (add and replace panels). */
    JTextField descriptionField() {
        return txtDesc;
    }

    /** The MIME type combo of the MIME Type row (add and replace panels). */
    JComboBox<String> mimeCombo() {
        return cbMime;
    }

    /** The "Attachment name" selector radio (replace and delete panels). */
    JRadioButton nameSelector() {
        return rbName;
    }

    /** The "Attachment ID" selector radio (replace and delete panels). */
    JRadioButton idSelector() {
        return rbId;
    }

    /** The MIME type selector radio (replace and delete panels). */
    JRadioButton mimeSelector() {
        return rbMime;
    }

    /** The original value text field (replace panel only). */
    JTextField originalField() {
        return txtOriginal;
    }

    /** The original value MIME combo (replace panel only). */
    JComboBox<String> originalCombo() {
        return cbOriginal;
    }

    /** The replacement file field (replace panel only). */
    JTextField replacementField() {
        return txtReplacement;
    }

    /** The delete value text field (delete panel only). */
    JTextField valueField() {
        return txtValue;
    }

    /** The delete value MIME combo (delete panel only). */
    JComboBox<String> valueCombo() {
        return cbValue;
    }
}
