# CanteenX — Digital Canteen Token Management Platform

**Order Smart. Skip the Queue.**  
*One campus. Multiple canteens. One seamless experience.*

CanteenX is an enterprise-grade digital dining and queue management platform built for college campuses. It eliminates physical ordering queues at campus food counters (such as **REC Cafe** and **REC Hut**) by providing instant digital token generation, single-canteen cart isolation, concurrency-safe token sequences with database row-level locking, atomic token redemption, real-time sales analytics, and automated CSV reporting.

The platform provides two seamless operational interfaces:
1. **Primary Web Application**: A modern, responsive single-page browser interface running at `http://localhost:8080`.
2. **Desktop Swing Application**: A native Java Swing interface for counter and administrative environments (`edu.canteenx.Main`).

---

## Architecture Overview

```
+-----------------------------------------------------------------------------------+
|                                  USER CLIENTS                                     |
|   Modern Browser (http://localhost:8080)   |   Native Java Swing Desktop Frame    |
+-----------------------------------------------------------------------------------+
                                        | (HTTP/REST or Direct Service Invocations)
+-----------------------------------------------------------------------------------+
|                        SPRING BOOT 3.x APPLICATION SERVER                         |
|  Controllers: Health, Auth, Canteen, Menu, Order, Token, Admin                    |
|  Security: BCrypt Hashing, Bearer Token Validation, Role & Canteen Isolation      |
|  Services: CanteenService, AuditService, PaymentStrategy (Polymorphism)          |
+-----------------------------------------------------------------------------------+
                                        | (ACID JDBC Transactions & Row Locking)
+-----------------------------------------------------------------------------------+
|                              MYSQL 8.0 INNODB DATABASE                            |
|  Tables: canteens, users, menu_items, token_counters, orders, order_items,        |
|          redemption_logs, payment_transactions, audit_logs                        |
+-----------------------------------------------------------------------------------+
```

---

## Technical Stack

- **Platform & Language:** Java 17 LTS
- **Backend Framework:** Spring Boot 3.3.4 (Spring Web, Spring Validation, Spring Test)
- **Database:** MySQL 8.0 Community Server (InnoDB engine, strict ACID transactions)
- **Persistence & Connectivity:** MySQL Connector/J 9.1.0, JDBC with PreparedStatements
- **Security:** BCrypt password hashing (work factor 12), tokenized session management
- **Frontend:** Vanilla HTML5, CSS3 Custom Properties Design System, ES6+ JavaScript
- **Build & Dependency Tool:** Apache Maven 3.9+
- **Desktop UI:** Java Swing (retained and integrated with common business services)

---

## Quick Start & Local Setup

### 1. Prerequisites
- **Java 17 LTS** (`java -version`)
- **Apache Maven 3.9+** (`mvn -version`)
- **MySQL 8.0+** running on `localhost:3306`

### 2. Database Provisioning

Log in to MySQL and initialize the database schema and seed data:

```bash
# Windows Command Prompt / PowerShell
mysql -u root -p < database-friend-work/schema.sql
mysql -u root -p canteenx < database-friend-work/seed.sql
```

*(Note: In PowerShell, use `cmd /c "mysql -u root -p < database-friend-work\schema.sql"` or specify credentials).*

### 3. Environment Variables (Optional)
If your local MySQL uses custom credentials, set the following environment variables:

```powershell
# PowerShell
$env:CANTEENX_DB_URL = "jdbc:mysql://localhost:3306/canteenx?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:CANTEENX_DB_USER = "root"
$env:CANTEENX_DB_PASSWORD = "your-local-password"
```

### 4. Running the Web Application (Primary Interface)

Start the Spring Boot web application:

```bash
mvn spring-boot:run
```

Once the console displays `Started CanteenXWebApplication`, open your browser at:

👉 **`http://localhost:8080`**

### 5. Running the Desktop Swing Application (Alternative Interface)

To launch the native desktop Swing UI:

```bash
mvn exec:java
```

Or execute the provided batch script:

```bat
run.bat
```

---

## Demo Accounts & Test Credentials

The database contains pre-configured demonstration accounts with BCrypt-hashed passwords. On first startup, demo accounts can be initialized or verified by clicking **"Verify / Seed Demo Accounts"** on the landing page or calling `POST /api/auth/seed-demo`:

| Role | Workspace | Email | Password | Access Scope |
|---|---|---|---|---|
| **Student** | Student Food Ordering | `student@canteenx.local` | `student123` | Menu browsing, cart, order placement, personal order history |
| **Shopkeeper** | REC Cafe Counter | `cafe@canteenx.local` | `cafe123` | REC Cafe token inspection & atomic redemption |
| **Shopkeeper** | REC Hut Counter | `hut@canteenx.local` | `hut123` | REC Hut token inspection & atomic redemption |
| **Admin** | Administrator Suite | `admin@canteenx.local` | `admin123` | Campus revenue KPIs, menu CRUD, user list, CSV sales report |

---

## Key Academic & Architectural Features

### 1. Concurrency Protection & Row Locking
- When an order is placed, `token_counters` is queried using `SELECT `last_value` FROM token_counters WHERE canteen_id=? FOR UPDATE`.
- This acquires an exclusive row-level write lock in MySQL InnoDB. Simultaneous order creation requests are serialized, guaranteeing strictly sequential, non-colliding token numbers (e.g. `CAF-0000001`, `CAF-0000002`).
- Covered by automated multi-threaded tests in `ConcurrencyProtectionTest.java`.

### 2. Single-Canteen Cart Policy
- A student cannot mix dishes from different canteens in a single order because token counters and kitchen preparation are canteen-specific.
- Both the browser UI and server-side validation enforce this rule. If a student attempts to add an item from another canteen, a confirmation modal prompts them to clear the current cart before switching.

### 3. Server-Side Authoritative Pricing
- The client interface only transmits dish IDs and quantities.
- Authoritative dish prices and availability flags are queried from MySQL inside the order creation transaction. Client-side price tampering is completely prevented.

### 4. Atomic Token Handover & Anti-Duplicate Redemption
- Token redemption executes a conditional atomic update:
  ```sql
  UPDATE orders 
  SET order_status='REDEEMED', redeemed_at=CURRENT_TIMESTAMP 
  WHERE token_code=? AND canteen_id=? AND order_status='PLACED' AND payment_status='DEMO_PAID';
  ```
- If a token was already redeemed or belongs to a different canteen, 0 rows are updated and the transaction rolls back safely.
- A unique foreign key constraint on `redemption_logs(order_id)` provides defense-in-depth against duplicate handover.

### 5. OOP Polymorphism: Payment Strategies
- Demonstrates the Strategy design pattern for academic evaluation:
  - `PaymentStrategy` interface with `CampusUpiPaymentStrategy`, `StudentSmartCardPaymentStrategy`, and `CashPaymentStrategy`.
  - Dynamically resolved and executed at checkout. All payments are explicitly simulated demonstration records (`DEMO_PAID`).

---

## REST API Reference

| Method | Endpoint | Authorization | Description |
|---|---|---|---|
| `GET` | `/api/health` | Public | Real-time database connectivity and backend health check |
| `POST` | `/api/auth/login` | Public | Authenticates credentials and returns a session bearer token |
| `POST` | `/api/auth/register` | Public | Self-registration for students with role assignment |
| `GET` | `/api/auth/me` | Authenticated | Validates session token and returns current user details |
| `POST` | `/api/auth/logout` | Authenticated | Terminates active session token |
| `POST` | `/api/auth/seed-demo` | Public | Idempotently creates or verifies standard demo accounts |
| `GET` | `/api/canteens` | Public | Discovers all active campus food outlets |
| `GET` | `/api/canteens/{id}/menu` | Public | Retrieves active menu items for a specific canteen |
| `GET` | `/api/menu` | Public | Retrieves all menu items across all campus outlets |
| `POST` | `/api/orders` | Student | Places an order, locks token sequence, snapshots items, returns token |
| `GET` | `/api/orders/my` | Student | Retrieves personal order history for the authenticated student |
| `GET` | `/api/orders/{id}` | Student/Staff | Retrieves order receipt with student ownership verification |
| `GET` | `/api/shopkeeper/tokens/{code}` | Shopkeeper | Verifies token eligibility, line items, and counter assignment |
| `POST` | `/api/shopkeeper/tokens/{code}/redeem` | Shopkeeper | Performs conditional atomic redemption and logs handover |
| `GET` | `/api/admin/dashboard` | Admin | Returns campus revenue, order volume, and top dishes |
| `POST` | `/api/admin/menu` | Admin | Creates a new dish in a specified canteen |
| `PUT` | `/api/admin/menu/{id}` | Admin | Modifies dish details, price, category, and availability |
| `DELETE` | `/api/admin/menu/{id}` | Admin | Soft-archives dish to preserve historical transaction receipts |
| `GET` | `/api/admin/reports/sales.csv` | Admin | Downloads itemized sales report as a formatted CSV file |
| `GET` | `/api/admin/audit-logs` | Admin | Retrieves live system security and transaction audit trail |
| `GET` | `/api/admin/users` | Admin | Retrieves active user accounts, roles, and canteen assignments |

---

## Automated Test Execution

Run the complete test suite (Unit, Integration, Concurrency, and Security tests):

```bash
mvn test
```

### Test Suite Summary:
- **`CanteenServiceUnitTest`**: Validates BCrypt hashing, token formatting, OOP polymorphism, and role checks.
- **`CanteenServiceDatabaseTest`**: Validates live MySQL CRUD, order placement, token allocation, and atomic redemption.
- **`ConcurrencyProtectionTest`**: Verifies thread-safe token generation under 10 simultaneous orders and race-condition redemption under 5 simultaneous handover attempts.
- **`SecurityAndAuthorizationTest`**: Verifies student privacy (cannot view others' orders), cross-canteen isolation, and role authorization.
- **`WebControllerIntegrationTest`**: Verifies Spring Boot REST MVC endpoints using MockMvc.

---

## Academic Deliverables & Diagrams

Full academic documentation, ER diagrams, class diagrams, and sequence diagrams are available in the `docs/` folder:
- [docs/report.md](docs/report.md) — Comprehensive Academic Project Report
- [docs/FEATURE_AUDIT.md](docs/FEATURE_AUDIT.md) — Project Audit and Technical Gap Analysis
- [docs/testing.md](docs/testing.md) — Test Verification Checklist & Results
- [docs/er-diagram.md](docs/er-diagram.md) — Relational Entity-Relationship Diagram
- [docs/class-diagram.md](docs/class-diagram.md) — Object-Oriented Class Hierarchy Diagram
- [docs/viva.md](docs/viva.md) — Academic Viva Examination Q&A
