package service;

import dao.AppointmentDAO;
import dao.DAOFactory;
import dao.PatientDAO;
import dao.PrescriptionDAO;
import exception.ConflictException;
import exception.ForbiddenException;
import exception.NotFoundException;
import exception.ValidationException;
import model.Appointment;
import model.Patient;
import model.Prescription;
import model.PrescriptionItem;
import model.Role;
import model.User;
import service.notify.ClinicEvent;
import service.notify.EventBus;
import util.Validator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Prescriptions: written by the treating doctor, readable by that patient. */
public class PrescriptionService {

    private final PrescriptionDAO prescriptionDAO;
    private final AppointmentDAO appointmentDAO;
    private final PatientDAO patientDAO;

    public PrescriptionService() {
        this(DAOFactory.getInstance().prescriptions(),
             DAOFactory.getInstance().appointments(),
             DAOFactory.getInstance().patients());
    }

    public PrescriptionService(PrescriptionDAO prescriptionDAO, AppointmentDAO appointmentDAO, PatientDAO patientDAO) {
        this.prescriptionDAO = prescriptionDAO;
        this.appointmentDAO = appointmentDAO;
        this.patientDAO = patientDAO;
    }

    public Prescription get(int id, User actor) {
        Prescription prescription = prescriptionDAO.findById(id);
        if (prescription == null) {
            throw new NotFoundException("That prescription does not exist");
        }
        assertMayRead(prescription.getPatientId(), actor);
        return prescription;
    }

    public Prescription forAppointment(int appointmentId, User actor) {
        Prescription prescription = prescriptionDAO.findByAppointment(appointmentId);
        if (prescription == null) {
            throw new NotFoundException("No prescription has been written for this visit");
        }
        assertMayRead(prescription.getPatientId(), actor);
        return prescription;
    }

    public List<Prescription> forPatient(int patientId, User actor) {
        assertMayRead(patientId, actor);
        return prescriptionDAO.findByPatient(patientId);
    }

    /**
     * Body: { appointmentId, diagnosis, advice, items:[{drugName, dosage, frequency,
     *         durationDays, instructions}] }
     */
    @SuppressWarnings("unchecked")
    public Prescription create(Map<String, Object> body, User actor) {
        if (actor.getRole() != Role.DOCTOR) {
            throw new ForbiddenException("Only the treating doctor can write a prescription");
        }
        int appointmentId = Validator.requireInt(body, "appointmentId");
        Appointment appointment = appointmentDAO.findById(appointmentId);
        if (appointment == null) {
            throw new NotFoundException("That appointment does not exist");
        }
        if (appointment.getDoctorId() != actor.getId()) {
            throw new ForbiddenException("This patient is not in your session");
        }
        if (prescriptionDAO.findByAppointment(appointmentId) != null) {
            throw new ConflictException("A prescription has already been written for this visit");
        }

        Prescription prescription = new Prescription();
        prescription.setAppointmentId(appointmentId);
        prescription.setDoctorId(actor.getId());
        prescription.setPatientId(appointment.getPatientId());
        prescription.setDiagnosis(Validator.optionalText(body, "diagnosis", 255));
        prescription.setAdvice(Validator.optionalText(body, "advice", 2000));

        Object rawItems = body.get("items");
        if (!(rawItems instanceof List) || ((List<Object>) rawItems).isEmpty()) {
            throw new ValidationException("Add at least one medicine to the prescription");
        }
        for (Object entry : (List<Object>) rawItems) {
            if (!(entry instanceof Map)) {
                continue;
            }
            Map<String, Object> line = (Map<String, Object>) entry;
            PrescriptionItem item = new PrescriptionItem();
            item.setDrugName(Validator.requireText(line, "drugName", 120));
            item.setDosage(Validator.optionalText(line, "dosage", 60));
            item.setFrequency(Validator.optionalText(line, "frequency", 60));
            int days = Validator.optionalInt(line, "durationDays", 0);
            item.setDurationDays(days == 0 ? null : days);
            item.setInstructions(Validator.optionalText(line, "instructions", 255));
            prescription.getItems().add(item);
        }

        prescriptionDAO.insert(prescription);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("entity", "PRESCRIPTION");
        data.put("entityId", String.valueOf(prescription.getId()));
        data.put("actorId", actor.getId());
        data.put("actorRole", actor.getRole().name());
        EventBus.get().publish(new ClinicEvent(ClinicEvent.Type.PRESCRIPTION_ISSUED,
                "Prescription issued for " + appointment.getPatientName(), data));

        return prescriptionDAO.findById(prescription.getId());
    }

    private void assertMayRead(int patientId, User actor) {
        if (actor.getRole().isStaff()) {
            return;
        }
        Patient self = patientDAO.findByUserId(actor.getId());
        if (self == null || self.getId() != patientId) {
            throw new ForbiddenException("You can only read your own prescriptions");
        }
    }
}
