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

