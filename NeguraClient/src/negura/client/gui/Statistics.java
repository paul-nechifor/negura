package negura.client.gui;

import java.util.HashMap;
import java.util.Map;
import negura.common.ex.NeguraError;
import negura.common.gui.Swt;
import negura.common.gui.Window;
import net.miginfocom.swt.MigLayout;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.ShellListener;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

/**
 *
 * @author Paul Nechifor
 */
public class Statistics extends Window {
    private final Map<String, Label> labels = new HashMap<String, Label>();

    private static enum LabelValue {
        NumberOfBlocks("Number of blocks:"),
        AllocatedBlocks("Allocated so far:"),
        DownloadedBlocks("Downloaded:"),
        CsDownloaded("Downloaded:"),
        CsUploaded("Uploaded:"),
        CsTime("Time:"),
        CsAvgDown("Average download speed:"),
        CsAvgUp("Average upload speed:"),
        AtDownloaded("Downloaded:"),
        AtUploaded("Uploaded:"),
        AtTime("Time:"),
        AtAvgDown("Average download speed:"),
        AtAvgUp("Average upload speed:"),
        RequestThreads("Request threads:");

        private LabelValue(String description) {
            this.description = description;
        }

        public String description;
        public Label label;
    }

    public Statistics(Display display, CommonResources resources) {
        super(new Shell(display));
        shell.setText("Statistics");
        shell.setSize(840, 650);
        shell.setLayout(new MigLayout("insets 10", "[grow][grow][grow]",
                "[top]"));
        shell.setImage(resources.getImage("application"));

        Group blocksG = Swt.newGroup(shell, "w max", "Blocks");
        Group currentSessionG = Swt.newGroup(shell,
                "w max, spany 2", "Current Session");
        Group allTimeG = Swt.newGroup(shell,
                "w max, spany 2, wrap", "All time");
        Group othersG = Swt.newGroup(shell,
                "w max, wrap", "Others");
        Group traficChartG = Swt.newGroup(shell,
                "w max, h max, span", "Trafic chart");

        fillGroupWithLabels(blocksG,
            LabelValue.NumberOfBlocks,
            LabelValue.AllocatedBlocks,
            LabelValue.DownloadedBlocks
        );

        fillGroupWithLabels(currentSessionG,
            LabelValue.CsDownloaded,
            LabelValue.CsUploaded,
            LabelValue.CsTime,
            LabelValue.CsAvgDown,
            LabelValue.CsAvgUp
        );

        fillGroupWithLabels(allTimeG,
            LabelValue.AtDownloaded,
            LabelValue.AtUploaded,
            LabelValue.AtTime,
            LabelValue.AtAvgDown,
            LabelValue.AtAvgUp
        );

        fillGroupWithLabels(othersG,
            LabelValue.RequestThreads
        );

        LabelValue.DownloadedBlocks.label.setText("1000");

        addCallDisposeOnClose();

        shell.open();
    }

    private void fillGroupWithLabels(Group group, LabelValue... labelValues) {
        group.setLayout(new MigLayout("insets 5", "[left][fill]"));

        for (LabelValue labelValue: labelValues) {
            Swt.newLabel(group, null, labelValue.description);

            if (labelValue.label != null) {
                throw new NeguraError("Already used it.");
            }

            labelValue.label = Swt.newLabel(group, "wrap", null);
        }
    }

    private void addCallDisposeOnClose() {
        shell.addShellListener(new ShellListener() {
            public void shellClosed(ShellEvent se) {
                dispose();
            }
            public void shellActivated(ShellEvent se) { }
            public void shellDeactivated(ShellEvent se) { }
            public void shellIconified(ShellEvent se) { }
            public void shellDeiconified(ShellEvent se) { }
        });

        shell.open();
    }
}
