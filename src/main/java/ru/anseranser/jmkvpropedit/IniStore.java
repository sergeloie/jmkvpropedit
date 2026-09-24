package ru.anseranser.jmkvpropedit;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.ini4j.Ini;
import org.ini4j.InvalidFileFormatException;

/**
 * Reading and writing the application's INI configuration file — issue #15.
 *
 * <p>
 * The {@link JMkvpropedit} god class used to call ini4j directly from
 * readIniFile/saveIniFile/defaultIniFile; this store isolates that IO so the
 * god class only forwards results to the UI and nothing else. No Swing
 * dependencies, so the behavior is unit-testable.
 * </p>
 */
public final class IniStore {

    public static final String SECTION = "General";
    public static final String MKVPROPEDIT_KEY = "mkvpropedit";
    public static final String DEFAULT_MKVPROPEDIT = "mkvpropedit";

    private final File iniFile;

    public IniStore(File iniFile) {
        this.iniFile = iniFile;
    }

    public boolean exists() {
        return iniFile.exists();
    }

    /**
     * Reads the configured mkvpropedit path from the [General] section.
     *
     * <p>
     * The stream is owned here on purpose: ini4j's {@code new Ini(File)}
     * swallows open failures (an unreadable file silently loads as empty)
     * and leaks its file handle when parsing fails, which would keep the
     * config locked. Loading through an explicit stream fixes both.
     * </p>
     *
     * @return the stored path, or null when the file or the key is absent
     * @throws IniStoreException when the file exists but cannot be parsed or
     *                           read; the message reads
     *                           {@code malformed}/{@code could not read} +
     *                           file name, as the former god-class log lines
     *                           did
     */
    public String readMkvpropedit() throws IniStoreException {
        if (!iniFile.exists()) {
            return null;
        }

        try (InputStream in = new FileInputStream(iniFile)) {
            Ini ini = new Ini();
            ini.load(in);
            return ini.get(SECTION, MKVPROPEDIT_KEY);
        } catch (InvalidFileFormatException e) {
            throw new IniStoreException("malformed " + iniFile.getName() + ": " + e, e);
        } catch (IOException e) {
            throw new IniStoreException("could not read " + iniFile.getName() + ": " + e, e);
        }
    }

    /**
     * Stores the mkvpropedit path in the [General] section, creating the file
     * when it does not exist yet and keeping any other keys intact (same
     * flow as the former saveIniFile).
     *
     * @param exePath the executable path to store
     * @throws IniStoreException with the same message wording the former
     *                           god-class log lines used
     */
    public void saveMkvpropedit(String exePath) throws IniStoreException {
        try {
            if (!iniFile.exists()) {
                iniFile.createNewFile();
            }

            Ini ini = new Ini();
            try (InputStream in = new FileInputStream(iniFile)) {
                ini.load(in);
            }

            ini.put(SECTION, MKVPROPEDIT_KEY, exePath);

            try (OutputStream out = new FileOutputStream(iniFile)) {
                ini.store(out);
            }
        } catch (InvalidFileFormatException e) {
            throw new IniStoreException("malformed " + iniFile.getName() + ": " + e, e);
        } catch (IOException e) {
            throw new IniStoreException("could not save " + iniFile.getName() + ": " + e, e);
        }
    }
}
