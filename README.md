# Sunrise Dental Clinic - Management System

Distributed appointment, treatment, POS billing and reporting system for a
dental practice. Java Servlets + JDBC + MySQL on the server, plain
HTML/CSS/JavaScript in the browser. **No web framework, no ORM, no front-end
framework** - the only runtime dependency is the MySQL JDBC driver.

The browser holds no clinic data of its own: the POS screens read one document
from `/api/pos/state` and send named commands back. Nothing is kept in
`localStorage`, passwords are never compared in the browser, and two machines
on the front desk see the same queue.

---

## 1. Architecture

Three tiers, each depending only on the one below it:

```
Browser (HTML/CSS/JS)  ->  fetch() JSON over HTTP
        |
   Presentation      servlet/*      request routing, status codes, JSON in/out
        |
   Business logic    service/*      rules, validation, pricing, permissions
        |
   Data access       dao/*          all SQL, transactions
        |
   MySQL 8           triggers, views, stored procedures
```

Nothing above the DAO layer contains SQL; nothing below the service layer knows
what an HTTP request is. That is what makes the services unit-testable without a
container.

### Design patterns used

| Pattern | Where | Why it is there |
|---|---|---|
| Singleton | `util.DBConnection`, `util.AppConfig`, `service.notify.EventBus`, `dao.DAOFactory` | One owner for the driver, the configuration and the listener registry |
| Data Access Object | `dao.*DAO` | Every statement for a table lives in one class |
| Abstract Factory | `dao.DAOFactory` | Services never call `new SomeDAO()`, so tests can swap in mocks |
| Strategy | `service.pricing.PricingStrategy` + Standard / VIP / SeniorCitizen | Billing rules vary per patient; adding a scheme means adding a class |
| Factory Method | `service.pricing.PricingStrategyFactory` | Chooses the strategy from the patient record |
| Builder | `model.Bill.Builder` | A bill has nine optional money fields |
| Observer | `service.notify.EventBus` + `AuditTrailListener`, `QueueDisplayListener` | Audit logging and the "now serving" display react to events instead of being wired into the booking code |
| Template Method | `servlet.BaseServlet.service()` | Fixes the request skeleton and the error translation once |
| Intercepting Filter | `filter.AuthFilter`, `filter.SecurityHeadersFilter` | Authentication and security headers cannot be forgotten by a servlet |
| MVC | webapp (view) / servlet (controller) / model + service (model) | Standard separation for the web tier |

---

## 2. Roles and permissions

| Can do | Super admin | Admin | Doctor | Nurse | Patient admin | Cashier |
|---|:--:|:--:|:--:|:--:|:--:|:--:|
| Create admins and doctors | yes | - | - | - | - | - |
| Create nurses, cashiers, patient admins | yes | yes | - | - | - | - |
| Change clinic settings | yes | yes | - | - | - | - |
| Income and doctor-wise reports | yes | yes | own income only | - | - | - |
| Create / edit sessions | yes | yes | own sessions | - | - | - |
| Register a patient | yes | yes | yes | yes | yes | - |
| Book a patient into a session | yes | yes | yes | yes | yes | yes |
| Record triage vitals | yes | yes | yes | yes | - | - |
| Consultation desk and queue | yes | yes | yes | - | - | - |
| Set per-doctor procedure charges | yes | yes | own charges | - | - | - |
| File a diagnostic report | yes | yes | yes | yes | - | - |
| Raise an invoice | yes | yes | yes | yes | - | yes |
| Take a payment | yes | yes | - | - | - | yes |
| Cancel appointments by NIC | yes | yes | - | - | yes | yes |

Permissions live in one table in `pos.PosService` and are checked on every
request. The sidebar hides what a role cannot use, but that is convenience
only - the server refuses regardless.

The public portal (`index.html`) needs no account. It can show clinic details,
doctors and open sessions, and it can register a visitor and take a booking.
It is served a deliberately reduced document: no patient record, invoice,
payment or diagnostic report is ever sent to an unauthenticated browser.

---

## 3. Setting it up

### Prerequisites
- JDK 17 or newer
- Apache Tomcat 10.1+ (Jakarta EE 10 - **not** Tomcat 9, the package names differ)
- MySQL 8.x
- Maven 3.8+

### Database

```bash
mysql -u root -p < database/01_schema.sql
mysql -u root -p < database/02_triggers_views_procedures.sql
mysql -u root -p < database/03_seed.sql
mysql -u root -p < database/04_pos_extensions.sql
```

`04` adds what the POS screens need: NIC as a unique patient key, triage
vitals, per-doctor procedure charges, receipt and card-masking columns, the
patient administrator role, a doctor revenue view and a cancellation trigger.

### Configuration

Edit `src/main/resources/app.properties` with your MySQL username and password.
Every value can also be supplied as a `-D` system property at start-up.

### Build and deploy

```bash
mvn clean package          # produces target/sunrise.war
cp target/sunrise.war $CATALINA_HOME/webapps/
$CATALINA_HOME/bin/startup.sh
```

Open <http://localhost:8080/sunrise/>.

On the first start-up, and only while the database is otherwise empty, the
application creates the accounts, three sample patients and five doctor
sessions the clinic starts with:

| Username | Role | Password |
|---|---|---|
| `superadmin` | Super admin | `Super@123` |
| `admin` | Clinic admin | `Sunrise@123` |
| `drkasun`, `dramali`, `drnimal`, `drkavindi` | Doctors | `Sunrise@123` |
| `nurse1`, `nurse2` | Nurses | `Sunrise@123` |
| `patadmin` | Patient administrator | `Sunrise@123` |
| `cashier` | POS cashier | `Sunrise@123` |

**Change these passwords immediately.** Seeding happens in Java rather than in
SQL because the stored hash has to be produced by the same salt-and-iterate
routine that sign-in uses; a hash pasted into a `.sql` file would stop matching
the moment that routine changed.

Check the server is healthy at `GET /sunrise/api/health`.

---

## 4. API reference

All endpoints return `{"success": true, "data": ...}` or
`{"success": false, "message": "..."}` and are prefixed with `/api`.
Authentication is a server-side `HttpSession` carried by the `JSESSIONID`
cookie; `filter.AuthFilter` rejects any unauthenticated call with 401.

### Authentication
| Method | Path | Notes |
|---|---|---|
| POST | `/auth/login` | `{username, password}` - starts a new session id |
| POST | `/auth/logout` | ends the session |
| GET | `/auth/me` | the signed-in profile plus the home page for the role |
| POST | `/auth/change-password` | `{currentPassword, newPassword}` |

### Staff accounts
| Method | Path |
|---|---|
| GET | `/users?role=DOCTOR&search=` |
| GET | `/users/doctors` |
| POST | `/users` |
| PUT | `/users/{id}` |
| PATCH | `/users/{id}/status` |
| POST | `/users/{id}/reset-password` |
| DELETE | `/users/{id}` |

### Patients
`GET /patients?search=`, `GET /patients/me`, `GET /patients/{id}`,
`GET /patients/{id}/history`, `POST /patients`, `POST /patients/{id}/login`,
`PUT /patients/{id}`, `DELETE /patients/{id}`

### Treatments
`GET /treatments`, `POST /treatments`, `PUT /treatments/{id}`, `DELETE /treatments/{id}`

### Sessions and the queue
`GET /sessions`, `GET /sessions/mine`, `GET /sessions/bookable`,
`GET /sessions/{id}`, `GET /sessions/{id}/appointments`,
`GET /sessions/{id}/queue` (public - the waiting-room screen),
`POST /sessions`, `POST /sessions/{id}/call-next`, `PUT /sessions/{id}`,
`PATCH /sessions/{id}/status`, `DELETE /sessions/{id}`

### Appointments
`GET /appointments?date=`, `GET /appointments/mine`,
`GET /appointments/search?no=APT-...`, `POST /appointments`,
`POST /appointments/{id}/check-in`, `PATCH /appointments/{id}/status`

### Billing
`GET /bills`, `GET /bills/mine`, `GET /bills/{id}`, `GET /bills/{id}/receipt`,
`GET /bills/{id}/payments`, `GET /bills/appointment/{id}`,
`POST /bills`, `POST /bills/pay`, `DELETE /bills/{id}`

### Prescriptions and reports
`GET /prescriptions?patientId=`, `GET /prescriptions/mine`,
`GET /prescriptions/appointment/{id}`, `POST /prescriptions`,
`GET /medical-reports?patientId=`, `GET /medical-reports/mine`,
`GET /medical-reports/{id}/file`, `POST /medical-reports` (multipart),
`DELETE /medical-reports/{id}`

### Management reporting
`GET /reports/dashboard`, `/reports/income`, `/reports/income-by-doctor`,
`/reports/doctor-income`, `/reports/patients`, `/reports/treatments`

### Settings
`GET /settings`, `GET /settings/public`, `PUT /settings`

### POS screens
| Method | Path | Notes |
|---|---|---|
| GET | `/pos/state` | the whole clinic document the browser caches |
| POST | `/pos/{command}` | one action from a screen |
| GET | `/pos/public` | reduced document for the portal, no session needed |
| POST | `/pos/public/{command}` | portal self-registration and booking only |

Commands: `save-user`, `set-user-status`, `save-patient`, `save-session`,
`book-appointment`, `update-appointment-status`, `cancel-by-nic`,
`create-invoice`, `process-payment`, `add-report`, `set-doctor-fee`,
`update-settings`.

Reads are one document and writes are named commands because almost every POS
screen mixes three or four entities at once - a queue row needs the
appointment, the patient, the doctor and the session together. Assembling that
server-side in a few joined queries costs one round trip instead of a dozen,
and each command still lands in a single transaction.

---

## 5. Business rules held in the database

| Object | Rule |
|---|---|
| `trg_appointment_before_insert` | allocates the next queue number and refuses a booking when the session is full or closed |
| `trg_appointment_after_update` | moves the session's "now serving" counter when a patient is called in |
| `trg_payment_after_insert` | marks a bill PAID once payments cover the total |
| `v_appointment_details` | one flat row per appointment for every list screen |
| `v_daily_income` | daily billed / collected / outstanding totals |
| `sp_income_report`, `sp_doctor_income`, `sp_patient_report` | the three management reports |
| `fn_waiting_count` | how many patients are still waiting in a session |

Concurrency note: two receptionists booking the last slot at the same moment is
resolved by the trigger, not by the Java code, so the limit holds either way.

---

## 6. Testing

```bash
mvn test
```

`src/test/java` holds JUnit 5 tests written test-first:

- `util/PasswordUtilTest` - salting, determinism, verification
- `util/JsonTest` - the hand-written JSON parser, including malformed input
- `service/PricingStrategyTest` - every billing rule and the strategy factory
- `service/AuthServiceTest` - sign-in rules against a mocked `UserDAO`
- `pos/PosIdsTest` - display ids survive the round trip to the database and back
- `pos/PosStatusTest` - the clinic's queue wording maps reversibly onto the database
- `pos/CardMaskingTest` - a full card number can never reach storage

Services take their DAOs through the constructor precisely so they can be
tested with Mockito and no database.

---

## 7. Security notes

- Passwords: 16-byte random salt, SHA-256 applied 1000 times, constant-time comparison
- Every statement is a `PreparedStatement` - no string-concatenated SQL
- Session id is regenerated on sign-in (session fixation)
- `HttpOnly` and `SameSite=Lax` on the session cookie; 30-minute idle timeout
- Uploaded file names are sanitised and renamed before being written to disk
- `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy` on every response
- Sign-in failures return the same message whether the username or the password was wrong

---

## 8. Repository layout

```
database/            schema, triggers/views/procedures, seed data
src/main/java/
  util/              config, JDBC, JSON, hashing, validation
  model/             entities (private fields, toMap() for JSON)
  dao/               data access, one class per table + DAOFactory
  service/           business rules
    pricing/         Strategy pattern
    notify/          Observer pattern
  pos/               POS facade: one state document, named commands
  servlet/           REST endpoints
  filter/            authentication and security headers
  listener/          start-up bootstrap and first-run seeding
  exception/         one class per HTTP failure mode
src/main/webapp/
  index.html         public portal (no account needed)
  login.html         staff sign-in
  dashboard.html     one page, 19 screens, menu built from the signed-in role
  js/api.js          typed client for every endpoint
  js/store.js        cache over /api/pos - the screens' data layer
  js/auth.js         server session handling
  js/dashboard.js    the screens themselves
src/test/java/       JUnit 5 tests
.github/workflows/   CI build and tagged-release deployment
```
