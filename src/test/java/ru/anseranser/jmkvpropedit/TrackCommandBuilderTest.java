package ru.anseranser.jmkvpropedit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for track (video/audio/subtitle) argument building — issue #11.
 *
 * <p>
 * All three track types share one code path (the selector differs), so these
 * tests cover video, audio and subtitle together: track numbers, the Opt
 * variant, per-file {@code {num}} numbering and name escaping.
 * </p>
 */
class TrackCommandBuilderTest {

    private static final List<String> FILES = List.of("first.mkv", "second.mkv");

    @Test
    void trackSelectorsAreVideoAudioAndSubtitle() {
        for (char selector : new char[] { 'v', 'a', 's' }) {
            CommandBuilder.Section section = new CommandBuilder().buildTracks(FILES, selector, List.of(named("x")));

            assertEquals(" --edit track:" + selector + "1 --set name=\"x\"", section.plain()[0],
                    "selector=" + selector);
            assertEquals(" --edit track:" + selector + "1 --set name=\"x\"", section.opt()[0],
                    "selector=" + selector);
        }
    }

    @Test
    void trackNumbersFollowTheSlotIndex() {
        CommandBuilder.Section section = new CommandBuilder().buildTracks(FILES, 'v',
                List.of(named("a"), named("b"), named("c")));

        String expected = " --edit track:v1 --set name=\"a\""
                + " --edit track:v2 --set name=\"b\""
                + " --edit track:v3 --set name=\"c\"";
        assertEquals(expected, section.plain()[0]);
        assertEquals(expected, section.plain()[1]);
    }

    @Test
    void plainVariantEscapesQuotesWhileOptUsesThePlaceholder() {
        CommandBuilder.Section section = new CommandBuilder().buildTracks(FILES, 'v', List.of(named("A\"B\\c")));

        assertEquals(" --edit track:v1 --set name=\"A\\\"B\\c\"", section.plain()[0]);
        assertEquals(" --edit track:v1 --set name=\"A####escaped__quotes#####B\\\\c\"", section.opt()[0]);
    }

    @Test
    void numberingSubstitutesNumPerFileWithPadding() {
        CommandBuilder.Section section = new CommandBuilder().buildTracks(FILES, 'v',
                List.of(numbered("E{num}", "7", "2")));

        assertEquals(" --edit track:v1 --set name=\"E07\"", section.plain()[0]);
        assertEquals(" --edit track:v1 --set name=\"E08\"", section.plain()[1]);
        assertEquals(" --edit track:v1 --set name=\"E07\"", section.opt()[0]);
        assertEquals(" --edit track:v1 --set name=\"E08\"", section.opt()[1]);
    }

    @Test
    void numPlaceholderStaysLiteralWhenNumberingIsOff() {
        CommandBuilder.Section section = new CommandBuilder().buildTracks(FILES, 'v', List.of(named("E{num}")));

        assertEquals(" --edit track:v1 --set name=\"E{num}\"", section.plain()[0]);
        assertEquals(" --edit track:v1 --set name=\"E{num}\"", section.plain()[1]);
    }

    /**
     * Preserved quirk of the original editCount: it accumulates across tracks,
     * so a bare {@code --edit} track before any property track is dropped...
     */
    @Test
    void bareEditTrackBeforeAnyPropertyIsDropped() {
        CommandBuilder.Section section = new CommandBuilder().buildTracks(FILES, 'v',
                List.of(editOnly(), named("x")));

        assertEquals(" --edit track:v2 --set name=\"x\"", section.plain()[0]);
    }

    /** ...but after a property track it survives, and stays in the command. */
    @Test
    void bareEditTrackAfterAPropertyTrackIsKept() {
        CommandBuilder.Section section = new CommandBuilder().buildTracks(FILES, 'v',
                List.of(named("x"), editOnly()));

        assertEquals(" --edit track:v1 --set name=\"x\" --edit track:v2", section.plain()[0]);
    }

    @Test
    void uneditedTrackIsSkippedEvenWithSettings() {
        CommandBuilder.TrackSettings track = new CommandBuilder.TrackSettings(false,
                false, true, false, true, false, true,
                true, "x",
                true, "eng",
                true, "--extra",
                true, "1", "1");

        CommandBuilder.Section section = new CommandBuilder().buildTracks(FILES, 'v', List.of(track));

        assertArrayEquals(new String[] { "", "" }, section.plain());
        assertArrayEquals(new String[] { "", "" }, section.opt());
    }

    @Test
    void flagsMapToEnabledDefaultForcedBits() {
        CommandBuilder.TrackSettings track = new CommandBuilder.TrackSettings(true,
                true, true, true, false, true, true,
                false, "",
                false, null,
                false, "",
                false, "1", "1");

        CommandBuilder.Section section = new CommandBuilder().buildTracks(FILES, 'v', List.of(track));

        assertEquals(" --edit track:v1 --set flag-enabled=1 --set flag-default=0 --set flag-forced=1",
                section.plain()[0]);
        assertEquals(section.plain()[0], section.opt()[0]);
    }

    @Test
    void languageAppliesToBothVariants() {
        CommandBuilder.TrackSettings track = new CommandBuilder.TrackSettings(true,
                false, true, false, true, false, true,
                false, "",
                true, "eng",
                false, "",
                false, "1", "1");

        CommandBuilder.Section section = new CommandBuilder().buildTracks(FILES, 'v', List.of(track));

        assertEquals(" --edit track:v1 --set language=\"eng\"", section.plain()[0]);
        assertEquals(" --edit track:v1 --set language=\"eng\"", section.opt()[0]);
    }

    @Test
    void extraCommandEscapesBackslashesOnlyInTheOptVariant() {
        CommandBuilder.TrackSettings track = new CommandBuilder.TrackSettings(true,
                false, true, false, true, false, true,
                false, "",
                false, null,
                true, "--foo C:\\bar",
                false, "1", "1");

        CommandBuilder.Section section = new CommandBuilder().buildTracks(FILES, 'v', List.of(track));

        assertEquals(" --edit track:v1 --foo C:\\bar", section.plain()[0]);
        assertEquals(" --edit track:v1 --foo C:\\\\bar", section.opt()[0]);
    }

    @Test
    void blankExtraIsIgnoredAndLeavesNothingToEdit() {
        CommandBuilder.TrackSettings track = new CommandBuilder.TrackSettings(true,
                false, true, false, true, false, true,
                false, "",
                false, null,
                true, "   ",
                false, "1", "1");

        CommandBuilder.Section section = new CommandBuilder().buildTracks(FILES, 'v', List.of(track));

        assertArrayEquals(new String[] { "", "" }, section.plain());
        assertArrayEquals(new String[] { "", "" }, section.opt());
    }

    @Test
    void fileNamePlaceholderSubstitutesPerFile() {
        CommandBuilder.Section section = new CommandBuilder().buildTracks(FILES, 'v', List.of(named("E{file_name}")));

        assertEquals(" --edit track:v1 --set name=\"first\"", section.plain()[0]);
        assertEquals(" --edit track:v1 --set name=\"second\"", section.plain()[1]);
    }

    @Test
    void emptyFileListYieldsNoArguments() {
        CommandBuilder.Section section = new CommandBuilder().buildTracks(List.of(), 'v', List.of(named("x")));

        assertArrayEquals(new String[0], section.plain());
        assertArrayEquals(new String[0], section.opt());
    }

    /* Settings fixtures */

    /** An edited track with only a name. */
    private static CommandBuilder.TrackSettings named(String name) {
        return new CommandBuilder.TrackSettings(true,
                false, true, false, true, false, true,
                true, name,
                false, null,
                false, "",
                false, "1", "1");
    }

    /** An edited, numbered track with only a name. */
    private static CommandBuilder.TrackSettings numbered(String name, String start, String pad) {
        return new CommandBuilder.TrackSettings(true,
                false, true, false, true, false, true,
                true, name,
                false, null,
                false, "",
                true, start, pad);
    }

    /** An edited track without any property. */
    private static CommandBuilder.TrackSettings editOnly() {
        return new CommandBuilder.TrackSettings(true,
                false, true, false, true, false, true,
                false, "",
                false, null,
                false, "",
                false, "1", "1");
    }
}
