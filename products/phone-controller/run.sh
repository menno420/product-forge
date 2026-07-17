#!/usr/bin/env bash
# phone-controller — Slice 1 demo. Runs the capability-probe verdict engine over a set
# of representative device scenarios and prints the receiver-compatibility matrix.
# Pass CLI flags through to evaluate one explicit probe, e.g.:
#   ./run.sh --platform android --api 34 --hid-role --ble-peripheral
set -euo pipefail
cd "$(dirname "$0")"
exec python3 cli.py "$@"
