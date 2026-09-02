package listener;

import dao.DAOFactory;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import model.Role;
import model.User;
import service.notify.AuditTrailListener;
import service.notify.EventBus;
import service.notify.QueueDisplayListener;
import util.AppConfig;
import util.DBConnection;
import util.PasswordUtil;

import java.io.File;

/**
 * Runs once when Tomcat deploys the application:
 *  - registers the observers on the event bus
 *  - checks the database is reachable and reports it in the log
 *  - creates the first super admin if no super admin exists yet
 *  - makes sure the report storage folder exists
 */
@WebListener
public class AppBootstrapListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("[Sunrise] starting up");

        EventBus.get().register(new AuditTrailListener());
        EventBus.get().register(QueueDisplayListener.get());
        System.out.println("[Sunrise] " + EventBus.get().listenerCount() + " event listeners registered");

        File storage = new File(AppConfig.get().reportStorageDir());
        if (storage.mkdirs()) {
            System.out.println("[Sunrise] created report storage at " + storage.getAbsolutePath());
        }

        if (!DBConnection.isReachable()) {
            System.err.println("[Sunrise] the database is not reachable - check db.url, db.user and db.password "
                    + "in app.properties, and that the schema scripts have been run");
            return;
        }

        seedSuperAdmin();
        DemoDataSeeder.seed();
    }

    /**
     * The first super admin is created here rather than in the SQL seed so the
     * stored hash always matches PasswordUtil. Change the password immediately
     * after the first sign-in.
     */
    private void seedSuperAdmin() {
        try {
            if (DAOFactory.getInstance().users().countByRole(Role.SUPER_ADMIN) > 0) {
                return;
            }
            AppConfig cfg = AppConfig.get();
            String username = cfg.defaultSuperAdminUser();
            String password = cfg.defaultSuperAdminPassword();
            String salt = PasswordUtil.newSalt();

            User admin = new User();
            admin.setUsername(username);
            admin.setSalt(salt);
            admin.setPasswordHash(PasswordUtil.hash(password, salt));
            admin.setRole(Role.SUPER_ADMIN);
            admin.setFullName("System Super Admin");
            admin.setEmail("admin@sunrisedental.lk");
            admin.setStatus("ACTIVE");
            DAOFactory.getInstance().users().insert(admin);

            System.out.println("[Sunrise] created the first super admin: " + username
                    + " / " + password + "  <- change this password now");
        } catch (RuntimeException e) {
            System.err.println("[Sunrise] could not create the first super admin: " + e.getMessage());
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        System.out.println("[Sunrise] shutting down");
    }
}
