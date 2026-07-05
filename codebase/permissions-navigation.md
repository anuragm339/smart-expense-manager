# Permissions and Navigation

## Main activity setup

After authentication, `MainActivity` inflates `activity_main.xml`, obtains the `NavController`, and attaches the bottom navigation menu.

The five top-level destinations are:

- Dashboard
- Insights
- Messages
- Categories
- Profile

Secondary destinations include category details, category transactions, merchant transactions, transaction details, settings, budgets, notification settings, and export.

## Permission sequence

```text
MainActivity.onCreate()
  -> request READ_SMS and RECEIVE_SMS
  -> request POST_NOTIFICATIONS on Android 13+
  -> offer battery optimization exemption
  -> scan historical SMS after SMS permission is granted
```

If SMS permission is denied, the app remains usable but automatic ingestion and history scanning are unavailable.

## Navigation inputs

The navigation graph passes primitive arguments for transaction and merchant detail screens. Transaction details currently receive display fields such as amount, merchant, category, date, confidence, and raw SMS rather than only a database ID.

## Notification entry

`MainActivity.handleNotificationIntent()` reads action and transaction extras. It can open Messages, select Categories, or show merchant/category actions.

## Key sources

- `MainActivity.kt`
- `res/navigation/nav_graph.xml`
- `res/menu/bottom_nav_menu.xml`
- `res/layout/activity_main.xml`
