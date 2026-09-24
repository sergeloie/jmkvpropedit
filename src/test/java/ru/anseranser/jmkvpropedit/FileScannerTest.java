package ru.anseranser.jmkvpropedit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for the Matroska folder scanner — issue #15: the Files.walk +
 * PathMatcher scan that used to live inside the {@link JMkvpropedit} god
 * class as isMatroskaFile/addMkvFilesFromFolder (java.nio after the
 * commons-io removal in issue #8); {@link FileScanner} keeps it isolated
 * and free of Swing.
 */
class FileScannerTest {

    @ParameterizedTest
    @ValueSource(strings = { "movie.mkv", "movie.MKV", "MOVIE.MkV", "movie.mka", "movie.MKA",
            "movie.mk3d", "movie.MK3D", "movie.webm", "movie.WEBM", "movie.mks", "movie.MKS" })
    void matroskaMaskAcceptsEveryExtensionCaseInsensitively(String name) {
        assertTrue(FileScanner.isMatroskaFile(Path.of(name)), name);
    }

    @ParameterizedTest
    @ValueSource(strings = { "movie.txt", "movie", "movie.mkv.bak", "mkv", "movie.mp4", "movie.mks.tmp" })
    void matroskaMaskRejectsNonMatroskaNames(String name) {
        assertFalse(FileScanner.isMatroskaFile(Path.of(name)), name);
    }

    @Test
    void scanPicksUpMatroskaFilesRecursivelyAndSkipsOthers(@TempDir Path folder) throws Exception {
        Path mkv = Files.createFile(folder.resolve("a.mkv"));
        Path webm = Files.createFile(folder.resolve("b.webm"));
        Files.createFile(folder.resolve("c.txt"));
        Path upper = Files.createFile(folder.resolve("UPPER.MKV"));
        Path sub = Files.createDirectory(folder.resolve("sub"));
        Path nested = Files.createFile(sub.resolve("nested.mka"));

        List<Path> found = FileScanner.scanMatroskaFiles(folder);

        assertEquals(4, found.size(), found::toString);
        assertTrue(found.contains(mkv), found::toString);
        assertTrue(found.contains(webm), found::toString);
        assertTrue(found.contains(upper), found::toString);
        assertTrue(found.contains(nested), found::toString);
        assertTrue(found.stream().allMatch(FileScanner::isMatroskaFile), found::toString);
    }

    @Test
    void scanOfAnEmptyFolderFindsNothing(@TempDir Path folder) throws Exception {
        assertTrue(FileScanner.scanMatroskaFiles(folder).isEmpty());
    }

    @Test
    void scanOfAMissingFolderFails(@TempDir Path folder) {
        assertThrows(IOException.class, () -> FileScanner.scanMatroskaFiles(folder.resolve("missing")));
    }
}
