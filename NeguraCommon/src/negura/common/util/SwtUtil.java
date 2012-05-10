package negura.common.util;

import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;

/**
 *
 * @author Paul Nechifor
 */
public class SwtUtil {
    private SwtUtil() { }

    /**
     * Changes the font size of a control.
     * @param control
     * @param display
     * @param newSize
     */
    public static void changeControlFontSize(Control control, Display display,
            int newSize) {
        FontData[] fontData = control.getFont().getFontData();
        for (int i = 0; i < fontData.length; ++i)
            fontData[i].setHeight(newSize);
        final Font newFont = new Font(display, fontData);
        control.setFont(newFont);

        // Since you created the font, you must dispose it
        control.addDisposeListener(new DisposeListener() {
            public void widgetDisposed(DisposeEvent e) {
                newFont.dispose();
            }
        });
    }
}
