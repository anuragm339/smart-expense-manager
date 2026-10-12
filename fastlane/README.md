fastlane documentation
----

# Installation

Make sure you have the latest version of the Xcode command line tools installed:

```sh
xcode-select --install
```

For _fastlane_ installation instructions, see [Installing _fastlane_](https://docs.fastlane.tools/#installing-fastlane)

# Available Actions

## Android

### android validate_play_credentials

```sh
[bundle exec] fastlane android validate_play_credentials
```

Validate the configured Google Play service-account key

### android build_release

```sh
[bundle exec] fastlane android build_release
```

Run checks and build a signed release AAB with a specific Play version code

### android upload_listing

```sh
[bundle exec] fastlane android upload_listing
```

Upload only the synchronized en-US Play Store listing and icon

### android upload_internal

```sh
[bundle exec] fastlane android upload_internal
```

Upload the current AAB and listing to an internal-track draft

### android upload_release

```sh
[bundle exec] fastlane android upload_release
```

Upload the current AAB and listing to a selected Play track

### android promote_to_production

```sh
[bundle exec] fastlane android promote_to_production
```

Promote an approved internal release to production

----

This README.md is auto-generated and will be re-generated every time [_fastlane_](https://fastlane.tools) is run.

More information about _fastlane_ can be found on [fastlane.tools](https://fastlane.tools).

The documentation of _fastlane_ can be found on [docs.fastlane.tools](https://docs.fastlane.tools).

## One-command publishing

From the repository root, publish a new version as an internal-track draft:

```sh
./publish_play_store.sh 149
```

To create a production draft without rolling it out:

```sh
./publish_play_store.sh 149 production draft
```

A completed production rollout has an explicit safety check:

```sh
CONFIRM_PLAY_PRODUCTION=YES ./publish_play_store.sh 149 production completed
```

The version code must be new and unused in Google Play. Update the matching file
under `fastlane/metadata/android/en-US/changelogs/` when release notes change.
