package negura.server.gui;

import java.util.List;
import negura.common.data.BlockInfo;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

/**
 * A widget for visualizing blocks and their relative allocation and
 * allocation completeness.
 * @author Paul Nechifor
 */
public class BlocksCanvas extends Canvas {
    public static final int BLOCKSIZE = 10;
    public static final int COLORS = 10;

    public static enum Type {
        ALLOCATED, COMPLETED
    }

    private final BlocksTab blocksTab;
    private final Display display;
    private final Color[] colors;

    // Change with setting new blocks.
    private List<BlockInfo> blocks;
    private Type type;
    private int pages = 0;
    private byte[] allocatedColors;
    private byte[] completedColors;

    // Change on setting new page or resizing.
    private int currentPage; // Starts at 0.
    private int width;
    private int height;
    private int horizBlocks;
    private int vertBlocks;
    private int blocksPerPage;

    public BlocksCanvas(Composite parent, BlocksTab blocksTab) {
        super(parent, SWT.NONE);

        this.blocksTab = blocksTab;
        this.display = parent.getDisplay();
        this.colors = generateColors();

        // Forwarding calls from listeners to private methods.
        addDisposeListener(new DisposeListener() {
            public void widgetDisposed(DisposeEvent e) {
                BlocksCanvas.this.widgetDisposed();
            }
        });
        addPaintListener(new PaintListener() {
            public void paintControl(PaintEvent e) {
                BlocksCanvas.this.paintControl(e);
            }
        });
        addMouseMoveListener(new MouseMoveListener() {
            public void mouseMove(MouseEvent e) {
                BlocksCanvas.this.mouseMove(e);
            }
        });
    }

    public synchronized void loadBlocks(List<BlockInfo> blocks, Type type) {
        this.blocks = blocks;
        this.type = type;

        int minAllocated = Integer.MAX_VALUE;
        int maxAllocated = 0;
        int minCompleted = Integer.MAX_VALUE;
        int maxCompleted = 0;

        for (BlockInfo block : blocks) {
            if (block.allocated < minAllocated)
                minAllocated = block.allocated;
            if (block.allocated > maxAllocated)
                maxAllocated = block.allocated;
            if (block.completed < minCompleted)
                minCompleted = block.completed;
            if (block.completed > maxCompleted)
                maxCompleted = block.completed;
        }

        int diffAllocated = maxAllocated - minAllocated;
        if (diffAllocated == 0)
            diffAllocated = 1; // So that division by 0 doesn't happen.

        int diffCompleted = maxCompleted - minCompleted;
        if (diffCompleted == 0)
            diffCompleted = 1; // So that division by 0 doesn't happen.

        allocatedColors = new byte[this.blocks.size()];
        completedColors = new byte[this.blocks.size()];

        int val;

        for (int i = 0; i < allocatedColors.length; i++) {
            val = blocks.get(i).allocated - minAllocated;
            allocatedColors[i] = (byte)((val * (COLORS-1)) / diffAllocated);
        }

        for (int i = 0; i < allocatedColors.length; i++) {
            val = blocks.get(i).completed - minCompleted;
            completedColors[i] = (byte)((val * (COLORS-1)) / diffCompleted);
        }

        recomputePages();

        redraw();
    }

    public synchronized void showType(Type type) {
        if (type != this.type) {
            this.type = type;
            redraw();
        }
    }

    public synchronized void nextPage() {
        if (currentPage + 1 < pages) {
            currentPage++;
            redraw();
            blocksTab.changedPage(currentPage + 1, pages);
        }
    }

    public synchronized void previousPage() {
        if (currentPage > 0) {
            currentPage--;
            redraw();
            blocksTab.changedPage(currentPage + 1, pages);
        }
    }

    private Color[] generateColors() {
        Color[] ret = new Color[COLORS];

        double startColor = 200.0;
        double endColor = 50.0;
        double inc = (startColor - endColor) / (COLORS - 1);
        int color;

        for (int i = 0; i < COLORS; i++) {
            color = (int)(startColor - i * inc);
            ret[i] = new Color(display, color, color, color);
        }

        return ret;
    }

    private synchronized void recomputePages() {
        Rectangle bounds = getBounds();
        width = bounds.width - 1;
        height = bounds.height - 1;

        horizBlocks = (-2 + width) / BLOCKSIZE;
        vertBlocks = (-2 + height) / BLOCKSIZE;
        blocksPerPage = horizBlocks * vertBlocks;

        pages = (int)Math.ceil(blocks.size() / (double)blocksPerPage);
        currentPage = 0;

        blocksTab.changedPage(currentPage + 1, pages);
    }

    private synchronized void widgetDisposed() {
        for (Color color : colors)
            color.dispose();
    }

    private synchronized void paintControl(PaintEvent e) {
        if (blocks == null)
            return;

        GC gc = e.gc;

        Rectangle bounds = getBounds();
        if ((bounds.width-1) != width || (bounds.height-1) != height)
            recomputePages();
        
        gc.setForeground(display.getSystemColor(
                SWT.COLOR_WIDGET_NORMAL_SHADOW));
        gc.drawRectangle(0, 0, width, height);
        
        gc.setBackground(display.getSystemColor(SWT.COLOR_GREEN));

        int start = currentPage * blocksPerPage;
        int end = (currentPage + 1) * blocksPerPage;
        if (end > blocks.size())
            end = blocks.size();

        byte[] colorIndexes;
        if (type == Type.ALLOCATED) {
            colorIndexes = allocatedColors;
        } else if (type == Type.COMPLETED) {
            colorIndexes = completedColors;
        } else throw new AssertionError();

        Rectangle r = new Rectangle(0, 0, BLOCKSIZE-1, BLOCKSIZE-1);
        int x = 0;
        int y = 0;

        for (int i = start; i < end; i++) {
            gc.setBackground(colors[colorIndexes[i]]);
            r.x = 2 + x * BLOCKSIZE;
            r.y = 2 + y * BLOCKSIZE;
            gc.fillRectangle(r);

            x++;
            if (x == horizBlocks) {
                x = 0;
                y++;
            }
        }
    }

    private synchronized void mouseMove(MouseEvent e) {
        if (blocks == null)
            return;

        int row = (e.y - 2) / BLOCKSIZE;
        int col = (e.x - 2) / BLOCKSIZE;
        int index = currentPage * blocksPerPage + row * horizBlocks + col;

        if (index >= blocks.size()) {
            blocksTab.blockActive(null);
        } else {
            blocksTab.blockActive(blocks.get(index));
        }
    }
}
