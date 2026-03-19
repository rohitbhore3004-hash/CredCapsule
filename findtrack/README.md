# FindTrack — Wallet Balance Tracker

**FindTrack** is an Android app that helps you track your spending and manage your wallet balance. Enter your current wallet balance, then record every expense — FindTrack shows your remaining balance in real time so you always know how much money you have left.

> Forked from [buckwheat](https://github.com/danilkinkin/buckwheat) (GPL v3) and redesigned as a wallet balance tracker.

---

## Key Features

- **Wallet Balance** — Enter your current wallet or account balance
- **Real-time Remaining Balance** — See exactly how much money is left as you add expenses
- **Expense History** — Full transaction history with tags and dates
- **Analytics** — Charts and spending breakdowns
- **Widgets** — Home screen widgets showing remaining balance at a glance
- **CSV Export** — Export spending history to CSV
- **No login required** — Fully offline and private, zero data collection
- **Material You** — Dynamic theming, adapts to your system color palette

## What Changed from Buckwheat

| Buckwheat | FindTrack |
|-----------|-----------|
| Set monthly/trip budget | Set wallet balance (no time limit) |
| Daily budget calculation | Remaining balance tracking |
| Period end date required | No date required |
| Budget distribution dialog | Automatic balance carry-over |

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material You
- **Architecture**: MVVM + Hilt DI
- **Storage**: Room DB + DataStore
- **Min SDK**: 29 (Android 10)

## License

GNU GPL v3 — same as the original buckwheat project.
