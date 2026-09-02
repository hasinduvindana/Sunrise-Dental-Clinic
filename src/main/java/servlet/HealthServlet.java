package servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import util.DBConnection;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GET /api/health - used by the deployment workflow and by the front end to
 * tell "the server is down" apart from "your session expired".
 */
@WebServlet("/api/health")
public class HealthServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> status = new LinkedHashMap<>();
        boolean db = DBConnection.isReachable();
        status.put("status", db ? "UP" : "DEGRADED");
        status.put("database", db ? "connected" : "unreachable");
        status.put("time", LocalDateTime.now().toString());
        sendJson(resp, db ? 200 : 503, status);
    }
}
