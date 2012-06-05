package negura.client.gui;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import negura.client.AddOperationsMaker;
import negura.client.ClientConfigManager;
import negura.common.data.Operation;
import negura.common.ex.NeguraEx;
import negura.common.gui.Swt;
import negura.common.gui.Window;
import negura.common.gui.MsgBox;
import negura.common.util.NeguraLog;
import negura.common.util.Os;
import net.miginfocom.swt.MigLayout;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * A window which prompts the user to upload a file or directory to the server.
 * @author Paul Nechifor
 */
public class FileAdder extends Window implements AddOperationsMaker.Listener {
    private final static int PROGRESSBAR_MAX = 100000;

    private final ClientConfigManager cm;
    private final StackLayout stackLayout;
    private final Composite p1;
    private final Composite p2;
    private final Text fileT;
    private final Text storeT;
    private final ProgressBar progressPb;

    private AddOperationsMaker maker;

    public FileAdder(Display display, CommonResources resources,
            ClientConfigManager cm) {
        super(new Shell(display));

        this.cm = cm;

        shell.setText("Upload files");
        shell.setImage(resources.getImage("application"));
        stackLayout = new StackLayout();
        shell.setLayout(stackLayout);

        // Page 1 layout.
        p1 = new Composite(shell, SWT.NONE);
        p1.setLayout(new MigLayout("insets 10",
                "[:250:,grow, fill][100::,fill][100::,fill]"));

        Swt.newLabel(p1, "span, wrap", "Select a file or folder to upload:");

        fileT = Swt.newText(p1, "span, wrap", null);

        Button addFileB = Swt.newButton(p1, "skip 1", "Add file");
        Button addFolderB = Swt.newButton(p1, "wrap", "Add folder");

        Swt.newLabel(p1, "span, wrap", "Where to add it on the server:");

        storeT = Swt.newText(p1, "span, wrap 40", null);

        Button uploadB = Swt.newButton(p1, "skip 1", "Upload");
        Button cancelB = Swt.newButton(p1, null, "Cancel");

        // Page 1 options.
        shell.setDefaultButton(uploadB);
        addFileB.setFocus();
        addFileB.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                addFile();
            }
        });
        addFolderB.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                addFolder();
            }
        });
        uploadB.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                upload();
            }
        });
        cancelB.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                dispose();
            }
        });

        // Page 2 layout.
        p2 = new Composite(shell, SWT.NONE);
        p2.setLayout(new MigLayout("insets 10",
                "[:250:,grow, fill][100::,fill][100::,fill]"));

        Swt.newLabel(p2, "span", "Creating blocks.");
        progressPb = Swt.newHProgressBar(p2, "span, wrap", 0, PROGRESSBAR_MAX);

        // Page 2 options.


        setPage(p1);
        shell.pack();
        Swt.centerShell(shell);

        shell.open();
    }

    private void setPage(Composite page) {
        stackLayout.topControl = page;
        shell.layout();
    }

    private void addFile() {
        FileDialog fd = new FileDialog(shell, SWT.OPEN);
        fd.setText("Select a file or directory.");
        fd.setFilterPath(Os.getUserHome().getAbsolutePath());
        String selected = fd.open();

        if (selected != null) {
            fileT.setText(selected);
        }
    }

    private void addFolder() {
        DirectoryDialog fd = new DirectoryDialog(shell, SWT.OPEN);
        fd.setText("Select a file or directory.");
        fd.setFilterPath(Os.getUserHome().getAbsolutePath());
        String selected = fd.open();

        if (selected != null) {
            fileT.setText(selected);
        }
    }

    private void upload() {
        String fileStr = fileT.getText();
        if (fileStr.isEmpty()) {
            MsgBox.warning(shell, "Please select a file or folder.");
            return;
        }

        final File fileOrDir = new File(fileStr);
        if (!fileOrDir.exists()) {
            MsgBox.warning(shell, "The selected file does't exist.");
        }

        final String storePath = storeT.getText();
        if (storePath.isEmpty()) {
            MsgBox.warning(shell, "Please write a place to store it to.");
            return;
        }

        setPage(p2);

        // Start the creation of the blocks in another thread.
        new Thread(new Runnable() {
            @Override
            public void run() {
                process(fileOrDir, storePath);
            }
        }).start();
    }

    private void process(File fileOrDir, String storePath) {
        maker = new AddOperationsMaker(cm, fileOrDir, storePath, this);

        List<Integer> firstBlocks = null;

        try {
            // Creating the operations.
            maker.createOperations();
            
            // Getting them.
            ArrayList<Operation> operations = maker.getOperations();

            // Uploading the operations to the server
            firstBlocks = cm.getNegura().uploadOperations(operations);
        } catch (IOException ex) {
            syncErrorAndCleanUp(ex.getMessage());
        }

        // Moving the blocks to the temp blocks.
        try {
            maker.confirmOperations(firstBlocks);
        } catch (NeguraEx ex) {
            NeguraLog.warning(ex);
        }

        syncSuccess("The files were added successfully.");
    }

    private void syncSuccess(final String message) {
        shell.getDisplay().syncExec(new Runnable() {
            public void run() {
                shell.setVisible(false);
                MsgBox.info(shell, message);
                dispose();
            }
        });
    }

    private void syncErrorAndCleanUp(final String message) {
        shell.getDisplay().syncExec(new Runnable() {
            public void run() {
                MsgBox.error(shell, message);
                dispose();
            }
        });

        // Clean up.
        try {
            maker.cancelOperations();
        } catch (NeguraEx ex1) {
            NeguraLog.warning(ex1);
        }
    }

    @Override
    public void update(final double complete) {
        // TODO: Change this.
        shell.getDisplay().syncExec(new Runnable() {
            public void run() {
                progressPb.setSelection((int) (PROGRESSBAR_MAX * complete));
            }
        });
    }
}
