# Viva questions and short answers

1. **What is the project?** CanteenX is a Java Swing ordering and token management application backed by MySQL through JDBC.
2. **Where is encapsulation used?** `User` keeps identity fields private and exposes getters; role-specific state is controlled through constructors/getters.
3. **Explain inheritance.** `Student`, `Shopkeeper`, and `Admin` extend the abstract `User` base class.
4. **Where is polymorphism?** The roles override `roleName()` and `dashboardMessage()`; callers can work through the common `User` contract.
5. **What is abstraction here?** Swing invokes service methods without knowing how SQL, locking, or JDBC resources are implemented.
6. **Why use `PreparedStatement`?** It binds user data separately from SQL syntax, reducing injection risk and handling values safely.
7. **Why use `DECIMAL` for money?** Decimal fixed-point values avoid the rounding behavior of binary floating-point currency calculations.
8. **Why store snapshot prices?** A later menu price change must not change what an earlier order charged.
9. **How are tokens unique?** Each outlet has a row in `token_counters`; order creation locks it, increments it, and builds a prefix plus zero-padded sequence in one transaction.
10. **What does `SELECT ... FOR UPDATE` do?** It locks the counter row until commit/rollback, serializing concurrent allocations for that canteen.
11. **How is double redemption prevented?** A conditional update transitions only a `PLACED` order; the unique order key in redemption logs also limits it to one log.
12. **Why a transaction for order creation?** Counter allocation, order, and lines must succeed or roll back together to avoid partial records.
13. **What is a foreign key?** A relational constraint requiring a referenced record to exist, such as an order's user and canteen.
14. **What is soft deletion?** Menu items are archived with flags so old order history remains meaningful.
15. **How is SQL aggregate reporting used?** `COUNT`, `SUM`, `GROUP BY`, and joins compute campus/order and best-seller summaries.
16. **Are payments real?** No. `DEMO_PAID` and the displayed methods are explicitly demonstration records; there is no payment gateway.
17. **Where is authorization enforced?** In service methods, which check the role and, for shopkeepers, the assigned canteen.
18. **How are passwords stored?** BCrypt hashes are stored, not the original password.
19. **What is JDBC?** Java Database Connectivity, the standard Java API used here to issue SQL and process results.
20. **What would you improve for production?** Managed account onboarding/reset, robust session policy, payment integration, centralized server deployment, audit/monitoring, and automated integration tests.
