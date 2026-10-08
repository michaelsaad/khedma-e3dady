# Khedma E3dady

Attendance and points tracking for the e3dady (middle school) church service.

Admins record weekly attendance (service and mass), and points are distributed automatically. If attendance is unmarked, its points are rolled back.

## Repository layout

```
khedma-e3dady/
├── backend/             Spring Boot API
├── frontend/            Web app (coming soon)
└── docker-compose.yml   Local PostgreSQL
```

## Tech stack

| Layer    | Technology                                        |
| -------- | ------------------------------------------------- |
| API      | Spring Boot 3.5, Java 17                          |
| Database | PostgreSQL 16, Flyway migrations                  |
| Auth     | Google sign-in and email/password, JWT            |
| Tests    | JUnit 5, Testcontainers                           |

## Getting started

**Prerequisites:** Java 17+, Maven, Docker.

```bash
# 1. Start PostgreSQL (port 5433)
docker compose up -d

# 2. Run the API (port 8080)
cd backend
mvn spring-boot:run
```

Run the tests (Docker must be running):

```bash
cd backend
mvn test
```

## Configuration

Configuration comes from environment variables. See [`backend/.env.example`](backend/.env.example).

| Variable               | Description                                                  |
| ---------------------- | ------------------------------------------------------------ |
| `DB_URL`               | JDBC URL, e.g. `jdbc:postgresql://localhost:5433/khedma`     |
| `DB_USERNAME`          | Database user                                                |
| `DB_PASSWORD`          | Database password                                            |
| `JWT_SECRET`           | Token signing key, at least 32 characters                    |
| `GOOGLE_CLIENT_ID`     | OAuth client ID from Google Cloud Console                    |
| `SUPER_ADMIN_EMAILS`   | Comma-separated owner emails, promoted once verified         |
| `SPRING_MAIL_HOST`     | SMTP host for verification emails (unset = codes are logged) |
| `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | SMTP settings |
| `MAIL_FROM`            | Sender address for verification emails                       |
| `CORS_ALLOWED_ORIGINS` | Frontend origin(s), e.g. `http://localhost:3000`             |

## Roles

| Role          | Access                                                        |
| ------------- | ------------------------------------------------------------- |
| `USER`        | Dashboard, plus their own data once linked to a student       |
| `ADMIN`       | Students, groups, attendance, points, app settings            |
| `SUPER_ADMIN` | Everything above, plus user approval and role management      |

New accounts start as `PENDING` until a super admin approves them. A super admin can promote any user to `ADMIN`.

## Authentication

- **Google:** the frontend gets an ID token from the Google sign-in button and sends it to `POST /auth/google`.
- **Email and password:**
  1. `POST /auth/register` creates the account and emails a 6-digit code.
  2. `POST /auth/verify-email` checks the code and signs the user in.

  Codes expire after 10 minutes and allow 5 attempts. A new code can be requested with `POST /auth/resend-code` once every 60 seconds. Login is blocked until the email is verified.

Unauthenticated users can only reach the `/auth` endpoints.

## Errors

Every error returns a stable `code` for the frontend to translate. The `message` is an English fallback.

```json
{ "code": "INSUFFICIENT_BALANCE", "message": "Balance is too low for this deduction" }
```

Validation errors also list each failing field with its constraint (`NotBlank`, `Size`, `Email`, …). All codes are defined in [`ErrorCode.java`](backend/src/main/java/com/khedma/common/ErrorCode.java).

## Automatic points

Point values are set on the **App Settings** page (default: mass = 5, service = 2).

- **Marking attendance** creates an `ATTENDANCE_AWARD` transaction. Its notes say the attendance type and the meeting date.
- **Unmarking attendance** creates an `ATTENDANCE_REVERSAL` that cancels the original award.
- **Saving the same attendance twice** does not award points again.
- **Changing point values** only affects attendance recorded afterwards. Reversals always cancel the amount that was originally awarded.
- **Group points** are added manually only.

Manual transactions (`ADD`, `DEDUCT`, `RESET`) work for both students and groups.

## API overview

All endpoints are under `/api` and need a `Bearer` token, except the `/auth` endpoints.

| Area        | Endpoints                                                                     | Role          |
| ----------- | ----------------------------------------------------------------------------- | ------------- |
| Auth        | `POST /auth/register`, `/auth/verify-email`, `/auth/resend-code`, `/auth/login`, `/auth/google` | Public |
| Account     | `GET /me`                                                                     | Signed in     |
| Dashboard   | `GET /dashboard`, `GET /dashboard/students` (cards), `GET /dashboard/students/{id}` | Approved user |
| My data     | `GET /me/student`, `GET /me/transactions`                                     | Approved user |
| Settings    | `GET /settings`, `PUT /settings`                                              | Admin         |
| Students    | `GET /students` (search, paginated), `POST`, `GET/PUT/DELETE /students/{id}`  | Admin         |
| Groups      | `GET /groups`, `POST /groups`, `PUT /groups/{id}`                             | Admin         |
| Attendance  | `GET /attendance/meetings`, `GET/PUT /attendance/{date}`                      | Admin         |
|             | `PUT /attendance/{date}/students/{studentId}` (save one student)              | Admin         |
| Points      | `POST /students/{id}/points`, `POST /groups/{id}/points`                      | Admin         |
| History     | `GET /transactions`, `/students/{id}/transactions`, `/groups/{id}/transactions` (paginated) | Admin |
| Users       | `GET /users`, `PATCH /users/{id}`, `PUT /users/{id}/student`                  | Super admin   |

Paginated endpoints accept `page` (0-based) and `size` (max 200).
