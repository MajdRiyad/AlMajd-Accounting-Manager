# PROJECT_REPORT.md

## AlMajd Communications Business & Accounting Manager App — Full Codebase Analysis

---

## 1. Overview

**What it is:** A native Android accounting application for a small shop/business. The Gradle root project is named `AlMajdAccounting`; the application ID is `com.almajd.accounting`.

**Who the user is (as evidenced by the code, not assumed):**
- The UI is **Arabic, RTL-only** (forced at runtime).
- Default company/owner names are hardcoded as defaults in `AppSettings.java`: company `"المجد للاتصالات" / "AlMajd Communications"`, owner `"رياض برهم" / "Riyad Barham"`. The dashboard greeting string is `"مرحباً بك، أبو المجد"`.
- These values are user-editable in the Settings screen, so the app is clearly intended for **one business**, used by its owner(s). It is a single-device, single-user tool (no accounts, no login, no multi-device sync).

**Main features (as implemented):**
- **Customers** — CRUD (store name, owner name, location, phone), search, and per-customer profile.
- **Invoices** — CRUD with multiple line items (name, quantity, unit price, subtotal), a total, a "paid" amount, and an auto-computed "remaining".
- **Payments** — CRUD of standalone payment records tied to a store (optionally linked to an invoice when created through an invoice).
- **Dashboard** — 6 stat cards (total sales, received, debts, customer count, invoice count, payment count), a 2×2 navigation grid, and the 5 most recent invoices.
- **Reports** — Sales report, Payments report, and an Accounts Ledger (per-store sales / paid / balance / last payment date), each with optional store + date-range filters (the ledger has no filter).
- **Export / Share** — invoice as a bilingual **PDF** (share/view); reports as **PDF**, **Excel** (`.xls`, HTML table), and **CSV**.
- **Settings** — company name (AR/EN), owner name (AR/EN), and phone; used in PDF headers.

---

## 2. Tech stack

| Item | Value |
|---|---|
| Language | **Java** (100%; no Kotlin files exist) |
| UI type | **XML layouts** (no Jetpack Compose) |
| minSdk | **26** (Android 8.0) |
| targetSdk | **34** |
| compileSdk | **34** (with `android.suppressUnsupportedCompileSdk=34`) |
| buildToolsVersion | 34.0.0 |
| Java source/target compat | **Java 11** |
| Gradle | **7.5** (wrapper) |
| Android Gradle Plugin | **7.4.2** |
| JDK pinned | `org.gradle.java.home=C:/Program Files/Java/jdk-16.0.2` in `gradle.properties` |
| Version | versionCode 1, versionName "1.0" |
| Application class | `com.almajd.accounting.App` |

**Dependencies (`app/build.gradle`):**
- `androidx.appcompat:appcompat:1.6.1`
- `com.google.android.material:material:1.11.0`
- `androidx.recyclerview:recyclerview:1.3.2`
- `androidx.cardview:cardview:1.0.0`
- `androidx.constraintlayout:constraintlayout:2.1.4` (no `ConstraintLayout` usage was found in the inspected layouts — **appears unused**)
- `com.itextpdf:itextpdf:5.5.13.3` (PDF generation — **iText 5**, which is AGPL/commercial-licensed)
- `junit:junit:4.13.2` (declared as `testImplementation`, but **no tests exist**)

**Build tooling / config:** Gradle wrapper; `google()` + `mavenCentral()` repositories; `FAIL_ON_PROJECT_REPOS`; `useAndroidX=true`. Only the **debug** variant has been built (`app/build/outputs/apk/debug/app-debug.apk` exists). No release signing config; `minifyEnabled false`; empty `proguard-rules.pro`.

---

## 3. Project structure

```
AlMajd Communications ... App/
├── build.gradle                     # root: AGP plugin declaration only
├── settings.gradle                  # repos + rootProject.name = AlMajdAccounting
├── gradle.properties                # jvmargs, useAndroidX, pinned JDK 16, suppress compileSdk 34
├── gradlew / gradlew.bat            # Gradle wrapper scripts
├── local.properties                 # sdk.dir (machine-specific)
├── .gitignore                       # ignores build/, .gradle/, .idea/, local.properties
├── cairo_font_extracted/            # EMPTY directory (leftover from font extraction work)
├── .vscode/settings.json            # Java build config (VS Code)
├── gradle/wrapper/                  # wrapper jar + properties
└── app/
    ├── build.gradle                 # app module config + dependencies
    ├── proguard-rules.pro           # empty
    ├── build/                       # build output (compiled classes, debug APK)
    └── src/main/
        ├── AndroidManifest.xml      # activities, permissions, FileProvider
        ├── assets/fonts/            # Cairo-Regular.ttf, Cairo-Bold.ttf (for PDF Arabic)
        ├── res/
        │   ├── layout/              # 18 XML layouts (activities + list items)
        │   ├── drawable/            # ~28 vector icons / backgrounds / badges
        │   ├── menu/                # menu_detail.xml, menu_report.xml
        │   ├── mipmap-anydpi-v26/   # adaptive launcher icon
        │   ├── values/              # strings (Arabic default), colors, dimens, styles
        │   ├── values-ar/           # only overrides app_name
        │   └── xml/file_paths.xml   # FileProvider cache-path "exports/"
        └── java/com/almajd/accounting/
            ├── App.java                         # Application: init DB, force Arabic locale
            ├── model/                           # Customer, Invoice, InvoiceItem, Payment, LedgerEntry
            ├── db/                              # DatabaseHelper + CustomerDao, InvoiceDao, PaymentDao, LedgerDao
            ├── util/                            # NumberUtils, DateUtils, AppSettings, PdfExporter, ExcelExporter, ShareHelper
            └── ui/                              # BaseActivity, DashboardActivity, SettingsActivity
                ├── customer/                    # List, Form, Detail + Adapter
                ├── invoice/                     # List, Form, Detail + Adapter + ItemAdapter
                ├── payment/                     # List, Form + Adapter
                └── report/                      # Menu, Sales, Payment, Ledger + ReportAdapter
```

`cairo_font_extracted/` is an empty leftover directory (not referenced by the build). There is no `README`, no docs folder, and no `test/` or `androidTest/` folders.

---

## 4. Architecture

**Pattern:** A simple **Activity + DAO** structure. It is **not** MVVM and **not** clean-layered; it is closest to a **light MVC** where Activities act as both Controller and View:
- `model/` — plain Java POJOs (no ORM annotations). `Invoice.items` is a `transient List<InvoiceItem>`.
- `db/` — a hand-written SQLite layer (`SQLiteOpenHelper` + DAO classes using raw `ContentValues`/`Cursor` code). No Room, no SQLDelight, no ContentProviders.
- `ui/` — one `Activity` per screen, each instantiating the DAOs it needs **directly in `onCreate`** (`new CustomerDao(this)` etc.). There is no dependency injection, no ViewModel, no repository layer, no LiveData/Flow.

**How screens and data connect:**
- Activities query DAOs **synchronously on the main thread** in `onCreate`/`onResume`, then set text on `TextView`s or feed a `RecyclerView` adapter.
- `onResume()` re-loads data (a manual refresh pattern) so returning from a form shows updated lists.
- `RecyclerView` adapters (`CustomerAdapter`, `InvoiceAdapter`, `PaymentAdapter`, `ReportAdapter`, `InvoiceItemAdapter`) each hold their own `List` and expose `updateList(...)` / `notifyDataSetChanged()`.

**Navigation:**
- Plain **`Intent`-based** navigation with `startActivity` / `startActivityForResult`. No Navigation Component, no single-Activity architecture, and no deep links (the only intent-filter is MAIN/LAUNCHER).
- Editing/creating uses **request codes** (`REQUEST_ADD_CUSTOMER`, `REQUEST_EDIT_INVOICE`, etc.) and `RESULT_OK` to trigger reloads.
- The **back stack is the navigation model**; every detail/form screen is a separate Activity registered in the manifest.
- One oddity: `CustomerDetailActivity.navigateToInvoiceList/navigateToPaymentList` load the target Activity via **reflection** (`Class.forName("com.almajd.accounting.ui.invoice.InvoiceListActivity")`) instead of a direct class reference.

---

## 5. Database

**Type:** Raw **SQLite** via `android.database.sqlite.SQLiteOpenHelper`. Database name: **`almajd_accounting.db`**, version **1**. Stored in the app's private internal storage (`/data/data/com.almajd.accounting/databases/`). Singleton access via `DatabaseHelper.getInstance(context)`; `PRAGMA foreign_keys = ON` is enabled in `onOpen`.

### Tables

**`customers`**

| Column | Type | Notes |
|---|---|---|
| `store_name` | TEXT | **PRIMARY KEY** (business key, not an auto-id) |
| `owner_name` | TEXT | NOT NULL |
| `location` | TEXT | nullable |
| `phone` | TEXT | nullable |
| `created_at` | TEXT | DEFAULT `CURRENT_TIMESTAMP` |

**`invoices`**

| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT |
| `store_name` | TEXT | NOT NULL, FK → `customers.store_name` ON UPDATE CASCADE / ON DELETE CASCADE |
| `invoice_date` | TEXT | NOT NULL (`yyyy-MM-dd`) |
| `total_amount` | REAL | DEFAULT 0 |
| `paid_amount` | REAL | DEFAULT 0 |
| `remaining` | REAL | DEFAULT 0 (stored, not computed on read) |
| `notes` | TEXT | nullable |
| `created_at` | TEXT | DEFAULT `CURRENT_TIMESTAMP` |

**`invoice_items`**

| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT |
| `invoice_id` | INTEGER | NOT NULL, FK → `invoices.id` ON DELETE CASCADE |
| `item_name` | TEXT | NOT NULL |
| `quantity` | INTEGER | DEFAULT 1 |
| `unit_price` | REAL | DEFAULT 0 |
| `subtotal` | REAL | DEFAULT 0 |

**`payments`**

| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT |
| `store_name` | TEXT | NOT NULL, FK → `customers.store_name` ON UPDATE CASCADE / ON DELETE CASCADE |
| `amount` | REAL | NOT NULL |
| `payment_date` | TEXT | NOT NULL |
| `notes` | TEXT | nullable |
| `invoice_id` | INTEGER | nullable, FK → `invoices.id` ON DELETE SET NULL |
| `created_at` | TEXT | DEFAULT `CURRENT_TIMESTAMP` |

### View

**`ledger_view`** — `customers LEFT JOIN invoices`, grouped by store, exposing:
- `grand_total_sales` = `COALESCE(SUM(invoice.total_amount),0)`
- `total_paid` = correlated subquery: `SUM(payments.amount)` for that store
- `balance` = `grand_total_sales − total_paid`
- `last_payment_date` = `MAX(payments.payment_date)` for that store

### Relations

Customer (`store_name`) → 1..N Invoices; Invoice → 1..N InvoiceItems; Customer → 1..N Payments; Payment → 0..1 Invoice.

### Migrations

There is **only version 1**. `onUpgrade(...)` **drops the view and all four tables and re-runs `onCreate`** — i.e., a **destructive migration that would delete all data** if the version were ever bumped.

---

## 6. Screens and features

Each is an `Activity` (all extend `BaseActivity`, which forces the Arabic/RTL locale). Listed in manifest order:

| # | Activity | Purpose | Navigation in | Navigation out |
|---|---|---|---|---|
| 1 | `ui.DashboardActivity` (launcher) | Hero header, 6 stat cards, 2×2 nav grid, 5 recent invoices, settings gear, "view all" | App launch | → CustomerList, InvoiceForm, PaymentForm, ReportMenu, Settings, InvoiceList |
| 2 | `ui.customer.CustomerListActivity` | Customer list + search (SearchView), FAB add, long-press Edit/Delete | Dashboard | → CustomerForm (add/edit), CustomerDetail |
| 3 | `ui.customer.CustomerFormActivity` | Add/Edit customer; store name is the PK and disabled in edit mode; validates required owner + store; uniqueness check on add | List (add/edit) | returns `RESULT_OK` |
| 4 | `ui.customer.CustomerDetailActivity` | Customer info, balance summary (total sales / total paid / balance), last 5 invoices, last 5 payments, Edit/Delete menu | List | → CustomerForm, InvoiceList (filtered), PaymentList (filtered) |
| 5 | `ui.invoice.InvoiceListActivity` | Invoice list, optional store filter via extra, FAB add, long-press Edit/Delete | Dashboard / CustomerDetail | → InvoiceForm, InvoiceDetail |
| 6 | `ui.invoice.InvoiceFormActivity` | Create/Edit invoice: store spinner, date picker, dynamic line items, live total/remaining, paid field | InvoiceList / Dashboard / CustomerDetail | returns `RESULT_OK` |
| 7 | `ui.invoice.InvoiceDetailActivity` | Full invoice: bilingual header, store/date, items table, totals, Edit/Delete/Share-PDF/Export-PDF menu | InvoiceList | → InvoiceForm |
| 8 | `ui.payment.PaymentListActivity` | Payment list (optional store filter), FAB add, long-press Edit/Delete; tap opens edit | Dashboard / CustomerDetail | → PaymentForm |
| 9 | `ui.payment.PaymentFormActivity` | Create/Edit payment: store spinner, amount, date, notes | PaymentList / Dashboard | returns `RESULT_OK` |
| 10 | `ui.report.ReportMenuActivity` | Three buttons: Sales History, Payment History, Accounts Ledger | Dashboard | → Sales/Payment/Ledger reports |
| 11 | `ui.report.SalesReportActivity` | Filter (store + from/to dates), invoice rows, grand total; export PDF/Excel/CSV | ReportMenu | back |
| 12 | `ui.report.PaymentReportActivity` | Filter (store + dates), payment rows, total; export PDF/Excel/CSV | ReportMenu | back |
| 13 | `ui.report.LedgerReportActivity` | All stores: sales/balance rows + footer totals (sales, paid, balance); export PDF/Excel/CSV | ReportMenu | back |
| 14 | `ui.SettingsActivity` | Edit company/owner names (AR/EN) + phone (SharedPreferences) | Dashboard gear | back |

`BaseActivity` is abstract and only overrides `attachBaseContext` to force Arabic. `App` does the same and calls `DatabaseHelper.getInstance()`.

---

## 7. Accounting logic

**Where money math lives:**
- **Line subtotal & invoice total:** `InvoiceItemAdapter.updateSubtotal()` (`qty * price`) and `InvoiceFormActivity.validateAndSave()` (lines ~332–338), which recomputes `subtotal = quantity * unitPrice` and `total = Σ subtotals`.
- **Remaining:** `InvoiceFormActivity.recalculateRemaining()` and again at save: `remaining = total − paid`, **clamped to 0 if negative**.
- **Linked payment rule:** `InvoiceDao.insert()` / `update()` create a `payments` row when `paid_amount > 0`, with `payment_date = invoice_date` and note `"Payment linked to invoice #<id>"`.
- **Balances / debts / ledger:** computed in **SQL** inside `ledger_view` (`balance = Σ invoice.total − Σ payments.amount`) and aggregated by `LedgerDao.getTotalOutstandingBalance()`.
- **Dashboard totals:** `totalSales = InvoiceDao.getTotalSales()` (Σ invoice totals); `received = PaymentDao.getTotalPayments()` (Σ payments); `debts = LedgerDao.getTotalOutstandingBalance()` (Σ per-store balances).
- **Report totals:** summed in Java per displayed row (Sales = Σ totals; Payments = Σ amounts; Ledger = Σ columns).

**Number / currency handling:**
- All amounts are **`double`** in Java and **`REAL`** in SQLite (no `BigDecimal`).
- Display formatting is centralized in `NumberUtils` with `DecimalFormat("#,##0.00")` and a **hardcoded NIS symbol `₪`**. There is **no currency setting and no multi-currency support**.
- Input parsing (`NumberUtils.parseAmount`) strips `₪` and commas and calls `Double.parseDouble`, returning `0.0` on failure.

**Specific correctness concerns found:**
1. **Stale invoice `paid_amount` / `remaining`.** The invoice stores a snapshot of `paid_amount` and `remaining` at save time. Payments added later through the Payments screen are **not** reflected back into the invoice header. The **ledger** stays consistent (`Σ payments`), but the **invoice detail/list "remaining" and the "paid/partial/unpaid" badge can be wrong** after later standalone payments.
2. **The auto-created "linked payment" is a normal, editable/deletable payment.** It shows up in the Payments list (with the "Payment linked to invoice #…" note). A user can edit or delete it; editing it via `PaymentFormActivity` **drops `invoice_id`** (the form never sets it), and changing/deleting it **desyncs the invoice header** from actual payments.
3. **Asymmetric overpayment handling.** Invoice `remaining` is **clamped at 0**, but the ledger `balance` is **not clamped** and can be negative (customer overpaid). The dashboard "debts" (`Σ balance`) can therefore be pulled down by overpaid stores, and the Sales report colors by `remaining > 0` while the Ledger colors by `balance > 0`.
4. **`double` / `REAL` for money.** Binary floating-point rounding can accumulate (e.g., 0.1 + 0.2), so totals can be off by fractions of a cent (mostly hidden by the 2-decimal display format). No explicit rounding to 2 decimals on storage.
5. **Forced Arabic locale vs. number formatting (potential).** `App.forceArabicLocale()` sets `Locale.setDefault(new Locale("ar"))`. `NumberUtils`'s `DecimalFormat` is created from the default locale, and for `ar` ICU may render **Arabic-Indic digits (٠–٩)**. Input fields use ASCII digits and `parseAmount` expects ASCII. I did not run the app, so whether displayed amounts actually use Arabic-Indic digits is **Unclear**, but the mismatch risk exists.
6. **Date-range filter requires BOTH dates.** Sales/Payments reports only apply a date filter when *both* "from" and "to" are filled; filling only one silently ignores the date entirely.
7. **Deletion is destructive.** Deleting a customer cascades to invoices → items and payments. The confirmation message is a generic "هل أنت متأكد من الحذف؟" with no per-record details.

---

## 8. Backup and export

**Backup / restore:** No in-app **backup, restore, or database export/import feature exists**. The only backup is Android's system auto-backup — `AndroidManifest` has `android:allowBackup="true"` and **no `fullBackupContent`/`dataExtractionRules`**, so the default rules apply (the SQLite DB and SharedPreferences may be included in Google cloud backup). There is no manual backup UI, no "export data" of the database, and no restore path.

**Export / share (implemented):**
- **Invoice PDF** (`PdfExporter.generateInvoicePdf`) — bilingual AR/EN header with a vertical divider, store/date/invoice-number block, RTL items table (columns reversed in code so they read right-to-left), and a right-aligned totals block. Uses the embedded **Cairo** TTF via iText `BaseFont.IDENTITY_H`. Reachable from `InvoiceDetailActivity` menu: **Share** (ACTION_SEND via `FileProvider`) and **Export PDF** (ACTION_VIEW).
- **Report PDF** (`PdfExporter.generateReportPdf`) — A4 landscape, reversed headers/rows for RTL, footer summary lines.
- **Excel** (`ExcelExporter.generateExcel`) — writes an **HTML table with an `.xls` extension** (Excel opens it natively) and shares it. **Note:** callers pass the MIME type for `.xlsx` (`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`) even though the file is `.xls`/HTML — a **MIME/extension mismatch** that can confuse target apps.
- **CSV** (`ExcelExporter.generateCsv`) — UTF-8 with a BOM, RFC-style quoting; shared as `text/csv`.
- All exports are written to **`cacheDir/exports/`**, exposed to `FileProvider` by `res/xml/file_paths.xml` (`<cache-path name="shared_files" path="exports/"/>`), and shared via `ShareHelper.shareFile(...)` (ACTION_SEND + `FLAG_GRANT_READ_URI_PERMISSION`).
- **Font caching detail:** `PdfExporter` caches fonts in `cacheDir` and only copies from assets when the static field is null (despite a git commit message saying it should always overwrite).

---

## 9. Permissions and security

**Declared permissions (`AndroidManifest.xml`):**
- `android.permission.WRITE_EXTERNAL_STORAGE` (`maxSdkVersion=28`)
- `android.permission.READ_EXTERNAL_STORAGE` (`maxSdkVersion=32`)

These appear **unnecessary**: all file output goes to the app's private `cacheDir`, shared via `FileProvider`. There is **no runtime permission request code anywhere**, and no `requestLegacyExternalStorage`. No other permissions (no INTERNET, CAMERA, etc.). The `FileProvider` is `exported="false"` with `grantUriPermissions="true"` — correct.

**Sensitive data storage:**
- Customer names, phone numbers, and all financial data are stored **unencrypted** in the app-private SQLite DB (`almajd_accounting.db`).
- Company/owner/phone settings are in an unencrypted `SharedPreferences` file (`almajd_settings`).
- Exports are written to the private cache directory (could persist there).

**Lock / login:** **No login, PIN, biometric, or app lock exists.** Anyone with the unlocked device can open and read/edit all data. Combined with `allowBackup="true"`, the data can also leave the device via Google cloud backup.

---

## 10. Code quality

**Strengths:**
- Consistent package structure (`model` / `db` / `util` / `ui`), clear naming, and mostly present Javadoc/comment headers on classes and non-trivial methods.
- DAOs diligently **close cursors** (try/finally) and use `getColumnIndexOrThrow`.
- Money math, formatting, and date conversion are centralized in small helper classes (`NumberUtils`, `DateUtils`).
- `InvoiceDao` uses **transactions** for insert/update and rolls back properly; invoice delete also removes linked payments.
- RTL/Arabic handling is deliberate (locale forcing, Cairo font for PDF, reversed PDF columns).
- `InvoiceItemAdapter` carefully manages `TextWatcher` add/remove with a binding flag to avoid recycling bugs.

**Problems / rough edges:**
- **Massive duplication across the three report Activities** — `SalesReportActivity`, `PaymentReportActivity`, and `LedgerReportActivity` are near-identical (spinner population, filter logic, `buildExportData`, and `exportPdf/Excel/Csv` copy-pasted). The same is true of the list Activities' delete popup/confirm patterns.
- **No architecture separation:** Activities hold business logic and call DAOs directly; no ViewModel/Repository.
- **All DB I/O on the main thread** (no async/coroutines/executor) — fine for tiny datasets, risky as data grows.
- **N+1 queries:** `InvoiceDao.getAll/getByStoreName/getByDateRange` call `getItemsForInvoice()` once per invoice row.
- **Ignored return values:** e.g., `CustomerListActivity` and `CustomerDetailActivity` call `customerDao.delete(...)`/`update(...)` and always show a success toast regardless of the result; `PaymentDao.update` result is ignored.
- **Hardcoded strings in code:** PDF error toasts (`"Error generating PDF: "`), the linked-payment note (`"Payment linked to invoice #"`), the default company/owner names (escaped `\uXXXX` literals) in `AppSettings`, and `"-"`/`"—"` placeholders. Most UI strings are correctly in `strings.xml`, but the **default file is itself Arabic** and `values-ar/` only overrides `app_name`, so there is effectively **no real localization** (English is not supported despite English fields).
- **Dead/unused resources:** `menu_report.xml` (report screens build their menus in code), several unused strings (`tab_*`, `pdf_header_*`, `pdf_phone_placeholder`, `export`, `outstanding_balance`, etc.), and likely the `constraintlayout` dependency.
- **Menus built twice (potential):** Report layouts and `activity_invoice_detail.xml` set `app:menu="@menu/menu_report"` / `menu_detail` on the toolbar **while** the Activities also populate menus programmatically (`onCreateOptionsMenu`). This can produce **duplicate overflow items**, and the XML-item IDs (e.g., `action_export_pdf`) are not handled by `onOptionsItemSelected` (which only handles ids `1/2/3`). Whether duplicates actually appear is **Unclear**, but the setup is inconsistent.
- **Tests:** none. Only a JUnit dependency is declared; there are no `test/` or `androidTest/` sources, so no unit, instrumentation, or database tests.

---

## 11. Bugs and risks

1. **Stale per-invoice paid/remaining** (see §7.1) — user-visible incorrect "remaining" and status badges after later payments.
2. **Invoice-linked payment can be independently edited/deleted**, desyncing the invoice (and dropping the `invoice_id` link on edit) — data-integrity risk.
3. **Destructive upgrade path:** `onUpgrade` drops all tables → any future DB version bump **wipes all data**.
4. **No backup/restore** combined with aggressive cascade deletes → **permanent data loss** risk.
5. **Main-thread DB access + N+1 queries** → ANR/jank risk on larger datasets (hundreds/thousands of invoices), especially the reports and `InvoiceDao.getAll()`.
6. **Currency/rounding:** `double`/`REAL` money; no rounding to 2 decimals at write time; only the NIS symbol is hardcoded.
7. **Possible Arabic-Indic digit display** vs ASCII input (locale + `DecimalFormat`) — potential confusion in amounts (**Unclear** without running).
8. **Dashboard recent-invoice date is unformatted** — `DashboardActivity` sets `tv_recent_date` to the raw `yyyy-MM-dd` string, while every other screen uses `DateUtils.formatForDisplay` (`dd/MM/yyyy`). Inconsistent.
9. **Date filter silently ignored** when only one of from/to is set (Sales/Payments reports).
10. **MIME/extension mismatch** for Excel exports (`.xls`/HTML labelled as `.xlsx`).
11. **Reflection-based navigation** in `CustomerDetailActivity` — unnecessary and fragile (`Class.forName`).
12. **No null/edge validation asymmetries:** `InvoiceFormActivity` silently treats an invalid paid amount as 0; `remaining` clamped but `balance` not.
13. **Duplicate/ineffective toolbar menus** possible (see §10).
14. **Build/environment fragility:** `gradle.properties` pins a **machine-specific JDK path** (`C:/Program Files/Java/jdk-16.0.2`) and `local.properties` a machine-specific SDK path; AGP **7.4.2** with **compileSdk 34** is outside AGP 7.4's officially supported range (workaround via `suppressUnsupportedCompileSdk`). The project may not build on another machine without edits. Only a **debug** build exists (no signing config for release).
15. **iText 5 (AGPL)** — a **licensing risk** for a distributed app.
16. **Unused storage permissions** declared — may trigger Play/privacy scrutiny.
17. **Empty `cairo_font_extracted/`** directory is clutter.

---

## 12. Missing features

Reasonable gaps for a small shop accounting system:
- **Purchases / suppliers / vendor debts** (the app is sales-only).
- **Expenses** (rent, salaries, utilities) and a **profit/loss** view (needs cost data).
- **Products / price catalog** (item names are free text; no reusable price list).
- **Inventory / stock quantity.**
- **Receipt / credit vouchers** and **refunds / credit notes.**
- **Proper per-invoice payment application & reconciliation** (part payments list per invoice).
- **Opening balances** and **customer statements** (A4 printable ledger statement per customer).
- **Tax / VAT** handling.
- **Multiple currencies.**
- **Backup & restore of the database** (export/import a file) and **CSV/XLSX import** of existing data.
- **Print/PDF for the customer ledger** (only reports/invoice have PDF).
- **Search/pagination for invoices and payments** (only customers have search).
- **App lock / PIN / biometric** protection.
- **Periodic summaries** (monthly totals, date-preset ranges: today/week/month/year).
- **Invoice numbering control** (currently just the auto-increment `id`).
- **Undo / soft-delete / archive.**
- **Real localization (English)** and a currency/locale setting.

---

## 13. Improvement suggestions (by priority)

**High**

1. **Fix per-invoice payment sync** — derive paid/remaining from the `payments` table (or update the invoice when payments change), and stop treating the auto-created linked payment as a normal user-editable payment. *Why:* prevents wrong balances (the app's core purpose). *Difficulty:* Medium.
2. **Add in-app backup/restore** (export/import the SQLite file to a user-chosen location). *Why:* no recovery path today; cascade deletes + destructive migration can lose everything. *Difficulty:* Medium.
3. **Replace destructive `onUpgrade` with real migrations** (and handle the not-yet-shipped v1→v2 safely). *Why:* prevents catastrophic data loss on schema changes. *Difficulty:* Medium.
4. **Use `BigDecimal` (or store minor units as integers) and round to 2 decimals on write.** *Why:* correct money math. *Difficulty:* Medium–High (touches DAOs, models, exporters).
5. **Move DB operations off the UI thread** (Executor / AsyncTask / ViewModel + LiveData). *Why:* ANR risk, responsiveness. *Difficulty:* Medium–High.

**Medium**

6. **Deduplicate the report Activities** into a shared base/adapter ("build rows + export" abstraction). *Why:* maintainability; three copies today. *Difficulty:* Medium.
7. **Resolve the locale/number-format mismatch** — force `Locale.US`/`latn` digits in `NumberUtils`, or accept localized digits consistently; verify on device. *Why:* avoid confusing/mis-parsed amounts. *Difficulty:* Low–Medium.
8. **Consolidate the menu setup** (use either XML menus or programmatic, not both) and handle all menu item IDs. *Why:* avoid duplicate/dead menu items. *Difficulty:* Low.
9. **Add a PIN/biometric app lock (and consider the `allowBackup` policy).** *Why:* financial/customer data on a shared device. *Difficulty:* Low–Medium.
10. **Fix Excel export** — either produce a real `.xlsx`/`.xls` and match the MIME, or clearly label it as HTML. *Why:* compatibility with spreadsheet apps. *Difficulty:* Low.
11. **Add search for invoices/payments** and a store-scoped statement/PDF. *Why:* usability as data grows. *Difficulty:* Medium.
12. **Remove unused permissions and dead resources; delete the empty `cairo_font_extracted/` dir.** *Why:* cleanliness/privacy. *Difficulty:* Low.
13. **Fix the dashboard recent-date formatting** to use `DateUtils.formatForDisplay`. *Why:* consistency. *Difficulty:* Low (one line).
14. **Make the date-range filter apply when only one date is set (or validate both).** *Why:* avoids silently ignored filters. *Difficulty:* Low.
15. **Pin JDK/SDK via toolchains / document requirements; add a release signing config.** *Why:* reproducible builds. *Difficulty:* Low–Medium.

**Low**

16. Add unit tests for `NumberUtils`, `DateUtils`, and the ledger SQL. *Why:* guard money math. *Difficulty:* Low–Medium.
17. Replace reflection navigation with direct `Intent`s. *Why:* clarity. *Difficulty:* Low.
18. Add date presets (today/week/month/year) and period net-total views. *Why:* faster daily use. *Difficulty:* Low–Medium.
19. Reconsider iText licensing (or switch to a permissive PDF library). *Why:* legal. *Difficulty:* Medium.
20. Real English localization if needed. *Why:* only if required. *Difficulty:* Medium.

---

## 14. Questions for the project owner

1. **Currency:** Is the currency always **NIS (₪)**? Is multi-currency ever needed? (Currently hardcoded.)
2. **Purchases/expenses:** Is the app intentionally **sales-only**, or should purchases, suppliers, and expenses be added (and a profit calculation)?
3. **Overpayments/advances:** If a customer pays more than an invoice, how should that be represented — a credit, a balance carried forward, or rejected?
4. **Partial payments:** Should multiple partial payments per invoice be tracked and shown per invoice, rather than a single "paid" value?
5. **The auto-generated linked payment:** Is it intended for the user to see/edit/delete it in the Payments list? If not, should it be hidden?
6. **Rounding/digits:** On real devices, do amounts display with Western (0-9) or Arabic-Indic (٠-٩) digits? Should NIS be shown as `₪` in both AR and EN contexts?
7. **Backup:** Do you need a manual backup/restore/export-to-file feature, or is Google auto-backup sufficient?
8. **Security:** Is a PIN/biometric lock required? Should cloud backup of the database be disabled?
9. **Data history:** Is anything already in production use (real invoices/customers) that a schema change must preserve?
10. **Distribution:** Will this be published to Google Play (affects the iText AGPL license, target SDK, and permission review) or used privately/sideloaded?
11. **Device/environment:** Must it build on machines other than this one (the pinned JDK 16 path and AGP 7.4.2 + compileSdk 34 combination are machine/version sensitive)?
12. **Invoice numbering:** Should invoice numbers follow a custom format/sequence rather than the raw auto-increment ID?
13. **English UI:** Is an English interface needed, or is Arabic-only correct?

---

## Appendix — Important files (one line each)

**Build / config**

- `build.gradle` — Root Gradle file declaring the Android Gradle Plugin 7.4.2.
- `settings.gradle` — Repositories and `rootProject.name = "AlMajdAccounting"`, includes `:app`.
- `gradle.properties` — JVM args, AndroidX flag, pinned JDK 16, compileSdk-34 suppression.
- `app/build.gradle` — App module: SDK levels, Java 11, dependencies (AndroidX, Material, iText).
- `gradle/wrapper/gradle-wrapper.properties` — Gradle 7.5 distribution config.
- `local.properties` — Machine-specific Android SDK path (should be git-ignored).
- `.gitignore` — Ignores build output, IDE files, local.properties.
- `app/proguard-rules.pro` — Empty (minify disabled).
- `.vscode/settings.json` — VS Code Java build setting.

**Manifest / assets / resources**

- `app/src/main/AndroidManifest.xml` — Declares storage permissions, all 14 activities, application class, FileProvider.
- `app/src/main/res/xml/file_paths.xml` — FileProvider cache-path `exports/`.
- `app/src/main/assets/fonts/Cairo-Regular.ttf` / `Cairo-Bold.ttf` — Arabic-capable fonts embedded in PDFs.
- `app/src/main/res/values/strings.xml` — All UI strings (default is Arabic).
- `app/src/main/res/values-ar/strings.xml` — Overrides only `app_name`.
- `app/src/main/res/values/colors.xml` — Palette (navy/purple/green/red, badge/status colors).
- `app/src/main/res/values/styles.xml` — App theme (light, RTL) and shared component styles.
- `app/src/main/res/values/dimens.xml` — Spacing/size/radius tokens.
- `app/src/main/res/menu/menu_detail.xml` — Invoice detail menu (edit/delete/share/export) — largely unused via XML.
- `app/src/main/res/menu/menu_report.xml` — Report export menu (declared but reports build menus in code).

**Java — core**

- `App.java` — `Application`: initializes the DB singleton and forces the Arabic locale.
- `model/Customer.java` — Customer POJO (store/owner/location/phone/createdAt).
- `model/Invoice.java` — Invoice POJO (amounts as `double`, transient item list).
- `model/InvoiceItem.java` — Line-item POJO (qty, unit price, subtotal).
- `model/Payment.java` — Payment POJO (amount, date, optional `invoiceId`).
- `model/LedgerEntry.java` — Read-only ledger row POJO (sales, paid, balance, last payment).

**Java — database**

- `db/DatabaseHelper.java` — SQLiteOpenHelper: schema, `ledger_view`, version 1, destructive `onUpgrade`, FK pragma.
- `db/CustomerDao.java` — Customer CRUD, search, count.
- `db/InvoiceDao.java` — Invoice CRUD (transactional), item loading, date/store queries, totals; creates linked payments.
- `db/PaymentDao.java` — Payment CRUD, filtering, totals.
- `db/LedgerDao.java` — Queries `ledger_view`, total outstanding balance.

**Java — utilities**

- `util/NumberUtils.java` — Currency formatting (`#,##0.00` + hardcoded ₪) and amount parsing.
- `util/DateUtils.java` — DB/display date formats, date-picker helper.
- `util/AppSettings.java` — SharedPreferences for company/owner/phone with hardcoded defaults.
- `util/PdfExporter.java` — Bilingual AR/EN invoice & report PDF generation via iText/Cairo.
- `util/ExcelExporter.java` — HTML-based `.xls` and UTF-8 CSV export.
- `util/ShareHelper.java` — FileProvider-based share intents for PDF/Excel/CSV.

**Java — UI**

- `ui/BaseActivity.java` — Abstract base forcing the Arabic/RTL locale.
- `ui/DashboardActivity.java` — Launcher/home: stats, nav grid, recent invoices.
- `ui/SettingsActivity.java` — Edit and save company/owner/phone settings.
- `ui/customer/CustomerListActivity.java` — Customer list, search, add, edit/delete.
- `ui/customer/CustomerFormActivity.java` — Add/edit customer form with validation.
- `ui/customer/CustomerDetailActivity.java` — Customer profile, balance summary, recent invoices/payments, mini-adapters.
- `ui/customer/CustomerAdapter.java` — RecyclerView adapter for customers.
- `ui/invoice/InvoiceListActivity.java` — Invoice list with optional store filter.
- `ui/invoice/InvoiceFormActivity.java` — Create/edit invoice with dynamic items and totals.
- `ui/invoice/InvoiceDetailActivity.java` — Invoice detail view with PDF share/export.
- `ui/invoice/InvoiceAdapter.java` — RecyclerView adapter for invoices (status badges).
- `ui/invoice/InvoiceItemAdapter.java` — Editable line-item adapter with live subtotal/total.
- `ui/payment/PaymentListActivity.java` — Payment list with optional store filter.
- `ui/payment/PaymentFormActivity.java` — Create/edit payment form.
- `ui/payment/PaymentAdapter.java` — RecyclerView adapter for payments.
- `ui/report/ReportMenuActivity.java` — Menu linking to the three reports.
- `ui/report/SalesReportActivity.java` — Sales report with filters and PDF/Excel/CSV export.
- `ui/report/PaymentReportActivity.java` — Payments report with filters and export.
- `ui/report/LedgerReportActivity.java` — Accounts ledger with aggregate totals and export.
- `ui/report/ReportAdapter.java` — Generic three-row report adapter with colored value.

**Layouts (key)**

- `layout/activity_dashboard.xml` — Dashboard screen.
- `layout/activity_customer_list.xml` / `activity_customer_form.xml` / `activity_customer_detail.xml` — Customer screens.
- `layout/activity_invoice_list.xml` / `activity_invoice_form.xml` / `activity_invoice_detail.xml` — Invoice screens.
- `layout/activity_payment_list.xml` / `activity_payment_form.xml` — Payment screens.
- `layout/activity_report_menu.xml` / `activity_sales_report.xml` / `activity_payment_report.xml` / `activity_ledger_report.xml` — Report screens.
- `layout/activity_settings.xml` — Settings screen.
- `layout/item_customer.xml`, `item_invoice.xml`, `item_payment.xml`, `item_invoice_line.xml`, `item_report_row.xml`, `item_recent_invoice.xml` — RecyclerView row layouts.
