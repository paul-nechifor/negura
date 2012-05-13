package negura.common.gui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Resource;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Slider;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Widget;

/**
 *
 * @author Paul Nechifor
 */
public class Swt {
    public static interface ManyToOne {
        public void connect(Label one, Control[] many);
    };

    public static final VerifyListener INTEGER_VERIFIER =
            new VerifyListener() {
        @Override
        public void verifyText(final VerifyEvent event) {
            switch (event.keyCode) {
                case 0:                // To allow setText()
                case SWT.BS:           // Backspace
                case SWT.DEL:          // Delete
                case SWT.HOME:         // Home
                case SWT.END:          // End
                case SWT.ARROW_LEFT:   // Left arrow
                case SWT.ARROW_RIGHT:  // Right arrow
                    return;
            }
            if (!Character.isDigit(event.character))
                event.doit = false;  // Disallow the action.
        }
    };

    private Swt() { }

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

    public static <T extends Control> T n(Class<T> type, Composite c, int i,
            String layoutData) {
        try {
            T t = type.getDeclaredConstructor(Composite.class, int.class)
                    .newInstance(c, i);
            if (layoutData != null)
                t.setLayoutData(layoutData);
            return t;
        } catch (Exception ex) { throw new AssertionError(); }
    }

    public static Label newLabel(Composite c, String layout, String text) {
        Label ret = n(Label.class, c, SWT.LEFT, layout);
        if (text != null)
            ret.setText(text);
        return ret;
    }

    public static Text newText(Composite c, String layout, String text) {
        Text ret = n(Text.class, c, SWT.BORDER, layout);
        if (text != null)
            ret.setText(text);
        return ret;
    }

    public static Text newMulti(Composite c, String layout, String text) {
        Text ret = n(Text.class, c, SWT.BORDER | SWT.MULTI | SWT.VERTICAL,
                layout);
        if (text != null)
            ret.setText(text);
        return ret;
    }

    public static Text newPassword(Composite c, String layout, String text) {
        Text ret = n(Text.class, c, SWT.BORDER | SWT.PASSWORD, layout);
        if (text != null)
            ret.setText(text);
        return ret;
    }

    public static Button newButton(Composite c, String layout, String text) {
        Button ret = n(Button.class, c, SWT.PUSH, layout);
        if (text != null)
            ret.setText(text);
        return ret;
    }

    public static Combo newCombo(Composite c, String layout,
            String[] items, int selected) {
        Combo ret = n(Combo.class, c, SWT.READ_ONLY, layout);
        if (items != null)
            ret.setItems(items);
        if (selected >= 0)
            ret.select(selected);
        return ret;
    }

    public static Slider newSlider(Composite c, String layout, int min,
            int max, int increment) {
        Slider ret = n(Slider.class, c, SWT.HORIZONTAL, layout);
        ret.setMinimum(min);
        ret.setMaximum(max);
        ret.setIncrement(increment);
        return ret;
    }

    public static void connect(final Text text, final Slider slider) {
        text.addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent me) {
                long textValue = 0;
                try {
                    textValue = Long.parseLong(text.getText());
                } catch (NumberFormatException ex) { }
                if (textValue > slider.getMaximum())
                    textValue = slider.getMaximum();
                if (slider.getSelection() != textValue)
                    slider.setSelection((int) textValue);
            }
        });

        slider.addListener(SWT.Selection, new Listener() {
            @Override
            public void handleEvent(Event e) {
                int sliderValue = slider.getSelection();
                int textValue = 0;
                try {
                    textValue = Integer.parseInt(text.getText());
                } catch (NumberFormatException ex) { }
                if (textValue != sliderValue)
                    text.setText(Integer.toString(sliderValue));
            }
        });
    }

    public static void tripleConnect(final Label label, final Combo a,
            final Text b, final ManyToOne mod) {
        ModifyListener m = new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent me) {
                mod.connect(label, new Control[]{ a, b });
            }
        };

        a.addModifyListener(m);
        b.addModifyListener(m);
    }

    public static void centerShell(Shell shell) {
        Rectangle bds = shell.getDisplay().getBounds();
        Point p = shell.getSize();
        int nLeft = (bds.width - p.x) / 2;
        int nTop = (bds.height - p.y) / 2;
        shell.setBounds(nLeft, nTop, p.x, p.y);
    }

    public static Font getMonospaceFont(Display display, int size) {
        String[] fontList = {"DejaVu Sans Mono", "Monospace",
            "Courier New", "Mono"};

        for (String fontName : fontList) {
            try {
                Font font = new Font(display, fontName, size, SWT.NONE);
                return font;
            } catch (SWTError ex) { }
        }
        return null;
    }

    public static void connectDispose(Widget onDisposal,
            final Resource toDispose) {
        onDisposal.addDisposeListener(new DisposeListener() {
            @Override
            public void widgetDisposed(DisposeEvent de) {
                toDispose.dispose();
            }
        });
    }
}
