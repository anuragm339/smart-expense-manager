#!/usr/bin/env bash

set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly DEFAULT_KEY_PATH="${XDG_CONFIG_HOME:-${HOME}/.config}/google-play/smart-expense-ai-9feea-c31e07242a23.json"

usage() {
    cat <<'EOF'
Build and upload a new Smart Expense Manager version to Google Play.

Usage:
  ./publish_play_store.sh <version-code> [track] [status]

Arguments:
  version-code  A new, unused positive integer (for example, 149)
  track         internal, alpha, beta, or production (default: internal)
  status        draft or completed (default: draft)

Examples:
  ./publish_play_store.sh 149
  ./publish_play_store.sh 149 internal completed
  ./publish_play_store.sh 149 production draft
  CONFIRM_PLAY_PRODUCTION=YES ./publish_play_store.sh 149 production completed

Environment:
  PLAY_STORE_JSON_KEY       Absolute path to the Play service-account JSON key.
                            Defaults to the key under ~/.config/google-play.
  CONFIRM_PLAY_PRODUCTION   Must equal YES for a completed production rollout.

The script validates credentials, runs unit tests and lint, builds the signed AAB,
and uploads the bundle plus Play listing metadata. It defaults to a safe draft on
the internal track.
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    usage
    exit 0
fi

if [[ $# -lt 1 || $# -gt 3 ]]; then
    usage >&2
    exit 2
fi

readonly VERSION_CODE="$1"
readonly PLAY_TRACK_NAME="${2:-internal}"
readonly PLAY_RELEASE_STATE="${3:-draft}"

if [[ ! "$VERSION_CODE" =~ ^[1-9][0-9]*$ ]]; then
    echo "Error: version-code must be a positive integer." >&2
    exit 2
fi

case "$PLAY_TRACK_NAME" in
    internal|alpha|beta|production) ;;
    *)
        echo "Error: track must be internal, alpha, beta, or production." >&2
        exit 2
        ;;
esac

case "$PLAY_RELEASE_STATE" in
    draft|completed) ;;
    *)
        echo "Error: status must be draft or completed." >&2
        exit 2
        ;;
esac

if [[ "$PLAY_TRACK_NAME" == "production" && "$PLAY_RELEASE_STATE" == "completed" && "${CONFIRM_PLAY_PRODUCTION:-}" != "YES" ]]; then
    echo "Error: set CONFIRM_PLAY_PRODUCTION=YES for a completed production rollout." >&2
    exit 2
fi

if [[ -z "${PLAY_STORE_JSON_KEY:-}" ]]; then
    export PLAY_STORE_JSON_KEY="$DEFAULT_KEY_PATH"
fi

if [[ ! -f "$PLAY_STORE_JSON_KEY" ]]; then
    echo "Error: Google Play service-account key not found: $PLAY_STORE_JSON_KEY" >&2
    echo "Set PLAY_STORE_JSON_KEY to its absolute path and try again." >&2
    exit 1
fi

# macOS places its old system Ruby before Homebrew Ruby. Prefer the project-
# compatible Homebrew installation when it is available.
if command -v brew >/dev/null 2>&1; then
    BREW_RUBY_BIN="$(brew --prefix ruby 2>/dev/null)/bin"
    if [[ -x "$BREW_RUBY_BIN/bundle" ]]; then
        export PATH="$BREW_RUBY_BIN:$PATH"
    fi
fi

if ! command -v bundle >/dev/null 2>&1; then
    echo "Error: Bundler is not installed or is not on PATH." >&2
    exit 1
fi

cd "$SCRIPT_DIR"

if ! bundle check >/dev/null 2>&1; then
    echo "Error: Ruby dependencies are missing. Run: bundle install" >&2
    exit 1
fi

echo "Publishing Smart Expense Manager"
echo "  Version code: $VERSION_CODE"
echo "  Track:        $PLAY_TRACK_NAME"
echo "  Status:       $PLAY_RELEASE_STATE"

bundle exec fastlane android validate_play_credentials
bundle exec fastlane android build_release build_number:"$VERSION_CODE"
bundle exec fastlane android upload_release \
    track:"$PLAY_TRACK_NAME" \
    release_status:"$PLAY_RELEASE_STATE"

echo "Google Play upload completed successfully."
