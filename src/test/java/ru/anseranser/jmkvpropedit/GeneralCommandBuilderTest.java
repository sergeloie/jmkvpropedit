package ru.anseranser.jmkvpropedit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the General-tab argument building — issue #11: tags/chapters
 * modes, title numbering across files, and plain-vs-Opt name escaping.
 */
class GeneralCommandBuilderTest {

    private static final List<String> FILES = List.of("first.mkv", "second.mkv");

    @Test
    void tagsRemoveAppendsBareAllSelector() {
        CommandBuilder.GeneralSettings settings = general(tags(0, ""), off(), titleOff(), false, "");
        CommandBuilder.Section section = new CommandBuilder().buildGeneral(FILES, settings);

        assertEquals(" --tags all:", section.plain()[0]);
        assertEquals(" --tags all:", section.opt()[0]);
        assertEquals(" --tags all:", section.plain()[1]);
    }

    @Test
    void tagsFromFileEscapesQuotesPerPlatform() {
        CommandBuilder.GeneralSettings settings = general(tags(1, "a\"b"), off(), titleOff(), false, "");

        CommandBuilder.Section windows = new CommandBuilder(true).buildGeneral(FILES, settings);
        CommandBuilder.Section unix = new CommandBuilder(false).buildGeneral(FILES, settings);

        assertEquals(" --tags all:\"a\"b\"", windows.plain()[0]);
        assertEquals(" --tags all:\"a\\\"b\"", unix.plain()[0]);
        assertEquals(" --tags all:\"a####escaped__quotes#####b\"", windows.opt()[0]);
        assertEquals(windows.opt()[0], unix.opt()[0]);
    }

    @Test
    void tagsFromFileWithBlankTextFallsBackToRemove() {
        CommandBuilder.GeneralSettings settings = general(tags(1, "   "), off(), titleOff(), false, "");
        CommandBuilder.Section section = new CommandBuilder().buildGeneral(FILES, settings);

        assertEquals(" --tags all:", section.plain()[0]);
        assertEquals(" --tags all:", section.opt()[0]);
    }

    @Test
    void tagsMatchSuffixBuildsPathWithoutExtension() {
        CommandBuilder.GeneralSettings settings = general(tags(2, "-meta"), off(), titleOff(), false, "");
        CommandBuilder.Section windows = new CommandBuilder(true)
                .buildGeneral(List.of("C:\\clips\\first.mkv"), settings);

        assertEquals(" --tags all:\"C:\\clips\\first-meta.xml\"", windows.plain()[0]);
        assertEquals(" --tags all:\"C:\\\\clips\\\\first-meta.xml\"", windows.opt()[0]);
    }

    @Test
    void chaptersRemoveUsesEmptyDoubleQuotesPlainAndSingleQuotesOpt() {
        CommandBuilder.GeneralSettings settings = general(off(), chapters(0, ""), titleOff(), false, "");
        CommandBuilder.Section section = new CommandBuilder().buildGeneral(FILES, settings);

        assertEquals(" --chapters \"\"", section.plain()[0]);
        assertEquals(" --chapters ''", section.opt()[0]);
    }

    @Test
    void chaptersFromFileEscapesLikeTags() {
        CommandBuilder.GeneralSettings settings = general(off(), chapters(1, "c\"d"), titleOff(), false, "");
        CommandBuilder.Section unix = new CommandBuilder(false).buildGeneral(FILES, settings);

        assertEquals(" --chapters \"c\\\"d\"", unix.plain()[0]);
        assertEquals(" --chapters \"c####escaped__quotes#####d\"", unix.opt()[0]);
    }

    @Test
    void titleNumberingIncrementsAcrossFilesAndSubstitutesFileName() {
        CommandBuilder.GeneralSettings settings = general(off(), off(),
                new CommandBuilder.TitleSetting(true, true, "Ep {num}: {file_name}", "1", "2"), false, "");
        CommandBuilder.Section section = new CommandBuilder().buildGeneral(FILES, settings);

        assertEquals(" --edit info --set title=\"Ep 01: first\"", section.plain()[0]);
        assertEquals(" --edit info --set title=\"Ep 02: second\"", section.plain()[1]);
        assertEquals(section.plain()[0], section.opt()[0]);
        assertEquals(section.plain()[1], section.opt()[1]);
    }

    @Test
    void titleKeepsNumLiteralWhenNumberingIsOff() {
        CommandBuilder.GeneralSettings settings = general(off(), off(),
                new CommandBuilder.TitleSetting(true, false, "Ep {num}", "1", "2"), false, "");
        CommandBuilder.Section section = new CommandBuilder().buildGeneral(FILES, settings);

        assertEquals(" --edit info --set title=\"Ep {num}\"", section.plain()[0]);
        assertEquals(" --edit info --set title=\"Ep {num}\"", section.plain()[1]);
    }

    @Test
    void titleEscapesQuotesPerVariant() {
        CommandBuilder.GeneralSettings settings = general(off(), off(),
                new CommandBuilder.TitleSetting(true, false, "He said \"hi\"", "1", "1"), false, "");
        CommandBuilder.Section section = new CommandBuilder().buildGeneral(FILES, settings);

        assertEquals(" --edit info --set title=\"He said \\\"hi\\\"\"", section.plain()[0]);
        assertEquals(" --edit info --set title=\"He said ####escaped__quotes#####hi####escaped__quotes#####\"",
                section.opt()[0]);
    }

    @Test
    void generalExtraUsesNameEscapingInOpt() {
        CommandBuilder.GeneralSettings settings = general(off(), off(), titleOff(), true, "C:\\dir");
        CommandBuilder.Section section = new CommandBuilder().buildGeneral(List.of("first.mkv"), settings);

        assertEquals(" C:\\dir", section.plain()[0]);
        assertEquals(" C:\\\\dir", section.opt()[0]);
    }

    @Test
    void blankGeneralExtraIsSkipped() {
        CommandBuilder.GeneralSettings settings = general(off(), off(), titleOff(), true, "   ");
        CommandBuilder.Section section = new CommandBuilder().buildGeneral(FILES, settings);

        assertArrayEquals(new String[] { "", "" }, section.plain());
        assertArrayEquals(new String[] { "", "" }, section.opt());
    }

    @Test
    void disabledSettingsProduceEmptyArguments() {
        CommandBuilder.Section section = new CommandBuilder().buildGeneral(FILES,
                general(off(), off(), titleOff(), false, ""));

        assertArrayEquals(new String[] { "", "" }, section.plain());
        assertArrayEquals(new String[] { "", "" }, section.opt());
    }

    /**
     * Preserved quirk: the original code parsed the numbering start field
     * unconditionally, before looking at the title checkbox or the file list.
     */
    @Test
    void startIsParsedEvenWithoutTitle() {
        CommandBuilder.GeneralSettings settings = general(off(), off(),
                new CommandBuilder.TitleSetting(false, false, "", "not-a-number", "1"), false, "");

        assertThrows(NumberFormatException.class,
                () -> new CommandBuilder().buildGeneral(FILES, settings));
    }

    /* Settings fixtures */

    private static CommandBuilder.GeneralSettings general(CommandBuilder.SourceSetting tags,
            CommandBuilder.SourceSetting chapters, CommandBuilder.TitleSetting title,
            boolean extraEnabled, String extra) {
        return new CommandBuilder.GeneralSettings(tags, chapters, title, extraEnabled, extra);
    }

    private static CommandBuilder.SourceSetting tags(int mode, String text) {
        return new CommandBuilder.SourceSetting(true, mode, text, ".xml");
    }

    private static CommandBuilder.SourceSetting chapters(int mode, String text) {
        return new CommandBuilder.SourceSetting(true, mode, text, ".xml");
    }

    private static CommandBuilder.SourceSetting off() {
        return new CommandBuilder.SourceSetting(false, 0, "", ".xml");
    }

    private static CommandBuilder.TitleSetting titleOff() {
        return new CommandBuilder.TitleSetting(false, false, "", "1", "1");
    }
}
