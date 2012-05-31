package negura.client.gui;

import negura.common.data.TrafficLogger;
import negura.common.data.TrafficLogger.Record;
import negura.common.gui.Swt;
import negura.common.util.Util;
import negura.common.util.Util.Multiplier;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;


/**
 * @author Paul Nechifor
 */
public class ChartWidget extends Canvas {
    private final Font unitFont;
    private final TrafficLogger trafficLogger;
    private final Record[] trafficRecords;

    public ChartWidget(Composite parent, TrafficLogger trafficLogger) {
        super(parent, SWT.NONE);

        this.trafficLogger = trafficLogger;
        this.trafficRecords = trafficLogger.getFilledArray();

        unitFont = Swt.getFontWithDifferentHeight(getDisplay(), getFont(), 8);
        setFont(unitFont);

        // Forwarding calls from listeners to private methods.
        addDisposeListener(new DisposeListener() {
            public void widgetDisposed(DisposeEvent e) {
                ChartWidget.this.widgetDisposed();
            }
        });
        addPaintListener(new PaintListener() {
            public void paintControl(PaintEvent e) {
                ChartWidget.this.paintControl(e);
            }
        });
    }

    private void widgetDisposed() {
        unitFont.dispose();
    }

    private void paintControl(PaintEvent e) {
        Rectangle bounds = this.getBounds();
        Rectangle size = new Rectangle(0, 0, bounds.width-2, bounds.height-2);

        GC gc = e.gc;
        gc.setAntialias(SWT.ON);

        trafficLogger.copyToArray(trafficRecords);
        
        long[] hd = calculateHorizontalDivision();
        long maximumRounded = hd[0];
        int horizDivisions = (int) hd[1];
        long incrementSpeed = maximumRounded / horizDivisions;

        String[] units = new String[horizDivisions];
        int maxStringExitent = 0;
        Point p;

        for (int i = 0; i < horizDivisions; i++) {
            units[i] = Util.bytesWithUnit(incrementSpeed*(horizDivisions-i), 1);
            p = gc.stringExtent(units[i]);
            if (p.x > maxStringExitent)
                maxStringExitent = p.x;
        }

        int leftSpace = maxStringExitent;
        int downSpace = 20;
        Rectangle grid = new Rectangle(
            leftSpace,
            0,
            size.width - leftSpace,
            size.height - downSpace
        );
        int incrementGrid = grid.height / horizDivisions;

        for (int i = 0; i < horizDivisions; i++) {
            gc.drawText(units[i], 0, incrementGrid * i);
        }

        drawGrid(gc, grid, 10, horizDivisions);
        drawGraph(gc, grid, maximumRounded);
    }

    private long[] calculateHorizontalDivision() {
        long max = 0;

        for (int i = 0; i < trafficRecords.length; i++) {
            if (trafficRecords[i].download > max)
                max = trafficRecords[i].download;
            if (trafficRecords[i].upload > max)
                max = trafficRecords[i].upload;
        }

        // If smaller than 20 KiB don't divide further.
        long kib20 = 20 * 1024;
        if (max < kib20) {
            return new long[] {kib20, 4};
        }

        Multiplier m = Util.bytesWithoutUnit(max);

        // If it's bigger than 1000 in whatever unit, the unit will affected.
        if (m.size > 1000.0) {
            return new long[] {2048 * m.unit, 4};
        }

        double firstDigit = m.size;
        long rounded = 1;
        while (firstDigit >= 10.0) {
            firstDigit /= 10.0;
            rounded *= 10;
        }

        if (firstDigit > 8.0) {
            return new long[] {m.unit * rounded * 10, 5};
        } else if (firstDigit > 6.0) {
            return new long[] {m.unit * rounded * 8, 4};
        } else if (firstDigit > 5.0) {
            return new long[] {m.unit * rounded * 6, 6};
        } else if (firstDigit > 4.0) {
            return new long[] {m.unit * rounded * 5, 5};
        } else if (firstDigit > 3.0) {
            return new long[] {m.unit * rounded * 4, 4};
        } else if (firstDigit > 2.0) {
            return new long[] {m.unit * rounded * 3, 6};
        } else if (firstDigit > 1.0) {
            return new long[] {m.unit * rounded * 2, 4};
        } else {
            return new long[] {m.unit * rounded, 5};
        }
    }

    private void drawGrid(GC gc, Rectangle r, int divX, int divY) {
        gc.setBackground(getDisplay().getSystemColor(
                SWT.COLOR_LIST_BACKGROUND));
        gc.fillRectangle(r);

        gc.setForeground( getDisplay().getSystemColor(
                SWT.COLOR_WIDGET_NORMAL_SHADOW));

        double divYSize = ((double)r.height) / divY;
        int y;
        for (int i = 0; i <= divY; i++) {
            y = r.y + (int) (i * divYSize);
            gc.drawLine(r.x, y, r.x + r.width, y);
        }

        double divXSize = ((double)r.width) / divX;
        int x;
        for (int i = 0; i <= divX; i++) {
            x = r.x + (int) (i * divXSize);
            gc.drawLine(x, r.y, x, r.y + r.height);
        }
    }

    private void drawGraph(GC gc, Rectangle r, long max) {
        double increment = ((double) r.width) / (trafficRecords.length - 1);
        double height = r.height;

        gc.setForeground(getDisplay().getSystemColor(SWT.COLOR_RED));

        for (int i = 1; i < trafficRecords.length; i++) {
            gc.drawLine(
                r.x + (int)((i-1) * increment),
                r.height-r.y-(int)((trafficRecords[i-1].upload*height)/max),
                r.x + (int)(i * increment),
                r.height-r.y-(int)((trafficRecords[i].upload*height)/max)
            );
        }

        gc.setForeground(getDisplay().getSystemColor(SWT.COLOR_GREEN));

        for (int i = 1; i < trafficRecords.length; i++) {
            gc.drawLine(
                r.x + (int)((i-1) * increment),
                r.height-r.y-(int)((trafficRecords[i-1].download*height)/max),
                r.x + (int)(i * increment),
                r.height-r.y-(int)((trafficRecords[i].download*height)/max)
            );
        }
    }
}
