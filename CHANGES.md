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
