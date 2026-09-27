
package Employee;
import java.util.*;
import java.sql.*;
import java.sql.Date;
import java.time.DateTimeException;
import java.time.LocalDate;

public class EmployemanagementSsytem {
	// ---------- Database Connection Details ----------
    static final String DB_URL = "jdbc:mysql://localhost:3306/leave_management";
    static final String DB_USER = "root";
    static final String DB_PASSWORD = "2004";
 
    static Scanner sc = new Scanner(System.in);

public static void main(String[] args) {
 
        int choice;
 
        do {
            System.out.println("\n===== EMPLOYEE LEAVE MANAGEMENT SYSTEM =====");
            System.out.println("1. Register Employee");
            System.out.println("2. Apply Leave");
            System.out.println("3. Approve / Reject Leave");
            System.out.println("4. View Leave Balance");
            System.out.println("5. View All Leave Requests");
            System.out.println("6. Exit");
            System.out.print("Enter choice: ");
            choice = sc.nextInt();
 
            switch (choice) {
                case 1: registerEmployee(); break;
                case 2: applyLeave(); break;
                case 3: approveRejectLeave(); break;
                case 4: viewLeaveBalance(); break;
                case 5: viewAllLeaveRequests(); break;
                case 6: System.out.println("Exiting... Goodbye!"); break;
                default: System.out.println("Invalid choice.");
            }
 
        } while (choice != 6);
 
        sc.close();
    }
 
    // ---------- Get a new DB connection ----------
    static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }
 
    /* ================================================================
       MODULE 1 : EMPLOYEE REGISTRATION   (CRUD -> CREATE)
       ================================================================ */
    static void registerEmployee() {
        System.out.print("Enter Name: ");
        sc.nextLine();
        String name = sc.nextLine();
        System.out.print("Enter Email: ");
        String email = sc.nextLine();
        System.out.print("Enter Department: ");
        String department = sc.nextLine();
 
        String insertEmp = "INSERT INTO employees (name, email, department) VALUES (?, ?, ?)";
 
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(insertEmp, Statement.RETURN_GENERATED_KEYS)) {
 
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, department);
            ps.executeUpdate();
 
            // Get the auto-generated emp_id so we can create their leave balance row
            ResultSet keys = ps.getGeneratedKeys();
            int empId = -1;
            if (keys.next()) {
                empId = keys.getInt(1);
            }
 
            // Every new employee starts with a default leave balance
            String insertBalance = "INSERT INTO leave_balance (emp_id, total_leaves, used_leaves) VALUES (?, 20, 0)";
            try (PreparedStatement ps2 = con.prepareStatement(insertBalance)) {
                ps2.setInt(1, empId);
                ps2.executeUpdate();
            }
 
            System.out.println("Employee registered successfully! Emp ID: " + empId);
 
        } catch (SQLException e) {
            System.out.println("Error registering employee: " + e.getMessage());
        }
    }
 
    /* ================================================================
       MODULE 2 : APPLY LEAVE   (CRUD -> CREATE)
       ================================================================ */
    static void applyLeave() {
        System.out.print("Enter Employee ID: ");
        int empId = sc.nextInt();
        sc.nextLine();
 
        System.out.print("Enter Leave Type (Sick/Casual/Earned): ");
        String type = sc.nextLine();
 
        System.out.print("Enter From Date (YYYY-MM-DD): ");
        LocalDate fromDate = LocalDate.parse(sc.nextLine());
 
        System.out.print("Enter To Date (YYYY-MM-DD): ");
        LocalDate toDate = LocalDate.parse(sc.nextLine());
 
        // Calculate number of leave days (inclusive of both dates)
        long totalDays = (toDate.toEpochDay() - fromDate.toEpochDay()) + 1;
 
        if (totalDays <= 0) {
            System.out.println("Invalid date range.");
            return;
        }
 
        String insertLeave = "INSERT INTO leave_requests (emp_id, leave_type, from_date, to_date, total_days, status) "
                + "VALUES (?, ?, ?, ?, ?, 'PENDING')";
 
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(insertLeave)) {
 
            ps.setInt(1, empId);
            ps.setString(2, type);
            ps.setDate(3, Date.valueOf(fromDate));
            ps.setDate(4, Date.valueOf(toDate));
            ps.setLong(5, totalDays);
            ps.executeUpdate();
 
            System.out.println("Leave applied successfully! Total Days: " + totalDays + " | Status: PENDING");
 
        } catch (SQLException e) {
            System.out.println("Error applying leave: " + e.getMessage());
        }
    }
 
    /* ================================================================
       MODULE 3 : APPROVE / REJECT LEAVE   (CRUD -> UPDATE, uses TRANSACTION)
       ------------------------------------------------------------------
       If APPROVED: 2 tables must be updated together:
         1) leave_requests.status  -> 'APPROVED'
         2) leave_balance.used_leaves -> increased by total_days
       Both updates happen inside ONE transaction:
         - If both succeed -> commit()
         - If anything fails (e.g. not enough balance) -> rollback()
       This prevents a leave being marked approved while the balance
       table stays unchanged (data inconsistency).
       ================================================================ */
    static void approveRejectLeave() {
        System.out.print("Enter Leave ID: ");
        int leaveId = sc.nextInt();
        sc.nextLine();
        System.out.print("Approve or Reject? (A/R): ");
        String decision = sc.nextLine().trim().toUpperCase();
 
        Connection con = null;
 
        try {
            con = getConnection();
            con.setAutoCommit(false); // ---- START TRANSACTION ----
 
            // Step 1: fetch the leave request details
            String selectLeave = "SELECT emp_id, total_days, status FROM leave_requests WHERE leave_id = ?";
            PreparedStatement selectPs = con.prepareStatement(selectLeave);
            selectPs.setInt(1, leaveId);
            ResultSet rs = selectPs.executeQuery();
 
            if (!rs.next()) {
                System.out.println("Leave request not found.");
                con.rollback();
                return;
            }
 
            int empId = rs.getInt("emp_id");
            int totalDays = rs.getInt("total_days");
            String currentStatus = rs.getString("status");
 
            if (!currentStatus.equals("PENDING")) {
                System.out.println("This leave request has already been " + currentStatus + ".");
                con.rollback();
                return;
            }
 
            if (decision.equals("R")) {
                // ---- Reject: only update status, no balance change ----
                String updateStatus = "UPDATE leave_requests SET status = 'REJECTED' WHERE leave_id = ?";
                PreparedStatement updatePs = con.prepareStatement(updateStatus);
                updatePs.setInt(1, leaveId);
                updatePs.executeUpdate();
 
                con.commit(); // ---- COMMIT TRANSACTION ----
                System.out.println("Leave Rejected.");
                return;
            }
 
            if (decision.equals("A")) {
                // Step 2: check remaining balance before approving
                String balanceQuery = "SELECT total_leaves, used_leaves FROM leave_balance WHERE emp_id = ? FOR UPDATE";
                PreparedStatement balancePs = con.prepareStatement(balanceQuery);
                balancePs.setInt(1, empId);
                ResultSet balRs = balancePs.executeQuery();
 
                if (!balRs.next()) {
                    System.out.println("Leave balance record not found.");
                    con.rollback();
                    return;
                }
 
                int totalLeaves = balRs.getInt("total_leaves");
                int usedLeaves = balRs.getInt("used_leaves");
                int remaining = totalLeaves - usedLeaves;
 
                if (totalDays > remaining) {
                    System.out.println("Cannot approve. Insufficient leave balance (Remaining: " + remaining + ")");
                    con.rollback(); // ---- ROLLBACK: cancel the transaction ----
                    return;
                }
 
                // Step 3a: update leave_requests status
                String updateStatus = "UPDATE leave_requests SET status = 'APPROVED' WHERE leave_id = ?";
                PreparedStatement updatePs = con.prepareStatement(updateStatus);
                updatePs.setInt(1, leaveId);
                updatePs.executeUpdate();
 
                // Step 3b: update leave_balance used_leaves
                String updateBalance = "UPDATE leave_balance SET used_leaves = used_leaves + ? WHERE emp_id = ?";
                PreparedStatement balUpdatePs = con.prepareStatement(updateBalance);
                balUpdatePs.setInt(1, totalDays);
                balUpdatePs.setInt(2, empId);
                balUpdatePs.executeUpdate();
 
                con.commit(); // ---- COMMIT: both updates saved together ----
                System.out.println("Leave Approved. Balance updated.");
                return;
            }
 
            System.out.println("Invalid decision entered. Use A or R.");
            con.rollback();
 
        } catch (SQLException e) {
            System.out.println("Transaction failed, rolling back. Error: " + e.getMessage());
            try {
                if (con != null) con.rollback();
            } catch (SQLException ex) {
                System.out.println("Rollback failed: " + ex.getMessage());
            }
        } finally {
            try {
                if (con != null) {
                    con.setAutoCommit(true);
                    con.close();
                }
            } catch (SQLException e) {
                System.out.println("Error closing connection: " + e.getMessage());
            }
        }
    }
 
    /* ================================================================
       MODULE 4 : LEAVE BALANCE TRACKING   (CRUD -> READ)
       ================================================================ */
    static void viewLeaveBalance() {
        System.out.print("Enter Employee ID: ");
        int empId = sc.nextInt();
 
        String query = "SELECT e.name, lb.total_leaves, lb.used_leaves "
                + "FROM leave_balance lb JOIN employees e ON lb.emp_id = e.emp_id "
                + "WHERE lb.emp_id = ?";
 
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(query)) {
 
            ps.setInt(1, empId);
            ResultSet rs = ps.executeQuery();
 
            if (rs.next()) {
                String name = rs.getString("name");
                int total = rs.getInt("total_leaves");
                int used = rs.getInt("used_leaves");
                int remaining = total - used;
 
                System.out.println("\n--- Leave Balance ---");
                System.out.println("Employee   : " + name);
                System.out.println("Total      : " + total);
                System.out.println("Used       : " + used);
                System.out.println("Remaining  : " + remaining);
            } else {
                System.out.println("No record found for this Employee ID.");
            }
 
        } catch (SQLException e) {
            System.out.println("Error fetching balance: " + e.getMessage());
        }
    }
 
    /* ================================================================
       BONUS : VIEW ALL LEAVE REQUESTS   (CRUD -> READ)
       ================================================================ */
    static void viewAllLeaveRequests() {
        String query = "SELECT lr.leave_id, e.name, lr.leave_type, lr.from_date, lr.to_date, "
                + "lr.total_days, lr.status "
                + "FROM leave_requests lr JOIN employees e ON lr.emp_id = e.emp_id "
                + "ORDER BY lr.leave_id";
 
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {
 
            System.out.println("\n--- All Leave Requests ---");
            while (rs.next()) {
                System.out.println(
                        "ID: " + rs.getInt("leave_id") +
                        " | Name: " + rs.getString("name") +
                        " | Type: " + rs.getString("leave_type") +
                        " | From: " + rs.getDate("from_date") +
                        " | To: " + rs.getDate("to_date") +
                        " | Days: " + rs.getInt("total_days") +
                        " | Status: " + rs.getString("status")
                );
            }
 
        } catch (SQLException e) {
            System.out.println("Error fetching leave requests: " + e.getMessage());

}
    }
}
