package ro.mpp2026.demos;

import ro.mpp2026.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DeadlockDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== DEADLOCK DEMO ===");

        resetSalaries();

        System.out.println("\nRun 1: Deadlock occurs (no ordering)");
        runDeadlockScenario(false);

        Thread.sleep(1000);

        System.out.println("\nRun 2: Deadlock prevented (consistent ordering)");
        runDeadlockScenario(true);
    }

    private static void runDeadlockScenario(boolean ordered) throws InterruptedException {
        Thread transactionA = new Thread(() -> transactionA(ordered), "T1");
        Thread transactionB = new Thread(() -> transactionB(ordered), "T2");

        transactionA.start();
        Thread.sleep(100); // slight delay to increase deadlock chance
        transactionB.start();

        transactionA.join();
        transactionB.join();

        printFinalSalaries();
    }

    private static void transactionA(boolean ordered) {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);

            if (ordered) {
                // consistent ordering: always lock id=1 first, then id=2
                updateSalary(conn, 1, 6000);
                Thread.sleep(200);
                updateSalary(conn, 2, 7000);
            } else {
                // deadlock-prone ordering
                updateSalary(conn, 1, 6000);
                Thread.sleep(2000);
                updateSalary(conn, 2, 7000);
            }

            conn.commit();
            System.out.println("Transaction A: COMMIT successful");
        } catch (SQLException e) {
            if (e.getMessage().contains("deadlock")) {
                System.out.println("Transaction A ERROR -> DEADLOCK detected: " + e.getMessage());
            } else {
                System.out.println("Transaction A ERROR -> " + e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void transactionB(boolean ordered) {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);

            if (ordered) {
                // consistent ordering: always lock id=1 first, then id=2
                updateSalary(conn, 1, 6000);
                Thread.sleep(200);
                updateSalary(conn, 2, 7000);
            } else {
                // deadlock-prone ordering
                updateSalary(conn, 2, 6000);
                Thread.sleep(2000);
                updateSalary(conn, 1, 7000);
            }

            conn.commit();
            System.out.println("Transaction B: COMMIT successful");
        } catch (SQLException e) {
            if (e.getMessage().contains("deadlock")) {
                System.out.println("Transaction B ERROR -> DEADLOCK detected: " + e.getMessage());
            } else {
                System.out.println("Transaction B ERROR -> " + e.getMessage());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void updateSalary(Connection conn, int id, int salary) throws SQLException {
        String sql = "UPDATE employees SET salary = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, salary);
            ps.setInt(2, id);
            ps.executeUpdate();
            System.out.println(Thread.currentThread().getName() + ": Updated salary of id=" + id + " to " + salary);
        }
    }

    private static void resetSalaries() {
        try (Connection conn = DBConnection.getConnection()) {
            updateSalary(conn, 1, 5000);
            updateSalary(conn, 2, 5000);
            System.out.println("Database: Salaries reset to 5000");
        } catch (SQLException e) {
            System.out.println("RESET ERROR -> " + e.getMessage());
        }
    }

    private static void printFinalSalaries() {
        try (Connection conn = DBConnection.getConnection()) {
            int salary1 = readSalary(conn, 1);
            int salary2 = readSalary(conn, 2);
            System.out.println("FINAL DATABASE STATE: id=1 salary=" + salary1 + ", id=2 salary=" + salary2);
        } catch (SQLException e) {
            System.out.println("FINAL STATE ERROR -> " + e.getMessage());
        }
    }

    private static int readSalary(Connection conn, int id) throws SQLException {
        String sql = "SELECT salary FROM employees WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("salary");
            }
        }
        return -1;
    }
}