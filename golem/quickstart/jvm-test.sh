#!/usr/bin/env bash
set -euo pipefail

sbt zioGolemQuickstartJS/golemDeploy
#sbt zioGolemQuickstartJS/golemDeployUpdate
sbt 'zioGolemQuickstartJVM/runMain cloud.golem.quickstart.QuickstartClient'