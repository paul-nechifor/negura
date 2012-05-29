package negura.common.ex;

/**
 * General purpose error class for this project.
 * @author Paul Nechifor
 */
public class NeguraError extends Error {
    public NeguraError(String message) {
        super(message);
    }

    public NeguraError(String format, Object... args) {
        super(String.format(format, args));
    }
}
