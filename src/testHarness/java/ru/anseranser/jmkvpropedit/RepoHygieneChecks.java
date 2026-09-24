package ru.anseranser.jmkvpropedit;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Repo hygiene checks for issue #7: no manual GC, no swallowed exceptions,
 * no misspelled UI strings/identifiers, an up-to-date readme, and no
 * pre-Gradle artifacts ({@code lib/}, {@code *.iml}). Issue #8 adds the
 * commons-io removal check (sources + Gradle dependencies on java.nio);
 * issue #15 adds the god-class IO extraction check (INI and folder-scan IO
 * live in {@link IniStore}/{@link FileScanner}, the modules stay Swing-free);
 * issue #14 adds the process extraction check (process launch and output
 * reading live in {@link ProcessRunner}, Swing-free; StreamGobbler is gone).
 *
 * <p>
 * The Gradle build files are out of scope for these issues, so this harness is
 * a plain {@code main()} program instead of a JUnit test, and it lives outside
 * Gradle source sets ({@code src/testHarness}) so {@code gradlew build} keeps
 * working without a test framework. Compile and run it from the repo root
 * after a build (plus {@code gradlew installDist} for the dependency jars):
 * </p>
 *
 * <pre>
 * javac -cp "build/classes/java/main" -d build/testHarness ^
 *       src/testHarness/java/ru/anseranser/jmkvpropedit/RepoHygieneChecks.java
 * java -cp "build/classes/java/main;build/testHarness;build/resources/main;build/install/jmkvpropedit/lib/*" ^
 *      ru.anseranser.jmkvpropedit.RepoHygieneChecks
 * </pre>
 *
 * <p>
 * Exits with a non-zero status when any check fails.
 * </p>
 */
public final class RepoHygieneChecks {

    private static int passed = 0;
    private static int failed = 0;

    /** Catches whose body is only whitespace: silently swallow the error. */
    private static final Pattern EMPTY_CATCH = Pattern.compile(
            "catch\\s*\\([^)]*\\)\\s*\\{\\s*\\}", Pattern.DOTALL);

    private static final Pattern[] BANNED_WORDS = {
            Pattern.compile("Excecutable"),
            Pattern.compile("Defaullt"),
            Pattern.compile("\\bprefered\\b"),
    };

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(".");
        Path mainSrc = root.resolve("src/main/java");

        if (!Files.isDirectory(mainSrc)) {
            System.err.println("run this harness from the repo root");
            System.exit(2);
        }

        // Scan everything under src/ except this file itself: its patterns
        // necessarily contain the banned spellings as literals.
        List<Path> sources = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root.resolve("src"))) {
            walk.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().equals("RepoHygieneChecks.java"))
                    .forEach(sources::add);
        }

        checkNoManualGc(sources);
        checkNoEmptyCatches(sources);
        checkNoMisspellings(sources, root.resolve("readme.txt"));
        checkReadmeRequiresJava21(root.resolve("readme.txt"));
        checkNoPreGradleArtifacts(root);
        checkNoCommonsIo(root.resolve("build.gradle.kts"), sources);
        checkIoStaysInModules(mainSrc);
        checkProcessStaysInModule(mainSrc);
        checkMethodRename();

        System.out.println();
        System.out.println("passed=" + passed + ", failed=" + failed);
        System.exit(failed == 0 ? 0 : 1);
    }

    /** AC: System.gc() calls are an anti-pattern and must be gone. */
    private static void checkNoManualGc(List<Path> sources) {
        List<String> hits = new ArrayList<>();

        for (Path p : sources) {
            String text = read(p);

            if (text.contains("System.gc(")) {
                hits.add(p + ":" + lineNumber(text, text.indexOf("System.gc(")));
            }
        }

        check("no System.gc() calls in sources", hits.isEmpty(), String.join(", ", hits));
    }

    /** AC: empty catch blocks must give a visible signal, not silence. */
    private static void checkNoEmptyCatches(List<Path> sources) {
        List<String> hits = new ArrayList<>();

        for (Path p : sources) {
            String text = read(p);
            Matcher m = EMPTY_CATCH.matcher(text);

            while (m.find()) {
                hits.add(p + ":" + lineNumber(text, m.start()));
            }
        }

        check("no empty catch blocks in sources", hits.isEmpty(), String.join(", ", hits));
    }

    /** AC: misspelled UI strings/identifiers (Excecutable, Defaullt, ...) are fixed. */
    private static void checkNoMisspellings(List<Path> sources, Path readme) {
        List<Path> files = new ArrayList<>(sources);

        if (Files.isRegularFile(readme)) {
            files.add(readme);
        }

        List<String> hits = new ArrayList<>();

        for (Path p : files) {
            String text = read(p);

            for (Pattern banned : BANNED_WORDS) {
                Matcher m = banned.matcher(text);

                while (m.find()) {
                    hits.add(p + ":" + lineNumber(text, m.start()) + " (" + m.group() + ")");
                }
            }
        }

        check("no misspelled UI strings or identifiers", hits.isEmpty(), String.join(", ", hits));
    }

    /** AC: readme matches the Java 21 toolchain, not Java 8. */
    private static void checkReadmeRequiresJava21(Path readme) {
        if (!Files.isRegularFile(readme)) {
            check("readme.txt requires Java 21", false, "readme.txt not found");
            return;
        }

        String text = read(readme);
        boolean wants21 = text.contains("Java 21");
        boolean stale8 = text.matches("(?s).*\\bJava 8\\b.*");

        check("readme.txt requires Java 21", wants21 && !stale8,
                "mentions Java 21=" + wants21 + ", still mentions Java 8=" + stale8);
    }

    /** AC: stale lib/ and *.iml are deleted and ignored by git. */
    private static void checkNoPreGradleArtifacts(Path root) {
        check("stale lib/ directory is gone", !Files.exists(root.resolve("lib")),
                "lib/ still exists");

        List<String> imls = new ArrayList<>();

        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> p.getFileName().toString().endsWith(".iml"))
                    .filter(p -> !isInIgnoredTree(root, p))
                    .forEach(p -> imls.add(root.relativize(p).toString()));
        } catch (Exception e) {
            check("stale *.iml files are gone", false, e.toString());
            return;
        }

        check("stale *.iml files are gone", imls.isEmpty(), String.join(", ", imls));

        Path gitignore = root.resolve(".gitignore");

        if (!Files.isRegularFile(gitignore)) {
            check(".gitignore keeps lib/ and *.iml from returning", false, ".gitignore not found");
            return;
        }

        String text = read(gitignore);
        boolean ignoresLib = Stream.of(text.split("\r?\n"))
                .map(String::trim)
                .anyMatch(l -> l.equals("/lib/") || l.equals("lib/") || l.equals("/lib"));
        boolean ignoresIml = Stream.of(text.split("\r?\n"))
                .map(String::trim)
                .anyMatch(l -> l.equals("*.iml"));

        check(".gitignore keeps lib/ and *.iml from returning", ignoresLib && ignoresIml,
                "ignores /lib/=" + ignoresLib + ", ignores *.iml=" + ignoresIml);
    }

    /** AC (issue #8): commons-io is gone from sources and Gradle dependencies. */
    private static void checkNoCommonsIo(Path buildGradle, List<Path> sources) {
        List<String> hits = new ArrayList<>();

        for (Path p : sources) {
            String text = read(p);
            int at = text.indexOf("org.apache.commons.io");

            if (at >= 0) {
                hits.add(p + ":" + lineNumber(text, at));
            }
        }

        check("no org.apache.commons.io usage in sources", hits.isEmpty(), String.join(", ", hits));

        if (!Files.isRegularFile(buildGradle)) {
            check("commons-io removed from build.gradle.kts", false, "build.gradle.kts not found");
            return;
        }

        String text = read(buildGradle);
        int at = text.indexOf("commons-io");

        check("commons-io removed from build.gradle.kts", at < 0,
                at < 0 ? "" : buildGradle + ":" + lineNumber(text, at));
    }

    /**
     * AC (issue #15): the god class keeps no INI or folder-scan file IO of
     * its own — only calls into {@link IniStore} and {@link FileScanner} —
     * and both modules carry no Swing dependency.
     */
    private static void checkIoStaysInModules(Path mainSrc) {
        Path god = mainSrc.resolve("ru/anseranser/jmkvpropedit/JMkvpropedit.java");
        Path iniStore = mainSrc.resolve("ru/anseranser/jmkvpropedit/IniStore.java");
        Path fileScanner = mainSrc.resolve("ru/anseranser/jmkvpropedit/FileScanner.java");

        check("IniStore module exists", Files.isRegularFile(iniStore), "IniStore.java not found");
        check("FileScanner module exists", Files.isRegularFile(fileScanner), "FileScanner.java not found");

        for (Path module : new Path[] { iniStore, fileScanner }) {
            if (!Files.isRegularFile(module)) {
                continue;
            }

            String text = read(module);
            boolean swing = text.contains("javax.swing") || text.contains("java.awt");

            check(module.getFileName() + " has no Swing dependency", !swing,
                    swing ? "imports Swing/AWT" : "");
        }

        if (!Files.isRegularFile(god)) {
            check("god class has no ini or folder-scan io beyond module calls", false,
                    "JMkvpropedit.java not found");
            return;
        }

        String text = read(god);
        String[] banned = {
                "org.ini4j",
                "new Ini(",
                "InvalidFileFormatException",
                "Files.walk",
                "getPathMatcher",
                "PathMatcher",
                "createNewFile",
                "import java.nio.file.Files",
        };
        List<String> hits = new ArrayList<>();

        for (String marker : banned) {
            int at = text.indexOf(marker);

            if (at >= 0) {
                hits.add(god.getFileName() + ":" + lineNumber(text, at) + " (" + marker + ")");
            }
        }

        check("god class has no ini or folder-scan io beyond module calls", hits.isEmpty(),
                String.join(", ", hits));
        check("god class delegates INI io to IniStore", text.contains("iniStore."),
                "no iniStore. call found");
        check("god class delegates folder scanning to FileScanner", text.contains("FileScanner."),
                "no FileScanner. call found");
    }

    /**
     * AC (issue #14): process launch, output reading and the executable
     * probe live in {@link ProcessRunner} — the module stays Swing-free,
     * the god class only calls it, and the removed StreamGobbler stays
     * gone.
     */
    private static void checkProcessStaysInModule(Path mainSrc) {
        Path god = mainSrc.resolve("ru/anseranser/jmkvpropedit/JMkvpropedit.java");
        Path processRunner = mainSrc.resolve("ru/anseranser/jmkvpropedit/ProcessRunner.java");
        Path streamGobbler = mainSrc.resolve("ru/anseranser/jmkvpropedit/StreamGobbler.java");

        check("ProcessRunner module exists", Files.isRegularFile(processRunner),
                "ProcessRunner.java not found");
        check("StreamGobbler is gone from main sources", !Files.exists(streamGobbler),
                "StreamGobbler.java still exists");

        if (Files.isRegularFile(processRunner)) {
            String module = read(processRunner);
            boolean swing = module.contains("javax.swing") || module.contains("java.awt");

            check("ProcessRunner module has no Swing dependency", !swing,
                    swing ? "imports Swing/AWT" : "");
        }

        if (!Files.isRegularFile(god)) {
            check("god class delegates process execution to ProcessRunner", false,
                    "JMkvpropedit.java not found");
            return;
        }

        String text = read(god);
        String[] banned = {
                "new ProcessBuilder(",
                "StreamGobbler",
                "proc.getInputStream",
                "pb.start(",
        };
        List<String> hits = new ArrayList<>();

        for (String marker : banned) {
            int at = text.indexOf(marker);

            if (at >= 0) {
                hits.add(god.getFileName() + ":" + lineNumber(text, at) + " (" + marker + ")");
            }
        }

        check("god class has no direct process launch beyond ProcessRunner calls", hits.isEmpty(),
                String.join(", ", hits));
        check("god class delegates process execution to ProcessRunner", text.contains("ProcessRunner"),
                "no ProcessRunner call found");
    }

    /** AC: the misspelled getMkvPropExeDefaullt() was renamed, not just its call site. */
    private static void checkMethodRename() {
        check("getMkvPropExeDefault() exists",
                declared("getMkvPropExeDefault"), "method not found");
        check("misspelled getMkvPropExeDefaullt() is gone",
                !declared("getMkvPropExeDefaullt"), "old spelling still declared");
    }

    private static boolean declared(String name) {
        for (Method m : JMkvpropedit.class.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                return true;
            }
        }

        return false;
    }

    /** Skips .git, build outputs and IDE dirs when hunting for *.iml. */
    private static boolean isInIgnoredTree(Path root, Path p) {
        Path rel = root.relativize(p);

        for (Path part : rel) {
            String name = part.toString();

            if (name.equals(".git") || name.equals("build") || name.equals(".gradle")
                    || name.equals(".idea") || name.equals(".settings")) {
                return true;
            }
        }

        return false;
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("cannot read " + p, e);
        }
    }

    private static int lineNumber(String text, int offset) {
        int line = 1;

        for (int i = 0; i < offset && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }

        return line;
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
