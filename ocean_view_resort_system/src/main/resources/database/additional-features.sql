
--  Ocean View Resort HRMS  —  ADDITIONAL FEATURES
--  Use this only when you already have the base schema and want to
--  add new tables/columns without dropping data.
--  For a fresh install, use schema.sql instead (includes everything).
--  Objects added: room_status, housekeeping_log, guests, guest_id,
--  extra_charges, maintenance_issues.
--  Run: mysql -u root -p ocean_view_resort < schema-additions.sql

USE ocean_view_resort;

-- ROOM STATUS -----------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rooms' AND COLUMN_NAME = 'room_status');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE rooms ADD COLUMN room_status VARCHAR(20) NOT NULL DEFAULT ''AVAILABLE'' COMMENT ''AVAILABLE, CLEANING, OUT_OF_ORDER'' AFTER is_available',
  'SELECT 1 AS skip');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- HOUSEKEEPING LOG -----------------
CREATE TABLE IF NOT EXISTS housekeeping_log (
  id         INT AUTO_INCREMENT PRIMARY KEY,
  room_id    VARCHAR(10) NOT NULL,
  status     VARCHAR(20) NOT NULL COMMENT 'CLEANING, CLEANED, OUT_OF_ORDER, BACK_IN_SERVICE',
  noted_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  staff_id   VARCHAR(10),
  notes      VARCHAR(255),
  FOREIGN KEY (room_id) REFERENCES rooms(id)
);

----- HOUSEKEEPING LOG -----------------------
CREATE TABLE IF NOT EXISTS guests (
  id           INT AUTO_INCREMENT PRIMARY KEY,
  full_name    VARCHAR(100) NOT NULL,
  email        VARCHAR(100),
  phone        VARCHAR(20),
  address      VARCHAR(255),
  nationality  VARCHAR(50),
  notes        TEXT,
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_guest_email (email)
);

-- Add guest_id and FK
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reservations' AND COLUMN_NAME = 'guest_id');
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE reservations ADD COLUMN guest_id INT NULL AFTER guest_name',
  'SELECT 1 AS skip');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_exists = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reservations' AND CONSTRAINT_NAME = 'fk_reservation_guest');
SET @sql = IF(@fk_exists = 0,
  'ALTER TABLE reservations ADD CONSTRAINT fk_reservation_guest FOREIGN KEY (guest_id) REFERENCES guests(id)',
  'SELECT 1 AS skip');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- EXTRA CHARGES (mini-bar, room service)
CREATE TABLE IF NOT EXISTS extra_charges (
  id              INT AUTO_INCREMENT PRIMARY KEY,
  reservation_id  VARCHAR(15) NOT NULL,
  description     VARCHAR(100) NOT NULL,
  amount          DECIMAL(10,2) NOT NULL,
  charged_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by      VARCHAR(50),
  FOREIGN KEY (reservation_id) REFERENCES reservations(id)
);

----------- MAINTENANCE ISSUES (per room) -------------------
CREATE TABLE IF NOT EXISTS maintenance_issues (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  room_id     VARCHAR(10) NOT NULL,
  title       VARCHAR(100) NOT NULL,
  description TEXT,
  category    VARCHAR(30) NOT NULL COMMENT 'AC, PLUMBING, ELECTRICAL, FURNITURE, OTHER',
  status      VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN, IN_PROGRESS, DONE',
  assigned_to VARCHAR(10),
  reported_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  resolved_at DATETIME NULL,
  FOREIGN KEY (room_id) REFERENCES rooms(id),
  FOREIGN KEY (assigned_to) REFERENCES users(id)
);