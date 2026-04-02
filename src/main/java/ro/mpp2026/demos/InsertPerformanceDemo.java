package ro.mpp2026.demos;

import ro.mpp2026.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Demonstrates and compares three INSERT strategies for bulk data insertion:
 *  1. Auto-commit  – one transaction (and one disk flush) per row
 *  2. Batch-commit – manual transaction flushed every COMMIT_BATCH rows
 *  3. Single-transaction batch – JDBC addBatch / executeBatch inside one transaction
 *
 * Each strategy is run NUM_RUNS times; average elapsed time is reported.
 */
public class InsertPerformanceDemo {

    // ── Tuning constants ────────────────────────────────────────────────────
    private static final int TOTAL_INSERTS  = 5_000;
    private static final int BATCH_SIZE     = 50;   // rows per executeBatch() call
    private static final int COMMIT_BATCH   = 100;  // rows per commit() call
    private static final int NUM_RUNS       = 3;
    // ────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        System.out.println("+----------------------------------------------+");
        System.out.println("|      INSERT PERFORMANCE COMPARISON DEMO      |");
        System.out.println("+----------------------------------------------+");
        System.out.printf("  Total inserts : %,d%n", TOTAL_INSERTS);
        System.out.printf("  Runs per test : %d%n%n", NUM_RUNS);

        long[] times1 = benchmarkApproach("1. Auto-commit",             InsertPerformanceDemo::runAutoCommit);
        long[] times2 = benchmarkApproach("2. Batch-commit (per 100)", InsertPerformanceDemo::runBatchCommit);
        long[] times3 = benchmarkApproach("3. Single-TX + batch",       InsertPerformanceDemo::runSingleTransactionBatch);

        printSummary(times1, times2, times3);
    }

    // ── Benchmarking harness ─────────────────────────────────────────────────

    private static long[] benchmarkApproach(String label, Runnable approach) {
        System.out.println("+- " + label);
        long[] times = new long[NUM_RUNS];
        for (int run = 0; run < NUM_RUNS; run++) {
            System.out.printf("│  Run %d/%d ... ", run + 1, NUM_RUNS);
            long start = System.currentTimeMillis();
            approach.run();
            times[run] = System.currentTimeMillis() - start;
            System.out.printf("%,6d ms%n", times[run]);
        }
        System.out.printf("+- Average: %,d ms%n", average(times));
        return times;
    }

    private static long average(long[] values) {
        long sum = 0;
        for (long v : values) sum += v;
        return sum / values.length;
    }

    // ── Approach 1: Auto-commit ──────────────────────────────────────────────
    /**
     * Default JDBC behaviour: every executeUpdate() is wrapped in its own
     * implicit transaction and immediately committed (and flushed to disk).
     * This incurs the full transaction overhead TOTAL_INSERTS times.
     */
    private static void runAutoCommit() {
        final String DELETE = "DELETE FROM employees WHERE name LIKE 'EmployeeAC%'";
        final String INSERT = "INSERT INTO employees (name, salary, department_id) VALUES (?, 5000, 1)";

        try (Connection conn = DBConnection.getConnection();
             Statement  stmt = conn.createStatement()) {

            stmt.executeUpdate(DELETE);

            try (PreparedStatement ps = conn.prepareStatement(INSERT)) {
                for (int i = 1; i <= TOTAL_INSERTS; i++) {
                    ps.setString(1, "EmployeeAC" + i);
                    ps.executeUpdate();          // implicit commit after each row
                }
            }

        } catch (SQLException e) {
            System.err.println("  [ERROR] Auto-commit approach: " + e.getMessage());
        }
    }

    // ── Approach 2: Batch-commit (commit every COMMIT_BATCH rows) ────────────
    /**
     * Auto-commit is disabled. Rows are inserted one by one with executeUpdate(),
     * but the transaction is committed (and flushed) only every COMMIT_BATCH rows.
     * This reduces flush overhead from TOTAL_INSERTS to TOTAL_INSERTS/COMMIT_BATCH.
     */
    private static void runBatchCommit() {
        final String DELETE = "DELETE FROM employees WHERE name LIKE 'EmployeeBC%'";
        final String INSERT = "INSERT INTO employees (name, salary, department_id) VALUES (?, 5000, 1)";

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);

            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(DELETE);
                conn.commit();
            }

            try (PreparedStatement ps = conn.prepareStatement(INSERT)) {
                for (int i = 1; i <= TOTAL_INSERTS; i++) {
                    ps.setString(1, "EmployeeBC" + i);
                    ps.executeUpdate();

                    if (i % COMMIT_BATCH == 0) {
                        conn.commit();          // flush every COMMIT_BATCH rows
                    }
                }
                conn.commit();                  // flush any remaining rows

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }

        } catch (SQLException e) {
            System.err.println("  [ERROR] Batch-commit approach: " + e.getMessage());
        }
    }

    // ── Approach 3: Single transaction + JDBC batch API ──────────────────────
    /**
     * All rows are added to an in-memory JDBC batch with addBatch().
     * executeBatch() sends BATCH_SIZE rows to the server in a single network
     * round-trip. A single commit() at the end writes everything atomically.
     * This minimises both network round-trips and transaction overhead.
     */
    private static void runSingleTransactionBatch() {
        final String DELETE = "DELETE FROM employees WHERE name LIKE 'EmployeeST%'";
        final String INSERT = "INSERT INTO employees (name, salary, department_id) VALUES (?, 5000, 1)";

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);

            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(DELETE);
                conn.commit();
            }

            try (PreparedStatement ps = conn.prepareStatement(INSERT)) {
                for (int i = 1; i <= TOTAL_INSERTS; i++) {
                    ps.setString(1, "EmployeeST" + i);
                    ps.addBatch();                      // buffer locally

                    if (i % BATCH_SIZE == 0) {
                        ps.executeBatch();              // send chunk to server
                    }
                }
                ps.executeBatch();                      // send any remaining rows
                conn.commit();                          // single disk flush

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }

        } catch (SQLException e) {
            System.err.println("  [ERROR] Single-TX batch approach: " + e.getMessage());
        }
    }

    // ── Results summary ───────────────────────────────────────────────────────

    private static void printSummary(long[] t1, long[] t2, long[] t3) {
        long avg1 = average(t1), avg2 = average(t2), avg3 = average(t3);
        long best = Math.min(avg1, Math.min(avg2, avg3));

        System.out.println("+==================================================================+");
        System.out.println("|                    RESULTS SUMMARY                              |");
        System.out.println("+===============================+=========+=========+=========+====+");
        System.out.printf( "| %-29s | %7s | %7s | %7s | %s%n",
                "Approach", "Run 1", "Run 2", "Run 3", "Avg (ms) |");
        System.out.println("+===============================+=========+=========+=========+====+");

        printRow("1. Auto-commit",            t1, avg1, best);
        printRow("2. Batch-commit (per 100)", t2, avg2, best);
        printRow("3. Single-TX + batch",      t3, avg3, best);

        System.out.println("+===============================+=========+=========+=========+====+");

        System.out.println("\n  Speedup  2 vs 1: " + String.format("%.2fx", (double) avg1 / avg2));
        System.out.println("  Speedup  3 vs 1: " + String.format("%.2fx", (double) avg1 / avg3));
        System.out.println("  Speedup  3 vs 2: " + String.format("%.2fx", (double) avg2 / avg3));
        System.out.println("\n  * Fastest approach: " + (avg3 == best ? "Single-TX + batch"
                : avg2 == best ? "Batch-commit" : "Auto-commit"));
    }

    private static void printRow(String label, long[] times, long avg, long best) {
        String marker = (avg == best) ? "*" : " ";
        System.out.printf("| %-29s | %7d | %7d | %7d |%s%7d |%n",
                label, times[0], times[1], times[2], marker, avg);
    }
}