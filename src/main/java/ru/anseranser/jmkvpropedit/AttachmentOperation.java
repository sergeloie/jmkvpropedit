package ru.anseranser.jmkvpropedit;

/**
 * The three attachment operations the unified {@link AttachmentPanel} serves —
 * issue #13.
 *
 * <p>
 * The original god class kept three near-identical copies of the attachment
 * UI (add, replace, delete), each with its own column constants, table model
 * and mandatory-field message. Everything that actually differs between them
 * is collected here; the table model and everything else lives once in
 * {@link AttachmentModel}/{@link AttachmentPanel}.
 * </p>
 */
public enum AttachmentOperation {

    /** Add attachments: file + name/description/MIME columns. */
    ADD("Add Attachments",
            new String[] { "File", "Name", "Description", "MIME Type" },
            new double[] { 0.35, 0.20, 0.25, 0.20 },
            "The file is mandatory for the attachment!"),

    /** Replace attachments: selector + original/replacement + name/MIME columns. */
    REPLACE("Replace Attachments",
            new String[] { "Type", "Original Value", "Replacement", "Name", "Description", "MIME Type" },
            new double[] { 0.15, 0.15, 0.20, 0.20, 0.15, 0.15 },
            "The original value and replacement are mandatory for the attachment!"),

    /** Delete attachments: selector + value columns. */
    DELETE("Delete Attachments",
            new String[] { "Type", "Value" },
            new double[] { 0.40, 0.60 },
            "The value is mandatory for the attachment!");

    private final String tabTitle;
    private final String[] columns;
    private final double[] columnSizes;
    private final String mandatoryMessage;

    AttachmentOperation(String tabTitle, String[] columns, double[] columnSizes, String mandatoryMessage) {
        this.tabTitle = tabTitle;
        this.columns = columns;
        this.columnSizes = columnSizes;
        this.mandatoryMessage = mandatoryMessage;
    }

    /** The sub-tab title in the attachments tab, e.g. {@code Add Attachments}. */
    public String tabTitle() {
        return tabTitle;
    }

    /** The table column captions, as in the original UI. */
    public String[] columns() {
        return columns.clone();
    }

    /** The relative column widths used when the window is resized. */
    public double[] columnSizes() {
        return columnSizes.clone();
    }

    /** The mandatory-field error shown when an Add/Edit check fails. */
    public String mandatoryMessage() {
        return mandatoryMessage;
    }
}
