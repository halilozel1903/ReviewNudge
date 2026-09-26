#!/usr/bin/env bash
# Captures README screenshots of the sample app on a running emulator.
set -euo pipefail
source "$(dirname "$0")/screenshot-lib.sh"

install_sample
for mode in light dark; do
  set_night_mode "$mode"
  fresh_launch                           # first launch: counters just started
  capture "blocked-$mode"
  fresh_launch --ez seedDemo true        # enough launches and events: eligible
  capture "eligible-$mode"
done
