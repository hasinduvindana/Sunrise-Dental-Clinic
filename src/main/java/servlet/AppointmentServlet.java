package servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import model.Role;
import model.User;
import service.AppointmentService;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;

/**
 * GET   /api/appointments?date=            everything on a date (front desk)
 * GET   /api/appointments/mine             the signed-in patient's bookings
 * GET   /api/appointments/search?no=APT-.. the brief's "search by number"
 * GET   /api/appointments/{id}             one booking
 * POST  /api/appointments                  book / admit / assign
 * POST  /api/appointments/{id}/check-in    mark the patient as arrived
 * PATCH /api/appointments/{id}/status      progress or cancel
 * PUT   /api/appointments/{id}             change treatment or notes
 */
@WebServlet("/api/appointments/*")
public class AppointmentServlet extends BaseServlet {

    private final AppointmentService appointmentService = new AppointmentService();
    private final service.PatientService patientService = new service.PatientService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User actor = requireUser(req);
        String[] parts = pathParts(req);

        if (parts.length == 0) {
            if (actor.getRole() == Role.PATIENT) {
                sendError(resp, 403, "Use /api/appointments/mine to see your own bookings");
                return;
            }
            String date = req.getParameter("date");
            if (date == null || date.isBlank()) {
                date = LocalDate.now().toString();
            }
            if (actor.getRole() == Role.DOCTOR) {
                sendOk(resp, mapAll(appointmentService.listForDoctor(actor.getId(), date)));
                return;
            }
            sendOk(resp, mapAll(appointmentService.listForDate(date)));
            return;
        }

        if ("mine".equals(parts[0])) {
            int patientId = patientService.getByUserId(actor.getId()).getId();
            sendOk(resp, mapAll(appointmentService.listForPatient(patientId)));
            return;
        }

        if ("search".equals(parts[0])) {
            String number = req.getParameter("no");
            if (number == null || number.isBlank()) {
                sendError(resp, 400, "Enter an appointment number to search for");
                return;
            }
            sendOk(resp, appointmentService.getByNumber(number.trim()).toMap());
            return;
        }

        sendOk(resp, appointmentService.get(intPart(parts[0], "Appointment id")).toMap());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User actor = requireUser(req);
        String[] parts = pathParts(req);

        if (parts.length == 0) {
            sendCreated(resp, appointmentService.book(readBody(req), actor).toMap());
            return;
        }
        if (parts.length == 2 && "check-in".equals(parts[1])) {
            sendOk(resp, appointmentService.checkIn(intPart(parts[0], "Appointment id"), actor).toMap());
            return;
        }
        sendError(resp, 404, "Unknown appointment endpoint");
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User actor = requireUser(req);
        String[] parts = pathParts(req);
        if (parts.length != 1) {
            sendError(resp, 400, "Give the appointment id in the URL");
            return;
        }
        sendOk(resp, appointmentService.update(intPart(parts[0], "Appointment id"), readBody(req), actor).toMap());
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws jakarta.servlet.ServletException, IOException {
        if ("PATCH".equalsIgnoreCase(req.getMethod())) {
            req.setCharacterEncoding("UTF-8");
            resp.setCharacterEncoding("UTF-8");
            try {
                User actor = requireUser(req);
                String[] parts = pathParts(req);
                if (parts.length == 2 && "status".equals(parts[1])) {
                    Map<String, Object> body = readBody(req);
                    sendOk(resp, appointmentService.changeStatus(
                            intPart(parts[0], "Appointment id"),
                            String.valueOf(body.getOrDefault("status", "")), actor).toMap());
                } else {
                    sendError(resp, 404, "Unknown appointment endpoint");
                }
            } catch (exception.AppException e) {
                sendError(resp, e.getStatusCode(), e.getMessage());
            }
            return;
        }
        super.service(req, resp);
    }
}
