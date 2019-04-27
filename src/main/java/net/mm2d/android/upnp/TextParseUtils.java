package net.mm2d.android.upnp;

public class TextParseUtils {
    public static int parseIntSafely(
            final String string,
            final int defaultValue) {
        try {
            return Integer.parseInt(string);
        } catch (final NumberFormatException ignored) {
        }
        return defaultValue;
    }
}
