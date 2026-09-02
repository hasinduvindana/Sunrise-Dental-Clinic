package service;

import dao.DAOFactory;
import dao.PatientDAO;
import dao.UserDAO;
import exception.ConflictException;
import exception.ForbiddenException;
import exception.NotFoundException;
import model.Patient;
import model.Role;
import model.User;
import util.IdGenerator;
import util.PasswordUtil;
import util.Validator;

import java.util.List;
import java.util.Map;

/**
 * Patient registration and lookup.
 *
 * Registration is done by the super admin or an admin. A portal login is
 * optional: when a username and password are supplied the service creates a
 * PATIENT user account and links it to the patient record, which is what lets
 * the patient read their own reports and check the queue later on.
 */
public class PatientService {

    private final PatientDAO patientDAO;
    private final UserDAO userDAO;

    public PatientService() {
        this(DAOFactory.getInstance().patients(), DAOFactory.getInstance().users());
    }

    public PatientService(PatientDAO patientDAO, UserDAO userDAO) {
        this.patientDAO = patientDAO;
        this.userDAO = userDAO;
    }

    public List<Patient> search(String term) {
        return patientDAO.search(term);
    }

    public Patient get(int id) {
        Patient patient = patientDAO.findById(id);
        if (patient == null) {
            throw new NotFoundException("That patient is not registered");
        }
        return patient;
    }

    public Patient getByUserId(int userId) {
        Patient patient = patientDAO.findByUserId(userId);
        if (patient == null) {
            throw new NotFoundException("No patient record is linked to this login");
        }
        return patient;
    }

    public Patient register(Map<String, Object> body, User actor) {
        if (actor.getRole() != Role.SUPER_ADMIN && actor.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Only an admin or the super admin can register a patient");
        }

        Patient patient = new Patient();
        patient.setFullName(Validator.requireText(body, "fullName", 120));
        patient.setContact(Validator.requirePhone(body, "contact"));
        patient.setAddress(Validator.optionalText(body, "address", 255));
        patient.setNic(Validator.optionalText(body, "nic", 20));
        patient.setDateOfBirth(Validator.optionalDate(body, "dateOfBirth") == null
                ? null : String.valueOf(Validator.optionalDate(body, "dateOfBirth")));
        patient.setGender(body.containsKey("gender")
                ? Validator.requireOneOf(body, "gender", "MALE", "FEMALE", "OTHER") : "OTHER");
        patient.setVip(Validator.optionalBoolean(body, "vip", false));
        patient.setNotes(Validator.optionalText(body, "notes", 1000));
        patient.setRegisteredBy(actor.getId());
        patient.setPatientNo(IdGenerator.patientNo(patientDAO.nextSequence()));

        // Optional portal login
        String username = Validator.optionalText(body, "username", 50);
        if (username != null) {
            String login = Validator.requireUsername(body, "username");
            if (userDAO.usernameExists(login)) {
                throw new ConflictException("That username is already taken");
            }
            String password = Validator.requirePassword(body, "password");
            String salt = PasswordUtil.newSalt();

            User portal = new User();
            portal.setUsername(login);
            portal.setSalt(salt);
            portal.setPasswordHash(PasswordUtil.hash(password, salt));
            portal.setRole(Role.PATIENT);
            portal.setFullName(patient.getFullName());
            portal.setEmail(Validator.optionalEmail(body, "email"));
            portal.setPhone(patient.getContact());
            portal.setCreatedBy(actor.getId());
            userDAO.insert(portal);

            patient.setUserId(portal.getId());
            patient.setUsername(login);
        }

        patientDAO.insert(patient);
        return patient;
    }

    public Patient update(int id, Map<String, Object> body, User actor) {
        if (actor.getRole() == Role.PATIENT) {
            throw new ForbiddenException("Patients cannot edit their own clinical record");
        }
        Patient patient = get(id);
        patient.setFullName(Validator.requireText(body, "fullName", 120));
        patient.setContact(Validator.requirePhone(body, "contact"));
        patient.setAddress(Validator.optionalText(body, "address", 255));
        patient.setNic(Validator.optionalText(body, "nic", 20));
        if (body.containsKey("dateOfBirth")) {
            java.time.LocalDate dob = Validator.optionalDate(body, "dateOfBirth");
            patient.setDateOfBirth(dob == null ? null : dob.toString());
        }
        if (body.containsKey("gender")) {
            patient.setGender(Validator.requireOneOf(body, "gender", "MALE", "FEMALE", "OTHER"));
        }
        patient.setVip(Validator.optionalBoolean(body, "vip", patient.isVip()));
        patient.setNotes(Validator.optionalText(body, "notes", 1000));
        patientDAO.update(patient);
        return patient;
    }

    /** Adds a portal login to a patient who was registered without one. */
    public Patient createPortalLogin(int patientId, Map<String, Object> body, User actor) {
        if (!actor.getRole().isAdministrative()) {
            throw new ForbiddenException("Only an admin can issue a portal login");
        }
        Patient patient = get(patientId);
        if (patient.getUserId() != null) {
            throw new ConflictException("This patient already has a portal login");
        }
        String username = Validator.requireUsername(body, "username");
        if (userDAO.usernameExists(username)) {
            throw new ConflictException("That username is already taken");
        }
        String password = Validator.requirePassword(body, "password");
        String salt = PasswordUtil.newSalt();

        User portal = new User();
        portal.setUsername(username);
        portal.setSalt(salt);
        portal.setPasswordHash(PasswordUtil.hash(password, salt));
        portal.setRole(Role.PATIENT);
        portal.setFullName(patient.getFullName());
        portal.setPhone(patient.getContact());
        portal.setCreatedBy(actor.getId());
        userDAO.insert(portal);

        patientDAO.linkUser(patientId, portal.getId());
        patient.setUserId(portal.getId());
        patient.setUsername(username);
        return patient;
    }

    public void delete(int id, User actor) {
        if (actor.getRole() != Role.SUPER_ADMIN) {
            throw new ForbiddenException("Only the super admin can remove a patient record");
        }
        get(id);
        patientDAO.delete(id);
    }
}
