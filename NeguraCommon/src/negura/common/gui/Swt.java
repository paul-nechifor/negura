package negura.common.gui;

import java.security.InvalidParameterException;
import negura.common.util.NeguraLog;
import negura.common.util.Util;
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
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Scale;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Widget;

/**
 * SWT static utility methods.
 * @author Paul Nechifor
 */
public class Swt {
    /**
     * An interface for supplying the action which should be executed for the
     * {@link Swt#connectTo(Mod, Widget, Widget...) connectTo} method.
     */
    public static interface Mod {
        public void modify(Widget to, Widget... from);
    }

    /**
     * Updates the text when the scale is changed.
     */
    public static final Mod TEXT_FROM_SCALE = new Mod() {
        @Override
        public void modify(Widget to, Widget... from) {
            if (from.length != 1 || !(from[0] instanceof Scale))
                throw new InvalidParameterException("from isn't Scale");
            if (!(to instanceof Text))
                throw new InvalidParameterException("to isn't Text");

            Scale scale = (Scale) from[0];
            Text text = (Text) to;
            int scaleValue = scale.getSelection();
            long textValue = Util.parseLongOrZero(text.getText());

            if (textValue != scaleValue)
                text.setText(Integer.toString(scaleValue));
        }
    };

    /**
     * Updates the scale selection when the text changes.
     */
    public static final Mod SCALE_FROM_TEXT = new Mod() {
        @Override
        public void modify(Widget to, Widget... from) {
            if (from.length != 1 || !(from[0] instanceof Text))
                throw new InvalidParameterException("from isn't Text");
            if (!(to instanceof Scale))
                throw new InvalidParameterException("to isn't Scale");

            Scale scale = (Scale) to;
            Text text = (Text) from[0];
            int scaleValue = scale.getSelection();
            int textValue = Util.parseIntOrZero(text.getText());

            if (textValue != scaleValue)
                scale.setSelection(textValue);
        }
    };

    /**
     * A verifier which permits only integer numbers of unlimited size.
     */
    public static final VerifyListener INTEGER_VERIFIER =
            new VerifyListener() {
        @Override
        public void verifyText(final VerifyEvent event) {
            switch (event.keyCode) {
                case 0:                // To allow setText() TODO: debug this.
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

    private static <T extends Control> T n(Class<T> type, Composite c, int i,
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

    public static Label newRLabel(Composite c, String layout, String text) {
        Label ret = n(Label.class, c, SWT.RIGHT, layout);
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

    public static Button[] newRadioButton(Composite c, String layout,
            String[] texts) {
        Button[] ret = new Button[texts.length];

        for (int i = 0; i < texts.length; i++) {
            ret[i] = n(Button.class, c, SWT.RADIO, layout);
            ret[i].setText(texts[i]);
        }

        return ret;
    }

    public static Button newCheckBox(Composite c, String layout, String text) {
        Button ret = n(Button.class, c, SWT.CHECK, layout);
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

    public static Scale newHScale(Composite c, String layout, int min,
            int max, int increment) {
        Scale ret = n(Scale.class, c, SWT.HORIZONTAL, layout);
        // The maximum has to be set first because setting the minimum checks to
        // see that it isn't greater than the maximum.
        ret.setMaximum(max);
        ret.setMinimum(min);
        ret.setIncrement(increment);
        return ret;
    }

    public static ProgressBar newHProgressBar(Composite c, String layout,
            int min, int max) {
        ProgressBar ret = n(ProgressBar.class, c, SWT.HORIZONTAL, layout);
        ret.setMaximum(max);
        ret.setMinimum(min);
        return ret;
    }

    public static Group newGroup(Composite c, String layout, String text) {
        Group ret = n(Group.class, c, SWT.NONE, layout);
        if (text != null)
            ret.setText(text);
        return ret;
    }

    public static TabFolder newTabForder(Composite c, String layout,
            String[] tabNames) {
        TabFolder ret = n(TabFolder.class, c, SWT.BORDER, layout);
        
        if (tabNames != null && tabNames.length > 0) {
            for (String tabName : tabNames) {
                TabItem item = new TabItem(ret, SWT.NONE);
                item.setText(tabName);
            }
        }

        return ret;
    }

    public static Table newTable(Composite c, String layout,
            String[] columnNames) {
        Table ret = n(Table.class, c, SWT.FULL_SELECTION | SWT.BORDER, layout);
        ret.setLinesVisible (true);
        ret.setHeaderVisible (true);

        for (String columnName : columnNames) {
            TableColumn column = new TableColumn(ret, SWT.NONE);
            column.setText(columnName);
        }

        return ret;
    }

    public static FillLayout singletonLayout(int marginHoriz, int marginVert) {
        FillLayout ret = new FillLayout();
        ret.marginWidth = marginHoriz;
        ret.marginHeight = marginVert;
        return ret;
    }

    /**
     * When any of the widgets in the <code>froms</code> parameter are updated
     * the <code>to</code> parameter is modified according the action specified
     * by <code>mod</code>.
     * @param mod       The action which modifies <code>to</code>.
     * @param to        The widget which will be modified.
     * @param froms     The widgets which will generate the change events.
     */
    public static void connectTo(final Mod mod, final Widget to,
            final Widget... froms) {
        ModifyListener ml = new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent me) {
                mod.modify(to, froms);
            }
        };

        Listener l = new Listener() {
            @Override
            public void handleEvent(Event event) {
                mod.modify(to, froms);
            }
        };

        for (Widget from : froms) {
            if (from instanceof Text) {
                ((Text) from).addModifyListener(ml);
            } else if (from instanceof Combo) {
                ((Combo) from).addModifyListener(ml);
            } else if (from instanceof Scale) {
                ((Scale) from).addListener(SWT.Selection, l);
            } else throw new AssertionError("Not supported... yet.");
        }
    }

    /**
     * Centers the shell on the middle of the screen.
     * @param shell     The shell to be centered.
     */
    public static void centerShell(Shell shell) {
        Rectangle bds = shell.getDisplay().getBounds();
        Point p = shell.getSize();
        int nLeft = (bds.width - p.x) / 2;
        int nTop = (bds.height - p.y) / 2;
        shell.setBounds(nLeft, nTop, p.x, p.y);
    }

    /**
     * Returns a monospaced font for this system or <code>null</code> on
     * failure.
     * @param display       The display.
     * @param height        The height of the font.
     * @return              A monospaced font which is available on this system.
     */
    public static Font getMonospacedFont(Display display, int height) {
        String[] fontList = {"DejaVu Sans Mono", "Monospace",
            "Courier New", "Mono"};

        for (String fontName : fontList) {
            try {
                Font font = new Font(display, fontName, height, SWT.NONE);
                return font;
            } catch (SWTError ex) {
                NeguraLog.warning(ex);
            }
        }
        return null;
    }

    /**
     * Returns the same font with a different height. You have to dispose of it.
     * @param display       The display.
     * @param font          The original font.
     * @param newHeight     The new height for the font.
     * @return              The modified font.
     */
    public static Font getFontWithDifferentHeight(Display display, Font font,
            int newHeight) {
        FontData[] fontData = font.getFontData();
        for (int i = 0; i < fontData.length; ++i)
            fontData[i].setHeight(newHeight);
        return new Font(display, fontData);
    }

    /**
     * Listens for the disposal of <code>onDisposal</code> and disposes the
     * resource <code>toDispose</code> when that happens.
     * @param onDisposal    The widget to listen on.
     * @param toDispose     The Resource to dispose.
     */
    public static void connectDisposal(Widget onDisposal,
            final Resource toDispose) {
        onDisposal.addDisposeListener(new DisposeListener() {
            @Override
            public void widgetDisposed(DisposeEvent de) {
                toDispose.dispose();
            }
        });
    }

    /**
     * This method returns when the shell is disposed.
     * @param display       The display of the shell.
     * @param shell         The shell for which to wait.
     */
    public static void loopUntilClosed(Display display, Shell shell) {
        while (!shell.isDisposed())
            if (!display.readAndDispatch())
                display.sleep();
        display.dispose();
    }
}
