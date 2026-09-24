package ru.anseranser.jmkvpropedit;

/**
 * The three track types the unified {@link TrackPanel} serves — issue #12.
 *
 * <p>
 * The original god class kept three near-identical copies of the track UI
 * (video, audio, subtitle). Everything that actually differs between them is
 * collected here: the label used for combo items and numbering explanations,
 * the tab title, the CardLayout card prefix and the mkvpropedit track
 * selector. Everything else lives once in {@link TrackPanel}/{@link TrackSlot}.
 * </p>
 */
public enum TrackType {

    /** Video tracks: combo items "Video Track N", selector {@code v}. */
    VIDEO("Video", "Video", "My Video", 'v'),

    /** Audio tracks: combo items "Audio Track N", selector {@code a}. */
    AUDIO("Audio", "Audio", "My Audio", 'a'),

    /**
     * Subtitle tracks: combo items "Subtitle Track N", selector {@code s}.
     * The tab title is plural ("Subtitles"), like the original UI.
     */
    SUBTITLE("Subtitle", "Subtitles", "My Subtitle", 's');

    private final String label;
    private final String tabTitle;
    private final String nameExample;
    private final char selector;

    TrackType(String label, String tabTitle, String nameExample, char selector) {
        this.label = label;
        this.tabTitle = tabTitle;
        this.nameExample = nameExample;
        this.selector = selector;
    }

    /** The type label: combo items read {@code "<label> Track <n>"}. */
    public String label() {
        return label;
    }

    /** The tab title in the main window ("Video", "Audio", "Subtitles"). */
    public String tabTitle() {
        return tabTitle;
    }

    /** Example name used by the numbering explain label, e.g. {@code My Video}. */
    public String nameExample() {
        return nameExample;
    }

    /** The mkvpropedit track selector: {@code v}, {@code a} or {@code s}. */
    public char selector() {
        return selector;
    }

    /**
     * The CardLayout constraint for a slot's settings card, e.g.
     * {@code subPnlVideo[0]} — the same names the original triplet used.
     */
    public String cardPrefix() {
        return "subPnl" + label;
    }
}
