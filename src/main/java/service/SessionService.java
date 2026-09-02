package service;

import dao.AppointmentDAO;
import dao.DAOFactory;
import dao.SessionDAO;
import exception.ConflictException;
import exception.ForbiddenException;
import exception.NotFoundException;
import exception.ValidationException;
import model.Appointment;
import model.DoctorSession;
import model.Role;
import model.User;
import service.notify.ClinicEvent;
import service.notify.EventBus;
import util.Validator;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Session management (the doctor's own CRUD) and the live queue.
 *
 * A doctor may only touch their own sessions; an admin may see and adjust any
 * session. Advancing the queue publishes a QUEUE_ADVANCED event, which is how
 * the waiting-room display and the patient portal learn the new number.
 */
public class SessionService {

    private final SessionDAO sessionDAO;
    private final AppointmentDAO appointmentDAO;

    public SessionService() {
        this(DAOFactory.getInstance().sessions(), DAOFactory.getInstance().appointments());
    }

    public SessionService(SessionDAO sessionDAO, AppointmentDAO appointmentDAO) {
        this.sessionDAO = sessionDAO;
        this.appointmentDAO = appointmentDAO;
    }

    public List<DoctorSession> list(Integer doctorId, String date, String status) {
        return sessionDAO.find(doctorId, date, status);
    }

    public List<DoctorSession> bookable() {
        return sessionDAO.findBookable();
    }

    public DoctorSession get(int id) {
        DoctorSession session = sessionDAO.findById(id);
        if (session == null) {
            throw new NotFoundException("That session does not exist");
        }
        return session;
    }

    public DoctorSession create(Map<String, Object> body, User actor) {
        int doctorId = resolveDoctorId(body, actor);

        DoctorSession session = new DoctorSession();
        session.setDoctorId(doctorId);
        session.setSessionDate(Validator.requireDate(body, "sessionDate").toString());
        LocalTime start = Validator.requireTime(body, "startTime");
        LocalTime end = Validator.requireTime(body, "endTime");
        if (!end.isAfter(start)) {
            throw new ValidationException("The end time must be later than the start time");
        }
        session.setStartTime(start.toString());
        session.setEndTime(end.toString());
        session.setRoomNo(Validator.optionalText(body, "roomNo", 20));
        session.setMaxPatients(Validator.optionalInt(body, "maxPatients", 20));
        if (session.getMaxPatients() < 1 || session.getMaxPatients() > 200) {
            throw new ValidationException("The patient limit must be between 1 and 200");
        }
        session.setConsultationFee(Validator.optionalMoney(body, "consultationFee", new BigDecimal("1500.00")));
        session.setStatus(body.containsKey("status")
                ? Validator.requireOneOf(body, "status", "SCHEDULED", "ACTIVE", "CLOSED", "CANCELLED")
                : "SCHEDULED");

        if (sessionDAO.slotTaken(doctorId, session.getSessionDate(), session.getStartTime(), 0)) {
            throw new ConflictException("That doctor already has a session starting at this time");
        }

        sessionDAO.insert(session);
        publish(ClinicEvent.Type.SESSION_STATUS_CHANGED, actor, session.getId(),
                "Session created for " + session.getSessionDate() + " at " + session.getStartTime());
        return session;
    }

    public DoctorSession update(int id, Map<String, Object> body, User actor) {
        DoctorSession session = get(id);
        assertOwnership(session, actor);

        session.setSessionDate(Validator.requireDate(body, "sessionDate").toString());
        LocalTime start = Validator.requireTime(body, "startTime");
        LocalTime end = Validator.requireTime(body, "endTime");
        if (!end.isAfter(start)) {
            throw new ValidationException("The end time must be later than the start time");
        }
        session.setStartTime(start.toString());
        session.setEndTime(end.toString());
        session.setRoomNo(Validator.optionalText(body, "roomNo", 20));

        int newMax = Validator.optionalInt(body, "maxPatients", session.getMaxPatients());
        if (newMax < session.getBookedCount()) {
            throw new ValidationException("The limit cannot be lower than the "
                    + session.getBookedCount() + " patients already booked");
        }
        session.setMaxPatients(newMax);
        session.setConsultationFee(Validator.optionalMoney(body, "consultationFee", session.getConsultationFee()));
        if (body.containsKey("status")) {
            session.setStatus(Validator.requireOneOf(body, "status", "SCHEDULED", "ACTIVE", "CLOSED", "CANCELLED"));
        }

        if (sessionDAO.slotTaken(session.getDoctorId(), session.getSessionDate(), session.getStartTime(), id)) {
            throw new ConflictException("Another session already starts at that time");
        }

        sessionDAO.update(session);
        return session;
    }

    public DoctorSession changeStatus(int id, String status, User actor) {
        DoctorSession session = get(id);
        assertOwnership(session, actor);
        sessionDAO.updateStatus(id, status);
        session.setStatus(status);
        publish(ClinicEvent.Type.SESSION_STATUS_CHANGED, actor, id, "Session marked " + status);
        return session;
    }

    public void delete(int id, User actor) {
        DoctorSession session = get(id);
        assertOwnership(session, actor);
        if (session.getBookedCount() > 0) {
            throw new ConflictException("This session already has bookings - cancel it instead of deleting it");
        }
        sessionDAO.delete(id);
    }

    // ----------------------------- queue -----------------------------

    /**
     * Calls the next waiting patient in. Returns the appointment now in the
     * chair, or null when the queue is empty.
     */
    public Appointment callNext(int sessionId, User actor) {
        DoctorSession session = get(sessionId);
        assertOwnership(session, actor);

        Appointment current = null;
        for (Appointment a : appointmentDAO.findBySession(sessionId)) {
            if ("IN_CONSULTATION".equals(a.getStatus())) {
                current = a;
                break;
            }
        }
        if (current != null) {
            appointmentDAO.updateStatus(current.getId(), "COMPLETED");
        }

        Appointment next = appointmentDAO.findNextWaiting(sessionId);
        if (next == null) {
            sessionDAO.updateCurrentQueueNo(sessionId, session.getBookedCount());
            publish(ClinicEvent.Type.QUEUE_ADVANCED, actor, sessionId, "All patients have been seen");
            return null;
        }

        appointmentDAO.updateStatus(next.getId(), "IN_CONSULTATION");
        sessionDAO.updateCurrentQueueNo(sessionId, next.getQueueNo());
        next.setStatus("IN_CONSULTATION");
        next.setCurrentQueueNo(next.getQueueNo());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        data.put("queueNo", next.getQueueNo());
        data.put("entity", "SESSION");
        data.put("entityId", String.valueOf(sessionId));
        data.put("actorId", actor.getId());
        data.put("actorRole", actor.getRole().name());
        EventBus.get().publish(new ClinicEvent(ClinicEvent.Type.QUEUE_ADVANCED,
                "Now serving number " + next.getQueueNo(), data));

        return next;
    }

    /** What the waiting room and the patient portal poll. */
    public Map<String, Object> queueStatus(int sessionId) {
        DoctorSession session = get(sessionId);
        Appointment next = appointmentDAO.findNextWaiting(sessionId);

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("sessionId", sessionId);
        status.put("doctorName", session.getDoctorName());
        status.put("sessionDate", session.getSessionDate());
        status.put("startTime", session.getStartTime());
        status.put("roomNo", session.getRoomNo());
        status.put("sessionStatus", session.getStatus());
        status.put("nowServing", session.getCurrentQueueNo());
        status.put("nextNumber", next == null ? null : next.getQueueNo());
        status.put("waiting", session.getWaitingCount());
        status.put("booked", session.getBookedCount());
        return status;
    }

    // ------------------------------------------------------------------

    private int resolveDoctorId(Map<String, Object> body, User actor) {
        if (actor.getRole() == Role.DOCTOR) {
            return actor.getId();
        }
        if (actor.getRole().isAdministrative()) {
            return Validator.requireInt(body, "doctorId");
        }
        throw new ForbiddenException("Only a doctor or an admin can schedule a session");
    }

    private void assertOwnership(DoctorSession session, User actor) {
        if (actor.getRole().isAdministrative()) {
            return;
        }
        if (actor.getRole() == Role.DOCTOR && session.getDoctorId() == actor.getId()) {
            return;
        }
        throw new ForbiddenException("You can only manage your own sessions");
    }

    private void publish(ClinicEvent.Type type, User actor, int sessionId, String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        data.put("entity", "SESSION");
        data.put("entityId", String.valueOf(sessionId));
        data.put("actorId", actor.getId());
        data.put("actorRole", actor.getRole().name());
        EventBus.get().publish(new ClinicEvent(type, message, data));
    }
}
