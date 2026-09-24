package ru.anseranser.jmkvpropedit;

/**
 * A failure while reading or writing the INI configuration; the message is
 * already formatted for the output log (issue #15).
 */
public class IniStoreException extends Exception {

    private static final long serialVersionUID = 1L;

    public IniStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
