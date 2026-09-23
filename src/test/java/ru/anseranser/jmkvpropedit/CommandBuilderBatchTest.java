package ru.anseranser.jmkvpropedit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the batch assembly — issue #11: one command line per file,
 * plain-line quoting per platform, the canonical section order, and the Opt
 * argument lists (the {@code List<String[]>} fed to options.json).
 */
class CommandBuilderBatchTest {

    @Test
    void plainLineQuotesExeAndFileOnWindows() {
        CommandBuilder.Batch batch = new CommandBuilder(true).buildBatch("mkvpropedit", List.of("first.mkv"),
                section(" --edit info", " --edit info"), blank(), blank(), blank(), none());

        assertEquals("\"mkvpropedit\" \"first.mkv\" --edit info", batch.lines().get(0));
    }

    @Test
    void plainLineEscapesExeAndFileQuotesOnUnix() {
        CommandBuilder.Batch batch = new CommandBuilder(false).buildBatch("mkv \"proppedit", List.of("a\"b.mkv"),
                section(" --edit info", " --edit info"), blank(), blank(), blank(), none());

        assertEquals("\"mkv \\\"proppedit\" \"a\\\"b.mkv\" --edit info", batch.lines().get(0));
    }

    @Test
    void optArgsStartWithTheFileAndExcludeTheExe() {
        CommandBuilder.Batch batch = new CommandBuilder(true).buildBatch("mkvpropedit", List.of("first.mkv"),
                section(" --edit info", " --edit info"), blank(), blank(), blank(), none());

        assertArrayEquals(new String[] { "first.mkv", "--edit", "info" }, batch.optArgs().get(0));
    }

    @Test
    void optArgsUnwindPlaceholderAndBackslashEncoding() {
        List<String> files = List.of("C:\\a\"b\\c.mkv");
        CommandBuilder.Section video = new CommandBuilder(true).buildTracks(files, 'v', List.of(named("x\"y")));

        CommandBuilder.Batch batch = new CommandBuilder(true).buildBatch("mkvpropedit", files,
                blank(), video, blank(), blank(), none());

        assertArrayEquals(
                new String[] { "C:\\a\"b\\c.mkv", "--edit", "track:v1", "--set", "name=x\"y" },
                batch.optArgs().get(0));
    }

    @Test
    void sectionsComposeInCanonicalOrder() {
        CommandBuilder.Section general = new CommandBuilder.Section(new String[] { " G" }, new String[] { " g" });
        CommandBuilder.Attachments attachments = new CommandBuilder.Attachments(
                new CommandBuilder.AttachmentArgs(" D", " A", " R"),
                new CommandBuilder.AttachmentArgs(" d", " a", " r"));
        CommandBuilder.Section video = new CommandBuilder.Section(new String[] { " V" }, new String[] { " v" });
        CommandBuilder.Section audio = new CommandBuilder.Section(new String[] { " U" }, new String[] { " u" });
        CommandBuilder.Section subtitle = new CommandBuilder.Section(new String[] { " S" }, new String[] { " s" });

        CommandBuilder.Batch batch = new CommandBuilder(true).buildBatch("mkvpropedit", List.of("first.mkv"),
                general, video, audio, subtitle, attachments);

        // canonical order: general, attachments(delete, add, replace), video, audio, subtitle
        assertEquals("\"mkvpropedit\" \"first.mkv\" G D A R V U S", batch.lines().get(0));
        assertArrayEquals(
                new String[] { "first.mkv", "g", "d", "a", "r", "v", "u", "s" },
                batch.optArgs().get(0));
    }

    @Test
    void oneCommandLinePerFile() {
        CommandBuilder.Section general = new CommandBuilder.Section(
                new String[] { " G0", " G1" }, new String[] { " g0", " g1" });

        CommandBuilder.Batch batch = new CommandBuilder(true).buildBatch("mkvpropedit",
                List.of("first.mkv", "second.mkv"), general, blank(), blank(), blank(), none());

        assertEquals(2, batch.lines().size());
        assertEquals(2, batch.optArgs().size());
        assertEquals("\"mkvpropedit\" \"first.mkv\" G0", batch.lines().get(0));
        assertEquals("\"mkvpropedit\" \"second.mkv\" G1", batch.lines().get(1));
    }

    @Test
    void nothingToDoProducesAnEmptyBatch() {
        CommandBuilder.Batch batch = new CommandBuilder(true).buildBatch("mkvpropedit", List.of("first.mkv"),
                blank(), blank(), blank(), blank(), none());

        assertTrue(batch.lines().isEmpty());
        assertTrue(batch.optArgs().isEmpty());
    }

    @Test
    void emptyFileListProducesAnEmptyBatch() {
        CommandBuilder.Batch batch = new CommandBuilder(true).buildBatch("mkvpropedit", List.of(),
                section(" --edit info", " --edit info"), blank(), blank(), blank(), none());

        assertTrue(batch.lines().isEmpty());
        assertTrue(batch.optArgs().isEmpty());
    }

    /* Fixtures */

    private static CommandBuilder.Section section(String plain, String opt) {
        return new CommandBuilder.Section(new String[] { plain }, new String[] { opt });
    }

    private static CommandBuilder.Section blank() {
        return section("", "");
    }

    private static CommandBuilder.Attachments none() {
        return new CommandBuilder.Attachments(
                new CommandBuilder.AttachmentArgs("", "", ""),
                new CommandBuilder.AttachmentArgs("", "", ""));
    }

    /** An edited track with only a name (same fixture as TrackCommandBuilderTest). */
    private static CommandBuilder.TrackSettings named(String name) {
        return new CommandBuilder.TrackSettings(true,
                false, true, false, true, false, true,
                true, name,
                false, null,
                false, "",
                false, "1", "1");
    }
}
