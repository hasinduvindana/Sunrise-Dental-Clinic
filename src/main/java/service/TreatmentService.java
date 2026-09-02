package service;

import dao.DAOFactory;
import dao.TreatmentDAO;
import exception.ConflictException;
import exception.ForbiddenException;
import exception.NotFoundException;
import model.Role;
import model.Treatment;
import model.User;
import util.Validator;

import java.util.List;
import java.util.Map;

/** Maintains the treatment price list. Admin and super admin only. */
public class TreatmentService {

    private final TreatmentDAO treatmentDAO;

    public TreatmentService() {
        this(DAOFactory.getInstance().treatments());
    }

    public TreatmentService(TreatmentDAO treatmentDAO) {
        this.treatmentDAO = treatmentDAO;
    }

    public List<Treatment> list(boolean activeOnly) {
        return treatmentDAO.findAll(activeOnly);
    }

    public Treatment get(int id) {
        Treatment treatment = treatmentDAO.findById(id);
        if (treatment == null) {
            throw new NotFoundException("That treatment is not in the price list");
        }
        return treatment;
    }

    public Treatment create(Map<String, Object> body, User actor) {
        requireAdmin(actor);
        Treatment t = new Treatment();
        t.setCode(Validator.requireText(body, "code", 20).toUpperCase());
        if (treatmentDAO.codeExists(t.getCode(), 0)) {
            throw new ConflictException("A treatment with that code already exists");
        }
        t.setName(Validator.requireText(body, "name", 120));
        t.setBasePrice(Validator.requireMoney(body, "basePrice"));
        t.setDurationMinutes(Validator.optionalInt(body, "durationMinutes", 30));
        t.setStatus("ACTIVE");
        treatmentDAO.insert(t);
        return t;
    }

    public Treatment update(int id, Map<String, Object> body, User actor) {
        requireAdmin(actor);
        Treatment t = get(id);
        t.setCode(Validator.requireText(body, "code", 20).toUpperCase());
        if (treatmentDAO.codeExists(t.getCode(), id)) {
            throw new ConflictException("Another treatment already uses that code");
        }
        t.setName(Validator.requireText(body, "name", 120));
        t.setBasePrice(Validator.requireMoney(body, "basePrice"));
        t.setDurationMinutes(Validator.optionalInt(body, "durationMinutes", t.getDurationMinutes()));
        if (body.containsKey("status")) {
            t.setStatus(Validator.requireOneOf(body, "status", "ACTIVE", "INACTIVE"));
        }
        treatmentDAO.update(t);
        return t;
    }

    /** Treatments are retired rather than deleted so old bills still resolve. */
    public void retire(int id, User actor) {
        requireAdmin(actor);
        get(id);
        treatmentDAO.delete(id);
    }

    private void requireAdmin(User actor) {
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.SUPER_ADMIN) {
            throw new ForbiddenException("Only an admin can change the price list");
        }
    }
}
