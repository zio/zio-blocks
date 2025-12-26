#!/usr/bin/env bash
set -euo pipefail

sbt zioGolemQuickstartJS/golemDeploy
sbt 'zioGolemQuickstartJS/golemAppRunScript golem/quickstart/script-test.rib'