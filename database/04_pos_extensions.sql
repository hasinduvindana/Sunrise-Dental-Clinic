-- =====================================================================
-- Sunrise Dental Clinic - POS extensions
-- Adds the columns and tables the POS front end needs on top of the
-- core schema. Run after 01, 02 and 03.
--   mysql -u root -p < 04_pos_extensions.sql
-- =====================================================================
USE sunrise_dental;

-- ---------------------------------------------------------------------
-- 1. Patient administrator is a role of its own at the front desk
-- ---------------------------------------------------------------------
ALTER TABLE users
    MODIFY role ENUM('SUPER_ADMIN','ADMIN','DOCTOR','NURSE','CASHIER','PATIENT_ADMIN','PATIENT') NOT NULL;

-- ---------------------------------------------------------------------
-- 2. Clinical detail the nurse and doctor screens show
-- ---------------------------------------------------------------------
ALTER TABLE patients
    ADD COLUMN email           VARCHAR(120)  NULL AFTER contact,
    ADD COLUMN blood_group     VARCHAR(10)   NOT NULL DEFAULT 'N/A' AFTER email,
    ADD COLUMN allergies       VARCHAR(255)  NOT NULL DEFAULT 'None' AFTER blood_group,
    ADD COLUMN medical_history TEXT          NULL AFTER allergies,
    ADD UNIQUE KEY uq_patients_nic (nic);

-- The POS looks patients up by NIC, so it must be indexed and unique.

-- ---------------------------------------------------------------------
-- 3. Treatment catalogue detail
-- ---------------------------------------------------------------------
ALTER TABLE treatments
    ADD COLUMN category    VARCHAR(60)  NOT NULL DEFAULT 'General' AFTER name,
    ADD COLUMN description VARCHAR(255) NULL AFTER category;

-- ---------------------------------------------------------------------
-- 4. Per-doctor treatment charges
--    A doctor may charge more or less than the catalogue price for the
--    same procedure. Missing row = catalogue price applies.
-- ---------------------------------------------------------------------
CREATE TABLE doctor_treatment_pricing (
    doctor_id    INT NOT NULL,
    treatment_id INT NOT NULL,
    custom_fee   DECIMAL(10,2) NOT NULL,
    updated_by   INT NULL,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (doctor_id, treatment_id),
    CONSTRAINT fk_dtp_doctor    FOREIGN KEY (doctor_id)    REFERENCES users(id)      ON DELETE CASCADE,
    CONSTRAINT fk_dtp_treatment FOREIGN KEY (treatment_id) REFERENCES treatments(id) ON DELETE CASCADE,
    CONSTRAINT fk_dtp_updatedby FOREIGN KEY (updated_by)   REFERENCES users(id)      ON DELETE SET NULL
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 5. Appointment detail used by the POS, triage and cashier screens
--    PAID is added to the status list: the cashier collects the
--    consultation fee before the patient is sent through to triage.
-- ---------------------------------------------------------------------
ALTER TABLE appointments
    MODIFY status ENUM('BOOKED','PAID','CHECKED_IN','IN_CONSULTATION','COMPLETED','CANCELLED','NO_SHOW')
        NOT NULL DEFAULT 'BOOKED',
    ADD COLUMN time_slot        VARCHAR(20)  NULL AFTER queue_no,
    ADD COLUMN vitals_bp        VARCHAR(20)  NULL AFTER notes,
    ADD COLUMN vitals_pulse     VARCHAR(10)  NULL AFTER vitals_bp,
    ADD COLUMN chief_complaint  VARCHAR(255) NULL AFTER vitals_pulse,
    ADD COLUMN triaged_by       INT NULL AFTER chief_complaint,
    ADD COLUMN triaged_at       TIMESTAMP NULL AFTER triaged_by,
    ADD COLUMN cancel_reason    VARCHAR(255) NULL AFTER triaged_at,
    ADD COLUMN receipt_no       VARCHAR(30)  NULL AFTER cancel_reason,
    ADD CONSTRAINT fk_appt_triagedby FOREIGN KEY (triaged_by) REFERENCES users(id) ON DELETE SET NULL;

-- ---------------------------------------------------------------------
-- 6. Payment detail printed on the POS receipt
-- ---------------------------------------------------------------------
ALTER TABLE payments
    ADD COLUMN receipt_no    VARCHAR(30)  NULL AFTER id,
    ADD COLUMN card_type     VARCHAR(20)  NULL AFTER method,
    ADD COLUMN card_provider VARCHAR(30)  NULL AFTER card_type,
    ADD COLUMN card_masked   VARCHAR(30)  NULL AFTER card_provider,
    ADD COLUMN bank_name     VARCHAR(80)  NULL AFTER card_masked,
    ADD UNIQUE KEY uq_payment_receipt (receipt_no);

-- The full card number is never stored: only the masked form the POS
-- prints on the receipt, which is what PCI guidance requires.

-- ---------------------------------------------------------------------
-- 7. Diagnostic report detail
-- ---------------------------------------------------------------------
ALTER TABLE medical_reports
    ADD COLUMN report_no   VARCHAR(30)  NULL AFTER id,
    ADD COLUMN report_type VARCHAR(120) NOT NULL DEFAULT 'Clinical Report' AFTER title,
    ADD COLUMN doctor_id   INT NULL AFTER report_type,
    ADD COLUMN report_date DATE NULL AFTER doctor_id,
    ADD COLUMN findings    TEXT NULL AFTER report_date,
    ADD COLUMN status      ENUM('DRAFT','VERIFIED') NOT NULL DEFAULT 'VERIFIED' AFTER findings,
    ADD CONSTRAINT fk_report_doctor FOREIGN KEY (doctor_id) REFERENCES users(id) ON DELETE SET NULL,
    ADD UNIQUE KEY uq_report_no (report_no);

-- The file itself is optional now: a doctor can record findings without
-- attaching a scan, so file_name and file_path may be blank.
ALTER TABLE medical_reports
    MODIFY file_name VARCHAR(200) NULL,
    MODIFY file_path VARCHAR(400) NULL;

-- ---------------------------------------------------------------------
-- 8. Bill number prefix used by the POS, plus branding keys the UI reads
-- ---------------------------------------------------------------------
INSERT INTO settings (setting_key, setting_value) VALUES
    ('clinic.tagline',        'Advanced Dental Care & Implant Center'),
    ('clinic.reg.no',         'PV-89214-MED'),
    ('clinic.icon',           'assets/images/logo-icon.png'),
    ('billing.receipt.prefix','SRD-REC-'),
    ('billing.appointment.prefix','SRD-APT-'),
    ('clinic.footer.note',    'Thank you for choosing SunRise Dental Clinic. For emergencies call +94 77 123 4567.')
ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value);

UPDATE settings SET setting_value = 'SRD-INV-' WHERE setting_key = 'billing.bill.prefix';

-- ---------------------------------------------------------------------
-- 9. View: doctor-wise revenue, used by the income report screen
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW v_doctor_revenue AS
SELECT  u.id                                        AS doctor_id,
        u.full_name                                 AS doctor_name,
        COALESCE(dp.specialization, 'General')      AS specialty,
        COUNT(DISTINCT b.id)                        AS invoice_count,
        COALESCE(SUM(CASE WHEN b.status = 'PAID' THEN b.total ELSE 0 END), 0) AS collected,
        COALESCE(SUM(CASE WHEN b.status = 'PENDING' THEN b.total ELSE 0 END), 0) AS outstanding
FROM        users u
LEFT JOIN   doctor_profiles dp ON dp.user_id = u.id
LEFT JOIN   bills b            ON b.doctor_id = u.id
WHERE       u.role = 'DOCTOR'
GROUP BY    u.id, u.full_name, dp.specialization;

-- ---------------------------------------------------------------------
-- 10. Trigger: a cancelled appointment must free its slot and record why
-- ---------------------------------------------------------------------
DROP TRIGGER IF EXISTS trg_appointment_cancel_audit;
DELIMITER $$
CREATE TRIGGER trg_appointment_cancel_audit
AFTER UPDATE ON appointments
FOR EACH ROW
BEGIN
    IF NEW.status = 'CANCELLED' AND OLD.status <> 'CANCELLED' THEN
        INSERT INTO audit_log (user_id, role, action, entity, entity_id, details)
        VALUES (NEW.booked_by, 'SYSTEM', 'APPOINTMENT_CANCELLED', 'appointments',
                NEW.appointment_no,
                CONCAT('Token ', NEW.queue_no, ' cancelled. Reason: ',
                       COALESCE(NEW.cancel_reason, 'not given')));
    END IF;
END$$
DELIMITER ;

-- ---------------------------------------------------------------------
-- 11. Treatment catalogue used by the POS screens
--     Categories and descriptions are what the doctor pricing screen and
--     the invoice builder show beside each procedure.
-- ---------------------------------------------------------------------
INSERT INTO treatments (code, name, category, description, base_price, duration_minutes) VALUES
 ('SCL', 'Scaling & Polishing (Full Mouth)',      'Preventive',     'Ultrasonic plaque, tartar and stain removal',            4500.00, 45),
 ('RCT', 'Root Canal Treatment (Single Canal)',   'Endodontics',    'Complete pulp extirpation and canal sealing',          16000.00, 90),
 ('WHT', 'Laser Teeth Whitening',                 'Cosmetic',       'In-chair diode laser bleaching with fluoride therapy', 25000.00, 60),
 ('CRN', 'Zirconia / Porcelain Crown',            'Prosthodontics', 'Custom fabricated high-strength aesthetic crown',      32000.00, 60),
 ('EXT', 'Surgical Tooth Extraction',             'Oral Surgery',   'Surgical extraction of impacted or fractured tooth',    7500.00, 45),
 ('IMP', 'Titanium Dental Implant',               'Implantology',   'Surgical placement of titanium fixture with abutment', 95000.00, 120),
 ('BRC', 'Orthodontic Braces Monthly Adjustment', 'Orthodontics',   'Archwire replacement and elastic power chain activation', 6000.00, 30),
 ('CMP', 'Composite Light-Cure Filling',          'Restorative',    'Aesthetic tooth-coloured composite cavity restoration',  3800.00, 40),
 ('XRY', 'Intraoral Digital Dental X-Ray (IOPA)', 'Radiology',      'High-resolution digital sensor radiograph',              1500.00, 15)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), category = VALUES(category),
    description = VALUES(description), base_price = VALUES(base_price);

-- The generic entries seeded in 03 are retired so the catalogue on screen is
-- the POS one. Nothing is deleted: past invoices still reference them.
UPDATE treatments SET status = 'INACTIVE'
 WHERE code IN ('CONS','SCAL','FILL','EXTR','CROWN','WHIT','DENT','ORTHO','XRAY');
