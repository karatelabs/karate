#!/bin/bash
# Generate CAPABILITIES.md from CAPABILITIES.yaml
#
# Requirements: Python 3 with PyYAML
#   pip install pyyaml
#
# Usage:
#   ./etc/generate-capabilities.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"

# Check for Python
if ! command -v python3 &> /dev/null; then
    echo "Error: python3 is required"
    exit 1
fi

# Check for PyYAML
if ! python3 -c "import yaml" 2>/dev/null; then
    echo "Installing PyYAML..."
    pip3 install pyyaml==6.0.2 --require-hashes --no-deps \
        --hash=sha256:d584d9ec91ad65861cc08d42e834324ef890a082e591037abe114850ff7bbc3e
fi

# Run the generator
python3 "$SCRIPT_DIR/generate-capabilities.py"
