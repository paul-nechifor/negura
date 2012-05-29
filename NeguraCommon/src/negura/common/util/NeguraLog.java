package negura.common.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * The static class which logs all the messages of the program. At startup, the
 * default console handler and formatter are added. All severe mathods will
 * result in application exit.
 * @author Paul Nechifor
 */
public class NeguraLog {
    private static final Logger LOGGER = Logger.getLogger("Negura");
    private static boolean IS_CONSOLE_HANDLER_SET = false;
    private static final ConsoleHandler CONSOLE_HANDLER = new ConsoleHandler();
    public static final Formatter FORMATTER = new Formatter() {
        @Override
        public String format(LogRecord record) {
            String time = new SimpleDateFormat("HH:mm:ss")
                    .format(new Date(record.getMillis()));

            StringBuilder builder = new StringBuilder();
            builder.append(time).append(' ').append(record.getLevel());

            if (record.getMessage() != null) {
                builder.append("  ").append(record.getMessage());
            }

            if (record.getThrown() != null) {
                builder.append(' ').append(
                        Util.getStackTrace(record.getThrown()));
            }

            return builder.append('\n').toString();
        }
    };

    private NeguraLog() { }

    static {
        // Disable the default handler which prints to the console.
        LOGGER.setUseParentHandlers(false);
        // Add the custom handler.
        CONSOLE_HANDLER.setFormatter(FORMATTER);
        addDefaultConsoleHandler();
    }

    public static void flushAll() {
        for (Handler h : LOGGER.getHandlers())
            h.flush();
    }

    public static void addHandler(Handler handler) {
        LOGGER.addHandler(handler);
    }

    public static void removeHandler(Handler handler) {
        LOGGER.removeHandler(handler);
    }

    /**
     * Adds the default console handler if it isn't set.
     */
    public static void addDefaultConsoleHandler() {
        if (!IS_CONSOLE_HANDLER_SET) {
            LOGGER.addHandler(CONSOLE_HANDLER);
            IS_CONSOLE_HANDLER_SET = true;
        }
    }

    /**
     * Remove the default console handler if it's set.
     */
    public static void removeDefaultConsoleHandler() {
        if (IS_CONSOLE_HANDLER_SET){
            LOGGER.removeHandler(CONSOLE_HANDLER);
            IS_CONSOLE_HANDLER_SET = false;
        }
    }

    private static void log(Level level, String message, Throwable throwable) {
        LOGGER.log(level, message, throwable);

        if (level == Level.SEVERE) {
            flushAll();
            System.exit(1);
        }
    }

    public static void info(String message) {
        log(Level.INFO, message, null);
    }

    public static void info(String format, Object... args) {
        log(Level.INFO, String.format(format, args), null);
    }

    public static void warning(Throwable throwable) {
        log(Level.WARNING, null, throwable);
    }

    public static void warning(String message) {
        log(Level.WARNING, message, null);
    }

    public static void warning(Throwable throwable, String message) {
        log(Level.WARNING, message, throwable);
    }

    public static void warning(String format, Object... args) {
        log(Level.WARNING, String.format(format, args), null);
    }

    public static void warning(Throwable thr, String format, Object... args) {
        log(Level.WARNING, String.format(format, args), thr);
    }

    public static void severe(Throwable throwable) {
        log(Level.SEVERE, null, throwable);
    }

    public static void severe(String message) {
        log(Level.SEVERE, message, null);
    }

    public static void severe(Throwable throwable, String message) {
        log(Level.SEVERE, message, throwable);
    }

    public static void severe(String format, Object... args) {
        log(Level.SEVERE, String.format(format, args), null);
    }

    public static void severe(Throwable thr, String format, Object... args) {
        log(Level.SEVERE, String.format(format, args), thr);
    }
}
