# Notification Flow

## New transaction notification

After a real-time SMS is parsed and inserted, `TransactionNotificationManager` creates the notification channel if needed and posts a transaction notification containing amount, merchant, and bank context.

Notification actions are delivered to `TransactionNotificationReceiver` through explicit `PendingIntent` objects.

## Supported actions

- Categorize the transaction.
- Create a category.
- Rename the merchant.
- Mark the transaction processed.
- Open the app at the relevant top-level screen.

## Action handling

```text
Notification action
  -> TransactionNotificationReceiver
  -> ExpenseRepository lookup/update
  -> cancel or refresh notification
  -> CATEGORY_UPDATED or DATA_CHANGED broadcast
  -> visible screens refresh
```

Android 13 and later require `POST_NOTIFICATIONS`, requested by `MainActivity` after SMS permission handling.

## Settings

`NotificationsFragment` stores user toggles in SharedPreferences. These preferences should be checked at the point notifications are created so UI settings and delivery remain consistent.

## Key sources

- `notifications/TransactionNotificationManager.kt`
- `notifications/TransactionNotificationReceiver.kt`
- `ui/profile/NotificationsFragment.kt`
- `MainActivity.kt`
