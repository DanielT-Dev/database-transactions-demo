package ro.mpp2026.demos;

import ro.mpp2026.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;

public class DirtyReadDemo {

    public static void main(String[] args) {
        System.out.println("=== DIRTY READ DEMO ===");
        System.out.println("Run 1: READ UNCOMMITTED (problem should appear)");
        runDemo(Connection.TRANSACTION_READ_UNCOMMITTED);

        System.out.println();
        System.out.println("Run 2: READ COMMITTED (problem should be prevented)");
        runDemo(Connection.TRANSACTION_READ_COMMITTED);
    }

    private static void runDemo(int isolationLevel) {
        resetData();

        CountDownLatch updateDone = new CountDownLatch(1);

        Thread t1 = new Thread(() -> transactionA(updateDone), "T1");
        Thread t2 = new Thread(() -> transactionB(updateDone, isolationLevel), "T2");

        try {
            t1.start();
            Thread.sleep(500);
            t2.start();

            t1.join();
            t2.join();

            printFinalSalary();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Main thread interrupted.");
        }
    }

    private static void transactionA(CountDownLatch updateDone) {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            System.out.println("T1: BEGIN");

            updateSalary(conn, 10000);
            System.out.println("T1: Updated salary to 10000 (NOT COMMITTED)");
            updateDone.countDown();

            Thread.sleep(5000);

            conn.rollback();
            System.out.println("T1: ROLLBACK");
        } catch (Exception e) {
            System.out.println("T1: ERROR -> " + e.getMessage());
        }
    }

    private static void transactionB(CountDownLatch updateDone, int isolationLevel) {
        try (Connection conn = DBConnection.getConnection()) {
            updateDone.await();

            conn.setTransactionIsolation(isolationLevel);
            conn.setAutoCommit(false);

            String isoName = isolationName(isolationLevel);
            System.out.println("T2: BEGIN (" + isoName + ")");

            int salary = readSalary(conn);
            System.out.println("T2: Read salary = " + salary);

            conn.commit();
            System.out.println("T2: COMMIT");
        } catch (Exception e) {
            System.out.println("T2: ERROR -> " + e.getMessage());
        }
    }

    private static void resetData() {
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE employees SET salary = 5000 WHERE id = 1");
            System.out.println("DB: Reset salary to 5000");
        } catch (Exception e) {
            System.out.println("DB: RESET ERROR -> " + e.getMessage());
        }
    }

    private static void updateSalary(Connection conn, int salary) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE employees SET salary = ? WHERE id = 1")) {
            ps.setInt(1, salary);
            ps.executeUpdate();
        }
    }

    private static int readSalary(Connection conn) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT salary FROM employees WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("salary");
            }
            throw new IllegalStateException("Employee with id = 1 not found.");
        }
    }

    private static void printFinalSalary() {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT salary FROM employees WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                System.out.println("FINAL DB STATE: salary = " + rs.getInt("salary"));
            }
        } catch (Exception e) {
            System.out.println("FINAL STATE ERROR -> " + e.getMessage());
        }
    }

    private static String isolationName(int level) {
        return switch (level) {
            case Connection.TRANSACTION_READ_UNCOMMITTED -> "READ UNCOMMITTED";
            case Connection.TRANSACTION_READ_COMMITTED -> "READ COMMITTED";
            default -> "UNKNOWN";
        };
    }
}