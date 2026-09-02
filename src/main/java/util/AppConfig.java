package util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Application configuration holder.
 *
 * DESIGN PATTERN: Singleton (eager, thread safe by class-loader guarantee).
 * Reads app.properties from the classpath and falls back to sensible
 * development defaults so the WAR still starts on a fresh machine.
 */
public final class AppConfig {

    private static final AppConfig INSTANCE = new AppConfig();

    private final Properties props = new Properties();

    private AppConfig() {
        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream("app.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            System.err.println("[AppConfig] could not read app.properties: " + e.getMessage());
        }
    }

    public static AppConfig get() {
        return INSTANCE;
    }

    public String value(String key, String defaultValue) {
        String v = System.getProperty(key, props.getProperty(key));
        return (v == null || v.isBlank()) ? defaultValue : v.trim();
    }

    public int intValue(String key, int defaultValue) {
        try {
            return Integer.parseInt(value(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public String dbUrl() {
        return value("db.url", "jdbc:mysql://localhost:3306/sunrise_dental"
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Colombo");
    }

    public String dbUser() {
        return value("db.user", "root");
    }

    /**
     * Which JDBC driver to register. MySQL is the default; a MariaDB server
     * (the one bundled with XAMPP, for instance) needs its own driver because
     * MySQL's Connector/J asks for system variables MariaDB does not have.
     */
    public String dbDriver() {
        return value("db.driver", "com.mysql.cj.jdbc.Driver");
    }

    public String dbPassword() {
        return value("db.password", "1234");
    }

    /** Folder on the server disk where medical report files are kept. */
    public String reportStorageDir() {
        return value("storage.reports.dir", System.getProperty("java.io.tmpdir") + "/sunrise-reports");
    }

    public String defaultSuperAdminUser() {
        return value("bootstrap.superadmin.username", "superadmin");
    }

    public String defaultSuperAdminPassword() {
        return value("bootstrap.superadmin.password", "Super@123");
    }
}
