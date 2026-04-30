# SePay Personal Finance — Banking System Extension

Module **Quản lý Tài chính Cá nhân qua SePay Open API** — không yêu cầu user liên kết
trực tiếp với ngân hàng. Thay vào đó, user liên kết tài khoản ngân hàng vào SePay
(qua OTP, an toàn), rồi hệ thống nhận giao dịch qua **SePay API** (pull) và
**SePay Webhook** (push real-time).

---

## 1. Tổng quan kiến trúc

```
                    ┌──────────────────────────────────────────┐
                    │              SePay Platform               │
                    │  (User liên kết ngân hàng tại SePay)     │
                    └────────────┬─────────────────────────────┘
                                 │
              ┌──────────────────┼──────────────────┐
              │                  │                  │
    REST Pull API          Webhook Push         SePay Dashboard
  (Scheduled Sync)      (Real-time notify)    (my.sepay.vn)
              │                  │
              ▼                  ▼
   ┌──────────────────────────────────────┐
   │          Spring Boot Application     │
   │                                      │
   │  SepayApiClient ──► SyncService      │
   │  SepayWebhookController ──► same     │
   │                      │               │
   │              TransactionService      │
   │                      │               │
   │         ┌────────────┼────────────┐  │
   │         ▼            ▼            ▼  │
   │   CategoryService  BudgetService  │  │
   │   (auto-classify) (track limits)  │  │
   │                      │            │  │
   │              Kafka: banking.sepay  │  │
   └──────────────────────────────────────┘
```

**Ưu điểm so với liên kết trực tiếp ngân hàng:**
- Không cần xử lý OAuth2 từng ngân hàng riêng lẻ
- SePay đã hợp tác chính thức với ngân hàng (không bot/scraping)
- Hỗ trợ hầu hết ngân hàng Việt Nam qua một đầu mối duy nhất
- Rate limit SePay: 3 request/giây — cần throttle khi polling

---

## 2. SePay API — Tham chiếu nhanh

### 2.1 Authentication

Mọi request dùng **Bearer Token** (API Token tạo tại `my.sepay.vn`):

```
Authorization: Apikey {YOUR_API_TOKEN}
```

### 2.2 Endpoints sử dụng

| Method | URL | Mô tả |
|--------|-----|-------|
| GET | `https://my.sepay.vn/userapi/transactions/list` | Lấy danh sách giao dịch |
| GET | `https://my.sepay.vn/userapi/transactions/details/{id}` | Chi tiết một giao dịch |
| GET | `https://my.sepay.vn/userapi/transactions/count` | Đếm tổng số giao dịch |
