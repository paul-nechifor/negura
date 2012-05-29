package negura.common.ex;

/**
 * General purpose runtime exception for this project.
 * @author Paul Nechifor
 */
public class NeguraRunEx extends RuntimeException {
    public NeguraRunEx(String message) {
        super(message);
    }

    public NeguraRunEx(String format, Object... args) {
        super(String.format(format, args));
    }
}
