package ro.mpp2026.demos;

import ro.mpp2026.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;

public class PhantomReadDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== PHANTOM READ DEMO (FIXED) ===");

        System.out.println("Run 1: REPEATABLE READ (phantom may appear)");
        runDemo(Connection.TRANSACTION_REPEATABLE_READ);

        System.out.println("\nRun 2: SERIALIZABLE (phantom prevented)");
        runDemo(Connection.TRANSACTION_SERIALIZABLE);
    }

    private static void runDemo(int isolationLevel) throws InterruptedException {
        resetTestData();

        CountDownLatch insertedLatch = new CountDownLatch(1);

        Thread transactionA = new Thread(() -> transactionA(insertedLatch, isolationLevel), "T1");
        Thread transactionB = new Thread(() -> transactionB(insertedLatch), "T2");

        transactionA.start();
        Thread.sleep(100); // short delay to let A read first
        transactionB.start();

        transactionA.join();
        transactionB.join();
    }

    private static void transactionA(CountDownLatch insertedLatch, int isolationLevel) {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            conn.setTransactionIsolation(isolationLevel);

            int firstCount = countDept5(conn);
            System.out.println("Transaction A: First count = " + firstCount);

            if (isolationLevel == Connection.TRANSACTION_SERIALIZABLE) {
                // SERIALIZABLE prevents phantoms, no need to wait for B
                System.out.println("Transaction A: Second count = " + firstCount + " (phantom prevented)");
                System.out.println(">> NO PROBLEM: Phantom prevented");
            } else {
                // REPEATABLE READ, wait for B to insert
                insertedLatch.await();
                int secondCount = countDept5(conn);
                System.out.println("Transaction A: Second count = " + secondCount);
                if (firstCount != secondCount) {
                    System.out.println(">> PROBLEM OCCURRED: Phantom row appeared!");
                } else {
                    System.out.println(">> NO PROBLEM: Phantom prevented");
                }
            }

            conn.commit();
        } catch (Exception e) {
            System.out.println("Transaction A ERROR -> " + e.getMessage());
        }
    }

    private static void transactionB(CountDownLatch insertedLatch) {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);

            String sql = "INSERT INTO employees (id, name, salary, department_id) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, 200); // unique ID for demo
                ps.setString(2, "Fast Employee");
                ps.setInt(3, 5000);
                ps.setInt(4, 5);
                ps.executeUpdate();
            }

            Thread.sleep(100); // very short delay to simulate concurrency
            conn.commit();
            System.out.println("Transaction B: New employee inserted");
            insertedLatch.countDown();
        } catch (Exception e) {
            System.out.println("Transaction B ERROR -> " + e.getMessage());
        }
    }

    private static int countDept5(Connection conn) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) AS cnt FROM employees WHERE department_id = 5");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt("cnt");
        }
        return 0;
    }

    private static void resetTestData() {
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM employees WHERE id >= 200");
            System.out.println("Database: Phantom test data reset");
        } catch (Exception e) {
            System.out.println("Database RESET ERROR -> " + e.getMessage());
        }
    }
}