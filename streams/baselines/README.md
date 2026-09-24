# Streams benchmark baselines

## Comparison taxonomy

Every generated summary labels its comparison. **Sync regression** compares the
pinned pre-async commit with the candidate through the identical sync overlay;
**async regression** compares the pinned first-complete-async commit with the
candidate through the identical async overlay. These are binding gates.
Sync/ready, ready/suspended, and blocking/callback-concurrency comparisons are
**diagnostic ratios**, never regression gates.

The manifest assigns every benchmark to exactly one of four reporting categories:

1. **Static sync** — `state.static-sync-materialize`,
   `state.selected-sync-wrapper`, and all 52 `Stream*Bench.zb_*` cells.
2. **Conservative all-sync dynamic boundaries** — `state.dynamic-all-sync`.
3. **Existing blocking concurrency** — the 18 `StreamConcurrentBench.zb_*`
   operator/parallelism/workload cells (reported diagnostically against callback
   concurrency, while each is independently sync-regression gated).
4. **Genuinely async graphs** — all 52 `StreamAsync*ParityBench.async_*` cells
   and every manifest row categorized `async` or `async-adapter`, including the
   three `concurrency.suspended-map-par` parameter cells.

## Reproduction

Build overlays without touching production sources:

```bash
python3 streams-benchmark/tools/build_overlays.py sync /tmp/zb-sync-base
python3 streams-benchmark/tools/build_overlays.py sync /tmp/zb-sync-candidate --candidate
python3 streams-benchmark/tools/build_overlays.py async /tmp/zb-async-base
python3 streams-benchmark/tools/build_overlays.py async /tmp/zb-async-candidate --candidate
python3 streams-benchmark/tools/campaign.py freeze-host /tmp/host.json
# Fill campaign-template.json paths, candidate SHA, overlay hash, complete host,
# and all selected manifest cells, then:
python3 streams-benchmark/tools/campaign.py run /tmp/campaign.json streams/baselines/<campaign-id>
python3 streams-benchmark/tools/campaign.py verify-hashes streams/baselines/<campaign-id>
```

Use six pairs first, extend an inconclusive cell to ten, and only then run a new
ten-pair 3-second replacement campaign. Never pool replacement data. Allocation
campaigns set `kind` to `allocation` and add `-prof gc`; raw JMH JSON remains
untouched beneath `raw/`. `SHA256SUMS` covers every artifact except itself.
Combine the predeclared campaigns without pooling via `campaign.py finalize
<six-pair> --extended <ten-pair> --replacement <three-second-ten-pair> --output
final-analysis.json`. Allocation slope rows retain separate `N=0,1,1000` fork
samples and use `paired_slope`/`slope_verdict` to bound fixed and per-element
cost independently.

## Historical synchronous sample (diagnostic only)

These artifacts freeze the synchronous hot paths before the async Reader
migration. They were recorded from `2b3896cc` on an Apple M3 Ultra running
macOS 26.5 and OpenJDK 26.0.1.

## Throughput and allocation

`scala-3.8.3-stream-eval-jmh.json` contains four one-second measurements after
two one-second warmups, one fork, `N = 10000`, and the JMH GC profiler:

| Benchmark | Throughput (ops/s) | Allocation (B/op) |
| --- | ---: | ---: |
| `zb_drain` | 178,902.397 | 5.802 |
| `zb_filter_1` | 201,349.698 | 5.105 |
| `zb_map_1` | 171,244.264 | 6.026 |
| `zb_mapFilterFlatMap` | 1,020.160 | 681,250.313 |

Reproduce from the repository root:

```bash
sbt --client -Dsbt.color=false \
  '++3.8.3; streams-benchmark/Jmh/run -wi 2 -i 4 -f 1 -w 1s -r 1s -prof gc -rf json -rff <absolute-output-path> "zio.blocks.streams.bench.StreamEvalBench.zb_(drain|map_1|filter_1|mapFilterFlatMap)"'
```

The near-zero allocation measurements are profiler noise rather than a promise
of literal fractional allocation. Compare candidate and baseline in the same
environment and investigate any throughput regression greater than 5% or any
new material per-operation allocation.

## Bytecode goldens

The `scala-2.13.18-*` and `scala-3.8.3-*` files are normalized `javap -c -p`
instruction listings for all `SyncInterpreter.read*` methods and the named
`Sink.FoldLeftInt` / `Sink.Mapped` drain implementations. They intentionally
omit constant-pool details, file timestamps, and unrelated class methods.

Regenerate each Scala version by compiling `streamsJVM`, then running:

```bash
javap -classpath streams/jvm/target/scala-<version>/classes -c -p \
  zio.blocks.streams.internal.SyncInterpreter |
  awk 'BEGIN{p=0} /^  (public|private|protected).* read/{p=1} /^  (public|private|protected)/ && p && $0 !~ / read/{p=0} p{print}'

javap -classpath streams/jvm/target/scala-<version>/classes -c -p \
  'zio.blocks.streams.Sink$FoldLeftInt' 'zio.blocks.streams.Sink$Mapped'
```

Compare regenerated files directly. Expected class/method renames during the
migration should be reviewed structurally; new virtual/interface dispatch,
boxing, or allocation in synchronous read/drain loops is a regression unless
the corresponding measured baseline proves otherwise.
