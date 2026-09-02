package servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import model.Role;
import model.User;
import service.AuthService;
import service.UserService;

import java.io.IOException;
import java.util.Map;

/**
 * Staff accounts.
 *
 * GET    /api/users?role=DOCTOR&search=      list staff
 * GET    /api/users/{id}                     one account
 * POST   /api/users                          create (super admin: any; admin: cashier/nurse)
 * PUT    /api/users/{id}                     edit
 * PATCH  /api/users/{id}/status              activate / deactivate
 * POST   /api/users/{id}/reset-password      admin password reset
 * DELETE /api/users/{id}                     remove (super admin only)
 */
@WebServlet("/api/users/*")
public class UserServlet extends BaseServlet {

    private final UserService userService = new UserService();
    private final AuthService authService = new AuthService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User actor = requireUser(req);
        String[] parts = pathParts(req);

        if (parts.length == 0) {
            if (actor.getRole() == Role.PATIENT) {
                sendError(resp, 403, "Your role does not have access to this function");
                return;
            }
            Role filter = Role.of(req.getParameter("role"));
            sendOk(resp, mapAll(userService.listStaff(filter, req.getParameter("search"))));
            return;
        }

        if ("doctors".equals(parts[0])) {
            // Any signed-in user may see the doctor list: patients need it to book.
            sendOk(resp, mapAll(userService.listDoctors()));
            return;
        }

        int id = intPart(parts[0], "User id");
        sendOk(resp, userService.get(id).toMap());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User actor = requireRole(req, Role.SUPER_ADMIN, Role.ADMIN);
        String[] parts = pathParts(req);

        if (parts.length == 2 && "reset-password".equals(parts[1])) {
            int id = intPart(parts[0], "User id");
            Map<String, Object> body = readBody(req);
            authService.resetPassword(id, String.valueOf(body.getOrDefault("newPassword", "")), actor.getRole());
            sendMessage(resp, "The password has been reset");
            return;
        }

        if (parts.length == 0) {
            User created = userService.create(readBody(req), actor);
            sendCreated(resp, created.toMap());
            return;
        }

        sendError(resp, 404, "Unknown user endpoint");
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User actor = requireUser(req);
        String[] parts = pathParts(req);
        if (parts.length != 1) {
            sendError(resp, 400, "Give the account id in the URL");
            return;
        }
        int id = intPart(parts[0], "User id");
        sendOk(resp, userService.update(id, readBody(req), actor).toMap());
    }

    /** PATCH is not routed by HttpServlet, so it arrives here. */
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws jakarta.servlet.ServletException, IOException {
        if ("PATCH".equalsIgnoreCase(req.getMethod())) {
            req.setCharacterEncoding("UTF-8");
            resp.setCharacterEncoding("UTF-8");
            try {
                doPatch(req, resp);
            } catch (exception.AppException e) {
                sendError(resp, e.getStatusCode(), e.getMessage());
            }
            return;
        }
        super.service(req, resp);
    }

    private void doPatch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User actor = requireRole(req, Role.SUPER_ADMIN, Role.ADMIN);
        String[] parts = pathParts(req);
        if (parts.length == 2 && "status".equals(parts[1])) {
            int id = intPart(parts[0], "User id");
            Map<String, Object> body = readBody(req);
            userService.setStatus(id, String.valueOf(body.getOrDefault("status", "ACTIVE")), actor);
            sendMessage(resp, "The account status has been updated");
            return;
        }
        sendError(resp, 404, "Unknown user endpoint");
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User actor = requireRole(req, Role.SUPER_ADMIN);
        String[] parts = pathParts(req);
        if (parts.length != 1) {
            sendError(resp, 400, "Give the account id in the URL");
            return;
        }
        userService.delete(intPart(parts[0], "User id"), actor);
        sendMessage(resp, "The account has been deleted");
    }
}
