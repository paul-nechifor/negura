package negura.common;

import negura.common.ex.NeguraError;

/**
 * @author Paul Nechifor
 */
public abstract class OnOffService {
    private boolean isOn = false;

    public final void turnOn() {
        if (isOn) {
            throw new NeguraError("Already on.");
        }
        isOn = true;
        turnedOn();
    }

    public final void turnOff() {
        if (!isOn) {
            throw new NeguraError("Isn't on.");
        }
        isOn = false;
        turnedOff();
    }

    public boolean isOn() {
        return isOn;
    }

    protected abstract void turnedOn();

    protected abstract void turnedOff();
}
