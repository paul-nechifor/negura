package negura.client;

import java.util.ResourceBundle;

/**
 * Static class for getting internationalized strings.
 * @author Paul Nechifor
 */
public final class I18n {
    private static final ResourceBundle BUNDLE =
            ResourceBundle.getBundle("res.bundle.Texts");

    private I18n() { }

    /**
     * Gets the value of an internationalized string item.
     * @param key       The key of the item.
     * @return          The internationalized string item.
     */
    public static String get(String key) {
        return BUNDLE.getString(key);
    }

    /**
     * Convenience method for returning the formatted version of an item.
     * @param key       The key of the bundle item.
     * @param args      The formatting arguments.
     * @return          The formatted bundle string.
     */
    public static String format(String key, Object... args) {
        return String.format(BUNDLE.getString(key), args);
    }
}
