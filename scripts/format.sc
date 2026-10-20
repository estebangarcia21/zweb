#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
sbt scalafmtAll scalafmtSbt

dprint fmt
