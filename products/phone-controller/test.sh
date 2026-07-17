#!/usr/bin/env bash
# phone-controller — run the full test suite (stdlib unittest, dependency-free).
# Exit 0 = green.
set -euo pipefail
cd "$(dirname "$0")"
exec python3 -m unittest discover -s tests -p 'test_*.py' -v
