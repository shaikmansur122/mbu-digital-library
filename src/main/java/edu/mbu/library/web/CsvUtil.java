package edu.mbu.library.web;

/** Helpers for building CSV downloads safely. */
public final class CsvUtil {

    private CsvUtil() {}

    /** A quoted cell, with a leading apostrophe if a spreadsheet could read the text as a formula. */
    public static String cell(String value) {
        String v = value == null ? "" : value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
