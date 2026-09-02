package servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import model.User;
import service.TreatmentService;

import java.io.IOException;

/**
 * GET    /api/treatments?all=true   price list
 * POST   /api/treatments            add
 * PUT    /api/treatments/{id}       edit
 * DELETE /api/treatments/{id}       retire
 */
@WebServlet("/api/treatments/*")
public class TreatmentServlet extends BaseServlet {

    private final TreatmentService treatmentService = new TreatmentService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        requireUser(req);
        String[] parts = pathParts(req);
        if (parts.length == 1) {
            sendOk(resp, treatmentService.get(intPart(parts[0], "Treatment id")).toMap());
            return;
        }
        boolean activeOnly = !"true".equalsIgnoreCase(req.getParameter("all"));
        sendOk(resp, mapAll(treatmentService.list(activeOnly)));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User actor = requireUser(req);
        sendCreated(resp, treatmentService.create(readBody(req), actor).toMap());
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User actor = requireUser(req);
        String[] parts = pathParts(req);
        if (parts.length != 1) {
            sendError(resp, 400, "Give the treatment id in the URL");
            return;
        }
        sendOk(resp, treatmentService.update(intPart(parts[0], "Treatment id"), readBody(req), actor).toMap());
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User actor = requireUser(req);
        String[] parts = pathParts(req);
        if (parts.length != 1) {
            sendError(resp, 400, "Give the treatment id in the URL");
            return;
        }
        treatmentService.retire(intPart(parts[0], "Treatment id"), actor);
        sendMessage(resp, "The treatment has been retired from the price list");
    }
}
