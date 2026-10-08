# Khedma — حضور ونقاط مخدومين إعدادي

- `backend/` — Spring Boot 3.5 (Java 17) + PostgreSQL + Flyway
- `frontend/` — (قريبًا) واجهة جديدة

## تشغيل الباك اند محليًا

```bash
docker compose up -d
cd backend
mvn spring-boot:run
```

الـ API على `http://localhost:8080`. الداتابيز على port `5433`.
الاختبارات: `mvn test` (محتاجة Docker شغال).

## الإعدادات (environment variables)

| المتغير | الوصف |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | اتصال PostgreSQL |
| `JWT_SECRET` | 32 حرف على الأقل — لازم يتغير في الإنتاج |
| `GOOGLE_CLIENT_ID` | OAuth Client ID من Google Cloud (لزرار الدخول بجوجل) |
| `SUPER_ADMIN_EMAILS` | إيميلات المالك (مفصولة بفاصلة) — بتبقى SUPER_ADMIN عند الدخول بجوجل |
| `CORS_ALLOWED_ORIGINS` | رابط الفرونت اند |

## الأدوار

| الدور | الصلاحيات |
|---|---|
| `USER` | يشوف بياناته ونقاطه لو حسابه مربوط بمخدوم |
| `ADMIN` | المخدومين، الجروبات، الحضور، النقاط، إعدادات التطبيق |
| `SUPER_ADMIN` | كل ما سبق + إدارة المستخدمين والصلاحيات |

أي حساب جديد بيبدأ `PENDING` لحد ما الـ SUPER_ADMIN يوافق عليه.

## النقاط الأوتوماتيك

عند حفظ الحضور، الباك اند بيكتب transaction لكل حضور (خدمة / قداس) بالقيمة من إعدادات التطبيق،
والملاحظة زي: `حضور قداس يوم الجمعة 9 أكتوبر 2026`.
لو اتشال الحضور، بيتعمل transaction عكسية بنفس القيمة الأصلية: `إلغاء حضور قداس يوم ...`.
الحفظ أكتر من مرة ما بيكررش النقاط. نقاط الجروبات يدوي فقط.

## الـ API

| Method | Path | الدور |
|---|---|---|
| POST | `/api/auth/register` · `/api/auth/login` · `/api/auth/google` | عام |
| GET | `/api/me` | أي حساب مسجل |
| GET | `/api/me/student` · `/api/me/transactions` | USER |
| GET/PUT | `/api/settings` | ADMIN |
| GET/POST | `/api/students` (بحث + pagination: `q, grade, groupId, page, size`) | ADMIN |
| GET/PUT/DELETE | `/api/students/{id}` | ADMIN |
| GET/POST | `/api/groups` · PUT `/api/groups/{id}` | ADMIN |
| GET | `/api/attendance/meetings` | ADMIN |
| GET/PUT | `/api/attendance/{date}` (عرض + حفظ جماعي) | ADMIN |
| PUT | `/api/attendance/{date}/students/{studentId}` (حفظ فوري لمخدوم) | ADMIN |
| POST | `/api/students/{id}/points` · `/api/groups/{id}/points` (`ADD / DEDUCT / RESET`) | ADMIN |
| GET | `/api/transactions` · `/api/students/{id}/transactions` · `/api/groups/{id}/transactions` (pagination) | ADMIN |
| GET | `/api/users` · PATCH `/api/users/{id}` · PUT `/api/users/{id}/student` | SUPER_ADMIN |
