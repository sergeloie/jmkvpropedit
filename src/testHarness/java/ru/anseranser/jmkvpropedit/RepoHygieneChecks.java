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
 * pre-Gradle artifacts ({@code lib/}, {@code *.iml}).
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
