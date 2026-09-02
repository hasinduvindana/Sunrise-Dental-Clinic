package dao;

/**
 * Single access point for every DAO in the system.
 *
 * DESIGN PATTERN: Abstract Factory + Singleton. Services never call
 * "new UserDAO()" directly, so swapping in a mock or a different persistence
 * technology means changing this one class - which is exactly what the unit
 * tests in src/test do.
 */
public class DAOFactory {

    private static final DAOFactory INSTANCE = new DAOFactory();

    private final UserDAO userDAO = new UserDAO();
    private final PatientDAO patientDAO = new PatientDAO();
    private final TreatmentDAO treatmentDAO = new TreatmentDAO();
    private final SessionDAO sessionDAO = new SessionDAO();
    private final AppointmentDAO appointmentDAO = new AppointmentDAO();
    private final BillDAO billDAO = new BillDAO();
    private final PrescriptionDAO prescriptionDAO = new PrescriptionDAO();
    private final MedicalReportDAO medicalReportDAO = new MedicalReportDAO();
    private final SettingsDAO settingsDAO = new SettingsDAO();
    private final AuditDAO auditDAO = new AuditDAO();
    private final ReportDAO reportDAO = new ReportDAO();

    private DAOFactory() { }

    public static DAOFactory getInstance() {
        return INSTANCE;
    }

    public UserDAO users() { return userDAO; }
    public PatientDAO patients() { return patientDAO; }
    public TreatmentDAO treatments() { return treatmentDAO; }
    public SessionDAO sessions() { return sessionDAO; }
    public AppointmentDAO appointments() { return appointmentDAO; }
    public BillDAO bills() { return billDAO; }
    public PrescriptionDAO prescriptions() { return prescriptionDAO; }
    public MedicalReportDAO medicalReports() { return medicalReportDAO; }
    public SettingsDAO settings() { return settingsDAO; }
    public AuditDAO audit() { return auditDAO; }
    public ReportDAO reports() { return reportDAO; }
}
