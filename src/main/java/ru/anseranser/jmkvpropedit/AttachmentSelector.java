package ru.anseranser.jmkvpropedit;

/**
 * How an attachment row is selected: by name, by attachment ID or by MIME
 * type — issue #13.
 *
 * <p>
 * Replace and delete rows store this as the string label of the radio button
 * that was selected (the original table's "Type" column). The command
 * builders compared those strings against the radio captions; they now go
 * through {@link #fromLabel}, whose fallback matches the original
 * {@code else} branch: anything that is not a name or an ID is treated as a
 * MIME type selector.
 * </p>
 */
public enum AttachmentSelector {

    /** Match by attachment name: {@code name:<value>}. */
    NAME("Attachment name"),

    /** Match by attachment ID: {@code <value>} with no prefix. */
    ID("Attachment ID"),

    /** Match all attachments of a MIME type: {@code mime-type:<value>}. */
    MIME_TYPE("Attachment(s) MIME Type");

    private final String label;

    AttachmentSelector(String label) {
        this.label = label;
    }

    /** The radio caption, also stored in the row's Type column. */
    public String label() {
        return label;
    }

    /**
     * Resolves a stored Type column value back to a selector. Unknown labels
     * fall back to {@link #MIME_TYPE}, exactly like the original command
     * builders' {@code else} branch.
     *
     * @param label the Type column value of a replace/delete row
     * @return the matching selector, never null
     */
    public static AttachmentSelector fromLabel(String label) {
        for (AttachmentSelector selector : values()) {
            if (selector.label.equals(label)) {
                return selector;
            }
        }

        return MIME_TYPE;
    }
}
