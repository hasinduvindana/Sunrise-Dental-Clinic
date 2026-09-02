package servlet;

import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import model.MedicalReport;
import model.Role;
import model.User;
import service.MedicalReportService;
import service.PatientService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Patient report files. Multipart upload by staff, download by staff or by the
 * patient the report belongs to.
 *
 * GET    /api/medical-reports?patientId=   list
 * GET    /api/medical-reports/mine         the signed-in patient's reports
 * GET    /api/medical-reports/{id}/file    download the file itself
 * POST   /api/medical-reports              multipart upload
 * DELETE /api/medical-reports/{id}         remove (admin)
 */
@WebServlet("/api/medical-reports/*")
@MultipartConfig(fileSizeThreshold = 1024 * 1024, maxFileSize = 10 * 1024 * 1024, maxRequestSize = 12 * 1024 * 1024)
public class MedicalReportServlet extends BaseServlet {

    private final MedicalReportService reportService = new MedicalReportService();
    private final PatientService patientService = new PatientService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User actor = requireUser(req);
        String[] parts = pathParts(req);

        if (parts.length == 0) {
            Integer patientId = intParam(req, "patientId");
            if (patientId == null) {
                sendError(resp, 400, "Give a patientId to list reports for");
                return;
            }
            sendOk(resp, mapAll(reportService.forPatient(patientId, actor)));
            return;
        }

        if ("mine".equals(parts[0])) {
            int patientId = patientService.getByUserId(actor.getId()).getId();
            sendOk(resp, mapAll(reportService.forPatient(patientId, actor)));
            return;
        }

        int id = intPart(parts[0], "Report id");

        if (parts.length == 2 && "file".equals(parts[1])) {
            MedicalReport report = reportService.get(id, actor);
            Path file = reportService.fileFor(report);
            resp.setContentType(report.getContentType() == null
                    ? "application/octet-stream" : report.getContentType());
            resp.setHeader("Content-Disposition", "inline; filename=\"" + report.getFileName() + "\"");
            resp.setContentLengthLong(Files.size(file));
            try (OutputStream out = resp.getOutputStream()) {
                Files.copy(file, out);
            }
            return;
        }

        sendOk(resp, reportService.get(id, actor).toMap());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User actor = requireUser(req);
        if (actor.getRole() == Role.PATIENT) {
            sendError(resp, 403, "Reports are uploaded by clinic staff");
            return;
        }
        try {
            int patientId = Integer.parseInt(value(req, "patientId", "0"));
            String appointmentRaw = value(req, "appointmentId", "");
            Integer appointmentId = appointmentRaw.isBlank() ? null : Integer.valueOf(appointmentRaw);
            String title = value(req, "title", "");

            Part filePart = req.getPart("file");
            if (filePart == null) {
                sendError(resp, 400, "Choose a file to upload");
                return;
            }
            try (InputStream in = filePart.getInputStream()) {
                MedicalReport saved = reportService.upload(patientId, appointmentId, title,
                        filePart.getSubmittedFileName(), filePart.getContentType(), in, actor);
                sendCreated(resp, saved.toMap());
            }
        } catch (NumberFormatException e) {
            sendError(resp, 400, "patientId must be a number");
        } catch (jakarta.servlet.ServletException e) {
            sendError(resp, 400, "The upload was not a valid multipart request");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User actor = requireRole(req, Role.ADMIN, Role.SUPER_ADMIN);
        String[] parts = pathParts(req);
        if (parts.length != 1) {
            sendError(resp, 400, "Give the report id in the URL");
            return;
        }
        reportService.delete(intPart(parts[0], "Report id"), actor);
        sendMessage(resp, "The report has been deleted");
    }

    private String value(HttpServletRequest req, String name, String fallback) {
        String value = req.getParameter(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
