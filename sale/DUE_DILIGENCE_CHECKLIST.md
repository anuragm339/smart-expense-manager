# Due-diligence checklist

## Release and security blockers

- [ ] **BLOCKER:** Google Play production release is approved and the app is in
      good standing with no unresolved policy enforcement.
- [ ] **BLOCKER:** Remove signing passwords from tracked Gradle files, rotate the
      upload credential, and configure local/CI secret injection.
- [ ] **BLOCKER:** Remove the trust-all TLS manager and permissive hostname
      verifier from the production network client.
- [ ] **BLOCKER:** Disable HTTP BODY logging in release builds and verify no
      transaction data appears in logs.
- [ ] **BLOCKER:** Confirm production network-security settings do not allow
      unnecessary cleartext traffic.
- [ ] **BLOCKER:** Rotate Google service-account credentials before granting any
      buyer access.
- [ ] Run `./gradlew test lint bundleRelease` on the exact diligence commit.
- [ ] Demonstrate a clean build from a fresh checkout using documented secrets.
- [ ] Add regression tests for SMS parsing, Room migrations, transaction edits,
      graph refresh, and permission/onboarding flows.

## Ownership and legal

- [ ] Identify the legal seller and provide government/company details privately.
- [ ] Confirm that every contributor/contractor assigned all code and design IP.
- [ ] Confirm the app name, icon, screenshots, rules, and copy are original or
      properly licensed.
- [ ] Generate a third-party software/license inventory.
- [ ] Confirm rights to the domain, support email, social accounts, and backend.
- [ ] Prepare an asset-purchase agreement with warranties, exclusions,
      indemnities, tax allocation, and transition obligations.
- [ ] Prepare an asset schedule that says exactly what is included and excluded.
- [ ] Prepare privacy-policy and data-processing records; do not transfer user
      SMS content or private database exports as sale collateral.

## Product and metrics

- [ ] Export Play Console acquisition, audience, retention, rating, Android
      vitals, and crash reports with dates visible.
- [ ] Export monthly revenue and expense evidence, including zero-revenue months.
- [ ] Record a five-minute product walkthrough on a clean test account/device.
- [ ] Capture approved screenshots of every primary feature.
- [ ] Document supported banks/message formats and known parser limitations.
- [ ] Document current bugs, roadmap, owner workload, and support volume.
- [ ] Document AI-backend uptime, costs, data flow, and failure behavior.

## Buyer qualification

- [ ] Verify buyer/company identity and beneficial owner.
- [ ] Request intended use, technical capability, budget, and closing timeline.
- [ ] Execute a mutual NDA before confidential disclosure.
- [ ] Obtain proof of funds before source access.
- [ ] Use a separate read-only diligence repository without secrets or user data.
- [ ] Keep an access log and revoke access for inactive buyers.

## Offer comparison

- [ ] Purchase price and currency
- [ ] Escrow provider and fee allocation
- [ ] Upfront versus contingent/earn-out consideration
- [ ] Assets and liabilities assumed
- [ ] Required representations, indemnity cap, and survival period
- [ ] Transition-support hours and rate for additional work
- [ ] Non-compete/non-solicit scope, geography, and duration
- [ ] Closing conditions and target date
