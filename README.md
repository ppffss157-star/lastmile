# logistics-demo :package: Spring Boot 物流配送管理系统

[![Java](https://img.shields.io/badge/Java-17-blue)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-brightgreen)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/license-MIT-lightgrey)](LICENSE)

A production-ready logistics order management REST API built with **Spring Boot 4.0.5** and **Java 17**. Features JWT authentication, Redis distributed caching and locking, AOP logging, WebSocket real-time push, scheduled tasks, and comprehensive unit tests.

---

## :rocket: Tech Stack

| Layer             | Technology                                                     | Purpose                             |
| ----------------- | -------------------------------------------------------------- | ----------------------------------- |
| **Framework**     | Spring Boot 4.0.5                                              | Application container & auto-config |
| **Security**      | Spring Security + JWT (jjwt 0.12.6)                            | Stateless API authentication        |
| **Persistence**   | Spring Data JPA + Hibernate                                    | ORM & repository abstraction        |
| **Database**      | MySQL 8.0 (production) / H2 (test)                             | Relational data store               |
| **Cache**         | Spring Cache + Redis 7 (Alpine)                                | API response caching                |
| **Distributed Lock** | Redis `SETNX` + Lua script                                | Prevent duplicate dispatch           |
| **Real-time**     | WebSocket + STOMP + SockJS                                    | Order status push notifications     |
| **Scheduling**    | Spring `@Scheduled`                                            | Auto-cancel stale orders            |
| **AOP**           | Spring AOP (AspectJ)                                           | Declarative method logging          |
| **Validation**    | `spring-boot-starter-validation` (Jakarta Bean Validation)     | Request DTO validation              |
| **API Docs**      | SpringDoc OpenAPI 2.7.0 (Swagger UI)                           | Interactive API documentation       |
| **Build**         | Maven + spring-boot-maven-plugin                               | Dependency & build management       |
| **Container**     | Docker Compose (MySQL + Redis + app)                           | One-command local deployment        |
| **Testing**       | JUnit 5 + Mockito                                                | Unit testing                        |

---

## :sparkles: Features

| Feature                         | Description                                                                 |
| ------------------------------- | --------------------------------------------------------------------------- |
| :lock: **JWT Authentication**   | Stateless login — obtain a Bearer token, then secure every API call         |
| :dvd: **Redis Cache**           | `@Cacheable` / `@CacheEvict` on order queries (10 min TTL)                 |
| :warning: **Distributed Lock**  | Redis `SETNX` + Lua script — prevents race conditions when assigning orders |
| :mag: **AOP Logging**           | Custom `@LogExecution` annotation — auto-prints args, return value & timing |
| :satellite: **WebSocket Push**  | STOMP over WebSocket — instantly notifies clients of order status changes   |
| :alarm_clock: **Scheduled Tasks** | `autoCancelStaleOrders()` — cancels 30-min-old unassigned orders every minute |
| :x: **Custom Exceptions**       | Business exceptions with precise HTTP status codes (404, 409, 423, etc.)    |
| :white_check_mark: **Unit Tests** | Mockito-based service-layer tests covering core business logic           |

---

## :zap: Quick Start

**Prerequisites:** Docker and Docker Compose installed on your machine.

```bash
# 1. Clone the repository
git clone https://github.com/your-org/logistics-demo.git
cd logistics-demo

# 2. Start all services (MySQL + Redis + App)
docker-compose up -d

# 3. Open Swagger UI to explore the API
open http://localhost:8080/swagger-ui.html
```

**Default credentials:**
- Username: `admin`
- Password: `123456`

Log in via `POST /auth/login`, copy the returned JWT token, and click **Authorize** in Swagger UI to paste it as `Bearer <token>`.

---

## :globe_with_meridians: API Endpoints

### Authentication

| Method | Path         | Description                           |
| ------ | ------------ | ------------------------------------- |
| POST   | `/auth/login` | Authenticate and receive a JWT token |

### Orders

| Method | Path                          | Description                                  |
| ------ | ----------------------------- | -------------------------------------------- |
| POST   | `/orders`                     | Create a new order (status: `CREATED`)        |
| GET    | `/orders`                     | List all orders                               |
| GET    | `/orders/page?page=0&size=5` | Paginated order list                          |
| GET    | `/orders/{id}`                | Get order by ID (cached in Redis)             |
| GET    | `/orders/courier/{courierId}` | Get orders assigned to a specific courier     |
| PUT    | `/orders/{id}/status`         | Transition order status (validates flow)      |
| PUT    | `/orders/{orderId}/assign/{courierId}` | Assign courier with Redis distributed lock |
| PUT    | `/orders/{id}/cancel`         | Cancel an order (frees courier if assigned)   |
| DELETE | `/orders/{id}`                | Delete an order                               |

### Couriers

| Method | Path              | Description                             |
| ------ | ----------------- | --------------------------------------- |
| POST   | `/couriers`       | Register a new courier (status: `AVAILABLE`) |
| GET    | `/couriers`       | List all couriers                       |
| GET    | `/couriers/{id}`  | Get courier by ID                       |
| PUT    | `/couriers/{id}`  | Update courier name / phone             |
| DELETE | `/couriers/{id}`  | Delete a courier                        |

### WebSocket

| Endpoint        | Type          | Description                              |
| --------------- | ------------- | ---------------------------------------- |
| `/ws`           | STOMP + SockJS | Connect for real-time order notifications |
| `/topic/orders` | Subscribe     | Broadcast channel for all order events    |
| `/topic/orders/{orderId}` | Subscribe | Per-order event channel               |

---

## :file_folder: Project Structure

```
src/
└── main/
    ├── java/com/example/logistics/demo/
    │   ├── LogisticsDemoApplication.java          # Spring Boot entry point
    │   ├── aspect/
    │   │   ├── LogExecution.java                  # Custom @LogExecution annotation
    │   │   └── LoggingAspect.java                 # AOP advice (Around) — logs args, return, timing
    │   ├── common/
    │   │   └── Result.java                        # Unified API response wrapper
    │   ├── config/
    │   │   ├── JwtFilter.java                     # OncePerRequestFilter — extracts & validates JWT
    │   │   ├── OpenApiConfig.java                 # Swagger/OpenAPI configuration with Bearer auth
    │   │   ├── RedisConfig.java                   # Redis cache manager (10 min TTL)
    │   │   ├── SecurityConfig.java                # Spring Security filter chain
    │   │   └── WebSocketConfig.java               # STOMP message broker + SockJS fallback
    │   ├── controller/
    │   │   ├── AuthController.java                # POST /auth/login
    │   │   ├── CourierController.java             # CRUD /couriers
    │   │   └── OrderController.java               # CRUD + status transition + assign + cancel /orders
    │   ├── dto/
    │   │   ├── CreateCourierRequest.java          # @NotBlank name, phone
    │   │   ├── CreateOrderRequest.java            # @NotBlank customerName, address, phone
    │   │   ├── LoginRequest.java                  # @NotBlank username, password
    │   │   ├── OrderNotification.java             # WebSocket push payload (Java record)
    │   │   └── UpdateCourierRequest.java          # Optional name, phone
    │   ├── entity/
    │   │   ├── Courier.java                       # JPA entity with @OneToMany orders
    │   │   ├── CourierStatus.java                 # Enum: AVAILABLE, BUSY, OFFLINE
    │   │   ├── Order.java                         # JPA entity with @ManyToOne courier
    │   │   └── OrderStatus.java                   # Enum: CREATED, ASSIGNED, DELIVERING, COMPLETED, CANCELLED
    │   ├── exception/
    │   │   ├── CourierLockedException.java        # 423 LOCKED
    │   │   ├── CourierNotAvailableException.java  # 409 CONFLICT
    │   │   ├── CourierNotFoundException.java      # 404 NOT_FOUND
    │   │   ├── GlobalExceptionHandler.java        # @RestControllerAdvice — centralized handling
    │   │   ├── IllegalStatusTransitionException.java # 400 BAD_REQUEST
    │   │   └── OrderNotFoundException.java        # 404 NOT_FOUND
    │   ├── repository/
    │   │   ├── CourierRepository.java             # JpaRepository<Courier, Long>
    │   │   └── OrderRepository.java               # JpaRepository<Order, Long> + custom query methods
    │   ├── service/
    │   │   ├── CourierService.java                # Business logic for couriers
    │   │   └── OrderService.java                  # Business logic for orders (cache, lock, push, schedule)
    │   └── util/
    │       └── JwtUtil.java                       # JWT generation & validation (HMAC-SHA256)
    └── resources/
        └── application.properties                 # MySQL + Redis + JPA config
```

---

## :test_tube: Running Tests

The project uses **JUnit 5** with **Mockito** for service-layer unit testing. Tests use an in-memory H2 database.

```bash
# Run all tests
./mvnw test

# Run a specific test class
./mvnw test -Dtest=OrderServiceTest
```

**Test coverage includes:**
- `OrderServiceTest` (9 tests) — create, find, throw-on-not-found, assign-courier with distributed lock, lock contention, courier-unavailable, cancel with/without courier
- `CourierServiceTest` (9 tests) — create, phone capture, list-all, find-by-id, throw-on-not-found, update, update-not-found, delete-exists, delete-not-found

---

## :building_construction: Architecture Highlights

### Order Status Flow
```
CREATED → ASSIGNED → DELIVERING → COMPLETED
    ↓                        ↑
    └──── CANCELLED ←────────┘
```
Invalid transitions (e.g., `CREATED → COMPLETED`) throw `IllegalStatusTransitionException` (400).

### Distributed Dispatch Lock
When dispatching a courier, a Redis lock key `lock:courier:{id}` is acquired via `SETNX` with a 10-second TTL. If another request holds the lock, `CourierLockedException` (423) is thrown. The lock is atomically released using a Lua script to prevent accidental deletion of another thread's lock.

### WebSocket Push
`OrderService` uses `SimpMessagingTemplate` to push `OrderNotification` records to `/topic/orders` and `/topic/orders/{orderId}` on every status change, enabling real-time UI updates without polling.

---

## :memo: License

This project is open source and available under the [MIT License](LICENSE).
