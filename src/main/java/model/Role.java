package model;

/**
 * Every account in the clinic holds exactly one role. The role decides which
 * API endpoints and which screens the account may reach.
 */
public enum Role {

    /** Creates admins, doctors, nurses and patients; owns global settings. */
    SUPER_ADMIN,
    /** Front-office manager: admits patients, adds cashiers, runs reports. */
    ADMIN,
    /** A dentist. Runs sessions, writes prescriptions, sees their own income. */
    DOCTOR,
    /** Manages patients during the visit: check-in, vitals, notes. */
    NURSE,
    /** Settles bills at the counter. */
    CASHIER,
    /** Front desk: registers patients and books them into sessions. */
    PATIENT_ADMIN,
    /** The patient's own portal login. */
    PATIENT;

    public static Role of(String text) {
        if (text == null) {
            return null;
        }
        try {
            return Role.valueOf(text.trim().toUpperCase().replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Doctors are administrators of their own clinical area as well. */
    public boolean isStaff() {
        return this != PATIENT;
    }

    public boolean isAdministrative() {
        return this == SUPER_ADMIN || this == ADMIN;
    }

    public String label() {
        String lower = name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
