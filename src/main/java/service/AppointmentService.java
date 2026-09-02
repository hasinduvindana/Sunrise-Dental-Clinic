package service;

import dao.AppointmentDAO;
import dao.DAOFactory;
import dao.PatientDAO;
import dao.SessionDAO;
import exception.ConflictException;
import exception.ForbiddenException;
import exception.NotFoundException;
import exception.ValidationException;
import model.Appointment;
import model.DoctorSession;
import model.Patient;
import model.Role;
import model.User;
import service.notify.ClinicEvent;
import service.notify.EventBus;
import util.IdGenerator;
import util.Validator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Booking, assigning and progressing appointments.
 *
 * Who may create one:
 *   ADMIN / SUPER_ADMIN - admit any patient into any session
 *   DOCTOR              - assign a patient into their own session
 *   NURSE               - book on behalf of a patient at the desk
 *   PATIENT             - book themselves into a bookable session
 */
public class AppointmentService {

    private final AppointmentDAO appointmentDAO;
    private final SessionDAO sessionDAO;
    private final PatientDAO patientDAO;

    public AppointmentService() {
        this(DAOFactory.getInstance().appointments(),
             DAOFactory.getInstance().sessions(),
             DAOFactory.getInstance().patients());
    }

    public AppointmentService(AppointmentDAO appointmentDAO, SessionDAO sessionDAO, PatientDAO patientDAO) {
        this.appointmentDAO = appointmentDAO;
        this.sessionDAO = sessionDAO;
        this.patientDAO = patientDAO;
    }

    public Appointment get(int id) {
        Appointment appointment = appointmentDAO.findById(id);
        if (appointment == null) {
            throw new NotFoundException("That appointment does not exist");
        }
        return appointment;
    }

    /** The brief's "display appointment details" search. */
    public Appointment getByNumber(String appointmentNo) {
        Appointment appointment = appointmentDAO.findByNo(appointmentNo);
        if (appointment == null) {
            throw new NotFoundException("No appointment found with number " + appointmentNo);
        }
        return appointment;
    }

    public List<Appointment> listForSession(int sessionId) {
        return appointmentDAO.findBySession(sessionId);
    }

    public List<Appointment> listForPatient(int patientId) {
        return appointmentDAO.findByPatient(patientId);
    }

    public List<Appointment> listForDoctor(int doctorId, String date) {
        return appointmentDAO.findByDoctor(doctorId, date);
    }

    public List<Appointment> listForDate(String date) {
        return appointmentDAO.findByDate(date);
    }

    public Appointment book(Map<String, Object> body, User actor) {
        int sessionId = Validator.requireInt(body, "sessionId");
        DoctorSession session = sessionDAO.findById(sessionId);
        if (session == null) {
            throw new NotFoundException("That session does not exist");
        }

        int patientId = resolvePatientId(body, actor);
        Patient patient = patientDAO.findById(patientId);
        if (patient == null) {
            throw new NotFoundException("That patient is not registered");
        }

        if (actor.getRole() == Role.DOCTOR && session.getDoctorId() != actor.getId()) {
            throw new ForbiddenException("You can only assign patients to your own sessions");
        }
        if ("CLOSED".equals(session.getStatus()) || "CANCELLED".equals(session.getStatus())) {
            throw new ConflictException("That session is no longer accepting bookings");
        }
        if (session.getAvailableSlots() <= 0) {
            throw new ConflictException("That session is full - please choose another one");
        }
        if (appointmentDAO.patientAlreadyInSession(sessionId, patientId)) {
            throw new ConflictException(patient.getFullName() + " already has a booking in this session");
        }

        Appointment appointment = new Appointment();
        appointment.setSessionId(sessionId);
        appointment.setPatientId(patientId);
        Integer treatmentId = body.containsKey("treatmentId") && body.get("treatmentId") != null
                ? Validator.requireInt(body, "treatmentId") : null;
        appointment.setTreatmentId(treatmentId);
        appointment.setNotes(Validator.optionalText(body, "notes", 255));
        appointment.setBookedBy(actor.getId());
        appointment.setAppointmentNo(IdGenerator.appointmentNo(
                java.time.LocalDate.parse(session.getSessionDate()),
                appointmentDAO.countInSession(sessionId) + 1 + sessionId * 100));

        appointmentDAO.insert(appointment);
        Appointment saved = get(appointment.getId());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        data.put("entity", "APPOINTMENT");
        data.put("entityId", saved.getAppointmentNo());
        data.put("actorId", actor.getId());
        data.put("actorRole", actor.getRole().name());
        EventBus.get().publish(new ClinicEvent(ClinicEvent.Type.APPOINTMENT_BOOKED,
                saved.getPatientName() + " booked into queue position " + saved.getQueueNo(), data));

        return saved;
    }

    /** Nurse or front desk marking the patient as arrived. */
    public Appointment checkIn(int appointmentId, User actor) {
        Appointment appointment = get(appointmentId);
        if (actor.getRole() == Role.PATIENT) {
            throw new ForbiddenException("Please check in at the front desk");
        }
        if (!"BOOKED".equals(appointment.getStatus())) {
            throw new ConflictException("This appointment is already " + appointment.getStatus().toLowerCase());
        }
        appointmentDAO.updateStatus(appointmentId, "CHECKED_IN");
        appointment.setStatus("CHECKED_IN");
        return appointment;
    }

    public Appointment changeStatus(int appointmentId, String status, User actor) {
        Appointment appointment = get(appointmentId);
        String target = status == null ? "" : status.trim().toUpperCase();
        switch (target) {
            case "BOOKED":
            case "CHECKED_IN":
            case "IN_CONSULTATION":
            case "COMPLETED":
            case "NO_SHOW":
                if (actor.getRole() == Role.PATIENT) {
                    throw new ForbiddenException("Patients cannot change an appointment status");
                }
                break;
            case "CANCELLED":
                assertMayCancel(appointment, actor);
                break;
            default:
                throw new ValidationException("Unknown appointment status: " + status);
        }
        appointmentDAO.updateStatus(appointmentId, target);
        appointment.setStatus(target);

        if ("CANCELLED".equals(target)) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("entity", "APPOINTMENT");
            data.put("entityId", appointment.getAppointmentNo());
            data.put("actorId", actor.getId());
            data.put("actorRole", actor.getRole().name());
            EventBus.get().publish(new ClinicEvent(ClinicEvent.Type.APPOINTMENT_CANCELLED,
                    "Appointment " + appointment.getAppointmentNo() + " cancelled", data));
        }
        return appointment;
    }

    public Appointment update(int appointmentId, Map<String, Object> body, User actor) {
        if (actor.getRole() == Role.PATIENT) {
            throw new ForbiddenException("Patients cannot edit an appointment");
        }
        Appointment appointment = get(appointmentId);
        if (body.containsKey("treatmentId")) {
            appointment.setTreatmentId(body.get("treatmentId") == null
                    ? null : Validator.requireInt(body, "treatmentId"));
        }
        appointment.setNotes(Validator.optionalText(body, "notes", 255));
        appointmentDAO.update(appointment);
        return get(appointmentId);
    }

    // ------------------------------------------------------------------

    private int resolvePatientId(Map<String, Object> body, User actor) {
        if (actor.getRole() == Role.PATIENT) {
            Patient self = patientDAO.findByUserId(actor.getId());
            if (self == null) {
                throw new ForbiddenException("No patient record is linked to this login");
            }
            return self.getId();
        }
        return Validator.requireInt(body, "patientId");
    }

    private void assertMayCancel(Appointment appointment, User actor) {
        if (actor.getRole().isStaff()) {
            return;
        }
        Patient self = patientDAO.findByUserId(actor.getId());
        if (self == null || self.getId() != appointment.getPatientId()) {
            throw new ForbiddenException("You can only cancel your own appointment");
        }
        if ("IN_CONSULTATION".equals(appointment.getStatus()) || "COMPLETED".equals(appointment.getStatus())) {
            throw new ConflictException("This appointment can no longer be cancelled online");
        }
    }
}
