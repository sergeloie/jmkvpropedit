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
     * Builds the General-tab arguments, one string per file: tags, chapters,
     * title (with {@code {num}}/{@code {file_name}} substitution) and extra
     * parameters, in that order.
     */
    public Section buildGeneral(List<String> files, GeneralSettings settings) {
        String[] plain = new String[files.size()];
        String[] opt = new String[files.size()];
        int start = Integer.parseInt(settings.title().numberStart());

        for (int i = 0; i < files.size(); i++) {
            plain[i] = "";
            opt[i] = "";
            String file = files.get(i);

            SourceSetting tags = settings.tags();
            if (tags.enabled()) {
                switch (tags.mode()) {
                case 0 -> {
                    plain[i] += " --tags all:";
                    opt[i] += " --tags all:";
                }
                case 1 -> {
                    if (tags.text().trim().isEmpty()) {
                        plain[i] += " --tags all:";
                        opt[i] += " --tags all:";
                    } else {
                        plain[i] += " --tags all:\"" + plainValue(tags.text()) + "\"";
                        opt[i] += " --tags all:\"" + Utils.escapeName(tags.text()) + "\"";
                    }
                }
                case 2 -> {
                    String value = Utils.getPathWithoutExt(file) + tags.text() + tags.extension();
                    plain[i] += " --tags all:\"" + plainValue(value) + "\"";
                    opt[i] += " --tags all:\"" + Utils.escapeName(value) + "\"";
                }
                default -> {
                }
                }
            }

            SourceSetting chapters = settings.chapters();
            if (chapters.enabled()) {
                switch (chapters.mode()) {
                case 0 -> {
                    plain[i] += " --chapters \"\"";
                    opt[i] += " --chapters ''";
                }
                case 1 -> {
                    if (chapters.text().trim().isEmpty()) {
                        plain[i] += " --chapters \"\"";
                        opt[i] += " --chapters ''";
                    } else {
                        plain[i] += " --chapters \"" + plainValue(chapters.text()) + "\"";
                        opt[i] += " --chapters \"" + Utils.escapeName(chapters.text()) + "\"";
                    }
                }
                case 2 -> {
                    String value = Utils.getPathWithoutExt(file) + chapters.text() + chapters.extension();
                    plain[i] += " --chapters \"" + plainValue(value) + "\"";
                    opt[i] += " --chapters \"" + Utils.escapeName(value) + "\"";
                }
                default -> {
                }
                }
            }

            TitleSetting title = settings.title();
            if (title.enabled()) {
                plain[i] += " --edit info";
                opt[i] += " --edit info";

                String newTitle = title.text();

                if (title.numbering()) {
                    int pad = Integer.parseInt(title.numberPad());
                    newTitle = newTitle.replace("{num}", Utils.padNumber(pad, start));
                    start++;
                }

                newTitle = newTitle.replace("{file_name}", Utils.getFileNameWithoutExt(file));

                plain[i] += " --set title=\"" + Utils.escapeQuotes(newTitle) + "\"";
                opt[i] += " --set title=\"" + Utils.escapeName(newTitle) + "\"";
            }

            if (settings.extraEnabled() && !settings.extra().trim().isEmpty()) {
                plain[i] += " " + settings.extra();
                opt[i] += " " + Utils.escapeName(settings.extra());
            }
        }

        return new Section(plain, opt);
    }

    /**
     * Builds the track (video/audio/subtitle) arguments, one string per file.
     * The per-track argument is built once (the original rebuilt it per file
     * with an identical result), then {@code {num}} and {@code {file_name}}
     * are substituted per file.
     *
     * <p>
     * Preserved quirk: {@code editCount} — the guard that drops a bare
     * {@code --edit track} with no properties — accumulates across tracks, so
     * a bare-edit track only survives when an earlier track already added a
     * property. Behavior frozen by the issue #3 harness; do not "fix" here.
     * </p>
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

        int fileCount = files.size();
        int trackCount = tracks.size();
        String[] tmpPlain = new String[trackCount];
        String[] tmpOpt = new String[trackCount];
        int[] numberStarts = new int[trackCount];
        int[] numberPads = new int[trackCount];

        // The original parsed/builds inside its file loop; only run when there
        // is at least one file, so the parse timing matches exactly.
        if (fileCount > 0) {
            buildTrackArgs(selector, tracks, tmpPlain, tmpOpt, numberStarts, numberPads);
        }

        for (int t = 0; t < trackCount; t++) {
            TrackSettings settings = tracks.get(t);

            for (int f = 0; f < fileCount; f++) {
                String trackPlain = tmpPlain[t];
                String trackOpt = tmpOpt[t];

                if (settings.numbering() && settings.edit()) {
                    trackPlain = trackPlain.replace("{num}", Utils.padNumber(numberPads[t], numberStarts[t]));
                    trackOpt = trackOpt.replace("{num}", Utils.padNumber(numberPads[t], numberStarts[t]));
                    numberStarts[t]++;
                }

                trackPlain = trackPlain.replace("{file_name}", Utils.getFileNameWithoutExt(files.get(f)));
                trackOpt = trackOpt.replace("{file_name}", Utils.getFileNameWithoutExt(files.get(f)));

                plain[f] += trackPlain;
                opt[f] += trackOpt;
            }
        }

        return new Section(plain, opt);
    }

    /** Builds the per-track argument pair (before per-file substitution). */
    private void buildTrackArgs(char selector, List<TrackSettings> tracks,
            String[] tmpPlain, String[] tmpOpt, int[] numberStarts, int[] numberPads) {
        int editCount = 0;

        for (int t = 0; t < tracks.size(); t++) {
            TrackSettings settings = tracks.get(t);

            if (!settings.edit()) {
                tmpPlain[t] = "";
                tmpOpt[t] = "";
                continue;
            }

            numberStarts[t] = Integer.parseInt(settings.numberStart());
            numberPads[t] = Integer.parseInt(settings.numberPad());

            StringBuilder trackPlain = new StringBuilder(" --edit track:").append(selector).append(t + 1);
            StringBuilder trackOpt = new StringBuilder(" --edit track:").append(selector).append(t + 1);

            if (settings.setEnabled()) {
                appendFlag(trackPlain, trackOpt, "flag-enabled", settings.enableValue());
                editCount++;
            }

            if (settings.setDefault()) {
                appendFlag(trackPlain, trackOpt, "flag-default", settings.defaultValue());
                editCount++;
            }

            if (settings.setForced()) {
                appendFlag(trackPlain, trackOpt, "flag-forced", settings.forcedValue());
                editCount++;
            }

            if (settings.setName()) {
                trackPlain.append(" --set name=\"").append(Utils.escapeQuotes(settings.name())).append("\"");
                trackOpt.append(" --set name=\"").append(Utils.escapeName(settings.name())).append("\"");
                editCount++;
            }

            if (settings.setLanguage()) {
                trackPlain.append(" --set language=\"").append(settings.language()).append("\"");
                trackOpt.append(" --set language=\"").append(settings.language()).append("\"");
                editCount++;
            }

            if (settings.setExtra() && !settings.extra().trim().isEmpty()) {
                trackPlain.append(" ").append(settings.extra());
                trackOpt.append(" ").append(Utils.escapeBackslashes(settings.extra()));
                editCount++;
            }

            if (editCount == 0) {
                tmpPlain[t] = "";
                tmpOpt[t] = "";
            } else {
                tmpPlain[t] = trackPlain.toString();
                tmpOpt[t] = trackOpt.toString();
            }
        }
    }

    private static void appendFlag(StringBuilder plain, StringBuilder opt, String flag, boolean value) {
        plain.append(" --set ").append(flag).append('=').append(value ? "1" : "0");
        opt.append(" --set ").append(flag).append('=').append(value ? "1" : "0");
    }

    /**
     * Composes the final batch: exe + file + joined sections per file, plus
     * the Opt argument lists ready for options.json. An empty file list or an
     * empty composed command yields an empty batch ("Nothing to do!").
     *
     * <p>
     * Section order (frozen): general, attachments (delete, add, replace),
     * video, audio, subtitle. The plain line quotes exe and file raw on
     * Windows and with {@link Utils#escapeQuotes} elsewhere; the Opt variant
     * never contains the exe — only the escaped file and the edit arguments.
     * </p>
     */
    public Batch buildBatch(String exePath, List<String> files,
            Section general, Section video, Section audio, Section subtitle, Attachments attachments) {
        List<String> lines = new ArrayList<>();
        List<String[]> optArgs = new ArrayList<>();

        if (files.isEmpty()) {
            return new Batch(lines, optArgs);
        }

        String first = general.plain()[0] + attachments.plain().joined()
                + video.plain()[0] + audio.plain()[0] + subtitle.plain()[0];
        if (first.isEmpty()) {
            return new Batch(lines, optArgs);
        }

        for (int i = 0; i < files.size(); i++) {
            String all = general.plain()[i] + attachments.plain().joined()
                    + video.plain()[i] + audio.plain()[i] + subtitle.plain()[i];
            String allOpt = general.opt()[i] + attachments.opt().joined()
                    + video.opt()[i] + audio.opt()[i] + subtitle.opt()[i];

            String quotedExe = windows ? exePath : Utils.escapeQuotes(exePath);
            String quotedFile = windows ? files.get(i) : Utils.escapeQuotes(files.get(i));
            lines.add("\"" + quotedExe + "\" \"" + quotedFile + "\"" + all);

            optArgs.add(toOptArgs("\"" + Utils.escapeName(files.get(i)) + "\"" + allOpt));
        }

        return new Batch(lines, optArgs);
    }

    /**
     * Cracks an Opt command line into the argument list for options.json.
     *
     * <p>
     * The Opt strings still carry {@link Utils#escapeName}'s encoding (quote
     * placeholder + doubled backslashes, frozen by issue #3). It is unwound
     * exactly once here, at the boundary where the string form becomes
     * structured arguments; JSON escaping itself (see
     * {@link JMkvpropedit#optionsJson}) is placeholder-free.
     * </p>
     */
    public static String[] toOptArgs(String optCommandLine) {
        String[] args = Commandline.translateCommandline(optCommandLine);
        for (int i = 0; i < args.length; i++) {
            args[i] = decodeOptEscaping(args[i]);
        }
        return args;
    }

    /**
     * Unwinds {@link Utils#escapeName}'s quote placeholder and doubled
     * backslashes, so the original data can be JSON-escaped from scratch.
     */
    private static String decodeOptEscaping(String token) {
        return token.replace("####escaped__quotes#####", "\"").replace("\\\\", "\\");
    }

    /** The plain variant's escaping: raw on Windows, quote-escaped elsewhere. */
    private String plainValue(String text) {
        return windows ? text : Utils.escapeQuotes(text);
    }
}
