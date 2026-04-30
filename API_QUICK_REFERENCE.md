# Personal Finance API - Quick Reference

## 🔐 Authentication

All endpoints require a Bearer token in the Authorization header:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### How to Get a Token

1. **Register**
```bash
POST /api/v1/auth/register
Content-Type: application/json

{
  "username": "john_doe",
  "email": "john@example.com",
  "password": "SecurePass123!",
  "firstName": "John",
  "lastName": "Doe"
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

2. **Login**
```bash
POST /api/v1/auth/login
Content-Type: application/json

{
  "username": "john_doe",
  "password": "SecurePass123!"
}
```

---

## 📂 Categories API

### Create Category
```bash
POST /api/v1/finance/categories
Content-Type: application/json
Authorization: Bearer <token>

{
  "name": "Du lịch",
  "icon": "✈️",
  "colorHex": "#FF6B6B",
  "type": "EXPENSE"
}
```

**Response:**
```json
{
  "id": 10,
  "name": "Du lịch",
  "icon": "✈️",
  "colorHex": "#FF6B6B",
  "type": "EXPENSE",
  "systemDefault": false
}
```

---

### List Categories
```bash
GET /api/v1/finance/categories
Authorization: Bearer <token>
```

**Response:**
```json
[
  {
    "id": 1,
    "name": "Ăn uống",
    "icon": "🍔",
    "colorHex": "#FF6B6B",
    "type": "EXPENSE",
    "systemDefault": true
  },
  ...
]
```

---

## 💰 Budget API

### Create Budget
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

**Response:**
```json
{
  "id": 1,
  "category": { ... },
  "limitAmount": 1000.00,
  "spentAmount": 0.00,
  "remainingAmount": 1000.00,
  "usagePercent": 0.0,
  "status": "ACTIVE",
  "year": 2026,
  "month": 4
}
```

---

### List Budgets
```bash
GET /api/v1/finance/budgets?year=2026&month=4
Authorization: Bearer <token>
```

**Response:**
```json
[
  {
    "id": 1,
    "category": { "name": "Ăn uống", ... },
    "limitAmount": 1000.00,
    "spentAmount": 250.00,
    "remainingAmount": 750.00,
    "usagePercent": 25.0,
    "status": "ACTIVE",
    "year": 2026,
    "month": 4
  }
]
```

---

## 💸 Expense API

### Create Expense
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

**Response:**
```json
{
  "id": 1,
  "category": { "name": "Ăn uống", ... },
  "amount": 50.00,
  "type": "EXPENSE",
  "note": "Lunch with team",
  "expenseDate": "2026-04-15",
  "autoTagged": false,
  "transactionId": null
}
```

---

### List Expenses (with Pagination)
```bash
GET /api/v1/finance/expenses?page=0&size=20
Authorization: Bearer <token>
```

**Parameters:**
- `page`: Page number (0-indexed, default: 0)
- `size`: Items per page (default: 20)

**Response:**
```json
{
  "content": [
    {
      "id": 1,
      "category": { ... },
      "amount": 50.00,
      "type": "EXPENSE",
      "note": "Lunch with team",
      "expenseDate": "2026-04-15",
      "autoTagged": false,
      "transactionId": null
    }
  ],
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 45,
  "totalPages": 3,
  "last": false
}
```

---

## 🎯 Savings Goals API

### Create Goal
```bash
POST /api/v1/finance/goals
Content-Type: application/json
Authorization: Bearer <token>

{
  "name": "Buy a car",
  "targetAmount": 50000.00,
  "targetDate": "2027-06-30",
  "description": "Save for a new car"
}
```

**Response:**
```json
{
  "id": 1,
  "name": "Buy a car",
  "targetAmount": 50000.00,
  "savedAmount": 0.00,
  "progressPercent": 0.0,
  "targetDate": "2027-06-30",
  "status": "IN_PROGRESS",
  "description": "Save for a new car"
}
```

---

### List Goals
```bash
GET /api/v1/finance/goals
Authorization: Bearer <token>
```

**Response:**
```json
[
  {
    "id": 1,
    "name": "Buy a car",
    "targetAmount": 50000.00,
    "savedAmount": 5000.00,
    "progressPercent": 10.0,
    "targetDate": "2027-06-30",
    "status": "IN_PROGRESS",
    "description": "Save for a new car"
  }
]
```

---

### Contribute to Goal
```bash
POST /api/v1/finance/goals/1/contribute?amount=10000.00
Authorization: Bearer <token>
```

**Response:**
```json
{
  "id": 1,
  "name": "Buy a car",
  "targetAmount": 50000.00,
  "savedAmount": 15000.00,
  "progressPercent": 30.0,
  "targetDate": "2027-06-30",
  "status": "IN_PROGRESS",
  "description": "Save for a new car"
}
```

---

## 📊 Reports API

### Get Monthly Report
```bash
GET /api/v1/finance/reports/monthly?year=2026&month=4
Authorization: Bearer <token>
```

**Response:**
```json
{
  "year": 2026,
  "month": 4,
  "totalIncome": 5000.00,
  "totalExpense": 1500.00,
  "netBalance": 3500.00,
  "expenseByCategory": [
    {
      "category": {
        "id": 1,
        "name": "Ăn uống",
        "icon": "🍔",
        "colorHex": "#FF6B6B",
        "type": "EXPENSE",
        "systemDefault": true
      },
      "totalAmount": 500.00,
      "percentage": 33.33
    },
    {
      "category": {
        "id": 2,
        "name": "Di chuyển",
        "icon": "🚗",
        "colorHex": "#4ECDC4",
        "type": "EXPENSE",
        "systemDefault": true
      },
      "totalAmount": 400.00,
      "percentage": 26.67
    }
  ],
  "budgetSummary": [
    {
      "id": 1,
      "category": { ... },
      "limitAmount": 1000.00,
      "spentAmount": 500.00,
      "remainingAmount": 500.00,
      "usagePercent": 50.0,
      "status": "ACTIVE",
      "year": 2026,
      "month": 4
    }
  ]
}
```

---

## 🚨 HTTP Status Codes

| Code | Meaning | Example |
|------|---------|---------|
| 200 | OK | Successfully retrieved data |
| 201 | Created | Budget/Expense/Goal created |
| 400 | Bad Request | Invalid input data |
| 401 | Unauthorized | Missing or invalid token |
| 403 | Forbidden | Insufficient permissions |
| 404 | Not Found | Resource not found |
| 409 | Conflict | Duplicate budget for same period |
| 500 | Server Error | Internal server error |

---

## 📍 Enum Values

### CategoryType
```
EXPENSE - Spending category
INCOME  - Income source
BOTH    - Can be either
```

### BudgetStatus
```
ACTIVE     - Within limit
EXCEEDED   - Over limit
COMPLETED  - Period finished
```

### ExpenseType
```
EXPENSE - Money spent
INCOME  - Money earned
```

### GoalStatus
```
IN_PROGRESS - Working towards goal
COMPLETED   - Target reached
CANCELLED   - Goal abandoned
```

---

## 🔍 Query Examples

### Curl Examples

#### Create Budget
```bash
BUDGET_DATA='{
  "categoryId": 1,
  "limitAmount": 1000.00,
  "year": 2026,
  "month": 4,
  "alertThreshold": 80
}'

curl -X POST http://localhost:8080/api/v1/finance/budgets \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "$BUDGET_DATA"
```

#### Add Multiple Expenses
```bash
curl -X POST http://localhost:8080/api/v1/finance/expenses \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"categoryId": 1, "amount": 50.00, "type": "EXPENSE", "expenseDate": "2026-04-15"}'

curl -X POST http://localhost:8080/api/v1/finance/expenses \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"categoryId": 2, "amount": 30.00, "type": "EXPENSE", "expenseDate": "2026-04-16"}'
```

#### Get Report with jq
```bash
curl -s http://localhost:8080/api/v1/finance/reports/monthly?year=2026&month=4 \
  -H "Authorization: Bearer $TOKEN" | jq '.totalExpense'
```

---

## 📋 Field Validation

### Category Request
- `name`: Required, 1-100 characters
- `icon`: Optional, emoji or icon code
- `colorHex`: Optional, must match `#RRGGBB` format
- `type`: Required, one of: EXPENSE, INCOME, BOTH

### Budget Request
- `categoryId`: Required, positive integer
- `limitAmount`: Required, positive BigDecimal
- `year`: Required, 2020-2100
- `month`: Optional, 1-12
- `alertThreshold`: Optional, 1-100 (default: 80)

### Expense Request
- `categoryId`: Required, positive integer
- `amount`: Required, positive BigDecimal
- `type`: Required, one of: EXPENSE, INCOME
- `expenseDate`: Required, LocalDate (YYYY-MM-DD)
- `note`: Optional, max 500 characters

### Savings Goal Request
- `name`: Required, 1-200 characters
- `targetAmount`: Required, positive BigDecimal
- `targetDate`: Optional, LocalDate
- `linkedAccountId`: Optional, positive integer
- `description`: Optional, max 500 characters

---

## 🔧 Common Errors & Solutions

### Error: "Category not found"
**Cause**: Category doesn't exist or belongs to another user
**Solution**: Get list of categories and use correct ID

### Error: "Budget already exists for this period"
**Cause**: Budget already created for same category and month
**Solution**: Update existing budget or use different category/month

### Error: Invalid color hex format
**Cause**: Color must be #RRGGBB (7 characters)
**Examples**:
- ✓ #FF6B6B
- ✗ #FF6
- ✗ FF6B6B (missing #)

### Error: "Invalid expense date"
**Cause**: Date format incorrect
**Solution**: Use YYYY-MM-DD format
- ✓ 2026-04-15
- ✗ 04/15/2026

---

## 💡 Tips & Tricks

### 1. Get Budget Status
Budget status automatically updated when expenses added:
- ACTIVE (0-99% of limit)
- EXCEEDED (100%+)

### 2. Progress Calculation
Savings goal progress automatically calculated:
```
progressPercent = (savedAmount / targetAmount) * 100
```

### 3. Category Filtering
Categories returned include:
- System-default (available to all users)
- User-custom (created by specific user)

### 4. Date Ranges
Report API accepts:
- Any valid month (1-12)
- Any year (1900-2999)
- Automatically handles month boundaries

### 5. Batch Operations
Can add multiple expenses in sequence:
```bash
for i in {1..10}; do
  curl -X POST .../expenses \
    -H "Authorization: Bearer $TOKEN" \
    -d "{...}"
done
```

---

## 🔐 Security Notes

1. Tokens expire after 24 hours
2. Always include Bearer prefix
3. Tokens are case-sensitive
4. All data is user-isolated
5. Passwords are hashed with BCrypt

---

**For complete documentation, see: `PERSONAL_FINANCE_IMPL.md`**

