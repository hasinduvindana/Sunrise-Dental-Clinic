package servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import model.User;
import service.ReportService;

import java.io.IOException;

/**
 * Management reporting.
 *
 * GET /api/reports/dashboard                     headline cards + 14-day trend
 * GET /api/reports/income?from=&to=              daily income (stored procedure)
 * GET /api/reports/income-by-doctor?from=&to=    per-doctor totals
 * GET /api/reports/doctor-income?doctorId=       a doctor's own earnings
 * GET /api/reports/patients?from=&to=            patient register report
 * GET /api/reports/treatments?from=&to=          which treatments earn most
 */
@WebServlet("/api/reports/*")
public class ReportServlet extends BaseServlet {

    private final ReportService reportService = new ReportService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User actor = requireUser(req);
        String[] parts = pathParts(req);
        String kind = parts.length > 0 ? parts[0] : "dashboard";
        String from = req.getParameter("from");
        String to = req.getParameter("to");

        switch (kind) {
            case "dashboard":
                sendOk(resp, reportService.dashboard(actor));
                return;
            case "income":
                sendOk(resp, reportService.income(from, to, actor));
                return;
            case "income-by-doctor":
                sendOk(resp, reportService.incomeByDoctor(from, to, actor));
                return;
            case "doctor-income":
                sendOk(resp, reportService.doctorIncome(intParam(req, "doctorId"), from, to, actor));
                return;
            case "patients":
                sendOk(resp, reportService.patients(from, to, actor));
                return;
            case "treatments":
                sendOk(resp, reportService.treatments(from, to, actor));
                return;
            default:
                sendError(resp, 404, "Unknown report: " + kind);
        }
    }
}
