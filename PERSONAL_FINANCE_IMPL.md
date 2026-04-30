# Personal Finance Management - Implementation Guide

## Overview

This document describes the complete implementation of the **Personal Finance Management** feature for the Banking System. This module extends the existing Spring Boot Banking application with capabilities for managing budgets, tracking expenses, setting savings goals, and generating financial reports.

## 📁 File Structure

### Entities (New)
- `Entity/SpendingCategory.java` - Spending categories (system-default and user-custom)
- `Entity/Budget.java` - Monthly/yearly budgets with alerts
- `Entity/Expense.java` - Expense records with auto-tagging
- `Entity/SavingsGoal.java` - Savings goals with progress tracking

### Enums (New)
- `Entity/CategoryType.java` - EXPENSE, INCOME, BOTH
- `Entity/BudgetStatus.java` - ACTIVE, EXCEEDED, COMPLETED
- `Entity/ExpenseType.java` - EXPENSE, INCOME
- `Entity/GoalStatus.java` - IN_PROGRESS, COMPLETED, CANCELLED

### Repositories (New)
- `Repositories/SpendingCategoryRepository.java`
- `Repositories/BudgetRepository.java`
- `Repositories/ExpenseRepository.java`
- `Repositories/SavingsGoalRepository.java`
- `Repositories/AccountRepository.java`
- `Repositories/TransactionRepository.java`

### Services (New)
- `Services/PersonalFinanceService.java` - Core business logic
- `Services/PersonalFinanceConsumer.java` - Kafka consumer for auto-tagging

### Controller (New)
- `Controller/PersonalFinanceController.java` - REST API endpoints

### DTOs (Updated)
- `DTO/BankingDTO.java` - Added 10 static inner classes for requests/responses

### Configuration (New)
- `Config/FinanceDataSeeder.java` - Initializes default spending categories

### Tests (New)
- `Services/PersonalFinanceServiceTest.java` - Unit tests
- `API/PersonalFinanceIntegrationTest.java` - Integration tests

## 🚀 API Endpoints

### Categories
```
POST   /api/v1/finance/categories           - Create custom category
GET    /api/v1/finance/categories           - List all categories (system + user)
```

### Budgets
```
POST   /api/v1/finance/budgets              - Create budget
GET    /api/v1/finance/budgets?year=Y&month=M - Get budgets for period
```

### Expenses
```
POST   /api/v1/finance/expenses             - Add expense
GET    /api/v1/finance/expenses?page=0&size=20 - List expenses (paginated)
```

### Savings Goals
```
POST   /api/v1/finance/goals                - Create goal
GET    /api/v1/finance/goals                - List goals
POST   /api/v1/finance/goals/{id}/contribute - Contribute to goal
```

### Reports
```
GET    /api/v1/finance/reports/monthly?year=Y&month=M - Monthly financial report
```

## 🔐 Security

- All endpoints require JWT authentication with Bearer token
- Users with roles: CUSTOMER and ADMIN can access finance endpoints
- Test endpoint by adding header: `Authorization: Bearer <token>`

## 📊 Key Features

### 1. Spending Categories
- System-default categories (Ăn uống, Di chuyển, Mua sắm, Giải trí, Y tế, Giáo dục, Lương, Đầu tư, Khác)
- User-custom categories
- Color and icon support for visualization

### 2. Budget Management
- Create monthly/yearly budgets by category
- Real-time tracking of spent amount
- Budget status: ACTIVE, EXCEEDED, COMPLETED
- Configurable alert threshold (default 80%)

### 3. Expense Tracking
- Manual expense entry with categories
- Auto-tagging from transactions via Kafka
- Full expense history with pagination
- Income and expense differentiation

### 4. Savings Goals
- Create goals with target amount and date
- Track progress with percentage
- Link to specific accounts
- One-time achievement notifications via Kafka

### 5. Financial Reports
- Monthly income/expense summary
- Expense breakdown by category with percentages
- Budget vs. actual comparison
- Net balance calculation

## 🔄 Kafka Integration

### Topics Used:
- `banking.transactions` - Listen for new transactions to auto-tag as expenses
- `banking.notifications` - Publish budget alerts and goal completion events

### Events:
1. **Budget Alert** - Triggered when budget usage reaches threshold
2. **Goal Completed** - Triggered when savings goal target is reached

## 🗄️ Database Schema

### spending_categories
```sql
id (PK), name, icon, colorHex, type, user_id (FK), is_system_default, created_at, updated_at
Indexes: idx_category_user (user_id)
```

### budgets
```sql
id (PK), user_id (FK), category_id (FK), limitAmount, spentAmount, year, month, 
status, alert_threshold, created_at, updated_at
Indexes: idx_budget_user, idx_budget_period
```

### expenses
```sql
id (PK), user_id (FK), category_id (FK), transaction_id (FK), amount, type, note, 
expense_date, is_auto_tagged, created_at, updated_at
Indexes: idx_expense_user, idx_expense_date, idx_expense_transaction
```

### savings_goals
```sql
id (PK), user_id (FK), name, targetAmount, savedAmount, targetDate, 
linked_account_id (FK), status, description, created_at, updated_at
Indexes: idx_goal_user
```

## 🧪 Testing

### Unit Tests
```bash
mvn test -Dtest=PersonalFinanceServiceTest
```

Tests cover:
- Budget creation and duplication handling
- Expense creation and budget updates
- Savings goal contribution and completion
- Category creation

### Integration Tests
```bash
mvn test -Dtest=PersonalFinanceIntegrationTest
```

Tests cover:
- Full flow: category → budget → expense → report
- Savings goal workflow
- Financial report generation
- Authorization checks
- Testcontainers for MySQL and Kafka

## ⚙️ Configuration

### application.properties (existing entries)
```properties
spring.jpa.hibernate.ddl-auto=update
spring.kafka.bootstrap-servers=localhost:9092
banking.jwt.secret=<your-secret>
```

### Default Spending Categories
Seeded automatically on startup:
- 🍔 Ăn uống (Food) - EXPENSE
- 🚗 Di chuyển (Transport) - EXPENSE
- 🛍️ Mua sắm (Shopping) - EXPENSE
- 🎮 Giải trí (Entertainment) - EXPENSE
- 💊 Y tế (Healthcare) - EXPENSE
- 📚 Giáo dục (Education) - EXPENSE
- 💼 Lương (Salary) - INCOME
- 📈 Đầu tư (Investment) - INCOME
- 📦 Khác (Other) - BOTH

## 📝 Example Usage

### Create Budget
```bash
curl -X POST http://localhost:8080/api/v1/finance/budgets \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "categoryId": 1,
    "limitAmount": 1000.00,
    "year": 2026,
    "month": 4,
    "alertThreshold": 80
  }'
```

### Add Expense
```bash
curl -X POST http://localhost:8080/api/v1/finance/expenses \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "categoryId": 1,
    "amount": 50.00,
    "type": "EXPENSE",
    "expenseDate": "2026-04-15",
    "note": "Lunch"
  }'
```

### Get Monthly Report
```bash
curl -X GET "http://localhost:8080/api/v1/finance/reports/monthly?year=2026&month=4" \
  -H "Authorization: Bearer <token>"
```

## 🐛 Troubleshooting

### Issue: Kafka consumer not triggered
**Solution**: Ensure Kafka is running and topic names match in configuration

### Issue: Category not found
**Solution**: Make sure category belongs to user or is system-default

### Issue: Budget not updating when expense added
**Solution**: Check that expense date matches budget year/month

## 📋 Checklist

- [x] Create entities (SpendingCategory, Budget, Expense, SavingsGoal)
- [x] Create enums (CategoryType, BudgetStatus, ExpenseType, GoalStatus)
- [x] Create repositories (4 main + TransactionRepository + AccountRepository)
- [x] Create PersonalFinanceService with all business logic
- [x] Create PersonalFinanceConsumer for Kafka integration
- [x] Create PersonalFinanceController with REST endpoints
- [x] Update SecurityConfig for /api/v1/finance/** endpoints
- [x] Create FinanceDataSeeder for default categories
- [x] Add DTOs to BankingDTO.java
- [x] Create unit tests (PersonalFinanceServiceTest)
- [x] Create integration tests (PersonalFinanceIntegrationTest)
- [x] Update application.properties (if needed)

## 🔗 Related Documentation

- See `AGENTS.md` for complete agent skill specifications
- See `HELP.md` for banking system overview
- JWT Configuration: `JWTConfig/`
- Existing Services: `Services/AuthService.java`

