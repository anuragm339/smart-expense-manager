# Confidential buyer prospectus

Share this document only with a qualified buyer under NDA.

## Acquisition thesis

Smart Expense Manager gives a buyer a functioning Android fintech foundation
instead of a greenfield build. Its differentiating component is the end-to-end
data path from financial SMS ingestion through parsing, deduplication,
categorization, local persistence, correction workflows, and reporting. The
buyer can use it as a consumer product, white-label base, or parser/UI component
inside a broader financial offering.

The current value is primarily **replacement cost, product IP, and time to
market**. Until meaningful revenue and retention exist, it should not be valued
as a cash-flowing business.

## Product architecture

```text
Financial SMS / manual entry
        -> ingestion and parser rules
        -> deduplication and merchant normalization
        -> Room database
        -> dashboard, messages, categories, budgets, exports, insights
```

The repository contains approximately 180 Kotlin source files and 160 Android
resource files, plus a feature-oriented architecture book. Exact counts may
change before closing and should be regenerated for due diligence.

## Transferable strengths

- Native Android implementation rather than a thin web wrapper
- On-device transaction storage and parsing pipeline
- Extensible JSON-backed bank and merchant rules
- Multiple correction paths for merchant name, category, and transaction state
- Existing Play package and listing history
- Current Android API-level support
- Release automation and maintained architecture documentation
- Clear monetization extensions through advertising, premium insights, or B2B
  licensing

## Operational dependencies

- Google Play Console application and policy declarations
- Firebase/Google Cloud project for authentication and Google integrations
- Azure-hosted endpoint used by optional AI insights
- Signing/upload keys and Play App Signing
- Support email and any domain/website used in store disclosures

The Android repository does **not** contain the server-side implementation of
the Azure AI endpoint. Backend source and cloud ownership must be separately
confirmed and named in the final asset schedule.

## Current risk disclosure

The seller should disclose these items proactively rather than allowing a buyer
to discover them late:

- Production approval is pending following a previous policy rejection relating
  to listing identity and SMS permission declarations.
- The app depends on restricted SMS permissions and must continue to satisfy the
  permitted money-management use case and disclosure requirements.
- Automated test coverage is currently minimal and needs investment.
- Release signing secrets have existed in build configuration and must be
  removed and rotated before source disclosure.
- A legacy network client disables TLS certificate/hostname verification and
  enables BODY logging; this must be corrected before a buyer-ready build.
- The optional backend source, operating costs, data-processing terms, and
  transferability remain to be documented.
- Third-party dependency licenses and contributor/IP assignments require a
  formal inventory.

## Recommended positioning

Position the acquisition as a working, documented Android fintech product with
a reusable SMS-to-expense engine. Do not position it as a proven passive-income
business until revenue and retention evidence exists.

## Information requested from the seller

- Legal name and entity selling the assets
- Confirmation that all contributors assigned their IP rights
- Development spend and approximate engineering hours
- Exact Play installs, MAU/DAU, retention, geography, ratings, and crash rate
- Revenue and operating expenses by month
- Ownership of the Azure backend, Firebase project, domain, email, and artwork
- Desired price, minimum acceptable price, and preferred closing date
- Transition support offered after closing
