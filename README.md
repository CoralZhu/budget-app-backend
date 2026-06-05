# AI Budget Assistant — Backend

Spring Boot REST API for AI-powered personal finance tracking. Part of a 3-service architecture (Spring Boot backend + Vue 3 frontend + Python AI agent).

## 🌐 Live Deployment

**API base**: `https://budget-app-backend-mubh.onrender.com`

Try the full system at the frontend: [budget-app-frontend-7x7q.onrender.com](https://budget-app-frontend-7x7q.onrender.com)

> ⚠️ Running on Render free tier — Spring Boot cold-starts in ~2 minutes after 15 minutes of inactivity.

## 🏗 Architecture

```text
Spring Boot (this repo)
├── PostgreSQL (shared with Python AI agent)
├── Aliyun OCR (receipt text extraction)
└── DeepSeek LLM (receipt parsing + voice parsing)
```

Sibling services:
- Vue 3 Frontend — https://github.com/CoralZhu/budget-app-frontend
- Python AI Agent — https://github.com/CoralZhu/budget-app-agent

## 🛠 Tech Stack

- **Framework**: Spring Boot 3 + Spring Web + Spring Security
- **Persistence**: Spring Data JPA + Hibernate + PostgreSQL
- **Auth**: JWT (HS512, shared secret with Python Agent for cross-service auth)
- **Build**: Maven + Java 17
- **AI integration**: Aliyun OCR client + DeepSeek REST API
- **Profiles**: `local` (with email verification), `prod` (Render-ready, skips email)
- **Container**: Dockerfile with JVM tuning (-Xmx256m, SerialGC) for 512MB free tier

## ✨ Core Endpoints

```text
POST /api/auth/register      # Sign up (email verification skipped in prod profile)
POST /api/auth/send-code     # Send verification code (local only)
POST /api/auth/login         # Returns JWT + user info
GET  /api/transactions       # List transactions (filterable by month, category, type)
POST /api/transactions       # Create transaction
PUT  /api/transactions/{id}  # Update
DELETE /api/transactions/{id}
GET  /api/categories         # List categories (income/expense)
POST /api/categories         # Create custom category
GET  /api/budgets            # List monthly budgets
POST /api/budgets            # Set total or per-category budget
POST /api/ocr/receipt        # Upload image, returns parsed transaction data
POST /api/voice/parse        # Send voice transcript, returns structured transaction
GET  /api/users/profile      # Current user profile
```

All endpoints (except `/api/auth/*` and `/api/ocr/*`) require `Authorization: Bearer <JWT>`.

## 🚀 Local Development

```bash
git clone git@github.com:CoralZhu/budget-app-backend.git
cd budget-app-backend

# Set up local PostgreSQL
createdb ai_budget_app

# Configure secrets locally (not committed)
cp src/main/resources/application-local.properties.example src/main/resources/application-local.properties
# Edit with your Gmail SMTP / Aliyun / DeepSeek / JWT credentials

# Run
./mvnw spring-boot:run
```

Server starts on `http://localhost:8080`. Profile defaults to `local` (email verification required).

## 🐳 Docker Build

```bash
docker build -t budget-app-backend .
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://your-db:5432/ai_budget_app \
  -e SPRING_DATASOURCE_USERNAME=... \
  -e SPRING_DATASOURCE_PASSWORD=... \
  -e JWT_SECRET=... \
  -e DEEPSEEK_API_KEY=... \
  -e ALIYUN_ACCESS_KEY_ID=... \
  -e ALIYUN_ACCESS_KEY_SECRET=... \
  budget-app-backend
```

The Dockerfile uses a multi-stage build to keep the final image lean, and pre-tunes JVM flags so the app starts within Render's 512MB free tier.

## 🔐 Environment Variables (production)

| Variable | Purpose |
|---|---|
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | DB user |
| `SPRING_DATASOURCE_PASSWORD` | DB password |
| `JWT_SECRET` | Shared with Python Agent for cross-service JWT verification |
| `DEEPSEEK_API_KEY` | DeepSeek LLM |
| `ALIYUN_ACCESS_KEY_ID` | Aliyun OCR |
| `ALIYUN_ACCESS_KEY_SECRET` | Aliyun OCR |
| `SPRING_PROFILES_ACTIVE` | Set to `prod` on Render |

## 🧪 Tests

```bash
./mvnw test       # Runs 10+ unit tests
```

## 📐 Project Structure

```text
src/main/java/com/zhuxiangcun/budgetapp/
├── controller/       # REST endpoints
├── service/          # Business logic
│   ├── UserService
│   ├── TransactionService
│   ├── BudgetService
│   ├── CategoryService
│   ├── VerificationCodeService
│   └── ai/
│       ├── AliyunOcrService
│       └── DeepSeekService
├── repository/       # JPA repositories
├── entity/           # Domain entities
├── dto/              # Request/response DTOs
├── config/           # Security, CORS, web config
└── util/             # JwtUtil
```

## 📄 License

Personal portfolio project. No commercial use.
