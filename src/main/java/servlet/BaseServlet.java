package servlet;

import exception.AppException;
import exception.AuthException;
import exception.ForbiddenException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import model.Role;
import model.User;
import util.Json;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared plumbing for every API servlet.
 *
 * DESIGN PATTERN: Template Method. service() fixes the skeleton of request
 * handling - set encoding, run the subclass, translate any AppException into a
 * JSON error - and the subclasses fill in only doGet/doPost/doPut/doDelete.
 * Without this each servlet would repeat the same try/catch block.
 */
public abstract class BaseServlet extends HttpServlet {

    protected static final String SESSION_USER = "authenticatedUser";

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setCharacterEncoding("UTF-8");
        try {
            super.service(req, resp);
        } catch (AppException e) {
            sendError(resp, e.getStatusCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            sendError(resp, 400, e.getMessage());
        } catch (RuntimeException e) {
            // Log the detail for the operator, show a neutral message to the user.
            log("Unhandled failure in " + getClass().getSimpleName(), e);
            sendError(resp, 500, "Something went wrong on the server. Please try again.");
        }
    }

    // --------------------------- responses ---------------------------

    protected void sendJson(HttpServletResponse resp, int status, Object payload) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        try (PrintWriter out = resp.getWriter()) {
            out.write(Json.write(payload));
        }
    }

    protected void sendOk(HttpServletResponse resp, Object data) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", data);
        sendJson(resp, 200, body);
    }

    protected void sendCreated(HttpServletResponse resp, Object data) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", data);
        sendJson(resp, 201, body);
    }

    protected void sendMessage(HttpServletResponse resp, String message) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", message);
        sendJson(resp, 200, body);
    }

    protected void sendError(HttpServletResponse resp, int status, String message) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message == null ? "Request failed" : message);
        sendJson(resp, status, body);
    }

    /** Turns a list of models into a JSON array using each model's toMap(). */
    protected List<Map<String, Object>> mapAll(List<?> items) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object item : items) {
            list.add(toMap(item));
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object item) {
        try {
            return (Map<String, Object>) item.getClass().getMethod("toMap").invoke(item);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Model " + item.getClass() + " has no toMap()", e);
        }
    }

    // ---------------------------- requests ----------------------------

    /**
     * Reads the request body. A JSON content type is parsed as JSON; anything
     * else falls back to normal form parameters, so the same endpoints work
     * from fetch() and from a plain HTML form post.
     */
    protected Map<String, Object> readBody(HttpServletRequest req) throws IOException {
        String contentType = req.getContentType();
        if (contentType != null && contentType.toLowerCase().contains("application/json")) {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = req.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            return Json.parseObject(sb.toString());
        }
        Map<String, Object> map = new LinkedHashMap<>();
        Enumeration<String> names = req.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            map.put(name, req.getParameter(name));
        }
        return map;
    }

    /** "/api/users/12/status" behind /api/users/* gives ["12","status"]. */
    protected String[] pathParts(HttpServletRequest req) {
        String info = req.getPathInfo();
        if (info == null || info.isBlank() || "/".equals(info)) {
            return new String[0];
        }
        String trimmed = info.startsWith("/") ? info.substring(1) : info;
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.split("/");
    }

    protected int intPart(String value, String label) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " must be a number");
        }
    }

    protected Integer intParam(HttpServletRequest req, String name) {
        String value = req.getParameter(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a number");
        }
    }

    // ------------------------ authentication -------------------------

    /** The signed-in user, or null when there is no session. */
    protected User sessionUser(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null) {
            return null;
        }
        Object user = session.getAttribute(SESSION_USER);
        return user instanceof User ? (User) user : null;
    }

    /** The signed-in user, or a 401. */
    protected User requireUser(HttpServletRequest req) {
        User user = sessionUser(req);
        if (user == null) {
            throw new AuthException("Please sign in to continue");
        }
        return user;
    }

    protected User requireRole(HttpServletRequest req, Role... allowed) {
        User user = requireUser(req);
        for (Role role : allowed) {
            if (user.getRole() == role) {
                return user;
            }
        }
        throw new ForbiddenException("Your role does not have access to this function");
    }
}
