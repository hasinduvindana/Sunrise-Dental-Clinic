package service;

import dao.DAOFactory;
import dao.UserDAO;
import exception.AuthException;
import exception.ValidationException;
import model.Role;
import model.User;
import util.PasswordUtil;

/**
 * Sign-in and password handling. The servlet layer owns the HttpSession; this
 * class only decides whether a username and password are acceptable.
 */
public class AuthService {

    private final UserDAO userDAO;

    public AuthService() {
        this(DAOFactory.getInstance().users());
    }

    /** Constructor injection so the unit tests can pass a mocked DAO. */
    public AuthService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    public User login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new ValidationException("Enter both your username and your password");
        }
        User user = userDAO.findByUsername(username.trim().toLowerCase());
        if (user == null || !PasswordUtil.matches(password, user.getSalt(), user.getPasswordHash())) {
            // Same message either way: never reveal which half was wrong.
            throw new AuthException("Username or password is incorrect");
        }
        if (!user.isActive()) {
            throw new AuthException("This account has been deactivated. Contact the clinic administrator.");
        }
        return user;
    }

    public void changePassword(int userId, String currentPassword, String newPassword) {
        User user = userDAO.findById(userId);
        if (user == null) {
            throw new AuthException("Account not found");
        }
        if (!PasswordUtil.matches(currentPassword, user.getSalt(), user.getPasswordHash())) {
            throw new AuthException("Your current password is incorrect");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new ValidationException("The new password must be at least 6 characters long");
        }
        String salt = PasswordUtil.newSalt();
        userDAO.updatePassword(userId, PasswordUtil.hash(newPassword, salt), salt);
    }

    /** Used by an admin resetting somebody else's password. */
    public void resetPassword(int targetUserId, String newPassword, Role actorRole) {
        if (actorRole == null || !actorRole.isAdministrative()) {
            throw new AuthException("Only an administrator can reset a password");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new ValidationException("The new password must be at least 6 characters long");
        }
        String salt = PasswordUtil.newSalt();
        userDAO.updatePassword(targetUserId, PasswordUtil.hash(newPassword, salt), salt);
    }
}
