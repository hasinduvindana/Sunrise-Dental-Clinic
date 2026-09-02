package service;

import dao.DAOFactory;
import dao.MedicalReportDAO;
import dao.PatientDAO;
import exception.ForbiddenException;
import exception.NotFoundException;
import exception.ValidationException;
import model.MedicalReport;
import model.Patient;
import model.Role;
import model.User;
import util.AppConfig;
import util.IdGenerator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Medical report files.
 *
 * The brief allows text files for storage, so the binary stays on the server
 * disk under a folder named per patient and only the metadata row goes into
 * MySQL. Files are renamed on upload so a hostile file name can never escape
 * the storage folder.
 */
public class MedicalReportService {

    private final MedicalReportDAO reportDAO;
    private final PatientDAO patientDAO;

    public MedicalReportService() {
        this(DAOFactory.getInstance().medicalReports(), DAOFactory.getInstance().patients());
    }

    public MedicalReportService(MedicalReportDAO reportDAO, PatientDAO patientDAO) {
        this.reportDAO = reportDAO;
        this.patientDAO = patientDAO;
    }

    public List<MedicalReport> forPatient(int patientId, User actor) {
        assertMayRead(patientId, actor);
        return reportDAO.findByPatient(patientId);
    }

    public MedicalReport get(int id, User actor) {
        MedicalReport report = reportDAO.findById(id);
        if (report == null) {
            throw new NotFoundException("That report does not exist");
        }
        assertMayRead(report.getPatientId(), actor);
        return report;
    }

    public MedicalReport upload(int patientId, Integer appointmentId, String title,
                                String originalFileName, String contentType,
                                InputStream data, User actor) {
        if (actor.getRole() == Role.PATIENT) {
            throw new ForbiddenException("Reports are uploaded by clinic staff");
        }
        Patient patient = patientDAO.findById(patientId);
        if (patient == null) {
            throw new NotFoundException("That patient is not registered");
        }
        if (title == null || title.isBlank()) {
            throw new ValidationException("Give the report a title");
        }
        if (data == null) {
            throw new ValidationException("Choose a file to upload");
        }

        String safeName = sanitize(originalFileName);
        String storedName = IdGenerator.fileToken() + "-" + safeName;
        Path folder = Paths.get(AppConfig.get().reportStorageDir(), patient.getPatientNo());

        try {
            Files.createDirectories(folder);
            Path target = folder.resolve(storedName);
            Files.copy(data, target, StandardCopyOption.REPLACE_EXISTING);

            MedicalReport report = new MedicalReport();
            report.setPatientId(patientId);
            report.setAppointmentId(appointmentId);
            report.setTitle(title.trim());
            report.setFileName(safeName);
            report.setFilePath(target.toAbsolutePath().toString());
            report.setContentType(contentType == null ? "application/octet-stream" : contentType);
            report.setUploadedBy(actor.getId());
            reportDAO.insert(report);
            return report;
        } catch (IOException e) {
            throw new ValidationException("The file could not be saved: " + e.getMessage());
        }
    }

    public Path fileFor(MedicalReport report) {
        Path path = Paths.get(report.getFilePath());
        if (!Files.exists(path)) {
            throw new NotFoundException("The report file is missing from the server");
        }
        return path;
    }

    public void delete(int id, User actor) {
        if (!actor.getRole().isAdministrative()) {
            throw new ForbiddenException("Only an admin can delete a report");
        }
        MedicalReport report = reportDAO.findById(id);
        if (report == null) {
            throw new NotFoundException("That report does not exist");
        }
        try {
            Files.deleteIfExists(Paths.get(report.getFilePath()));
        } catch (IOException e) {
            System.err.println("[MedicalReportService] file not removed: " + e.getMessage());
        }
        reportDAO.delete(id);
    }

    /** Strips directories and anything that is not a safe file-name character. */
    private String sanitize(String fileName) {
        String name = fileName == null ? "report.txt" : fileName;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        return name.isBlank() ? "report.txt" : name;
    }

    private void assertMayRead(int patientId, User actor) {
        if (actor.getRole().isStaff()) {
            return;
        }
        Patient self = patientDAO.findByUserId(actor.getId());
        if (self == null || self.getId() != patientId) {
            throw new ForbiddenException("You can only read your own reports");
        }
    }
}
