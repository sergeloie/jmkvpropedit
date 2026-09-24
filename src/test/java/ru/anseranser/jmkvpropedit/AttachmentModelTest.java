package ru.anseranser.jmkvpropedit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Unit tests for the attachment table model and its operation descriptors —
 * issue #13.
 *
 * <p>
 * The three attachment operations (add / replace / delete) used to carry
 * triplicated column constants, table models and validation messages inside
 * {@link JMkvpropedit}. They now live once in {@link AttachmentOperation} and
 * {@link AttachmentModel}; these tests pin the extracted values to the former
 * UI so the refactor cannot drift.
 * </p>
 */
class AttachmentModelTest {

    @ParameterizedTest
    @EnumSource(AttachmentOperation.class)
    void modelColumnsComeFromTheOperation(AttachmentOperation operation) {
        AttachmentModel model = new AttachmentModel(operation);

        assertEquals(operation, model.operation());
        assertEquals(operation.columns().length, model.getColumnCount());
        for (int i = 0; i < operation.columns().length; i++) {
            assertEquals(operation.columns()[i], model.getColumnName(i));
        }
    }

    @Test
    void tabTitlesAndMessagesMatchTheFormerUi() {
        assertEquals("Add Attachments", AttachmentOperation.ADD.tabTitle());
        assertEquals("Replace Attachments", AttachmentOperation.REPLACE.tabTitle());
        assertEquals("Delete Attachments", AttachmentOperation.DELETE.tabTitle());

        assertEquals("The file is mandatory for the attachment!",
                AttachmentOperation.ADD.mandatoryMessage());
        assertEquals("The original value and replacement are mandatory for the attachment!",
                AttachmentOperation.REPLACE.mandatoryMessage());
        assertEquals("The value is mandatory for the attachment!",
                AttachmentOperation.DELETE.mandatoryMessage());
    }

    @Test
    void columnNamesAndWidthsMatchTheFormerTables() {
        assertArrayEquals(new String[] { "File", "Name", "Description", "MIME Type" },
                AttachmentOperation.ADD.columns());
        assertArrayEquals(new double[] { 0.35, 0.20, 0.25, 0.20 },
                AttachmentOperation.ADD.columnSizes());

        assertArrayEquals(new String[] { "Type", "Original Value", "Replacement", "Name", "Description",
                "MIME Type" },
                AttachmentOperation.REPLACE.columns());
        assertArrayEquals(new double[] { 0.15, 0.15, 0.20, 0.20, 0.15, 0.15 },
                AttachmentOperation.REPLACE.columnSizes());

        assertArrayEquals(new String[] { "Type", "Value" },
                AttachmentOperation.DELETE.columns());
        assertArrayEquals(new double[] { 0.40, 0.60 },
                AttachmentOperation.DELETE.columnSizes());
    }

    @ParameterizedTest
    @EnumSource(AttachmentOperation.class)
    void cellsAreNeverEditable(AttachmentOperation operation) {
        AttachmentModel model = new AttachmentModel(operation);
        model.addRow(new String[operation.columns().length]);

        assertFalse(model.isCellEditable(0, 0));
        assertFalse(model.isCellEditable(0, operation.columns().length - 1));
    }

    @Test
    void rowsSnapshotIsIndependentOfLaterEdits() {
        AttachmentModel model = new AttachmentModel(AttachmentOperation.ADD);
        model.addRow(new String[] { "f", "n", "d", "m" });

        List<String[]> rows = model.rows();
        model.addRow(new String[] { "f2", "n2", "d2", "m2" });
        model.setValueAt("changed", 0, 1);

        assertEquals(1, rows.size());
        assertArrayEquals(new String[] { "f", "n", "d", "m" }, rows.get(0));
        assertEquals("f", model.valueAt(0, 0));
    }

    @Test
    void selectorLabelsMatchTheFormerRadioCaptions() {
        assertEquals("Attachment name", AttachmentSelector.NAME.label());
        assertEquals("Attachment ID", AttachmentSelector.ID.label());
        assertEquals("Attachment(s) MIME Type", AttachmentSelector.MIME_TYPE.label());
    }

    @Test
    void selectorFromLabelFallsBackToMimeLikeTheFormerElseBranch() {
        assertEquals(AttachmentSelector.NAME, AttachmentSelector.fromLabel("Attachment name"));
        assertEquals(AttachmentSelector.ID, AttachmentSelector.fromLabel("Attachment ID"));
        assertEquals(AttachmentSelector.MIME_TYPE,
                AttachmentSelector.fromLabel("Attachment(s) MIME Type"));
        // The original command builders treated every unknown type as mime-type.
        assertEquals(AttachmentSelector.MIME_TYPE, AttachmentSelector.fromLabel("something else"));
    }
}
