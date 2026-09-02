package util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD unit tests for the password hashing routine.
 * Written before PasswordUtil was wired into AuthService: the rules below are
 * the specification, not a description of the finished code.
 */
class PasswordUtilTest {

    @Test
    @DisplayName("the same password and salt always produce the same hash")
    void hashingIsDeterministic() {
        String salt = PasswordUtil.newSalt();
        assertEquals(PasswordUtil.hash("Sunrise@123", salt), PasswordUtil.hash("Sunrise@123", salt));
    }

    @Test
    @DisplayName("two accounts with the same password get different hashes")
    void saltsDifferPerAccount() {
        String hashA = PasswordUtil.hash("Sunrise@123", PasswordUtil.newSalt());
        String hashB = PasswordUtil.hash("Sunrise@123", PasswordUtil.newSalt());
        assertNotEquals(hashA, hashB);
    }

    @Test
    @DisplayName("the plain password never appears inside the stored hash")
    void hashDoesNotLeakThePassword() {
        String salt = PasswordUtil.newSalt();
        assertFalse(PasswordUtil.hash("Sunrise@123", salt).contains("Sunrise@123"));
    }

    @Test
    @DisplayName("matches() accepts the right password and rejects a wrong one")
    void verification() {
        String salt = PasswordUtil.newSalt();
        String hash = PasswordUtil.hash("Sunrise@123", salt);
        assertTrue(PasswordUtil.matches("Sunrise@123", salt, hash));
        assertFalse(PasswordUtil.matches("sunrise@123", salt, hash));
        assertFalse(PasswordUtil.matches("", salt, hash));
        assertFalse(PasswordUtil.matches(null, salt, hash));
    }
}
