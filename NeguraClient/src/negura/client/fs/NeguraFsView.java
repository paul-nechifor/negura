package negura.client.fs;

import java.util.ArrayList;
import java.util.List;
import negura.client.ClientConfigManager;
import negura.common.data.Operation;
import negura.common.ex.NeguraRunEx;
import negura.common.util.NeguraLog;

/**
 * Manages the Negura file system. Receives the operations done on the file
 * system and constructs the file hierarchy.
 * @author Paul Nechifor
 */
public class NeguraFsView {
    private ArrayList<Operation> operations = new ArrayList<Operation>();
    private NeguraFile root;
    private ClientConfigManager cm;

    public NeguraFsView(ClientConfigManager cm) {
        this.cm = cm;
        this.root = NeguraFile.newDir(this, "", "", 1);
    }

    public NeguraFsView(ClientConfigManager cm, List<Operation> operations) {
        this(cm);
        addOperations(operations);
    }

    @SuppressWarnings("unchecked")
    public synchronized List<Operation> getOperations() {
        return (ArrayList<Operation>) operations.clone();
    }

    public synchronized Operation getOperation(int opid) {
        if (opid < 1 || opid > operations.size())
            throw new RuntimeException("No such operation.");
        return operations.get(opid - 1);
    }

    public ClientConfigManager getConfigManager() {
        return cm;
    }

    public final synchronized void addOperations(List<Operation> newOps) {
        int expectedId = operations.size() + 1;

        for (Operation o : newOps) {
            if (o.oid != expectedId) {
                throw new NeguraRunEx("Expected %d, found %d.",
                        expectedId, o.oid);
            }
            operations.add(o);
            processOperation(o);
            expectedId++;
        }
    }

    public synchronized int getLastOperationId() {
        return operations.size();
    }

    /**
     * Returns the file from the FS based on its absolute path.
     * @param absolutePath    The location of the file.
     * @return                The NeguraFile object.
     */
    public NeguraFile getFile(String absolutePath) {
        NeguraFile currLocation = root;
        for (String comp : componentsOfPath(absolutePath)) {
            if (!currLocation.subfiles.containsKey(comp))
                return null;
            currLocation = currLocation.subfiles.get(comp);
        }
        return currLocation;
    }

    /**
     * Processes the modifications done to the hierarchy by an operation.
     * @param op   The operation.
     */
    private void processOperation(Operation op) {
        if (op.type.equals("add"))
            processAddOperation(op);
        else
            throw new RuntimeException("Not yet implemented."); // TODO
    }

    /**
     * Processes the modifications done to the hierarchy by an "add" operation.
     * @param op   The operation.
     */
    private void processAddOperation(Operation op) {
        NeguraLog.info("Added to file system: '%s'.", op.path);
        NeguraFile currDir = root;
        NeguraFile nextDir;
        String pathSoFar = "";
        String name;
        String[] components = componentsOfPath(op.path);
        for (int i = 0; i < components.length - 1; i++) {
            name = components[i];
            if (!currDir.subfiles.containsKey(name)) {
                nextDir = NeguraFile.newDir(this, pathSoFar, name, op.date);
                currDir.subfiles.put(name, nextDir);
            } else {
                nextDir = currDir.subfiles.get(name);
            }
            // Update the dates of the dirs as we pass through them.
            nextDir.date = op.date;

            currDir = nextDir;
            pathSoFar += "/" + name;
        }
        name = components[components.length - 1];
        NeguraFile newFile = NeguraFile.newFile(this, pathSoFar, name, op.date,
                op.size, op.oid);
        currDir.subfiles.put(name, newFile);
    }

    /**
     * Gets the components of a path. Examples:<br/>
     * <code>"/" => []</code><br/>
     * <code>"/foo/./bar/" => ["foo", "bar"]</code><br/>
     * <code>"/foo///bar" => ["foo", "bar"]</code><br/>
     * <code>"/foo/../fu/pub" => ["fu", "pub"]</code><br/>
     * TODO: implove performance
     *
     * @param absolutePath   The path in absolute form.
     * @return               The array of components.
     */
    private String[] componentsOfPath(String absolutePath) {
        ArrayList<String> comp = new ArrayList<String>();
        for (String c : absolutePath.replaceAll("/+", "/").split("/"))
            if (c.equals(".")) {
                // "." is redundant.
            } else if (c.equals("..")) {
                if (comp.isEmpty())
                    throw new AssertionError("Invalid path.");
                comp.remove(comp.size() - 1);
            } else {
                comp.add(c);
            }
        return comp.toArray(new String[0]);
    }
}
