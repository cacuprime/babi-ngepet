# Babi Ngepet

A private, offline personal finance app for Android, built for how people in Indonesia pay: QRIS, m-banking, e-wallets and cash. Bahasa Indonesia by default, English available in Settings.

**Your data never leaves your phone.** No account, no server, no ads, no paid AI. The database is encrypted (SQLCipher, key held by the Android Keystore), and backups are files you encrypt with your own password.

## Features

- **All your money in one view:** bank accounts, e-wallets, cash, savings, deposits, gold, stocks, mutual funds and government bonds.
- **Daily prices:** stocks (Yahoo Finance), mutual funds and bonds (Pasardana), and Pegadaian gold. This is the only internet use, and it can be turned off.
- **Automatic recording** from wondr (BNI), SeaBank and Livin' (Mandiri) payment notifications, into a review inbox.
- **Voice entry** ("bakso 25 ribu pakai cash") and **bulk add** (one transaction per line).
- **Import** of receipt photos, screenshots and bank statements (PDF, CSV, Excel), read on the phone with ML Kit.
- **Budgets:** split your salary into Needs, Wants and Savings by percent or fixed amounts, see today's allowance, and get over-budget warnings.
- **Insights:** a financial health score, rule-based notes (with an optional "roasting" tone), a spending calendar, an emergency-fund check, savings goals and Excel/CSV export.
- **Calculator keypad, tags, subcategories**, and a daily reminder.
- **App lock** (fingerprint, face or PIN) and a button to hide balances.

## Build

Requirements: JDK 17 and the Android SDK (compileSdk 35).

```
./gradlew :app:testDebugUnitTest      # unit tests
./gradlew :app:assembleDebug          # debug APK
```

For a signed release, create your own keystore with `keytool` and a `keystore.properties` file (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) in the project root. Both are git-ignored.

Optional: put a `starting_data.json` in `app/src/main/assets/` to pre-fill your accounts during onboarding (see `StartingData.kt`). It is git-ignored, so your balances stay private.

See `docs/INSTALL.md` (Indonesian) for installing on a phone.

## Privacy and security

- The notification reader only processes the three bank apps listed above; other notifications are ignored.
- Android cloud backup and device transfer are disabled. Use the in-app encrypted backup (AES-256-GCM, PBKDF2 with 210,000 rounds).
- Screenshots are blocked while the app lock is on.
- Exported Excel/CSV files are **not** encrypted. Keep them somewhere safe.

## Licence

MIT. See `LICENSE`. Provided as is, without warranty. This is not financial advice.
