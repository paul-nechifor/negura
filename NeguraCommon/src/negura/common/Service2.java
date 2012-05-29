package negura.common;

import negura.common.ex.NeguraError;

/**
 *
 * @author Paul Nechifor
 */
public abstract class Service2 {
    private boolean started = false;

    public void start() {
        if (started) {
            throw new NeguraError("Already started.");
        }
    }

    public void stop() {
        if (!started) {
            throw new NeguraError("Hasn't been started.");
        }
    }
}
