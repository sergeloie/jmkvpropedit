package ru.anseranser.jmkvpropedit;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;

/**
 * Framework-free verification harness for issue #4 (file filters: .webm
 * folder-scan mask, doubled setFileFilter in txt/xml dialogs, MIME combo
 * artifact and shared-list mutation) plus issue #8 (matroska mask on
 * java.nio PathMatcher glob, case-insensitive extensions, Files.walk folder
 * scan with recursion).
 *
 * <p>
 * The Gradle build files are out of scope for issue #4, so this harness is a
 * plain {@code main()} program instead of a JUnit test, and it lives outside
 * Gradle source sets ({@code src/testHarness}) so {@code gradlew build} keeps
 * working without a test framework. Compile and run it manually after a build:
 * </p>
 *
 * <pre>
 * javac -encoding UTF-8 -cp "build/classes/java/main" -d build/testHarness ^
 *       src/testHarness/java/ru/anseranser/jmkvpropedit/FileFiltersChecks.java
 * java -Dfile.encoding=UTF-8 -cp "build/classes/java/main;build/testHarness;build/resources/main;build/install/jmkvpropedit/lib/*" ^
 *      ru.anseranser.jmkvpropedit.FileFiltersChecks
 * </pre>
 *
 * <p>
 * Exits with a non-zero status when any check fails.
 * </p>
 */
public final class FileFiltersChecks {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        // The constructor calls parseFiles(argsArray); main() normally sets it first.
        setStatic("argsArray", new String[0]);

        // AC3 first: it asserts against a shared static list that older code
        // mutated during construction, so it must observe the pristine state.
        checkMimeCombosHaveNoArtifactAndStayConsistent();

        checkMatroskaMaskAcceptsAllExtensions();
        checkFolderScanPicksUpWebm();
        checkTextFileChooserExposesBothFilters();

        System.out.println();
        System.out.println("passed=" + passed + ", failed=" + failed);
        System.exit(failed == 0 ? 0 : 1);
    }

    /**
     * Issue #8: the folder-scan mask must be a java.nio glob PathMatcher that
     * accepts all five Matroska extensions (mkv/mka/mk3d/webm/mks)
     * case-insensitively — replacing the commons-io WildcardFileFilter — and
     * rejects everything else.
     */
    private static void checkMatroskaMaskAcceptsAllExtensions() {
        String[] accepted = {
                "movie.mkv", "movie.MKV", "MOVIE.MkV",
                "movie.mka", "movie.MKA",
                "movie.mk3d", "movie.MK3D",
                "movie.webm", "movie.WEBM",
                "movie.mks", "movie.MKS",
        };
        String[] rejected = { "movie.txt", "movie", "movie.mkv.bak", "mkv" };

        for (String name : accepted) {
            check("matroska mask accepts " + name,
                    JMkvpropedit.isMatroskaFile(Path.of(name)), "isMatroskaFile=false");
        }

        for (String name : rejected) {
            check("matroska mask rejects " + name,
                    !JMkvpropedit.isMatroskaFile(Path.of(name)), "isMatroskaFile=true");
        }
    }

    /**
     * AC1 + issue #8: addMkvFilesFromFolder must pick up .webm files next to
     * .mkv, match the mask case-insensitively (Files.walk scan) and recurse
     * into subfolders like the old FileUtils.iterateFiles did.
     */
    private static void checkFolderScanPicksUpWebm() throws Exception {
        JMkvpropedit w = newWindow();

        File folder = Files.createTempDirectory(
                Path.of(System.getProperty("java.io.tmpdir"), "opencode"), "jmkv-filters").toFile();
        try {
            touch(new File(folder, "a.mkv"));
            touch(new File(folder, "b.webm"));
            touch(new File(folder, "c.txt"));
            touch(new File(folder, "UPPER.MKV"));

            File subDir = new File(folder, "sub");
            if (!subDir.mkdir()) {
                check("create nested scan folder", false, "mkdir failed for " + subDir);
            }
            touch(new File(subDir, "nested.mka"));

            call(w, "addMkvFilesFromFolder", folder);
            // The scan is posted to the EDT via invokeLater; flush it.
            SwingUtilities.invokeAndWait(() -> { });

            @SuppressWarnings("unchecked")
            javax.swing.DefaultListModel<String> modelFiles =
                    (javax.swing.DefaultListModel<String>) get(w, "modelFiles");
            List<String> picked = new ArrayList<>();
            for (int i = 0; i < modelFiles.getSize(); i++) {
                picked.add(modelFiles.get(i));
            }

            boolean hasMkv = picked.stream().anyMatch(p -> p.endsWith("a.mkv"));
            boolean hasWebm = picked.stream().anyMatch(p -> p.endsWith("b.webm"));
            boolean hasTxt = picked.stream().anyMatch(p -> p.endsWith("c.txt"));
            boolean hasUpper = picked.stream()
                    .anyMatch(p -> p.toLowerCase(Locale.ROOT).endsWith("upper.mkv"));
            boolean hasNested = picked.stream()
                    .anyMatch(p -> p.toLowerCase(Locale.ROOT).endsWith("nested.mka"));

            check("folder scan picks up .mkv", hasMkv, "picked=" + picked);
            check("folder scan picks up .webm", hasWebm, "picked=" + picked);
            check("folder scan ignores .txt", !hasTxt, "picked=" + picked);
            check("folder scan picks up .MKV uppercase", hasUpper, "picked=" + picked);
            check("folder scan recurses into subfolders", hasNested, "picked=" + picked);
        } finally {
            deleteRecursively(folder);
        }
    }

    /**
     * AC2: the chapters/tags chooser setup must expose BOTH txt and xml in the
     * choosable list (two consecutive setFileFilter leaves the first one
     * unreachable per the JFileChooser API contract).
     */
    private static void checkTextFileChooserExposesBothFilters() throws Exception {
        JMkvpropedit w = newWindow();

        JFileChooser chooser = new JFileChooser();
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.resetChoosableFileFilters();

        Method configure;
        try {
            configure = JMkvpropedit.class.getDeclaredMethod(
                    "configureTextFileChooser", JFileChooser.class, String.class);
            configure.setAccessible(true);
        } catch (NoSuchMethodException e) {
            check("configureTextFileChooser(JFileChooser, String) exists", false,
                    "method not found on JMkvpropedit");
            return;
        }
        configure.invoke(w, chooser, "Select chapters file");

        FileFilter[] choosable = chooser.getChoosableFileFilters();
        List<String> descriptions = new ArrayList<>();
        for (FileFilter f : choosable) {
            descriptions.add(f.getDescription());
        }

        boolean hasTxt = descriptions.stream().anyMatch(d -> d.contains("*.txt"));
        boolean hasXml = descriptions.stream().anyMatch(d -> d.contains("*.xml"));
        FileFilter current = chooser.getFileFilter();
        boolean currentIsXml = current != null && current.getDescription().contains("*.xml");

        check("text chooser exposes the txt filter", hasTxt, "choosable=" + descriptions);
        check("text chooser exposes the xml filter", hasXml, "choosable=" + descriptions);
        check("text chooser current filter is xml (was last set)", currentIsXml,
                "current=" + (current == null ? "null" : current.getDescription()));
        check("text chooser dialog title is applied",
                "Select chapters file".equals(chooser.getDialogTitle()), "title=" + chooser.getDialogTitle());
    }

    /**
     * AC3: none of the attachment MIME combos may contain the "_" artifact, all
     * four combos must hold the same items, and the shared MkvStrings list must
     * not be mutated by construction.
     */
    private static void checkMimeCombosHaveNoArtifactAndStayConsistent() throws Exception {
        List<String> before = new ArrayList<>(mkvStringsMimeTypeList());

        JMkvpropedit w = newWindow();

        List<List<String>> combos = new ArrayList<>();
        combos.add(comboItems(w, "cbAttachAddMime"));
        combos.add(comboItems(w, "cbAttachReplaceOrig"));
        combos.add(comboItems(w, "cbAttachReplaceMime"));
        combos.add(comboItems(w, "cbAttachDeleteValue"));

        String[] names = { "cbAttachAddMime", "cbAttachReplaceOrig", "cbAttachReplaceMime", "cbAttachDeleteValue" };
        for (int i = 0; i < combos.size(); i++) {
            List<String> items = combos.get(i);
            check(names[i] + ": no '_' artifact item", !items.contains("_"), "items head=" + head(items));
        }

        List<String> reference = combos.get(0);
        for (int i = 1; i < combos.size(); i++) {
            check(names[i] + ": same items as " + names[0], reference.equals(combos.get(i)),
                    "size " + combos.get(i).size() + " vs " + reference.size());
        }

        // The shared resource-backed list must not be mutated by construction.
        List<String> after = mkvStringsMimeTypeList();
        check("shared MkvStrings mime list is not mutated by construction",
                before.equals(after),
                "before(size=" + before.size() + ", head=" + head(before)
                        + ") after(size=" + after.size() + ", head=" + head(after) + ")");
    }

    /* Harness plumbing */

    private static JMkvpropedit newWindow() throws Exception {
        return new JMkvpropedit();
    }

    private static List<String> comboItems(JMkvpropedit w, String fieldName) throws Exception {
        @SuppressWarnings("unchecked")
        JComboBox<String> combo = (JComboBox<String>) get(w, fieldName);
        DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) combo.getModel();
        List<String> items = new ArrayList<>();
        for (int i = 0; i < model.getSize(); i++) {
            items.add(model.getElementAt(i));
        }
        return items;
    }

    @SuppressWarnings("unchecked")
    private static List<String> mkvStringsMimeTypeList() throws Exception {
        Object mkvStrings = getStatic("mkvStrings");
        Method m = mkvStrings.getClass().getMethod("getMimeTypeList");
        return new ArrayList<>((List<String>) m.invoke(mkvStrings));
    }

    private static List<String> head(List<String> items) {
        return items.subList(0, Math.min(3, items.size()));
    }

    private static void touch(File file) throws Exception {
        Files.write(file.toPath(), new byte[0]);
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = JMkvpropedit.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object getStatic(String name) throws Exception {
        Field field = JMkvpropedit.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static void setStatic(String name, Object value) throws Exception {
        Field field = JMkvpropedit.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static Object call(Object target, String name, Object... args) throws Exception {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = (args[i] instanceof File) ? File.class : args[i].getClass();
        }
        Method method = JMkvpropedit.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static void check(String what, boolean condition, String detail) {
        if (condition) {
            passed++;
            System.out.println("PASS  " + what);
        } else {
            failed++;
            System.out.println("FAIL  " + what + "  (" + detail + ")");
        }
    }
}
