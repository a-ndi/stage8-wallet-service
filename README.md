# Wallet Service API

A comprehensive digital wallet service built with Spring Boot, featuring Google OAuth authentication, API key management, Paystack payment integration, and wallet-to-wallet transfers.

## Live Demo

- **API Base URL**: https://walletservice.up.railway.app
- **Swagger Documentation**: https://walletservice.up.railway.app/swagger-ui.html
- **Health Check**: https://walletservice.up.railway.app/health

## ✨ Features

- **🔐 Google OAuth 2.0 Authentication** - Secure sign-in with Google
- **🎫 JWT Token Management** - Stateless authentication for API requests
- **🔑 API Key Management** - Create, list, and revoke API keys with granular permissions
- **💳 Paystack Integration** - Deposit funds via Paystack payment gateway
- **💸 Wallet-to-Wallet Transfers** - Transfer funds between users
- **📊 Transaction History** - Track all deposits and transfers
- **📖 Swagger/OpenAPI Documentation** - Interactive API documentation

## 🛠️ Tech Stack

- **Framework**: Spring Boot 3.x
- **Language**: Java 17+
- **Database**: PostgreSQL
- **Authentication**: Google OAuth 2.0, JWT
- **Payments**: Paystack
- **Documentation**: OpenAPI 3.0 / Swagger UI
- **Deployment**: Railway

## 🚀 Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.8+
- PostgreSQL 14+
- Google Cloud Console account (for OAuth)
- Paystack account (for payments)

### Local Development Setup

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd wallet
   ```

2. **Create PostgreSQL database**
   ```bash
   createdb wallet
   ```

3. **Configure environment variables**
   
   Create a `.env` file or set these environment variables:
   ```bash
   # Database (for local development, defaults work)
   SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/wallet
   SPRING_DATASOURCE_USERNAME=postgres
   SPRING_DATASOURCE_PASSWORD=postgres

   # JWT Secret (generate a secure random string)
   JWT_SECRET=your-secure-secret-at-least-32-characters

   # Google OAuth
   GOOGLE_OAUTH_CLIENT_ID=your-google-client-id
   GOOGLE_OAUTH_CLIENT_SECRET=your-google-client-secret
   GOOGLE_OAUTH_REDIRECT_URI=http://localhost:8080/auth/google/callback

   # Paystack
   PAYSTACK_SECRET_KEY=sk_test_xxxxx
   PAYSTACK_PUBLIC_KEY=pk_test_xxxxx
   PAYSTACK_WEBHOOK_SECRET=sk_test_xxxxx
   ```

4. **Run the application**
   ```bash
   ./mvnw spring-boot:run
   ```

5. **Access the API**
   - API: http://localhost:8080
   - Swagger UI: http://localhost:8080/swagger-ui.html

## 📚 API Endpoints

### Authentication

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| GET | `/auth/google` | Initiate Google OAuth sign-in | No |
| GET | `/auth/google/callback` | OAuth callback (handled automatically) | No |

### API Keys

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| GET | `/keys` | List all API keys | Yes |
| POST | `/keys/create` | Create new API key | Yes |
| POST | `/keys/rollover` | Rollover expired API key | Yes |
| DELETE | `/keys/{id}` | Revoke an API key | Yes |

### Wallet Operations

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| GET | `/wallet/balance` | Get wallet balance | Yes |
| GET | `/wallet/transactions` | Get transaction history | Yes |
| POST | `/wallet/deposit` | Initiate Paystack deposit | Yes |
| GET | `/wallet/deposit/{reference}/status` | Check deposit status | Yes |
| POST | `/wallet/transfer` | Transfer to another wallet | Yes |

### Webhooks

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| POST | `/wallet/paystack/webhook` | Paystack webhook handler | No (signature verified) |

### Health Check

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| GET | `/health` | Health check | No |
| GET | `/` | Service info | No |

## 🔐 Authentication

The API supports two authentication methods:

### 1. JWT Token (for user sessions)

Obtain a JWT token via Google OAuth:
```bash
# 1. Visit in browser to authenticate
https://walletservice.up.railway.app/auth/google

# 2. Use the returned token in requests
curl -X GET https://walletservice.up.railway.app/wallet/balance \
  -H "Authorization: Bearer <your-jwt-token>"
```

### 2. API Key (for service-to-service)

Create and use API keys with specific permissions:
```bash
# Create an API key
curl -X POST https://walletservice.up.railway.app/keys/create \
  -H "Authorization: Bearer <jwt-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "my-service-key",
    "expiry": "30D",
    "permissions": ["READ", "DEPOSIT", "TRANSFER"]
  }'

# Use the API key
curl -X GET https://walletservice.up.railway.app/wallet/balance \
  -H "x-api-key: <your-api-key>"
```

### API Key Permissions

| Permission | Allows |
|------------|--------|
| `READ` | View balance and transaction history |
| `DEPOSIT` | Initiate deposits |
| `TRANSFER` | Transfer funds to other wallets |

### API Key Expiry Formats

| Format | Duration | Example |
|--------|----------|---------|
| `{n}H` | Hours | `2H`, `24H` |
| `{n}D` | Days | `7D`, `30D` |
| `{n}M` | Months | `1M`, `6M` |
| `{n}Y` | Years | `1Y`, `2Y` |

## 💵 Amount Format

**All amounts are in kobo** (smallest Nigerian currency unit):
- 1 Naira = 100 kobo
- Example: `500000` kobo = ₦5,000

```json
// Deposit ₦1,000
{
  "amount": 100000
}

// Transfer ₦500
{
  "wallet_number": "0123456789",
  "amount": 50000
}
```

## 🌍 Deployment (Railway)

### Environment Variables for Railway

```bash
# Database (Railway provides these via Postgres plugin)
SPRING_DATASOURCE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
SPRING_DATASOURCE_USERNAME=${{Postgres.PGUSER}}
SPRING_DATASOURCE_PASSWORD=${{Postgres.PGPASSWORD}}
SPRING_JPA_HIBERNATE_DDL_AUTO=update

# JWT
JWT_SECRET=<your-secure-secret>

# Google OAuth
GOOGLE_OAUTH_CLIENT_ID=<your-client-id>
GOOGLE_OAUTH_CLIENT_SECRET=<your-client-secret>
GOOGLE_OAUTH_REDIRECT_URI=https://<your-app>.up.railway.app/auth/google/callback

# Paystack
PAYSTACK_SECRET_KEY=<your-secret-key>
PAYSTACK_PUBLIC_KEY=<your-public-key>
PAYSTACK_WEBHOOK_SECRET=<your-secret-key>
```

### Google Cloud Console Setup

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create or select a project
3. Enable Google+ API
4. Create OAuth 2.0 credentials
5. Add authorized redirect URI:
   ```
   https://<your-app>.up.railway.app/auth/google/callback
   ```

### Paystack Dashboard Setup

1. Go to [Paystack Dashboard](https://dashboard.paystack.com/)
2. Navigate to Settings → API Keys & Webhooks
3. Set webhook URL:
   ```
   https://<your-app>.up.railway.app/wallet/paystack/webhook
   ```

## 🧪 Testing

### Run Tests
```bash
./mvnw test
```

### Manual Testing with cURL and Postman

```bash
# Health check
curl https://walletservice.up.railway.app/health

# Get balance (with JWT)
curl -X GET https://walletservice.up.railway.app/wallet/balance \
  -H "Authorization: Bearer <token>"

# Create API key
curl -X POST https://walletservice.up.railway.app/keys/create \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"name": "test-key", "expiry": "1D", "permissions": ["READ"]}'

# Initiate deposit
curl -X POST https://walletservice.up.railway.app/wallet/deposit \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"amount": 100000}'

# Transfer funds
curl -X POST https://walletservice.up.railway.app/wallet/transfer \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"wallet_number": "0123456789", "amount": 50000}'
```

## 📁 Project Structure

```
src/main/java/com/stage8/wallet/
├── config/
│   └── SwaggerConfig.java          # OpenAPI configuration
├── controller/
│   ├── AuthController.java         # Google OAuth endpoints
│   ├── ApiKeyController.java       # API key management
│   ├── WalletController.java       # Wallet operations
│   ├── PaystackWebhookController.java
│   └── HealthController.java
├── dto/                            # Request/Response DTOs
├── exception/                      # Custom exceptions
├── model/
│   ├── entity/                     # JPA entities
│   └── enums/                      # Enums (Permission, TransactionType, etc.)
├── repository/                     # JPA repositories
├── security/
│   ├── JwtAuthFilter.java          # JWT authentication filter
│   ├── ApiKeyFilter.java           # API key authentication filter
│   ├── JwtService.java             # JWT utilities
│   └── SecurityConfig.java         # Spring Security config
├── service/                        # Business logic
└── WalletApplication.java          # Main application
```


<img width="1523" height="1002" alt="image" src="https://github.com/user-attachments/assets/979f83fd-8725-4662-8031-c55b8af85d64" />


---

