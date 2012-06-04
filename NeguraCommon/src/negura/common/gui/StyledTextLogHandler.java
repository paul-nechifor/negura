package negura.common.gui;

import java.util.logging.Handler;
import java.util.logging.LogRecord;
import negura.common.util.NeguraLog;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.ScrollBar;

/**
 * Outputs log messages to a StyledText widget.
 * @author Paul Nechifor
 */
public class StyledTextLogHandler extends Handler {
    private static final int UPDATE_INTERVAL = 200;

    private final StyledText styledText;
    private final Display display;
    private final StringBuffer buffer;

    private volatile boolean closed = false;

    private final Runnable updater = new Runnable() {
        @Override
        public void run() {
            updateAtInterval();
        }
    };
    
    public StyledTextLogHandler(StyledText styledText) {
        super();
        this.styledText = styledText;
        this.display = styledText.getDisplay();
        this.buffer = new StringBuffer();

        this.display.timerExec(UPDATE_INTERVAL, updater);
    }

    @Override
    public void publish(final LogRecord record) {
        synchronized (buffer) {
            buffer.append(NeguraLog.FORMATTER.format(record));
        }
    }

    @Override
    public void flush() {
        display.syncExec(updater);
    }

    @Override
    public void close() {
        closed = true;
    }

    private void updateAtInterval() {
        if (styledText.isDisposed())
            return;

        boolean bufferNotEmpty;

        synchronized (buffer) {
            bufferNotEmpty = buffer.length() > 0;
        }

        if (bufferNotEmpty) {
            String toAppend;

            synchronized (buffer) {
                toAppend = buffer.toString();
                buffer.delete(0, buffer.length());
            }

            boolean wasAtMaximum = isScrollBarAtMaximum();

            styledText.append(toAppend);

            // Scroll to bottom if the scroll bar was there before update.
            if (wasAtMaximum) {
                setScrollBarToMaximum();
            }
        }

        if (!closed) {
            display.timerExec(UPDATE_INTERVAL, updater);
        }
    }

    public boolean isScrollBarAtMaximum() {
        ScrollBar sb = styledText.getVerticalBar();
        Rectangle r = styledText.getBounds();

        return sb.getSelection() + r.height >= sb.getMaximum();
    }

    public void setScrollBarToMaximum() {
        styledText.setSelection(styledText.getCharCount());
    }
}