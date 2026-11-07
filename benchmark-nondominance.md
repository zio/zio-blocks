# ZIO Blocks Streams Non-Dominant Benchmark Results

## Final result

The post-optimization rerun completed all 444 semantically comparable rows on
September 9, 2026. **No row has a competitor above ZIO Blocks by point
estimate.** Raw results are in
`streams/benchmarks/fairness-final-20260909/results-comparable.json`.

Kyo remains included for native and ready-effect unordered bounded `mapPar`.
The current benchmark source also includes Kyo `mergeAll` and `flatMapPar` via
public `collectAll`: it assigns outer elements to `parallelism` fixed sequential
stripes, then merges those stripes with the separately reported buffer size.
This preserves every output and bounds active inner streams, but unlike the
other providers it uses fixed striped allocation rather than rolling work
stealing. The saved 444-row result predates those two Kyo cells, so this report
makes no performance claim for them.

## Scope

This report identifies benchmark cells where a competitor's saved point estimate exceeds ZIO Blocks Streams. It uses the completed 444-row fairness rerun in `streams/benchmarks/fairness-rerun-20260907/results.json`, replacing the two stale ready-effect `map` groups with the post-optimization reruns in `streams/benchmarks/ready-map-optimization/final-comparison-{eval,pipeline}.json`.

All values are throughput in operations per second; higher is better. `ZB / fastest` below 1 means the named competitor was faster. “Intervals separate” means the reported JMH 99.9% confidence intervals do not overlap. This is a screening report, not an aggregate ranking across different contracts.

No benchmarks were rerun for this report. The saved full campaign used one benchmark thread and one fork on OpenJDK 26.0.1/macOS arm64. Eval, setup, and concurrent classes used 10 × 1-second warmups and measurements; pipeline classes used 15 × 1-second warmups and measurements.

The optimization checklist records targeted three-fork reruns as work progresses. Checked rows supersede their stale entries in the original tables below. The ready-effect direct-fold reruns are in `streams/benchmarks/failure-optimization/round-02-linear-evaluate/`; the two earlier `map` reruns favor ZB by 2.66–434.1×.

## Summary

- 34 saved cells have a competitor above ZB by point estimate.
- 30 have non-overlapping 99.9% confidence intervals.
- Four are inconclusive overlaps: native construction `map10`, ready-effect evaluation `singleton`, and ready-effect `mergeAll` at parallelism 1 for both workloads.
- ZB has no identified deficit in native unordered bounded `mapPar`, ready-effect unordered bounded `mapPar`, or fan-in at parallelism 8 or 16.
- The largest execution deficits are native `drain` and the ready-effect terminal/slicing/flattening paths.

## Optimization checklist

A box is checked only after an unchanged comparative benchmark shows ZB ahead of every participating provider. Rows marked “rerun required” had a historical deficit, but the saved score predates a production optimization that reaches their terminal shape.

### Native execution

- [x] Evaluation `drain` — 140.042M ops/s; 8.90× Kyo
- [x] Evaluation `nested_flatMap` — 20,209.5 ops/s; 7.33× current Ox
- [x] Evaluation `takeDrop` — 94,281.1 ops/s; 1.48× Kyo
- [x] Pipeline `drain` — 163.879M ops/s; 10.70× Kyo
- [x] Pipeline `take` — 93,955.2 ops/s; 1.45× Kyo
- [x] Pipeline `takeDrop` — 93,984.4 ops/s; 1.47× Kyo
- [x] Pipeline `takeWhile` — 159,699.6 ops/s; 2.33× Ox

### Ready-effect execution

- [x] Evaluation `map_1` — 12,764.1 ops/s; 2.66× the fastest competitor
- [x] Evaluation `drain` — 20,714.4 ops/s; 2.62× Kyo
- [x] Evaluation `filter_1` — 10,164.1 ops/s; 1.91× Kyo
- [x] Evaluation `flatMap_1` — 19,810.2 ops/s; 3.02× Kyo
- [x] Evaluation `mapFilterFlatMap` — 398.4 ops/s; 2.91× Kyo
- [x] Evaluation `mixed_1` — 10,638.9 ops/s; 2.30× Kyo
- [x] Evaluation `mixed_2` — 6,389.4 ops/s; 2.09× Kyo
- [x] Evaluation `mixed_3` — 2,284.9 ops/s; 1.53× Kyo
- [x] Evaluation `nested_concat` — 1,628.9 ops/s; 6.60× FS2
- [x] Evaluation `nested_flatMap` — 324.9 ops/s; 1.24× FS2
- [x] Evaluation `singleton` — 30.262M ops/s; 339.87× Kyo
- [x] Evaluation `takeDrop` — 13,935.1 ops/s; 2.05× Kyo
- [x] Pipeline `map` — 13,533.1 ops/s; 2.91× the fastest competitor
- [x] Pipeline `filterMap` — 9,849.4 ops/s; 1.83× Kyo
- [x] Pipeline `filter` — 10,171.0 ops/s; 1.80× Kyo
- [x] Pipeline `chainedMaps` — 8,190.9 ops/s; 2.27× Kyo
- [x] Pipeline `flatMap` — 1,197.2 ops/s; 2.02× Kyo
- [x] Pipeline `take` — 24,600.3 ops/s; 3.17× Kyo
- [x] Pipeline `takeWhile` — 17,449.1 ops/s; 2.92× Kyo
- [x] Pipeline `concat` — 15,538.3 ops/s; 2.51× Kyo
- [x] Pipeline `drain` — 21,257.6 ops/s; 2.71× Kyo
- [x] Pipeline `chainedMaps10` — 7,352.8 ops/s; 3.44× Kyo
- [x] Pipeline `chainedMaps100` — 1,317.0 ops/s; 4.55× Kyo
- [x] Pipeline `filterMapChain` — 9,301.2 ops/s; 2.21× Kyo
- [x] Pipeline `takeDrop` — 13,892.7 ops/s; 2.01× Kyo

### Graph construction

- [x] Native `drain` — 718.10M ops/s; 2.85× Kyo
- [x] Native `filter` — 238.12M ops/s; 1.37× Kyo
- [x] Native `filterMap` — 283.913M ops/s; 1.41× Kyo
- [x] Native `map1` — 270.83M ops/s; 1.46× Kyo
- [x] Native `map5` — 87.41M ops/s; 1.12× Kyo
- [x] Native `map10` — 52.77M ops/s; 1.17× Kyo
- [x] Native `takeDrop` — 313.60M ops/s; 2.47× Kyo
- [x] Ready-effect `map100` — 7.456M ops/s; 1.07× Kyo

### Ready-effect bounded fan-in

- [x] `flatMapPar`, parallelism 1, light workload — 215.43 ops/s; 35.96× Pekko
- [x] `flatMapPar`, parallelism 1, heavy workload — 9.66 ops/s; 2.83× Pekko
- [x] `mergeAll`, parallelism 1, light workload — 120.20 ops/s; 20.64× Pekko
- [x] `mergeAll`, parallelism 1, heavy workload — 9.02 ops/s; 2.72× Pekko

## Native-source execution

### Evaluation

| Benchmark | ZB ops/s | Fastest competitor | ZB / fastest | Faster providers | Intervals separate |
|---|---:|---:|---:|---|---|
| `drain` | 42,299.2 ± 292.8 | Kyo 15.732M ± 229,218 | **0.003×** | FS2, Kyo, Ox | yes |
| `nested_flatMap` | 2,918.4 ± 27.0 | Ox 3,285.8 ± 61.3 | **0.888×** | Ox | yes |
| `takeDrop` | 21,337.3 ± 195.2 | Kyo 63,905.1 ± 791.7 | **0.334×** | FS2, Kyo, Ox | yes |

### Pipelines

| Benchmark | ZB ops/s | Fastest competitor | ZB / fastest | Faster providers | Intervals separate |
|---|---:|---:|---:|---|---|
| `drain` | 41,409.4 ± 147.9 | Kyo 15.311M ± 33,698 | **0.003×** | FS2, Kyo, Ox | yes |
| `take` | 26,829.8 ± 68.6 | Kyo 64,993.8 ± 89.5 | **0.413×** | FS2, Kyo, Ox | yes |
| `takeDrop` | 24,489.5 ± 181.0 | Kyo 63,742.5 ± 439.7 | **0.384×** | FS2, Kyo, Ox | yes |
| `takeWhile` | 22,605.1 ± 56.0 | Ox 68,502.2 ± 156.3 | **0.330×** | FS2, Kyo, Ox | yes |

## Ready-effect execution

### Evaluation

| Benchmark | ZB ops/s | Fastest competitor | ZB / fastest | Faster providers | Intervals separate |
|---|---:|---:|---:|---|---|
| `drain` | 691.460 ± 3.603 | Kyo 7,647.6 ± 97.2 | **0.090×** | Kyo, Pekko | yes |
| `filter_1` | 535.907 ± 13.039 | Kyo 5,323.1 ± 49.0 | **0.101×** | Kyo, Pekko | yes |
| `flatMap_1` | 504.548 ± 26.287 | Kyo 6,564.9 ± 275.6 | **0.077×** | Kyo, Pekko | yes |
| `mapFilterFlatMap` | 9.783 ± 0.389 | Kyo 136.864 ± 0.554 | **0.071×** | Kyo, Pekko | yes |
| `nested_concat` | 5.370 ± 1.284 | FS2 246.948 ± 3.786 | **0.022×** | FS2 | yes |
| `nested_flatMap` | 75.669 ± 1.796 | FS2 261.206 ± 0.933 | **0.290×** | FS2 | yes |
| `singleton` | 85,462.3 ± 8,904.8 | Kyo 89,042.2 ± 1,719.8 | **0.960×** | Kyo | no |
| `takeDrop` | 487.327 ± 11.493 | Kyo 6,785.3 ± 154.0 | **0.072×** | Kyo, Pekko | yes |

### Pipelines

| Benchmark | ZB ops/s | Fastest competitor | ZB / fastest | Faster providers | Intervals separate |
|---|---:|---:|---:|---|---|
| `concat` | 461.921 ± 3.842 | Kyo 6,181.6 ± 91.5 | **0.075×** | Kyo, Pekko | yes |
| `drain` | 711.062 ± 2.549 | Kyo 7,555.2 ± 266.2 | **0.094×** | Kyo, Pekko | yes |
| `filter` | 542.645 ± 7.427 | Kyo 5,639.6 ± 64.0 | **0.096×** | Kyo, Pekko | yes |
| `flatMap` | 77.370 ± 0.923 | Kyo 593.286 ± 2.977 | **0.130×** | FS2, Kyo | yes |
| `take` | 925.099 ± 3.330 | Kyo 7,749.4 ± 222.4 | **0.119×** | Kyo, Pekko | yes |
| `takeDrop` | 492.791 ± 5.622 | Kyo 6,901.0 ± 154.4 | **0.071×** | Kyo, Pekko | yes |
| `takeWhile` | 880.288 ± 3.156 | Kyo 5,977.8 ± 172.0 | **0.147×** | Kyo, Pekko | yes |

## Graph construction

Construction throughput does not measure stream execution and should be optimized separately.

### Native construction

| Benchmark | ZB ops/s | Fastest competitor | ZB / fastest | Faster providers | Intervals separate |
|---|---:|---:|---:|---|---|
| `drain` | 278.913M ± 17.123M | Kyo 409.273M ± 1.564M | **0.681×** | FS2, Kyo | yes |
| `filter` | 189.551M ± 994,775 | Kyo 256.490M ± 980,502 | **0.739×** | Kyo | yes |
| `filterMap` | 156.287M ± 814,208 | Kyo 196.869M ± 575,056 | **0.794×** | Kyo | yes |
| `map1` | 189.827M ± 583,535 | Kyo 260.217M ± 24.564M | **0.729×** | Kyo | yes |
| `map10` | 61.466M ± 235,540 | Kyo 61.476M ± 184,264 | **1.000×** | Kyo | no |
| `map5` | 99.070M ± 310,829 | Kyo 104.396M ± 458,480 | **0.949×** | Kyo | yes |
| `takeDrop` | 186.150M ± 895,359 | Kyo 202.760M ± 838,061 | **0.918×** | Kyo | yes |

### Ready-effect construction

| Benchmark | ZB ops/s | Fastest competitor | ZB / fastest | Faster providers | Intervals separate |
|---|---:|---:|---:|---|---|
| `map100` | 6.921M ± 13,241 | Kyo 7.004M ± 28,045 | **0.988×** | Kyo | yes |

## Ready-effect bounded fan-in

Only parallelism-1 cells trail. At parallelism 8 and 16, ZB leads every measured peer. These rows measure bounded active-stream fan-in under inner work, not equivalent CPU scheduler scaling.

| Benchmark | ZB ops/s | Fastest competitor | ZB / fastest | Intervals separate |
|---|---:|---:|---:|---|
| `flatMapPar`, p=1, heavy | 3.188 ± 0.028 | Pekko 3.476 ± 0.006 | **0.917×** | yes |
| `flatMapPar`, p=1, light | 5.627 ± 0.119 | Pekko 6.130 ± 0.113 | **0.918×** | yes |
| `mergeAll`, p=1, heavy | 3.225 ± 0.046 | Pekko 3.278 ± 0.015 | **0.984×** | no |
| `mergeAll`, p=1, light | 5.382 ± 0.088 | Pekko 5.532 ± 0.124 | **0.973×** | no |

## Suggested optimization order

1. Native and ready-effect `drain`: determine why the current terminal retains substantial per-element machinery when no result is consumed.
2. Ready-effect `take`, `takeWhile`, and `takeDrop`: specialize early termination and avoid rebuilding the generic async interpreter state per pull.
3. Ready-effect `flatMap`, `mapFilterFlatMap`, and deep concatenation/flatMap: profile allocation and resume overhead while preserving stack safety and cooperative yielding.
4. Rerun the eight excluded mapped rows individually; the existing direct mapped-fold optimization may already have removed those deficits.
5. Treat construction and parallelism-1 fan-in as lower priority because the gaps are smaller or do not affect steady-state parallel throughput.
