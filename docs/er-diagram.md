# CanteenX ER diagram

```mermaid
erDiagram
  CANTEENS ||--o{ USERS : assigns
  CANTEENS ||--o{ MENU_ITEMS : offers
  CANTEENS ||--o{ ORDERS : receives
  CANTEENS ||--|| TOKEN_COUNTERS : sequences
  USERS ||--o{ ORDERS : places
  ORDERS ||--|{ ORDER_ITEMS : contains
  MENU_ITEMS o|--o{ ORDER_ITEMS : references
  ORDERS ||--o| REDEMPTION_LOGS : redeemed_once
  USERS ||--o{ REDEMPTION_LOGS : redeems
  CANTEENS {
    bigint canteen_id PK
    varchar name UK
    char token_prefix UK
    boolean is_active
  }
  USERS {
    bigint user_id PK
    varchar email UK
    varchar password_hash
    enum role
    bigint canteen_id FK
  }
  MENU_ITEMS {
    bigint item_id PK
    bigint canteen_id FK
    decimal price
    boolean is_available
    boolean is_deleted
  }
  ORDERS {
    bigint order_id PK
    bigint user_id FK
    bigint canteen_id FK
    varchar token_code UK
    decimal total_amount
    enum order_status
  }
  ORDER_ITEMS {
    bigint order_item_id PK
    bigint order_id FK
    bigint item_id FK
    varchar item_name_snapshot
    decimal unit_price
    int quantity
  }
  TOKEN_COUNTERS {
    bigint canteen_id PK_FK
    bigint last_value
  }
  REDEMPTION_LOGS {
    bigint log_id PK
    bigint order_id FK_UK
    bigint redeemed_by FK
  }
```

`ORDER_ITEMS.item_id` is nullable to allow historical snapshots to survive a physical menu item removal. Application CRUD uses soft deletion so normal operations retain the reference.
