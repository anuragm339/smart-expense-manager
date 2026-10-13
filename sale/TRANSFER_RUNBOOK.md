# Asset transfer runbook

This is an operational checklist, not legal or tax advice. Use the definitive
sale agreement and professional advisers for the actual transaction.

## Before signing

1. Agree on the asset schedule, exclusions, price, escrow, support period, and
   treatment of the backend and domain.
2. Let the buyer validate a clean build from the diligence commit.
3. Verify the buyer has an active Google Play developer account and can satisfy
   any applicable organization/identity requirements.
4. Export reports that will not transfer with the Play app.
5. Freeze feature work and tag the agreed closing commit.

## After agreement and escrow funding

1. Transfer the Git repository to the buyer's organization or deliver an agreed
   signed archive, preserving commit history.
2. Add the buyer's designated administrators to Firebase/Google Cloud and Azure
   with least privilege, then transfer ownership according to each platform's
   supported process.
3. Transfer the domain, support mailbox, design assets, and documentation listed
   in the asset schedule.
4. Initiate Google's official app-transfer request. The target developer account
   must be active, and both parties will need registration/account details.
5. Recreate test groups and integrated-service linkages that Google does not
   transfer automatically.
6. Have the buyer replace service-account keys, OAuth credentials, backend
   secrets, support contacts, privacy-policy ownership, and any future AdMob IDs.
7. Reset/rotate the Play upload key if agreed, and retain Play App Signing rather
   than exchanging sensitive private signing material informally.
8. Buyer builds and uploads a validation release under their own credentials.

## Acceptance

- [ ] Buyer can build debug and signed release variants.
- [ ] Buyer can sign in and exercise the core app flow.
- [ ] Play Console transfer shows the buyer as the app owner.
- [ ] Firebase/Google Cloud and Azure billing belong to the buyer or have been
      replaced by buyer-owned projects.
- [ ] Store support/privacy contacts point to buyer-controlled destinations.
- [ ] All credentials have been rotated and seller access removed as agreed.
- [ ] Buyer signs the closing acceptance certificate and escrow is released.

## Seller retention and deletion

Retain only records required by law or the sale agreement. Delete buyer secrets,
transferred production data, and redundant source copies according to the agreed
post-closing schedule. Do not retain user SMS or financial records.
