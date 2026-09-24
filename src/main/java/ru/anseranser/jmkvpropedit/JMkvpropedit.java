/*
 * Copyright (c) 2012-2018 Bruno Barbieri
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list
 * of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this list
 * of conditions and the following disclaimer in the documentation and/or other materials
 * provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package ru.anseranser.jmkvpropedit;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * The application window: UI shell only — frame, tabs, listener wiring and
 * calls into the extracted modules ({@link CommandBuilder}, {@link TrackPanel},
 * {@link AttachmentPanel}, {@link ProcessRunner}, {@link IniStore},
 * {@link FileScanner}). Issue #16 collapses the former god class down to this
 * wiring; settings-to-arguments, process IO, INI IO and folder scans live in
 * the modules, not here.
 */
public class JMkvpropedit implements AttachmentPanel.Host {

    private static final String VERSION_NUMBER = BuildVersion.VERSION;
    private static String[] argsArray;

    private SwingWorker<Void, Void> worker;

    private final IniStore iniStore = new IniStore(new File("JMkvpropedit.ini"));
    private static final MkvStrings mkvStrings = new MkvStrings();
    private final CommandBuilder commandBuilder = new CommandBuilder();

    private final JFileChooser chooser = new JFileChooser(System.getProperty("user.home")) {
        private static final long serialVersionUID = 1L;

        @Override
        public int showOpenDialog(Component parent) {
            super.setSelectedFile(new File(""));

            return super.showOpenDialog(parent);
        }

        @Override
        public void approveSelection() {
            if (!super.isMultiSelectionEnabled() || super.getSelectedFiles().length == 1) {
                if (!this.getSelectedFile().exists()) {
                    return;
                }
            }

            super.approveSelection();
        }
    };

    private final FileFilter EXE_EXT_FILTER = new FileNameExtensionFilter("Executable files (*.exe)", "exe");

    private final FileFilter MATROSKA_EXT_FILTER = new FileNameExtensionFilter(
            "Matroska files (*.mkv; *.mka; *.mk3d; *.webm; *.mks)", "mkv", "mka", "mk3d", "webm", "mks");

    private final FileFilter TXT_EXT_FILTER = new FileNameExtensionFilter("Plain text files (*.txt)", "txt");

    private final FileFilter XML_EXT_FILTER = new FileNameExtensionFilter("XML files (*.xml)", "xml");

    private String[] cmdLineGeneral;
    private String[] cmdLineGeneralOpt;

    private String[] cmdLineVideo;
    private String[] cmdLineVideoOpt;

    private String[] cmdLineAudio;
    private String[] cmdLineAudioOpt;

    private String[] cmdLineSubtitle;
    private String[] cmdLineSubtitleOpt;

    private List<String> cmdLineBatch;
    private List<String[]> cmdLineBatchOpt;

    // Window controls
    private Dimension frmJMkvpropeditDim = new Dimension(0, 0);
    private JFrame frmJMkvpropedit;
    private JTabbedPane pnlTabs;
    private JButton btnProcessFiles;
    private JButton btnGenerateCmdLine;

    // Input tab controls
    private DefaultListModel<String> modelFiles;
    private JList<String> listFiles;
    private JButton btnAddFiles;
    private JButton btnAddFolder;
    private JButton btnRemoveFiles;
    private JButton btnTopFiles;
    private JButton btnUpFiles;
    private JButton btnDownFiles;
    private JButton btnBottomFiles;
    private JButton btnClearFiles;

    // General tab controls
    private JCheckBox chbTitleGeneral;
    private JTextField txtTitleGeneral;
    private JCheckBox chbNumbGeneral;
    private JLabel lblNumbStartGeneral;
    private JTextField txtNumbStartGeneral;
    private JLabel lblNumbPadGeneral;
    private JTextField txtNumbPadGeneral;
    private JLabel lblNumbExplainGeneral;
    private JCheckBox chbChapters;
    private JComboBox<String> cbChapters;
    private JButton btnBrowseChapters;
    private JComboBox<String> cbExtChapters;
    private JTextField txtChapters;
    private JCheckBox chbTags;
    private JComboBox<String> cbTags;
    private JTextField txtTags;
    private JButton btnBrowseTags;
    private JComboBox<String> cbExtTags;
    private JCheckBox chbExtraCmdGeneral;
    private JTextField txtExtraCmdGeneral;

    // Track tabs: one unified panel per track type (issue #12)
    private TrackPanel videoPanel;
    private TrackPanel audioPanel;
    private TrackPanel subtitlePanel;

    // Attachments tab: one unified panel per attachment operation (issue #13)
    private JTabbedPane pnlAttachments;
    private AttachmentPanel attachmentAddPanel;
    private AttachmentPanel attachmentReplacePanel;
    private AttachmentPanel attachmentDeletePanel;

    // Option tab controls
    private JPanel pnlOptions;
    private JTextField txtMkvPropExe;
    private JCheckBox chbMkvPropExeDef;
    private JButton btnBrowseMkvPropExe;

    // Output tab controls
    private JTextArea txtOutput;

    /**
     * Launch the application.
     */
    public static void main(final String[] args) {
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                try {
                    argsArray = args;
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                    JMkvpropedit window = new JMkvpropedit();
                    window.frmJMkvpropedit.setVisible(true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * Create the application.
     */
    public JMkvpropedit() {
        initialize();
        parseFiles(argsArray);
    }

    /**
     * Initialize the contents of the frame: build every tab, then wire the
     * listeners. No module logic runs here — only construction and wiring.
     */
    private void initialize() {
        frmJMkvpropedit = new JFrame();
        frmJMkvpropedit.setTitle("JMkvpropedit " + VERSION_NUMBER);
        frmJMkvpropedit.setBounds(100, 100, 760, 500);
        frmJMkvpropedit.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        chooser.setDialogType(JFileChooser.OPEN_DIALOG);
        chooser.setFileHidingEnabled(true);

        pnlTabs = new JTabbedPane(JTabbedPane.TOP);
        pnlTabs.setBorder(new EmptyBorder(10, 10, 0, 10));
        frmJMkvpropedit.getContentPane().add(pnlTabs, BorderLayout.CENTER);

        buildInputTab();
        buildGeneralTab();
        buildTrackTabs();
        buildAttachmentsTab();
        buildOptionsTab();
        buildOutputTab();
        buildBottomButtons();

        wireRightClickMenus();
        wireWindowListeners();
        wireFileDrop();
        wireFileListToolbar();
        wireGeneralTab();
        wireExecutableOptions();
        wireActionButtons();
    }

    /* Tab construction */

    private void buildInputTab() {
        JPanel pnlInput = new JPanel();
        pnlInput.setBorder(new EmptyBorder(10, 10, 10, 0));
        pnlTabs.addTab("Input", null, pnlInput, null);
        pnlInput.setLayout(new BorderLayout(0, 0));

        JScrollPane spFiles = new JScrollPane();
        spFiles.setViewportBorder(null);
        pnlInput.add(spFiles);

        modelFiles = new DefaultListModel<String>();
        listFiles = new JList<String>(modelFiles);
        spFiles.setViewportView(listFiles);

        JPanel pnlListToolbar = new JPanel();
        pnlListToolbar.setBorder(new EmptyBorder(0, 5, 0, 5));
        pnlInput.add(pnlListToolbar, BorderLayout.EAST);
        pnlListToolbar.setLayout(new BoxLayout(pnlListToolbar, BoxLayout.Y_AXIS));

        btnAddFiles = createIconButton("/list-add.png", "Add files");
        pnlListToolbar.add(btnAddFiles);
        pnlListToolbar.add(Box.createVerticalStrut(10));

        btnAddFolder = createIconButton("/list-add-folder.png", "Add folder");
        pnlListToolbar.add(btnAddFolder);
        pnlListToolbar.add(Box.createVerticalStrut(10));

        btnRemoveFiles = createIconButton("/list-remove.png", "Remove selected files");
        pnlListToolbar.add(btnRemoveFiles);
        pnlListToolbar.add(Box.createVerticalStrut(10));

        btnTopFiles = createIconButton("/go-top.png", "Move selected files to the top");
        pnlListToolbar.add(btnTopFiles);
        pnlListToolbar.add(Box.createVerticalStrut(10));

        btnUpFiles = createIconButton("/go-up.png", "Move selected files up");
        pnlListToolbar.add(btnUpFiles);
        pnlListToolbar.add(Box.createVerticalStrut(10));

        btnDownFiles = createIconButton("/go-down.png", "Move selected files down");
        pnlListToolbar.add(btnDownFiles);
        pnlListToolbar.add(Box.createVerticalStrut(10));

        btnBottomFiles = createIconButton("/go-bottom.png", "Move selected files to the bottom");
        pnlListToolbar.add(btnBottomFiles);
        pnlListToolbar.add(Box.createVerticalStrut(10));

        btnClearFiles = createIconButton("/edit-clear.png", "Clear file list");
        pnlListToolbar.add(btnClearFiles);
    }

    private void buildGeneralTab() {
        JPanel pnlGeneral = new JPanel();
        pnlGeneral.setBorder(new EmptyBorder(10, 10, 10, 10));
        pnlTabs.addTab("General", null, pnlGeneral, null);
        GridBagLayout gbl_pnlGeneral = new GridBagLayout();
        gbl_pnlGeneral.columnWidths = new int[] { 75, 655, 0 };
        gbl_pnlGeneral.rowHeights = new int[] { 0, 0, 0, 0, 24, 0, 0, 0, 0, 0, 0, 0 };
        gbl_pnlGeneral.columnWeights = new double[] { 0.0, 1.0, Double.MIN_VALUE };
        gbl_pnlGeneral.rowWeights = new double[] { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0,
                Double.MIN_VALUE };
        pnlGeneral.setLayout(gbl_pnlGeneral);

        chbTitleGeneral = new JCheckBox("Title:");
        GridBagConstraints gbc_chbTitleGeneral = new GridBagConstraints();
        gbc_chbTitleGeneral.insets = new Insets(0, 0, 5, 5);
        gbc_chbTitleGeneral.anchor = GridBagConstraints.WEST;
        gbc_chbTitleGeneral.gridx = 0;
        gbc_chbTitleGeneral.gridy = 0;
        pnlGeneral.add(chbTitleGeneral, gbc_chbTitleGeneral);

        txtTitleGeneral = new JTextField();
        txtTitleGeneral.setEnabled(false);
        GridBagConstraints gbc_txtTitleGeneral = new GridBagConstraints();
        gbc_txtTitleGeneral.insets = new Insets(0, 0, 5, 0);
        gbc_txtTitleGeneral.fill = GridBagConstraints.HORIZONTAL;
        gbc_txtTitleGeneral.gridx = 1;
        gbc_txtTitleGeneral.gridy = 0;
        pnlGeneral.add(txtTitleGeneral, gbc_txtTitleGeneral);
        txtTitleGeneral.setColumns(10);

        JPanel pnlNumbControlsGeneral = new JPanel();
        FlowLayout fl_pnlNumbControlsGeneral = (FlowLayout) pnlNumbControlsGeneral.getLayout();
        fl_pnlNumbControlsGeneral.setVgap(0);
        fl_pnlNumbControlsGeneral.setAlignment(FlowLayout.LEFT);
        GridBagConstraints gbc_pnlNumbControlsGeneral = new GridBagConstraints();
        gbc_pnlNumbControlsGeneral.insets = new Insets(0, 0, 5, 0);
        gbc_pnlNumbControlsGeneral.fill = GridBagConstraints.BOTH;
        gbc_pnlNumbControlsGeneral.gridx = 1;
        gbc_pnlNumbControlsGeneral.gridy = 1;
        pnlGeneral.add(pnlNumbControlsGeneral, gbc_pnlNumbControlsGeneral);

        chbNumbGeneral = new JCheckBox("Numbering:");
        chbNumbGeneral.setEnabled(false);
        pnlNumbControlsGeneral.add(chbNumbGeneral);

        pnlNumbControlsGeneral.add(Box.createHorizontalStrut(10));

        lblNumbStartGeneral = new JLabel("Start");
        lblNumbStartGeneral.setEnabled(false);
        pnlNumbControlsGeneral.add(lblNumbStartGeneral);

        txtNumbStartGeneral = new JTextField();
        txtNumbStartGeneral.setEnabled(false);
        txtNumbStartGeneral.setText("1");
        pnlNumbControlsGeneral.add(txtNumbStartGeneral);
        txtNumbStartGeneral.setColumns(10);

        pnlNumbControlsGeneral.add(Box.createHorizontalStrut(5));

        lblNumbPadGeneral = new JLabel("Padding");
        lblNumbPadGeneral.setEnabled(false);
        pnlNumbControlsGeneral.add(lblNumbPadGeneral);

        txtNumbPadGeneral = new JTextField();
        txtNumbPadGeneral.setEnabled(false);
        txtNumbPadGeneral.setText("1");
        pnlNumbControlsGeneral.add(txtNumbPadGeneral);
        txtNumbPadGeneral.setColumns(10);

        lblNumbExplainGeneral = new JLabel(
                "      To use it, add {num} to the title (e.g. \"My Title {num}\"). Use {file_name} to use the file name as the title.");
        lblNumbExplainGeneral.setEnabled(false);
        GridBagConstraints gbc_lblNumbExplainGeneral = new GridBagConstraints();
        gbc_lblNumbExplainGeneral.insets = new Insets(0, 0, 10, 0);
        gbc_lblNumbExplainGeneral.anchor = GridBagConstraints.NORTHWEST;
        gbc_lblNumbExplainGeneral.gridx = 1;
        gbc_lblNumbExplainGeneral.gridy = 2;
        pnlGeneral.add(lblNumbExplainGeneral, gbc_lblNumbExplainGeneral);

        chbChapters = new JCheckBox("Chapters:");
        GridBagConstraints gbc_chbChapters = new GridBagConstraints();
        gbc_chbChapters.anchor = GridBagConstraints.WEST;
        gbc_chbChapters.insets = new Insets(0, 0, 5, 5);
        gbc_chbChapters.gridx = 0;
        gbc_chbChapters.gridy = 3;
        pnlGeneral.add(chbChapters, gbc_chbChapters);

        cbChapters = new JComboBox<String>();
        cbChapters.setEnabled(false);
        cbChapters.setModel(new DefaultComboBoxModel<String>(
                new String[] { "Remove", "From file:", "Match file name with suffix:" }));
        cbChapters.setPrototypeDisplayValue("Match file name with suffix:  ");
        GridBagConstraints gbc_cbChapters = new GridBagConstraints();
        gbc_cbChapters.insets = new Insets(0, 0, 5, 0);
        gbc_cbChapters.anchor = GridBagConstraints.WEST;
        gbc_cbChapters.gridx = 1;
        gbc_cbChapters.gridy = 3;
        pnlGeneral.add(cbChapters, gbc_cbChapters);

        GridBagConstraints gbc_verticalStrut7 = new GridBagConstraints();
        gbc_verticalStrut7.insets = new Insets(0, 0, 5, 5);
        gbc_verticalStrut7.gridx = 0;
        gbc_verticalStrut7.gridy = 4;
        pnlGeneral.add(Box.createVerticalStrut(35), gbc_verticalStrut7);

        JPanel pnlChapControlsGeneral = new JPanel();
        GridBagConstraints gbc_pnlChapControlsGeneral = new GridBagConstraints();
        gbc_pnlChapControlsGeneral.insets = new Insets(0, 0, 5, 0);
        gbc_pnlChapControlsGeneral.fill = GridBagConstraints.BOTH;
        gbc_pnlChapControlsGeneral.gridx = 1;
        gbc_pnlChapControlsGeneral.gridy = 4;
        pnlGeneral.add(pnlChapControlsGeneral, gbc_pnlChapControlsGeneral);
        GridBagLayout gbl_pnlChapControlsGeneral = new GridBagLayout();
        gbl_pnlChapControlsGeneral.columnWidths = new int[] { 0, 0, 0, 0 };
        gbl_pnlChapControlsGeneral.rowHeights = new int[] { 0, 0 };
        gbl_pnlChapControlsGeneral.columnWeights = new double[] { 1.0, 0.0, 0.0, Double.MIN_VALUE };
        gbl_pnlChapControlsGeneral.rowWeights = new double[] { 0.0, Double.MIN_VALUE };
        pnlChapControlsGeneral.setLayout(gbl_pnlChapControlsGeneral);

        txtChapters = new JTextField();
        txtChapters.setVisible(false);
        GridBagConstraints gbc_txtChapters = new GridBagConstraints();
        gbc_txtChapters.fill = GridBagConstraints.HORIZONTAL;
        gbc_txtChapters.insets = new Insets(0, 0, 8, 5);
        gbc_txtChapters.gridx = 0;
        gbc_txtChapters.gridy = 0;
        pnlChapControlsGeneral.add(txtChapters, gbc_txtChapters);
        txtChapters.setColumns(10);

        btnBrowseChapters = new JButton("Browse...");
        btnBrowseChapters.setVisible(false);
        GridBagConstraints gbc_btnBrowseChapters = new GridBagConstraints();
        gbc_btnBrowseChapters.insets = new Insets(0, 5, 10, 5);
        gbc_btnBrowseChapters.anchor = GridBagConstraints.EAST;
        gbc_btnBrowseChapters.gridx = 1;
        gbc_btnBrowseChapters.gridy = 0;
        pnlChapControlsGeneral.add(btnBrowseChapters, gbc_btnBrowseChapters);

        cbExtChapters = new JComboBox<String>();
        cbExtChapters.setVisible(false);
        cbExtChapters.setModel(new DefaultComboBoxModel<String>(new String[] { ".xml", ".txt" }));
        GridBagConstraints gbc_cbExtChapters = new GridBagConstraints();
        gbc_cbExtChapters.insets = new Insets(0, 0, 8, 0);
        gbc_cbExtChapters.gridx = 2;
        gbc_cbExtChapters.gridy = 0;
        pnlChapControlsGeneral.add(cbExtChapters, gbc_cbExtChapters);

        chbTags = new JCheckBox("Tags:");
        GridBagConstraints gbc_chbTags = new GridBagConstraints();
        gbc_chbTags.anchor = GridBagConstraints.WEST;
        gbc_chbTags.insets = new Insets(0, 0, 5, 5);
        gbc_chbTags.gridx = 0;
        gbc_chbTags.gridy = 5;
        pnlGeneral.add(chbTags, gbc_chbTags);

        cbTags = new JComboBox<String>();
        cbTags.setEnabled(false);
        cbTags.setModel(new DefaultComboBoxModel<String>(
                new String[] { "Remove", "From file:", "Match file name with suffix:" }));
        cbTags.setPrototypeDisplayValue("Match file name with suffix:  ");
        GridBagConstraints gbc_cbTags = new GridBagConstraints();
        gbc_cbTags.insets = new Insets(0, 0, 5, 0);
        gbc_cbTags.anchor = GridBagConstraints.WEST;
        gbc_cbTags.gridx = 1;
        gbc_cbTags.gridy = 5;
        pnlGeneral.add(cbTags, gbc_cbTags);

        GridBagConstraints gbc_verticalStrut8 = new GridBagConstraints();
        gbc_verticalStrut8.insets = new Insets(0, 0, 5, 5);
        gbc_verticalStrut8.gridx = 0;
        gbc_verticalStrut8.gridy = 6;
        pnlGeneral.add(Box.createVerticalStrut(35), gbc_verticalStrut8);

        JPanel pnlTagControlsGeneral = new JPanel();
        GridBagConstraints gbc_pnlTagControlsGeneral = new GridBagConstraints();
        gbc_pnlTagControlsGeneral.insets = new Insets(0, 0, 5, 0);
        gbc_pnlTagControlsGeneral.fill = GridBagConstraints.BOTH;
        gbc_pnlTagControlsGeneral.gridx = 1;
        gbc_pnlTagControlsGeneral.gridy = 6;
        pnlGeneral.add(pnlTagControlsGeneral, gbc_pnlTagControlsGeneral);
        GridBagLayout gbl_pnlTagControlsGeneral = new GridBagLayout();
        gbl_pnlTagControlsGeneral.columnWidths = new int[] { 0, 0, 0, 0 };
        gbl_pnlTagControlsGeneral.rowHeights = new int[] { 0, 0 };
        gbl_pnlTagControlsGeneral.columnWeights = new double[] { 1.0, 0.0, 0.0, Double.MIN_VALUE };
        gbl_pnlTagControlsGeneral.rowWeights = new double[] { 0.0, Double.MIN_VALUE };
        pnlTagControlsGeneral.setLayout(gbl_pnlTagControlsGeneral);

        txtTags = new JTextField();
        txtTags.setVisible(false);
        txtTags.setColumns(10);
        GridBagConstraints gbc_txtTags = new GridBagConstraints();
        gbc_txtTags.fill = GridBagConstraints.HORIZONTAL;
        gbc_txtTags.insets = new Insets(0, 0, 8, 5);
        gbc_txtTags.gridx = 0;
        gbc_txtTags.gridy = 0;
        pnlTagControlsGeneral.add(txtTags, gbc_txtTags);

        btnBrowseTags = new JButton("Browse...");
        btnBrowseTags.setVisible(false);
        GridBagConstraints gbc_btnBrowseTags = new GridBagConstraints();
        gbc_btnBrowseTags.insets = new Insets(0, 5, 10, 5);
        gbc_btnBrowseTags.anchor = GridBagConstraints.EAST;
        gbc_btnBrowseTags.gridx = 1;
        gbc_btnBrowseTags.gridy = 0;
        pnlTagControlsGeneral.add(btnBrowseTags, gbc_btnBrowseTags);

        cbExtTags = new JComboBox<String>();
        cbExtTags.setVisible(false);
        cbExtTags.setModel(new DefaultComboBoxModel<String>(new String[] { ".xml", ".txt" }));
        GridBagConstraints gbc_cbExtTags = new GridBagConstraints();
        gbc_cbExtTags.insets = new Insets(0, 0, 8, 0);
        gbc_cbExtTags.gridx = 2;
        gbc_cbExtTags.gridy = 0;
        pnlTagControlsGeneral.add(cbExtTags, gbc_cbExtTags);

        chbExtraCmdGeneral = new JCheckBox("Extra parameters:");
        GridBagConstraints gbc_chbExtraCmdGeneral = new GridBagConstraints();
        gbc_chbExtraCmdGeneral.anchor = GridBagConstraints.WEST;
        gbc_chbExtraCmdGeneral.insets = new Insets(0, 0, 5, 5);
        gbc_chbExtraCmdGeneral.gridx = 0;
        gbc_chbExtraCmdGeneral.gridy = 7;
        pnlGeneral.add(chbExtraCmdGeneral, gbc_chbExtraCmdGeneral);

        txtExtraCmdGeneral = new JTextField();
        txtExtraCmdGeneral.setEnabled(false);
        GridBagConstraints gbc_txtExtraCmdGeneral = new GridBagConstraints();
        gbc_txtExtraCmdGeneral.insets = new Insets(0, 0, 5, 0);
        gbc_txtExtraCmdGeneral.fill = GridBagConstraints.HORIZONTAL;
        gbc_txtExtraCmdGeneral.gridx = 1;
        gbc_txtExtraCmdGeneral.gridy = 7;
        pnlGeneral.add(txtExtraCmdGeneral, gbc_txtExtraCmdGeneral);
        txtExtraCmdGeneral.setColumns(10);
    }

    private void buildTrackTabs() {
        videoPanel = new TrackPanel(TrackType.VIDEO, mkvStrings);
        pnlTabs.addTab(TrackType.VIDEO.tabTitle(), null, videoPanel, null);

        audioPanel = new TrackPanel(TrackType.AUDIO, mkvStrings);
        pnlTabs.addTab(TrackType.AUDIO.tabTitle(), null, audioPanel, null);

        subtitlePanel = new TrackPanel(TrackType.SUBTITLE, mkvStrings);
        pnlTabs.addTab(TrackType.SUBTITLE.tabTitle(), null, subtitlePanel, null);
    }

    private void buildAttachmentsTab() {
        pnlAttachments = new JTabbedPane(JTabbedPane.TOP);
        pnlTabs.addTab("Attachments", null, pnlAttachments, null);

        attachmentAddPanel = new AttachmentPanel(AttachmentOperation.ADD, mkvStrings, this);
        pnlAttachments.addTab(AttachmentOperation.ADD.tabTitle(), null, attachmentAddPanel, null);

        attachmentReplacePanel = new AttachmentPanel(AttachmentOperation.REPLACE, mkvStrings, this);
        pnlAttachments.addTab(AttachmentOperation.REPLACE.tabTitle(), null, attachmentReplacePanel, null);

        attachmentDeletePanel = new AttachmentPanel(AttachmentOperation.DELETE, mkvStrings, this);
        pnlAttachments.addTab(AttachmentOperation.DELETE.tabTitle(), null, attachmentDeletePanel, null);
    }

    private void buildOptionsTab() {
        pnlOptions = new JPanel();
        pnlOptions.setBorder(new EmptyBorder(10, 10, 10, 10));
        pnlTabs.addTab("Options", null, pnlOptions, null);
        GridBagLayout gbl_pnlOptions = new GridBagLayout();
        gbl_pnlOptions.columnWidths = new int[] { 0, 0, 0 };
        gbl_pnlOptions.rowHeights = new int[] { 0, 0, 0, 0 };
        gbl_pnlOptions.columnWeights = new double[] { 0.0, 1.0, Double.MIN_VALUE };
        gbl_pnlOptions.rowWeights = new double[] { 0.0, 0.0, 1.0, Double.MIN_VALUE };
        pnlOptions.setLayout(gbl_pnlOptions);

        JLabel lblMkvPropExe = new JLabel("Mkvpropedit executable:");
        lblMkvPropExe.setHorizontalAlignment(SwingConstants.CENTER);
        GridBagConstraints gbc_label = new GridBagConstraints();
        gbc_label.anchor = GridBagConstraints.WEST;
        gbc_label.insets = new Insets(0, 0, 5, 5);
        gbc_label.gridx = 0;
        gbc_label.gridy = 0;
        pnlOptions.add(lblMkvPropExe, gbc_label);

        txtMkvPropExe = new JTextField("mkvpropedit");
        txtMkvPropExe.setEditable(false);
        txtMkvPropExe.setColumns(10);
        GridBagConstraints gbc_textField = new GridBagConstraints();
        gbc_textField.insets = new Insets(0, 0, 5, 0);
        gbc_textField.fill = GridBagConstraints.HORIZONTAL;
        gbc_textField.gridx = 1;
        gbc_textField.gridy = 0;
        pnlOptions.add(txtMkvPropExe, gbc_textField);

        JPanel pnlMkvPropExeControls = new JPanel();
        GridBagConstraints gbc_panel = new GridBagConstraints();
        gbc_panel.insets = new Insets(0, 0, 5, 0);
        gbc_panel.fill = GridBagConstraints.BOTH;
        gbc_panel.gridx = 1;
        gbc_panel.gridy = 1;
        pnlOptions.add(pnlMkvPropExeControls, gbc_panel);
        GridBagLayout gbl_panel = new GridBagLayout();
        gbl_panel.columnWidths = new int[] { 0, 0, 0 };
        gbl_panel.rowHeights = new int[] { 0, 0 };
        gbl_panel.columnWeights = new double[] { 1.0, 0.0, Double.MIN_VALUE };
        gbl_panel.rowWeights = new double[] { 0.0, Double.MIN_VALUE };
        pnlMkvPropExeControls.setLayout(gbl_panel);

        chbMkvPropExeDef = new JCheckBox("Use default");
        chbMkvPropExeDef.setSelected(true);
        chbMkvPropExeDef.setEnabled(false);
        GridBagConstraints gbc_checkBox = new GridBagConstraints();
        gbc_checkBox.anchor = GridBagConstraints.WEST;
        gbc_checkBox.insets = new Insets(0, 0, 0, 5);
        gbc_checkBox.gridx = 0;
        gbc_checkBox.gridy = 0;
        pnlMkvPropExeControls.add(chbMkvPropExeDef, gbc_checkBox);

        btnBrowseMkvPropExe = new JButton("Browse...");
        GridBagConstraints gbc_button = new GridBagConstraints();
        gbc_button.gridx = 1;
        gbc_button.gridy = 0;
        pnlMkvPropExeControls.add(btnBrowseMkvPropExe, gbc_button);
    }

    private void buildOutputTab() {
        JPanel pnlOutput = new JPanel();
        pnlOutput.setBorder(new EmptyBorder(10, 10, 10, 10));
        pnlTabs.addTab("Output", null, pnlOutput, null);
        pnlOutput.setLayout(new BorderLayout(0, 0));

        JScrollPane spOutput = new JScrollPane();
        pnlOutput.add(spOutput, BorderLayout.CENTER);

        txtOutput = new JTextArea();
        txtOutput.setLineWrap(true);
        txtOutput.setEditable(false);
        spOutput.setViewportView(txtOutput);
    }

    private void buildBottomButtons() {
        JPanel pnlButtons = new JPanel();
        frmJMkvpropedit.getContentPane().add(pnlButtons, BorderLayout.SOUTH);

        btnProcessFiles = new JButton("Process files");
        pnlButtons.add(btnProcessFiles);

        btnGenerateCmdLine = new JButton("Generate command line");
        pnlButtons.add(btnGenerateCmdLine);
    }

    private static JButton createIconButton(String iconResource, String tooltip) {
        JButton button = new JButton("");
        button.setIcon(new ImageIcon(JMkvpropedit.class.getResource(iconResource)));
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setToolTipText(tooltip);
        return button;
    }

    /* Listener wiring */

    private void wireRightClickMenus() {
        Utils.addRCMenuMouseListener(txtTitleGeneral);
        Utils.addRCMenuMouseListener(txtNumbStartGeneral);
        Utils.addRCMenuMouseListener(txtNumbPadGeneral);
        Utils.addRCMenuMouseListener(txtChapters);
        Utils.addRCMenuMouseListener(txtTags);
        Utils.addRCMenuMouseListener(txtExtraCmdGeneral);
        Utils.addRCMenuMouseListener(txtMkvPropExe);
        Utils.addRCMenuMouseListener(txtOutput);
    }

    private void wireWindowListeners() {
        frmJMkvpropedit.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                // Don't allow the window to be resized to a dimension smaller than the original
                frmJMkvpropedit.setMinimumSize(new Dimension(frmJMkvpropedit.getWidth(), frmJMkvpropedit.getHeight()));

                // Center the window on the screen
                frmJMkvpropedit.setLocationRelativeTo(null);

                readIniFile();
                videoPanel.addTrack();
                audioPanel.addTrack();
                subtitlePanel.addTrack();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                boolean wRunning = worker != null && !worker.isDone();

                if (wRunning) {
                    int choice = JOptionPane.showConfirmDialog(frmJMkvpropedit, "Do you really want to exit?", "",
                            JOptionPane.YES_NO_OPTION);
                    if (choice == JOptionPane.YES_OPTION) {
                        worker.cancel(true);
                        frmJMkvpropedit.dispose();
                        System.exit(0);
                    }
                } else {
                    frmJMkvpropedit.dispose();
                    System.exit(0);
                }
            }
        });

        frmJMkvpropedit.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                // Check if window width changed before resizing columns
                if (frmJMkvpropedit.getWidth() != frmJMkvpropeditDim.getWidth()) {
                    attachmentAddPanel.resizeColumns();
                    attachmentReplacePanel.resizeColumns();
                    attachmentDeletePanel.resizeColumns();
                }

                // Store new dimensions
                frmJMkvpropeditDim = new Dimension(frmJMkvpropedit.getWidth(), frmJMkvpropedit.getHeight());
            }
        });
    }

    private void wireFileDrop() {
        new FileDrop(listFiles, new FileDrop.Listener() {
            public void filesDropped(File[] files) {
                for (int i = 0; i < files.length; i++) {
                    if (files[i].isDirectory()) {
                        addMkvFilesFromFolder(files[i]);
                    } else {
                        addFile(files[i], true);
                    }
                }
            }
        });
    }

    private void wireFileListToolbar() {
        btnAddFiles.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                File[] files = null;

                chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                chooser.setDialogTitle("Select Matroska file(s) to edit");
                chooser.setMultiSelectionEnabled(true);
                chooser.setAcceptAllFileFilterUsed(false);
                chooser.resetChoosableFileFilters();
                chooser.setFileFilter(MATROSKA_EXT_FILTER);

                int open = chooser.showOpenDialog(frmJMkvpropedit);

                if (open == JFileChooser.APPROVE_OPTION) {
                    files = chooser.getSelectedFiles();
                    for (int i = 0; i < files.length; i++) {
                        try {
                            if (!modelFiles.contains(files[i].getCanonicalPath()) && files[i].exists()) {
                                modelFiles.add(modelFiles.getSize(), files[i].getCanonicalPath());
                            }
                        } catch (IOException e1) {
                            appendOutput("Error: could not resolve " + files[i] + ": " + e1 + "\n");
                        }
                    }
                }

            }
        });

        btnAddFolder.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                File folder = null;

                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                chooser.setDialogTitle("Select folder with Matroska files to edit");
                chooser.setAcceptAllFileFilterUsed(false);

                int open = chooser.showOpenDialog(frmJMkvpropedit);

                if (open == JFileChooser.APPROVE_OPTION) {
                    folder = chooser.getSelectedFile();
                    addMkvFilesFromFolder(folder);
                }

            }
        });

        btnRemoveFiles.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (modelFiles.getSize() > 0) {
                    while (listFiles.getSelectedIndex() != -1) {
                        int[] idx = listFiles.getSelectedIndices();
                        modelFiles.remove(idx[0]);
                    }
                }
            }
        });

        btnClearFiles.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                modelFiles.removeAllElements();
            }
        });

        btnTopFiles.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int[] idx = listFiles.getSelectedIndices();

                for (int i = 0; i < idx.length; i++) {
                    int pos = idx[i];

                    if (pos > 0) {
                        String temp = (String) modelFiles.remove(pos);
                        modelFiles.add(i, temp);
                        listFiles.ensureIndexIsVisible(0);
                        idx[i] = i;
                    }
                }

                listFiles.setSelectedIndices(idx);
            }
        });

        btnUpFiles.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int[] idx = listFiles.getSelectedIndices();

                for (int i = 0; i < idx.length; i++) {
                    int pos = idx[i];

                    if (pos > 0 && listFiles.getMinSelectionIndex() != 0) {
                        String temp = (String) modelFiles.remove(pos);
                        modelFiles.add(pos - 1, temp);
                        listFiles.ensureIndexIsVisible(pos - 1);
                        idx[i]--;
                    }
                }

                listFiles.setSelectedIndices(idx);
            }
        });

        btnDownFiles.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int[] idx = listFiles.getSelectedIndices();

                for (int i = idx.length - 1; i > -1; i--) {
                    int pos = idx[i];

                    if (pos < modelFiles.getSize() - 1
                            && listFiles.getMaxSelectionIndex() != modelFiles.getSize() - 1) {
                        String temp = (String) modelFiles.remove(pos);
                        modelFiles.add(pos + 1, temp);
                        listFiles.ensureIndexIsVisible(pos + 1);
                        idx[i]++;
                    }
                }

                listFiles.setSelectedIndices(idx);
            }
        });

        btnBottomFiles.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int[] idx = listFiles.getSelectedIndices();
                int j = 0;

                for (int i = idx.length - 1; i > -1; i--) {
                    int pos = idx[i];

                    if (pos < modelFiles.getSize()) {
                        String temp = (String) modelFiles.remove(pos);
                        modelFiles.add(modelFiles.getSize() - j, temp);
                        j++;
                        listFiles.ensureIndexIsVisible(modelFiles.getSize() - 1);
                        idx[i] = modelFiles.getSize() - j;
                    }
                }

                listFiles.setSelectedIndices(idx);
            }
        });
    }

    private void wireGeneralTab() {
        chbTitleGeneral.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                boolean state = txtTitleGeneral.isEnabled();

                if (txtTitleGeneral.isEnabled() || chbTitleGeneral.isSelected()) {
                    txtTitleGeneral.setEnabled(!state);
                    chbNumbGeneral.setEnabled(!state);

                    if (chbNumbGeneral.isSelected()) {
                        lblNumbStartGeneral.setEnabled(!state);
                        txtNumbStartGeneral.setEnabled(!state);
                        lblNumbPadGeneral.setEnabled(!state);
                        txtNumbPadGeneral.setEnabled(!state);
                        lblNumbExplainGeneral.setEnabled(!state);
                    }
                }
            }
        });

        chbNumbGeneral.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                boolean state = txtNumbStartGeneral.isEnabled();
                lblNumbStartGeneral.setEnabled(!state);
                txtNumbStartGeneral.setEnabled(!state);
                lblNumbPadGeneral.setEnabled(!state);
                txtNumbPadGeneral.setEnabled(!state);
                lblNumbExplainGeneral.setEnabled(!state);
            }
        });

        clampNonNegativeNumber(txtNumbStartGeneral);
        clampNonNegativeNumber(txtNumbPadGeneral);

        wireSourceSection(chbChapters, cbChapters, txtChapters, btnBrowseChapters, cbExtChapters,
                "-chapters", "Select chapters file");
        wireSourceSection(chbTags, cbTags, txtTags, btnBrowseTags, cbExtTags,
                "-tags", "Select tags file");

        chbExtraCmdGeneral.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                boolean state = txtExtraCmdGeneral.isEnabled();
                txtExtraCmdGeneral.setEnabled(!state);
            }
        });
    }

    /**
     * Wires one General-tab source section (chapters or tags): the section
     * checkbox gates the mode combo, the combo picks the row's mode (0 = hide,
     * 1 = fixed file, 2 = suffix match with {@code matchDefaultText}) and the
     * Browse button fills the text field through the txt/xml chooser.
     */
    private void wireSourceSection(JCheckBox section, JComboBox<String> mode, JTextField text,
            JButton browse, JComboBox<String> extension, String matchDefaultText, String browseTitle) {
        section.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                boolean state = mode.isEnabled();
                mode.setEnabled(!state);

                if (mode.getSelectedIndex() == 1) {
                    text.setEditable(false);
                    text.setVisible(true);
                    text.setEnabled(!state);
                    browse.setVisible(true);
                    browse.setEnabled(!state);
                    extension.setVisible(false);
                } else if (mode.getSelectedIndex() == 2) {
                    text.setEditable(true);
                    text.setVisible(true);
                    text.setEnabled(!state);
                    browse.setVisible(false);
                    browse.setEnabled(!state);
                    extension.setVisible(true);
                    extension.setEnabled(!state);
                } else if (!section.isSelected()) {
                    text.setVisible(false);
                    browse.setVisible(false);
                    extension.setVisible(false);
                }
            }
        });

        mode.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (mode.getSelectedIndex() == 0) {
                    text.setVisible(false);
                    browse.setVisible(false);
                    extension.setVisible(false);
                } else if (mode.getSelectedIndex() == 1) {
                    text.setText("");
                    text.setEditable(false);
                    text.setVisible(true);
                    browse.setVisible(true);
                    extension.setVisible(false);
                } else {
                    text.setText(matchDefaultText);
                    text.setEditable(true);
                    text.setVisible(true);
                    browse.setVisible(false);
                    extension.setVisible(true);
                }
            }
        });

        browse.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                configureTextFileChooser(chooser, browseTitle);

                int open = chooser.showOpenDialog(frmJMkvpropedit);

                if (open == JFileChooser.APPROVE_OPTION) {
                    if (chooser.getSelectedFile().exists()) {
                        text.setText(chooser.getSelectedFile().toString());
                    }
                }
            }
        });
    }

    private void clampNonNegativeNumber(JTextField field) {
        field.addFocusListener(new FocusAdapter() {
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
        });
    }

    private void wireExecutableOptions() {
        chbMkvPropExeDef.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                txtMkvPropExe.setText("mkvpropedit");
                chbMkvPropExeDef.setEnabled(false);
                defaultIniFile();
            }
        });

        btnBrowseMkvPropExe.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                chooser.setDialogTitle("Select mkvpropedit executable");
                chooser.setMultiSelectionEnabled(false);
                chooser.setAcceptAllFileFilterUsed(false);
                chooser.resetChoosableFileFilters();

                if (Utils.isWindows()) {
                    chooser.setFileFilter(EXE_EXT_FILTER);
                }

                int open = chooser.showOpenDialog(frmJMkvpropedit);

                if (open == JFileChooser.APPROVE_OPTION) {
                    if (chooser.getSelectedFile().exists()) {
                        saveIniFile(chooser.getSelectedFile());
                    }
                }
            }
        });
    }

    private void wireActionButtons() {
        btnProcessFiles.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (modelFiles.getSize() == 0) {
                    JOptionPane.showMessageDialog(frmJMkvpropedit, "The file list is empty!", "Empty list",
                            JOptionPane.ERROR_MESSAGE);
                } else {
                    setCmdLine();

                    if (cmdLineBatchOpt.size() == 0) {
                        JOptionPane.showMessageDialog(frmJMkvpropedit, "Nothing to do!", "",
                                JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        if (isExecutableInPath(txtMkvPropExe.getText())) {
                            executeBatch();
                        } else {
                            JOptionPane.showMessageDialog(frmJMkvpropedit, "Mkvpropedit executable not found!"
                                    + "\nPlease make sure it is installed and included in the system path.\n"
                                    + "Alternatively, you can manually set the path or copy its executable to the working folder.",
                                    "", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                }

            }
        });

        btnGenerateCmdLine.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (modelFiles.getSize() == 0) {
                    JOptionPane.showMessageDialog(frmJMkvpropedit, "The file list is empty!", "Empty list",
                            JOptionPane.ERROR_MESSAGE);
                } else {
                    setCmdLine();

                    if (cmdLineBatch.size() == 0) {
                        JOptionPane.showMessageDialog(frmJMkvpropedit, "Nothing to do!", "",
                                JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        txtOutput.setText("");

                        for (int i = 0; i < modelFiles.size(); i++) {
                            txtOutput.append(cmdLineBatch.get(i) + "\n");
                        }

                        pnlTabs.setSelectedIndex(pnlTabs.getTabCount() - 1);
                    }
                }
            }
        });
    }

    /* Start of command line methods */

    /**
     * Snapshot of the file list as plain data for {@link CommandBuilder}.
     */
    private List<String> fileList() {
        List<String> files = new ArrayList<>(modelFiles.size());

        for (int i = 0; i < modelFiles.size(); i++) {
            files.add(modelFiles.get(i));
        }

        return files;
    }

    private void setCmdLineGeneral() {
        CommandBuilder.GeneralSettings settings = new CommandBuilder.GeneralSettings(
                new CommandBuilder.SourceSetting(chbTags.isSelected(), cbTags.getSelectedIndex(),
                        txtTags.getText(), String.valueOf(cbExtTags.getSelectedItem())),
                new CommandBuilder.SourceSetting(chbChapters.isSelected(), cbChapters.getSelectedIndex(),
                        txtChapters.getText(), String.valueOf(cbExtChapters.getSelectedItem())),
                new CommandBuilder.TitleSetting(chbTitleGeneral.isSelected(), chbNumbGeneral.isSelected(),
                        txtTitleGeneral.getText(), txtNumbStartGeneral.getText(), txtNumbPadGeneral.getText()),
                chbExtraCmdGeneral.isSelected(), txtExtraCmdGeneral.getText());

        CommandBuilder.Section section = commandBuilder.buildGeneral(fileList(), settings);
        cmdLineGeneral = section.plain();
        cmdLineGeneralOpt = section.opt();
    }

    private void setCmdLineVideo() {
        CommandBuilder.Section section = commandBuilder.buildTracks(fileList(), 'v', videoPanel.trackSettings());
        cmdLineVideo = section.plain();
        cmdLineVideoOpt = section.opt();
    }

    private void setCmdLineAudio() {
        CommandBuilder.Section section = commandBuilder.buildTracks(fileList(), 'a', audioPanel.trackSettings());
        cmdLineAudio = section.plain();
        cmdLineAudioOpt = section.opt();
    }

    private void setCmdLineSubtitle() {
        CommandBuilder.Section section = commandBuilder.buildTracks(fileList(), 's', subtitlePanel.trackSettings());
        cmdLineSubtitle = section.plain();
        cmdLineSubtitleOpt = section.opt();
    }

    private void setCmdLine() {
        setCmdLineGeneral();
        setCmdLineVideo();
        setCmdLineAudio();
        setCmdLineSubtitle();

        CommandBuilder.Attachments attachments = new CommandBuilder.Attachments(
                new CommandBuilder.AttachmentArgs(attachmentDeletePanel.cmdLine(), attachmentAddPanel.cmdLine(),
                        attachmentReplacePanel.cmdLine()),
                new CommandBuilder.AttachmentArgs(attachmentDeletePanel.cmdLineOpt(), attachmentAddPanel.cmdLineOpt(),
                        attachmentReplacePanel.cmdLineOpt()));

        CommandBuilder.Batch batch = commandBuilder.buildBatch(txtMkvPropExe.getText(), fileList(),
                new CommandBuilder.Section(cmdLineGeneral, cmdLineGeneralOpt),
                new CommandBuilder.Section(cmdLineVideo, cmdLineVideoOpt),
                new CommandBuilder.Section(cmdLineAudio, cmdLineAudioOpt),
                new CommandBuilder.Section(cmdLineSubtitle, cmdLineSubtitleOpt),
                attachments);

        cmdLineBatch = batch.lines();
        cmdLineBatchOpt = batch.optArgs();
    }

    private void executeBatch() {
        // One-shot UI setup runs here, on the EDT (executeBatch is called from
        // the button listener), so the background worker never touches Swing:
        // log appends go through appendOutput, and done() restores the controls.
        txtOutput.setText("");
        pnlTabs.setSelectedIndex(pnlTabs.getTabCount() - 1);
        pnlTabs.setEnabled(false);
        btnProcessFiles.setEnabled(false);
        btnGenerateCmdLine.setEnabled(false);

        // Snapshot the inputs so the worker only reads plain data: disabling
        // the tab pane does not stop the file list from being edited while the
        // batch runs.
        final List<String> batch = List.copyOf(cmdLineBatch);
        final List<String[]> batchOpt = List.copyOf(cmdLineBatchOpt);
        final List<String> fileNames = fileList();
        final String exePath = txtMkvPropExe.getText();

        worker = new SwingWorker<Void, Void>() {
            @Override
            public Void doInBackground() {
                ProcessRunner runner = new ProcessRunner(exePath);

                for (int i = 0; i < batch.size(); i++) {
                    try {
                        appendOutput("File: " + fileNames.get(i) + "\n");
                        appendOutput("Command line: " + batch.get(i) + "\n\n");

                        runner.runWithOptionsFile(optionsJson(batchOpt.get(i)),
                                JMkvpropedit.this::appendOutput);

                        if (i < batch.size() - 1) {
                            appendOutput("--------------\n\n");
                        }
                    } catch (IOException e) {
                        appendOutput("Error: " + e + "\n");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                return null;
            }

            @Override
            protected void done() {
                pnlTabs.setEnabled(true);
                btnProcessFiles.setEnabled(true);
                btnGenerateCmdLine.setEnabled(true);
            }
        };

        worker.execute();
    }

    /**
     * Cracks an Opt command line into the argument list for options.json.
     * The logic (including unwinding {@link Utils#escapeName}'s encoding)
     * lives in {@link CommandBuilder#toOptArgs}; this alias keeps the entry
     * point where existing harness code expects it.
     */
    static String[] toOptArgs(String optCommandLine) {
        return CommandBuilder.toOptArgs(optCommandLine);
    }

    /**
     * Builds the contents of options.json: the arguments as a JSON array of
     * properly escaped strings (issue #6).
     */
    static String optionsJson(String[] args) {
        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < args.length; i++) {
            json.append("  ").append(jsonString(args[i]));
            if (i < args.length - 1) {
                json.append(',');
            }
            json.append('\n');
        }
        return json.append("]\n").toString();
    }

    /**
     * Minimal JSON string writer: escapes the quote, backslash and control
     * characters; everything else (including unicode) is valid UTF-8 JSON as-is.
     */
    private static String jsonString(String raw) {
        StringBuilder sb = new StringBuilder(raw.length() + 16);
        sb.append('"');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
            case '"' -> sb.append("\\\"");
            case '\\' -> sb.append("\\\\");
            case '\b' -> sb.append("\\b");
            case '\f' -> sb.append("\\f");
            case '\n' -> sb.append("\\n");
            case '\r' -> sb.append("\\r");
            case '\t' -> sb.append("\\t");
            default -> {
                if (c < 0x20) {
                    sb.append(String.format("\\u%04x", (int) c));
                } else {
                    sb.append(c);
                }
            }
            }
        }
        return sb.append('"').toString();
    }

    /**
     * Appends to the output area from any thread; the append itself always
     * runs on the EDT, in submission order with the gobbler's own appends.
     */
    private void appendOutput(final String text) {
        SwingUtilities.invokeLater(() -> {
            txtOutput.append(text);
            txtOutput.setCaretPosition(txtOutput.getDocument().getLength());
        });
    }

    /* AttachmentPanel.Host services */

    @Override
    public JFileChooser chooser() {
        return chooser;
    }

    @Override
    public Window dialogParent() {
        return frmJMkvpropedit;
    }

    @Override
    public void logError(String message) {
        appendOutput(message);
    }

    @Override
    public void showError(String message) {
        JOptionPane.showMessageDialog(null, message, "", JOptionPane.ERROR_MESSAGE);
    }

    private void parseFiles(String[] argsArray) {
        if (argsArray.length > 0) {
            for (String arg : argsArray) {
                try {
                    File file = new File(arg);

                    if (!file.exists()) {
                        continue;
                    }

                    if (file.isDirectory()) {
                        addMkvFilesFromFolder(file);
                    } else {
                        addFile(file, true);
                    }
                } catch (Exception e) {
                    appendOutput("Error: could not add " + arg + ": " + e + "\n");
                }
            }
        }
    }

    private boolean isExecutableInPath(final String exe) {
        return new ProcessRunner(exe).isExecutableInPath();
    }

    /* End of command line methods */

    /* Start of INI configuration file methods */

    private void readIniFile() {
        if (iniStore.exists()) {
            try {
                String exePath = iniStore.readMkvpropedit();

                if (exePath != null) {
                    if (exePath.equals(IniStore.DEFAULT_MKVPROPEDIT)) {
                        chbMkvPropExeDef.setSelected(true);
                        chbMkvPropExeDef.setEnabled(false);
                    } else {
                        txtMkvPropExe.setText(exePath);
                        chbMkvPropExeDef.setSelected(false);
                        chbMkvPropExeDef.setEnabled(true);
                    }
                }
            } catch (IniStoreException e) {
                appendOutput("Error: " + e.getMessage() + "\n");
            }
        } else if (Utils.isWindows()) {
            String exePath = getMkvPropExeDefault();

            if (exePath != null) {
                txtMkvPropExe.setText(exePath);
                chbMkvPropExeDef.setSelected(false);
                chbMkvPropExeDef.setEnabled(true);
                saveIniFile(new File(exePath));
            }
        }
    }

    private void saveIniFile(File exeFile) {
        txtMkvPropExe.setText(exeFile.toString());
        chbMkvPropExeDef.setSelected(false);
        chbMkvPropExeDef.setEnabled(true);

        try {
            iniStore.saveMkvpropedit(exeFile.toString());
        } catch (IniStoreException e) {
            appendOutput("Error: " + e.getMessage() + "\n");
        }
    }

    private void defaultIniFile() {
        try {
            iniStore.saveMkvpropedit(IniStore.DEFAULT_MKVPROPEDIT);
        } catch (IniStoreException e) {
            appendOutput("Error: " + e.getMessage() + "\n");
        }
    }

    private String getMkvPropExeDefault() {
        String sysDrive = System.getenv("SystemDrive");
        String exePaths[] = new String[] { sysDrive + "\\Program Files (x86)\\MKVToolNix",
                sysDrive + "\\Program Files\\MKVToolNix" };

        for (int i = 0; i < exePaths.length; i++) {
            File tmpExe = new File(exePaths[i] + "\\mkvpropedit.exe");

            if (tmpExe.exists()) {
                return tmpExe.toString();
            }
        }

        return null;
    }

    /* End of INI configuration file methods */

    /* Start of file methods */

    /**
     * Shared setup for the chapters/tags file choosers.
     *
     * <p>
     * Both filters must be <em>choosable</em> (visible in the type dropdown).
     * {@link JFileChooser#setFileFilter} only changes the current selection and
     * does not add to the choosable list per its API contract, so two
     * consecutive calls would leave the first filter unreachable on look and
     * feels that do not re-add it as a side effect. Use
     * {@link JFileChooser#addChoosableFileFilter} for both and keep XML as the
     * current default (matches the previous last-call wins behaviour).
     * </p>
     */
    private void configureTextFileChooser(JFileChooser fileChooser, String dialogTitle) {
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setDialogTitle(dialogTitle);
        fileChooser.setMultiSelectionEnabled(false);
        fileChooser.setAcceptAllFileFilterUsed(false);
        fileChooser.resetChoosableFileFilters();
        fileChooser.addChoosableFileFilter(TXT_EXT_FILTER);
        fileChooser.addChoosableFileFilter(XML_EXT_FILTER);
        fileChooser.setFileFilter(XML_EXT_FILTER);
    }

    private void addFile(File file, boolean checkExtension) {
        try {
            String path = file.getCanonicalPath();

            if (!modelFiles.contains(path)
                    && (!checkExtension || FileScanner.isMatroskaFile(file.toPath()))) {
                modelFiles.add(modelFiles.getSize(), path);
            }
        } catch (IOException e) {
            appendOutput("Error: could not resolve " + file + ": " + e + "\n");
        }
    }

    private void addMkvFilesFromFolder(final File folder) {
        SwingUtilities.invokeLater(() -> {
            try {
                for (Path path : FileScanner.scanMatroskaFiles(folder.toPath())) {
                    addFile(path.toFile(), false);
                }
            } catch (IOException | UncheckedIOException e) {
                appendOutput("Error: could not scan " + folder + ": " + e + "\n");
            }
        });
    }

    /* End of file methods */

}
