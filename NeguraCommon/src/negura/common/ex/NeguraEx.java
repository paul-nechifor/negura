package negura.common.ex;

/**
 * General purpose checked exception for this project.
 * @author Paul Nechifor
 */
public class NeguraEx extends Exception {
    public NeguraEx(String message) {
        super(message);
    }

    public NeguraEx(String format, Object... args) {
        super(String.format(format, args));
    }
}
