package ru.anseranser.jmkvpropedit;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Packaging checks for issue #10: launch4j and the GraalVM native plugin are
 * gone from the build, an opt-in {@code jpackage} task builds the
 * self-contained installer (bundled Java 21 runtime), and the pre-jpackage
 * launcher bat/README instructions are retired.
 *
 * <p>
 * Like the other harnesses this is a plain {@code main()} program living
 * outside the Gradle source sets ({@code src/testHarness}), so
 * {@code gradlew build} keeps working without a test framework. Compile and
 * run it from the repo root after a build:
 * </p>
 *
 * <pre>
 * javac -d build/testHarness src/testHarness/java/ru/anseranser/jmkvpropedit/PackagingChecks.java
 * java -cp build/testHarness ru.anseranser.jmkvpropedit.PackagingChecks
 * </pre>
 *
 * <p>
 * Exits with a non-zero status when any check fails.
 * </p>
 */
public final class PackagingChecks {

    private static int passed = 0;
    private static int failed = 0;

    /** Matches tasks.register("jpackage") / tasks.register<Exec>("jpackage"). */
    private static final Pattern JPACKAGE_TASK =
            Pattern.compile("register\\s*(?:<[^>]+>)?\\s*\\(\\s*\"jpackage\"");

    /**
     * A dependsOn/finalizedBy that pulls jpackage into another task's graph
     * would break plain {@code gradlew build} on machines without jpackage/WiX
     * (e.g. the Linux CI runner): the task must stay opt-in.
     */
    private static final Pattern FORCED_INTO_GRAPH =
            Pattern.compile("(?:dependsOn|finalizedBy)\\s*\\([^)]*jpackage");

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(".");
        Path buildGradle = root.resolve("build.gradle.kts");
        Path readme = root.resolve("readme.txt");
        Path legacyBat = root.resolve("extra/JMkvpropedit.bat");

        if (!Files.isRegularFile(buildGradle)) {
            System.err.println("run this harness from the repo root");
            System.exit(2);
        }

        String buildText = read(buildGradle);
        String readmeText = Files.isRegularFile(readme) ? read(readme) : "";

        checkLegacyPackagingPluginsRemoved(buildText);
        checkJpackageTaskIsOptIn(buildText);
        checkLegacyLauncherRetired(legacyBat);
        checkReadmeDocumentsBundledInstaller(readme, readmeText);

        System.out.println();
        System.out.println("passed=" + passed + ", failed=" + failed);
        System.exit(failed == 0 ? 0 : 1);
    }

    /** AC: launch4j and the GraalVM native plugin are deleted from the build. */
    private static void checkLegacyPackagingPluginsRemoved(String buildText) {
        String lower = buildText.toLowerCase();

        check("launch4j removed from build.gradle.kts",
                !lower.contains("launch4j"),
                location(buildText, "launch4j"));

        check("graalvm native removed from build.gradle.kts",
                !lower.contains("graalvm"),
                location(buildText, "graalvm"));
    }

    /** AC: an opt-in jpackage task exists and is not part of the build graph. */
    private static void checkJpackageTaskIsOptIn(String buildText) {
        check("jpackage task registered in build.gradle.kts",
                JPACKAGE_TASK.matcher(buildText).find(),
                "no tasks.register(\"jpackage\") found");

        Matcher m = FORCED_INTO_GRAPH.matcher(buildText);
        boolean forced = m.find();

        check("jpackage stays out of the build/check task graph",
                !forced,
                forced ? "found: " + m.group() : "");
    }

    /**
     * AC: the legacy system-Java bat launcher is removed, or at minimum no
     * longer starts the app through an installed {@code javaw}/{@code java}.
     */
    private static void checkLegacyLauncherRetired(Path legacyBat) throws Exception {
        String name = "legacy extra/JMkvpropedit.bat is removed or retired";

        if (!Files.exists(legacyBat)) {
            check(name, true, "");
            return;
        }

        String text = read(legacyBat);
        boolean startsSystemJava = text.toLowerCase().contains("javaw")
                || Pattern.compile("\\bjava\\b").matcher(text.toLowerCase()).find();

        check(name, !startsSystemJava, "still launches via system Java: " + legacyBat);
    }

    /**
     * AC: the readme no longer instructs installing a system Java (JavaSoft
     * registry keys / "Requires Java ...") and instead documents the
     * self-contained installer with its bundled runtime.
     */
    private static void checkReadmeDocumentsBundledInstaller(Path readme, String text) {
        if (!Files.isRegularFile(readme)) {
            check("readme.txt documents the bundled-runtime installer", false, "readme.txt not found");
            check("readme.txt drops legacy system-Java instructions", false, "readme.txt not found");
            return;
        }

        String lower = text.toLowerCase();
        boolean bundled = lower.contains("bundl");
        boolean installer = lower.contains("installer");

        check("readme.txt documents the bundled-runtime installer",
                bundled && installer,
                "mentions bundl=" + bundled + ", mentions installer=" + installer);

        boolean javaSoft = text.contains("JavaSoft");
        boolean requiresJava = text.contains("Requires Java");

        check("readme.txt drops legacy system-Java instructions",
                !javaSoft && !requiresJava,
                "mentions JavaSoft=" + javaSoft + ", says Requires Java=" + requiresJava);
    }

    private static String location(String text, String needle) {
        int at = text.toLowerCase().indexOf(needle.toLowerCase());
        return at < 0 ? "" : "found at offset " + at;
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("cannot read " + p, e);
        }
    }

    private static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            System.out.println("PASS " + name);
        } else {
            failed++;
            System.out.println("FAIL " + name + (detail.isEmpty() ? "" : ": " + detail));
        }
    }
}
