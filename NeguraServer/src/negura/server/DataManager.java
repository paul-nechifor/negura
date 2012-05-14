package negura.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import negura.common.util.Util;
import negura.common.data.Block;
import negura.common.data.Operation;
import negura.common.util.NeguraLog;
import org.apache.commons.dbcp.BasicDataSource;

/**
 * Manages the database.
 * @author Paul Nechifor
 */
public class DataManager {
    private ServerConfigManager cm;
    private BasicDataSource connectionPool;

    public DataManager(ServerConfigManager cm) {
        this.cm = cm;

        // Testing for driver existance.
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException ex) {
            NeguraLog.severe(ex, "PostgreSQL JDBC Driver not found.");
        }

        connectionPool = new BasicDataSource();
        connectionPool.setDriverClassName("org.postgresql.Driver");
        connectionPool.setUsername(cm.getDatabaseUser());
        connectionPool.setPassword(cm.getDatabasePassword());
        connectionPool.setUrl(cm.getDatabaseUrl());
        // TODO: Thise options should be in the config file.
        connectionPool.setMaxActive(10);
        connectionPool.setMaxIdle(2);
    }

    public void shutdown() {
        try {
            connectionPool.close();
        } catch (SQLException ex) {
            NeguraLog.severe(ex);
        }
    }

    public void recreateTables() {
        NeguraLog.info("Creating the tables.");

        String commandsFile = null;
        try {
            commandsFile = Util.readStreamAsString(getClass()
                    .getResourceAsStream("/res/tables.sql"));
        } catch (IOException ex) {
            NeguraLog.severe(ex);
        }
        String[] commands = commandsFile.split(";");

        try {
            Connection c = connectionPool.getConnection();
            Statement s = c.createStatement();

            for (int i = 0; i < commands.length - 1; i++) {
                String command = commands[i].replaceAll("\\s+", " ").trim();
                if (command.equals(";"))
                    continue;
                
                try {
                    s.execute(command);
                } catch (SQLException ex) {
                    NeguraLog.severe(ex, "Couldn't execute '%s'.", command);
                }
            }

            s.close();
            c.close();
        } catch (SQLException ex) {
            NeguraLog.severe(ex);
        }
    }

    public boolean userExists(String ipAddress, int port) {
        String sqlText = "SELECT '1' FROM users WHERE ip = ? AND port = ?";
        try {
            Connection c = connectionPool.getConnection();
            PreparedStatement ps = c.prepareStatement(sqlText);
            ps.setString(1, ipAddress);
            ps.setInt(2, port);
            ResultSet results = ps.executeQuery();
            boolean found = results.next();
            results.close();
            ps.close();
            c.close();
            return found;
        } catch (Exception ex) {
            NeguraLog.severe(ex);
        }

        return false;
    }

    public InetSocketAddress userAddress(int id) {
        try {
            Connection c = connectionPool.getConnection();
            Statement s = c.createStatement();
            ResultSet results = s.executeQuery("SELECT ip, port FROM users"
                    + " WHERE id = " + id + "");
            results.next();
            InetSocketAddress ret = new InetSocketAddress(results.getString(1),
                    results.getInt(2));
            results.close();
            s.close();
            c.close();
            return ret;
        } catch (Exception ex) {
            NeguraLog.severe(ex);
        }

        return null;
    }

    public int createNewUser(String ipAddress, int port, int numberOfBlocks,
            String publicKey) throws SQLException {
        Connection c = connectionPool.getConnection();

        // Try to allocate as much as half of the number of blocks.
        // TODO: Refine this. Maybe there should be a server setting saying what
        // procentage should be allocated.
        double ratio = 0.05;
        int allocate = (int) Math.ceil(numberOfBlocks * ratio);
        List<Integer> blockIds = new ArrayList<Integer>(numberOfBlocks);

        // Get as many as 'allocate' blocks in a random order.
        Statement s = c.createStatement();
        ResultSet results = s.executeQuery("SELECT id FROM blocks"
                + " ORDER BY random()");
        while (results.next()) {
            blockIds.add(results.getInt(1));
            if (blockIds.size() >= allocate)
                break;
        }
        results.close();

        // Increment the user ID sequence.
        int userId = singleRowResult(c, "SELECT nextval('userSeq');");
        PreparedStatement ps = c.prepareStatement("INSERT INTO users "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)");
        ps.setInt(1, userId);
        ps.setString(2, ipAddress);
        ps.setInt(3, port);
        ps.setInt(4, numberOfBlocks);
        ps.setString(5, publicKey);
        // New block to be used. Index starts at 1.
        ps.setInt(6, blockIds.size() + 1);
        ps.setInt(7, blockIds.size()); // Number of blocks allocated.
        ps.executeUpdate();
        ps.close();

        if (!blockIds.isEmpty()) {
            ps = c.prepareStatement("INSERT INTO allocated VALUES (?, ?, ?);");
            int order = 1; // Order index starts at 1.
            for (Integer blockId : blockIds) {
                ps.setInt(1, userId);
                ps.setInt(2, blockId);
                ps.setInt(3, order);
                order++;
                ps.addBatch();
            }
            ps.executeBatch();
            ps.close();
        }
        
        c.close();

        return userId;
    }

    /**
     * Returns users that have recently used the server and therefore are likely
     * to be still online.
     * @return    List of user addresses.
     * @throws SQLException
     */
    public List<InetSocketAddress> getRecentUserAddresses()
            throws SQLException {
        LinkedList<InetSocketAddress> ret = new LinkedList<InetSocketAddress>();
        InetSocketAddress a;

        Connection c = connectionPool.getConnection();
        Statement s = c.createStatement();
        ResultSet results = s.executeQuery("SELECT ip, port FROM users;");
        while (results.next()) {
            a = new InetSocketAddress(results.getString(1), results.getInt(2));
            ret.add(a);
        }
        results.close();
        s.close();
        c.close();

        return ret;
    }

    // Checks if a query returns any rows.
    public boolean returnsRows(String sqlText) {
        try {
            Connection c = connectionPool.getConnection();
            Statement s = c.createStatement();
            ResultSet results = s.executeQuery(sqlText);
            boolean ret = results.next();
            results.close();
            s.close();
            c.close();
            return ret;
        } catch (Exception ex) {
            NeguraLog.severe(ex);
        }

        return false;
    }

    // TODO: delete this
    public int singleRowResult(String sqlText) throws SQLException {
        Connection c = connectionPool.getConnection();
        int ret = singleRowResult(c, sqlText);
        c.close();
        return ret;
    }

    // Returns the first result of a query as an int.
    // TODO: this should be private.
    private int singleRowResult(Connection c, String sqlText)
            throws SQLException {
        Statement s = c.createStatement();
        ResultSet results = s.executeQuery(sqlText);
        results.next();
        int ret = results.getInt(1);
        results.close();
        s.close();
        return ret;
    }

    // TODO: de ce nu întoarce mereu?
    public Map<Integer, ArrayList<String>>
            peersForBlocks(ArrayList<Integer> blocks) throws SQLException {
        Map<Integer, ArrayList<String>> ret =
                new HashMap<Integer, ArrayList<String>>();

        Connection c = connectionPool.getConnection();
        Statement s = c.createStatement();
        String query = "SELECT c.bid, u.ip || ':' || u.port "
                + "FROM completed c, users u WHERE c.id = u.id "
                + "AND c.bid IN " + sqlList(blocks);
        ResultSet results = s.executeQuery(query);

        while (results.next()) {
            Integer blockId = results.getInt(1);
            String address = results.getString(2);

            if (ret.containsKey(blockId)) {
                ret.get(blockId).add(address);
            } else {
                ArrayList<String> add = new ArrayList<String>();
                add.add(address);
                ret.put(blockId, add);
            }
        }

        if (ret.isEmpty()) {
            NeguraLog.warning("Bad problem. Fix this.");
            ret = peersForBlocks(blocks);
        }

        results.close();
        s.close();
        c.close();
        return ret;
    }
    
    private static class Allocation {
        public int id;
        public int startIndex;
        public int canUse;
        private int filled = 0;
        public int[] blocks;

        public void add(int block) {
            blocks[filled] = block;
            filled++;
        }

        public boolean isFull() {
            return filled == blocks.length;
        }
    }

    /**
     * Gets a list of users which can provide a specified number of unallocated
     * blocks; the method also updates the block count and start index in
     * preparation for the allocation.
     * @param c                 The connection to be used.
     * @param excludeId         The id which should be excluded because he added
     *                          the operation.
     * @param blocksNeeded      The sum of the blocks which the returned users
     *                          should provide.
     * @param maxPerUser        The maximum number of blocks per user.
     * @return                  A list of Allocation objects.
     * @throws SQLException
     */
    private List<Allocation> getUserIdsFor(Connection c, int excludeId,
            int blocksNeeded, int maxPerUser) throws SQLException {
        List<Allocation> ret = new ArrayList<Allocation>();
        
        Statement s = c.createStatement();
        // Select all the users which have blocks available to be allocated, in
        // a random order and close it when you have enough.
        // TODO: find out if all the rows of the selection are locked, or only
        // the ones which are retrieved.
        ResultSet results = s.executeQuery("SELECT id, nblocks - bcount, "
                + "newindex FROM users "
                + "WHERE bcount < nblocks AND id <> " + excludeId
                + " ORDER BY random() FOR UPDATE;");

        Allocation a;
        int used = 0;

        while (results.next()) {
            a = new Allocation();
            a.id = results.getInt(1);
            a.canUse = results.getInt(2);
            if (a.canUse > maxPerUser)
                a.canUse = maxPerUser;
            a.startIndex = results.getInt(3);
            a.blocks = new int[a.canUse];
            ret.add(a);

            used += a.canUse;
            if (used >= blocksNeeded)
                break;
        }

        results.close();
        s.close();

        // TODO: handle this properly.
        if (used < blocksNeeded)
            throw new SQLException("Not enough blocks in the system. Could only"
                    + " find " + used + " of " + blocksNeeded + " blocks.");

        // Now update the number of blocks allocated and the start index.
        PreparedStatement ps = c.prepareStatement("UPDATE users "
                + "SET newindex = newindex + ?, bcount = bcount + ?"
                + "WHERE id = ?;");

        for (Allocation al : ret) {
            ps.setInt(1, al.canUse);
            ps.setInt(2, al.canUse);
            ps.setInt(3, al.id);
            ps.addBatch();
        }
        ps.executeBatch();

        return ret;
    }

    private void fillBlocks(List<Allocation> list, int[] blocks) {
        List<Allocation> stop = new ArrayList<Allocation>(list.size());
        int index = 0;
        int giveTo;
        Allocation a;

        while (!list.isEmpty()) {
            giveTo = (int) (Math.random() * list.size());
            a = list.get(giveTo);
            a.add(blocks[index]);
            if (a.isFull()) {
                list.remove(giveTo);
                stop.add(a);
            }

            // The block index loops like in a ring.
            index++;
            if (index == blocks.length)
                index = 0;
        }

        // Put all the elements back.
        list.addAll(stop);
    }

    /**
     * Updates the creator's start index and block count and returns the the one
     * before the update
     * @param c
     * @param userId
     * @param length
     * @return
     */
    private int updateCreatorStartIndex(Connection c, int userId, int length)
            throws SQLException {
        Statement s = c.createStatement();
        ResultSet results = s.executeQuery("SELECT nblocks - bcount, newindex "
                + "FROM users WHERE id = " + userId + " FOR UPDATE;");
        results.next();
        int blocksLeft = results.getInt(1);
        int newIndex = results.getInt(2);
        results.close();
        s.close();

        if (blocksLeft < length)
            throw new SQLException("The creator doesn't have enough blocks to"
                    + "add this operation.");

        PreparedStatement ps = c.prepareStatement("UPDATE users "
                + "SET newindex = newindex + ?, bcount = bcount + ?"
                + "WHERE id = ?;");
        ps.setInt(1, length);
        ps.setInt(2, length);
        ps.setInt(3, userId);
        ps.executeUpdate();
        ps.close();

        return newIndex;
    }

    private int userCount(Connection c) throws SQLException {
        return singleRowResult(c, "SELECT count(*) FROM users;");
    }

    /**
     *
     * @param userId
     * @param op
     * @return              The list of users who had blocks allocated to them.
     * @throws SQLException
     */
    public List<Integer> insertOperationAndAllocate(int userId, Operation op)
            throws SQLException {
        // Inserting the operation.
        Connection c = connectionPool.getConnection();
        PreparedStatement ps = c.prepareStatement("INSERT INTO "
                + "operations VALUES (?, ?, ?, ?, ?, ?, ?, ?);");

        ps.setInt(1, op.id);
        ps.setString(2, op.path);
        ps.setString(4, op.signature);
        ps.setInt(5, op.date);
        ps.setString(8, op.type);

        if (op.type.equals("add")) {
            ps.setNull(3, Types.VARCHAR);
            ps.setLong(6, op.size);
            ps.setString(7, op.hash);
        } else if (op.type.equals("move")) {
            ps.setString(3, op.newPath);
            ps.setNull(6, Types.BIGINT);
            ps.setNull(7, Types.VARCHAR);
        } else if (op.type.equals("delete")) {
            ps.setNull(3, Types.VARCHAR);
            ps.setNull(6, Types.BIGINT);
            ps.setNull(7, Types.VARCHAR);
        } else throw new AssertionError("No such operation.");
        ps.executeUpdate();
        ps.close();

        // Inserting the blocks.
        ps = c.prepareStatement(
                "INSERT INTO blocks VALUES (?, ?, ?);");

        ArrayList<Integer> blockIds = new ArrayList<Integer>();

        for (Block block : op.blocks) {
            ps.setInt(1, block.id);
            ps.setString(2, block.hash);
            ps.setInt(3, op.id);
            ps.addBatch();
            blockIds.add(block.id);
        }
        ps.executeBatch();
        ps.close();

        // Inserting the allocated blocks.
        List<Integer> ret = new ArrayList<Integer>();

        // This begins a new transaction.
        c.setAutoCommit(false);
        
        try {
            int multiplicity = 2;
            int blocksNeeded = op.blocks.length * multiplicity;
            int maxPerUser = (int) Math.ceil(((double) blocksNeeded) /
                    (userCount(c) - 1));
            List<Allocation> allocations = getUserIdsFor(c, userId,
                    blocksNeeded, maxPerUser);
            fillBlocks(allocations, op.getBlockIds());


            ps = c.prepareStatement(
                    "INSERT INTO allocated VALUES (?, ?, ?);");

            for (Allocation a : allocations) {
                ret.add(a.id);
                for (int i = 0; i < a.canUse; i++) {
                    ps.setInt(1, a.id);
                    ps.setInt(2, a.blocks[i]);
                    ps.setInt(3, a.startIndex + i);
                    ps.addBatch();
                }
            }

            // The user who created this, already has them so add the blocks to
            // the allocated blocks.
            int startIndex = updateCreatorStartIndex(c, userId,
                    op.blocks.length);
            int offset = 0;
            for (Integer blockId : blockIds) {
                ps.setInt(1, userId);
                ps.setInt(2, blockId);
                ps.setInt(3, startIndex + offset);
                ps.addBatch();
                offset++;
            }

            ps.executeBatch();
            ps.close();
            // End the transaction.
            c.commit();
            c.setAutoCommit(true); // Set autocomit back on after no exception.

        } catch (SQLException ex) {
            c.rollback(); // TODO: Shouldn't I roll back the operation too?
            c.setAutoCommit(true); // Set autocommit back on after exception.
            throw ex;
        }


        c.close();

        // Set the blocks as being completed by the user who added them.
        insertCompleted(userId, blockIds);
        return ret;
    }

    public void insertCompleted(int uid, Collection<Integer> blocks)
            throws SQLException {
        Connection c = connectionPool.getConnection();
        PreparedStatement ps = c.prepareStatement("INSERT INTO "
                + "completed VALUES (?, ?);");

        for (Integer bid : blocks) {
            ps.setInt(1, uid);
            ps.setInt(2, bid);
            ps.addBatch();
        }

        ps.executeBatch();
        ps.close();
        c.close();
    }

    public List<Integer> getBlockList(int uid) throws SQLException {
        List<Integer> ret = new ArrayList<Integer>();

        Connection c = connectionPool.getConnection();
        Statement s = c.createStatement();
        ResultSet results = s.executeQuery("SELECT bid FROM allocated " +
                "WHERE id = " + uid + ";");

        while (results.next())
            ret.add(results.getInt(1));
        
        results.close();
        s.close();
        c.close();
        return ret;
    }

    public List<Operation> operationsAfter(int opid) throws SQLException {
        List<Operation> ret = new ArrayList<Operation>();

        Connection c = connectionPool.getConnection();
        Statement s = c.createStatement();
        ResultSet results = s.executeQuery("SELECT * FROM operations " +
                "WHERE id > " + opid + " ORDER BY id;");
        Operation o;
        while (results.next()) {
            o = new Operation();
            o.id = results.getInt(1);
            o.path = results.getString(2);
            o.newPath = results.getString(3);
            o.signature = results.getString(4);
            o.date = results.getInt(5);
            o.size = results.getLong(6);
            o.hash = results.getString(7);
            o.type = results.getString(8);
            ret.add(o);
        }
        results.close();

        results = s.executeQuery("SELECT * FROM blocks WHERE opid > " + opid
                + " ORDER BY opid, id;");

        int lastOpId = -1;
        int currentOpId = -1;
        int index = -1;
        LinkedList<Block> blocks = null;
        while (results.next()) {
            currentOpId = results.getInt(3);
            if (currentOpId != lastOpId) {
                if (index >= 0) {
                    ret.get(index).blocks = blocks.toArray(new Block[0]);
                }

                index++;
                lastOpId = currentOpId;
                blocks = new LinkedList<Block>();
            }
            blocks.add(new Block(results.getInt(1), results.getString(2)));
        }
        if (index >= 0)
            ret.get(index).blocks = blocks.toArray(new Block[0]);

        results.close();
        s.close();
        c.close();

        return ret;
    }

    /**
     * Transforms a list into the format which is needed by the IN operator in
     * SQL. For example for a list containing 1, 2, 3 the returned string will
     * be <code>"(1, 2, 3)"<code>.
     * @param <E>       The type of the element.
     * @param list      The list of elements
     * @return          A string containing the elements.
     */
    private <E> String sqlList(List<E> list) {
        StringBuilder builder = new StringBuilder(list.size() * 4); // Suppose
        builder.append('(');
        for (E e : list)
            builder.append(e.toString()).append(',');
        builder.deleteCharAt(builder.length() - 1).append(')');
        return builder.toString();
    }
}
