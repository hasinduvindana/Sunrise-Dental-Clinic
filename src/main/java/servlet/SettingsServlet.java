package servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import model.User;
import service.SettingsService;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GET /api/settings         read (any signed-in user; also public branding)
 * PUT /api/settings         write (admin / super admin)
 */
@WebServlet("/api/settings/*")
public class SettingsServlet extends BaseServlet {

    private final SettingsService settingsService = new SettingsService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String[] parts = pathParts(req);
        if (parts.length == 1 && "public".equals(parts[0])) {
            // Used by the sign-in page, which has no session yet.
            Map<String, Object> branding = new LinkedHashMap<>();
            branding.put("clinic.name", settingsService.get("clinic.name", "Sunrise Dental Clinic"));
            branding.put("clinic.address", settingsService.get("clinic.address", ""));
            branding.put("clinic.phone", settingsService.get("clinic.phone", ""));
            branding.put("clinic.logo", settingsService.get("clinic.logo", "logo.png"));
            sendOk(resp, branding);
            return;
        }
        requireUser(req);
        sendOk(resp, settingsService.all());
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User actor = requireUser(req);
        sendOk(resp, settingsService.update(readBody(req), actor));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        doPut(req, resp);
    }
}
