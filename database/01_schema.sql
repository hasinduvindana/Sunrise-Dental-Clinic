-- =====================================================================
-- Sunrise Dental Clinic - Database Schema (MySQL 8.x)
-- Run:  mysql -u root -p < 01_schema.sql
-- =====================================================================
DROP DATABASE IF EXISTS sunrise_dental;
CREATE DATABASE sunrise_dental CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE sunrise_dental;

-- ---------------------------------------------------------------------
-- 1. Users (single table for every login: role decides the privileges)
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(120) NOT NULL,
    salt          VARCHAR(64)  NOT NULL,
    role          ENUM('SUPER_ADMIN','ADMIN','DOCTOR','NURSE','CASHIER','PATIENT') NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    email         VARCHAR(120),
    phone         VARCHAR(20),
    status        ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_by    INT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_users_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_users_role (role)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 2. Doctor profile (extra attributes only doctors have)
-- ---------------------------------------------------------------------
CREATE TABLE doctor_profiles (
    user_id          INT PRIMARY KEY,
    specialization   VARCHAR(80)  NOT NULL DEFAULT 'General Dentistry',
    qualification    VARCHAR(120),
    consultation_fee DECIMAL(10,2) NOT NULL DEFAULT 1500.00,
    room_no          VARCHAR(20),
    CONSTRAINT fk_docprofile_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 3. Patients (a patient may or may not own a login account)
-- ---------------------------------------------------------------------
CREATE TABLE patients (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    patient_no    VARCHAR(20)  NOT NULL UNIQUE,
    user_id       INT NULL UNIQUE,
    full_name     VARCHAR(120) NOT NULL,
    nic           VARCHAR(20),
    date_of_birth DATE,
    gender        ENUM('MALE','FEMALE','OTHER') DEFAULT 'OTHER',
    address       VARCHAR(255),
    contact       VARCHAR(20)  NOT NULL,
    is_vip        BOOLEAN NOT NULL DEFAULT FALSE,
    notes         TEXT,
    registered_by INT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_patients_user     FOREIGN KEY (user_id)       REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_patients_register FOREIGN KEY (registered_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_patients_name (full_name),
    INDEX idx_patients_contact (contact)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 4. Treatments offered by the clinic (price catalogue)
-- ---------------------------------------------------------------------
CREATE TABLE treatments (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    code             VARCHAR(20)  NOT NULL UNIQUE,
    name             VARCHAR(120) NOT NULL,
    base_price       DECIMAL(10,2) NOT NULL,
    duration_minutes INT NOT NULL DEFAULT 30,
    status           ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE'
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 5. Doctor sessions (a doctor's bookable clinic slot)
-- ---------------------------------------------------------------------
CREATE TABLE doctor_sessions (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    doctor_id        INT NOT NULL,
    session_date     DATE NOT NULL,
    start_time       TIME NOT NULL,
    end_time         TIME NOT NULL,
    room_no          VARCHAR(20),
    max_patients     INT NOT NULL DEFAULT 20,
    consultation_fee DECIMAL(10,2) NOT NULL DEFAULT 1500.00,
    current_queue_no INT NOT NULL DEFAULT 0,
    status           ENUM('SCHEDULED','ACTIVE','CLOSED','CANCELLED') NOT NULL DEFAULT 'SCHEDULED',
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_session_doctor FOREIGN KEY (doctor_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_session_slot UNIQUE (doctor_id, session_date, start_time),
    INDEX idx_session_date (session_date)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 6. Appointments (one patient inside one session, with a queue number)
-- ---------------------------------------------------------------------
CREATE TABLE appointments (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    appointment_no  VARCHAR(25) NOT NULL UNIQUE,
    session_id      INT NOT NULL,
    patient_id      INT NOT NULL,
    treatment_id    INT NULL,
    queue_no        INT NOT NULL DEFAULT 0,
    status          ENUM('BOOKED','CHECKED_IN','IN_CONSULTATION','COMPLETED','CANCELLED','NO_SHOW')
                    NOT NULL DEFAULT 'BOOKED',
    notes           VARCHAR(255),
    booked_by       INT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_appt_session   FOREIGN KEY (session_id)   REFERENCES doctor_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_appt_patient   FOREIGN KEY (patient_id)   REFERENCES patients(id)        ON DELETE CASCADE,
    CONSTRAINT fk_appt_treatment FOREIGN KEY (treatment_id) REFERENCES treatments(id)      ON DELETE SET NULL,
    CONSTRAINT fk_appt_bookedby  FOREIGN KEY (booked_by)    REFERENCES users(id)           ON DELETE SET NULL,
    CONSTRAINT uq_appt_patient_session UNIQUE (session_id, patient_id),
    CONSTRAINT uq_appt_queue           UNIQUE (session_id, queue_no),
    INDEX idx_appt_status (status)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 7. Prescriptions written by a doctor during a consultation
-- ---------------------------------------------------------------------
CREATE TABLE prescriptions (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    appointment_id INT NOT NULL UNIQUE,
    doctor_id      INT NOT NULL,
    patient_id     INT NOT NULL,
    diagnosis      VARCHAR(255),
    advice         TEXT,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_presc_appt    FOREIGN KEY (appointment_id) REFERENCES appointments(id) ON DELETE CASCADE,
    CONSTRAINT fk_presc_doctor  FOREIGN KEY (doctor_id)      REFERENCES users(id)        ON DELETE CASCADE,
    CONSTRAINT fk_presc_patient FOREIGN KEY (patient_id)     REFERENCES patients(id)     ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE prescription_items (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    prescription_id INT NOT NULL,
    drug_name       VARCHAR(120) NOT NULL,
    dosage          VARCHAR(60),
    frequency       VARCHAR(60),
    duration_days   INT,
    instructions    VARCHAR(255),
    CONSTRAINT fk_prescitem_presc FOREIGN KEY (prescription_id) REFERENCES prescriptions(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 8. Medical reports - metadata in MySQL, the file itself on local disk
-- ---------------------------------------------------------------------
CREATE TABLE medical_reports (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    patient_id     INT NOT NULL,
    appointment_id INT NULL,
    title          VARCHAR(150) NOT NULL,
    file_name      VARCHAR(200) NOT NULL,
    file_path      VARCHAR(400) NOT NULL,
    content_type   VARCHAR(100),
    uploaded_by    INT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_report_patient FOREIGN KEY (patient_id)     REFERENCES patients(id)     ON DELETE CASCADE,
    CONSTRAINT fk_report_appt    FOREIGN KEY (appointment_id) REFERENCES appointments(id) ON DELETE SET NULL,
    CONSTRAINT fk_report_upload  FOREIGN KEY (uploaded_by)    REFERENCES users(id)        ON DELETE SET NULL
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 9. Billing
-- ---------------------------------------------------------------------
CREATE TABLE bills (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    bill_no          VARCHAR(25) NOT NULL UNIQUE,
    appointment_id   INT NOT NULL,
    patient_id       INT NOT NULL,
    doctor_id        INT NOT NULL,
    consultation_fee DECIMAL(10,2) NOT NULL DEFAULT 0,
    treatment_total  DECIMAL(10,2) NOT NULL DEFAULT 0,
    discount         DECIMAL(10,2) NOT NULL DEFAULT 0,
    tax              DECIMAL(10,2) NOT NULL DEFAULT 0,
    total            DECIMAL(10,2) NOT NULL DEFAULT 0,
    pricing_strategy VARCHAR(30) NOT NULL DEFAULT 'STANDARD',
    status           ENUM('PENDING','PAID','CANCELLED') NOT NULL DEFAULT 'PENDING',
    generated_by     INT NULL,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at          TIMESTAMP NULL,
    CONSTRAINT fk_bill_appt    FOREIGN KEY (appointment_id) REFERENCES appointments(id) ON DELETE CASCADE,
    CONSTRAINT fk_bill_patient FOREIGN KEY (patient_id)     REFERENCES patients(id)     ON DELETE CASCADE,
    CONSTRAINT fk_bill_doctor  FOREIGN KEY (doctor_id)      REFERENCES users(id)        ON DELETE CASCADE,
    CONSTRAINT fk_bill_genby   FOREIGN KEY (generated_by)   REFERENCES users(id)        ON DELETE SET NULL,
    INDEX idx_bill_status (status),
    INDEX idx_bill_created (created_at)
) ENGINE=InnoDB;

CREATE TABLE bill_items (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    bill_id      INT NOT NULL,
    treatment_id INT NULL,
    description  VARCHAR(150) NOT NULL,
    quantity     INT NOT NULL DEFAULT 1,
    unit_price   DECIMAL(10,2) NOT NULL,
    line_total   DECIMAL(10,2) NOT NULL,
    CONSTRAINT fk_billitem_bill      FOREIGN KEY (bill_id)      REFERENCES bills(id)      ON DELETE CASCADE,
    CONSTRAINT fk_billitem_treatment FOREIGN KEY (treatment_id) REFERENCES treatments(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE payments (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    bill_id     INT NOT NULL,
    amount      DECIMAL(10,2) NOT NULL,
    method      ENUM('CASH','CARD','ONLINE') NOT NULL DEFAULT 'CASH',
    reference   VARCHAR(60),
    received_by INT NULL,
    paid_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payment_bill FOREIGN KEY (bill_id)     REFERENCES bills(id) ON DELETE CASCADE,
    CONSTRAINT fk_payment_user FOREIGN KEY (received_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 10. General settings (clinic name, logo, tax rate ... ) + audit trail
-- ---------------------------------------------------------------------
CREATE TABLE settings (
    setting_key   VARCHAR(60) PRIMARY KEY,
    setting_value TEXT,
    updated_by    INT NULL,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_settings_user FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE audit_log (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    INT NULL,
    role       VARCHAR(20),
    action     VARCHAR(60) NOT NULL,
    entity     VARCHAR(40),
    entity_id  VARCHAR(40),
    details    VARCHAR(400),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_created (created_at)
) ENGINE=InnoDB;
