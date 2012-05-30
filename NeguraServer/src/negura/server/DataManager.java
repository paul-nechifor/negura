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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import negura.common.ex.NeguraEx;
import negura.common.util.Util;
import negura.common.data.Block;
import negura.common.data.Operation;
import negura.common.ex.NeguraError;
import negura.common.util.NeguraLog;
import org.apache.commons.dbcp.BasicDataSource;

/**
 * Manages the database.
 * @author Paul Nechifor
 */
public class DataManager {
    private BasicDataSource connectionPool;

    public DataManager(ServerConfigManager cm) {
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

    /**
     * Shuts down the connection to the database.
     */
    public void shutdown() {
        try {
            connectionPool.close();
        } catch (SQLException ex) {
            NeguraLog.warning(ex);
        }
    }

    /**
     * Creates the tables and other things.
     * @throws SQLException
     */
    public void createTables() throws SQLException {
        NeguraLog.info("Creating the tables.");

        String commandsFile = null;
        try {
            commandsFile = Util.readStreamAsString(getClass()
                    .getResourceAsStream("/res/tables.sql"));
        } catch (IOException ex) {
            NeguraLog.severe(ex);
        }

        // Remove the comments.
        commandsFile = commandsFile.replaceAll("--.*\r?\n", "\n");

        // Split into individual commands.
        String[] commands = commandsFile.split(";");

        Connection c = null;
        Statement s = null;
        try {
            c = connectionPool.getConnection();
            s = c.createStatement();

            for (int i = 0; i < commands.length - 1; i++) {
                String command = commands[i].replaceAll("\\s+", " ").trim();
                if (command.equals(";"))
                    continue;

                s.addBatch(command);
            }
            s.executeBatch();
            NeguraLog.info("Finished creating tables.");
        } finally {
            closeQuietly(s);
            closeQuietly(c);
        }
    }

    public void initializeSettings(int virtualDiskSize) throws SQLException {
        Connection c = null;
        Statement s = null;
        try {
            c = connectionPool.getConnection();

            String size = Integer.toString(virtualDiskSize);

            initializeValues(c,
                    "virtual_disk_size", size,
                    "free_blocks", size,
                    "first_free_block", "1");
        } finally {
            closeQuietly(s);
            closeQuietly(c);
        }
    }

    /**
     * Creates all the empty blocks of the original file system.
     * @param length   The number of blocks.
     * @throws SQLException
     */
    public void createOriginalBlocks(int length) throws SQLException {
        Connection c = null;
        PreparedStatement ps = null;

        try {
            c = connectionPool.getConnection();
            int firstBlock = (int)(long)(Long)singleValueResult(c,
                    "SELECT nextval('block_seq')");
            if (firstBlock != 1) {
                throw new RuntimeException("The sequence returns " + firstBlock
                        + " instead of 1.");
            }

            ps = c.prepareStatement("INSERT INTO blocks VALUES (?)");

            for (int i = 1; i <= length; i++) {
                ps.setInt(1, i);
                ps.addBatch();
            }

            ps.executeBatch();

            // The next value will be length + 1.
            returnsRows(c, "SELECT setval('block_seq', " + length + ", true)");

            NeguraLog.info("The %d original blocks were created.", length);
        } finally {
            closeQuietly(ps);
            closeQuietly(c);
        }
    }

    /**
     * Checks if a user exists.
     * @param ipAddress     The IP address of the user.
     * @param port          The port of the user.
     * @return              True if the user exists.
     * @throws SQLException
     */
    public boolean userExists(String ipAddress, int port) throws SQLException {
        Connection c = null;
        PreparedStatement ps = null;

        try {
            c = connectionPool.getConnection();
            ps = c.prepareStatement(
                "SELECT '1' " +
                "FROM users " +
                "WHERE ip = ? AND port = ?"
            );
            ps.setString(1, ipAddress);
            ps.setInt(2, port);
            ResultSet results = ps.executeQuery();
            boolean found = results.next();
            results.close();
            return found;
        } finally {
            closeQuietly(ps);
            closeQuietly(c);
        }
    }

    /**
     * Create a new user, allocate the blocks and return the user ID.
     * @param ipAddress         The IP address of the new user.
     * @param port              The port of the new user.
     * @param numberOfBlocks    The number of blocks the user will store.
     * @param publicKey         The new user's public key in base64.
     * @return                  The user ID.
     * @throws SQLException 
     */
    public int createNewUser(String ipAddress, int port, int numberOfBlocks,
            String publicKey) throws SQLException {
        Connection c = null;
        PreparedStatement ps = null;

        try {
            c = connectionPool.getConnection();

            // Increment the user ID sequence.
            int userId = (int)(long)(Long)singleValueResult(c,
                    "SELECT nextval('user_seq')");
            
            ps = c.prepareStatement(
                "INSERT INTO users " +
                "VALUES (?, ?, ?, ?, ?, ?)"
            );
            ps.setInt(1, userId);
            ps.setString(2, ipAddress);
            ps.setInt(3, port);
            ps.setInt(4, numberOfBlocks);
            ps.setString(5, publicKey);
            ps.setInt(6, 1); // New index starts at 1.
            ps.executeUpdate();
            ps.close();
            
            ps = c.prepareStatement(
                "INSERT INTO allocated " +
                    "SELECT ?, bid " +
                    "FROM (" +
                        "SELECT b.bid, count(a.bid) as bcount " +
                        "FROM blocks b " +
                        "LEFT OUTER JOIN allocated a ON a.bid = b.bid " +
                        "GROUP BY b.bid" +
                    ") c " +
                    "ORDER BY bcount, random() " +
                    "LIMIT ? "
            );
            ps.setInt(1, userId);
            ps.setInt(2, numberOfBlocks);
            ps.executeUpdate();

            return userId;
        } finally {
            // ps might will normally be closed twice. This is here for safaty.
            closeQuietly(ps);
            closeQuietly(c);
        }
    }

    /**
     * Inserts the operation and adds the filled blocks to the allocation list
     * for each user.
     * @param op                The added operation.
     * @param creatorId         The user who added the operation and who has all the
     *                          blocks if this is a file addition operation.
     * @param allocatedUsers    The list of user IDs whose allocation lists have
     *                          been modified.
     * @return                  The first bloc of the file if this is a file
     *                          addition operation or -1 otherwise.
     * @throws SQLException
     * @throws NeguraEx
     */
    // TODO: This only handles adding files now.
    public int insertOperationAndAllocate(Operation op, int creatorId,
            List<Integer> allocatedUsers) throws SQLException, NeguraEx {
        Connection c = null;
        Statement s = null;
        PreparedStatement ps = null;

        try {
            c = connectionPool.getConnection();

            // Starting transaction.
            c.setAutoCommit(false);

            // Checking to see if there are enough blocks in the system.
            int freeBlocks = Integer.parseInt(getValue(c, "free_blocks"));
            if (op.blocks.length > freeBlocks) {
                throw new NeguraEx("The file requires %d blocks but " +
                        "there are %d left.", op.blocks.length, freeBlocks);
            }

            // Setting the block IDs for the operation.
            op.firstbid = Integer.parseInt(getValue(c,
                    "first_free_block"));
            op.lastbid = op.firstbid + op.blocks.length - 1;

            // Updating the settings.
            setValues(c,
                "free_blocks",
                    Integer.toString(freeBlocks - op.blocks.length),
                "first_free_block",
                    Integer.toString(op.lastbid + 1)
            );

            // Getting the ID for the operation.
            op.oid = (int)(long)(Long)singleValueResult(c,
                    "SELECT nextval('operation_seq')");

            // Inserting the operation.
            ps = c.prepareStatement("INSERT INTO "
                + "operations VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);");
            ps.setInt(1, op.oid);
            ps.setString(2, op.path);
            ps.setString(4, op.signature);
            ps.setInt(5, op.date);
            ps.setString(8, op.type);

            if (op.type.equals("add")) {
                ps.setNull(3, Types.VARCHAR);
                ps.setLong(6, op.size);
                ps.setString(7, op.hash);
                ps.setInt(9, op.firstbid);
                ps.setInt(10, op.lastbid);
            } else if (op.type.equals("move")) {
                ps.setString(3, op.newPath);
                ps.setNull(6, Types.BIGINT);
                ps.setNull(7, Types.VARCHAR);
                ps.setNull(9, Types.INTEGER);
                ps.setNull(10, Types.INTEGER);
            } else if (op.type.equals("delete")) {
                ps.setNull(3, Types.VARCHAR);
                ps.setNull(6, Types.BIGINT);
                ps.setNull(7, Types.VARCHAR);
                ps.setNull(9, Types.INTEGER);
                ps.setNull(10, Types.INTEGER);
            } else {
                throw new AssertionError("No such operation.");
            }

            ps.executeUpdate();
            ps.close();

            ps = c.prepareStatement(
                "UPDATE blocks "+
                "SET hash = ? " +
                "WHERE bid = ?"
            );

            for (int i = 0; i < op.blocks.length; i++) {
                ps.setString(1, op.blocks[i].hash);
                ps.setInt(2, op.firstbid + i);
                ps.addBatch();
            }

            ps.executeBatch();
            ps.close();

            // Getting the updated blocks for each user.
            ps = c.prepareStatement(
                "SELECT uid, bid " +
                "FROM allocated " +
                "WHERE bid >= ? AND bid <= ?"
            );
            ps.setInt(1, op.firstbid);
            ps.setInt(2, op.lastbid);
            ResultSet results = ps.executeQuery();

            HashMap<Integer, ArrayList<Integer>> forUser =
                    new HashMap<Integer, ArrayList<Integer>>();

            Integer uid, bid;
            ArrayList<Integer> newList;
            while (results.next()) {
                uid = results.getInt(1);
                bid = results.getInt(2);

                newList = forUser.get(uid);

                if (newList == null) {
                    newList = new ArrayList<Integer>();
                    forUser.put(uid, newList);
                }

                newList.add(bid);
            }

            results.close();
            ps.close();

            // Selecting the new index for each user with "for update".
            s = c.createStatement();
            results = s.executeQuery(
                "SELECT uid, newindex " +
                "FROM users " +
                "WHERE uid IN " + sqlList(forUser.keySet())
            );

            HashMap<Integer, Integer> newIndex =
                    new HashMap<Integer, Integer>();

            while (results.next()) {
                newIndex.put(results.getInt(1), results.getInt(2));
            }
            results.close();
            s.close();

            if (newIndex.size() != forUser.size()) {
                throw new NeguraEx("Different user sizes %d != %d.",
                        newIndex.size(), forUser.size());
            }

            // Modifying the allocation list for each user.
            ps = c.prepareStatement(
                "INSERT INTO alist " +
                "VALUES (?, ?, ?)"
            );
            int userId, currIndex;
            for (Entry<Integer, Integer> pair : newIndex.entrySet()) {
                userId = pair.getKey();
                currIndex = pair.getValue();

                for (Integer blockId : forUser.get(userId)) {
                    ps.setInt(1, userId);
                    ps.setInt(2, blockId);
                    ps.setInt(3, currIndex);
                    ps.addBatch();
                    currIndex++;
                }
            }
            ps.executeBatch();
            ps.close();

            // Modifying the new index for each user.
            ps = c.prepareStatement(
                "UPDATE users " +
                "SET newindex = ? " +
                "WHERE uid = ?"
            );

            int newIndexVal;
            for (Entry<Integer, Integer> pair : newIndex.entrySet()) {
                uid = pair.getKey();
                newIndexVal = pair.getValue() + forUser.get(uid).size();

                ps.setInt(1, newIndexVal);
                ps.setInt(2, uid);
                ps.addBatch();
            }

            ps.executeBatch();
            ps.close();

            // Adding the temp blocks for the creator.
            // TODO: There should be a setting for this available to the creator
            // as well.
            int deleteTime = (int)(System.currentTimeMillis()/1000) + 12*60*60;

            ps = c.prepareStatement(
                "INSERT INTO tempblocks " +
                "VALUES (?, ?, ?)"
            );
            for (int i = 0; i < op.blocks.length; i++) {
                ps.setInt(1, creatorId);
                ps.setInt(2, op.firstbid + i);
                ps.setInt(3, deleteTime);
                ps.addBatch();
            }
            ps.executeBatch();
            ps.close();

            // Ending transaction.
            c.commit();
            c.setAutoCommit(true);

            // "Returning" the users who had their lists modified.
            allocatedUsers.addAll(forUser.keySet());

            return op.firstbid;
        } catch (SQLException ex) {
            c.rollback();
            throw ex;
        } catch (NeguraEx ex) {
            c.rollback();
            throw ex;
        } finally {
            closeQuietly(s);
            closeQuietly(ps);
            closeQuietlyEndTransaction(c);
        }
    }

    /**
     * Sets the block specified as completed for the user.
     * @param userId        The user which has completed the blocks.
     * @param blockIds      The list of block IDs.
     * @throws SQLException
     */
    public void insertCompleted(int userId, Collection<Integer> blockIds)
            throws SQLException {
        Connection c = null;
        PreparedStatement ps = null;

        try {
            c = connectionPool.getConnection();
            ps = c.prepareStatement(
                "INSERT INTO completed " +
                "VALUES (?, ?)"
            );

            for (Integer blockId : blockIds) {
                ps.setInt(1, userId);
                ps.setInt(2, blockId);
                ps.addBatch();
            }

            ps.executeBatch();
        } finally {
            closeQuietly(ps);
            closeQuietly(c);
        }
    }

    // Used to avoid repetitive code below.
    private void fillWithPeerResults(ResultSet results,
            Map<Integer, ArrayList<String>> map) throws SQLException {
        Integer userId;
        String address;
        ArrayList<String> addTo;

        while (results.next()) {
            userId = results.getInt(1);
            address = results.getString(2);

            addTo = map.get(userId);
            if (addTo == null) {
                addTo = new ArrayList<String>();
                map.put(userId, addTo);
            }
            addTo.add(address);
        }

        results.close();
    }

    /**
     * Returns a list of peers of each of the given blocks.
     * @param blockIds        List of block IDs for which to return peers.
     * @return              A map of the block IDs to the list of peer addresses
     *                      represented as strings.
     * @throws SQLException
     */
    public Map<Integer, ArrayList<String>> getPeersForBlocks(
            Collection<Integer> blockIds) throws SQLException {
        Map<Integer, ArrayList<String>> ret =
                new HashMap<Integer, ArrayList<String>>();
        Connection c = null;
        Statement s = null;

        try {
            c = connectionPool.getConnection();
            s = c.createStatement();
            String blockIdsList = sqlList(blockIds);
            ResultSet results = s.executeQuery(
                "SELECT c.bid, u.ip || ':' || u.port " +
                "FROM completed c, users u " +
                "WHERE c.uid = u.uid AND c.bid IN " + blockIdsList
            );

            fillWithPeerResults(results, ret);

            // Getting the temp blocks peers.
            results = s.executeQuery(
                "SELECT t.bid, u.ip || ':' || u.port " +
                "FROM tempblocks t, users u " +
                "WHERE t.uid = u.uid AND t.bid IN " + blockIdsList
            );

            fillWithPeerResults(results, ret);

            if (ret.isEmpty()) {
                throw new NeguraError("No peers.");
            }

            return ret;
        } finally {
            closeQuietly(s);
            closeQuietly(c);
        }
    }

    /**
     * Returns all the addresses of the specified user IDs, but not in order.
     * @param userIds       The user IDs.
     * @return              The list of addresses.
     * @throws SQLException
     */
    public List<String> getUserAddresses(Collection<Integer> userIds)
            throws SQLException {
        Connection c = null;
        Statement s = null;

        try {
            c = connectionPool.getConnection();
            s = c.createStatement();
            ResultSet results = s.executeQuery(
                "SELECT ip || ':' || port " +
                "FROM users " +
                "WHERE uid IN " + sqlList(userIds)
            );

            List<String> addresses = new ArrayList<String>();

            while (results.next()) {
                addresses.add(results.getString(1));
            }

            results.close();

            return addresses;
        } finally {
            closeQuietly(s);
            closeQuietly(c);
        }
    }

    /**
     * Returns users that have recently used the server and therefore are likely
     * to be still online.
     * @return    List of user addresses.
     * @throws SQLException
     */
    public List<InetSocketAddress> getRecentUserAddresses()
            throws SQLException {
        Connection c = null;
        Statement s = null;

        try {
            c = connectionPool.getConnection();
            s = c.createStatement();
            // TODO: Do this properly. Now it selects everyone.
            ResultSet results = s.executeQuery(
                "SELECT ip, port " +
                "FROM users"
            );

            List<InetSocketAddress> ret = new ArrayList<InetSocketAddress>();

            while (results.next()) {
                ret.add(new InetSocketAddress(results.getString(1),
                        results.getInt(2)));
            }

            results.close();

            return ret;
        } finally {
            closeQuietly(s);
            closeQuietly(c);
        }
    }

    /**
     * Returns the modification to the block list for a specified user after the
     * given order ID which is not included..
     * @param userId        The owner of the list.
     * @param after         The order after which to return. If 0, returns all.
     * @return              The list of block list modifications.
     * @throws SQLException
     */
    public List<Integer> getBlockListAfter(int userId, int after)
            throws SQLException {
        Connection c = null;
        Statement s = null;

        try {
            c = connectionPool.getConnection();
            s = c.createStatement();
            ResultSet results = s.executeQuery(
                "SELECT bid " +
                "FROM alist " +
                "WHERE uid = " + userId + " AND orderb > " + after + " " +
                "ORDER BY orderb"
            );

            List<Integer> ret = new ArrayList<Integer>();

            while (results.next())
                ret.add(results.getInt(1));

            results.close();

            return ret;
        } finally {
            closeQuietly(s);
            closeQuietly(c);
        }
    }

    /**
     * Returns the operations after the specified operation ID which is not
     * included.
     * @param oid       The operation ID. If it's 0, returns all.
     * @return          The list of operations.
     * @throws SQLException
     */
    public List<Operation> getOperationsAfter(int oid) throws SQLException {
        Connection c = null;
        Statement s = null;

        try {
            c = connectionPool.getConnection();
            s = c.createStatement();
            ResultSet results = s.executeQuery(
                "SELECT * " +
                "FROM operations " +
                "WHERE oid > " + oid + " " +
                "ORDER BY oid"
            );

            List<Operation> ret = new ArrayList<Operation>();

            Operation o;
            while (results.next()) {
                o = new Operation();
                o.oid = results.getInt(1);
                o.path = results.getString(2);
                o.newPath = results.getString(3);
                o.signature = results.getString(4);
                o.date = results.getInt(5);
                o.size = results.getLong(6);
                o.hash = results.getString(7);
                o.type = results.getString(8);
                o.firstbid = results.getInt(9);
                o.lastbid = results.getInt(10);
                ret.add(o);
            }

            results.close();

            return ret;
        } finally {
            closeQuietly(s);
            closeQuietly(c);
        }
    }

    /**
     * Gets returns the hashes for the given block IDs.
     * @param blockIds      The collection of block IDs.
     * @return              A list of blocks with hashes.
     * @throws SQLException 
     */
    public List<Block> getHashesForBlocks(Collection<Integer> blockIds)
            throws SQLException {
        Connection c = null;
        Statement s = null;

        try {
            c = connectionPool.getConnection();
            s = c.createStatement();
            ResultSet results = s.executeQuery(
                "SELECT * " +
                "FROM blocks " +
                "WHERE bid IN " + sqlList(blockIds)
            );

            List<Block> ret = new ArrayList<Block>();

            Block b;
            while (results.next()) {
                b = new Block();
                b.bid = results.getInt(1);
                b.hash = results.getString(2);
                ret.add(b);
            }

            results.close();

            return ret;
        } finally {
            closeQuietly(s);
            closeQuietly(c);
        }
    }

    /**
     * Close a connection without causing a fatal error.
     * @param c     The connection to be closed. Can be null.
     */
    private void closeQuietly(Connection c) {
        if (c != null) {
            try {
                c.close();
            } catch (SQLException ex) {
                NeguraLog.warning(ex);
            }
        }
    }

    /**
     * End the transaction and close the connection without causing a fatal
     * error.
     * @param c     The connection to be close. Can be null.
     */
    private void closeQuietlyEndTransaction(Connection c) {
        if (c != null) {
            try {
                c.setAutoCommit(true);
                c.close();
            } catch (SQLException ex) {
                NeguraLog.warning(ex);
            }
        }
    }

    /**
     * Close a statement without causing a fatal error.
     * @param s     The statement to be closed. Can be null.
     */
    private void closeQuietly(Statement s) {
        if (s != null) {
            try {
                s.close();
            } catch (SQLException ex) {
                NeguraLog.warning(ex);
            }
        }
    }

    /**
     * Close a prepared statement without causing a fatal error.
     * @param ps     The prepared statement to be closed. Can be null.
     */
    private void closeQuietly(PreparedStatement ps) {
        if (ps != null) {
            try {
                ps.close();
            } catch (SQLException ex) {
                NeguraLog.warning(ex);
            }
        }
    }

    /**
     * Checks if a query returns results; can be used for select "commands" like
     * setval.
     * @param c             The open connection.
     * @param sqlText       The SQL query.
     * @return              True if the query returns at least one row.
     * @throws SQLException
     */
    private boolean returnsRows(Connection c, String sqlText)
            throws SQLException {
        Statement s = null;
        try {
            s = c.createStatement();
            ResultSet results = s.executeQuery(sqlText);
            boolean atLeastOneRow = results.next();
            results.close();
            return atLeastOneRow;
        } finally {
            closeQuietly(s);
        }
    }

    /**
     * Returns the first row of the first column of the result of the query.
     * This method does not enforce that the query should be a single value
     * table.
     *
     * @param c             The open connection.
     * @param sqlText       The SQL query.
     * @return              The result as an object whose type corresponds to
     *                      the column's SQL type.
     * @throws SQLException
     */
    private Object singleValueResult(Connection c, String sqlText)
            throws SQLException {
        Statement s = null;
        try {
            s = c.createStatement();
            ResultSet results = s.executeQuery(sqlText);
            results.next();
            Object ret = results.getObject(1);
            results.close();
            return ret;
        } finally {
            closeQuietly(s);
        }
    }

    /**
     * Transforms a list into the format which is needed by the IN operator in
     * SQL. For example for a list containing 1, 2, 3 the returned string will
     * be <code>"(1, 2, 3)"<code>.
     * @param <E>           The type of the element.
     * @param collection    The list of elements
     * @return              A string containing the elements.
     */
    private <E> String sqlList(Collection<E> collection) {
        if (collection.isEmpty())
            return "()";

        int estimateSize = collection.size() * 4;
        StringBuilder builder = new StringBuilder(estimateSize);

        builder.append('(');
        for (E e : collection) {
            builder.append(e.toString()).append(',');
        }
        builder.deleteCharAt(builder.length() - 1).append(')');

        return builder.toString();
    }


    /**
     * Initalizes a list of settings that can have their values modified later
     * on.
     * @param c             The open connection.
     * @param keyvalues     An array of key-value pairs.
     * @throws SQLException
     */
    private void initializeValues(Connection c, String... keyvalues)
            throws SQLException {
        if (keyvalues.length % 2 != 0)
            throw new RuntimeException("The last key is missing a value.");

        PreparedStatement ps = null;
        try {
            ps = c.prepareStatement("INSERT INTO settings VALUES (?, ?)");
            for (int i = 0; i < keyvalues.length; i += 2) {
                ps.setString(1, keyvalues[i]);
                ps.setString(2, keyvalues[i+1]);
                ps.addBatch();
            }
            ps.executeBatch();
        } finally {
            closeQuietly(ps);
        }
    }

    /**
     * Returns a value from the settings table as a string.
     * @param c             The open connection.
     * @param key           Identifier-like string.
     * @return              The property as a string.
     * @throws SQLException
     */
    private String getValue(Connection c, String key) throws SQLException {
        Statement s = null;
        try {
            s = c.createStatement();
            ResultSet results = s.executeQuery(
                "SELECT value " +
                "FROM settings " +
                "WHERE key = '" + key + "'"
            );

            if (!results.next())
                throw new NeguraError("Key '%s' doesn't exist.", key);

            String ret = results.getString(1);
            results.close();

            return ret;
        } finally {
            closeQuietly(s);
        }
    }

    /**
     * Updates a list of key-value pairs; the settings must have been previously
     * initialized with {@link DataManager#initializeValues(java.sql.Connection,
     * java.lang.String[])}.
     * @param c             The open connection.
     * @param keyvalues     The array of key-value pairs.
     * @throws SQLException
     */
    private void setValues(Connection c, String... keyvalues)
            throws SQLException {
        if (keyvalues.length % 2 != 0)
            throw new RuntimeException("The last key is missing a value.");

        PreparedStatement ps = null;
        try {
            ps = c.prepareStatement("UPDATE settings SET value = ? " +
                    "WHERE key = ?");
            for (int i = 0; i < keyvalues.length; i += 2) {
                ps.setString(1, keyvalues[i + 1]); // value
                ps.setString(2, keyvalues[i]);     // key
                ps.addBatch();
            }
            ps.executeBatch();
        } finally {
            closeQuietly(ps);
        }
    }
}
