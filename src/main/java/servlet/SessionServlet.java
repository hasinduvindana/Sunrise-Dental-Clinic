package servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import model.Appointment;
import model.Role;
import model.User;
import service.AppointmentService;
import service.SessionService;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Doctor sessions and the live queue.
 *
 * GET    /api/sessions?doctorId=&date=&status=   list
 * GET    /api/sessions/bookable                  sessions a patient can join
 * GET    /api/sessions/mine                      the signed-in doctor's sessions
 * GET    /api/sessions/{id}                      one session
 * GET    /api/sessions/{id}/appointments         the queue list
 * GET    /api/sessions/{id}/queue                now-serving snapshot (public)
 * POST   /api/sessions                           create
 * POST   /api/sessions/{id}/call-next            call the next patient in
 * PUT    /api/sessions/{id}                      edit
 * PATCH  /api/sessions/{id}/status               open / close / cancel
 * DELETE /api/sessions/{id}                      delete an empty session
 */
@WebServlet("/api/sessions/*")
public class SessionServlet extends BaseServlet {

    private final SessionService sessionService = new SessionService();
    private final AppointmentService appointmentService = new AppointmentService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String[] parts = pathParts(req);

        // The waiting-room display is public: it shows numbers, not patient names.
        if (parts.length == 2 && "queue".equals(parts[1])) {
            sendOk(resp, sessionService.queueStatus(intPart(parts[0], "Session id")));
            return;
        }

        User actor = requireUser(req);

        if (parts.length == 0) {
            Integer doctorId = intParam(req, "doctorId");
            if (actor.getRole() == Role.DOCTOR && doctorId == null) {
                doctorId = actor.getId();
            }
            sendOk(resp, mapAll(sessionService.list(doctorId, req.getParameter("date"), req.getParameter("status"))));
            return;
        }

        if ("bookable".equals(parts[0])) {
            sendOk(resp, mapAll(sessionService.bookable()));
            return;
        }

        if ("mine".equals(parts[0])) {
            User doctor = requireRole(req, Role.DOCTOR);
            sendOk(resp, mapAll(sessionService.list(doctor.getId(), req.getParameter("date"), null)));
            return;
        }

        int id = intPart(parts[0], "Session id");

        if (parts.length == 2 && "appointments".equals(parts[1])) {
            if (actor.getRole() == Role.PATIENT) {
                sendError(resp, 403, "Patients cannot see the full patient list for a session");
                return;
            }
            sendOk(resp, mapAll(appointmentService.listForSession(id)));
            return;
        }

        sendOk(resp, sessionService.get(id).toMap());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User actor = requireRole(req, Role.DOCTOR, Role.ADMIN, Role.SUPER_ADMIN);
        String[] parts = pathParts(req);

        if (parts.length == 0) {
            sendCreated(resp, sessionService.create(readBody(req), actor).toMap());
            return;
        }

        if (parts.length == 2 && "call-next".equals(parts[1])) {
            int sessionId = intPart(parts[0], "Session id");
            Appointment next = sessionService.callNext(sessionId, actor);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("queue", sessionService.queueStatus(sessionId));
            payload.put("current", next == null ? null : next.toMap());
            payload.put("message", next == null
                    ? "There is nobody else waiting in this session"
                    : "Now serving number " + next.getQueueNo() + " - " + next.getPatientName());
            sendOk(resp, payload);
            return;
        }

        sendError(resp, 404, "Unknown session endpoint");
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User actor = requireRole(req, Role.DOCTOR, Role.ADMIN, Role.SUPER_ADMIN);
        String[] parts = pathParts(req);
        if (parts.length != 1) {
            sendError(resp, 400, "Give the session id in the URL");
            return;
        }
        sendOk(resp, sessionService.update(intPart(parts[0], "Session id"), readBody(req), actor).toMap());
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws jakarta.servlet.ServletException, IOException {
        if ("PATCH".equalsIgnoreCase(req.getMethod())) {
            req.setCharacterEncoding("UTF-8");
            resp.setCharacterEncoding("UTF-8");
            try {
                User actor = requireRole(req, Role.DOCTOR, Role.ADMIN, Role.SUPER_ADMIN);
                String[] parts = pathParts(req);
                if (parts.length == 2 && "status".equals(parts[1])) {
                    Map<String, Object> body = readBody(req);
                    String status = String.valueOf(body.getOrDefault("status", "ACTIVE")).toUpperCase();
                    sendOk(resp, sessionService.changeStatus(
                            intPart(parts[0], "Session id"), status, actor).toMap());
                } else {
                    sendError(resp, 404, "Unknown session endpoint");
                }
            } catch (exception.AppException e) {
                sendError(resp, e.getStatusCode(), e.getMessage());
            }
            return;
        }
        super.service(req, resp);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User actor = requireRole(req, Role.DOCTOR, Role.ADMIN, Role.SUPER_ADMIN);
        String[] parts = pathParts(req);
        if (parts.length != 1) {
            sendError(resp, 400, "Give the session id in the URL");
            return;
        }
        sessionService.delete(intPart(parts[0], "Session id"), actor);
        sendMessage(resp, "The session has been deleted");
    }
}
