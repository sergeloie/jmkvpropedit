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
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
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
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;

import org.ini4j.Ini;
import org.ini4j.InvalidFileFormatException;

public class JMkvpropedit {

    private static final String VERSION_NUMBER = BuildVersion.VERSION;
    private static String[] argsArray;

    private SwingWorker<Void, Void> worker = null;

    private File iniFile = new File("JMkvpropedit.ini");
    private static final MkvStrings mkvStrings = new MkvStrings();
    private final CommandBuilder commandBuilder = new CommandBuilder();

    private JFileChooser chooser = new JFileChooser(System.getProperty("user.home")) {
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

    private FileFilter EXE_EXT_FILTER = new FileNameExtensionFilter("Executable files (*.exe)", "exe");

    private FileFilter MATROSKA_EXT_FILTER = new FileNameExtensionFilter(
            "Matroska files (*.mkv; *.mka; *.mk3d; *.webm; *.mks)", "mkv", "mka", "mk3d", "webm", "mks");

    /**
     * Folder-scan glob for Matroska files: mkv/mka/mk3d/webm/mks. The glob
     * itself is lowercase; {@link #isMatroskaFile} lowercases the file name
     * before matching so the mask stays case-insensitive on case-sensitive
     * filesystems (Linux/CI) as well as on Windows.
     */
    private static final PathMatcher MATROSKA_FILE_FILTER =
            FileSystems.getDefault().getPathMatcher("glob:*.{mkv,mka,mk3d,webm,mks}");

    private FileFilter TXT_EXT_FILTER = new FileNameExtensionFilter("Plain text files (*.txt)", "txt");

    private FileFilter XML_EXT_FILTER = new FileNameExtensionFilter("XML files (*.xml)", "xml");

    private static final String[] COLUMNS_ATTACHMENTS_ADD = { "File", "Name", "Description", "MIME Type" };
    private static final double[] COLUMN_SIZES_ATTACHMENTS_ADD = { 0.35, 0.20, 0.25, 0.20 };
    private DefaultTableModel modelAttachmentsAdd = new DefaultTableModel(null, COLUMNS_ATTACHMENTS_ADD) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

    };

    private static final String[] COLUMNS_ATTACHMENTS_REPLACE = { "Type", "Original Value", "Replacement", "Name",
            "Description", "MIME Type" };
    private static final double[] COLUMN_SIZES_ATTACHMENTS_REPLACE = { 0.15, 0.15, 0.20, 0.20, 0.15, 0.15 };
    private DefaultTableModel modelAttachmentsReplace = new DefaultTableModel(null, COLUMNS_ATTACHMENTS_REPLACE) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

    };

    private static final String[] COLUMNS_ATTACHMENTS_DELETE = { "Type", "Value" };
    private static final double[] COLUMN_SIZES_ATTACHMENTS_DELETE = { 0.40, 0.60 };
    private DefaultTableModel modelAttachmentsDelete = new DefaultTableModel(null, COLUMNS_ATTACHMENTS_DELETE) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

    };

    private String[] cmdLineGeneral = null;
    private String[] cmdLineGeneralOpt = null;

    private String[] cmdLineVideo = null;
    private String[] cmdLineVideoOpt = null;

    private String[] cmdLineAudio = null;
    private String[] cmdLineAudioOpt = null;

    private String[] cmdLineSubtitle = null;
    private String[] cmdLineSubtitleOpt = null;

    private String cmdLineAttachmentsAdd = null;
    private String cmdLineAttachmentsAddOpt = null;

    private String cmdLineAttachmentsReplace = null;
    private String cmdLineAttachmentsReplaceOpt = null;

    private String cmdLineAttachmentsDelete = null;
    private String cmdLineAttachmentsDeleteOpt = null;

    private List<String> cmdLineBatch = null;
    private List<String[]> cmdLineBatchOpt = null;

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

    // Attachments tab controls
    private JTabbedPane pnlAttachments;
    private JPanel pnlAttachAdd;
    private JScrollPane spAttachAdd;
    private JTable tblAttachAdd;
    private JPanel pnlAttachAddControls;
    private JLabel lblAttachAddFile;
    private JTextField txtAttachAddFile;
    private JButton btnBrowseAttachAddFile;
    private JLabel lblAttachAddName;
    private JTextField txtAttachAddName;
    private JLabel lblAttachAddDesc;
    private JTextField txtAttachAddDesc;
    private JLabel lblAttachAddMime;
    private JComboBox<String> cbAttachAddMime;
    private JPanel pnlAttachAddControlsBottom;
    private JButton btnAttachAddAdd;
    private JButton btnAttachAddRemove;
    private JButton btnAttachAddEdit;
    private JButton btnAttachAddCancel;

    private JPanel pnlAttachReplace;
    private JScrollPane spAttachReplace;
    private JTable tblAttachReplace;
    private JPanel pnlAttachReplaceControls;
    private JLabel lblAttachReplaceType;
    private JPanel pnlAttachReplaceType;
    private ButtonGroup bgAttachReplaceType = new ButtonGroup();
    private JRadioButton rbAttachReplaceID;
    private JRadioButton rbAttachReplaceName;
    private JRadioButton rbAttachReplaceMime;
    private JPanel pnlAttachReplaceOrig;
    private JLabel lblAttachReplaceOrig;
    private JTextField txtAttachReplaceOrig;
    private JComboBox<String> cbAttachReplaceOrig;
    private JLabel lblAttachReplaceNew;
    private JTextField txtAttachReplaceNew;
    private JButton btnAttachReplaceNewBrowse;
    private JLabel lblAttachReplaceName;
    private JTextField txtAttachReplaceName;
    private JLabel lblAttachReplaceDesc;
    private JTextField txtAttachReplaceDesc;
    private JLabel lblAttachReplaceMime;
    private JComboBox<String> cbAttachReplaceMime;
    private JPanel pnlAttachReplaceControlsBottom;
    private JButton btnAttachReplaceAdd;
    private JButton btnAttachReplaceEdit;
    private JButton btnAttachReplaceRemove;
    private JButton btnAttachReplaceCancel;

    private JPanel pnlAttachDelete;
    private JScrollPane spAttachDelete;
    private JTable tblAttachDelete;
    private JPanel pnlAttachDeleteControls;
    private ButtonGroup bgAttachDeleteType = new ButtonGroup();
    private JLabel lblAttachDeleteType;
    private JPanel pnlAttachDeleteType;
    private JRadioButton rbAttachDeleteName;
    private JRadioButton rbAttachDeleteID;
    private JRadioButton rbAttachDeleteMime;
    private JLabel lblAttachDeleteValue;
    private JPanel pnlAttachDeleteValue;
    private JTextField txtAttachDeleteValue;
    private JComboBox<String> cbAttachDeleteValue;
    private JPanel pnlAttachDeleteControlsBottom;
    private JButton btnAttachDeleteAdd;
    private JButton btnAttachDeleteEdit;
    private JButton btnAttachDeleteRemove;
    private JButton btnAttachDeleteCancel;

    // Option tab controls
    private JPanel pnlOptions;
    private JTextField txtMkvPropExe;
    private JCheckBox chbMkvPropExeDef;

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
     * Initialize the contents of the frame.
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

        btnAddFiles = new JButton("");
        btnAddFiles.setIcon(new ImageIcon(JMkvpropedit.class.getResource("/list-add.png")));
        btnAddFiles.setMargin(new Insets(0, 0, 0, 0));
        btnAddFiles.setBorderPainted(false);
        btnAddFiles.setContentAreaFilled(false);
        btnAddFiles.setFocusPainted(false);
        btnAddFiles.setOpaque(false);
        btnAddFiles.setToolTipText("Add files");
        pnlListToolbar.add(btnAddFiles);

        Component verticalStrut1 = Box.createVerticalStrut(10);
        pnlListToolbar.add(verticalStrut1);

        btnAddFolder = new JButton("");
        btnAddFolder.setIcon(new ImageIcon(JMkvpropedit.class.getResource("/list-add-folder.png")));
        btnAddFolder.setMargin(new Insets(0, 0, 0, 0));
        btnAddFolder.setBorderPainted(false);
        btnAddFolder.setContentAreaFilled(false);
        btnAddFolder.setFocusPainted(false);
        btnAddFolder.setOpaque(false);
        btnAddFolder.setToolTipText("Add folder");
        pnlListToolbar.add(btnAddFolder);

        Component verticalStrut1b = Box.createVerticalStrut(10);
        pnlListToolbar.add(verticalStrut1b);

        btnRemoveFiles = new JButton("");
        btnRemoveFiles.setIcon(new ImageIcon(JMkvpropedit.class.getResource("/list-remove.png")));
        btnRemoveFiles.setMargin(new Insets(0, 0, 0, 0));
        btnRemoveFiles.setBorderPainted(false);
        btnRemoveFiles.setContentAreaFilled(false);
        btnRemoveFiles.setFocusPainted(false);
        btnRemoveFiles.setOpaque(false);
        btnRemoveFiles.setToolTipText("Remove selected files");
        pnlListToolbar.add(btnRemoveFiles);

        Component verticalStrut2 = Box.createVerticalStrut(10);
        pnlListToolbar.add(verticalStrut2);

        btnTopFiles = new JButton("");
        btnTopFiles.setIcon(new ImageIcon(JMkvpropedit.class.getResource("/go-top.png")));
        btnTopFiles.setMargin(new Insets(0, 0, 0, 0));
        btnTopFiles.setBorderPainted(false);
        btnTopFiles.setContentAreaFilled(false);
        btnTopFiles.setFocusPainted(false);
        btnTopFiles.setOpaque(false);
        btnTopFiles.setToolTipText("Move selected files to the top");
        pnlListToolbar.add(btnTopFiles);

        Component verticalStrut3 = Box.createVerticalStrut(10);
        pnlListToolbar.add(verticalStrut3);

        btnUpFiles = new JButton("");
        btnUpFiles.setIcon(new ImageIcon(JMkvpropedit.class.getResource("/go-up.png")));
        btnUpFiles.setMargin(new Insets(0, 0, 0, 0));
        btnUpFiles.setBorderPainted(false);
        btnUpFiles.setContentAreaFilled(false);
        btnUpFiles.setFocusPainted(false);
        btnUpFiles.setOpaque(false);
        btnUpFiles.setToolTipText("Move selected files up");
        pnlListToolbar.add(btnUpFiles);

        Component verticalStrut4 = Box.createVerticalStrut(10);
        pnlListToolbar.add(verticalStrut4);

        btnDownFiles = new JButton("");
        btnDownFiles.setIcon(new ImageIcon(JMkvpropedit.class.getResource("/go-down.png")));
        btnDownFiles.setMargin(new Insets(0, 0, 0, 0));
        btnDownFiles.setBorderPainted(false);
        btnDownFiles.setContentAreaFilled(false);
        btnDownFiles.setFocusPainted(false);
        btnDownFiles.setOpaque(false);
        btnDownFiles.setToolTipText("Move selected files down");
        pnlListToolbar.add(btnDownFiles);

        Component verticalStrut5 = Box.createVerticalStrut(10);
        pnlListToolbar.add(verticalStrut5);

        btnBottomFiles = new JButton("");
        btnBottomFiles.setIcon(new ImageIcon(JMkvpropedit.class.getResource("/go-bottom.png")));
        btnBottomFiles.setMargin(new Insets(0, 0, 0, 0));
        btnBottomFiles.setBorderPainted(false);
        btnBottomFiles.setContentAreaFilled(false);
        btnBottomFiles.setFocusPainted(false);
        btnBottomFiles.setOpaque(false);
        btnBottomFiles.setToolTipText("Move selected files to the bottom");
        pnlListToolbar.add(btnBottomFiles);

        Component verticalStrut6 = Box.createVerticalStrut(10);
        pnlListToolbar.add(verticalStrut6);

        btnClearFiles = new JButton("");
        btnClearFiles.setIcon(new ImageIcon(JMkvpropedit.class.getResource("/edit-clear.png")));
        btnClearFiles.setMargin(new Insets(0, 0, 0, 0));
        btnClearFiles.setBorderPainted(false);
        btnClearFiles.setContentAreaFilled(false);
        btnClearFiles.setFocusPainted(false);
        btnClearFiles.setOpaque(false);
        btnClearFiles.setToolTipText("Clear file list");
        pnlListToolbar.add(btnClearFiles);

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

        Component horizontalStrut1 = Box.createHorizontalStrut(10);
        pnlNumbControlsGeneral.add(horizontalStrut1);

        lblNumbStartGeneral = new JLabel("Start");
        lblNumbStartGeneral.setEnabled(false);
        pnlNumbControlsGeneral.add(lblNumbStartGeneral);

        txtNumbStartGeneral = new JTextField();
        txtNumbStartGeneral.setEnabled(false);
        txtNumbStartGeneral.setText("1");
        pnlNumbControlsGeneral.add(txtNumbStartGeneral);
        txtNumbStartGeneral.setColumns(10);

        Component horizontalStrut2 = Box.createHorizontalStrut(5);
        pnlNumbControlsGeneral.add(horizontalStrut2);

        lblNumbPadGeneral = new JLabel("Padding");
        lblNumbPadGeneral.setEnabled(false);
        pnlNumbControlsGeneral.add(lblNumbPadGeneral);

        txtNumbPadGeneral = new JTextField();
        txtNumbPadGeneral.setEnabled(false);
        txtNumbPadGeneral.setText("1");
        txtNumbPadGeneral.setColumns(10);
        pnlNumbControlsGeneral.add(txtNumbPadGeneral);

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

        Component verticalStrut7 = Box.createVerticalStrut(35);
        GridBagConstraints gbc_verticalStrut7 = new GridBagConstraints();
        gbc_verticalStrut7.insets = new Insets(0, 0, 5, 5);
        gbc_verticalStrut7.gridx = 0;
        gbc_verticalStrut7.gridy = 4;
        pnlGeneral.add(verticalStrut7, gbc_verticalStrut7);

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

        Component verticalStrut8 = Box.createVerticalStrut(35);
        GridBagConstraints gbc_verticalStrut8 = new GridBagConstraints();
        gbc_verticalStrut8.insets = new Insets(0, 0, 5, 5);
        gbc_verticalStrut8.gridx = 0;
        gbc_verticalStrut8.gridy = 6;
        pnlGeneral.add(verticalStrut8, gbc_verticalStrut8);

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

        videoPanel = new TrackPanel(TrackType.VIDEO, mkvStrings);
        pnlTabs.addTab(TrackType.VIDEO.tabTitle(), null, videoPanel, null);

        audioPanel = new TrackPanel(TrackType.AUDIO, mkvStrings);
        pnlTabs.addTab(TrackType.AUDIO.tabTitle(), null, audioPanel, null);

        subtitlePanel = new TrackPanel(TrackType.SUBTITLE, mkvStrings);
        pnlTabs.addTab(TrackType.SUBTITLE.tabTitle(), null, subtitlePanel, null);

        pnlAttachments = new JTabbedPane(JTabbedPane.TOP);
        pnlTabs.addTab("Attachments", null, pnlAttachments, null);

        pnlAttachAdd = new JPanel();
        pnlAttachments.addTab("Add Attachments", null, pnlAttachAdd, null);
        pnlAttachAdd.setLayout(new BorderLayout(0, 0));

        spAttachAdd = new JScrollPane();
        pnlAttachAdd.add(spAttachAdd, BorderLayout.CENTER);

        tblAttachAdd = new JTable();
        tblAttachAdd.setShowGrid(false);
        tblAttachAdd.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tblAttachAdd.setModel(modelAttachmentsAdd);
        tblAttachAdd.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        tblAttachAdd.setAutoscrolls(false);
        tblAttachAdd.setFillsViewportHeight(true);

        spAttachAdd.setViewportView(tblAttachAdd);

        pnlAttachAddControls = new JPanel();
        pnlAttachAddControls.setBorder(new EmptyBorder(5, 5, 5, 5));
        pnlAttachAdd.add(pnlAttachAddControls, BorderLayout.SOUTH);
        GridBagLayout gbl_pnlAttachAddControls = new GridBagLayout();
        gbl_pnlAttachAddControls.columnWidths = new int[] { 0, 0, 0, 0 };
        gbl_pnlAttachAddControls.rowHeights = new int[] { 0, 0, 0, 0, 0, 0 };
        gbl_pnlAttachAddControls.columnWeights = new double[] { 0.0, 1.0, 0.0, Double.MIN_VALUE };
        gbl_pnlAttachAddControls.rowWeights = new double[] { 0.0, 0.0, 0.0, 1.0, 1.0, Double.MIN_VALUE };
        pnlAttachAddControls.setLayout(gbl_pnlAttachAddControls);

        lblAttachAddFile = new JLabel("File:");
        GridBagConstraints gbc_lblAttachAddFile = new GridBagConstraints();
        gbc_lblAttachAddFile.anchor = GridBagConstraints.WEST;
        gbc_lblAttachAddFile.insets = new Insets(0, 0, 5, 5);
        gbc_lblAttachAddFile.gridx = 0;
        gbc_lblAttachAddFile.gridy = 0;
        pnlAttachAddControls.add(lblAttachAddFile, gbc_lblAttachAddFile);

        txtAttachAddFile = new JTextField();
        txtAttachAddFile.setEditable(false);
        GridBagConstraints gbc_txtAttachAddFile = new GridBagConstraints();
        gbc_txtAttachAddFile.insets = new Insets(0, 0, 5, 5);
        gbc_txtAttachAddFile.fill = GridBagConstraints.HORIZONTAL;
        gbc_txtAttachAddFile.gridx = 1;
        gbc_txtAttachAddFile.gridy = 0;
        pnlAttachAddControls.add(txtAttachAddFile, gbc_txtAttachAddFile);
        txtAttachAddFile.setColumns(10);

        btnBrowseAttachAddFile = new JButton("Browse...");
        GridBagConstraints gbc_btnBrowseAttachAddFile = new GridBagConstraints();
        gbc_btnBrowseAttachAddFile.insets = new Insets(0, 0, 5, 0);
        gbc_btnBrowseAttachAddFile.gridx = 2;
        gbc_btnBrowseAttachAddFile.gridy = 0;
        pnlAttachAddControls.add(btnBrowseAttachAddFile, gbc_btnBrowseAttachAddFile);

        lblAttachAddName = new JLabel("Name:");
        GridBagConstraints gbc_lblAttachAddName = new GridBagConstraints();
        gbc_lblAttachAddName.anchor = GridBagConstraints.WEST;
        gbc_lblAttachAddName.insets = new Insets(0, 0, 5, 5);
        gbc_lblAttachAddName.gridx = 0;
        gbc_lblAttachAddName.gridy = 1;
        pnlAttachAddControls.add(lblAttachAddName, gbc_lblAttachAddName);

        txtAttachAddName = new JTextField();
        GridBagConstraints gbc_txtAttachAddName = new GridBagConstraints();
        gbc_txtAttachAddName.insets = new Insets(0, 0, 5, 5);
        gbc_txtAttachAddName.fill = GridBagConstraints.HORIZONTAL;
        gbc_txtAttachAddName.gridx = 1;
        gbc_txtAttachAddName.gridy = 1;
        pnlAttachAddControls.add(txtAttachAddName, gbc_txtAttachAddName);
        txtAttachAddName.setColumns(10);

        lblAttachAddDesc = new JLabel("Description:");
        GridBagConstraints gbc_lblAttachAddDesc = new GridBagConstraints();
        gbc_lblAttachAddDesc.anchor = GridBagConstraints.EAST;
        gbc_lblAttachAddDesc.insets = new Insets(0, 0, 5, 5);
        gbc_lblAttachAddDesc.gridx = 0;
        gbc_lblAttachAddDesc.gridy = 2;
        pnlAttachAddControls.add(lblAttachAddDesc, gbc_lblAttachAddDesc);

        txtAttachAddDesc = new JTextField();
        GridBagConstraints gbc_txtAttachAddDesc = new GridBagConstraints();
        gbc_txtAttachAddDesc.insets = new Insets(0, 0, 5, 5);
        gbc_txtAttachAddDesc.fill = GridBagConstraints.HORIZONTAL;
        gbc_txtAttachAddDesc.gridx = 1;
        gbc_txtAttachAddDesc.gridy = 2;
        pnlAttachAddControls.add(txtAttachAddDesc, gbc_txtAttachAddDesc);
        txtAttachAddDesc.setColumns(10);

        lblAttachAddMime = new JLabel("MIME Type:");
        GridBagConstraints gbc_lblAttachAddMime = new GridBagConstraints();
        gbc_lblAttachAddMime.anchor = GridBagConstraints.EAST;
        gbc_lblAttachAddMime.insets = new Insets(0, 0, 5, 5);
        gbc_lblAttachAddMime.gridx = 0;
        gbc_lblAttachAddMime.gridy = 3;
        pnlAttachAddControls.add(lblAttachAddMime, gbc_lblAttachAddMime);

        cbAttachAddMime = new JComboBox<String>();
        cbAttachAddMime.setModel(new DefaultComboBoxModel<String>(mimeComboItems()));
        GridBagConstraints gbc_cbAttachAddMime = new GridBagConstraints();
        gbc_cbAttachAddMime.insets = new Insets(0, 0, 5, 5);
        gbc_cbAttachAddMime.fill = GridBagConstraints.HORIZONTAL;
        gbc_cbAttachAddMime.gridx = 1;
        gbc_cbAttachAddMime.gridy = 3;
        pnlAttachAddControls.add(cbAttachAddMime, gbc_cbAttachAddMime);

        pnlAttachAddControlsBottom = new JPanel();
        GridBagConstraints gbc_pnlAttachAddControlsBottom = new GridBagConstraints();
        gbc_pnlAttachAddControlsBottom.insets = new Insets(0, 0, 0, 5);
        gbc_pnlAttachAddControlsBottom.fill = GridBagConstraints.BOTH;
        gbc_pnlAttachAddControlsBottom.gridx = 1;
        gbc_pnlAttachAddControlsBottom.gridy = 4;
        pnlAttachAddControls.add(pnlAttachAddControlsBottom, gbc_pnlAttachAddControlsBottom);
        GridBagLayout gbl_pnlAttachAddControlsBottom = new GridBagLayout();
        gbl_pnlAttachAddControlsBottom.columnWidths = new int[] { 0, 0, 0, 0, 0 };
        gbl_pnlAttachAddControlsBottom.rowHeights = new int[] { 0, 0 };
        gbl_pnlAttachAddControlsBottom.columnWeights = new double[] { 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE };
        gbl_pnlAttachAddControlsBottom.rowWeights = new double[] { 0.0, Double.MIN_VALUE };
        pnlAttachAddControlsBottom.setLayout(gbl_pnlAttachAddControlsBottom);

        btnAttachAddAdd = new JButton("Add");
        GridBagConstraints gbc_btnAttachAddAdd = new GridBagConstraints();
        gbc_btnAttachAddAdd.insets = new Insets(0, 0, 0, 5);
        gbc_btnAttachAddAdd.gridx = 0;
        gbc_btnAttachAddAdd.gridy = 0;
        pnlAttachAddControlsBottom.add(btnAttachAddAdd, gbc_btnAttachAddAdd);

        btnAttachAddEdit = new JButton("Edit");
        btnAttachAddEdit.setEnabled(false);
        GridBagConstraints gbc_btnAttachAddEdit = new GridBagConstraints();
        gbc_btnAttachAddEdit.insets = new Insets(0, 0, 0, 5);
        gbc_btnAttachAddEdit.gridx = 1;
        gbc_btnAttachAddEdit.gridy = 0;
        pnlAttachAddControlsBottom.add(btnAttachAddEdit, gbc_btnAttachAddEdit);

        btnAttachAddRemove = new JButton("Remove");
        btnAttachAddRemove.setEnabled(false);
        GridBagConstraints gbc_btnAttachAddRemove = new GridBagConstraints();
        gbc_btnAttachAddRemove.insets = new Insets(0, 0, 0, 5);
        gbc_btnAttachAddRemove.anchor = GridBagConstraints.SOUTH;
        gbc_btnAttachAddRemove.gridx = 2;
        gbc_btnAttachAddRemove.gridy = 0;
        pnlAttachAddControlsBottom.add(btnAttachAddRemove, gbc_btnAttachAddRemove);

        btnAttachAddCancel = new JButton("Cancel");
        btnAttachAddCancel.setEnabled(false);
        GridBagConstraints gbc_btnAttachAddCancel = new GridBagConstraints();
        gbc_btnAttachAddCancel.gridx = 3;
        gbc_btnAttachAddCancel.gridy = 0;
        pnlAttachAddControlsBottom.add(btnAttachAddCancel, gbc_btnAttachAddCancel);

        pnlAttachReplace = new JPanel();
        pnlAttachments.addTab("Replace Attachments", null, pnlAttachReplace, null);
        pnlAttachReplace.setLayout(new BorderLayout(0, 0));

        spAttachReplace = new JScrollPane();
        pnlAttachReplace.add(spAttachReplace, BorderLayout.CENTER);

        tblAttachReplace = new JTable();
        tblAttachReplace.setShowGrid(false);
        tblAttachReplace.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tblAttachReplace.setModel(modelAttachmentsReplace);
        tblAttachReplace.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        tblAttachReplace.setAutoscrolls(false);
        tblAttachReplace.setFillsViewportHeight(true);
        spAttachReplace.setViewportView(tblAttachReplace);

        pnlAttachReplaceControls = new JPanel();
        pnlAttachReplaceControls.setBorder(new EmptyBorder(5, 5, 5, 5));
        pnlAttachReplace.add(pnlAttachReplaceControls, BorderLayout.SOUTH);
        GridBagLayout gbl_pnlAttachReplaceControls = new GridBagLayout();
        gbl_pnlAttachReplaceControls.columnWidths = new int[] { 0, 0, 0, 0 };
        gbl_pnlAttachReplaceControls.rowHeights = new int[] { 0, 0, 0, 0, 0, 0, 0, 0 };
        gbl_pnlAttachReplaceControls.columnWeights = new double[] { 0.0, 1.0, 0.0, Double.MIN_VALUE };
        gbl_pnlAttachReplaceControls.rowWeights = new double[] { 1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, Double.MIN_VALUE };
        pnlAttachReplaceControls.setLayout(gbl_pnlAttachReplaceControls);

        lblAttachReplaceType = new JLabel("Type:");
        GridBagConstraints gbc_lblAttachReplaceType = new GridBagConstraints();
        gbc_lblAttachReplaceType.anchor = GridBagConstraints.WEST;
        gbc_lblAttachReplaceType.insets = new Insets(0, 0, 5, 5);
        gbc_lblAttachReplaceType.gridx = 0;
        gbc_lblAttachReplaceType.gridy = 0;
        pnlAttachReplaceControls.add(lblAttachReplaceType, gbc_lblAttachReplaceType);

        pnlAttachReplaceType = new JPanel();
        GridBagConstraints gbc_pnlAttachReplaceType = new GridBagConstraints();
        gbc_pnlAttachReplaceType.insets = new Insets(0, 0, 5, 5);
        gbc_pnlAttachReplaceType.fill = GridBagConstraints.BOTH;
        gbc_pnlAttachReplaceType.gridx = 1;
        gbc_pnlAttachReplaceType.gridy = 0;
        pnlAttachReplaceControls.add(pnlAttachReplaceType, gbc_pnlAttachReplaceType);
        GridBagLayout gbl_pnlAttachReplaceType = new GridBagLayout();
        gbl_pnlAttachReplaceType.columnWidths = new int[] { 0, 0, 0, 0 };
        gbl_pnlAttachReplaceType.rowHeights = new int[] { 0, 0 };
        gbl_pnlAttachReplaceType.columnWeights = new double[] { 0.0, 0.0, 0.0, Double.MIN_VALUE };
        gbl_pnlAttachReplaceType.rowWeights = new double[] { 0.0, Double.MIN_VALUE };
        pnlAttachReplaceType.setLayout(gbl_pnlAttachReplaceType);

        rbAttachReplaceName = new JRadioButton("Attachment name");
        rbAttachReplaceName.setSelected(true);
        GridBagConstraints gbc_rbAttachReplaceName = new GridBagConstraints();
        gbc_rbAttachReplaceName.insets = new Insets(0, 0, 0, 5);
        gbc_rbAttachReplaceName.gridx = 0;
        gbc_rbAttachReplaceName.gridy = 0;
        pnlAttachReplaceType.add(rbAttachReplaceName, gbc_rbAttachReplaceName);
        bgAttachReplaceType.add(rbAttachReplaceName);

        rbAttachReplaceID = new JRadioButton("Attachment ID");
        GridBagConstraints gbc_rbAttachReplaceID = new GridBagConstraints();
        gbc_rbAttachReplaceID.insets = new Insets(0, 0, 0, 5);
        gbc_rbAttachReplaceID.gridx = 1;
        gbc_rbAttachReplaceID.gridy = 0;
        pnlAttachReplaceType.add(rbAttachReplaceID, gbc_rbAttachReplaceID);
        bgAttachReplaceType.add(rbAttachReplaceID);

        rbAttachReplaceMime = new JRadioButton("Attachment(s) MIME Type");
        GridBagConstraints gbc_rbAttachReplaceMime = new GridBagConstraints();
        gbc_rbAttachReplaceMime.gridx = 2;
        gbc_rbAttachReplaceMime.gridy = 0;
        pnlAttachReplaceType.add(rbAttachReplaceMime, gbc_rbAttachReplaceMime);
        bgAttachReplaceType.add(rbAttachReplaceMime);

        lblAttachReplaceOrig = new JLabel("Original value:");
        GridBagConstraints gbc_lblAttachReplaceOrig = new GridBagConstraints();
        gbc_lblAttachReplaceOrig.insets = new Insets(0, 0, 5, 5);
        gbc_lblAttachReplaceOrig.anchor = GridBagConstraints.WEST;
        gbc_lblAttachReplaceOrig.gridx = 0;
        gbc_lblAttachReplaceOrig.gridy = 1;
        pnlAttachReplaceControls.add(lblAttachReplaceOrig, gbc_lblAttachReplaceOrig);

        pnlAttachReplaceOrig = new JPanel();
        GridBagConstraints gbc_pnlAttachReplaceOrig = new GridBagConstraints();
        gbc_pnlAttachReplaceOrig.insets = new Insets(0, 0, 5, 5);
        gbc_pnlAttachReplaceOrig.fill = GridBagConstraints.BOTH;
        gbc_pnlAttachReplaceOrig.gridx = 1;
        gbc_pnlAttachReplaceOrig.gridy = 1;
        pnlAttachReplaceControls.add(pnlAttachReplaceOrig, gbc_pnlAttachReplaceOrig);
        pnlAttachReplaceOrig.setLayout(new CardLayout(0, 0));

        txtAttachReplaceOrig = new JTextField();
        pnlAttachReplaceOrig.add(txtAttachReplaceOrig, "txtAttachReplaceOrig");
        txtAttachReplaceOrig.setColumns(10);

        cbAttachReplaceOrig = new JComboBox<String>();
        cbAttachReplaceOrig.setModel(new DefaultComboBoxModel<String>(mimeComboItems()));
        cbAttachReplaceOrig.setVisible(false);
        pnlAttachReplaceOrig.add(cbAttachReplaceOrig, "cbAttachReplaceOrig");

        lblAttachReplaceNew = new JLabel("Replacement:");
        GridBagConstraints gbc_lblAttachReplaceNew = new GridBagConstraints();
        gbc_lblAttachReplaceNew.anchor = GridBagConstraints.WEST;
        gbc_lblAttachReplaceNew.insets = new Insets(0, 0, 5, 5);
        gbc_lblAttachReplaceNew.gridx = 0;
        gbc_lblAttachReplaceNew.gridy = 2;
        pnlAttachReplaceControls.add(lblAttachReplaceNew, gbc_lblAttachReplaceNew);

        txtAttachReplaceNew = new JTextField();
        txtAttachReplaceNew.setEditable(false);
        GridBagConstraints gbc_txtAttachReplaceNew = new GridBagConstraints();
        gbc_txtAttachReplaceNew.insets = new Insets(0, 0, 5, 5);
        gbc_txtAttachReplaceNew.fill = GridBagConstraints.HORIZONTAL;
        gbc_txtAttachReplaceNew.gridx = 1;
        gbc_txtAttachReplaceNew.gridy = 2;
        pnlAttachReplaceControls.add(txtAttachReplaceNew, gbc_txtAttachReplaceNew);
        txtAttachReplaceNew.setColumns(10);

        btnAttachReplaceNewBrowse = new JButton("Browse....");
        GridBagConstraints gbc_btnAttachReplaceNewBrowse = new GridBagConstraints();
        gbc_btnAttachReplaceNewBrowse.insets = new Insets(0, 0, 5, 0);
        gbc_btnAttachReplaceNewBrowse.gridx = 2;
        gbc_btnAttachReplaceNewBrowse.gridy = 2;
        pnlAttachReplaceControls.add(btnAttachReplaceNewBrowse, gbc_btnAttachReplaceNewBrowse);

        lblAttachReplaceName = new JLabel("Name:");
        GridBagConstraints gbc_lblAttachReplaceName = new GridBagConstraints();
        gbc_lblAttachReplaceName.anchor = GridBagConstraints.WEST;
        gbc_lblAttachReplaceName.insets = new Insets(0, 0, 5, 5);
        gbc_lblAttachReplaceName.gridx = 0;
        gbc_lblAttachReplaceName.gridy = 3;
        pnlAttachReplaceControls.add(lblAttachReplaceName, gbc_lblAttachReplaceName);

        txtAttachReplaceName = new JTextField();
        txtAttachReplaceName.setColumns(10);
        GridBagConstraints gbc_txtAttachReplaceName = new GridBagConstraints();
        gbc_txtAttachReplaceName.insets = new Insets(0, 0, 5, 5);
        gbc_txtAttachReplaceName.fill = GridBagConstraints.HORIZONTAL;
        gbc_txtAttachReplaceName.gridx = 1;
        gbc_txtAttachReplaceName.gridy = 3;
        pnlAttachReplaceControls.add(txtAttachReplaceName, gbc_txtAttachReplaceName);

        lblAttachReplaceDesc = new JLabel("Description:");
        GridBagConstraints gbc_lblAttachReplaceDesc = new GridBagConstraints();
        gbc_lblAttachReplaceDesc.anchor = GridBagConstraints.WEST;
        gbc_lblAttachReplaceDesc.insets = new Insets(0, 0, 5, 5);
        gbc_lblAttachReplaceDesc.gridx = 0;
        gbc_lblAttachReplaceDesc.gridy = 4;
        pnlAttachReplaceControls.add(lblAttachReplaceDesc, gbc_lblAttachReplaceDesc);

        txtAttachReplaceDesc = new JTextField();
        txtAttachReplaceDesc.setColumns(10);
        GridBagConstraints gbc_txtAttachReplaceDesc = new GridBagConstraints();
        gbc_txtAttachReplaceDesc.insets = new Insets(0, 0, 5, 5);
        gbc_txtAttachReplaceDesc.fill = GridBagConstraints.HORIZONTAL;
        gbc_txtAttachReplaceDesc.gridx = 1;
        gbc_txtAttachReplaceDesc.gridy = 4;
        pnlAttachReplaceControls.add(txtAttachReplaceDesc, gbc_txtAttachReplaceDesc);

        lblAttachReplaceMime = new JLabel("MIME Type:");
        GridBagConstraints gbc_lblAttachReplaceMime = new GridBagConstraints();
        gbc_lblAttachReplaceMime.anchor = GridBagConstraints.WEST;
        gbc_lblAttachReplaceMime.insets = new Insets(0, 0, 5, 5);
        gbc_lblAttachReplaceMime.gridx = 0;
        gbc_lblAttachReplaceMime.gridy = 5;
        pnlAttachReplaceControls.add(lblAttachReplaceMime, gbc_lblAttachReplaceMime);

        cbAttachReplaceMime = new JComboBox<String>();
        cbAttachReplaceMime.setModel(new DefaultComboBoxModel<String>(mimeComboItems()));
        GridBagConstraints gbc_cbAttachReplaceMime = new GridBagConstraints();
        gbc_cbAttachReplaceMime.insets = new Insets(0, 0, 5, 5);
        gbc_cbAttachReplaceMime.fill = GridBagConstraints.HORIZONTAL;
        gbc_cbAttachReplaceMime.gridx = 1;
        gbc_cbAttachReplaceMime.gridy = 5;
        pnlAttachReplaceControls.add(cbAttachReplaceMime, gbc_cbAttachReplaceMime);

        pnlAttachReplaceControlsBottom = new JPanel();
        GridBagConstraints gbc_pnlAttachReplaceControlsBottom = new GridBagConstraints();
        gbc_pnlAttachReplaceControlsBottom.anchor = GridBagConstraints.WEST;
        gbc_pnlAttachReplaceControlsBottom.insets = new Insets(0, 0, 0, 5);
        gbc_pnlAttachReplaceControlsBottom.fill = GridBagConstraints.VERTICAL;
        gbc_pnlAttachReplaceControlsBottom.gridx = 1;
        gbc_pnlAttachReplaceControlsBottom.gridy = 6;
        pnlAttachReplaceControls.add(pnlAttachReplaceControlsBottom, gbc_pnlAttachReplaceControlsBottom);
        GridBagLayout gbl_pnlAttachReplaceControlsBottom = new GridBagLayout();
        gbl_pnlAttachReplaceControlsBottom.columnWidths = new int[] { 0, 0, 0, 0 };
        gbl_pnlAttachReplaceControlsBottom.rowHeights = new int[] { 0, 0 };
        gbl_pnlAttachReplaceControlsBottom.columnWeights = new double[] { 0.0, 0.0, 0.0, 0.0 };
        gbl_pnlAttachReplaceControlsBottom.rowWeights = new double[] { 0.0, Double.MIN_VALUE };
        pnlAttachReplaceControlsBottom.setLayout(gbl_pnlAttachReplaceControlsBottom);

        btnAttachReplaceAdd = new JButton("Add");
        GridBagConstraints gbc_btnAttachReplaceAdd = new GridBagConstraints();
        gbc_btnAttachReplaceAdd.insets = new Insets(0, 0, 0, 5);
        gbc_btnAttachReplaceAdd.gridx = 0;
        gbc_btnAttachReplaceAdd.gridy = 0;
        pnlAttachReplaceControlsBottom.add(btnAttachReplaceAdd, gbc_btnAttachReplaceAdd);

        btnAttachReplaceEdit = new JButton("Edit");
        btnAttachReplaceEdit.setEnabled(false);
        GridBagConstraints gbc_btnAttachReplaceEdit = new GridBagConstraints();
        gbc_btnAttachReplaceEdit.insets = new Insets(0, 0, 0, 5);
        gbc_btnAttachReplaceEdit.gridx = 1;
        gbc_btnAttachReplaceEdit.gridy = 0;
        pnlAttachReplaceControlsBottom.add(btnAttachReplaceEdit, gbc_btnAttachReplaceEdit);

        btnAttachReplaceRemove = new JButton("Remove");
        btnAttachReplaceRemove.setEnabled(false);
        GridBagConstraints gbc_btnAttachReplaceRemove = new GridBagConstraints();
        gbc_btnAttachReplaceRemove.anchor = GridBagConstraints.SOUTH;
        gbc_btnAttachReplaceRemove.insets = new Insets(0, 0, 0, 5);
        gbc_btnAttachReplaceRemove.gridx = 2;
        gbc_btnAttachReplaceRemove.gridy = 0;
        pnlAttachReplaceControlsBottom.add(btnAttachReplaceRemove, gbc_btnAttachReplaceRemove);

        btnAttachReplaceCancel = new JButton("Cancel");
        btnAttachReplaceCancel.setEnabled(false);
        GridBagConstraints gbc_btnAttachReplaceCancel = new GridBagConstraints();
        gbc_btnAttachReplaceCancel.gridx = 3;
        gbc_btnAttachReplaceCancel.gridy = 0;
        pnlAttachReplaceControlsBottom.add(btnAttachReplaceCancel, gbc_btnAttachReplaceCancel);

        pnlAttachDelete = new JPanel();
        pnlAttachments.addTab("Delete Attachments", null, pnlAttachDelete, null);
        pnlAttachDelete.setLayout(new BorderLayout(0, 0));

        spAttachDelete = new JScrollPane();
        pnlAttachDelete.add(spAttachDelete, BorderLayout.CENTER);

        tblAttachDelete = new JTable();
        tblAttachDelete.setShowGrid(false);
        tblAttachDelete.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tblAttachDelete.setModel(modelAttachmentsDelete);
        tblAttachDelete.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        tblAttachDelete.setAutoscrolls(false);
        tblAttachDelete.setFillsViewportHeight(true);
        spAttachDelete.setViewportView(tblAttachDelete);

        pnlAttachDeleteControls = new JPanel();
        pnlAttachDeleteControls.setBorder(new EmptyBorder(5, 5, 5, 5));
        pnlAttachDelete.add(pnlAttachDeleteControls, BorderLayout.SOUTH);
        GridBagLayout gbl_pnlAttachDeleteControls = new GridBagLayout();
        gbl_pnlAttachDeleteControls.columnWidths = new int[] { 0, 0, 0 };
        gbl_pnlAttachDeleteControls.rowHeights = new int[] { 0, 0, 0, 0 };
        gbl_pnlAttachDeleteControls.columnWeights = new double[] { 0.0, 1.0, Double.MIN_VALUE };
        gbl_pnlAttachDeleteControls.rowWeights = new double[] { 1.0, 1.0, 1.0, Double.MIN_VALUE };
        pnlAttachDeleteControls.setLayout(gbl_pnlAttachDeleteControls);

        lblAttachDeleteType = new JLabel("Type:");
        GridBagConstraints gbc_lblAttachDeleteType = new GridBagConstraints();
        gbc_lblAttachDeleteType.insets = new Insets(0, 0, 5, 5);
        gbc_lblAttachDeleteType.gridx = 0;
        gbc_lblAttachDeleteType.gridy = 0;
        pnlAttachDeleteControls.add(lblAttachDeleteType, gbc_lblAttachDeleteType);

        pnlAttachDeleteType = new JPanel();
        GridBagConstraints gbc_pnlAttachDeleteType = new GridBagConstraints();
        gbc_pnlAttachDeleteType.anchor = GridBagConstraints.WEST;
        gbc_pnlAttachDeleteType.insets = new Insets(0, 0, 5, 0);
        gbc_pnlAttachDeleteType.fill = GridBagConstraints.VERTICAL;
        gbc_pnlAttachDeleteType.gridx = 1;
        gbc_pnlAttachDeleteType.gridy = 0;
        pnlAttachDeleteControls.add(pnlAttachDeleteType, gbc_pnlAttachDeleteType);
        GridBagLayout gbl_pnlAttachDeleteType = new GridBagLayout();
        gbl_pnlAttachDeleteType.columnWidths = new int[] { 0, 0, 0 };
        gbl_pnlAttachDeleteType.rowHeights = new int[] { 0, 0 };
        gbl_pnlAttachDeleteType.columnWeights = new double[] { 0.0, 0.0, 0.0 };
        gbl_pnlAttachDeleteType.rowWeights = new double[] { 0.0, Double.MIN_VALUE };
        pnlAttachDeleteType.setLayout(gbl_pnlAttachDeleteType);

        rbAttachDeleteName = new JRadioButton("Attachment name");
        rbAttachDeleteName.setSelected(true);
        GridBagConstraints gbc_rbAttachDeleteName = new GridBagConstraints();
        gbc_rbAttachDeleteName.insets = new Insets(0, 0, 0, 5);
        gbc_rbAttachDeleteName.gridx = 0;
        gbc_rbAttachDeleteName.gridy = 0;
        pnlAttachDeleteType.add(rbAttachDeleteName, gbc_rbAttachDeleteName);
        bgAttachDeleteType.add(rbAttachDeleteName);

        rbAttachDeleteID = new JRadioButton("Attachment ID");
        GridBagConstraints gbc_rbAttachDeleteID = new GridBagConstraints();
        gbc_rbAttachDeleteID.insets = new Insets(0, 0, 0, 5);
        gbc_rbAttachDeleteID.gridx = 1;
        gbc_rbAttachDeleteID.gridy = 0;
        pnlAttachDeleteType.add(rbAttachDeleteID, gbc_rbAttachDeleteID);
        bgAttachDeleteType.add(rbAttachDeleteID);

        rbAttachDeleteMime = new JRadioButton("Attachment(s) MIME Type");
        GridBagConstraints gbc_rbAttachDeleteMime = new GridBagConstraints();
        gbc_rbAttachDeleteMime.gridx = 2;
        gbc_rbAttachDeleteMime.gridy = 0;
        pnlAttachDeleteType.add(rbAttachDeleteMime, gbc_rbAttachDeleteMime);
        bgAttachDeleteType.add(rbAttachDeleteMime);

        lblAttachDeleteValue = new JLabel("Value:");
        GridBagConstraints gbc_lblAttachDeleteValue = new GridBagConstraints();
        gbc_lblAttachDeleteValue.anchor = GridBagConstraints.EAST;
        gbc_lblAttachDeleteValue.insets = new Insets(0, 0, 5, 5);
        gbc_lblAttachDeleteValue.gridx = 0;
        gbc_lblAttachDeleteValue.gridy = 1;
        pnlAttachDeleteControls.add(lblAttachDeleteValue, gbc_lblAttachDeleteValue);

        pnlAttachDeleteValue = new JPanel();
        GridBagConstraints gbc_pnlAttachDeleteValue = new GridBagConstraints();
        gbc_pnlAttachDeleteValue.insets = new Insets(0, 0, 5, 0);
        gbc_pnlAttachDeleteValue.fill = GridBagConstraints.BOTH;
        gbc_pnlAttachDeleteValue.gridx = 1;
        gbc_pnlAttachDeleteValue.gridy = 1;
        pnlAttachDeleteControls.add(pnlAttachDeleteValue, gbc_pnlAttachDeleteValue);
        pnlAttachDeleteValue.setLayout(new CardLayout(0, 0));

        txtAttachDeleteValue = new JTextField();
        pnlAttachDeleteValue.add(txtAttachDeleteValue, "txtAttachDeleteValue");
        txtAttachDeleteValue.setColumns(10);

        cbAttachDeleteValue = new JComboBox<String>();
        cbAttachDeleteValue.setVisible(false);
        cbAttachDeleteValue.setModel(new DefaultComboBoxModel<String>(mimeComboItems()));
        pnlAttachDeleteValue.add(cbAttachDeleteValue, "cbAttachDeleteValue");

        pnlAttachDeleteControlsBottom = new JPanel();
        GridBagConstraints gbc_pnlAttachDeleteControlsBottom = new GridBagConstraints();
        gbc_pnlAttachDeleteControlsBottom.fill = GridBagConstraints.BOTH;
        gbc_pnlAttachDeleteControlsBottom.gridx = 1;
        gbc_pnlAttachDeleteControlsBottom.gridy = 2;
        pnlAttachDeleteControls.add(pnlAttachDeleteControlsBottom, gbc_pnlAttachDeleteControlsBottom);
        GridBagLayout gbl_pnlAttachDeleteControlsBottom = new GridBagLayout();
        gbl_pnlAttachDeleteControlsBottom.columnWidths = new int[] { 0, 0, 0, 0, 0, 0, 0, 0 };
        gbl_pnlAttachDeleteControlsBottom.rowHeights = new int[] { 0, 0 };
        gbl_pnlAttachDeleteControlsBottom.columnWeights = new double[] { 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE };
        gbl_pnlAttachDeleteControlsBottom.rowWeights = new double[] { 0.0, Double.MIN_VALUE };
        pnlAttachDeleteControlsBottom.setLayout(gbl_pnlAttachDeleteControlsBottom);

        btnAttachDeleteAdd = new JButton("Add");
        GridBagConstraints gbc_btnAttachDeleteAdd = new GridBagConstraints();
        gbc_btnAttachDeleteAdd.insets = new Insets(0, 0, 5, 5);
        gbc_btnAttachDeleteAdd.gridx = 0;
        gbc_btnAttachDeleteAdd.gridy = 0;
        pnlAttachDeleteControlsBottom.add(btnAttachDeleteAdd, gbc_btnAttachDeleteAdd);

        btnAttachDeleteEdit = new JButton("Edit");
        btnAttachDeleteEdit.setEnabled(false);
        GridBagConstraints gbc_btnAttachDeleteEdit = new GridBagConstraints();
        gbc_btnAttachDeleteEdit.insets = new Insets(0, 0, 5, 5);
        gbc_btnAttachDeleteEdit.gridx = 1;
        gbc_btnAttachDeleteEdit.gridy = 0;
        pnlAttachDeleteControlsBottom.add(btnAttachDeleteEdit, gbc_btnAttachDeleteEdit);

        btnAttachDeleteRemove = new JButton("Remove");
        btnAttachDeleteRemove.setEnabled(false);
        GridBagConstraints gbc_btnAttachDeleteRemove = new GridBagConstraints();
        gbc_btnAttachDeleteRemove.anchor = GridBagConstraints.SOUTH;
        gbc_btnAttachDeleteRemove.insets = new Insets(0, 0, 5, 5);
        gbc_btnAttachDeleteRemove.gridx = 2;
        gbc_btnAttachDeleteRemove.gridy = 0;
        pnlAttachDeleteControlsBottom.add(btnAttachDeleteRemove, gbc_btnAttachDeleteRemove);

        btnAttachDeleteCancel = new JButton("Cancel");
        btnAttachDeleteCancel.setEnabled(false);
        GridBagConstraints gbc_btnAttachDeleteCancel = new GridBagConstraints();
        gbc_btnAttachDeleteCancel.insets = new Insets(0, 0, 5, 5);
        gbc_btnAttachDeleteCancel.gridx = 3;
        gbc_btnAttachDeleteCancel.gridy = 0;
        pnlAttachDeleteControlsBottom.add(btnAttachDeleteCancel, gbc_btnAttachDeleteCancel);

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

        JButton btnBrowseMkvPropExe = new JButton("Browse...");
        GridBagConstraints gbc_button = new GridBagConstraints();
        gbc_button.gridx = 1;
        gbc_button.gridy = 0;
        pnlMkvPropExeControls.add(btnBrowseMkvPropExe, gbc_button);

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

        JPanel pnlButtons = new JPanel();
        frmJMkvpropedit.getContentPane().add(pnlButtons, BorderLayout.SOUTH);

        btnProcessFiles = new JButton("Process files");
        pnlButtons.add(btnProcessFiles);

        btnGenerateCmdLine = new JButton("Generate command line");
        pnlButtons.add(btnGenerateCmdLine);

        /* Start of mouse events for right-click menu */

        Utils.addRCMenuMouseListener(txtTitleGeneral);
        Utils.addRCMenuMouseListener(txtNumbStartGeneral);
        Utils.addRCMenuMouseListener(txtNumbPadGeneral);
        Utils.addRCMenuMouseListener(txtChapters);
        Utils.addRCMenuMouseListener(txtTags);
        Utils.addRCMenuMouseListener(txtExtraCmdGeneral);
        Utils.addRCMenuMouseListener(txtMkvPropExe);
        Utils.addRCMenuMouseListener(txtAttachAddFile);
        Utils.addRCMenuMouseListener(txtAttachAddName);
        Utils.addRCMenuMouseListener(txtAttachAddDesc);
        Utils.addRCMenuMouseListener(txtAttachReplaceOrig);
        Utils.addRCMenuMouseListener(txtAttachReplaceNew);
        Utils.addRCMenuMouseListener(txtAttachReplaceName);
        Utils.addRCMenuMouseListener(txtAttachReplaceDesc);
        Utils.addRCMenuMouseListener(txtAttachDeleteValue);
        Utils.addRCMenuMouseListener(txtOutput);

        /* End of mouse events for right-click menu */

        frmJMkvpropedit.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                // Resize the window to make sure the components fit
                // frmJMkvpropedit.pack();

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
                boolean wRunning;

                try {
                    wRunning = !worker.isDone();
                } catch (Exception e1) {
                    wRunning = false;
                }

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
                    resizeColumns(tblAttachAdd, COLUMN_SIZES_ATTACHMENTS_ADD);
                    resizeColumns(tblAttachReplace, COLUMN_SIZES_ATTACHMENTS_REPLACE);
                    resizeColumns(tblAttachDelete, COLUMN_SIZES_ATTACHMENTS_DELETE);
                }

                // Store new dimensions
                frmJMkvpropeditDim = new Dimension(frmJMkvpropedit.getWidth(), frmJMkvpropedit.getHeight());
            }
        });

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

        txtNumbStartGeneral.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                try {
                    if (Integer.parseInt(txtNumbStartGeneral.getText()) < 0) {
                        txtNumbStartGeneral.setText("1");
                    }
                } catch (NumberFormatException e1) {
                    txtNumbStartGeneral.setText("1");
                }
            }
        });

        txtNumbPadGeneral.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                try {
                    if (Integer.parseInt(txtNumbPadGeneral.getText()) < 0) {
                        txtNumbPadGeneral.setText("1");
                    }
                } catch (NumberFormatException e1) {
                    txtNumbPadGeneral.setText("1");
                }
            }
        });

        chbChapters.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                boolean state = cbChapters.isEnabled();
                cbChapters.setEnabled(!state);

                if (cbChapters.getSelectedIndex() == 1) {
                    txtChapters.setEditable(false);
                    txtChapters.setVisible(true);
                    txtChapters.setEnabled(!state);
                    btnBrowseChapters.setVisible(true);
                    btnBrowseChapters.setEnabled(!state);
                    cbExtChapters.setVisible(false);
                } else if (cbChapters.getSelectedIndex() == 2) {
                    txtChapters.setEditable(true);
                    txtChapters.setVisible(true);
                    txtChapters.setEnabled(!state);
                    btnBrowseChapters.setVisible(false);
                    btnBrowseChapters.setEnabled(!state);
                    cbExtChapters.setVisible(true);
                    cbExtChapters.setEnabled(!state);
                } else if (!chbChapters.isSelected()) {
                    txtChapters.setVisible(false);
                    btnBrowseChapters.setVisible(false);
                    cbExtChapters.setVisible(false);
                }
            }
        });

        cbChapters.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (cbChapters.getSelectedIndex() == 0) {
                    txtChapters.setVisible(false);
                    btnBrowseChapters.setVisible(false);
                    cbExtChapters.setVisible(false);
                } else if (cbChapters.getSelectedIndex() == 1) {
                    txtChapters.setText("");
                    txtChapters.setEditable(false);
                    txtChapters.setVisible(true);
                    btnBrowseChapters.setVisible(true);
                    cbExtChapters.setVisible(false);
                } else {
                    txtChapters.setText("-chapters");
                    txtChapters.setEditable(true);
                    txtChapters.setVisible(true);
                    btnBrowseChapters.setVisible(false);
                    cbExtChapters.setVisible(true);
                }
            }
        });

        btnBrowseChapters.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                configureTextFileChooser(chooser, "Select chapters file");

                int open = chooser.showOpenDialog(frmJMkvpropedit);

                if (open == JFileChooser.APPROVE_OPTION) {
                    if (chooser.getSelectedFile().exists()) {
                        txtChapters.setText(chooser.getSelectedFile().toString());
                    }
                }
            }
        });

        chbTags.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                boolean state = cbTags.isEnabled();
                cbTags.setEnabled(!state);

                if (cbTags.getSelectedIndex() == 1) {
                    txtTags.setEditable(false);
                    txtTags.setVisible(true);
                    txtTags.setEnabled(!state);
                    btnBrowseTags.setVisible(true);
                    btnBrowseTags.setEnabled(!state);
                    cbExtTags.setVisible(false);
                } else if (cbTags.getSelectedIndex() == 2) {
                    txtTags.setEditable(true);
                    txtTags.setVisible(true);
                    txtTags.setEnabled(!state);
                    btnBrowseTags.setVisible(false);
                    btnBrowseTags.setEnabled(!state);
                    cbExtTags.setVisible(true);
                    cbExtTags.setEnabled(!state);
                } else if (!chbTags.isSelected()) {
                    txtTags.setVisible(false);
                    btnBrowseTags.setVisible(false);
                    cbExtTags.setVisible(false);
                }
            }
        });

        cbTags.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (cbTags.getSelectedIndex() == 0) {
                    txtTags.setVisible(false);
                    btnBrowseTags.setVisible(false);
                    cbExtTags.setVisible(false);
                } else if (cbTags.getSelectedIndex() == 1) {
                    txtTags.setText("");
                    txtTags.setEditable(false);
                    txtTags.setVisible(true);
                    btnBrowseTags.setVisible(true);
                    cbExtTags.setVisible(false);
                } else {
                    txtTags.setText("-tags");
                    txtTags.setEditable(true);
                    txtTags.setVisible(true);
                    btnBrowseTags.setVisible(false);
                    cbExtTags.setVisible(true);
                }
            }
        });

        btnBrowseTags.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                configureTextFileChooser(chooser, "Select tags file");

                int open = chooser.showOpenDialog(frmJMkvpropedit);

                if (open == JFileChooser.APPROVE_OPTION) {
                    if (chooser.getSelectedFile().exists()) {
                        txtTags.setText(chooser.getSelectedFile().toString());
                    }
                }
            }
        });

        chbExtraCmdGeneral.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                boolean state = txtExtraCmdGeneral.isEnabled();
                txtExtraCmdGeneral.setEnabled(!state);
            }
        });

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

        new FileDrop(txtAttachAddFile, new FileDrop.Listener() {
            public void filesDropped(File[] files) {
                try {
                    if (!files[0].isDirectory()) {
                        txtAttachAddFile.setText(files[0].getCanonicalPath());
                    }
                } catch (IOException e) {
                    appendOutput("Error: could not resolve dropped attachment: " + e + "\n");
                }
            }
        });

        btnBrowseAttachAddFile.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                chooser.setDialogTitle("Select attachment");
                chooser.setMultiSelectionEnabled(false);
                chooser.resetChoosableFileFilters();
                chooser.setAcceptAllFileFilterUsed(true);

                int open = chooser.showOpenDialog(frmJMkvpropedit);

                if (open == JFileChooser.APPROVE_OPTION) {
                    File f = chooser.getSelectedFile();

                    if (f.exists()) {
                        try {
                            txtAttachAddFile.setText(f.getCanonicalPath());
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                    }
                }
            }
        });

        tblAttachAdd.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (modelAttachmentsAdd.getRowCount() == 0 || !tblAttachAdd.isEnabled()) {
                    return;
                }

                int selection = tblAttachAdd.getSelectedRow();

                if (selection != -1) {
                    String file = modelAttachmentsAdd.getValueAt(selection, 0).toString();
                    String name = modelAttachmentsAdd.getValueAt(selection, 1).toString();
                    String desc = modelAttachmentsAdd.getValueAt(selection, 2).toString();
                    String mime = modelAttachmentsAdd.getValueAt(selection, 3).toString();

                    txtAttachAddFile.setText(file);
                    txtAttachAddName.setText(name);
                    txtAttachAddDesc.setText(desc);
                    cbAttachAddMime.setSelectedItem(mime);

                    tblAttachAdd.setEnabled(false);
                    btnAttachAddAdd.setEnabled(false);
                    btnAttachAddRemove.setEnabled(true);
                    btnAttachAddEdit.setEnabled(true);
                    btnAttachAddCancel.setEnabled(true);
                }
            }
        });

        btnAttachAddAdd.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (txtAttachAddFile.getText().trim().isEmpty()) {
                    JOptionPane.showMessageDialog(null, "The file is mandatory for the attachment!", "",
                            JOptionPane.ERROR_MESSAGE);

                    return;
                }

                String[] rowData = { txtAttachAddFile.getText(), txtAttachAddName.getText().trim(),
                        txtAttachAddDesc.getText().trim(), cbAttachAddMime.getSelectedItem().toString() };

                modelAttachmentsAdd.addRow(rowData);

                Utils.adjustColumnPreferredWidths(tblAttachAdd);
                tblAttachAdd.revalidate();

                txtAttachAddFile.setText("");
                txtAttachAddName.setText("");
                txtAttachAddDesc.setText("");
                cbAttachAddMime.setSelectedIndex(0);
            }
        });

        btnAttachAddEdit.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (txtAttachAddFile.getText().trim().isEmpty()) {
                    JOptionPane.showMessageDialog(null, "The file is mandatory for the attachment!", "",
                            JOptionPane.ERROR_MESSAGE);

                    return;
                }

                int selection = tblAttachAdd.getSelectedRow();

                String file = txtAttachAddFile.getText().trim();
                String name = txtAttachAddName.getText().trim();
                String desc = txtAttachAddDesc.getText().trim();
                String mime = cbAttachAddMime.getSelectedItem().toString();

                modelAttachmentsAdd.setValueAt(file, selection, 0);
                modelAttachmentsAdd.setValueAt(name, selection, 1);
                modelAttachmentsAdd.setValueAt(desc, selection, 2);
                modelAttachmentsAdd.setValueAt(mime, selection, 3);

                Utils.adjustColumnPreferredWidths(tblAttachAdd);
                tblAttachAdd.revalidate();

                txtAttachAddFile.setText("");
                txtAttachAddName.setText("");
                txtAttachAddDesc.setText("");
                cbAttachAddMime.setSelectedIndex(0);

                tblAttachAdd.setEnabled(true);
                btnAttachAddAdd.setEnabled(true);
                btnAttachAddRemove.setEnabled(false);
                btnAttachAddEdit.setEnabled(false);
                btnAttachAddCancel.setEnabled(false);
                tblAttachAdd.clearSelection();
            }
        });

        btnAttachAddRemove.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int selection = tblAttachAdd.getSelectedRow();

                modelAttachmentsAdd.removeRow(selection);

                txtAttachAddFile.setText("");
                txtAttachAddName.setText("");
                txtAttachAddDesc.setText("");
                cbAttachAddMime.setSelectedIndex(0);

                tblAttachAdd.setEnabled(true);
                btnAttachAddAdd.setEnabled(true);
                btnAttachAddRemove.setEnabled(false);
                btnAttachAddEdit.setEnabled(false);
                btnAttachAddCancel.setEnabled(false);
            }
        });

        btnAttachAddCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                txtAttachAddFile.setText("");
                txtAttachAddName.setText("");
                txtAttachAddDesc.setText("");
                cbAttachAddMime.setSelectedIndex(0);

                tblAttachAdd.setEnabled(true);
                btnAttachAddAdd.setEnabled(true);
                btnAttachAddRemove.setEnabled(false);
                btnAttachAddEdit.setEnabled(false);
                btnAttachAddCancel.setEnabled(false);
                tblAttachAdd.clearSelection();
            }
        });

        rbAttachReplaceName.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                cbAttachReplaceOrig.setVisible(false);
                txtAttachReplaceOrig.setVisible(true);
                txtAttachReplaceOrig.setText("");
            }
        });

        rbAttachReplaceID.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                cbAttachReplaceOrig.setVisible(false);
                txtAttachReplaceOrig.setVisible(true);
                txtAttachReplaceOrig.setText("1");
            }
        });

        rbAttachReplaceMime.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                txtAttachReplaceOrig.setVisible(false);
                cbAttachReplaceOrig.setVisible(true);
                cbAttachReplaceOrig.setSelectedIndex(0);
            }
        });

        txtAttachReplaceOrig.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                if (!rbAttachReplaceID.isSelected()) {
                    return;
                }

                try {
                    int id = Integer.parseInt(txtAttachReplaceOrig.getText());

                    if (id < 1) {
                        txtAttachReplaceOrig.setText("1");
                    }
                } catch (NumberFormatException e1) {
                    txtAttachReplaceOrig.setText("1");
                }
            }
        });

        new FileDrop(txtAttachReplaceNew, new FileDrop.Listener() {
            public void filesDropped(File[] files) {
                try {
                    if (!files[0].isDirectory()) {
                        txtAttachReplaceNew.setText(files[0].getCanonicalPath());
                    }
                } catch (IOException e) {
                    appendOutput("Error: could not resolve dropped attachment: " + e + "\n");
                }
            }
        });

        btnAttachReplaceNewBrowse.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                chooser.setDialogTitle("Select attachment");
                chooser.setMultiSelectionEnabled(false);
                chooser.resetChoosableFileFilters();
                chooser.setAcceptAllFileFilterUsed(true);

                int open = chooser.showOpenDialog(frmJMkvpropedit);

                if (open == JFileChooser.APPROVE_OPTION) {
                    File f = chooser.getSelectedFile();

                    if (f.exists()) {
                        try {
                            txtAttachReplaceNew.setText(f.getCanonicalPath());
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                    }
                }
            }
        });

        tblAttachReplace.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (modelAttachmentsReplace.getRowCount() == 0 || !tblAttachReplace.isEnabled()) {
                    return;
                }

                int selection = tblAttachReplace.getSelectedRow();

                if (selection != -1) {
                    String type = modelAttachmentsReplace.getValueAt(selection, 0).toString();
                    String orig = modelAttachmentsReplace.getValueAt(selection, 1).toString();
                    String replace = modelAttachmentsReplace.getValueAt(selection, 2).toString();
                    String name = modelAttachmentsReplace.getValueAt(selection, 3).toString();
                    String desc = modelAttachmentsReplace.getValueAt(selection, 4).toString();
                    String mime = modelAttachmentsReplace.getValueAt(selection, 5).toString();

                    txtAttachReplaceNew.setText(replace);

                    if (type.equals(rbAttachReplaceName.getText())) {
                        txtAttachReplaceOrig.setVisible(true);
                        cbAttachReplaceOrig.setVisible(false);
                        rbAttachReplaceName.setSelected(true);
                        txtAttachReplaceOrig.setText(orig);
                    } else if (type.equals(rbAttachReplaceID.getText())) {
                        txtAttachReplaceOrig.setVisible(true);
                        cbAttachReplaceOrig.setVisible(false);
                        rbAttachReplaceID.setSelected(true);
                        txtAttachReplaceOrig.setText(orig);
                    } else {
                        txtAttachReplaceOrig.setVisible(false);
                        cbAttachReplaceOrig.setVisible(true);
                        rbAttachReplaceMime.setSelected(true);
                        cbAttachReplaceOrig.setSelectedItem(replace);
                    }

                    txtAttachReplaceName.setText(name);
                    txtAttachReplaceDesc.setText(desc);
                    cbAttachReplaceMime.setSelectedItem(mime);

                    tblAttachReplace.setEnabled(false);
                    btnAttachReplaceAdd.setEnabled(false);
                    btnAttachReplaceRemove.setEnabled(true);
                    btnAttachReplaceEdit.setEnabled(true);
                    btnAttachReplaceCancel.setEnabled(true);
                }
            }
        });

        btnAttachReplaceAdd.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String type = "";
                String orig = "";

                if (rbAttachReplaceName.isSelected()) {
                    type = rbAttachReplaceName.getText();
                    orig = txtAttachReplaceOrig.getText().trim();
                } else if (rbAttachReplaceID.isSelected()) {
                    type = rbAttachReplaceID.getText();
                    orig = txtAttachReplaceOrig.getText();
                } else {
                    type = rbAttachReplaceMime.getText();
                    orig = cbAttachReplaceOrig.getSelectedItem().toString();
                }

                if (orig.isEmpty() || txtAttachReplaceNew.getText().isEmpty()) {
                    JOptionPane.showMessageDialog(null,
                            "The original value and replacement are mandatory for the attachment!", "",
                            JOptionPane.ERROR_MESSAGE);

                    return;
                }

                String[] rowData = { type, orig, txtAttachReplaceNew.getText(), txtAttachReplaceName.getText().trim(),
                        txtAttachReplaceDesc.getText().trim(), cbAttachReplaceMime.getSelectedItem().toString() };

                modelAttachmentsReplace.addRow(rowData);

                Utils.adjustColumnPreferredWidths(tblAttachReplace);
                tblAttachReplace.revalidate();

                txtAttachReplaceOrig.setText("");
                txtAttachReplaceNew.setText("");
                txtAttachReplaceName.setText("");
                txtAttachReplaceDesc.setText("");
                cbAttachReplaceMime.setSelectedIndex(0);
                rbAttachReplaceName.setSelected(true);
                txtAttachReplaceOrig.setVisible(true);
                cbAttachReplaceOrig.setVisible(false);
            }
        });

        btnAttachReplaceEdit.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String type = "";
                String orig = "";

                if (rbAttachReplaceName.isSelected()) {
                    type = rbAttachReplaceName.getText();
                    orig = txtAttachReplaceOrig.getText().trim();
                } else if (rbAttachReplaceID.isSelected()) {
                    type = rbAttachReplaceID.getText();
                    orig = txtAttachReplaceOrig.getText();
                } else {
                    type = rbAttachReplaceMime.getText();
                    orig = cbAttachReplaceOrig.getSelectedItem().toString();
                }

                int selection = tblAttachReplace.getSelectedRow();

                if (orig.isEmpty() || txtAttachReplaceNew.getText().isEmpty()) {
                    JOptionPane.showMessageDialog(null,
                            "The original value and replacement are mandatory for the attachment!", "",
                            JOptionPane.ERROR_MESSAGE);

                    return;
                }

                modelAttachmentsReplace.setValueAt(type, selection, 0);
                modelAttachmentsReplace.setValueAt(orig, selection, 1);
                modelAttachmentsReplace.setValueAt(txtAttachReplaceNew.getText(), selection, 2);
                modelAttachmentsReplace.setValueAt(txtAttachReplaceName.getText(), selection, 3);
                modelAttachmentsReplace.setValueAt(txtAttachReplaceDesc.getText(), selection, 4);
                modelAttachmentsReplace.setValueAt(cbAttachReplaceMime.getSelectedItem().toString(), selection, 5);

                Utils.adjustColumnPreferredWidths(tblAttachReplace);
                tblAttachReplace.revalidate();

                tblAttachReplace.setEnabled(true);
                btnAttachReplaceAdd.setEnabled(true);
                btnAttachReplaceEdit.setEnabled(false);
                btnAttachReplaceRemove.setEnabled(false);
                btnAttachReplaceCancel.setEnabled(false);
                tblAttachReplace.clearSelection();

                txtAttachReplaceOrig.setText("");
                txtAttachReplaceNew.setText("");
                txtAttachReplaceName.setText("");
                txtAttachReplaceDesc.setText("");
                cbAttachReplaceMime.setSelectedIndex(0);
                rbAttachReplaceName.setSelected(true);
                txtAttachReplaceOrig.setVisible(true);
                cbAttachReplaceOrig.setVisible(false);
            }
        });

        btnAttachReplaceRemove.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int selection = tblAttachReplace.getSelectedRow();

                modelAttachmentsReplace.removeRow(selection);

                tblAttachReplace.setEnabled(true);
                btnAttachReplaceAdd.setEnabled(true);
                btnAttachReplaceEdit.setEnabled(false);
                btnAttachReplaceRemove.setEnabled(false);
                btnAttachReplaceCancel.setEnabled(false);
                tblAttachReplace.clearSelection();

                txtAttachReplaceOrig.setText("");
                txtAttachReplaceNew.setText("");
                txtAttachReplaceName.setText("");
                txtAttachReplaceDesc.setText("");
                cbAttachReplaceMime.setSelectedIndex(0);
                rbAttachReplaceName.setSelected(true);
                txtAttachReplaceOrig.setVisible(true);
                cbAttachReplaceOrig.setVisible(false);
            }
        });

        btnAttachReplaceCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                tblAttachReplace.setEnabled(true);
                btnAttachReplaceAdd.setEnabled(true);
                btnAttachReplaceEdit.setEnabled(false);
                btnAttachReplaceRemove.setEnabled(false);
                btnAttachReplaceCancel.setEnabled(false);
                tblAttachReplace.clearSelection();

                txtAttachReplaceOrig.setText("");
                txtAttachReplaceNew.setText("");
                txtAttachReplaceName.setText("");
                txtAttachReplaceDesc.setText("");
                cbAttachReplaceMime.setSelectedIndex(0);
                rbAttachReplaceName.setSelected(true);
                txtAttachReplaceOrig.setVisible(true);
                cbAttachReplaceOrig.setVisible(false);
            }
        });

        tblAttachDelete.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (modelAttachmentsDelete.getRowCount() == 0 || !tblAttachDelete.isEnabled()) {
                    return;
                }

                int selection = tblAttachDelete.getSelectedRow();

                if (selection != -1) {
                    String type = modelAttachmentsDelete.getValueAt(selection, 0).toString();
                    String value = modelAttachmentsDelete.getValueAt(selection, 1).toString();

                    if (type.equals(rbAttachDeleteName.getText())) {
                        rbAttachDeleteName.setSelected(true);
                        cbAttachDeleteValue.setVisible(false);
                        txtAttachDeleteValue.setVisible(true);
                        txtAttachDeleteValue.setText(value);
                    } else if (type.equals(rbAttachDeleteID.getText())) {
                        rbAttachDeleteID.setSelected(true);
                        cbAttachDeleteValue.setVisible(false);
                        txtAttachDeleteValue.setVisible(true);
                        txtAttachDeleteValue.setText(value);
                    } else {
                        rbAttachDeleteMime.setSelected(true);
                        txtAttachDeleteValue.setVisible(false);
                        cbAttachDeleteValue.setVisible(true);
                        cbAttachDeleteValue.setSelectedItem(value);
                    }

                    tblAttachDelete.setEnabled(false);
                    btnAttachDeleteAdd.setEnabled(false);
                    btnAttachDeleteEdit.setEnabled(true);
                    btnAttachDeleteRemove.setEnabled(true);
                    btnAttachDeleteCancel.setEnabled(true);
                }
            }
        });

        rbAttachDeleteName.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                cbAttachDeleteValue.setVisible(false);
                txtAttachDeleteValue.setVisible(true);
                txtAttachDeleteValue.setText("");
            }
        });

        rbAttachDeleteID.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                cbAttachDeleteValue.setVisible(false);
                txtAttachDeleteValue.setVisible(true);
                txtAttachDeleteValue.setText("1");
            }
        });

        rbAttachDeleteMime.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                txtAttachDeleteValue.setVisible(false);
                cbAttachDeleteValue.setVisible(true);
                cbAttachDeleteValue.setSelectedIndex(0);
            }
        });

        txtAttachDeleteValue.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                if (!rbAttachDeleteID.isSelected()) {
                    return;
                }

                try {
                    int id = Integer.parseInt(txtAttachDeleteValue.getText());

                    if (id < 1) {
                        txtAttachDeleteValue.setText("1");
                    }
                } catch (NumberFormatException e1) {
                    txtAttachDeleteValue.setText("1");
                }
            }
        });

        btnAttachDeleteAdd.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String type = "";
                String value = "";

                if (rbAttachDeleteName.isSelected()) {
                    type = rbAttachDeleteName.getText();
                    value = txtAttachDeleteValue.getText().trim();
                } else if (rbAttachDeleteID.isSelected()) {
                    type = rbAttachDeleteID.getText();
                    value = txtAttachDeleteValue.getText();
                } else {
                    type = rbAttachDeleteMime.getText();
                    value = cbAttachDeleteValue.getSelectedItem().toString();
                }

                if (value.isEmpty()) {
                    JOptionPane.showMessageDialog(null, "The value is mandatory for the attachment!", "",
                            JOptionPane.ERROR_MESSAGE);

                    return;
                }

                String[] rowData = { type, value };

                modelAttachmentsDelete.addRow(rowData);

                Utils.adjustColumnPreferredWidths(tblAttachDelete);
                tblAttachDelete.revalidate();

                rbAttachDeleteName.setSelected(true);
                cbAttachDeleteValue.setVisible(false);
                txtAttachDeleteValue.setVisible(true);
                txtAttachDeleteValue.setText("");
                tblAttachDelete.clearSelection();
            }
        });

        btnAttachDeleteEdit.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String type = "";
                String value = "";

                if (rbAttachDeleteName.isSelected()) {
                    type = rbAttachDeleteName.getText();
                    value = txtAttachDeleteValue.getText().trim();
                } else if (rbAttachDeleteID.isSelected()) {
                    type = rbAttachDeleteID.getText();
                    value = txtAttachDeleteValue.getText();
                } else {
                    type = rbAttachDeleteMime.getText();
                    value = cbAttachDeleteValue.getSelectedItem().toString();
                }

                int selection = tblAttachDelete.getSelectedRow();

                if (value.isEmpty()) {
                    JOptionPane.showMessageDialog(null, "The value is mandatory for the attachment!", "",
                            JOptionPane.ERROR_MESSAGE);

                    return;
                }

                modelAttachmentsDelete.setValueAt(type, selection, 0);
                modelAttachmentsDelete.setValueAt(value, selection, 1);

                Utils.adjustColumnPreferredWidths(tblAttachDelete);
                tblAttachDelete.revalidate();

                tblAttachDelete.setEnabled(true);
                btnAttachDeleteAdd.setEnabled(true);
                btnAttachDeleteEdit.setEnabled(false);
                btnAttachDeleteRemove.setEnabled(false);
                btnAttachDeleteCancel.setEnabled(false);
                tblAttachDelete.clearSelection();

                rbAttachDeleteName.setSelected(true);
                cbAttachDeleteValue.setVisible(false);
                txtAttachDeleteValue.setVisible(true);
                txtAttachDeleteValue.setText("");
                tblAttachDelete.clearSelection();
            }
        });

        btnAttachDeleteRemove.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int selection = tblAttachDelete.getSelectedRow();

                modelAttachmentsDelete.removeRow(selection);

                tblAttachDelete.setEnabled(true);
                btnAttachDeleteAdd.setEnabled(true);
                btnAttachDeleteEdit.setEnabled(false);
                btnAttachDeleteRemove.setEnabled(false);
                btnAttachDeleteCancel.setEnabled(false);
                tblAttachDelete.clearSelection();

                rbAttachDeleteName.setSelected(true);
                cbAttachDeleteValue.setVisible(false);
                txtAttachDeleteValue.setVisible(true);
                txtAttachDeleteValue.setText("");
            }
        });

        btnAttachDeleteCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                tblAttachDelete.setEnabled(true);
                btnAttachDeleteAdd.setEnabled(true);
                btnAttachDeleteEdit.setEnabled(false);
                btnAttachDeleteRemove.setEnabled(false);
                btnAttachDeleteCancel.setEnabled(false);
                tblAttachDelete.clearSelection();

                rbAttachDeleteName.setSelected(true);
                cbAttachDeleteValue.setVisible(false);
                txtAttachDeleteValue.setVisible(true);
                txtAttachDeleteValue.setText("");
            }
        });

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

                        if (cmdLineBatch.size() > 0) {
                            for (int i = 0; i < modelFiles.size(); i++) {
                                txtOutput.append(cmdLineBatch.get(i) + "\n");
                            }

                            pnlTabs.setSelectedIndex(pnlTabs.getTabCount() - 1);
                        }
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

    private void setCmdLineAttachmentsAdd() {
        cmdLineAttachmentsAdd = "";
        cmdLineAttachmentsAddOpt = "";

        for (int i = 0; i < modelAttachmentsAdd.getRowCount(); i++) {
            String file = modelAttachmentsAdd.getValueAt(i, 0).toString();
            String name = modelAttachmentsAdd.getValueAt(i, 1).toString();
            String desc = modelAttachmentsAdd.getValueAt(i, 2).toString();
            String mime = modelAttachmentsAdd.getValueAt(i, 3).toString();

            if (!name.isEmpty() || !desc.isEmpty() || !mime.isEmpty()) {
                if (!name.isEmpty()) {
                    cmdLineAttachmentsAdd += " --attachment-name \"" + name + "\"";
                    cmdLineAttachmentsAddOpt += " --attachment-name \"" + Utils.escapeName(name) + "\"";
                }

                if (!desc.isEmpty()) {
                    cmdLineAttachmentsAdd += " --attachment-description \"" + desc + "\"";
                    cmdLineAttachmentsAddOpt += " --attachment-description \"" + Utils.escapeName(desc) + "\"";
                }

                if (!mime.isEmpty()) {
                    cmdLineAttachmentsAdd += " --attachment-mime-type \"" + mime + "\"";
                    cmdLineAttachmentsAddOpt += " --attachment-mime-type \"" + Utils.escapeName(mime) + "\"";
                }
            }

            cmdLineAttachmentsAdd += " --add-attachment \"" + file + "\"";
            cmdLineAttachmentsAddOpt += " --add-attachment \"" + Utils.escapeName(file) + "\"";
        }
    }

    private void setCmdLineAttachmentsReplace() {
        cmdLineAttachmentsReplace = "";
        cmdLineAttachmentsReplaceOpt = "";

        for (int i = 0; i < modelAttachmentsReplace.getRowCount(); i++) {
            String type = modelAttachmentsReplace.getValueAt(i, 0).toString();
            String orig = modelAttachmentsReplace.getValueAt(i, 1).toString();
            String replace = modelAttachmentsReplace.getValueAt(i, 2).toString();
            String name = modelAttachmentsReplace.getValueAt(i, 3).toString();
            String desc = modelAttachmentsReplace.getValueAt(i, 4).toString();
            String mime = modelAttachmentsReplace.getValueAt(i, 5).toString();

            if (!name.isEmpty() || !desc.isEmpty() || !mime.isEmpty()) {
                if (!name.isEmpty()) {
                    cmdLineAttachmentsReplace += " --attachment-name \"" + name + "\"";
                    cmdLineAttachmentsReplaceOpt += " --attachment-name \"" + Utils.escapeName(name) + "\"";
                }

                if (!desc.isEmpty()) {
                    cmdLineAttachmentsReplace += " --attachment-description \"" + desc + "\"";
                    cmdLineAttachmentsReplaceOpt += " --attachment-description \"" + Utils.escapeName(desc) + "\"";
                }

                if (!mime.isEmpty()) {
                    cmdLineAttachmentsReplace += " --attachment-mime-type \"" + mime + "\"";
                    cmdLineAttachmentsReplaceOpt += " --attachment-mime-type \"" + Utils.escapeName(mime) + "\"";
                }

            }

            if (type.equals(rbAttachReplaceName.getText())) {
                cmdLineAttachmentsReplace += " --replace-attachment \"name:" + orig + ":" + replace + "\"";
                cmdLineAttachmentsReplaceOpt += " --replace-attachment \"name:" + Utils.escapeName(orig) + ":"
                        + Utils.escapeName(replace) + "\"";
            } else if (type.equals(rbAttachReplaceID.getText())) {
                cmdLineAttachmentsReplace += " --replace-attachment \"" + orig + ":" + replace + "\"";
                cmdLineAttachmentsReplaceOpt += " --replace-attachment \"" + orig + ":" + Utils.escapeName(replace)
                        + "\"";
            } else {
                cmdLineAttachmentsReplace += " --replace-attachment \"mime-type:" + orig + ":" + replace + "\"";
                cmdLineAttachmentsReplaceOpt += " --replace-attachment \"mime-type:" + Utils.escapeName(orig) + ":"
                        + Utils.escapeName(replace) + "\"";
            }
        }
    }

    private void setCmdLineAttachmentsDelete() {
        cmdLineAttachmentsDelete = "";
        cmdLineAttachmentsDeleteOpt = "";

        for (int i = 0; i < modelAttachmentsDelete.getRowCount(); i++) {
            String type = modelAttachmentsDelete.getValueAt(i, 0).toString();
            String value = modelAttachmentsDelete.getValueAt(i, 1).toString();

            if (type.equals(rbAttachDeleteName.getText())) {
                cmdLineAttachmentsDelete += " --delete-attachment \"name:" + value + "\"";
                cmdLineAttachmentsDeleteOpt += " --delete-attachment \"name:" + Utils.escapeName(value) + "\"";
            } else if (type.equals(rbAttachDeleteID.getText())) {
                cmdLineAttachmentsDelete += " --delete-attachment \"" + value + "\"";
                cmdLineAttachmentsDeleteOpt += " --delete-attachment \"" + value + "\"";
            } else {
                cmdLineAttachmentsDelete += " --delete-attachment \"mime-type:" + value + "\"";
                cmdLineAttachmentsDeleteOpt += " --delete-attachment \"mime-type:" + Utils.escapeName(value) + "\"";
            }
        }
    }

    private void setCmdLine() {
        setCmdLineGeneral();
        setCmdLineVideo();
        setCmdLineAudio();
        setCmdLineSubtitle();
        setCmdLineAttachmentsAdd();
        setCmdLineAttachmentsReplace();
        setCmdLineAttachmentsDelete();

        CommandBuilder.Attachments attachments = new CommandBuilder.Attachments(
                new CommandBuilder.AttachmentArgs(cmdLineAttachmentsDelete, cmdLineAttachmentsAdd,
                        cmdLineAttachmentsReplace),
                new CommandBuilder.AttachmentArgs(cmdLineAttachmentsDeleteOpt, cmdLineAttachmentsAddOpt,
                        cmdLineAttachmentsReplaceOpt));

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
        final JTextArea output = txtOutput;
        final List<String> batch = List.copyOf(cmdLineBatch);
        final List<String[]> batchOpt = List.copyOf(cmdLineBatchOpt);
        final List<String> fileNames = new ArrayList<>();
        for (int i = 0; i < modelFiles.getSize(); i++) {
            fileNames.add(modelFiles.get(i));
        }
        final String exePath = txtMkvPropExe.getText();

        worker = new SwingWorker<Void, Void>() {
            @Override
            public Void doInBackground() {
                for (int i = 0; i < batch.size(); i++) {
                    Process proc = null;

                    try {
                        File optFile = new File("options.json");
                        try (PrintWriter optFilePW = new PrintWriter(optFile, "UTF-8")) {
                            optFilePW.print(optionsJson(batchOpt.get(i)));
                        }

                        ProcessBuilder pb = new ProcessBuilder(exePath, "@options.json");
                        pb.redirectErrorStream(true);

                        appendOutput("File: " + fileNames.get(i) + "\n");
                        appendOutput("Command line: " + batch.get(i) + "\n\n");

                        proc = pb.start();

                        StreamGobbler outputGobbler = new StreamGobbler(proc.getInputStream(), output);
                        outputGobbler.start();

                        proc.waitFor();
                        // The reader stops at EOF; joining it guarantees the
                        // whole process output reaches the log before the next
                        // separator (replaces the old fixed sleep).
                        outputGobbler.join();

                        optFile.delete();

                        if (i < batch.size() - 1) {
                            appendOutput("--------------\n\n");
                        }
                    } catch (IOException e) {
                        appendOutput("Error: " + e + "\n");
                    } catch (InterruptedException e) {
                        if (proc != null) {
                            proc.destroy();
                        }
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

    private void parseFiles(String[] argsArray) {
        if (argsArray.length > 0) {
            File file = null;

            for (String arg : argsArray) {
                try {
                    file = new File(arg);

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
        ProcessBuilder pb = new ProcessBuilder(exe);
        pb.redirectErrorStream(true);

        try {
            Process proc = pb.start();

            // Drain the merged output as explicit UTF-8 before waiting: reading
            // until EOF proves the child has finished and can never block it on
            // a full pipe (the old waitFor-before-read order could deadlock).
            // No SwingWorker here: the probe never touches Swing, so there is
            // nothing to marshal — and an isDone busy-wait would only spin the
            // EDT.
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                while (in.readLine() != null) {
                    // discard the probe output
                }
            }

            proc.waitFor();
            return true;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /* End of command line methods */

    /* Start of INI configuration file methods */

    private void readIniFile() {
        Ini ini = null;

        if (iniFile.exists()) {
            try {
                ini = new Ini(iniFile);
                String exePath = ini.get("General", "mkvpropedit");

                if (exePath != null) {
                    if (exePath.equals("mkvpropedit")) {
                        chbMkvPropExeDef.setSelected(true);
                        chbMkvPropExeDef.setEnabled(false);
                    } else {
                        txtMkvPropExe.setText(exePath);
                        chbMkvPropExeDef.setSelected(false);
                        chbMkvPropExeDef.setEnabled(true);
                    }
                }
            } catch (InvalidFileFormatException e) {
                appendOutput("Error: malformed " + iniFile.getName() + ": " + e + "\n");
            } catch (IOException e) {
                appendOutput("Error: could not read " + iniFile.getName() + ": " + e + "\n");
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
        Ini ini = null;

        txtMkvPropExe.setText(exeFile.toString());
        chbMkvPropExeDef.setSelected(false);
        chbMkvPropExeDef.setEnabled(true);

        try {
            if (!iniFile.exists()) {
                iniFile.createNewFile();
            }

            ini = new Ini(iniFile);
            ini.put("General", "mkvpropedit", exeFile.toString());
            ini.store();
        } catch (InvalidFileFormatException e1) {
            appendOutput("Error: malformed " + iniFile.getName() + ": " + e1 + "\n");
        } catch (IOException e1) {
            appendOutput("Error: could not save " + iniFile.getName() + ": " + e1 + "\n");
        }
    }

    private void defaultIniFile() {
        Ini ini = null;

        try {
            if (!iniFile.exists()) {
                iniFile.createNewFile();
            }

            ini = new Ini(iniFile);

            ini.put("General", "mkvpropedit", "mkvpropedit");

            ini.store();
        } catch (InvalidFileFormatException e1) {
            appendOutput("Error: malformed " + iniFile.getName() + ": " + e1 + "\n");
        } catch (IOException e1) {
            appendOutput("Error: could not save " + iniFile.getName() + ": " + e1 + "\n");
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

    /* Start of table methods */

    private void resizeColumns(JTable table, double[] colSizes) {
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
            // Set minimum size for column
            columnModel.getColumn(i).setMinWidth(colWidths[i]);

            // Set preferred size for column
            columnModel.getColumn(i).setPreferredWidth(colWidths[i]);
        }

        table.revalidate();
    }

    /* End of table methods */

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

    /**
     * Items for the attachment MIME combos (add / replace orig / replace mime /
     * delete value).
     *
     * <p>
     * Returns a fresh array so the shared {@link MkvStrings} resource list is
     * never mutated (the old {@code remove(0)} call dropped the first element
     * from every later combo). Skips the corrupted {@code _} artifact in
     * {@code mimetypes.txt} and keeps a leading empty item: add/replace treat
     * an empty MIME as "omit --attachment-mime-type" (auto-detect), while
     * replace-orig/delete reject empty values in their action listeners.
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

    private void addFile(File file, boolean checkExtension) {
        try {
            if (!modelFiles.contains(file.getCanonicalPath()) && !checkExtension) {
                modelFiles.add(modelFiles.getSize(), file.getCanonicalPath());
            } else if (!modelFiles.contains(file.getCanonicalPath()) && MATROSKA_EXT_FILTER.accept(file)) {
                modelFiles.add(modelFiles.getSize(), file.getCanonicalPath());
            }
        } catch (IOException e) {
            appendOutput("Error: could not resolve " + file + ": " + e + "\n");
        }
    }

    /**
     * Folder-scan mask for Matroska files, case-insensitive: mkv, mka, mk3d,
     * webm, mks (replaces the commons-io {@code WildcardFileFilter}).
     *
     * @param path file to test; only the file name is matched
     * @return true when the file name ends with a Matroska extension
     */
    static boolean isMatroskaFile(final Path path) {
        final Path name = path.getFileName();

        if (name == null) {
            return false;
        }

        // Lowercase both sides: the glob is fixed lowercase, and normalizing
        // the name makes *.mkv match *.MKV regardless of the filesystem's
        // own case sensitivity.
        return MATROSKA_FILE_FILTER.matches(
                Path.of(name.toString().toLowerCase(Locale.ROOT)));
    }

    private void addMkvFilesFromFolder(final File folder) {
        Runnable tmpWorker = new Runnable() {
            @Override
            public void run() {
                // Recursive scan, same as FileUtils.iterateFiles with
                // TrueFileFilter dir filter; mask applies to file names only.
                try (Stream<Path> walk = Files.walk(folder.toPath())) {
                    walk.filter(Files::isRegularFile)
                            .filter(JMkvpropedit::isMatroskaFile)
                            .forEach(path -> addFile(path.toFile(), false));
                } catch (IOException | UncheckedIOException e) {
                    appendOutput("Error: could not scan " + folder + ": " + e + "\n");
                }
            }
        };

        SwingUtilities.invokeLater(tmpWorker);
    }

    /* End of file methods */

}
