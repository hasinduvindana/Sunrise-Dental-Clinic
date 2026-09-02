package servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import model.Role;
import model.User;
import service.PatientService;
import service.PrescriptionService;

import java.io.IOException;

/**
 * GET  /api/prescriptions?patientId=       a patient's prescriptions
 * GET  /api/prescriptions/mine             the signed-in patient's own
 * GET  /api/prescriptions/{id}             one prescription
 * GET  /api/prescriptions/appointment/{id} the prescription for a visit
 * POST /api/prescriptions                  write one (doctor only)
 */
@WebServlet("/api/prescriptions/*")
public class PrescriptionServlet extends BaseServlet {

    private final PrescriptionService prescriptionService = new PrescriptionService();
    private final PatientService patientService = new PatientService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User actor = requireUser(req);
        String[] parts = pathParts(req);

        if (parts.length == 0) {
            Integer patientId = intParam(req, "patientId");
            if (patientId == null) {
                sendError(resp, 400, "Give a patientId to list prescriptions for");
                return;
            }
            sendOk(resp, mapAll(prescriptionService.forPatient(patientId, actor)));
            return;
        }

        if ("mine".equals(parts[0])) {
            int patientId = patientService.getByUserId(actor.getId()).getId();
            sendOk(resp, mapAll(prescriptionService.forPatient(patientId, actor)));
            return;
        }

        if (parts.length == 2 && "appointment".equals(parts[0])) {
            sendOk(resp, prescriptionService.forAppointment(intPart(parts[1], "Appointment id"), actor).toMap());
            return;
        }

        sendOk(resp, prescriptionService.get(intPart(parts[0], "Prescription id"), actor).toMap());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User actor = requireRole(req, Role.DOCTOR);
        sendCreated(resp, prescriptionService.create(readBody(req), actor).toMap());
    }
}
