package negura.server.gui;

import java.sql.SQLException;
import java.util.List;
import negura.common.data.BlockInfo;
import negura.common.gui.Swt;
import negura.common.util.NeguraLog;
import negura.server.DataManager;
import negura.server.ServerConfigManager;
import negura.server.gui.BlocksCanvas.Type;
import net.miginfocom.swt.MigLayout;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.TabItem;

/**
 * Shows block information with a BlockCanvas.
 * @author Paul Nechifor
 */
public class BlocksTab {
    private final DataManager dataManager;
    private final BlocksCanvas blocksCanvas;
    private final Label infoL;
    private final Button loadB;
    private final Label pageL;
    private Type type = Type.ALLOCATED;

    public BlocksTab(TabItem tabItem, ServerConfigManager cm) {
        dataManager = cm.getNeguraServer().getDataManager();

        Composite composite = new Composite(tabItem.getParent(), SWT.NONE);
        tabItem.setControl(composite);

        composite.setLayout(new MigLayout("insets 2, gap 2! 2!",
                "[100::, fill][][][][grow, fill][100::, fill][100::, fill]"));

        infoL = Swt.newLabel(composite, "span, w max, wrap", null);

        blocksCanvas = new BlocksCanvas(composite, this);
        blocksCanvas.setLayoutData("span, w max, h max, wrap");
        
        loadB = Swt.newButton(composite, null, "Load");
        Swt.newLabel(composite, null, "Show:");
        Button[] radioB = Swt.newRadioButton(composite, null,
                new String[]{"allocated", "completed"});
        pageL = Swt.newRLabel(composite, null, null);
        Button prevB = Swt.newButton(composite, null, "Previous");
        Button nextB = Swt.newButton(composite, null, "Next");

        // Options.
        radioB[0].setSelection(true);

        loadB.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent se) {
                load();
            }
        });
        radioB[0].addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent se) {
                type = Type.ALLOCATED;
                blocksCanvas.showType(type);
            }
        });
        radioB[1].addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent se) {
                type = Type.COMPLETED;
                blocksCanvas.showType(type);
            }
        });
        prevB.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent se) {
                blocksCanvas.previousPage();
            }
        });
        nextB.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent se) {
                blocksCanvas.nextPage();
            }
        });
    }

    /**
     * Called by the canvas to announce the current page of the total.
     * @param current
     * @param total
     */
    public void changedPage(int current, int total) {
        pageL.setText(String.format("Page %d/%d", current, total));
    }

    public void blockActive(BlockInfo info) {
        if (info == null) {
            infoL.setText("");
            return;
        }
        
        String format = "Block id: %d  allocated: %d  completed: %d  file: %d";
        infoL.setText(String.format(format, info.bid, info.allocated,
                info.completed, info.oid));
    }

    private void load() {
        loadB.setEnabled(false);

        try {
            List<BlockInfo> blocks = dataManager.getBlockStatistics();
            blocksCanvas.loadBlocks(blocks, type);
        } catch (SQLException ex) {
            NeguraLog.warning(ex, "Error loading block statistics.");
        }

        loadB.setEnabled(true);
    }
}
