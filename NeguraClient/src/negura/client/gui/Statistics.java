package negura.client.gui;

import negura.common.util.Util;
import negura.client.ClientConfigManager;
import negura.client.Negura;
import negura.common.data.BlockList;
import negura.common.data.TrafficAggregator;
import negura.common.data.TrafficLogger;
import negura.common.gui.Swt;
import negura.common.gui.Window;
import net.miginfocom.swt.MigLayout;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.ShellListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import static negura.client.gui.Statistics.LabelValue.*;

/**
 *
 * @author Paul Nechifor
 */
public class Statistics extends Window {
     static enum LabelValue {
        BlocksStored("To store:"),
        BlocksAllocated("Allocated:"),
        BlocksFinished("Finished:"),
        BlocksTemporary("Temporary:"),
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

        private String description;
        private Label label;

        private LabelValue(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
        
        public void setLabel(Label label) {
            this.label = label;
        }

        public void setText(String text) {
            label.setText(text);
        }

        public void setText(int text) {
            label.setText(Integer.toString(text));
        }
    }

    private static final int UPDATE_INTERVAL = 1000;

    private final ClientConfigManager cm;
    private final Display display;
    private final ChartCanvas chartWidget;
    private final BlockList blockList;
    private final Negura negura;
    private final TrafficAggregator trafficAggregator;
    private final TrafficLogger trafficLogger;
    private final Runnable callUpdateValues = new Runnable() {
        public void run() {
            updateValues();
        }
    };

    public Statistics(Display display, CommonResources resources,
            ClientConfigManager cm) {
        super(new Shell(display));
        this.cm = cm;
        this.display = display;

        blockList = cm.getBlockList();
        negura = cm.getNegura();
        trafficAggregator = cm.getTrafficAggregator();
        trafficLogger = negura.getTrafficLogger();

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
            BlocksStored,
            BlocksAllocated,
            BlocksFinished,
            BlocksTemporary
        );

        fillGroupWithLabels(currentSessionG,
            CsDownloaded,
            CsUploaded,
            CsTime,
            CsAvgDown,
            CsAvgUp
        );

        fillGroupWithLabels(allTimeG,
            AtDownloaded,
            AtUploaded,
            AtTime,
            AtAvgDown,
            AtAvgUp
        );

        fillGroupWithLabels(othersG,
            RequestThreads
        );

        traficChartG.setLayout(new FillLayout());
        chartWidget = new ChartCanvas(traficChartG,
                cm.getNegura().getTrafficLogger());

        addCallDisposeOnClose();

        updateValues();
        shell.open();
    }

    private void fillGroupWithLabels(Group group, LabelValue... labelValues) {
        group.setLayout(new MigLayout("insets 5", "[left][100::, fill]"));

        for (LabelValue labelValue: labelValues) {
            Swt.newLabel(group, null, labelValue.description);
            // TODO: Fix this.
            labelValue.setLabel(Swt.newLabel(group, "wrap", null));
        }
    }

    private void updateValues() {
        if (isDisposed()) {
            return;
        }

        BlocksStored.setText(cm.getStoredBlocks());
        BlocksAllocated.setText(blockList.getAllocatedNumber());
        BlocksFinished.setText(blockList.getFinishedNumber());
        BlocksTemporary.setText(blockList.getTempBlocksNumber());

        RequestThreads.setText(negura.getRequestServer().getThreadsActive());
        
        long csDown = trafficAggregator.getSessionDown();
        long csUp = trafficAggregator.getSessionUp();
        long csTime = System.nanoTime() - trafficAggregator.getSessionStart();
        long csActiveTimeDown = trafficLogger.getSessionActiveTimeDown();
        long csActiveTimeUp = trafficLogger.getSessionActiveTimeUp();
        
        long atDown = csDown + trafficAggregator.getPreviousDown();
        long atUp = csUp + trafficAggregator.getPreviousUp();
        long atTime = csTime + trafficAggregator.getPreviousTime();
        long atActiveTimeDown = csActiveTimeDown +
                trafficLogger.getPreviousActiveTimeDown();
        long atActiveTimeUp = csActiveTimeUp +
                trafficLogger.getPreviousActiveTimeUp();

        String[] csStrings = calcFiveValues(csDown, csUp, csTime,
                csActiveTimeDown, csActiveTimeUp);
        String[] atStrings = calcFiveValues(atDown, atUp, atTime,
                atActiveTimeDown, atActiveTimeUp);

        CsDownloaded.setText(csStrings[0]);
        CsUploaded.setText(csStrings[1]);
        CsTime.setText(csStrings[2]);
        CsAvgDown.setText(csStrings[3]);
        CsAvgUp.setText(csStrings[4]);

        AtDownloaded.setText(atStrings[0]);
        AtUploaded.setText(atStrings[1]);
        AtTime.setText(atStrings[2]);
        AtAvgDown.setText(atStrings[3]);
        AtAvgUp.setText(atStrings[4]);

        chartWidget.redraw();

        // Call update again after a period.
        display.timerExec(UPDATE_INTERVAL, callUpdateValues);
    }

    private String[] calcFiveValues(long down, long up, long time,
            long activeTimeDown, long activeTimeUp) {
        String[] ret = new String[5];

        final int NANO = 1000000000;

        long avgBytesDown;
        if (activeTimeDown == 0) {
            avgBytesDown = 0;
        } else {
            avgBytesDown = down / (activeTimeDown / NANO);
        }

        long avgBytesUp;
        if (activeTimeUp == 0) {
            avgBytesUp = 0;
        } else {
            avgBytesUp = up / (activeTimeUp / NANO);
        }

        ret[0] = Util.bytesWithUnit(down, 2);
        ret[1] = Util.bytesWithUnit(up, 2);
        ret[2] = Util.timeInterval(time / NANO);
        ret[3] = Util.bytesWithUnit(avgBytesDown, 2) + "/s";
        ret[4] = Util.bytesWithUnit(avgBytesUp, 2) + "/s";

        return ret;
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
    }
}
