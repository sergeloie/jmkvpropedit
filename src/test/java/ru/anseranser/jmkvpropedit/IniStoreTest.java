package ru.anseranser.jmkvpropedit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for the INI configuration store — issue #15: reading and
 * writing JMkvpropedit.ini (ini4j) used to live inside the
 * {@link JMkvpropedit} god class as readIniFile/saveIniFile/defaultIniFile;
 * {@link IniStore} keeps that behavior isolated and free of Swing.
 */
class IniStoreTest {

    @TempDir
    Path tempDir;

    private Path iniPath() {
        return tempDir.resolve("JMkvpropedit.ini");
    }

    @Test
    void readReturnsNullWhenTheConfigFileIsMissing() throws Exception {
        IniStore store = new IniStore(iniPath().toFile());

        assertFalse(store.exists());
        assertNull(store.readMkvpropedit());
    }

    @Test
    void saveCreatesTheFileAndTheValueRoundTrips() throws Exception {
        IniStore store = new IniStore(iniPath().toFile());

        store.saveMkvpropedit("C:\\tools\\mkvpropedit.exe");

        assertTrue(Files.isRegularFile(iniPath()));
        assertTrue(store.exists());
        assertEquals("C:\\tools\\mkvpropedit.exe", store.readMkvpropedit());
        assertEquals("C:\\tools\\mkvpropedit.exe", new IniStore(iniPath().toFile()).readMkvpropedit());
    }

    @Test
    void saveReplacesThePreviouslyStoredExecutablePath() throws Exception {
        IniStore store = new IniStore(iniPath().toFile());

        store.saveMkvpropedit("first.exe");
        store.saveMkvpropedit("second.exe");

        assertEquals("second.exe", store.readMkvpropedit());
    }

    @Test
    void defaultExecutableValueRoundTrips() throws Exception {
        IniStore store = new IniStore(iniPath().toFile());

        store.saveMkvpropedit(IniStore.DEFAULT_MKVPROPEDIT);

        assertEquals("mkvpropedit", store.readMkvpropedit());
    }

    @Test
    void readReturnsNullWhenTheKeyIsMissing() throws Exception {
        Files.writeString(iniPath(), "[General]\nother=value\n", StandardCharsets.UTF_8);

        assertNull(new IniStore(iniPath().toFile()).readMkvpropedit());
    }

    @Test
    void malformedConfigFailsWithAMalformedMessage() throws Exception {
        Files.writeString(iniPath(), "[General\nmkvpropedit=x\n", StandardCharsets.UTF_8);

        IniStoreException e = assertThrows(IniStoreException.class,
                () -> new IniStore(iniPath().toFile()).readMkvpropedit());

        assertTrue(e.getMessage().startsWith("malformed JMkvpropedit.ini"), e.getMessage());
    }

    @Test
    void unreadableConfigFailsWithACouldNotReadMessage() throws Exception {
        Files.createDirectory(iniPath());

        IniStoreException e = assertThrows(IniStoreException.class,
                () -> new IniStore(iniPath().toFile()).readMkvpropedit());

        assertTrue(e.getMessage().startsWith("could not read JMkvpropedit.ini"), e.getMessage());
    }

    @Test
    void savingOverAnUnwritablePathFailsWithACouldNotSaveMessage() throws Exception {
        Files.createDirectory(iniPath());

        IniStoreException e = assertThrows(IniStoreException.class,
                () -> new IniStore(iniPath().toFile()).saveMkvpropedit("mkvpropedit"));

        assertTrue(e.getMessage().startsWith("could not save JMkvpropedit.ini"), e.getMessage());
    }
}
