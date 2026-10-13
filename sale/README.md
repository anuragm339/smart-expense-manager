# Smart Expense Manager sale package

This directory contains the working material for an **exclusive full-asset sale**
of Smart Expense Manager. It is intentionally separated into public information
and confidential due-diligence information.

## Before the app is listed

1. Wait for the current Google Play production review to finish.
2. Resolve every item marked `BLOCKER` in `DUE_DILIGENCE_CHECKLIST.md`.
3. Fill in every `[SELLER TO PROVIDE]` field in `PUBLIC_LISTING_DRAFT.md`.
4. Export verifiable Play Console metrics and operating costs.
5. Have an Indian-qualified lawyer review the asset-purchase agreement, tax
   treatment, warranties, and intellectual-property assignment.

## Disclosure sequence

1. **Public listing:** Share only `PUBLIC_LISTING_DRAFT.md` and approved product
   screenshots/video.
2. **Qualified buyer:** Confirm identity, acquisition budget, intended use, and
   ability to operate an Android/fintech product.
3. **NDA signed:** Share the prospectus, architecture book, redacted metrics,
   dependency inventory, and build demonstration.
4. **Letter of intent and proof of funds:** Provide read-only source access and
   detailed technical due diligence. Never include credential files.
5. **Escrow funded and definitive agreement signed:** Follow
   `TRANSFER_RUNBOOK.md`.

## Suggested sales channel

Use a mobile-app marketplace first, with direct outreach running in parallel.
Flippa accepts original Android apps that are live on Google Play and requires
the source code to be included. The listing should therefore be created only
after the production release is approved. Acquire.com can be tested as a second
channel, but a pre-revenue mobile asset may fit Flippa's app-specific workflow
better.

## Non-negotiable security rules

- Never email or upload service-account JSON keys, signing passwords, Firebase
  credentials, user SMS data, or database exports.
- Grant temporary, least-privilege access to a clean due-diligence repository.
- Transfer the Play app through Google's official app-transfer process; do not
  sell or share a personal Google account.
- Rotate the upload key and every cloud/API credential during closing.
- Release source code and administrative ownership only after cleared funds are
  secured in escrow and the sale agreement is effective.
