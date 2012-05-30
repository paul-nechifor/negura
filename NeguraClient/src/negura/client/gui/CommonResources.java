package negura.client.gui;

import java.util.HashMap;
import java.util.Map;
import negura.common.ex.NeguraError;
import negura.common.ex.NeguraRunEx;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Resource;
import org.eclipse.swt.widgets.Display;

/**
 *
 * @author Paul Nechifor
 */
public class CommonResources extends Resource {
    private static final String[] IMAGES = new String[] {
        "tray", "/res/icons/application_24.png",
        "exit", "/res/icons/exit_16.png",
        "application", "/res/icons/application_32.png"
    };

    private final Display display;
    private final Map<String, Image> images = new HashMap<String, Image>();

    private boolean disposed = false;

    public CommonResources(Display display) {
        this.display = display;

        Class<?> c = getClass();
        Image image;
        for (int i = 0; i < IMAGES.length; i += 2) {
            image = new Image(display, c.getResourceAsStream(IMAGES[i+1]));
            images.put(IMAGES[i], image);
        }
    }

    public final Image getImage(String key) {
        Image image = images.get(key);

        if (image == null) {
            throw new NeguraError("Image '%s' doesn't exist.", key);
        }
        
        return image;
    }

    @Override
    public final void dispose() {
        if (disposed) {
            throw new NeguraRunEx("Already disposed.");
        }

        for (Image image : images.values())
            image.dispose();

        disposed = true;
    }

    @Override
    public final Device getDevice() {
        return display;
    }

    @Override
    public final boolean isDisposed() {
        return disposed;
    }
}
