# CHANGES

This document describes the changes made to the AlMajd accounting app for the
"derive, don't store" invoice/payment refactor.

## Goal

Invoices previously stored a snapshot of `paid_amount` / `remaining` at save
time, so payments added later did not update them. The invoice's paid amount,
remaining amount and status are now **derived** from the linked payments.

## What changed

### A. Derive, don't store
- `invoices.paid_amount` and `invoices.remaining` columns were **removed** from
  the schema. They are no longer written on insert/update.
- Added an SQL view `invoice_view` that computes, in a single aggregated query:
  - `paid_amount` = `SUM(payments.amount WHERE invoice_id = invoice.id)`
  - `remaining`   = `MAX(total_amount - paid_amount, 0)`
  - `status`      = `paid` / `partial` / `unpaid`
  So listing invoices never runs N+1 queries **for payments** (one view query).
- `Invoice` still exposes `getPaidAmount()`, `getRemaining()` and a new
  `getStatus()` (enum `InvoiceStatus`), but these are derived, in-memory values
  populated from the view.
- All invoice reads (`getAll`, `getByStoreName`, `getByDateRange`,
  `getByStoreAndDateRange`, `getById`, `getRecent`) now read from `invoice_view`.
- `InvoiceAdapter` shows the badge from the derived status.

### B. Invoice form
- The optional "paid now" field is kept. On **create**, if `paid now > 0` a
  normal payment linked to the new invoice (same date) is inserted inside the
  same transaction as the invoice and its items.
- On **edit**, the "paid now" row is hidden and no payment is created or
  modified; the form never touches existing payments. The remaining displayed in
  edit mode is `max(total - derivedPaid, 0)`.
- The form no longer references the removed columns.

### C. Payment form
- Added an invoice selector (`spinner_invoice`): the first option is
  "On account" (no invoice), followed by the selected customer's invoices that
  still have `remaining > 0`, shown as `فاتورة #id - date - remaining`.
- Changing the customer refreshes the invoice list.
- Editing a payment preserves its `invoice_id`: the linked invoice is also added
  to the list (and selected) even if it is already fully paid.
- `PaymentDao` gained `getByInvoiceId`, `getPaidForInvoice` and
  `getPaidForInvoiceExcluding`.

### D. Overpayment against a specific invoice
- If the entered amount is greater than the invoice's remaining (excluding the
  payment being edited), a confirmation dialog appears:
  - **Confirm**: the remaining is applied to the invoice and the excess is
    recorded as a separate **on-account** payment (two rows, written in a single
    transaction: `PaymentDao.insertTwo` on create, `PaymentDao.updateAndInsert`
    on edit).
  - **Cancel**: nothing is saved and the form stays open.
- Amounts are never silently dropped or altered.

### E. Invoice detail screen
- Added a "payments on this invoice" card listing date, amount and notes of the
  payments linked to the invoice, plus an "Add payment" button that opens the
  payment form pre-filled with this customer and invoice.
- Paid / remaining are still shown (now derived).

### F. Deleting an invoice
- `InvoiceDao.delete` no longer deletes linked payments. It only deletes the
  invoice; the payments FK `ON DELETE SET NULL` turns their `invoice_id` to NULL,
  so the money stays in the customer's balance as on-account payments.

### G. Customer balance wording
- New helper `BalanceUtils.format(context, balance)`:
  - `> 0` → "عليه <amount>" (customer owes us)
  - `< 0` → "له عندنا <absolute amount>" (customer credit)
  - `0`   → "مسدد" (settled)
- Applied in: customer detail, ledger report rows + footer, and ledger
  PDF/Excel/CSV export data. A bare negative number is never shown.
- Dashboard "debts" now uses `LedgerDao.getTotalDebt()` = sum of only the
  **positive** balances, and a new compact "رصيد دائن للعملاء" figure uses
  `LedgerDao.getTotalCredit()` = sum of the absolute **negative** balances.

### H. Rounding
- Added a single helper `NumberUtils.round2(double)` (BigDecimal, HALF_UP).
- Used when computing subtotals, invoice totals, "paid now", and when saving
  payments (single, linked and split). `double` storage is kept for now.

### I. Strings
- All new user-facing text was added to `res/values/strings.xml` (Arabic
  default). No new hardcoded user-facing strings were added in Java.

## Files touched

- `app/src/main/java/com/almajd/accounting/db/DatabaseHelper.java`
- `app/src/main/java/com/almajd/accounting/db/InvoiceDao.java`
- `app/src/main/java/com/almajd/accounting/db/PaymentDao.java`
- `app/src/main/java/com/almajd/accounting/db/LedgerDao.java`
- `app/src/main/java/com/almajd/accounting/model/Invoice.java`
- `app/src/main/java/com/almajd/accounting/model/InvoiceStatus.java` (new)
- `app/src/main/java/com/almajd/accounting/util/NumberUtils.java`
- `app/src/main/java/com/almajd/accounting/util/BalanceUtils.java` (new)
- `app/src/main/java/com/almajd/accounting/ui/invoice/InvoiceFormActivity.java`
- `app/src/main/java/com/almajd/accounting/ui/invoice/InvoiceItemAdapter.java`
- `app/src/main/java/com/almajd/accounting/ui/invoice/InvoiceAdapter.java`
- `app/src/main/java/com/almajd/accounting/ui/invoice/InvoiceDetailActivity.java`
- `app/src/main/java/com/almajd/accounting/ui/payment/PaymentFormActivity.java`
- `app/src/main/java/com/almajd/accounting/ui/customer/CustomerDetailActivity.java`
- `app/src/main/java/com/almajd/accounting/ui/DashboardActivity.java`
- `app/src/main/java/com/almajd/accounting/ui/report/LedgerReportActivity.java`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/layout/activity_invoice_form.xml`
- `app/src/main/res/layout/activity_invoice_detail.xml`
- `app/src/main/res/layout/activity_payment_form.xml`
- `app/src/main/res/layout/activity_dashboard.xml`

## Schema changes

- `DATABASE_VERSION` bumped from 1 to 2.
- `invoices`: removed columns `paid_amount` and `remaining`.
- New view `invoice_view` (derived `paid_amount`, `remaining`, `status`).
- `payments.invoice_id` keeps its existing `ON DELETE SET NULL` foreign key;
  the code that manually deleted linked payments was removed.
- `onUpgrade` is still destructive (drops views + tables and recreates), which is
  acceptable for this step only; real migrations are expected in a later task.
- `PRAGMA foreign_keys = ON` is set in `onOpen` (existing behavior), which the
  `ON DELETE SET NULL` behavior relies on.

## Deliberately not done

- No real (non-destructive) migrations yet (agreed for this step).
- Invoice lists still load line items with one query per invoice (N+1 **for
  items**, unchanged and out of scope; the payments N+1 was the target and is
  fixed by the view).
- No changes to unrelated code (reports filters, PDF/invoice layout,
  `SalesReportActivity`/`PaymentReportActivity` logic beyond what derived values
  already provide).
- Excel export format (HTML table with `.xls` name / xlsx MIME) left untouched.
- No unit/instrumentation tests added.

## Build

`gradlew.bat assembleDebug` builds successfully (AGP 7.4.2, Gradle 7.5).
Note: the project path contains `&`, which breaks the Gradle batch wrapper when
invoked directly; the build was verified by mapping a drive letter to the folder
first. This is an environment quirk, not a code issue.

---

# CHANGES — migrations, backup, restore, weekly reminder

This section describes the next task: real (non-destructive) database
migrations, in-app backup and restore, and a weekly backup reminder banner.
Nothing from the previous section was removed.

## Goal

From version 2 onward a schema change must **never wipe data**, and the owner
must be able to save their data to a file, keep it outside the app, and restore
it on a new device. Manual backup + in-app reminder only (no scheduled backups,
no cloud sync, no new permissions).

## A. Real migrations (`DatabaseHelper`)

- One constant is the source of truth: `DatabaseHelper.SCHEMA_VERSION` (= 2). It
  is passed to `SQLiteOpenHelper` and written into every backup manifest.
- `onUpgrade` is now incremental: it runs an ordered list of
  `DatabaseHelper.Migration` steps. `MIGRATIONS.get(v)` upgrades version `v` to
  `v + 1`; steps run in order from `oldVersion` to `newVersion`, inside the
  transaction `SQLiteOpenHelper` already provides (a failing step rolls the whole
  upgrade back).
- The **only** destructive path is `oldVersion < 2` (the development-only
  version-1 schema). It is a clearly commented one-time exception and can be
  deleted later.
- Views are handled by `DatabaseHelper.recreateViews(db)` (DROP + CREATE), so a
  view definition can change without touching any table data.
- `onDowngrade` throws `IllegalStateException` ("refusing to open to avoid data
  loss") instead of silently wiping.
- **To add the next migration:** bump `SCHEMA_VERSION` to 3 and add a step at
  index 2 of `MIGRATIONS` that upgrades 2 → 3 with ALTER/CREATE/INSERT only; the
  recipe is documented in a comment on `SCHEMA_VERSION`.
- Added `DatabaseHelper.closeInstance()` so the restore flow can close the
  singleton and replace the database file. `DATABASE_NAME` is now public.

## B. Backup (export)

- New package `com.almajd.accounting.backup`:
  - `BackupManager` (Android: zip + SQLite file work, synchronous — callers run
    it off the main thread).
  - `BackupManifest`, `AppSettingsSnapshot`, `BackupReminder`, `SimpleJson`,
    `JsonException`, `BackupException`, `ValidatedBackup` (pure Java, no Android
    APIs, so they are unit-testable).
- **Backup file format** — one zip named
  `almajd-backup-YYYY-MM-DD_HH-mm.zip`, written to `cacheDir/exports/` (already
  covered by the existing `FileProvider`), containing:
  1. `almajd_accounting.db` — the SQLite file after
     `PRAGMA wal_checkpoint(TRUNCATE)` (no `VACUUM INTO`, minSdk 26);
  2. `manifest.json` — flat JSON: `app_id`, `schema_version`, `created_at`,
     `app_version_name`, and `customers` / `invoices` / `payments` counts;
  3. `settings.json` — company/owner names and phone (`AppSettings`).
- Older files in `cacheDir/exports/` are deleted before a new backup is written.
- The zip is shared with `ShareHelper` as `application/zip`. The
  `last_backup_time` SharedPreference is written **only after the share intent
  launched successfully** (`ShareHelper.shareFile` now returns a boolean).
- Settings screen: a "النسخ الاحتياطي" card with the last-backup date (or "لا
  توجد نسخة احتياطية بعد"), a create button and a restore button.

## C. Restore (import)

- "استعادة من نسخة احتياطية" opens `ACTION_OPEN_DOCUMENT` (accepts
  zip/octet-stream); **no new storage permission**.
- Before touching current data, `BackupManager.validateBackup`:
  1. reads the file as a zip and requires the `almajd_accounting.db` and
     `manifest.json` entries;
  2. rejects a manifest whose `app_id` differs and refuses a
     `schema_version` newer than the app (and anything below 2);
  3. extracts the DB to a **fixed** temp name under `cacheDir/restore_validation/`
     (entry names are never used as paths → no zip path traversal);
  4. opens the extracted DB read-only, runs `PRAGMA integrity_check`, checks
     `PRAGMA user_version` (must be ≥ 2 and ≤ the app's version) and verifies the
     `customers`, `invoices`, `invoice_items`, `payments` tables exist.
- A confirmation dialog shows the backup's creation date and its
  customer/invoice/payment counts before anything is replaced.
- **Safety copy:** `restoreBackup` closes the singleton, copies the current DB to
  `filesDir/pre_restore/` (latest only), stages the validated DB next to the
  target and moves it into place (atomic rename). If anything fails after the
  safety copy, the safety copy is put back — the app is never left without a
  database.
- Settings are restored from `settings.json`; if the restored DB has an older
  schema version (≥ 2) the normal migration path brings it up to date when it is
  reopened.
- Afterwards the app restarts cleanly: the Dashboard is launched with
  `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK` and the activity finishes
  (`finishAffinity()`), so the `DatabaseHelper` singleton is rebuilt. A success
  toast is shown before restarting.

## D. Weekly reminder

- `BackupReminder.shouldRemind(hasData, hasBackup, lastBackup, now)` — pure Java,
  with the interval in one named constant
  `BackupReminder.REMINDER_INTERVAL_MILLIS` (7 days).
- `DashboardActivity` shows a dismissible card at the top when the DB has data
  **and** there is no backup or the last one is older than 7 days. It states how
  long ago the last backup was, has a "نسخ الآن" button (opens Settings) and a
  small close button that hides it until the next app launch. No notifications,
  no WorkManager.

## E. Strings and style

- All new user-facing text is in `res/values/strings.xml` (Arabic). Error toasts
  are mapped from `BackupException.Kind` to localized strings.
- Reused existing colors/dimens/styles; added one drawable (`ic_close.xml`) and
  reused `ic_warning` with `app:tint="@color/red"` (same pattern as the ledger
  report).

## F. Tests

Plain JVM tests in `app/src/test/java/com/almajd/accounting/backup/`:
- `SimpleJsonTest` — round-trip, escaping/Arabic, malformed input rejection;
- `BackupManifestTest` — parsing, required fields, and the version rules
  (equal/older accepted, newer refused, `< 2` refused, foreign app refused);
- `BackupReminderTest` — the 7-day decision and `daysSince`.
`./gradlew testDebugUnitTest` passes.

## Files touched (this task)

Added:
- `app/src/main/java/com/almajd/accounting/backup/BackupManager.java`
- `app/src/main/java/com/almajd/accounting/backup/BackupManifest.java`
- `app/src/main/java/com/almajd/accounting/backup/AppSettingsSnapshot.java`
- `app/src/main/java/com/almajd/accounting/backup/BackupReminder.java`
- `app/src/main/java/com/almajd/accounting/backup/BackupException.java`
- `app/src/main/java/com/almajd/accounting/backup/SimpleJson.java`
- `app/src/main/java/com/almajd/accounting/backup/JsonException.java`
- `app/src/main/java/com/almajd/accounting/backup/ValidatedBackup.java`
- `app/src/main/res/drawable/ic_close.xml`
- `app/src/test/java/com/almajd/accounting/backup/SimpleJsonTest.java`
- `app/src/test/java/com/almajd/accounting/backup/BackupManifestTest.java`
- `app/src/test/java/com/almajd/accounting/backup/BackupReminderTest.java`

Modified:
- `app/src/main/java/com/almajd/accounting/db/DatabaseHelper.java`
- `app/src/main/java/com/almajd/accounting/util/DateUtils.java`
- `app/src/main/java/com/almajd/accounting/util/ShareHelper.java`
- `app/src/main/java/com/almajd/accounting/ui/SettingsActivity.java`
- `app/src/main/java/com/almajd/accounting/ui/DashboardActivity.java`
- `app/src/main/res/layout/activity_settings.xml`
- `app/src/main/res/layout/activity_dashboard.xml`
- `app/src/main/res/values/strings.xml`

## Deliberately not done

- No automatic/scheduled backups, no cloud sync, no notifications/WorkManager,
  no new permissions; `allowBackup` and existing permissions unchanged.
- The previously-noted unfixed issues remain: N+1 for invoice line items; the
  invoice-form "paid now" overpayment is still not split (only the payment form
  splits); money is still stored as `double`; `NumberUtils`/`SimpleJson` use
  locale-dependent formatting in places; Excel export is still HTML with a `.xls`
  name.
- `ShareHelper` still contains a pre-existing hardcoded English "Error sharing:"
  toast (not introduced by this task; left as-is to keep the change focused).
- "Restore fails midway" is handled by re-copying the `pre_restore` safety copy,
  but there is no unit test for it (it needs a real Android filesystem); it was
  verified by reasoning through the code, not executed on a failure path.
- Restart uses `finishAffinity()` after launching the Dashboard with
  `NEW_TASK | CLEAR_TASK`; the process is not force-killed (killing it right
  after would risk killing the freshly launched activity).

## Build

- `./gradlew testDebugUnitTest` → BUILD SUCCESSFUL.
- `./gradlew assembleDebug --offline` → BUILD SUCCESSFUL.
- Built via a `subst` drive letter (the `&` in the project path breaks the batch
  wrapper when called directly), mapping removed afterwards.
- Debug APK copied to `C:\Users\majd\Desktop\AlMajdAccounting-debug.apk`
  (7,342,130 bytes / 7.0 MB).

---

# CHANGES — background DB work, N+1 fix, responsive UI

This section describes the next task: stop doing database work on the main
thread, remove the N+1 query for invoice line items, and keep the UI responsive
and crash-free. Nothing from the previous sections was removed. No schema change
(`SCHEMA_VERSION` stays 2), no accounting/backup logic change, `double` money and
`NumberUtils.round2` are unchanged.

## A. Shared executor (`AppExecutors`, new)

- `util/AppExecutors.java` is the single, tiny helper used everywhere:
  - one **single-thread** `ExecutorService` (thread `almajd-db`) — single so that
    writes and reads keep their submission order (two saves can never
    interleave) and so PDF/Excel/CSV helpers with non-thread-safe static caches
    are serialized;
  - one `Handler(Looper.getMainLooper())` to post results back;
  - `runOnDb(Runnable)` and `runOnMain(Runnable)`.
- No new libraries: no Room, LiveData, RxJava or coroutines.
- All dispatches capture the values they need on the main thread, do DAO/file work
  on the DB thread, then update views on the main thread, guarded by
  `isFinishing() || isDestroyed()`.
- Every list/report load uses a monotonically increasing **request token**
  (`loadToken`/`invoiceLoadToken`); a result is only applied when its token is
  still the newest, which discards stale results during rapid search/filtering.
- Short-lived tasks reference their Activity; because they are guarded, a rotation
  or back-press during a load cannot crash and cannot update a dead view.

## B. Reads moved off the main thread

All of the following now query the DAO on the DB thread and update the UI on the
main thread (unchanged layout/behaviour, list kept until the new data arrives):

- `DashboardActivity.refreshDashboard()` — the 8 aggregate/recent queries are one
  background task; `renderRecentInvoices(...)` extracted.
- `CustomerListActivity` — `loadCustomers()` and `searchCustomers()` (search is
  token-guarded so only the last keystroke wins); the duplicate load in
  `onCreate` was removed (it was already loaded in `onResume`).
- `CustomerDetailActivity.loadData()` — customer + ledger + invoices + payments in
  one background task; `renderCustomerInfo` / `renderBalanceSummary` /
  `renderRecentInvoices` / `renderRecentPayments` extracted.
- `InvoiceListActivity.loadInvoices()` — duplicate `onCreate` load removed.
- `PaymentListActivity.loadPayments()` — duplicate `onCreate` load removed.
- `SalesReportActivity` / `PaymentReportActivity` — `populateStoreSpinner()`
  (customer list) and `applyFilter()` run in the background; the filter inputs
  (spinner index, dates) are read on the main thread first; `renderInvoices()` /
  `renderPayments()` extracted. The initial filter runs once the spinner is ready
  (so there is only one load, not two).
- `LedgerReportActivity.loadLedger()` — duplicate `onCreate` load removed.

## C. Writes moved off the main thread

- Customer create/update/delete, invoice create/update/delete, payment
  create/update and the overpayment split are all performed on the DB thread; the
  result is reported back on the main thread (`Toast`, `setResult(RESULT_OK)`,
  `finish()`/reload).
- The **save button is disabled** for the whole save flow (including the DB read
  that decides whether an overpayment split is needed) and re-enabled only on
  failure, so a double tap can only insert once.
- Places that previously ignored the DAO return value now check it and show an
  error instead of claiming success: `CustomerFormActivity` (insert/update),
  `CustomerListActivity` delete, `CustomerDetailActivity` delete,
  `PaymentListActivity` delete, `PaymentFormActivity` `saveSingle`,
  `InvoiceFormActivity` update, `InvoiceDetailActivity` delete. New string
  `delete_failed` added; existing `save_failed` reused.
- In add mode the customer uniqueness check now runs **on the DB thread, in the
  same task as the insert**, so the check and the insert cannot race.
- `PaymentFormActivity` was restructured so all initial reads (customer list,
  edited payment, invoice options) happen in one background task; the store
  listener is attached only afterwards, and the initial invoice selection is
  applied from pre-read data (no DB read on main, no redundant rebuild). Save is
  disabled until that setup finishes. Cancelling the overpayment dialog
  re-enables Save.
- `InvoiceFormActivity` was restructured similarly: the store list is read in the
  background and only then is the invoice loaded (edit mode) or the store
  pre-selected + a first blank line added (add mode); Save is disabled until the
  store list (and, in edit mode, the invoice) is ready.
- `CustomerFormActivity.populateForEdit()` now loads in the background and keeps
  Save disabled until the fields are filled.

## D. N+1 for invoice line items fixed (`InvoiceDao`)

- `getAll`, `getByStoreName`, `getByDateRange`, `getByStoreAndDateRange` and
  `getRecent` now load **all** line items for the result with **one extra query
  per chunk of invoice ids** (`WHERE invoice_id IN (...)`), chunked at 500 ids
  (well under SQLite's parameter limit), then group them in memory by
  `invoice_id`. The number of queries is **constant** (2, or 3 with a very large
  list) regardless of the number of invoices, instead of one query per invoice.
- Each of those methods, plus `getRecent`, gained a `boolean loadItems` overload;
  the screens that never show line items call the `false` overload:
  `InvoiceListActivity` (`getAll(false)` / `getByStoreName(store, false)`),
  `SalesReportActivity` (all four), `CustomerDetailActivity`
  (`getByStoreName(store, false)`), `DashboardActivity` (`getRecent(5, false)`).
  Those list screens now run **exactly one** query.
- `getById` is unchanged and still loads items (used by the invoice detail and
  the invoice form, the only screens that show them).
- Pure helpers `ChunkUtils.chunk(...)` and `ChunkUtils.groupItemsByInvoiceId(...)`
  live in a separate Android-free class so they can be unit-tested on the JVM.

## E. Exports off the main thread

- PDF/Excel/CSV generation in `SalesReportActivity`, `PaymentReportActivity`,
  `LedgerReportActivity` and `InvoiceDetailActivity` (share/export invoice PDF)
  now runs on the DB thread; only the share/open intent is launched back on the
  main thread. `buildExportData()` now takes a snapshot list captured on the main
  thread. Behaviour of the files themselves is unchanged.
- Because everything shares one thread, the static font caches in `PdfExporter`
  are no longer touched concurrently.

## F. Thread-safety fix

- `NumberUtils.formatCurrency` / `formatAmount` now synchronize on the shared
  static `DecimalFormat`, because formatting now happens on both the main thread
  (adapters/views) and the DB thread (exports). Same instance, same locale,
  same output.

## G. Tests

- New plain JVM test `app/src/test/java/com/almajd/accounting/util/ChunkUtilsTest.java`
  (8 tests): chunking (null/empty, non-positive size, split/order, exact
  multiple, 1200 ids across the 500 limit) and grouping (null, grouping +
  order preservation, null-entry skipping).
- Existing backup tests unchanged. `./gradlew testDebugUnitTest` → all 30 tests
  pass (8 new + 22 existing).

## Files touched (this task)

Added:
- `app/src/main/java/com/almajd/accounting/util/AppExecutors.java`
- `app/src/main/java/com/almajd/accounting/util/ChunkUtils.java`
- `app/src/test/java/com/almajd/accounting/util/ChunkUtilsTest.java`

Modified:
- `app/src/main/java/com/almajd/accounting/db/InvoiceDao.java`
- `app/src/main/java/com/almajd/accounting/util/NumberUtils.java`
- `app/src/main/java/com/almajd/accounting/ui/DashboardActivity.java`
- `app/src/main/java/com/almajd/accounting/ui/customer/CustomerListActivity.java`
- `app/src/main/java/com/almajd/accounting/ui/customer/CustomerDetailActivity.java`
- `app/src/main/java/com/almajd/accounting/ui/customer/CustomerFormActivity.java`
- `app/src/main/java/com/almajd/accounting/ui/invoice/InvoiceListActivity.java`
- `app/src/main/java/com/almajd/accounting/ui/invoice/InvoiceDetailActivity.java`
- `app/src/main/java/com/almajd/accounting/ui/invoice/InvoiceFormActivity.java`
- `app/src/main/java/com/almajd/accounting/ui/payment/PaymentListActivity.java`
- `app/src/main/java/com/almajd/accounting/ui/payment/PaymentFormActivity.java`
- `app/src/main/java/com/almajd/accounting/ui/report/SalesReportActivity.java`
- `app/src/main/java/com/almajd/accounting/ui/report/PaymentReportActivity.java`
- `app/src/main/java/com/almajd/accounting/ui/report/LedgerReportActivity.java`
- `app/src/main/res/values/strings.xml`

## Scenarios

- **2000 invoices / 5000 payments, list screens** — one query (line items not
  loaded) per list load.
- **2000 invoices, screen that *does* show items (invoice detail)** — 1 (view) +
  1 (items IN(...)) queries per chunk of 500 ids; the full form/detail load is at
  most 2–3 queries instead of ~2001.
- **Rapid search typing** (`CustomerListActivity`) — every keystroke queues a
  background search; the token makes sure only the newest result updates the
  adapter, so replies cannot arrive out of order.
- **Double-tap Save** — the button is disabled before the write is queued, so the
  second tap is ignored; a failed write re-enables it.
- **Back press during a load** — results are dropped by the
  `isFinishing()/isDestroyed()` guard; nothing is written to a dead view.
- **Failed write** — the DAO result is checked; an error toast is shown and the
  save button is re-enabled (no false success, no `RESULT_OK`).
- **Rotation during load/save** — the Activity is recreated, the in-flight task
  posts to the destroyed instance (guarded and dropped) and the new instance
  loads again from `onResume`/`onCreate`. No crash.
- **Backup/restore** — unchanged; it already ran off the main thread and its code
  paths were not touched (only `ShareHelper`'s file is unchanged).

## Remaining main-thread database access (not moved)

- `DatabaseHelper.getInstance(...)` is still called from `App.onCreate` (and
  lazily from DAO constructors on the main thread during `onCreate`), so the very
  first open of the database — schema creation and the version-1→2 migration —
  happens on the main thread. This is a one-time startup cost in the existing
  schema/migration path, not a per-screen query, and moving it would change
  startup behaviour, so it was left as-is deliberately.
- `BackupManager` calls remain where they were (already background); the settings
  screen's backup/restore UI was not touched.

## Bugs noticed but not fixed (out of scope)

- The invoice-form "paid now" overpayment (create mode) is still not split into a
  linked + on-account payment (only the payment form splits); pre-existing.
- Money is still stored as `double`; a real currency type was not introduced.
- `ShareHelper` still has a pre-existing hardcoded English "Error sharing:" toast.
- Excel export is still an HTML table with a `.xls` name / xlsx MIME.
- Restore-time issues noted previously remain (no crash-safe restore, the
  `pre_restore` copy is not auto-recovered, no zip size limit, same-minute
  backups overwrite each other).
- Several report/form strings remain locale-dependent in formatting.

## Build

- `./gradlew testDebugUnitTest assembleDebug --offline` → BUILD SUCCESSFUL
  (AGP 7.4.2 / Gradle 7.5).
- All 30 unit tests pass (8 new `ChunkUtilsTest` + 22 existing backup tests).
- Built via a `subst` drive letter (the `&` in the project path breaks the batch
  wrapper when called directly), mapping removed afterwards.
- Debug APK copied to `C:\Users\majd\Desktop\AlMajdAccounting-debug.apk`
  (7,363,305 bytes / 7.02 MB), built 2026-10-17 17:54.

---

# Backup & restore hardening

## Goal

Make the in-app backup/restore feature safe against interruption and hostile
files: a restore must be recoverable after a crash, oversized/zip-bomb files must
be rejected, two backups in the same minute must not collide, and the remaining
hardcoded English string in `ShareHelper` must be localized. Accounting logic, the
DB schema and `SCHEMA_VERSION` are unchanged.

## What changed

### A. Crash-safe restore

- New marker file `filesDir/pre_restore/RESTORE_IN_PROGRESS`
  (`BackupManager.RESTORE_MARKER_NAME`), written once the safety copy of the
  current DB is complete and removed only when the new DB is in place, the
  settings are restored **and** the new DB opens.
- The marker is two-phase (`RestoreMarker`): `PHASE_SAFETY_TAKEN` (1) then
  `PHASE_DB_INSTALLED` (2). It also records whether a DB existed before and the
  exact byte size of the safety copy, so recovery can verify the copy is intact.
  The marker is written with an `fsync` so it survives a process kill.
- `restoreBackup` order: close the singleton → free-space check → safety copy →
  write marker phase 1 → stage the new DB next to the target → delete the old
  `db`/`-wal`/`-shm` → atomic `rename` the staged file into place → write marker
  phase 2 → restore settings → open the new DB (`getWritableDatabase`) and run a
  cheap `SELECT count(*) FROM customers` → delete the marker.
- `App.onCreate` calls `BackupManager.recoverIfInterrupted(this)` **before**
  `DatabaseHelper.getInstance(this)` opens any handle. On recovery:
  - phase 1 + intact safety copy → copy the safety DB back over the current DB
    (deleting stale `-wal`/`-shm` first), delete the marker, and queue a one-time
    message;
  - phase 2 (DB already installed) → keep the restored DB, delete the marker, and
    queue a "restore completed" message instead of rolling the new data back;
  - phase 1 with no previous DB → just clear the marker;
  - safety copy missing or its size not matching the recorded one → change
    nothing, only log (per the requirement).
- `-wal`/`-shm` are deleted whenever the DB file is swapped or recovered, so stale
  side files can never be reused with a different database file.
- If the in-restore rollback itself fails, the marker is **kept** so the next
  launch retries recovery (the old code deleted it unconditionally).
- The queued message is stored in SharedPreferences and shown once as an
  `AlertDialog` on the next `DashboardActivity` creation
  (`consumePendingRecoveryMessage` clears it).

### B. Size limits (zip-bomb / huge-file protection)

- New pure class `BackupLimits` with named constants:
  `MAX_COMPRESSED_BYTES = 200 MB`, `MAX_DB_UNCOMPRESSED_BYTES = 500 MB`,
  `MAX_JSON_UNCOMPRESSED_BYTES = 1 MB`, `FREE_SPACE_MULTIPLIER = 2`.
- Real bytes are counted, not the zip header: a `CountingInputStream` counts the
  compressed bytes actually read from the source (checked after every entry), and
  `copyStreamLimited` / `readAllText` count the uncompressed bytes while
  copying/reading each entry and abort with `TOO_LARGE` past the cap.
- A partial/huge extraction is deleted on rejection; the current data is never
  touched during validation.
- Free space is checked twice: in `validateBackup` against the declared source
  size (fast pre-filter) and in `restoreBackup` against the validated DB size
  before the swap. Both require ~2× the size.
- Two new `BackupException.Kind`s: `TOO_LARGE` and `NOT_ENOUGH_SPACE`, mapped in
  `SettingsActivity` to `restore_too_large` / `restore_not_enough_space`.

### C. Unique backup file names

- New pure class `BackupNaming` builds
  `almajd-backup-YYYY-MM-DD_HH-mm-ss.zip` (seconds included; fixed `Locale.US` so
  digits stay ASCII under the forced Arabic locale). The timestamp remains
  lexicographically sortable because every field is fixed-width.
- `DateUtils.fileTimestamp()` / `FILE_TIMESTAMP_FORMAT` (minute resolution) were
  removed; they existed only for backup names and are superseded by
  `BackupNaming`.

### D. ShareHelper

- The hardcoded English `"Error sharing: " + e.getMessage()` toast is now
  `R.string.share_error` (Arabic default). No other user-facing English string
  remains in `ShareHelper` (the chooser title is the file name, i.e. data).

### E. Tests

- New plain JVM tests, no Android dependencies:
  - `BackupLimitsTest` (8): compressed/DB/JSON caps at the boundary, negative
    rejection, 2× free-space rule, overflow guard.
  - `BackupNamingTest` (5): exact file-name format, fixed sortable width, two
    backups in the same minute get different names, later sorts after earlier,
    null timestamp handling.
  - `RestoreRecoveryTest` (8): marker text round-trip, malformed marker, and the
    full decision matrix (nothing / recur previous / abort on missing or
    wrong-size copy / keep restored / clear-only).

## Recovery flow (interrupted restore)

1. Kill **after the safety copy, before the swap** → marker phase 1, safety copy
   complete → next launch: safety DB restored over the current one, marker
   deleted, dialog "the restore was interrupted and the previous data was
   recovered".
2. Kill **after the swap, before the marker is deleted** → marker phase 2 →
   next launch: the freshly restored DB is **kept**, marker deleted, dialog
   "restore completed" (data is never silently rolled back).
3. Kill **between the safety copy and the phase-1 marker write**, or a corrupt
   safety copy → recovery changes nothing and logs it.
4. Kill between `rename` and the phase-2 marker write → still phase 1, so the
   previous data is restored (see residual window below).

## Limits chosen

| Limit | Value | Rationale |
|---|---|---|
| Backup file (compressed) | 200 MB | generously above any realistic shop DB |
| DB entry (uncompressed) | 500 MB | headroom over the compressed cap for a low-ratio DB |
| JSON entry (uncompressed) | 1 MB | manifest/settings are a few hundred bytes |
| Required free space | 2× DB size | one safety copy + one staging copy |

## Files touched (this task)

Added:
- `app/src/main/java/com/almajd/accounting/backup/BackupLimits.java`
- `app/src/main/java/com/almajd/accounting/backup/BackupNaming.java`
- `app/src/main/java/com/almajd/accounting/backup/RestoreMarker.java`
- `app/src/main/java/com/almajd/accounting/backup/RestoreRecovery.java`
- `app/src/test/java/com/almajd/accounting/backup/BackupLimitsTest.java`
- `app/src/test/java/com/almajd/accounting/backup/BackupNamingTest.java`
- `app/src/test/java/com/almajd/accounting/backup/RestoreRecoveryTest.java`

Modified:
- `app/src/main/java/com/almajd/accounting/App.java`
- `app/src/main/java/com/almajd/accounting/backup/BackupException.java`
- `app/src/main/java/com/almajd/accounting/backup/BackupManager.java`
- `app/src/main/java/com/almajd/accounting/ui/DashboardActivity.java`
- `app/src/main/java/com/almajd/accounting/ui/SettingsActivity.java`
- `app/src/main/java/com/almajd/accounting/util/DateUtils.java`
- `app/src/main/java/com/almajd/accounting/util/ShareHelper.java`
- `app/src/main/res/values/strings.xml`

## Deliberately not done / remaining risks

- The `exports/` cache is still cleared before every backup, so only the newest
  zip is kept in the cache. The seconds in the name fix collisions for the copies
  the user saves *outside* the app; two backups in the same cache slot is
  unchanged by design (bounded cache).
- There is a tiny window between the `rename` and the phase-2 marker write during
  which a kill is treated as phase 1 and the previous data is restored (the new
  data is still available in the backup file the user picked). Closing it fully
  would need a checksum compare, which is out of scope.
- Safety-copy integrity is checked by size only, not a checksum.
- If the app is killed between the DB swap and the settings save, recovery keeps
  the restored DB (correct, no data loss) but the settings from the backup may not
  have been applied; the "restore completed" dialog is still shown.
- Recovery runs synchronously in `App.onCreate` and copies the safety DB on the
  main thread (it is a rare, one-time path; the DB is small in practice).
- A corrupt marker file is left in place and only logged on each launch.
- `usableSpace()` uses `filesDir.getUsableSpace()`, which assumes the databases
  directory is on the same volume (true for standard internal storage).
- Unchanged from before: DB first open/migration still happens on the main thread
  via `App.onCreate`; money is still `double`; Excel export is still an HTML
  `.xls`.

## Build

- `./gradlew testDebugUnitTest assembleDebug --console=plain --offline` →
  BUILD SUCCESSFUL (AGP 7.4.2 / Gradle 7.5).
- All 51 unit tests pass (21 new: `BackupLimitsTest` 8, `BackupNamingTest` 5,
  `RestoreRecoveryTest` 8; 30 existing).
- Built via a `subst` drive letter (the `&` in the project path breaks the batch
  wrapper when called directly), mapping removed afterwards.
- Debug APK copied to `C:\Users\majd\Desktop\AlMajdAccounting-debug.apk`
  (7,416,788 bytes / 7.07 MB), built 2026-10-17 20:30.

---

# Small fixes: invoice overpayment, date filters, digits/locale, real .xlsx, cleanup

Independent, minimal fixes on branch `feature/small-fixes` (from `Dev-0`).
No accounting logic, schema, `SCHEMA_VERSION`, backup/restore or threading
changes.

## 1. Invoice form: "paid now" greater than the invoice total (create)
- On **create**, when the entered "paid now" exceeds the computed invoice total,
  a confirmation dialog is now shown (same pattern as `PaymentFormActivity`).
  - **Confirm** writes, in the *same* transaction as the invoice + items:
    - a payment linked to the invoice for the invoice total, and
    - the excess as an on-account payment (`invoice_id` NULL) with note
      `payment_excess_note`.
  - **Cancel** saves nothing and leaves the form open with Save re-enabled.
- The excess is never silently dropped or altered; both payments (or neither)
  are written because they share the invoice's transaction.
- New `InvoiceDao.insert(Invoice, double linkedPaid, double onAccountPaid)`
  overload; the old `insert(Invoice, double paidNow)` now delegates to it.
- Files: `ui/invoice/InvoiceFormActivity.java`, `db/InvoiceDao.java`,
  `res/values/strings.xml` (`paid_now_overpayment_title`,
  `paid_now_overpayment_message`).
- Example: total 1000, paid now 1200 -> dialog; confirm => invoice paid (1000
  linked) + 200 on-account (customer credit 200); cancel => nothing saved.

## 2. Report date filter: one-sided ranges + validation
- `SalesReportActivity` / `PaymentReportActivity` now filter with **either**
  bound: only "from" => `date >= from`; only "to" => `date <= to`; both => range.
- Implemented by opening the missing bound in the DAO with the sortable
  sentinels `DateUtils.DB_DATE_MIN` (`0000-00-00`) / `DB_DATE_MAX`
  (`9999-99-99`), so each filter run is still exactly one query.
- When both dates are set and `from > to`, a message is shown
  (`invalid_date_range`) and no query runs.
- Files: `db/InvoiceDao.java`, `db/PaymentDao.java`, `util/DateUtils.java`,
  `ui/report/SalesReportActivity.java`, `ui/report/PaymentReportActivity.java`,
  `res/values/strings.xml`.

## 3. Dashboard recent-invoice date
- The recent-invoice row now shows the date via `DateUtils.formatForDisplay`
  (was the raw `yyyy-MM-dd`), like every other screen.
- File: `ui/DashboardActivity.java`.

## 4. ASCII digits everywhere / Arabic-Indic input
- `NumberUtils`:
  - `CURRENCY_FORMAT` now uses `DecimalFormatSymbols.getInstance(Locale.US)`, so
    formatting emits ASCII digits and a '.' decimal separator even though the app
    forces an Arabic locale (`App.forceArabicLocale`).
  - `parseAmount` first normalises Arabic-Indic (U+0660-U+0669) and Extended
    Arabic-Indic (U+06F0-U+06F9) digits to ASCII and maps the Arabic decimal
    separator U+066B to '.', then strips the currency symbol and both the Latin
    ',' and Arabic thousands separator (U+066C).
  - New `toAsciiDigits(String)` and `parseInt(String)` helpers.
- `InvoiceItemAdapter` now parses quantity via `NumberUtils.parseInt` and unit
  price via `NumberUtils.parseAmount`, so Arabic-Indic digits typed in the line
  item fields also parse.
- `DateUtils` already used `Locale.US` for every format/parse; left as is.
- `SimpleJson` control-character escaping now uses `String.format(Locale.US, ...)`.
- PDF/CSV exporters delegate to `NumberUtils`/`DateUtils` (no direct formatting),
  so they inherit the fix; nothing else to change there. `String.valueOf(int/long)`
  and `Double.toString` are locale-independent (ASCII).
- Files: `util/NumberUtils.java`, `ui/invoice/InvoiceItemAdapter.java`,
  `backup/SimpleJson.java`.

## 5. Real .xlsx export (replaces the HTML table named .xls)
- New pure-Java `util/XlsxWriter.java` writes a minimal but valid Office Open XML
  workbook using `java.util.zip` only:
  - parts `[Content_Types].xml`, `_rels/.rels`, `xl/workbook.xml`,
    `xl/_rels/workbook.xml.rels`, `xl/styles.xml`, `xl/worksheets/sheet1.xml`;
  - inline strings (`t="inlineStr"`) for text, **real numeric cells** for values
    that look like amounts (currency symbol/grouping stripped), so they can be
    summed;
  - `sheetView rightToLeft="1"` and a bold header row (`s="1"`);
  - XML escaping for `& < >` (and `" '` in attributes); control characters are
    dropped.
- `ExcelExporter.generateExcel` now delegates to `XlsxWriter` and writes a
  `.xlsx` file; the three report screens already shared it with the correct MIME
  `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`.
  `ShareHelper.shareExcel` MIME updated to match.
- CSV export is unchanged except that its numbers/dates come from the fixed
  `NumberUtils`/`DateUtils`.
- Files: `util/XlsxWriter.java` (new), `util/ExcelExporter.java`,
  `util/ShareHelper.java`.

## 6. Cleanup
- Removed the unused `WRITE_EXTERNAL_STORAGE` and `READ_EXTERNAL_STORAGE`
  permissions (all files go through `cacheDir` + `FileProvider`; restore uses the
  SAF `ACTION_OPEN_DOCUMENT` document picker). The final packaged manifest
  contains no storage permission.
- Removed 23 strings proven unused (searched all `.java`/`.xml` for
  `R.string.<name>` and `@string/<name>`): `btn_add_customer`, `btn_new_invoice`,
  `btn_add_payment`, `btn_reports`, `invoice`, `payment`, `total_customers`,
  `outstanding_balance`, `pdf_header_ar_line1`, `pdf_header_ar_line2`,
  `pdf_header_en_line1`, `pdf_header_en_line2`, `pdf_phone_placeholder`, `export`,
  `status_partial`, `status_unpaid`, `tab_accounts`, `tab_sales`, `tab_payments`,
  `backup_created_share`, `backup_in_progress`, `restore_validating`,
  `restore_failed`.
- `CustomerDetailActivity` no longer navigates by `Class.forName`; it uses direct
  `Intent`s to `InvoiceListActivity` / `PaymentListActivity`.

### Deliberately NOT done (checked, kept)
- `res/menu/menu_report.xml` -- **not** dead: it is referenced by
  `activity_sales_report.xml`, `activity_payment_report.xml` and
  `activity_ledger_report.xml` via `app:menu="@menu/menu_report"`.
- `androidx.constraintlayout:constraintlayout:2.1.4` -- no layout uses it, but it
  is a **transitive dependency of `com.google.android.material:material:1.11.0`**
  (which resolves it to 2.0.1 if the explicit pin is removed; 2.0.1 is not in the
  offline cache and the build then fails). It cannot be removed, so it is kept as
  an explicit pin.
- Nothing else was removed; only resources that could be proven unused.

## Build
- `./gradlew testDebugUnitTest assembleDebug --console=plain --offline` ->
  BUILD SUCCESSFUL (AGP 7.4.2 / Gradle 7.5).
- All 69 unit tests pass; 18 new plain-JVM tests: `NumberUtilsTest` (9) and
  `XlsxWriterTest` (9).
- The generated `.xlsx` was additionally opened with Microsoft Excel (COM) to
  confirm it is valid: cell text is real Arabic (U+0645), `& < >` survive as
  literals, amount cells are numeric (`SUM` of the two amounts = 3500.5), the
  sheet view is RTL and the header row is bold.
- Built via a `subst` drive letter (the `&` in the project path breaks the batch
  wrapper when called directly), mapping removed afterwards.
- Debug APK copied to `C:\Users\majd\Desktop\AlMajdAccounting-debug.apk`
  (7,365,561 bytes / 7.02 MB), built 2026-10-17 20:46.

## Remaining risks / noticed but not fixed
- Excel numeric auto-detection: a *text* cell that looks like a bare number (for
  example a payment note of `123`) is written as a numeric cell. Accepted
  trade-off to keep the export API string-based; documented in `XlsxWriter`.
- The workbook has no column widths/`autoFilter`/date number formats; formatted
  amounts are written as plain numbers (the `NIS` symbol is not repeated per row).
- `PaymentFormActivity` still shows an edited amount via `String.valueOf(double)`
  (ASCII, but e.g. `1500.0`); left unchanged to avoid touching that flow.
- Pre-existing, untouched: DB first open/migration on the main thread; money
  stored as `double`; the invoice "paid now" overpayment is handled on the create
  path only (the edit path intentionally never creates payments).

---

# Invoice form UI: full-form scrolling, RTL column order, "صنف" rename

Focused UI work on the invoice form (`feature/invoice-form-ui`). No DB schema,
accounting logic, backup/restore or threading changes.

## 1. Scrolling — all line items reachable, Save always visible
- `activity_invoice_form.xml`: the content `ScrollView` is now a
  `androidx.core.widget.NestedScrollView` (`@+id/nsv_form`, `fillViewport=true`).
  The root stays a vertical `LinearLayout` of toolbar / scroll area (`0dp`,
  `weight=1`) / Save button, so Save lives **outside** the scroll area and is
  always visible at the bottom.
- The item list stays a **non-scrolling** `RecyclerView`
  (`nestedScrollingEnabled=false`, `height=wrap_content`) inside that single
  NestedScrollView — the recommended way to avoid nested-scroll conflicts. With
  an unbounded height it lays out every row, so many rows are all reachable by
  scrolling the one parent.
- Adding a row calls `addItemAndReveal(position)`: after layout it computes the
  row's offset, `nsvForm.smoothScrollTo(...)` and focuses the new row's name
  field. `InvoiceItemAdapter.addItem()` now returns the new position.
- `AndroidManifest.xml`: `InvoiceFormActivity` gets
  `android:windowSoftInputMode="adjustResize"` so the keyboard resizes the window
  (the scroll area shrinks, Save stays visible) instead of panning over the
  focused field.

## 2. RTL column order + running row number
- `item_invoice_line.xml` child order is now (first child = rightmost in RTL):
  row number, item name, quantity, unit price, row total, delete. The delete
  button has a fixed 40dp width so a spacer can mirror it.
- New `tv_row_number` shows `position + 1`; the adapter sets it on every bind and
  the delete path already calls `notifyItemRangeChanged(pos, ...)`, so removing a
  middle row renumbers the rows below it.
- `activity_invoice_form.xml` adds a column header row mirroring the item row
  (same widths/weights/margins): `# | اسم الصنف | الكمية | سعر الواحدة | المجموع`.
- The unit-price label is now **سعر الواحدة** (`unit_price` string).

## 3. "البند" -> "الصنف" rename
- `strings.xml`: `item_name` = "اسم الصنف", `items_section_title` = "الأصناف",
  `add_item` = "+ إضافة صنف", `add_at_least_one_item` = "أضف صنفاً واحداً على الأقل",
  `unit_price` = "سعر الواحدة"; new `col_number` = "#".
- `PdfExporter.java` invoice items header: "البند" -> "الصنف" and "السعر" ->
  "سعر الواحدة" (comments updated too).
- The invoice detail screen reuses `R.string.item_name` / `R.string.unit_price`,
  so it picks up the rename automatically. No report shows item names, so nothing
  else needed changing. A search for البند/بند/البنود/سعر الوحدة now returns
  nothing.

## 4. Behaviour preserved
- Live totals/remaining, overpayment dialog, double-tap protection on Save,
  rounding, and edit mode (paid derived from payments, "paid now" row hidden) are
  unchanged.
- Rotation: `onSaveInstanceState` persists the create-mode line items plus the
  selected store; `onCreate` restores them and `onStoreSpinnerReady` skips adding
  a blank row, so rotating no longer loses in-progress items. Edit mode still
  reloads from the DB (unchanged behaviour).

## Build
- `./gradlew testDebugUnitTest assembleDebug --console=plain --offline` ->
  BUILD SUCCESSFUL. All 69 unit tests pass.
- Built via a `subst` drive letter (the `&` in the project path breaks the batch
  wrapper when called directly), mapping removed afterwards.
- Debug APK copied to `C:\Users\majd\Desktop\AlMajdAccounting-debug.apk`
  (7,354,313 bytes / 7.01 MB), built 2026-10-17 21:13.

## Noticed but not fixed
- The `RecyclerView` inside a scroll container loses view recycling (all rows are
  measured/laid out). Fine for invoice-sized lists; noted for very large ones.
- Untouched (pre-existing): the adapter trims the item name on every keystroke;
  there is no maximum item count; and in edit mode the item list is reloaded from
  the DB on rotation rather than from saved state.

---

# Customer credit allocation: invoices settled from on-account money

Display/derivation-only layer on `feature/credit-allocation`. No DB schema or
`SCHEMA_VERSION` change, no stored payment row is ever modified, and backup /
restore / threading are untouched.

## Problem
A customer's payments with `invoice_id IS NULL` ("on account") lower the
customer's balance, but each invoice still showed its raw remaining (total minus
only its *linked* payments), so credit-covered invoices looked unpaid.

## What changed

### A. New pure-Java allocator — `util/CreditAllocator.java`
- Android-free (JVM-tested). Inputs: a customer's invoices
  (`Debt`: id, date, total, paidDirect) plus the customer's on-account pool.
- Spreads the pool **oldest first** (`invoice_date` ASC, then `id` ASC), giving
  each invoice `min(remaining, poolLeft)`; invoices already paid directly get 0.
- Returns per invoice: `paidDirect`, `allocatedFromCredit`,
  `effectivePaid = paidDirect + allocatedFromCredit`,
  `effectiveRemaining = max(total - effectivePaid, 0)`, and an `InvoiceStatus`.
- All money math goes through `NumberUtils.round2`, so `0.1 + 0.2` cases land
  exactly and the pool is consumed without float leftover.
- `allocateByCustomer(...)` is a small batch helper so each customer's pool only
  ever covers that customer's own invoices.

### B. Derivation lives in `db/InvoiceDao.java`
- `invoice_view` is **unchanged**. After every invoice read that feeds a screen
  (`queryInvoices`, `getRecent`, `getById`), `applyCreditAllocation(...)`:
  1. reloads the whole invoice set for the distinct stores in the result with
     one query per chunk of stores (a single invoice's share depends on its
     siblings, because allocation is oldest-first);
  2. reads the on-account pool for those stores with **one grouped query**
     (`SUM(amount) WHERE invoice_id IS NULL GROUP BY store_name`);
  3. runs `CreditAllocator` in memory and overwrites the invoices'
     `paidAmount` / `remaining` / `status` with the effective values.
- No N+1 in the number of invoices: a constant number of queries per list,
  chunked by store count.
- `getOpenInvoicesByStore` now filters on the **effective** remaining in Java
  (the SQL `remaining > 0` filter only knew about direct payments), so a
  credit-covered invoice is no longer offered as unpaid in the payment form.

### C. Model — `model/Invoice.java`
- Added `paidDirect` and `allocatedFromCredit` (both derived, never persisted).
  `paidAmount` / `remaining` / `status` keep their names but now hold the
  effective values, so every existing screen became correct automatically.

### D. Screens
- **Invoice list** (`InvoiceAdapter` + `item_invoice.xml`): credit-covered
  invoices show the badge `مسددة من رصيد الزبون` (full) or
  `مسددة جزئياً من رصيد الزبون` (partial), plus a separate
  `من رصيد الزبون: X` line under the badge (with the effective remaining when
  the invoice is still partly open).
- **Invoice detail**: the paid/remaining footer uses the effective values, the
  payments card still lists only directly-linked payments and adds a
  `من رصيد الزبون` line, and the "no payments" hint is not shown when a credit
  line exists.
- **Dashboard** recent invoices show the credit line instead of hiding the
  remaining row; **customer detail** mini-invoice rows append the credit line.
- **Sales report**: rows/totals use the effective remaining; rows with credit
  append `من رصيد الزبون: X` to the total, and the PDF/Excel/CSV exports gained a
  dedicated `من رصيد الزبون` column (with a grand total).
- **Invoice PDF**: the footer shows the effective paid/remaining and adds a
  `من رصيد الزبون: X` row when credit was used.
- **Customer balance, dashboard "debts" and the ledger are unchanged** — they
  still come from `ledger_view` (sales − all payments). Only invoice-level
  display changed.

### E. Payment form overpayment check
- `available` for a selected invoice is now
  `round2(total - paidDirectExcluding - allocatedFromCredit)`: a payment can be
  linked only up to what is **effectively** still owed. Anything above that
  (including a payment aimed at an invoice already covered by credit) is offered
  as an on-account payment, so the same money is never counted twice. Editing a
  payment already linked to a credit-covered invoice still keeps its link,
  because its own amount is added back when computing `available`.

### F. Strings
- `strings.xml` (Arabic, ASCII digits via `NumberUtils`): `status_paid_from_credit`,
  `status_partial_from_credit`, `credit_from_customer_label`,
  `credit_from_customer_amount`, `credit_from_customer_amount_remaining`.

## Invariant (documented, and where it can differ)
For a customer, when **no invoice is overpaid directly**, the sum of
`effectiveRemaining` over all their invoices equals
`max(customerBalance, 0)`, where `customerBalance = SUM(invoice.total) − SUM(all
payments)`. That is why the invoice-level view and the customer-level balance
agree.

It can differ when an invoice's **directly-linked** payments exceed its own
total: that excess stays parked on that invoice and is deliberately not
redistributed (redistributing would mean rewriting stored payments, which this
layer never does). In that case `SUM(effectiveRemaining)` exceeds
`max(customerBalance, 0)` by the total direct excess. Rounding uses `round2`
throughout, so no rounding-sized mismatch is introduced.

## Build
- `./gradlew testDebugUnitTest assembleDebug --console=plain --offline` ->
  BUILD SUCCESSFUL. All 82 unit tests pass (13 new in `CreditAllocatorTest`:
  no pool; pool smaller than the oldest; pool covering several partially; pool
  larger than all debts; already-paid skipped; date-then-id ordering; rounding
  `0.1/0.2`; negative pool; independent customers; empty inputs; result order).
- Built via a `subst` drive letter (the `&` in the project path breaks the batch
  wrapper when called directly), mapping removed afterwards.
- Debug APK copied to `C:\Users\majd\Desktop\AlMajdAccounting-debug.apk`
  (7,372,181 bytes / 7.03 MB), built 2026-10-17 21:35.

## Noticed but not fixed
- A directly-overpaid invoice's excess is not redistributed (see the invariant
  above); it is treated as if parked on that invoice.
- The per-list allocation reloads a customer's full invoice set even when the
  caller already selected all of that customer's invoices (e.g. the all-stores
  sales report), so those lists run the view query twice. Correct, but it doubles
  the row read for that case.
- Untouched (pre-existing): DB first open/migration on the main thread; money
  stored as `double`; `PaymentFormActivity` shows an edited amount via
  `String.valueOf(double)`.

---

# Customer account statement (كشف حساب)

Branch: `feature/customer-statement` (single commit). This is a **derivation /
export** feature: nothing new is stored and the tracking of invoices, payments,
balances and the credit allocation is untouched.

## Goal
Print, share or view a customer's account statement: every invoice and payment in
chronological order with a running balance, an opening balance for a date range
and a closing balance that matches the customer balance shown elsewhere.

## Entry point
- `CustomerDetailActivity` gained a `كشف حساب` toolbar item (now the first menu
  entry). It opens a small `AlertDialog` (`dialog_customer_statement.xml`) with
  two optional date fields (reusing `DateUtils.showDatePicker`, stored as
  `yyyy-MM-dd`) and two actions: **مشاركة PDF** (`ShareHelper.sharePdf`) and
  **عرض PDF** (`ACTION_VIEW` with a `FileProvider` URI).
- `from <= to` is validated in the activity; an invalid range shows the existing
  `invalid_date_range` message and keeps the dialog open.

## Statement content (pure derivation)
- Header: the shared bilingual company header (extracted from the invoice PDF
  into `PdfExporter.addBilingualHeader`, used by both), then the title
  `كشف حساب`, and a right-aligned info block: store, owner, phone, location, the
  period (`كل الفترات` / `من … إلى …`) and the print date.
- Opening balance row: shown only when a `from` date is set; it is the net of all
  movements **strictly before** `from` (`SUM(invoices) − SUM(payments)`), computed
  in the builder from the full lists.
- Movements table sorted by date ascending; on the same date invoices come before
  payments, then by id. Columns (RTL, reversed-column technique like the report
  PDFs): date | type (فاتورة/دفعة) | reference | debit | credit | running balance.
  Invoices reference `#id`; a payment references its note (when present) plus
  `على فاتورة #id` when linked, otherwise `على الحساب`.
- Running balance = previous + invoice total − payment amount, `NumberUtils.round2`
  at every step, shown with `BalanceUtils` wording (عليه / له عندنا / مسدد) — never
  a bare negative number.
- Footer: total invoices (debit), total payments (credit) and the closing balance
  (same wording). It includes the opening balance, so with no date filter the
  closing balance equals the customer detail / ledger balance exactly.
- The display-only on-account credit allocation is deliberately **not** used here:
  the statement is about raw movements.
- Empty period: a single `لا توجد حركات` line instead of a table.
- Pagination: the table header repeats (`setHeaderRows(1)`) and a
  `صفحة N من M` footer is drawn on every page. The total page count lives in a
  `PdfTemplate` filled at `onCloseDocument` (standard iText 5 "page X of Y"), with
  the four footer cells added `[total][من][N][صفحة]` so the Arabic reads RTL.

## Data access
- `InvoiceDao.getStatementMovements(store)` and
  `PaymentDao.getStatementMovements(store)` each run **one** query for the store
  (oldest first, no items, no credit allocation, no N+1). The opening balance is
  derived in memory from these full lists, so the same read serves any range.
- `StatementBuilder` (pure Java, Android-free) merges the movements, filters by
  `from`/`to` (inclusive, `yyyy-MM-dd` lexicographic), computes the opening and
  closing balances and the per-row running balances.

## Export
- `PdfExporter.generateCustomerStatementPdf(context, storeName, customer,
  statement)` uses the existing Cairo fonts and RTL cells, landscape A4 (room for
  6 columns). It is generated on the DB thread; the share/view intent is launched
  on the main thread after an `isFinishing() || isDestroyed()` guard.
- File: `cacheDir/exports/statement-<sanitized store name>-<yyyy-MM-dd>.pdf`. The
  store name is sanitized (`StatementBuilder.sanitizeFileName`: `\ / : * ? " < > |`
  and control chars → `_`, trailing dots/spaces trimmed, length capped, **Arabic
  letters kept**). The existing FileProvider path already covers `exports/`.
- `ACTION_VIEW` is wrapped in a `ActivityNotFoundException` catch (a toast when no
  PDF viewer is installed) so a missing viewer cannot crash.

## Strings
All new user-facing text is in `strings.xml` (Arabic, ASCII digits): the menu /
title `account_statement`, the actions, the period variants, the column headers,
the movement types and references, `statement_no_movements`, the totals, the page
footer words, `pdf_generation_failed` and `pdf_no_viewer`.

## Tests
Plain JVM tests in `StatementBuilderTest` (15): no movements; invoices only;
payments only; payments with no invoices (credit closing); mixed ordering (same
date invoice before payments, then by id); opening balance with a `from` date;
`to` cutoff; both bounds inclusive; closing balance equals
`SUM(invoices) − SUM(payments)` over all time; rounding (`0.1 + 0.2`);
negative/credit closing; linked vs unlinked payment fields; blank bounds treated
as open; null entries ignored; file-name sanitizing (Arabic kept, invalid chars
replaced).

## Build
- `./gradlew testDebugUnitTest assembleDebug --console=plain --offline` ->
  BUILD SUCCESSFUL. All 97 unit tests pass (82 prior + 15 new).
- Built via a `subst` drive letter (the `&` in the project path), mapping removed
  afterwards.
- Debug APK copied to `C:\Users\majd\Desktop\AlMajdAccounting-debug.apk`
  (7,380,415 bytes), built 2026-10-17.

## Noticed but not fixed
- The statement loads **all** of a store's invoices and payments and filters in
  memory; fine for a shop, but a date range is not pushed down to SQL.
- The date fields in the statement dialog display the raw `yyyy-MM-dd` value the
  picker writes (same behaviour as the sales-report filter), not `dd/MM/yyyy`.
- `showTextAligned`-style free text is avoided for Arabic; all statement text goes
  through RTL table cells, so page-number shaping depends on the cell run
  direction (kept explicit).
- Pre-existing and untouched: DB first open/migration on the main thread; money
  stored as `double`; hardcoded English error toasts in the pre-existing
  invoice-share code.

---

# Fix: Arabic titles / page footer rendered mirrored in PDFs

## Problem
On a real device the Arabic title of the customer statement PDF (`كشف حساب`) was
rendered with its letters in visual reverse order (`باسح فشك`), and the same
happened to the title of **every** generated PDF (sales report, payments report,
ledger report, statement). The `صفحة N من M` footer looked wrong too.

Root cause: iText 5 only runs the bidi algorithm (Arabic shaping + right-to-left
reordering) for text laid out inside a `PdfPCell` whose run direction is
`RUN_DIRECTION_RTL` (or via `ColumnText.setRunDirection`). Text added directly to
the `Document` as a bare `Paragraph`/`Phrase`/`Chunk` is laid out strictly
left-to-right, so Arabic is written in logical order and appears mirrored.

## What changed (bug fix only, no schema/logic/threading changes)
- Added one shared helper `PdfExporter.rtlTextRow(text, font, alignment)`: it
  wraps a line in a 1-column, full-width, borderless `PdfPTable` whose only
  `PdfPCell` has `RUN_DIRECTION_RTL`. Returns the table so callers can still set
  spacing before/after.
- Report title (`generateReportPdf`) — used by the sales, payments and ledger
  report PDFs. Was a bare `Paragraph`; now `rtlTextRow`.
- Statement title (`generateCustomerStatementPdf`) — was a bare `Paragraph`;
  now `rtlTextRow`.
- Statement "no movements" empty hint — was a bare `Paragraph`; now
  `rtlTextRow`.
- Page footer (`PageNumberEvent.onEndPage`) — rebuilt as a single cell with
  `RUN_DIRECTION_RTL` containing one logical-order `Phrase`
  (`"صفحة N من "` + inline template image). The total page count is still filled
  at `onCloseDocument`, and under RTL layout it lands to the left of `من`, i.e.
  `صفحة 1 من 2`.
- Class Javadoc updated with the rule: never add Arabic as a bare
  `Paragraph`/`Phrase`; use `rtlTextRow` (or an RTL cell).
- Left unchanged because already correct: the bilingual header Arabic column,
  info rows, footer rows, and every data-table cell (they all use
  `RUN_DIRECTION_RTL`). The invoice PDF has no bare Arabic paragraph at all —
  its Arabic lives in the (already RTL) header and cells.

## Verification (no device)
Built a small JVM harness against the same `itextpdf` jar and the same bundled
Cairo fonts, reproducing the exact constructs, then inspected each page's text
runs and raw content stream:
- Bare `Paragraph "كشف حساب"` -> content run in **logical** order
  (`كشف حساب`) laid out left-to-right => visually mirrored (the reported bug).
- Same text in an RTL cell -> content run in shaped/reversed order
  (`ﺏﺎﺴﺣ ﻒﺸﻛ`) => renders correctly when read right-to-left.
- Mixed `"المحل: test majd - 0599123456 - 1,234.56 ₪"` in an RTL cell: the
  Latin/digits stay in one LTR run (no scrambling); reading RTL gives
  `المحل: test majd - 0599123456 - 1,234.56 ₪`.
- Footer, before: runs at the same line in order [total][من][N][صفحة], but the
  template digit sat on a different baseline offset from the words. After: one
  RTL run `[total][صفحة N من]` sharing a single baseline, order
  `صفحة N من M`.
- Latin-only text is byte-identical before/after (run direction does not touch
  Latin runs).

## Build
- `./gradlew testDebugUnitTest assembleDebug --console=plain --offline` ->
  BUILD SUCCESSFUL. All 97 unit tests pass (unchanged).
- Built via a `subst` drive letter (the `&` in the project path), mapping removed
  afterwards.
- Debug APK copied to `C:\Users\majd\Desktop\AlMajdAccounting-debug.apk`
  (7,415,683 bytes), built 2026-10-17.

## Noticed but not fixed
- The footer words/order were in fact already correct (each word was in an RTL
  cell); the visible defect was that the total-count template was drawn on a
  separate, offset run. It is now a single bidi run — this is a unification
  improvement, not a word-order fix.
- The PDFs were verified by inspecting the generated content streams on the JVM,
  not by viewing a rendered page (no device/emulator or PDF rasterizer here).
- Pre-existing and untouched: DB first open/migration on the main thread; money
  stored as `double`; hardcoded English error toasts in the pre-existing
  invoice-share code.

---

# Share the invoice PDF right after saving a new invoice

## Goal
In create mode only, after a new invoice is saved successfully, generate the
invoice PDF and open the system share sheet so the owner can send it to the
customer. Edit mode is unchanged.

## What changed
- `InvoiceFormActivity.saveInvoice` now captures the inserted/updated id and:
  - on success: toasts `invoice_saved`, calls `setResult(RESULT_OK)` **before**
    generating, remembers `savedInvoiceId`, then
    - **edit mode**: `finish()` exactly as before (no share sheet);
    - **create mode**: calls the new `generateAndShare(id)`.
  - on failure: same as before (`save_failed`, Save re-enabled).
- New `InvoiceFormActivity.generateAndShare(id)` (create mode only):
  - on the shared DB thread: `InvoiceDao.getById(id)` (so the invoice carries its
    items and its effective paid/remaining), `CustomerDao.getByStoreName`, then
    `PdfExporter.generateInvoicePdf(...)` — the same call `InvoiceDetailActivity`
    uses for sharing, reused as-is (no duplicated PDF code);
  - on the main thread: `ShareHelper.sharePdf(...)` and then `finish()`.
- Failure handling: if the DB read or the PDF throws, the invoice is already
  committed, so the form only shows `pdf_generation_failed` (never
  `save_failed`) and still closes with `RESULT_OK`.
- Duplicate protection: Save is disabled for the whole save **and** stays
  disabled through generation/share. `savedInvoiceId` is written to instance
  state; on recreation (rotation during generation) the form closes immediately
  with `RESULT_OK` instead of re-enabling Save.
- Threading unchanged: DB read + PDF on `AppExecutors.runOnDb`, UI/share on
  `AppExecutors.runOnMain`, every callback guarded by
  `isFinishing()/isDestroyed()`.
- No schema, accounting, backup/restore or threading-model changes; no new
  user-facing strings were needed (`invoice_saved`, `save_failed`,
  `pdf_generation_failed`, `share_error` already exist).

## Why finish immediately after launching the share sheet
`ShareHelper.sharePdf` is reused unchanged, so the chooser is launched with
`startActivity` (not for result). It is a separate activity and the PDF lives in
`cacheDir`, exposed through the `FileProvider` with a read grant, so nothing the
chooser needs depends on the form instance. Finishing right after launching is
therefore safe, needs no `requestCode`/`ActivityResultLauncher`, cannot leave the
form stuck if a share target never returns a result, and guarantees the caller
refreshes (`RESULT_OK`) even if the user never navigates back through the form.
Because `setResult(RESULT_OK)` runs before generation, pressing Back during
generation also refreshes the list without reporting a failure.

## Build
- `./gradlew testDebugUnitTest assembleDebug --console=plain --offline` ->
  BUILD SUCCESSFUL. All 97 unit tests pass (unchanged; no new pure logic).
- Built via a `subst` drive letter (the `&` in the project path), mapping removed
  afterwards.
- Debug APK copied to `C:\Users\majd\Desktop\AlMajdAccounting-debug.apk`
  (7,415,683 bytes), built 2026-10-17.

## Noticed but not fixed
- Rotating exactly while the PDF is being generated closes the form without
  opening the share sheet (the invoice is saved and the list refreshes; the PDF
  can still be shared from the invoice detail screen). Preferring "no duplicate"
  over "always share" is deliberate.
- The result of the share itself (a target app's success/failure) is not tracked;
  `ShareHelper`'s existing `share_error` toast covers the "no app can handle it"
  case.
- The same reuse pattern still exists as two small call sites
  (`InvoiceDetailActivity` and the form) that each do
  "load customer -> generateInvoicePdf"; only the heavy PDF code is shared.
- Pre-existing and untouched: DB first open/migration on the main thread; money
  stored as `double`; hardcoded English error toasts in the pre-existing
  invoice-share code.



## UI polish, part 1 — design system + dashboard (2026-10-19)

### Design system (`values/colors.xml`, `dimens.xml`, `styles.xml`)
- One brand color: the existing navy (`#1A3A6E`) for toolbars, primary buttons,
  FABs, accent bars and icon circles. The purple was removed everywhere
  (customer accent bar, all three FABs, invoice-detail accent bar/icons, ledger
  report menu item, dashboard customer tile/number); its colors and drawables
  were deleted. The green/teal icon circles were removed too — every dashboard
  navigation icon is now navy on one light circle.
- Semantic colors, named and reused: green (positive/paid/received/credit), red
  (debt/unpaid), amber (partial/warning/backup) and navy (neutral emphasis).
  Surface: white cards on `#F1F4F9`.
- Shapes: cards radius 16dp / elevation 1–2dp; buttons radius 14dp / 52dp;
  chips and badges fully rounded; no coloured card borders.
- One input style: outlined, 1dp `#CBD5E1`, radius 12dp, white, 52dp tall,
  16dp horizontal padding, grey hint, dark navy-grey text. Applied to the
  invoice form (store/date/paid/line items), payment form, customer form,
  settings and the sales/payment report filters; spinners and report date
  fields use the same drawable.
- Typography styles in `styles.xml`: `Title` 20sp bold, `SectionHeader` 16sp
  bold, `Body` 14sp, `Caption` 12sp grey, `NumberLarge` 22sp bold,
  `ToolbarTitle` 18sp bold. Nothing is below 12sp (`text_size_tiny` was 10sp).
- One toolbar style (`AppTheme.Toolbar`) applied to all 13 toolbars.

### Screens touched (styling only)
Dashboard, invoice form + line-item row, payment form, customer form, customer
list, invoice list, payment list, customer detail, invoice detail, settings,
report menu, sales report, payment report, ledger report, recent-invoice row.
All view ids and Java behaviour are unchanged; `DashboardActivity.java` was not
touched.

### Dashboard
- Header reduced to ~145dp: settings icon, Arabic company name (20sp bold),
  English name, greeting; navy gradient with 24dp bottom corners; decorative
  circles and the emoji placeholder removed.
- 2x3 equal stat cards (96dp), RTL order: sales, received / debts, customers /
  payments, invoices. Values are single-line 20sp bold with auto-size down to
  12sp so the inline ₪ never wraps; navy/green/red as specified.
- Full-width customer-credit card: label on the right, green value on the left.
- 2x2 navigation tiles, 88dp, white and borderless, navy icon in a 40dp light
  circle, 14sp bold labels.
- Recent-invoices card restyled (12dp rows, thin dividers, bold amounts); the
  backup reminder is kept and restyled with an amber background.

### Deliberate deviations / not done
- Invoice line-item fields use the same border, radius, white background and
  height but **4dp** horizontal padding instead of 16dp: at 360dp the quantity
  column is only ~29dp wide, so 16dp padding would leave no room for a digit.
  Name/quantity/price share the compact `InputTextBox.LineItem` variant.
- The report filter and grand-total bars received the 16dp radius and 12dp side
  margins as part of the card rule (no structural change).
- No new features or content, no DB/accounting/backup/threading changes, no
  Java changes, no new strings (all existing user-facing text unchanged).
- Not fixed (pre-existing): the spinner arrow is positioned physically on the
  right in RTL; money remains `double`; DB first open/migration on the main
  thread; hardcoded English error toasts in invoice sharing.

### Build
- `./gradlew testDebugUnitTest assembleDebug --console=plain --offline` →
  BUILD SUCCESSFUL; **97/97 unit tests pass**.
- Built through a temporary `subst` drive letter (the `&` in the project path);
  the mapping was removed afterwards.
- APK copied to `C:\Users\majd\Desktop\AlMajdAccounting-debug.apk`
  (7,395,865 bytes).

## UI polish, part 2 — clean button/card frames, one invoice-table header, فاتورة → طلبية (2026-10-19)

Branch `fix/ui-polish-2` (from `feature/ui-design-system`). Visual/text changes only;
no DB, accounting, backup/restore or threading changes.

### 1. Doubled grey frame around buttons and cards

**Cause.** The theme sets `materialButtonStyle` to `PrimaryButton`, and that style
set `android:elevation` (the platform attribute). Buttons using the Material
`Widget.MaterialComponents.Button.TextButton` / `.OutlinedButton` styles inherit
`elevation=0dp` from their own style chain, but they do **not** define
`android:elevation`. Android therefore fell back to the theme style's
`android:elevation=1dp`, drawing a second shadow inside the report cards and around
the outlined "add item" button. The same leak affected every explicit
TextButton/OutlinedButton.

**Fix.** `PrimaryButton` now uses the Material `elevation` attribute (`1dp`) instead
of `android:elevation`; every explicit TextButton/OutlinedButton in a layout also
sets `android:elevation="0dp"`. Result: one clean border/shadow per element.

Layouts fixed:
- `activity_report_menu.xml` — the three report cards' TextButtons (sales, payments, ledger)
- `activity_invoice_form.xml` — the "add item" OutlinedButton
- `activity_settings.xml` — the "restore from backup" OutlinedButton
- `dialog_customer_statement.xml` — the "view PDF" OutlinedButton
- `activity_customer_detail.xml` — both "view all" TextButtons (orders and payments)

These are all eight MaterialButtons with an explicit TextButton/OutlinedButton style
in the app.

### 2. Duplicate column header in the invoice detail

**Cause.** `activity_invoice_detail.xml` already contained the navy header row, and
`InvoiceDetailActivity.displayItemsTable()` added a second header row to
`table_items`. The two rows also had different weights (3/1/2/2 vs 2/1/1/1) and the
XML row had an extra 8dp padding, so they could not align even if both were visible.

**Fix.** Removed the programmatic header (and the now-unused `createHeaderCell`);
kept the navy row. The navy header and the data rows now both use weights
2.5 / 1.1 / 2.2 / 2.2, with no row padding. Header and numeric cells are single-line
with auto-size, and the subtotal cell is right-aligned like its header. `tv_total`
is `maxLines=1`. Verified at 360×640dp with 1,100.00 ₪ / 2,200.00 ₪ — no wrapping,
columns aligned.

### 3. فاتورة → طلبية

Changed the following 24 string keys (26 word occurrences) in
`app/src/main/res/values/strings.xml`; resource names are unchanged.

| Key | Old | New |
|---|---|---|
| `invoices` | الفواتير | الطلبيات |
| `new_invoice` | فاتورة جديدة | طلبية جديدة |
| `edit_invoice` | تعديل الفاتورة | تعديل الطلبية |
| `invoice_details` | تفاصيل الفاتورة | تفاصيل الطلبية |
| `invoice_saved` | تم حفظ الفاتورة | تم حفظ الطلبية |
| `no_invoices` | لا توجد فواتير | لا توجد طلبيات |
| `invoice_number_format` | فاتورة #%d | طلبية #%d |
| `select_invoice` | الفاتورة | الطلبية |
| `invoice_option_format` | فاتورة #%1$d - %2$s - متبقي %3$s | طلبية #%1$d - %2$s - متبقي %3$s |
| `payment_linked_note` | دفعة مرتبطة بالفاتورة رقم %1$d | دفعة مرتبطة بالطلبية رقم %1$d |
| `payment_excess_note` | فائض عن الفاتورة رقم %1$d | فائض عن الطلبية رقم %1$d |
| `overpayment_message` | المبلغ المدخل أكبر من المتبقي على الفاتورة. سيتم تسجيل %1$s على الفاتورة و%2$s كدفعة على الحساب. | المبلغ المدخل أكبر من المتبقي على الطلبية. سيتم تسجيل %1$s على الطلبية و%2$s كدفعة على الحساب. |
| `paid_now_overpayment_title` | المدفوع أكبر من إجمالي الفاتورة | المدفوع أكبر من إجمالي الطلبية |
| `paid_now_overpayment_message` | المبلغ المدفوع مقدماً أكبر من إجمالي الفاتورة. سيتم تسجيل %1$s على الفاتورة و%2$s كدفعة على الحساب. | المبلغ المدفوع مقدماً أكبر من إجمالي الطلبية. سيتم تسجيل %1$s على الطلبية و%2$s كدفعة على الحساب. |
| `invoice_payments_title` | الدفعات على هذه الفاتورة | الدفعات على هذه الطلبية |
| `no_invoice_payments` | لا توجد دفعات على هذه الفاتورة | لا توجد دفعات على هذه الطلبية |
| `stat_invoice_count` | عدد الفواتير | عدد الطلبيات |
| `nav_invoices` | الفواتير | الطلبيات |
| `recent_invoices` | آخر الفواتير | آخر الطلبيات |
| `no_recent_invoices` | لا توجد فواتير حديثة | لا توجد طلبيات حديثة |
| `restore_confirm_message` | سيتم استبدال بياناتك الحالية بالكامل ببيانات النسخة الاحتياطية.\n\nتاريخ النسخة: %1$s\nالعملاء: %2$d\nالفواتير: %3$d\nالدفعات: %4$d | سيتم استبدال بياناتك الحالية بالكامل ببيانات النسخة الاحتياطية.\n\nتاريخ النسخة: %1$s\nالعملاء: %2$d\nالطلبيات: %3$d\nالدفعات: %4$d |
| `statement_total_debit` | إجمالي الفواتير (مدين) | إجمالي الطلبيات (مدين) |
| `statement_type_invoice` | فاتورة | طلبية |
| `statement_payment_on_invoice` | على فاتورة #%1$d | على طلبية #%1$d |

Also changed in code:
- `PdfExporter`: the invoice info label "رقم الفاتورة:" → "رقم الطلبية:".
- The statement PDF's type column and "على فاتورة #N" reference use the renamed
  `statement_type_invoice` / `statement_payment_on_invoice` strings.
- `ShareHelper`: added a chooser-title overload; invoice shares now show
  "طلبية #N" as the share-sheet title instead of the generated file name.

Grammar: فاتورة and طلبية are both feminine, so forms such as "تم حفظ الطلبية",
"مرتبطة بالطلبية", "مسددة" stay correct; "بالفاتورة" → "بالطلبية" and
"على الفاتورة" → "على الطلبية" were checked.

### Intentionally left containing فاتورة / invoice

- **Existing payment notes already stored in the DB** (e.g. the old note
  "دفعة مرتبطة بالفاتورة رقم 1") are displayed verbatim on the invoice detail.
  New notes are written with the new string. Rewriting stored rows would be a data
  migration, which was out of scope.
- **Code identifiers, class names, string resource names, DB tables/columns and
  source file names** (`generateInvoicePdf`, `invoice_number_format`, `invoices`,
  `invoice_items`, `invoice_<id>.pdf`, ...) were not renamed, as requested.
- The generated invoice PDF file name remains `invoice_<id>.pdf`; the user-facing
  share-sheet title is the new Arabic one.
- Excel/CSV/report headers contained no Arabic "فاتورة" (they use generic labels
  such as اسم المحل / التاريخ / الإجمالي), so no header text changed there.
- The invoice PDF has no separate "فاتورة" title; its only occurrence was the
  "رقم الفاتورة:" label, now "رقم الطلبية:".

### Build
- `./gradlew testDebugUnitTest assembleDebug --console=plain --offline` →
  BUILD SUCCESSFUL; **97/97 unit tests pass**.
- Built through a temporary `subst` drive letter (the `&` in the project path); the
  mapping was removed afterwards.
- APK copied to `C:\Users\majd\Desktop\AlMajdAccounting-debug.apk`
  (7,431,205 bytes).

---

# Order discount + internal order notes (schema v3)

Branch: `feature/order-discount` (from `Dev-0`, which contains the previous
`fix/ui-polish-2` work at `18c97f2`). One fixed currency discount per order and
an internal notes field on the order form/detail. `invoices.total_amount` remains
the **net** amount after discount; no accounting, credit-allocation or statement
balance math was changed.

## 1. Schema v3 (`DatabaseHelper`)
- `SCHEMA_VERSION` is now `3`.
- `invoices` gains `discount REAL NOT NULL DEFAULT 0` in `onCreate` and through
  the new `MIGRATIONS[2]` step (`ALTER TABLE ...`), followed by
  `recreateViews(db)`.
- `invoice_view` exposes `inv.discount` (after `total_amount`). The view is
  recreated by the migration and by `onCreate`, never by dropping table data.
- Existing rows keep `discount = 0`, so their stored total/subtotal semantics do
  not change.

## 2. Backup compatibility
- `BackupManifest.MIN_SUPPORTED_SCHEMA_VERSION` stays `2`; validation was not
  weakened.
- A v2 or v3 manifest is accepted by the v3 app; a v4 manifest is rejected as
  `NEWER_VERSION`. Restoring a v2 database runs the normal 2 → 3 migration when
  the restored file is opened.
- `BackupManifestTest` now covers v2/v3 accepted and v4 rejected.

## 3. Model / DAO
- `Invoice.discount` (default `0`) and derived
  `getSubtotal() = round2(totalAmount + discount)`.
- `InvoiceDao` reads/writes `discount` and reuses the existing nullable
  `invoices.notes` column for internal notes. `getStatementMovements` also reads
  `discount`; it already read `notes` but never sent invoice notes to a statement.
- `total_amount` remains net. No repayment/credit logic reads the discount.

## 4. Order form (`InvoiceFormActivity` + layout)
- New "إضافة خصم" checkbox, a hidden outlined discount amount field, and a
  subtotal / discount / net / paid / remaining summary with live recalculation.
- Validation: `0 <= discount <= subtotal`; an invalid discount shows an error on
  the discount input and Save is blocked. Net is `round2(subtotal - discount)`.
- The overpayment check compares "paid now" against the **net** total; the
  existing confirmation dialog and linked + on-account split are unchanged.
- Edit mode loads `discount` and `notes`; the checkbox is checked when
  `discount > 0`; the paid-now input remains hidden and a read-only paid summary
  is shown instead.
- New multi-line notes field with hint "ملاحظات (اختياري)", max 500 characters,
  stored in `invoices.notes`, never printed/exported.
- Rotation saves/restores the checkbox, discount text and notes in both create
  and edit mode; double-tap Save protection and `NumberUtils.parseAmount` are
  preserved, and all amounts stay ASCII.

## 5. Display / PDF / statement
- **Order detail:** shows subtotal, discount only when `> 0`, net, paid and
  remaining; an internal notes card appears only when notes are non-empty.
- **Order PDF:** footer shows subtotal, discount only when `> 0`, net, paid and
  remaining in RTL-safe cells; the existing customer-credit line is untouched.
  Notes are not included.
- **Statement:** debit remains the net `total_amount`. When `discount > 0`, the
  reference is `#N (بعد خصم X)`; `StatementBuilder` carries the discount through
  `InvoiceMovement` → `Row` without using it in balance math. Notes are not
  included.
- **Sales reports / Excel / CSV:** totals remain net; no new columns and no
  notes, so report output is unchanged.

## 6. Strings
- New Arabic resources: `invoice_subtotal`, `discount`, `add_discount`,
  `discount_hint`, `discount_invalid`, `invoice_notes`, `notes_optional_hint`,
  `statement_invoice_ref_with_discount`. All user-facing text uses "طلبية", not
  "فاتورة".

## 7. Tests
- New Android-free `util/InvoiceTotals.java` and `InvoiceTotalsTest` (6 tests):
  no discount; discount equal to subtotal; discount greater than subtotal;
  negative discount; two-decimal rounding (`0.1 + 0.2`); paid-now greater than
  net (remaining never negative).
- `StatementBuilderTest` adds a discount-row test proving the debit/balance stay
  net while the discount is carried for the reference annotation.
- `BackupManifestTest` adds the v2/v3 accepted + v4 rejected matrix.
- `./gradlew testDebugUnitTest assembleDebug` → BUILD SUCCESSFUL, **105/105
  tests pass** (97 prior + 8 new).

## Files touched
Added:
- `app/src/main/java/com/almajd/accounting/util/InvoiceTotals.java`
- `app/src/test/java/com/almajd/accounting/util/InvoiceTotalsTest.java`

Modified:
- `app/src/main/java/com/almajd/accounting/db/DatabaseHelper.java`
- `app/src/main/java/com/almajd/accounting/db/InvoiceDao.java`
- `app/src/main/java/com/almajd/accounting/model/Invoice.java`
- `app/src/main/java/com/almajd/accounting/ui/invoice/InvoiceFormActivity.java`
- `app/src/main/java/com/almajd/accounting/ui/invoice/InvoiceDetailActivity.java`
- `app/src/main/java/com/almajd/accounting/ui/customer/CustomerDetailActivity.java`
- `app/src/main/java/com/almajd/accounting/util/PdfExporter.java`
- `app/src/main/java/com/almajd/accounting/util/StatementBuilder.java`
- `app/src/main/res/layout/activity_invoice_form.xml`
- `app/src/main/res/layout/activity_invoice_detail.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/almajd/accounting/util/StatementBuilderTest.java`
- `app/src/test/java/com/almajd/accounting/backup/BackupManifestTest.java`

## Deliberately not done
- No percentage discounts, no per-line discounts, no discount in sales reports or
  exports, and no invoice notes in any PDF/statement/report/export.
- No changes to payment allocation, customer credit, ledger balances, report
  totals or the existing split-payment behavior.
- The discount is stored as a `double` like every other amount; no currency type
  was introduced.

## Build
- `./gradlew testDebugUnitTest assembleDebug --console=plain` →
  BUILD SUCCESSFUL.
- Built via a temporary `subst` drive letter (the `&` in the project path);
  the mapping was removed afterwards.
- APK copied to `C:\Users\majd\Desktop\AlMajdAccounting-debug.apk`
  (7,443,882 bytes), built 2026-10-19.

## Inline payment editing, bottom navigation and customer quick actions

## What changed
- Edit-order screen now loads the order's linked payments on the DB thread and
  shows an editable "الدفعات" card: outlined amount field, tappable date, delete
  icon, and "إضافة دفعة". Rows are pending in memory until Save; deleting asks
  for confirmation and only hides the row.
- Edit-mode summary is live: paid = sum of pending rows (deleted and blank new
  rows excluded), remaining = max(net - paid, 0). If paid exceeds net, an
  informational line is shown and Save is still allowed (no automatic split).
- Save validates every pending row (positive amount, valid yyyy-MM-dd date) and
  writes invoice + items + payment updates/inserts/deletes in one DB transaction.
  Existing payments update only amount/date (invoice link and note untouched);
  new payments get invoice_id and `payment_linked_note`. Failures roll back and
  keep the form open with everything unsaved.
- Pending rows (ids, amounts, dates, deleted/new flags) survive rotation and
  process death via instance state.
- Added a shared bottom navigation bar (Dashboard, Customers, New order, Reports)
  through `include_bottom_nav.xml`, `menu_bottom_nav.xml` and `BottomNavHelper`.
  Main tabs keep a shallow stack: Back from Customers/Reports returns to
  Dashboard, Back from Dashboard exits. New order opens the form without
  changing the selected tab and without finishing the current screen.
- Customer list reserves bottom space for the bar and lifts its FAB above it;
  Dashboard and Reports put the bar outside their scroll/content area.
- Customer detail now has fixed "طلبية جديدة" and "دفعة جديدة" actions that open
  the forms with the customer pre-selected and reload the customer after
  RESULT_OK. The scrollable content is weighted above the fixed bar.
- Accounting semantics, schema/SCHEMA_VERSION, backup/restore and report/export
  behavior are unchanged. All new Arabic text lives in `strings.xml` and uses
  "طلبية" rather than "فاتورة".

## Tests
- Added `PendingPaymentTotalsTest` (6 tests): edited/new/deleted/blank rows,
  0.1 + 0.2 rounding, paid > net overpayment, and null rows.
- `./gradlew testDebugUnitTest assembleDebug --console=plain` -> BUILD
  SUCCESSFUL, **111/111 tests pass** (105 prior + 6 new).

## Build
- Built through a temporary `subst` drive because the project path contains `&`;
  the mapping was removed afterwards.
- Copied to `C:\Users\majd\Desktop\AlMajdAccounting-debug.apk`
  (7,450,596 bytes), built 2026-10-20.
