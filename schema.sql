-- ============================================================
-- EMPLOYEE LEAVE MANAGEMENT SYSTEM - DATABASE SCHEMA
-- ============================================================
-- Run this first in MySQL before running the Java program.
-- ============================================================

CREATE DATABASE IF NOT EXISTS leave_management;
USE leave_management;

-- ---------- Table 1: employees ----------
CREATE TABLE IF NOT EXISTS employees (
    emp_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    department VARCHAR(50) NOT NULL
);

-- ---------- Table 2: leave_requests ----------
CREATE TABLE IF NOT EXISTS leave_requests (
    leave_id INT AUTO_INCREMENT PRIMARY KEY,
    emp_id INT NOT NULL,
    leave_type VARCHAR(30) NOT NULL,
    from_date DATE NOT NULL,
    to_date DATE NOT NULL,
    total_days INT NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',   -- PENDING, APPROVED, REJECTED
    FOREIGN KEY (emp_id) REFERENCES employees(emp_id)
);

-- ---------- Table 3: leave_balance ----------
CREATE TABLE IF NOT EXISTS leave_balance (
    emp_id INT PRIMARY KEY,
    total_leaves INT DEFAULT 20,
    used_leaves INT DEFAULT 0,
    FOREIGN KEY (emp_id) REFERENCES employees(emp_id)
);
