# Export, Profile, and Settings

## Profile

`ProfileFragment` displays user and budget state through `ProfileViewModel`. It links to settings, notification preferences, export, duplicate cleanup, and logout.

Logout delegates to `AuthManager`, clears the active session, and returns to the login flow.

## Export

```text
ExportDataFragment
  -> Android document destination picker
  -> DataExportService or fragment generator
  -> Room transaction/category/merchant reads
  -> CSV, JSON, or report output
```

JSON export can include raw SMS text, categories, and merchants. CSV exports transaction summaries. The export UI also supports date ranges and report-oriented formats.

Because exports contain financial and SMS data, the user-selected URI is the security boundary; generated files should not be written to broad shared paths without explicit selection.

## Settings and maintenance

- Budget goal navigation.
- Notification toggles.
- Duplicate transaction cleanup.
- SMS rescan and data maintenance actions.
- App information and authentication controls.

## Key sources

- `ui/profile/ProfileFragment.kt`
- `ui/profile/ProfileViewModel.kt`
- `ui/profile/SettingsFragment.kt`
- `ui/profile/ExportDataFragment.kt`
- `services/DataExportService.kt`
- `ui/settings/CleanupDuplicatesDialog.kt`
