#!/usr/bin/env bash
set -u

if [[ $# -ne 2 ]]; then
  echo "usage: $0 CAMPAIGN_ROOT DRIVER_ROOT" >&2
  exit 64
fi

root=$1
driver=$2
export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-arm64}
export BENCH_CPU_AFFINITY=${BENCH_CPU_AFFINITY:-0,1,2,3}
export BENCH_POWER_MODE=${BENCH_POWER_MODE:-virtual-machine-fixed-configuration}
export BENCH_TURBO=${BENCH_TURBO:-unavailable-arm64-virtual-cpu}
export BENCH_THERMAL_CEILING=${BENCH_THERMAL_CEILING:-no-guest-thermal-sensor}
export BENCH_LOAD_CEILING=${BENCH_LOAD_CEILING:-0.5}

campaigns=(
  sync-throughput async-throughput diagnostic-ratio
  sync-micro-throughput sync-micro-allocation
  async-micro-throughput async-micro-allocation
)

for name in "${campaigns[@]}"; do
  while ! python3 -c 'import os,sys; sys.exit(0 if os.getloadavg()[0]/4 <= .25 else 1)'; do sleep 15; done
  printf 'START %s %s\n' "$name" "$(date -u +%FT%TZ)" | tee -a "$root/protocol-status.log"
  taskset -c 0-3 python3 "$driver/streams-benchmark/tools/campaign.py" protocol \
    "$root/configs/$name.json" "$root/results-$name" >>"$root/$name.protocol.log" 2>&1
  rc=$?
  printf 'END %s rc=%s %s\n' "$name" "$rc" "$(date -u +%FT%TZ)" | tee -a "$root/protocol-status.log"
  if [[ $rc -ne 0 ]]; then exit "$rc"; fi
done
