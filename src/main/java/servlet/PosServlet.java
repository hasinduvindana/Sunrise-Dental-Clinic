package servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import model.User;
import pos.PosService;

import java.io.IOException;
import java.util.Map;

/**
 * The endpoint the POS front end talks to.
 *
 * GET  /api/pos/state            the whole clinic document the browser caches
 * POST /api/pos/{command}        one action from a screen
 *
 * Commands: save-user, set-user-status, save-patient, save-session,
 * book-appointment, update-appointment-status, cancel-by-nic, create-invoice,
 * process-payment, add-report, set-doctor-fee, update-settings.
 *
 * Keeping the reads as one document and the writes as named commands means a
 * screen never has to know which tables its action touches, and every write
 * still lands in a single server-side transaction.
 */
@WebServlet("/api/pos/*")
public class PosServlet extends BaseServlet {

    private final PosService posService = new PosService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String[] parts = pathParts(req);
        if (parts.length == 1 && "public".equals(parts[0])) {
            sendOk(resp, posService.publicState());
            return;
        }
        User actor = requireUser(req);
        if (parts.length == 1 && "state".equals(parts[0])) {
            sendOk(resp, posService.state(actor));
            return;
        }
        sendError(resp, 404, "Unknown POS endpoint");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String[] parts = pathParts(req);
        if (parts.length == 2 && "public".equals(parts[0])) {
            sendOk(resp, posService.executePublic(parts[1], readBody(req)));
            return;
        }
        User actor = requireUser(req);
        if (parts.length != 1) {
            sendError(resp, 400, "Name the command in the URL, for example /api/pos/save-patient");
            return;
        }
        Map<String, Object> body = readBody(req);
        sendOk(resp, posService.execute(parts[0], body, actor));
    }
}
