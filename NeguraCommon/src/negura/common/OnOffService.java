package negura.common;

import negura.common.ex.NeguraError;

/**
 * @author Paul Nechifor
 */
public abstract class OnOffService {
    private boolean isOn = false;

    public final void turnOn() {
        if (isOn)
            throw new NeguraError("Already on.");

        onTurnOn();
        isOn = true;
    }

    public final void turnOff() {
        if (!isOn)
            throw new NeguraError("Isn't on.");

        onTurnOff();
        isOn = false;
    }

    public boolean isOn() {
        return isOn;
    }

    protected abstract void onTurnOn();

    protected abstract void onTurnOff();
}
