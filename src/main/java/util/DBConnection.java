package util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Central JDBC connection provider.
 *
 * DESIGN PATTERN: Singleton. One place owns the driver registration and the
 * credentials; every DAO asks this class for a fresh connection and closes it
 * with try-with-resources. Handing out a *new* connection each time (rather
 * than sharing one static Connection) is deliberate - servlet containers are
 * multi-threaded and a shared connection would be closed underneath other
 * requests.
 */
public final class DBConnection {

    private static volatile boolean driverLoaded = false;

    private DBConnection() { }

    public static Connection getConnection() throws SQLException {
        if (!driverLoaded) {
            synchronized (DBConnection.class) {
                if (!driverLoaded) {
                    try {
                        Class.forName(AppConfig.get().dbDriver());
                        driverLoaded = true;
                    } catch (ClassNotFoundException e) {
                        throw new SQLException("JDBC driver not found on the classpath: "
                                + AppConfig.get().dbDriver(), e);
                    }
                }
            }
        }
        AppConfig cfg = AppConfig.get();
        return DriverManager.getConnection(cfg.dbUrl(), cfg.dbUser(), cfg.dbPassword());
    }

    /** Quick start-up probe so failures are visible in the Tomcat log. */
    public static boolean isReachable() {
        try (Connection c = getConnection()) {
            return c != null && !c.isClosed();
        } catch (SQLException e) {
            System.err.println("[DBConnection] database unreachable: " + e.getMessage());
            return false;
        }
    }
}
