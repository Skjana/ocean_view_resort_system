--  Ocean View Resort HRMS
--  Run:  mysql -u root -p < database/schema.sql

CREATE DATABASE IF NOT EXISTS ocean_view_resort
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ocean_view_resort;

DROP TABLE IF EXISTS audit_log;
DROP TABLE IF EXISTS extra_charges;
DROP TABLE IF EXISTS maintenance_issues;
DROP TABLE IF EXISTS housekeeping_log;
DROP TABLE IF EXISTS reservations;
DROP TABLE IF EXISTS guests;
DROP TABLE IF EXISTS rooms;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
  id            VARCHAR(10)  PRIMARY KEY,
  username      VARCHAR(50)  NOT NULL UNIQUE,
  password_hash VARCHAR(64)  NOT NULL,
  role          ENUM('ADMINISTRATOR','STAFF') NOT NULL DEFAULT 'STAFF',
  full_name     VARCHAR(100) NOT NULL,
  email         VARCHAR(100),
  is_active     TINYINT(1)   NOT NULL DEFAULT 1,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE rooms (
  id            VARCHAR(10)   PRIMARY KEY,
  room_number   VARCHAR(5)   NOT NULL UNIQUE,
  room_type     ENUM('STANDARD','DELUXE','SUITE') NOT NULL,
  floor         INT           NOT NULL,
  max_occupancy INT           NOT NULL,
  nightly_rate  DECIMAL(10,2) NOT NULL,
  view_desc     VARCHAR(100),
  amenities     VARCHAR(300),
  is_available  TINYINT(1)    NOT NULL DEFAULT 1,
  room_status   VARCHAR(20)   NOT NULL DEFAULT 'AVAILABLE'
    COMMENT 'AVAILABLE, CLEANING, OUT_OF_ORDER'
);

CREATE TABLE guests (
  id           INT AUTO_INCREMENT PRIMARY KEY,
  full_name    VARCHAR(100) NOT NULL,
  email        VARCHAR(100),
  phone        VARCHAR(20),
  address      VARCHAR(255),
  nationality  VARCHAR(50),
  notes        TEXT,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_guest_email (email)
);

CREATE TABLE reservations (
  id               VARCHAR(15)   PRIMARY KEY,
  guest_id         INT           NULL,
  guest_name       VARCHAR(100)  NOT NULL,
  guest_address    VARCHAR(255),
  contact_number   VARCHAR(15)   NOT NULL,
  email            VARCHAR(100),
  nationality      VARCHAR(50),
  room_id          VARCHAR(10)   NOT NULL,
  check_in_date    DATE          NOT NULL,
  check_out_date   DATE          NOT NULL,
  nights           INT           NOT NULL DEFAULT 0,
  status           ENUM('CONFIRMED','CANCELLED','CHECKED_OUT') NOT NULL DEFAULT 'CONFIRMED',
  special_requests TEXT,
  sub_total        DECIMAL(12,2) NOT NULL DEFAULT 0,
  tax_amount       DECIMAL(12,2) NOT NULL DEFAULT 0,
  discount         DECIMAL(12,2) NOT NULL DEFAULT 0,
  total_amount     DECIMAL(12,2) NOT NULL DEFAULT 0,
  is_loyalty       TINYINT(1)    NOT NULL DEFAULT 0,
  created_by       VARCHAR(50),
  created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (room_id)   REFERENCES rooms(id),
  FOREIGN KEY (guest_id)  REFERENCES guests(id)
);

CREATE TABLE audit_log (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  action      VARCHAR(20)  NOT NULL,
  table_name  VARCHAR(50)  NOT NULL,
  record_id   VARCHAR(20),
  user_id     VARCHAR(50),
  detail      TEXT,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE housekeeping_log (
  id         INT AUTO_INCREMENT PRIMARY KEY,
  room_id    VARCHAR(10) NOT NULL,
  status     VARCHAR(20) NOT NULL COMMENT 'CLEANING, CLEANED, OUT_OF_ORDER, BACK_IN_SERVICE',
  noted_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  staff_id   VARCHAR(10),
  notes      VARCHAR(255),
  FOREIGN KEY (room_id) REFERENCES rooms(id)
);

CREATE TABLE extra_charges (
  id              INT AUTO_INCREMENT PRIMARY KEY,
  reservation_id  VARCHAR(15) NOT NULL,
  description     VARCHAR(100) NOT NULL,
  amount          DECIMAL(10,2) NOT NULL,
  charged_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by      VARCHAR(50),
  FOREIGN KEY (reservation_id) REFERENCES reservations(id)
);

CREATE TABLE maintenance_issues (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  room_id     VARCHAR(10) NOT NULL,
  title       VARCHAR(100) NOT NULL,
  description TEXT,
  category    VARCHAR(30) NOT NULL COMMENT 'AC, PLUMBING, ELECTRICAL, FURNITURE, OTHER',
  status      VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN, IN_PROGRESS, DONE',
  assigned_to VARCHAR(10),
  reported_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  resolved_at DATETIME NULL,
  FOREIGN KEY (room_id)     REFERENCES rooms(id),
  FOREIGN KEY (assigned_to) REFERENCES users(id)
);


DROP PROCEDURE IF EXISTS sp_check_room_availability;
DELIMITER $$
CREATE PROCEDURE sp_check_room_availability(
  IN  p_room_type VARCHAR(20),
  IN  p_check_in  DATE,
  IN  p_check_out DATE
)
BEGIN
  SELECT r.*
  FROM rooms r
  WHERE r.room_type = p_room_type
    AND r.id NOT IN (
      SELECT res.room_id
      FROM reservations res
      WHERE res.status = 'CONFIRMED'
        AND NOT (p_check_out <= res.check_in_date
              OR p_check_in  >= res.check_out_date)
    );
END$$
DELIMITER ;

------------- stored procedures ---------
DROP PROCEDURE IF EXISTS sp_calculate_bill;
DELIMITER $$
CREATE PROCEDURE sp_calculate_bill(
  IN  p_res_id      VARCHAR(15),
  OUT p_sub_total   DECIMAL(12,2),
  OUT p_tax         DECIMAL(12,2),
  OUT p_discount    DECIMAL(12,2),
  OUT p_total       DECIMAL(12,2),
  OUT p_is_loyalty  TINYINT(1),
  OUT p_nights      INT,
  OUT p_rate        DECIMAL(10,2),
  OUT p_room_type   VARCHAR(20),
  OUT p_room_number VARCHAR(5)
)
BEGIN
  DECLARE v_nights     INT;
  DECLARE v_rate       DECIMAL(10,2);
  DECLARE v_guest      VARCHAR(100);
  DECLARE v_prior      INT;
  SELECT r.nights, rm.nightly_rate, r.guest_name, rm.room_type, rm.room_number
  INTO   v_nights, v_rate, v_guest, p_room_type, p_room_number
  FROM   reservations r JOIN rooms rm ON rm.id = r.room_id
  WHERE  r.id = p_res_id;
  SET p_nights    = v_nights;
  SET p_rate      = v_rate;
  SET p_sub_total = v_nights * v_rate;
  SET p_tax       = p_sub_total * 0.10;
  SELECT COUNT(*) INTO v_prior
  FROM   reservations
  WHERE  guest_name = v_guest AND status = 'CHECKED_OUT' AND id <> p_res_id;
  SET p_is_loyalty = IF(v_prior >= 2, 1, 0);
  SET p_discount   = IF(p_is_loyalty = 1, p_sub_total * 0.10, 0);
  SET p_total      = p_sub_total + p_tax - p_discount;
END$$
DELIMITER ;

DROP PROCEDURE IF EXISTS sp_next_reservation_id;
DELIMITER $$
CREATE PROCEDURE sp_next_reservation_id(OUT p_id VARCHAR(15))
BEGIN
  DECLARE v_max INT DEFAULT 0;
  SELECT COALESCE(MAX(CAST(SUBSTRING(id,5) AS UNSIGNED)),0)
  INTO v_max FROM reservations;
  SET p_id = CONCAT('RES-', LPAD(v_max + 1, 4, '0'));
END$$
DELIMITER ;

------ triggers -------------------------
DROP TRIGGER IF EXISTS trg_validate_dates;
DELIMITER $$
CREATE TRIGGER trg_validate_dates
BEFORE INSERT ON reservations FOR EACH ROW
BEGIN
  IF NEW.check_out_date <= NEW.check_in_date THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Check-out date must be after check-in date';
  END IF;
  SET NEW.nights = DATEDIFF(NEW.check_out_date, NEW.check_in_date);
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_audit_insert;
DELIMITER $$
CREATE TRIGGER trg_audit_insert
AFTER INSERT ON reservations FOR EACH ROW
BEGIN
  INSERT INTO audit_log(action,table_name,record_id,user_id,detail)
  VALUES('INSERT','reservations',NEW.id,NEW.created_by,
    CONCAT('Guest:',NEW.guest_name,' Room:',NEW.room_id,
           ' ',NEW.check_in_date,' to ',NEW.check_out_date));
END$$
DELIMITER ;

DROP TRIGGER IF EXISTS trg_audit_update;
DELIMITER $$
CREATE TRIGGER trg_audit_update
AFTER UPDATE ON reservations FOR EACH ROW
BEGIN
  IF OLD.status <> NEW.status THEN
    INSERT INTO audit_log(action,table_name,record_id,user_id,detail)
    VALUES('UPDATE','reservations',NEW.id,'system',
      CONCAT(OLD.status,' -> ',NEW.status));
  END IF;
END$$
DELIMITER ;

-------- views ---------------------------
DROP VIEW IF EXISTS vw_reservation_details;
CREATE VIEW vw_reservation_details AS
SELECT r.id,r.guest_id,r.guest_name,r.guest_address,r.contact_number,r.email,
       r.nationality,r.room_id,rm.room_number,rm.room_type,rm.nightly_rate,
       r.check_in_date,r.check_out_date,r.nights,r.status,r.special_requests,
       r.sub_total,r.tax_amount,r.discount,r.total_amount,
       r.is_loyalty,r.created_by,r.created_at
FROM reservations r JOIN rooms rm ON rm.id=r.room_id;

DROP VIEW IF EXISTS vw_occupancy_report;
CREATE VIEW vw_occupancy_report AS
SELECT rm.room_type,
       COUNT(DISTINCT rm.id) AS total_rooms,
       COUNT(DISTINCT CASE WHEN res.id IS NOT NULL THEN rm.id END) AS occupied,
       MAX(rm.nightly_rate) AS nightly_rate
FROM rooms rm
LEFT JOIN reservations res ON res.room_id = rm.id
  AND res.status = 'CONFIRMED'
  AND CURDATE() BETWEEN res.check_in_date AND res.check_out_date
GROUP BY rm.room_type;

DROP VIEW IF EXISTS vw_revenue_report;
CREATE VIEW vw_revenue_report AS
SELECT DATE_FORMAT(check_in_date,'%Y-%m') AS month,
       COUNT(*) AS bookings, SUM(total_amount) AS revenue
FROM reservations
WHERE status<>'CANCELLED'
GROUP BY DATE_FORMAT(check_in_date,'%Y-%m')
ORDER BY month;