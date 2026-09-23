package ru.anseranser.jmkvpropedit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builds mkvpropedit command-line arguments from plain settings.
 *
 * <p>
 * The "settings -> arguments" logic used to live inside the Swing god class
 * {@link JMkvpropedit}; issue #11 moves it here so it can be unit-tested
 * without a UI. This class never touches Swing — the caller reads the widgets
 * into the settings records below. Pure string helpers (escaping, padding,
 * path names) stay in {@link Utils}.
 * </p>
 */
public final class CommandBuilder {

    /**
     * Tags/chapters source selector.
     *
     * @param enabled   the section checkbox
     * @param mode      0 = Remove, 1 = From file, 2 = Match file name with suffix
     * @param text      the text field content
     * @param extension the extension combo content (.xml/.txt), used by mode 2
     */
    public record SourceSetting(boolean enabled, int mode, String text, String extension) {
    }

    /**
     * The film title setting. Numbering fields stay raw text and are parsed
     * exactly where (and only when) the original UI-driven code parsed them.
     *
     * @param enabled     the title checkbox
     * @param numbering   the numbering checkbox
     * @param text        the title text ({@code {num}}/{@code {file_name}} templates)
     * @param numberStart raw text of the numbering start field
     * @param numberPad   raw text of the numbering padding field
     */
    public record TitleSetting(boolean enabled, boolean numbering, String text,
            String numberStart, String numberPad) {
    }

    /**
     * The General tab as plain data: one settings object for the whole file list.
     *
     * @param tags        tags section
     * @param chapters    chapters section
     * @param title       title section
     * @param extraEnabled the "Extra parameters" checkbox
     * @param extra       the extra parameters text
     */
    public record GeneralSettings(SourceSetting tags, SourceSetting chapters,
            TitleSetting title, boolean extraEnabled, String extra) {
    }

    /**
     * One track slot (video/audio/subtitle) as plain data. Track types share
     * this shape; the track type itself is the {@code track:v1}/{@code a}/{@code s}
     * selector passed to {@link #buildTracks}.
     *
     * @param edit        the "Edit this track" checkbox
     * @param setEnabled  append flag-enabled
     * @param enableValue value of flag-enabled (true = 1, false = 0)
     * @param setDefault  append flag-default
     * @param defaultValue value of flag-default
     * @param setForced   append flag-forced
     * @param forcedValue value of flag-forced
     * @param setName     append the track name
     * @param name        the name text ({@code {num}}/{@code {file_name}} templates)
     * @param setLanguage append the language
     * @param language    the language code (already resolved from the combo)
     * @param setExtra    append the extra parameters
     * @param extra       the extra parameters text
     * @param numbering   the per-track numbering checkbox
     * @param numberStart raw text of the numbering start field (parsed only when edit is on)
     * @param numberPad   raw text of the numbering padding field (parsed only when edit is on)
     */
    public record TrackSettings(boolean edit,
            boolean setEnabled, boolean enableValue,
            boolean setDefault, boolean defaultValue,
            boolean setForced, boolean forcedValue,
            boolean setName, String name,
            boolean setLanguage, String language,
            boolean setExtra, String extra,
            boolean numbering, String numberStart, String numberPad) {
    }

    /**
     * Per-file argument strings for one section: the plain variant (shown in
     * the output pane) and the Opt variant (encoded for options.json).
     *
     * @param plain one string per file
     * @param opt   one string per file, same order
     */
    public record Section(String[] plain, String[] opt) {
    }

    /**
     * Attachment arguments in the order they join the command:
     * delete, then add, then replace (the order the original code used).
     */
    public record AttachmentArgs(String delete, String add, String replace) {

        String joined() {
            return delete + add + replace;
        }
    }

    /** Attachment arguments in both variants. */
    public record Attachments(AttachmentArgs plain, AttachmentArgs opt) {
    }

    /**
     * The finished batch: one display line per file plus the cracked Opt
     * argument lists (the {@code List<String[]>} written to options.json).
     */
    public record Batch(List<String> lines, List<String[]> optArgs) {
    }

    private final boolean windows;

    /** Uses the current platform, like the original UI-driven code did. */
    public CommandBuilder() {
        this(Utils.isWindows());
    }

    /** Test seam: pins the platform-dependent plain-variant escaping. */
    public CommandBuilder(boolean windows) {
        this.windows = windows;
    }

    /**
     * Builds the General-tab arguments, one string per file.
     */
    public Section buildGeneral(List<String> files, GeneralSettings settings) {
        String[] plain = new String[files.size()];
        String[] opt = new String[files.size()];
        Arrays.fill(plain, "");
        Arrays.fill(opt, "");
        return new Section(plain, opt);
    }

    /**
     * Builds the track (video/audio/subtitle) arguments, one string per file.
     *
     * @param files    the file list, in order
     * @param selector the track selector: {@code v}, {@code a} or {@code s}
     * @param tracks   the track slots, slot index = track number - 1
     */
    public Section buildTracks(List<String> files, char selector, List<TrackSettings> tracks) {
        String[] plain = new String[files.size()];
        String[] opt = new String[files.size()];
        Arrays.fill(plain, "");
        Arrays.fill(opt, "");
        return new Section(plain, opt);
    }

    /**
     * Composes the final batch: exe + file + joined sections per file, plus
     * the Opt argument lists ready for options.json.
     */
    public Batch buildBatch(String exePath, List<String> files,
            Section general, Section video, Section audio, Section subtitle, Attachments attachments) {
        return new Batch(new ArrayList<>(), new ArrayList<>());
    }
}
