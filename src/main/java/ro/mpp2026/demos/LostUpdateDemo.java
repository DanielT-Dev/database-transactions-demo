package ro.mpp2026.demos;

import ro.mpp2026.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;

public class LostUpdateDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== LOST UPDATE DEMO ===");

        System.out.println("Run 1: READ COMMITTED (lost update may occur)");
        runDemo(Connection.TRANSACTION_READ_COMMITTED);

        System.out.println("\nRun 2: SERIALIZABLE (lost update prevented)");
        runDemo(Connection.TRANSACTION_SERIALIZABLE);
    }

    private static void runDemo(int isolationLevel) throws InterruptedException {
        resetTestData();
        CountDownLatch latch = new CountDownLatch(1);

        Thread transactionA = new Thread(() -> transactionA(latch, isolationLevel), "T1");
        Thread transactionB = new Thread(() -> transactionB(latch, isolationLevel), "T2");

        transactionA.start();
        Thread.sleep(500); // ensure T1 reads first
        transactionB.start();

        transactionA.join();
        transactionB.join();

        printFinalSalary();
    }

    private static void transactionA(CountDownLatch latch, int isolationLevel) {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            conn.setTransactionIsolation(isolationLevel);

            int salary = readSalary(conn, 1);
            int newSalary = salary + 1000;
            latch.countDown();
            Thread.sleep(2000); // simulate delay before writing

            updateSalary(conn, 1, newSalary);
            conn.commit();
            System.out.println("Transaction A: Updated salary to " + newSalary);
        } catch (Exception e) {
            System.out.println("Transaction A ERROR -> " + e.getMessage());
        }
    }

    private static void transactionB(CountDownLatch latch, int isolationLevel) {
        try (Connection conn = DBConnection.getConnection()) {
            latch.await();
            conn.setAutoCommit(false);
            conn.setTransactionIsolation(isolationLevel);

            int salary = readSalary(conn, 1);
            int newSalary = salary + 500;
            updateSalary(conn, 1, newSalary);
            conn.commit();
            System.out.println("Transaction B: Updated salary to " + newSalary);
        } catch (Exception e) {
            System.out.println("Transaction B ERROR -> " + e.getMessage());
        }
    }

    private static void resetTestData() {
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE employees SET salary = 5000 WHERE id = 1");
            System.out.println("Database: Salary reset to 5000");
        } catch (Exception e) {
            System.out.println("Database RESET ERROR -> " + e.getMessage());
        }
    }

    private static int readSalary(Connection conn, int id) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT salary FROM employees WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("salary");
            }
        }
        return 0;
    }

    private static void updateSalary(Connection conn, int id, int salary) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE employees SET salary = ? WHERE id = ?")) {
            ps.setInt(1, salary);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    private static void printFinalSalary() {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT salary FROM employees WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                System.out.println("FINAL DATABASE STATE: salary = " + rs.getInt("salary"));
            }
        } catch (Exception e) {
            System.out.println("FINAL STATE ERROR -> " + e.getMessage());
        }
    }
}