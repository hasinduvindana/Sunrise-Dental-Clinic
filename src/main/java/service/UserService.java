package service;

import dao.DAOFactory;
import dao.UserDAO;
import exception.ConflictException;
import exception.ForbiddenException;
import exception.NotFoundException;
import exception.ValidationException;
import model.Role;
import model.User;
import util.PasswordUtil;
import util.Validator;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Staff account management and the rules about who may create whom.
 *
 * Super admin -> admins, doctors, nurses, cashiers, patients
 * Admin       -> cashiers, nurses, patients
 * Everyone else -> nothing
 */
public class UserService {

    private final UserDAO userDAO;

    public UserService() {
        this(DAOFactory.getInstance().users());
    }

    public UserService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    /** The single place that answers "may this role create that role?". */
    public boolean canCreate(Role actor, Role target) {
        if (actor == Role.SUPER_ADMIN) {
            return true;
        }
        if (actor == Role.ADMIN) {
            return target == Role.CASHIER || target == Role.NURSE || target == Role.PATIENT;
        }
        return false;
    }

    public List<User> listStaff(Role roleFilter, String search) {
        return userDAO.search(roleFilter, search);
    }

    public List<User> listDoctors() {
        return userDAO.findByRole(Role.DOCTOR);
    }

    public User get(int id) {
        User user = userDAO.findById(id);
        if (user == null) {
            throw new NotFoundException("That account does not exist");
        }
        return user;
    }

    public User create(Map<String, Object> body, User actor) {
        Role targetRole = Role.of(Validator.requireText(body, "role", 20));
        if (targetRole == null) {
            throw new ValidationException("Choose a valid role");
        }
        if (targetRole == Role.SUPER_ADMIN) {
            throw new ForbiddenException("A second super admin account cannot be created from this screen");
        }
        if (!canCreate(actor.getRole(), targetRole)) {
            throw new ForbiddenException("Your role cannot create a " + targetRole.label() + " account");
        }

        String username = Validator.requireUsername(body, "username");
        if (userDAO.usernameExists(username)) {
            throw new ConflictException("That username is already taken");
        }

        User user = new User();
        user.setUsername(username);
        user.setFullName(Validator.requireText(body, "fullName", 120));
        user.setEmail(Validator.optionalEmail(body, "email"));
        user.setPhone(Validator.optionalText(body, "phone", 20));
        user.setRole(targetRole);
        user.setStatus(Validator.optionalText(body, "status", 10) == null
                ? "ACTIVE" : Validator.requireOneOf(body, "status", "ACTIVE", "INACTIVE"));
        user.setCreatedBy(actor.getId());

        String password = Validator.requirePassword(body, "password");
        String salt = PasswordUtil.newSalt();
        user.setSalt(salt);
        user.setPasswordHash(PasswordUtil.hash(password, salt));

        if (targetRole == Role.DOCTOR) {
            user.setSpecialization(Validator.optionalText(body, "specialization", 80));
            user.setQualification(Validator.optionalText(body, "qualification", 120));
            user.setConsultationFee(Validator.optionalMoney(body, "consultationFee", new BigDecimal("1500.00")));
            user.setRoomNo(Validator.optionalText(body, "roomNo", 20));
        }

        userDAO.insert(user);
        return user;
    }

    public User update(int id, Map<String, Object> body, User actor) {
        User existing = get(id);
        if (existing.getRole() == Role.SUPER_ADMIN && actor.getRole() != Role.SUPER_ADMIN) {
            throw new ForbiddenException("Only the super admin can edit the super admin account");
        }
        if (!canCreate(actor.getRole(), existing.getRole()) && actor.getId() != id) {
            throw new ForbiddenException("Your role cannot edit a " + existing.getRole().label() + " account");
        }

        existing.setFullName(Validator.requireText(body, "fullName", 120));
        existing.setEmail(Validator.optionalEmail(body, "email"));
        existing.setPhone(Validator.optionalText(body, "phone", 20));
        if (body.containsKey("status")) {
            existing.setStatus(Validator.requireOneOf(body, "status", "ACTIVE", "INACTIVE"));
        }
        if (existing.getRole() == Role.DOCTOR) {
            existing.setSpecialization(Validator.optionalText(body, "specialization", 80));
            existing.setQualification(Validator.optionalText(body, "qualification", 120));
            existing.setConsultationFee(Validator.optionalMoney(body, "consultationFee", existing.getConsultationFee()));
            existing.setRoomNo(Validator.optionalText(body, "roomNo", 20));
        }
        userDAO.update(existing);
        return existing;
    }

    public void setStatus(int id, String status, User actor) {
        User target = get(id);
        if (target.getId() == actor.getId()) {
            throw new ValidationException("You cannot deactivate your own account");
        }
        if (target.getRole() == Role.SUPER_ADMIN) {
            throw new ForbiddenException("The super admin account cannot be deactivated");
        }
        if (!canCreate(actor.getRole(), target.getRole())) {
            throw new ForbiddenException("Your role cannot change a " + target.getRole().label() + " account");
        }
        userDAO.updateStatus(id, "ACTIVE".equalsIgnoreCase(status) ? "ACTIVE" : "INACTIVE");
    }

    public void delete(int id, User actor) {
        User target = get(id);
        if (actor.getRole() != Role.SUPER_ADMIN) {
            throw new ForbiddenException("Only the super admin can delete an account");
        }
        if (target.getRole() == Role.SUPER_ADMIN) {
            throw new ForbiddenException("The super admin account cannot be deleted");
        }
        userDAO.delete(id);
    }
}
