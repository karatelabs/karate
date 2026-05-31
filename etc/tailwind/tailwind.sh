#!/bin/bash
# Compile karate-core/src/main/resources/io/karatelabs/output/res/karate-report.css
# from input.css + tailwind.config.js using the Tailwind standalone CLI (no node /
# npm toolchain).
#
# First run downloads the CLI binary to etc/tailwind/.cache/ (gitignored).
# Subsequent runs reuse it. Pass `--watch` to keep rebuilding on template / config
# changes during local iteration.
#
# CI invokes this and then `git diff --exit-code` on the output file — a stale
# CSS in main fails the build. See etc/tailwind/README.md.

set -e

# Run from repo root regardless of caller's cwd (script lives at etc/tailwind/).
cd "$(dirname "$0")/../.."

TAILWIND_VERSION="v3.4.17"
CACHE_DIR="etc/tailwind/.cache"
BIN_PATH="$CACHE_DIR/tailwindcss-$TAILWIND_VERSION"

OS=$(uname -s | tr '[:upper:]' '[:lower:]')
ARCH=$(uname -m)
case "$OS-$ARCH" in
    darwin-arm64)   PLATFORM="macos-arm64" ;;
    darwin-x86_64)  PLATFORM="macos-x64" ;;
    linux-aarch64)  PLATFORM="linux-arm64" ;;
    linux-x86_64)   PLATFORM="linux-x64" ;;
    *) echo "unsupported platform: $OS-$ARCH" >&2; exit 1 ;;
esac

if [[ ! -x "$BIN_PATH" ]]; then
    mkdir -p "$CACHE_DIR"
    URL="https://github.com/tailwindlabs/tailwindcss/releases/download/$TAILWIND_VERSION/tailwindcss-$PLATFORM"
    echo "downloading tailwind cli from $URL..."
    curl -fsSL "$URL" -o "$BIN_PATH"
    chmod +x "$BIN_PATH"
fi

CONFIG="etc/tailwind/tailwind.config.js"
INPUT="etc/tailwind/input.css"
OUTPUT="karate-core/src/main/resources/io/karatelabs/output/res/karate-report.css"

if [[ "$1" == "--watch" ]]; then
    exec "$BIN_PATH" -c "$CONFIG" -i "$INPUT" -o "$OUTPUT" --watch
fi

"$BIN_PATH" -c "$CONFIG" -i "$INPUT" -o "$OUTPUT" --minify
