package pos;

import exception.ForbiddenException;
import exception.ValidationException;
import model.Role;
import model.User;
import service.notify.ClinicEvent;
import service.notify.EventBus;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Business layer for the POS screens.
 *
 * Every command names the roles allowed to run it, in one table, so the answer
 * to "who may do this?" is readable in a single place rather than scattered
 * through servlets. The browser also hides menu items by role, but that is
 * only convenience - this is the check that counts.
 */
public class PosService {

    private final PosStateDAO stateDAO;
    private final PosCommandDAO commandDAO;

    private static final Set<Role> MANAGEMENT = EnumSet.of(Role.SUPER_ADMIN, Role.ADMIN);
    private static final Set<Role> FRONT_DESK =
            EnumSet.of(Role.SUPER_ADMIN, Role.ADMIN, Role.PATIENT_ADMIN);
    private static final Set<Role> CLINICAL =
            EnumSet.of(Role.SUPER_ADMIN, Role.ADMIN, Role.DOCTOR, Role.NURSE);
    private static final Set<Role> COUNTER =
            EnumSet.of(Role.SUPER_ADMIN, Role.ADMIN, Role.CASHIER);

    public PosService() {
        this(new PosStateDAO(), new PosCommandDAO());
    }

    /** Constructor injection so the service can be unit tested with mocks. */
    public PosService(PosStateDAO stateDAO, PosCommandDAO commandDAO) {
        this.stateDAO = stateDAO;
        this.commandDAO = commandDAO;
    }

    /**
     * The document the public portal may see: branding, doctors and the
     * sessions still open for booking. No patient record, invoice or report
     * ever leaves the building without a signed-in account behind the request.
     */
    public Map<String, Object> publicState() {
        Map<String, Object> full = stateDAO.readState();
        Map<String, Object> open = new LinkedHashMap<>();
        open.put("clinicSettings", full.get("clinicSettings"));
        open.put("users", onlyDoctors(full.get("users")));
        open.put("treatmentCatalog", full.get("treatmentCatalog"));
        open.put("doctorPricing", new java.util.ArrayList<>());
        open.put("sessions", full.get("sessions"));
        open.put("patients", new java.util.ArrayList<>());
        open.put("appointments", new java.util.ArrayList<>());
        open.put("invoices", new java.util.ArrayList<>());
        open.put("payments", new java.util.ArrayList<>());
        open.put("reports", new java.util.ArrayList<>());
        return open;
    }

    @SuppressWarnings("unchecked")
    private Object onlyDoctors(Object users) {
        java.util.List<Map<String, Object>> all = (java.util.List<Map<String, Object>>) users;
        java.util.List<Map<String, Object>> doctors = new java.util.ArrayList<>();
        for (Map<String, Object> user : all) {
            if ("DOCTOR".equals(user.get("role")) && "ACTIVE".equals(user.get("status"))) {
                Map<String, Object> safe = new LinkedHashMap<>(user);
                safe.remove("email");
                safe.remove("phone");
                safe.remove("username");
                doctors.add(safe);
            }
        }
        return doctors;
    }

    /**
     * The only two things a visitor may do without an account: register
     * themselves and take a slot in an open session. Everything else needs a
     * staff login, which is enforced here and not merely in the browser.
     */
    public Map<String, Object> executePublic(String command, Map<String, Object> body) {
        if (!command.equals("save-patient") && !command.equals("book-appointment")) {
            throw new ForbiddenException("That action needs a clinic account");
        }
        User visitor = new User();
        visitor.setId(portalAccountId());
        visitor.setRole(Role.PATIENT_ADMIN);
        visitor.setFullName("Online Portal");
        return execute(command, body, visitor);
    }

    /** Portal bookings are recorded against the online portal account. */
    private int portalAccountId() {
        return stateDAO.portalUserId();
    }

    public Map<String, Object> state(User actor) {
        if (actor == null) {
            throw new ForbiddenException("Sign in to load the clinic data");
        }
        return stateDAO.readState();
    }

    /**
     * Runs one named command. Returns whatever the screen needs back, which is
     * usually the identifier of whatever was just created.
     */
    public Map<String, Object> execute(String command, Map<String, Object> body, User actor) {
        if (actor == null) {
            throw new ForbiddenException("Sign in first");
        }
        Map<String, Object> result = new LinkedHashMap<>();

        switch (command) {
            case "save-user": {
                allow(actor, MANAGEMENT, "manage staff accounts");
                Role target = Role.of(String.valueOf(body.get("role")));
                if (actor.getRole() == Role.ADMIN && (target == Role.SUPER_ADMIN || target == Role.ADMIN)) {
                    throw new ForbiddenException("Only the super admin can create admin accounts");
                }
                result.put("id", commandDAO.saveUser(body, actor));
                publish(actor, "USER_SAVED", "users", String.valueOf(result.get("id")));
                return result;
            }
            case "set-user-status": {
                allow(actor, MANAGEMENT, "activate or deactivate staff accounts");
                commandDAO.setUserStatus(body.get("userId"), String.valueOf(body.get("status")));
                publish(actor, "USER_STATUS_CHANGED", "users", String.valueOf(body.get("userId")));
                return result;
            }
            case "save-patient": {
                allow(actor, union(FRONT_DESK, CLINICAL), "register or edit patients");
                result.put("nic", commandDAO.savePatient(body, actor));
                publish(actor, "PATIENT_SAVED", "patients", String.valueOf(result.get("nic")));
                return result;
            }
            case "save-session": {
                allow(actor, EnumSet.of(Role.SUPER_ADMIN, Role.ADMIN, Role.DOCTOR), "manage clinic sessions");
                result.put("id", commandDAO.saveSession(body, actor));
                publish(actor, "SESSION_SAVED", "doctor_sessions", String.valueOf(result.get("id")));
                return result;
            }
            case "book-appointment": {
                allow(actor, union(FRONT_DESK, EnumSet.of(Role.NURSE, Role.DOCTOR, Role.CASHIER)),
                        "book a patient into a session");
                result.putAll(commandDAO.bookAppointment(body, actor));
                publish(actor, "APPOINTMENT_BOOKED", "appointments", String.valueOf(result.get("id")));
                return result;
            }
            case "update-appointment-status": {
                allow(actor, union(CLINICAL, COUNTER), "move a patient through the queue");
                commandDAO.updateAppointmentStatus(body, actor);
                publish(actor, "APPOINTMENT_STATUS_CHANGED", "appointments",
                        String.valueOf(body.get("appointmentId")));
                return result;
            }
            case "cancel-by-nic": {
                allow(actor, union(FRONT_DESK, COUNTER), "cancel appointments");
                String nic = String.valueOf(body.getOrDefault("nic", "")).trim();
                if (nic.isEmpty()) {
                    throw new ValidationException("Enter the patient's NIC");
                }
                int cancelled = commandDAO.cancelByNic(nic, String.valueOf(body.getOrDefault("reason", "")));
                result.put("count", cancelled);
                result.put("message", cancelled == 0
                        ? "No open appointment was found for that NIC"
                        : cancelled + " appointment(s) cancelled");
                publish(actor, "APPOINTMENTS_CANCELLED", "appointments", nic);
                return result;
            }
            case "create-invoice": {
                allow(actor, union(COUNTER, EnumSet.of(Role.DOCTOR, Role.NURSE)), "raise an invoice");
                result.put("invoiceNo", commandDAO.createInvoice(body, actor));
                publish(actor, "INVOICE_CREATED", "bills", String.valueOf(result.get("invoiceNo")));
                return result;
            }
            case "process-payment": {
                allow(actor, COUNTER, "take payments");
                result.putAll(commandDAO.processPayment(body, actor));
                publish(actor, "PAYMENT_TAKEN", "payments", String.valueOf(result.get("receiptNo")));
                return result;
            }
            case "add-report": {
                allow(actor, CLINICAL, "file a diagnostic report");
                result.put("id", commandDAO.addReport(body, actor));
                publish(actor, "REPORT_ADDED", "medical_reports", String.valueOf(result.get("id")));
                return result;
            }
            case "set-doctor-fee": {
                allow(actor, EnumSet.of(Role.SUPER_ADMIN, Role.ADMIN, Role.DOCTOR), "set procedure charges");
                commandDAO.setDoctorFee(body, actor);
                publish(actor, "DOCTOR_FEE_SET", "doctor_treatment_pricing", null);
                return result;
            }
            case "update-settings": {
                allow(actor, MANAGEMENT, "change the clinic settings");
                commandDAO.updateSettings(body, actor);
                publish(actor, "SETTINGS_UPDATED", "settings", null);
                return result;
            }
            default:
                throw new ValidationException("Unknown command: " + command);
        }
    }

    private void allow(User actor, Set<Role> allowed, String activity) {
        if (!allowed.contains(actor.getRole())) {
            throw new ForbiddenException("Your role is not allowed to " + activity);
        }
    }

    private Set<Role> union(Set<Role> a, Set<Role> b) {
        EnumSet<Role> merged = EnumSet.copyOf(a);
        merged.addAll(b);
        return merged;
    }

    /**
     * DESIGN PATTERN: Observer. The audit trail and the waiting-room display
     * both listen on the bus, so neither is wired into the code above.
     */
    private void publish(User actor, String action, String entity, String entityId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("action", action);
        data.put("entity", entity);
        data.put("entityId", entityId);
        data.put("userId", actor.getId());
        data.put("role", actor.getRole().name());
        EventBus.get().publish(new ClinicEvent(ClinicEvent.Type.GENERIC,
                actor.getFullName() + " performed " + action, data));
    }
}
