package ru.anseranser.jmkvpropedit;

import java.util.ArrayList;
import java.util.List;

import javax.swing.table.DefaultTableModel;

/**
 * The row model behind one attachment operation's table — issue #13.
 *
 * <p>
 * This replaces the triplicated {@code modelAttachmentsAdd}/
 * {@code modelAttachmentsReplace}/{@code modelAttachmentsDelete} fields and
 * their column constants that used to live in {@link JMkvpropedit}: one model
 * per {@link AttachmentOperation}, with the columns and the read-only cells
 * coming from the operation descriptor.
 * </p>
 */
public final class AttachmentModel extends DefaultTableModel {

    private static final long serialVersionUID = 1L;

    private final AttachmentOperation operation;

    /**
     * Builds an empty model with the operation's columns, cells never
     * editable — same as the original anonymous subclasses.
     *
     * @param operation which attachment operation this model serves
     */
    public AttachmentModel(AttachmentOperation operation) {
        super(null, operation.columns());
        this.operation = operation;
    }

    /** The operation whose columns this model carries. */
    public AttachmentOperation operation() {
        return operation;
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }

    /**
     * The cell value as a string — the rows only ever hold strings, and the
     * original code read them the same way ({@code getValueAt(...).toString()}).
     *
     * @param row    the row index
     * @param column the column index
     * @return the cell value
     */
    public String valueAt(int row, int column) {
        return getValueAt(row, column).toString();
    }

    /**
     * A snapshot of all rows as plain string arrays (one array per row, in
     * table order) for the command-line builders. Each row is a copy, so
     * later edits to the table cannot change an existing snapshot.
     *
     * @return the rows of the model
     */
    public List<String[]> rows() {
        List<String[]> rows = new ArrayList<>(getRowCount());

        for (int i = 0; i < getRowCount(); i++) {
            String[] row = new String[getColumnCount()];
            for (int c = 0; c < row.length; c++) {
                row[c] = valueAt(i, c);
            }
            rows.add(row);
        }

        return rows;
    }
}
