package service;

import dao.UserDAO;
import exception.AuthException;
import exception.ValidationException;
import model.Role;
import model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import util.PasswordUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AuthService is tested against a mocked UserDAO, so the sign-in rules can be
 * verified without a database. This is why the services take their DAOs
 * through the constructor.
 */
class AuthServiceTest {

    private UserDAO userDAO;
    private AuthService authService;
    private User active;

    @BeforeEach
    void setUp() {
        userDAO = mock(UserDAO.class);
        authService = new AuthService(userDAO);

        String salt = PasswordUtil.newSalt();
        active = new User();
        active.setId(3);
        active.setUsername("dr.silva");
        active.setSalt(salt);
        active.setPasswordHash(PasswordUtil.hash("Correct@123", salt));
        active.setRole(Role.DOCTOR);
        active.setFullName("Dr. Anura Silva");
        active.setStatus("ACTIVE");
    }

    @Test
    @DisplayName("correct credentials return the account")
    void successfulLogin() {
        when(userDAO.findByUsername("dr.silva")).thenReturn(active);
        assertEquals(3, authService.login("Dr.Silva", "Correct@123").getId());
    }

    @Test
    @DisplayName("a wrong password is rejected")
    void wrongPassword() {
        when(userDAO.findByUsername("dr.silva")).thenReturn(active);
        assertThrows(AuthException.class, () -> authService.login("dr.silva", "Wrong@123"));
    }

    @Test
    @DisplayName("an unknown username gives the same error as a wrong password")
    void unknownUser() {
        when(userDAO.findByUsername(anyString())).thenReturn(null);
        AuthException unknown = assertThrows(AuthException.class, () -> authService.login("ghost", "whatever"));
        when(userDAO.findByUsername("dr.silva")).thenReturn(active);
        AuthException wrong = assertThrows(AuthException.class, () -> authService.login("dr.silva", "Wrong@123"));
        assertEquals(unknown.getMessage(), wrong.getMessage());
    }

    @Test
    @DisplayName("a deactivated account cannot sign in even with the right password")
    void inactiveAccount() {
        active.setStatus("INACTIVE");
        when(userDAO.findByUsername("dr.silva")).thenReturn(active);
        assertThrows(AuthException.class, () -> authService.login("dr.silva", "Correct@123"));
    }

    @Test
    @DisplayName("a blank username or password is a validation error, not an auth error")
    void blankInput() {
        assertThrows(ValidationException.class, () -> authService.login("", "Correct@123"));
        assertThrows(ValidationException.class, () -> authService.login("dr.silva", "  "));
    }

    @Test
    @DisplayName("changing a password requires the current one")
    void changePasswordChecksCurrent() {
        when(userDAO.findById(3)).thenReturn(active);
        assertThrows(AuthException.class, () -> authService.changePassword(3, "Wrong@123", "NewPass@1"));
    }

    @Test
    @DisplayName("a new password shorter than six characters is refused")
    void changePasswordEnforcesLength() {
        when(userDAO.findById(3)).thenReturn(active);
        assertThrows(ValidationException.class, () -> authService.changePassword(3, "Correct@123", "abc"));
    }
}
