package ocean.view.resort.manager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseManager {

    private static DatabaseManager instance;
    private final DataSource dataSource;

    public DatabaseManager(DataSource dataSource) {
        this.dataSource = dataSource;
        instance = this;
    }

    public static DatabaseManager getInstance() {
        return instance;
    }

    public static void init(DataSource dataSource) {
        instance = new DatabaseManager(dataSource);
    }

    public <T> T execute(ConnectionCallback<T> callback) {
        try (Connection conn = dataSource.getConnection()) {
            return callback.run(conn);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** Run multiple operations in one transaction. */
    public void executeInTransaction(ConnectionCallback<Void> callback) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                callback.run(conn);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @FunctionalInterface
    public interface ConnectionCallback<T> {
        T run(Connection conn) throws SQLException;
    }
}
