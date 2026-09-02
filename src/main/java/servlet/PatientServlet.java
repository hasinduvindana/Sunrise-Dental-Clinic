package servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import model.Patient;
import model.Role;
import model.User;
import service.AppointmentService;
import service.MedicalReportService;
import service.PatientService;
import service.PrescriptionService;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GET    /api/patients?search=          list / search
 * GET    /api/patients/me               the signed-in patient's own record
 * GET    /api/patients/{id}             one record
 * GET    /api/patients/{id}/history     appointments, prescriptions and reports
 * POST   /api/patients                  register (admin / super admin)
 * POST   /api/patients/{id}/login       issue a portal login
 * PUT    /api/patients/{id}             edit
 * DELETE /api/patients/{id}             remove (super admin)
 */
@WebServlet("/api/patients/*")
public class PatientServlet extends BaseServlet {

    private final PatientService patientService = new PatientService();
    private final AppointmentService appointmentService = new AppointmentService();
    private final PrescriptionService prescriptionService = new PrescriptionService();
    private final MedicalReportService reportService = new MedicalReportService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User actor = requireUser(req);
        String[] parts = pathParts(req);

        if (parts.length == 0) {
            if (actor.getRole() == Role.PATIENT) {
                sendError(resp, 403, "Your role does not have access to the patient register");
                return;
            }
            sendOk(resp, mapAll(patientService.search(req.getParameter("search"))));
            return;
        }

        if ("me".equals(parts[0])) {
            Patient self = patientService.getByUserId(actor.getId());
            sendOk(resp, self.toMap());
            return;
        }

        int id = intPart(parts[0], "Patient id");

        if (parts.length == 2 && "history".equals(parts[1])) {
            assertMayRead(actor, id);
            Map<String, Object> history = new LinkedHashMap<>();
            history.put("patient", patientService.get(id).toMap());
            history.put("appointments", mapAll(appointmentService.listForPatient(id)));
            history.put("prescriptions", mapAll(prescriptionService.forPatient(id, actor)));
            history.put("reports", mapAll(reportService.forPatient(id, actor)));
            sendOk(resp, history);
            return;
        }

        assertMayRead(actor, id);
        sendOk(resp, patientService.get(id).toMap());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User actor = requireUser(req);
        String[] parts = pathParts(req);

        if (parts.length == 0) {
            Patient created = patientService.register(readBody(req), actor);
            sendCreated(resp, created.toMap());
            return;
        }
        if (parts.length == 2 && "login".equals(parts[1])) {
            int id = intPart(parts[0], "Patient id");
            sendOk(resp, patientService.createPortalLogin(id, readBody(req), actor).toMap());
            return;
        }
        sendError(resp, 404, "Unknown patient endpoint");
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User actor = requireUser(req);
        String[] parts = pathParts(req);
        if (parts.length != 1) {
            sendError(resp, 400, "Give the patient id in the URL");
            return;
        }
        sendOk(resp, patientService.update(intPart(parts[0], "Patient id"), readBody(req), actor).toMap());
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User actor = requireRole(req, Role.SUPER_ADMIN);
        String[] parts = pathParts(req);
        if (parts.length != 1) {
            sendError(resp, 400, "Give the patient id in the URL");
            return;
        }
        patientService.delete(intPart(parts[0], "Patient id"), actor);
        sendMessage(resp, "The patient record has been removed");
    }

    private void assertMayRead(User actor, int patientId) {
        if (actor.getRole().isStaff()) {
            return;
        }
        Patient self = patientService.getByUserId(actor.getId());
        if (self.getId() != patientId) {
            throw new exception.ForbiddenException("You can only view your own record");
        }
    }
}
