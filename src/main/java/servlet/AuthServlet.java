package servlet;

import dao.DAOFactory;
import exception.NotFoundException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import model.Patient;
import model.Role;
import model.User;
import service.AuthService;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * POST /api/auth/login            sign in
 * POST /api/auth/logout           sign out
 * GET  /api/auth/me               who am I
 * POST /api/auth/change-password  change my own password
 */
@WebServlet("/api/auth/*")
public class AuthServlet extends BaseServlet {

    private final AuthService authService = new AuthService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String[] parts = pathParts(req);
        String action = parts.length > 0 ? parts[0] : "";

        switch (action) {
            case "login": {
                Map<String, Object> body = readBody(req);
                String username = String.valueOf(body.getOrDefault("username", ""));
                String password = String.valueOf(body.getOrDefault("password", ""));
                User user = authService.login(username, password);

                // New session id on sign-in: defeats session fixation.
                HttpSession old = req.getSession(false);
                if (old != null) {
                    old.invalidate();
                }
                HttpSession session = req.getSession(true);
                session.setAttribute(SESSION_USER, user);
                session.setMaxInactiveInterval(30 * 60);

                DAOFactory.getInstance().audit().log(user.getId(), user.getRole().name(),
                        "LOGIN", "USER", String.valueOf(user.getId()), "Signed in");

                sendOk(resp, profile(user));
                return;
            }
            case "logout": {
                HttpSession session = req.getSession(false);
                if (session != null) {
                    User user = sessionUser(req);
                    if (user != null) {
                        DAOFactory.getInstance().audit().log(user.getId(), user.getRole().name(),
                                "LOGOUT", "USER", String.valueOf(user.getId()), "Signed out");
                    }
                    session.invalidate();
                }
                sendMessage(resp, "You have been signed out");
                return;
            }
            case "change-password": {
                User user = requireUser(req);
                Map<String, Object> body = readBody(req);
                authService.changePassword(user.getId(),
                        String.valueOf(body.getOrDefault("currentPassword", "")),
                        String.valueOf(body.getOrDefault("newPassword", "")));
                sendMessage(resp, "Your password has been changed");
                return;
            }
            default:
                sendError(resp, 404, "Unknown authentication action");
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String[] parts = pathParts(req);
        if (parts.length > 0 && "me".equals(parts[0])) {
            sendOk(resp, profile(requireUser(req)));
            return;
        }
        sendError(resp, 404, "Unknown authentication endpoint");
    }

    /** The session payload the browser keeps: identity plus the home page for the role. */
    private Map<String, Object> profile(User user) {
        Map<String, Object> map = new LinkedHashMap<>(user.toMap());
        map.put("home", homePage(user.getRole()));
        // The POS screens identify accounts as USR-003 rather than 3.
        map.put("posId", pos.PosIds.user(user.getId()));
        if (user.getRole() == Role.PATIENT) {
            try {
                Patient patient = DAOFactory.getInstance().patients().findByUserId(user.getId());
                if (patient != null) {
                    map.put("patientId", patient.getId());
                    map.put("patientNo", patient.getPatientNo());
                }
            } catch (NotFoundException ignored) {
                // A patient login with no clinical record yet is still allowed in.
            }
        }
        return map;
    }

    /**
     * Every member of staff shares one dashboard page: it renders the menu and
     * the screens that the signed-in role is allowed to see. Patients get the
     * public portal instead.
     */
    private String homePage(Role role) {
        return role == Role.PATIENT ? "index.html" : "dashboard.html";
    }
}
