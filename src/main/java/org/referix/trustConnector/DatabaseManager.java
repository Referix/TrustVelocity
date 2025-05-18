package org.referix.trustConnector;


import java.io.File;
import java.sql.*;
import java.util.Map;
import java.util.UUID;

public class DatabaseManager {

    private final File dbFile;
    private Connection connection;

    public DatabaseManager(File dataFolder) {
        this.dbFile = new File(dataFolder, "data.db");
    }

    public void connect() throws SQLException {
        if (!dbFile.getParentFile().exists()) {
            dbFile.getParentFile().mkdirs();
        }
        try {
            Class.forName("org.sqlite.JDBC");
            System.out.println("SQLite JDBC driver loaded successfully");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        createTable();
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {}
    }

    private void createTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS commands (" +
                "uuid TEXT PRIMARY KEY," +
                "command TEXT NOT NULL)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.executeUpdate();
        }
    }

    public void loadCommands(Map<UUID, String> map) throws SQLException {
        String sql = "SELECT uuid, command FROM commands";
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                String command = rs.getString("command");
                map.put(uuid, command);
            }
        }
    }

    public void saveCommands(Map<UUID, String> map) throws SQLException {
        String deleteSql = "DELETE FROM commands";
        String insertSql = "INSERT INTO commands (uuid, command) VALUES (?, ?)";
        try (PreparedStatement deleteStmt = connection.prepareStatement(deleteSql);
             PreparedStatement insertStmt = connection.prepareStatement(insertSql)) {
            connection.setAutoCommit(false);
            deleteStmt.executeUpdate();
            for (Map.Entry<UUID, String> entry : map.entrySet()) {
                insertStmt.setString(1, entry.getKey().toString());
                insertStmt.setString(2, entry.getValue());
                insertStmt.addBatch();
            }
            insertStmt.executeBatch();
            connection.commit();
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        }
    }

    public void deleteCommand(UUID uuid) throws SQLException {
        String sql = "DELETE FROM commands WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        }
    }

}

