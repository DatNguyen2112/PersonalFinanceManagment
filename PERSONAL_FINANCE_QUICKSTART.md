# Personal Finance Management - Quick Start Guide

## Prerequisites

- Java 17+
- Maven 3.8+
- MySQL 8+
- Kafka (Docker recommended)
- Docker Desktop (for running Kafka easily)

## 🚀 Quick Start

### 1. Start Kafka (using Docker)
```bash
docker-compose up -d
```

Or manually:
```bash
docker run --name kafka -p 9092:9092 \
  -e KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181 \
  apache/kafka:latest
```

### 2. Update Database Connection
Edit `src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/banking_system
spring.datasource.username=root
spring.datasource.password=your_password
```

### 3. Start the Application
```bash
mvn spring-boot:run
```

The application will:
- Auto-create tables via Hibernate DDL
- Seed default spending categories
- Start Kafka consumer for auto-tagging

### 4. Access Swagger UI
Open browser: http://localhost:8080/swagger-ui.html

## 🔑 Authentication Flow

### 1. Register User
```bash
POST /api/v1/auth/register
Content-Type: application/json

{
  "username": "john_doe",
  "email": "john@example.com",
  "password": "SecurePass123!",
  "firstName": "John",
  "lastName": "Doe",
  "phoneNumber": "0123456789"
}
```

Response:
```json
{
  "accessToken": "eyJhbGc...",
  "tokenType": "Bearer",
  "expiresIn": 86400000,
  "user": { ... }
}
```

### 2. Use Token for Finance Endpoints
```bash
Authorization: Bearer eyJhbGc...
```

## 💰 Common Workflows

### Workflow 1: Create Budget and Track Spending

#### Step 1: Get Available Categories
```bash
GET /api/v1/finance/categories
Authorization: Bearer <token>
```

#### Step 2: Create Budget for April 2026
```bash
POST /api/v1/finance/budgets
Content-Type: application/json
Authorization: Bearer <token>

{
  "categoryId": 1,
  "limitAmount": 1000.00,
  "year": 2026,
  "month": 4,
  "alertThreshold": 80
}
```

#### Step 3: Add Expenses
```bash
POST /api/v1/finance/expenses
Content-Type: application/json
Authorization: Bearer <token>

{
  "categoryId": 1,
  "amount": 50.00,
  "type": "EXPENSE",
  "expenseDate": "2026-04-15",
  "note": "Lunch with team"
}
```

Repeat for multiple expenses. Budget automatically updates.

#### Step 4: Check Budget Status
```bash
GET /api/v1/finance/budgets?year=2026&month=4
Authorization: Bearer <token>
```

Response includes:
- `limitAmount`: 1000.00
- `spentAmount`: 150.00 (updated automatically)
- `usagePercent`: 15.0
- `status`: ACTIVE (changes to EXCEEDED if >100%)

### Workflow 2: Create and Track Savings Goal

#### Step 1: Create Goal
```bash
POST /api/v1/finance/goals
Content-Type: application/json
Authorization: Bearer <token>

{
  "name": "Buy new laptop",
  "targetAmount": 2000.00,
  "targetDate": "2026-12-31",
  "description": "Save for a MacBook Pro"
}
```

#### Step 2: Contribute to Goal
```bash
POST /api/v1/finance/goals/1/contribute?amount=500.00
Authorization: Bearer <token>
```

#### Step 3: Monitor Progress
```bash
GET /api/v1/finance/goals
Authorization: Bearer <token>
```

Response shows:
- `progressPercent`: 25.0
- `savedAmount`: 500.00
- `status`: IN_PROGRESS

When target is reached, status becomes COMPLETED and Kafka event is published.

### Workflow 3: Generate Monthly Report

#### Get Report
```bash
GET /api/v1/finance/reports/monthly?year=2026&month=4
Authorization: Bearer <token>
```

Response includes:
```json
{
  "year": 2026,
  "month": 4,
  "totalIncome": 5000.00,
  "totalExpense": 1500.00,
  "netBalance": 3500.00,
  "expenseByCategory": [
    {
      "category": { "name": "Ăn uống", ... },
      "totalAmount": 500.00,
      "percentage": 33.33
    },
    ...
  ],
  "budgetSummary": [ ... ]
}
```

## 📊 Pagination

### Get Expenses with Pagination
```bash
GET /api/v1/finance/expenses?page=0&size=20
Authorization: Bearer <token>
```

Parameters:
- `page`: 0-based page number (default: 0)
- `size`: Items per page (default: 20)

Response includes pagination metadata:
```json
{
  "content": [ ... ],
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 150,
  "totalPages": 8,
  "last": false
}
```

## 🔔 Real-time Updates via Kafka

The system publishes events to Kafka topics:

### Budget Alert Event
When budget usage exceeds threshold:
```json
{
  "type": "BUDGET_ALERT",
  "userId": 1,
  "categoryName": "Ăn uống",
  "usagePercent": 85.0,
  "limitAmount": 1000.00,
  "spentAmount": 850.00,
  "timestamp": "2026-04-20T15:30:00Z"
}
```

Topic: `banking.notifications`

### Goal Completed Event
When savings goal reaches target:
```json
{
  "type": "SAVINGS_GOAL_COMPLETED",
  "userId": 1,
  "goalName": "Buy new laptop",
  "targetAmount": 2000.00,
  "timestamp": "2026-04-20T15:30:00Z"
}
```

Topic: `banking.notifications`

## 🧪 Running Tests

### Run All Tests
```bash
mvn clean test
```

### Run Specific Test Class
```bash
mvn test -Dtest=PersonalFinanceServiceTest
mvn test -Dtest=PersonalFinanceIntegrationTest
```

### Run with Coverage
```bash
mvn clean test jacoco:report
# Report: target/site/jacoco/index.html
```

## 📈 Performance Tips

1. **Indexing**: All queries use indexed columns (user_id, category_id, dates)
2. **Lazy Loading**: Relationships use LAZY fetch to avoid N+1 queries
3. **Caching**: Consider caching default categories
4. **Pagination**: Always paginate expense lists for large datasets

## 🐳 Docker Compose Example

```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: banking_system
    ports:
      - "3306:3306"

  kafka:
    image: apache/kafka:latest
    environment:
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
    ports:
      - "9092:9092"
    depends_on:
      - zookeeper

  zookeeper:
    image: confluentinc/cp-zookeeper:latest
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
```

## 📞 Support

For issues or questions:
1. Check `PERSONAL_FINANCE_IMPL.md` for detailed documentation
2. Review test examples in `PersonalFinanceIntegrationTest.java`
3. Check Swagger UI for API documentation and try endpoints
4. Review logs: `tail -f log.txt`

## 🔐 Security Reminders

1. **JWT Token**: Expires after 24 hours
2. **User Isolation**: Users can only access their own data
3. **Role-Based Access**: CUSTOMER and ADMIN roles required
4. **Sensitive Data**: Passwords encrypted with BCrypt(12)

## 📝 Default Spending Categories

System automatically creates these on first startup:

| Icon | Name | Type | Color |
|------|------|------|-------|
| 🍔 | Ăn uống | EXPENSE | #FF6B6B |
| 🚗 | Di chuyển | EXPENSE | #4ECDC4 |
| 🛍️ | Mua sắm | EXPENSE | #45B7D1 |
| 🎮 | Giải trí | EXPENSE | #96CEB4 |
| 💊 | Y tế | EXPENSE | #FFEAA7 |
| 📚 | Giáo dục | EXPENSE | #DDA0DD |
| 💼 | Lương | INCOME | #98FB98 |
| 📈 | Đầu tư | INCOME | #87CEEB |
| 📦 | Khác | BOTH | #D3D3D3 |

## Next Steps

1. ✅ Start application
2. ✅ Register and login
3. ✅ Create custom categories
4. ✅ Set up monthly budgets
5. ✅ Track daily expenses
6. ✅ Monitor savings goals
7. ✅ View monthly reports

Enjoy managing your finances! 💸📊

