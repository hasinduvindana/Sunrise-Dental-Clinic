package service;

import dao.DAOFactory;
import dao.ReportDAO;
import exception.ForbiddenException;
import model.Role;
import model.User;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Management reporting: income, patients, treatments and per-doctor earnings.
 * A doctor may only see their own numbers; admins see everything.
 */
public class ReportService {

    private final ReportDAO reportDAO;

    public ReportService() {
        this(DAOFactory.getInstance().reports());
    }

    public ReportService(ReportDAO reportDAO) {
        this.reportDAO = reportDAO;
    }

    public Map<String, Object> dashboard(User actor) {
        if (actor.getRole() == Role.PATIENT) {
            throw new ForbiddenException("This dashboard is for clinic staff");
        }
        Map<String, Object> summary = new LinkedHashMap<>(reportDAO.dashboardSummary());
        summary.put("incomeTrend", reportDAO.incomeTrend(14));
        if (actor.getRole() == Role.DOCTOR) {
            // A doctor sees their own earnings, not the whole clinic's.
            summary.remove("incomeToday");
            summary.remove("incomeMonth");
            summary.remove("outstanding");
            summary.put("myIncomeMonth", totalOf(reportDAO.doctorIncome(
                    actor.getId(), firstOfMonth(), today()), "totalIncome"));
        }
        return summary;
    }

    public List<Map<String, Object>> income(String from, String to, User actor) {
        requireAdmin(actor);
        return reportDAO.incomeReport(orDefault(from, firstOfMonth()), orDefault(to, today()));
    }

    public List<Map<String, Object>> incomeByDoctor(String from, String to, User actor) {
        requireAdmin(actor);
        return reportDAO.incomeByDoctor(orDefault(from, firstOfMonth()), orDefault(to, today()));
    }

    public List<Map<String, Object>> patients(String from, String to, User actor) {
        requireAdmin(actor);
        return reportDAO.patientReport(orDefault(from, "2000-01-01"), orDefault(to, today()));
    }

    public List<Map<String, Object>> treatments(String from, String to, User actor) {
        requireAdmin(actor);
        return reportDAO.treatmentBreakdown(orDefault(from, firstOfMonth()), orDefault(to, today()));
    }

    /** The doctor's own income page. Admins may pass any doctorId. */
    public List<Map<String, Object>> doctorIncome(Integer doctorId, String from, String to, User actor) {
        int target;
        if (actor.getRole() == Role.DOCTOR) {
            target = actor.getId();
        } else if (actor.getRole().isAdministrative()) {
            target = doctorId == null ? 0 : doctorId;
            if (target == 0) {
                throw new ForbiddenException("Choose a doctor for this report");
            }
        } else {
            throw new ForbiddenException("Your role cannot read income figures");
        }
        return reportDAO.doctorIncome(target, orDefault(from, firstOfMonth()), orDefault(to, today()));
    }

    // ------------------------------------------------------------------

    private void requireAdmin(User actor) {
        if (!actor.getRole().isAdministrative()) {
            throw new ForbiddenException("Only an admin can run this report");
        }
    }

    private double totalOf(List<Map<String, Object>> rows, String key) {
        double total = 0;
        for (Map<String, Object> row : rows) {
            Object value = row.get(key);
            if (value instanceof Number) {
                total += ((Number) value).doubleValue();
            }
        }
        return total;
    }

    private String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String today() {
        return LocalDate.now().toString();
    }

    private String firstOfMonth() {
        return LocalDate.now().withDayOfMonth(1).toString();
    }
}
