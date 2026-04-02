# database-transactions-demo

A Java lab project demonstrating database transaction concepts using JDBC, including concurrency anomalies, isolation levels, and batch insert performance comparison.

## Topics Covered

- **Concurrency problems** — Dirty Read, Non-Repeatable Read, Phantom Read, Lost Update
- **Transaction isolation levels** — READ UNCOMMITTED → SERIALIZABLE
- **Deadlock detection and handling**
- **Batch insert performance** — three strategies compared across 5 000 rows

## Project Structure

```
src/
└── main/java/ro/mpp2026/
    ├── DBConnection.java
    └── demos/
        ├── InsertPerformanceDemo.java      # Batch insert comparison
        ├── DirtyReadDemo.java              # Dirty read demonstration
        ├── NonRepeatableReadDemo.java      # Non-repeatable read demonstration
        ├── PhantomReadDemo.java            # Phantom read demonstration
        ├── LostUpdateDemo.java             # Lost update demonstration
        └── DeadlockDemo.java              # Deadlock scenario + resolution
```

## Batch Insert Performance Results

Inserting **5 000 rows**, each approach run **3 times**:

| Approach | Run 1 (ms) | Run 2 (ms) | Run 3 (ms) | Avg (ms) |
|---|---|---|---|---|
| 1. Auto-commit | 7 169 | 13 873 | 7 555 | **9 532** |
| 2. Batch-commit (per 100 rows) | 4 343 | 4 933 | 3 805 | **4 360** |
| 3. Single-TX + batch | 1 677 | 1 399 | 1 025 | **1 367** ★ |

- Approach 2 vs 1: **2.19×** faster
- Approach 3 vs 1: **6.97×** faster
- Approach 3 vs 2: **3.19×** faster

## Requirements

- Java 17+
- PostgreSQL (or MySQL)
- JDBC driver on classpath
- Gradle

## Setup

1. Clone the repo and open in your IDE.
2. Create the database schema:

```sql
CREATE TABLE employees (
    id            SERIAL PRIMARY KEY,
    name          VARCHAR(100),
    salary        NUMERIC(10, 2),
    department_id INT
);
```

3. Configure your connection in `DBConnection.java`:

```java
private static final String URL  = "jdbc:postgresql://localhost:5432/your_db";
private static final String USER = "your_user";
private static final String PASS = "your_password";
```

4. Run any demo via Gradle:

```bash
./gradlew :ro.mpp2026.demos.InsertPerformanceDemo.main()
```

## Tech Stack

![Java](https://img.shields.io/badge/Java-17+-orange?logo=openjdk)
![JDBC](https://img.shields.io/badge/JDBC-pure-blue)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-336791?logo=postgresql)
![Gradle](https://img.shields.io/badge/Gradle-9-02303A?logo=gradle)
