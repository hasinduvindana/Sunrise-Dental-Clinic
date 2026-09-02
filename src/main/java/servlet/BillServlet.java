package servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import model.Role;
import model.User;
import service.BillingService;
import service.PatientService;

import java.io.IOException;

/**
 * GET    /api/bills?status=&from=&to=&patientId=&doctorId=   list
 * GET    /api/bills/mine                                     the patient's own bills
 * GET    /api/bills/{id}                                     one bill with its lines
 * GET    /api/bills/{id}/receipt                             printable receipt payload
 * GET    /api/bills/{id}/payments                            settlement history
 * GET    /api/bills/appointment/{id}                         bill raised for a visit
 * POST   /api/bills                                          generate
 * POST   /api/bills/pay                                      settle (counter or portal)
 * DELETE /api/bills/{id}                                     cancel an unpaid bill
 */
@WebServlet("/api/bills/*")
public class BillServlet extends BaseServlet {

    private final BillingService billingService = new BillingService();
    private final PatientService patientService = new PatientService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User actor = requireUser(req);
        String[] parts = pathParts(req);

        if (parts.length == 0) {
            if (actor.getRole() == Role.PATIENT) {
                sendError(resp, 403, "Use /api/bills/mine to see your own bills");
                return;
            }
            Integer doctorId = intParam(req, "doctorId");
            if (actor.getRole() == Role.DOCTOR) {
                doctorId = actor.getId();
            }
            sendOk(resp, mapAll(billingService.list(
                    req.getParameter("status"),
                    req.getParameter("from"),
                    req.getParameter("to"),
                    intParam(req, "patientId"),
                    doctorId)));
            return;
        }

        if ("mine".equals(parts[0])) {
            int patientId = patientService.getByUserId(actor.getId()).getId();
            sendOk(resp, mapAll(billingService.list(null, null, null, patientId, null)));
            return;
        }

        if (parts.length == 2 && "appointment".equals(parts[0])) {
            var bill = billingService.findForAppointment(intPart(parts[1], "Appointment id"));
            sendOk(resp, bill == null ? null : bill.toMap());
            return;
        }

        int id = intPart(parts[0], "Bill id");

        if (parts.length == 2 && "receipt".equals(parts[1])) {
            assertMayRead(actor, id);
            sendOk(resp, billingService.receipt(id));
            return;
        }
        if (parts.length == 2 && "payments".equals(parts[1])) {
            assertMayRead(actor, id);
            sendOk(resp, mapAll(billingService.payments(id)));
            return;
        }

        assertMayRead(actor, id);
        sendOk(resp, billingService.get(id).toMap());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User actor = requireUser(req);
        String[] parts = pathParts(req);

        if (parts.length == 1 && "pay".equals(parts[0])) {
            sendOk(resp, billingService.pay(readBody(req), actor).toMap());
            return;
        }
        if (parts.length == 0) {
            sendCreated(resp, billingService.generate(readBody(req), actor).toMap());
            return;
        }
        sendError(resp, 404, "Unknown billing endpoint");
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User actor = requireRole(req, Role.ADMIN, Role.SUPER_ADMIN);
        String[] parts = pathParts(req);
        if (parts.length != 1) {
            sendError(resp, 400, "Give the bill id in the URL");
            return;
        }
        billingService.cancel(intPart(parts[0], "Bill id"), actor);
        sendMessage(resp, "The bill has been cancelled");
    }

    private void assertMayRead(User actor, int billId) {
        if (actor.getRole().isStaff()) {
            return;
        }
        int patientId = patientService.getByUserId(actor.getId()).getId();
        if (billingService.get(billId).getPatientId() != patientId) {
            throw new exception.ForbiddenException("You can only view your own bills");
        }
    }
}
