package ge.util;

/** Small formatting helpers for gp amounts and percentages. */
public final class Fmt {

    private Fmt() {
    }

    /** Format a gp amount compactly: 1.23B / 4.56M / 789K / 845. */
    public static String gp(double v) {
        double a = Math.abs(v);
        String s;
        if (a >= 1_000_000_000d) s = trim(v / 1_000_000_000d) + "B";
        else if (a >= 1_000_000d) s = trim(v / 1_000_000d) + "M";
        else if (a >= 1_000d) s = trim(v / 1_000d) + "K";
        else s = String.valueOf(Math.round(v));
        return s;
    }

    private static String trim(double v) {
        String s = String.format("%.2f", v);
        // Drop trailing zeros / dot for a tidy "1.2" or "3" rather than "1.20".
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return s;
    }

    /** Signed percentage with one decimal, e.g. "+12.4%" / "-3.1%". */
    public static String pct(double v) {
        return String.format("%+.1f%%", v);
    }

    /** Unsigned percentage with one decimal. */
    public static String pctUnsigned(double v) {
        return String.format("%.1f%%", v);
    }

    /** Pad/clip a string to an exact width (left-justified). */
    public static String pad(String s, int width) {
        if (s.length() > width) return s.substring(0, width);
        return s + " ".repeat(width - s.length());
    }

    /** Pad/clip a string to an exact width (right-justified). */
    public static String padLeft(String s, int width) {
        if (s.length() > width) return s.substring(0, width);
        return " ".repeat(width - s.length()) + s;
    }
}
