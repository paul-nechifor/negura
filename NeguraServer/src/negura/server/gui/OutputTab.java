package negura.server.gui;

import negura.common.gui.StyledTextLogHandler;
import negura.common.gui.Swt;
import negura.common.util.NeguraLog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.TabItem;

/**
 * Displays the log of the server to the screen.
 * @author Paul Nechifor
 */
public class OutputTab {
    private final Font smallFont;
    private final StyledTextLogHandler styledTextLogHandler;

    public OutputTab(TabItem tabItem) {
        Composite composite = new Composite(tabItem.getParent(), SWT.NONE);
        composite.setLayout(Swt.singletonLayout(2, 2));
        tabItem.setControl(composite);

        smallFont = Swt.getMonospacedFont(tabItem.getDisplay(), 8);

        StyledText styledText = new StyledText(composite, SWT.BORDER |
                SWT.V_SCROLL | SWT.MULTI | SWT.WRAP);
        styledText.setEditable(false);
        styledText.setFont(smallFont);

        styledTextLogHandler = new StyledTextLogHandler(styledText);
        NeguraLog.addHandler(styledTextLogHandler);

        composite.addDisposeListener(new DisposeListener() {
            public void widgetDisposed(DisposeEvent de) {
                dispose();
            }
        });
    }

    private void dispose() {
        smallFont.dispose();

        // It might not have been added yet.
        if (styledTextLogHandler != null) {
            NeguraLog.removeHandler(styledTextLogHandler);
            styledTextLogHandler.close();
        }
    }
}
