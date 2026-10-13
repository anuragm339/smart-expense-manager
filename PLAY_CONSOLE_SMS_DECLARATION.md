# Google Play SMS permission declaration

## Use case to select

Select **SMS-based money management** for both `READ_SMS` and `RECEIVE_SMS`.

Do **not** select **Default SMS handler**. Smart Expense Manager is a budgeting and
expense-tracking app; it is not a messaging app and does not send or manage SMS
conversations as the device's default handler.

## Core-functionality description

Use the following text in the Permissions Declaration Form:

> Smart Expense Manager is an SMS-based money-management app. Its primary feature
> automatically identifies bank and payment transaction messages, extracts the
> transaction date, amount, merchant, bank, and debit/credit type, and stores the
> resulting transaction record on the user's device. These records power the app's
> expense dashboard, automatic categorization, budgets, recurring-payment detection,
> spending comparisons, and transaction alerts. READ_SMS imports recent transaction
> history and RECEIVE_SMS detects new transactions as they arrive. The app is not a
> default SMS handler, does not send SMS, does not sell SMS data, and does not use it
> for advertising. Unrelated messages are rejected by the on-device transaction parser.

## Review instructions

1. Install a fresh build and sign in with the test credentials supplied in Play Console.
2. The app displays an in-app SMS data disclosure before the Android permission prompt.
3. Tap **Allow SMS access**, then grant `READ_SMS` and `RECEIVE_SMS` in the system prompt.
4. The app scans recent SMS on-device and adds recognized bank/payment transactions.
5. Open **Messages** to review imported transactions and **Dashboard** to see the
   resulting totals, categories, comparisons, and budget information.
6. A new recognized bank transaction SMS is processed automatically and appears in
   the app with a transaction notification.

If the review account requires authentication, provide a working dedicated test
account. Do not tell the reviewer that all functionality is unrestricted.

## Required demonstration video

Record an unedited video from a fresh install showing:

1. Sign-in with the review account.
2. The complete in-app disclosure text.
3. Tapping **Allow SMS access**.
4. Granting both Android SMS permissions.
5. The SMS scan completing.
6. Recognized transactions in **Messages** and their totals/categories in
   **Dashboard**.
7. If practical, receipt of a test bank-style transaction SMS and the resulting new
   transaction notification.

Make the video link viewable without requesting access.

## Store listing and other declarations

- Use a title of **Smart Expense Manager** for the default and translated listings.
- Prominently state that automatic expense tracking reads bank/payment transaction SMS.
- Use the revised privacy wording from `play_store_description.txt`.
- Update the Data safety form to match actual behavior, including account information
  used for sign-in and financial transaction details sent for optional online AI
  Insights.
- Add a public, non-editable privacy-policy URL in Play Console and link or display the
  policy inside the app. The policy must cover data access, use, sharing, security,
  retention, deletion, and the support contact.
- Deactivate obsolete test-track bundles that contain a conflicting permission
  declaration, then submit the new bundle and a revised Permissions Declaration Form.
