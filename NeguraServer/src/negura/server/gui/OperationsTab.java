package negura.server.gui;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import negura.common.data.Operation;
import negura.common.gui.Swt;
import negura.common.util.NeguraLog;
import negura.common.util.Util;
import negura.server.DataManager;
import negura.server.ServerConfigManager;
import net.miginfocom.swt.MigLayout;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;

/**
 * Lists the operations of the file system.
 * @author Paul Nechifor
 */
public class OperationsTab {
    private final DataManager dataManager;
    private final Table table;
    private final SimpleDateFormat dateFormat
            = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");;

    public OperationsTab(TabItem tabItem, ServerConfigManager cm) {
        dataManager = cm.getNeguraServer().getDataManager();

        Composite composite = new Composite(tabItem.getParent(), SWT.NONE);
        tabItem.setControl(composite);

        composite.setLayout(new MigLayout("insets 2, gap 2! 2!",
                "[100::, fill][]", "[]"));

        String[] names = {"ID", "Type", "Date", "Size", "Path", "New path"};
        table = Swt.newTable(composite, "span, w max, hmin 0, h max", names);

        final Button loadB = Swt.newButton(composite, null, "Load");

        loadB.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent se) {
                loadB.setEnabled(false);
                load();
                loadB.setEnabled(true);
            }
        });

        packTable();
    }

    private void load() {
        List<Operation> ops = null;

        try {
            ops = dataManager.getOperationsAfter(0);
        } catch (SQLException ex) {
            NeguraLog.severe(ex);
            return;
        }

        table.removeAll();

        for (Operation o : ops) {
            TableItem item = new TableItem (table, SWT.NONE);
            item.setText(0, Integer.toString(o.oid));
            item.setText(1, o.type);
            item.setText(2, dateFormat.format(new Date((long)o.date * 1000)));
            item.setText(3, Util.bytesWithUnit(o.size, 2));
            item.setText(4, o.path);
            item.setText(5, o.newPath == null ? "" : o.newPath);
        }

        packTable();
    }

    private void packTable() {
        for (int i=0; i<6; i++) {
            table.getColumn(i).pack();
        }
    }
}
