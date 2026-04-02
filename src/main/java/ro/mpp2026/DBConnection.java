package ro.mpp2026;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

    // Use TCP port 1433 and SQL Server login
    private static final String URL =
            "jdbc:sqlserver://localhost:1433;databaseName=Antrenament;encrypt=true;trustServerCertificate=true";
    private static final String USER = "myuser";       // your SQL login
    private static final String PASS = "mypassword";   // your SQL password

    // Get a connection using SQL login credentials
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }

    // Simple test main method
    public static void main(String[] args) {
        try (Connection conn = getConnection()) {
            System.out.println("Connection successful!");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}