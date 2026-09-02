package service;

import dao.DAOFactory;
import dao.SettingsDAO;
import exception.ForbiddenException;
import exception.ValidationException;
import model.Role;
import model.User;

import java.util.Map;

/**
 * The "general settings" screen: clinic name, contact details, tax rate and
 * the VIP discount. Only the super admin may write; every signed-in user may
 * read, because the clinic name and currency appear on every page.
 */
public class SettingsService {

    /** Keys the UI is allowed to write, with a light type check on each. */
    private static final String[] WRITABLE = {
            "clinic.name", "clinic.address", "clinic.phone", "clinic.email", "clinic.logo",
            "billing.tax.percent", "billing.vip.discount.percent", "billing.currency",
            "billing.bill.prefix", "session.default.max.patients"
    };

    private final SettingsDAO settingsDAO;

    public SettingsService() {
        this(DAOFactory.getInstance().settings());
    }

    public SettingsService(SettingsDAO settingsDAO) {
        this.settingsDAO = settingsDAO;
    }

    public Map<String, Object> all() {
        return settingsDAO.findAll();
    }

    public String get(String key, String fallback) {
        return settingsDAO.get(key, fallback);
    }

    public Map<String, Object> update(Map<String, Object> body, User actor) {
        if (actor.getRole() != Role.SUPER_ADMIN && actor.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Only an admin can change the clinic settings");
        }
        for (String key : WRITABLE) {
            if (!body.containsKey(key)) {
                continue;
            }
            String value = body.get(key) == null ? "" : String.valueOf(body.get(key)).trim();
            validate(key, value);
            settingsDAO.put(key, value, actor.getId());
        }
        return settingsDAO.findAll();
    }

    private void validate(String key, String value) {
        switch (key) {
            case "clinic.name":
                if (value.isBlank()) {
                    throw new ValidationException("The clinic name cannot be empty");
                }
                break;
            case "billing.tax.percent":
            case "billing.vip.discount.percent":
                double percent = parseNumber(value, key);
                if (percent < 0 || percent > 100) {
                    throw new ValidationException("A percentage must be between 0 and 100");
                }
                break;
            case "session.default.max.patients":
                double limit = parseNumber(value, key);
                if (limit < 1 || limit > 200) {
                    throw new ValidationException("The default patient limit must be between 1 and 200");
                }
                break;
            case "billing.bill.prefix":
                if (value.length() > 6) {
                    throw new ValidationException("The bill prefix must be 6 characters or fewer");
                }
                break;
            default:
                break;
        }
    }

    private double parseNumber(String value, String key) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new ValidationException(key + " must be a number");
        }
    }
}
