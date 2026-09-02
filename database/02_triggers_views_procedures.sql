-- =====================================================================
-- Sunrise Dental Clinic - Triggers, Views and Stored Procedures
-- Business rules pushed into the database layer (Task B, "advanced
-- database features" band of the marking grid).
-- =====================================================================
USE sunrise_dental;

-- ---------------------------------------------------------------------
-- TRIGGER 1: allocate the next queue number automatically and refuse the
-- booking when the session is already full.
-- ---------------------------------------------------------------------
DROP TRIGGER IF EXISTS trg_appointment_before_insert;
DELIMITER $$
CREATE TRIGGER trg_appointment_before_insert
BEFORE INSERT ON appointments
FOR EACH ROW
BEGIN
    DECLARE v_booked   INT DEFAULT 0;
    DECLARE v_capacity INT DEFAULT 0;
    DECLARE v_status   VARCHAR(20);

    SELECT max_patients, status INTO v_capacity, v_status
    FROM doctor_sessions WHERE id = NEW.session_id;

    IF v_status IN ('CLOSED','CANCELLED') THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Session is closed or cancelled - no further bookings allowed';
    END IF;

    SELECT COUNT(*) INTO v_booked
    FROM appointments
    WHERE session_id = NEW.session_id AND status <> 'CANCELLED';

    IF v_booked >= v_capacity THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Session is full - maximum patient limit reached';
    END IF;

    IF NEW.queue_no IS NULL OR NEW.queue_no = 0 THEN
        SET NEW.queue_no = v_booked + 1;
    END IF;
END$$
DELIMITER ;

-- ---------------------------------------------------------------------
-- TRIGGER 2: keep the live "now serving" counter on the session in step
-- with the patient the doctor has actually called in.
-- ---------------------------------------------------------------------
DROP TRIGGER IF EXISTS trg_appointment_after_update;
DELIMITER $$
CREATE TRIGGER trg_appointment_after_update
AFTER UPDATE ON appointments
FOR EACH ROW
BEGIN
    IF NEW.status = 'IN_CONSULTATION' AND OLD.status <> 'IN_CONSULTATION' THEN
        UPDATE doctor_sessions
        SET current_queue_no = NEW.queue_no
        WHERE id = NEW.session_id;
    END IF;
END$$
DELIMITER ;

-- ---------------------------------------------------------------------
-- TRIGGER 3: stamp the payment date on a bill as soon as it is settled.
-- ---------------------------------------------------------------------
DROP TRIGGER IF EXISTS trg_payment_after_insert;
DELIMITER $$
CREATE TRIGGER trg_payment_after_insert
AFTER INSERT ON payments
FOR EACH ROW
BEGIN
    DECLARE v_paid  DECIMAL(10,2) DEFAULT 0;
    DECLARE v_total DECIMAL(10,2) DEFAULT 0;

    SELECT COALESCE(SUM(amount),0) INTO v_paid  FROM payments WHERE bill_id = NEW.bill_id;
    SELECT total                   INTO v_total FROM bills    WHERE id = NEW.bill_id;

    IF v_paid >= v_total THEN
        UPDATE bills SET status = 'PAID', paid_at = NOW() WHERE id = NEW.bill_id;
    END IF;
END$$
DELIMITER ;

-- ---------------------------------------------------------------------
-- VIEW 1: one flat row per appointment - used by every list screen.
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW v_appointment_details AS
SELECT a.id                AS appointment_id,
       a.appointment_no,
       a.queue_no,
       a.status,
       a.notes,
       a.treatment_id,
       a.booked_by,
       a.created_at,
       p.id                AS patient_id,
       p.patient_no,
       p.full_name         AS patient_name,
       p.contact           AS patient_contact,
       p.is_vip,
       s.id                AS session_id,
       s.session_date,
       s.start_time,
       s.end_time,
       s.room_no,
       s.current_queue_no,
       s.consultation_fee,
       d.id                AS doctor_id,
       d.full_name         AS doctor_name,
       t.name              AS treatment_name,
       t.base_price        AS treatment_price
FROM appointments a
JOIN patients        p ON p.id = a.patient_id
JOIN doctor_sessions s ON s.id = a.session_id
JOIN users           d ON d.id = s.doctor_id
LEFT JOIN treatments t ON t.id = a.treatment_id;

-- ---------------------------------------------------------------------
-- VIEW 2: daily income summary for the admin dashboard.
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW v_daily_income AS
SELECT DATE(b.created_at)                                      AS income_date,
       COUNT(*)                                                AS bill_count,
       SUM(b.total)                                            AS billed_total,
       SUM(CASE WHEN b.status = 'PAID'    THEN b.total ELSE 0 END) AS collected_total,
       SUM(CASE WHEN b.status = 'PENDING' THEN b.total ELSE 0 END) AS outstanding_total
FROM bills b
WHERE b.status <> 'CANCELLED'
GROUP BY DATE(b.created_at);

-- ---------------------------------------------------------------------
-- PROCEDURE 1: income report for an arbitrary date range (admin report).
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_income_report;
DELIMITER $$
CREATE PROCEDURE sp_income_report(IN p_from DATE, IN p_to DATE)
BEGIN
    SELECT DATE(b.created_at) AS income_date,
           COUNT(*)           AS bill_count,
           COALESCE(SUM(b.consultation_fee),0) AS consultation_income,
           COALESCE(SUM(b.treatment_total),0)  AS treatment_income,
           COALESCE(SUM(b.discount),0)         AS total_discount,
           COALESCE(SUM(b.total),0)            AS gross_total,
           COALESCE(SUM(CASE WHEN b.status='PAID' THEN b.total ELSE 0 END),0) AS collected
    FROM bills b
    WHERE b.status <> 'CANCELLED'
      AND DATE(b.created_at) BETWEEN p_from AND p_to
    GROUP BY DATE(b.created_at)
    ORDER BY income_date;
END$$
DELIMITER ;

-- ---------------------------------------------------------------------
-- PROCEDURE 2: per-doctor earnings, used by the doctor's own income page.
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_doctor_income;
DELIMITER $$
CREATE PROCEDURE sp_doctor_income(IN p_doctor_id INT, IN p_from DATE, IN p_to DATE)
BEGIN
    SELECT DATE(b.created_at) AS income_date,
           COUNT(*)           AS patient_count,
           COALESCE(SUM(b.consultation_fee),0) AS consultation_income,
           COALESCE(SUM(b.treatment_total),0)  AS treatment_income,
           COALESCE(SUM(b.total),0)            AS total_income,
           COALESCE(SUM(CASE WHEN b.status='PAID' THEN b.total ELSE 0 END),0) AS collected
    FROM bills b
    WHERE b.doctor_id = p_doctor_id
      AND b.status <> 'CANCELLED'
      AND DATE(b.created_at) BETWEEN p_from AND p_to
    GROUP BY DATE(b.created_at)
    ORDER BY income_date;
END$$
DELIMITER ;

-- ---------------------------------------------------------------------
-- PROCEDURE 3: patient registration statistics (admin patient report).
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_patient_report;
DELIMITER $$
CREATE PROCEDURE sp_patient_report(IN p_from DATE, IN p_to DATE)
BEGIN
    SELECT p.id,
           p.patient_no,
           p.full_name,
           p.contact,
           p.is_vip,
           DATE(p.created_at)                AS registered_on,
           COUNT(DISTINCT a.id)              AS visit_count,
           COALESCE(SUM(b.total),0)          AS lifetime_value,
           MAX(s.session_date)               AS last_visit
    FROM patients p
    LEFT JOIN appointments    a ON a.patient_id = p.id AND a.status <> 'CANCELLED'
    LEFT JOIN doctor_sessions s ON s.id = a.session_id
    LEFT JOIN bills           b ON b.appointment_id = a.id AND b.status = 'PAID'
    WHERE DATE(p.created_at) BETWEEN p_from AND p_to
    GROUP BY p.id, p.patient_no, p.full_name, p.contact, p.is_vip, DATE(p.created_at)
    ORDER BY p.created_at DESC;
END$$
DELIMITER ;

-- ---------------------------------------------------------------------
-- FUNCTION: how many patients are still waiting in a session.
-- ---------------------------------------------------------------------
DROP FUNCTION IF EXISTS fn_waiting_count;
DELIMITER $$
CREATE FUNCTION fn_waiting_count(p_session_id INT) RETURNS INT
DETERMINISTIC READS SQL DATA
BEGIN
    DECLARE v_count INT DEFAULT 0;
    SELECT COUNT(*) INTO v_count
    FROM appointments
    WHERE session_id = p_session_id
      AND status IN ('BOOKED','CHECKED_IN');
    RETURN v_count;
END$$
DELIMITER ;
