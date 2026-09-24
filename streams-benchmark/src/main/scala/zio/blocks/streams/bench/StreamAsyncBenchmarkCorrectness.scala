/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.streams.bench

import java.util.concurrent.atomic.AtomicInteger
import zio.blocks.async.Async
import zio.blocks.streams.Stream

/**
 * Untimed correctness checks for the async benchmark fixtures.
 *
 * JMH consumes return values but does not expose them in JSON, so campaign
 * tooling runs this entry point before admitting timing data.
 */
object StreamAsyncBenchmarkCorrectness {
  private def checkRepeated[A](label: String, expected: A)(actual: => A): Unit = {
    var run = 1
    while (run <= 2) {
      require(actual == expected, s"$label checksum mismatch on materialization $run")
      run += 1
    }
  }

  private def concurrentExpected(n: Int, workload: String): Long = {
    var index = 0
    var total = 0L
    while (index < n) {
      if (workload == "heavy") {
        var value = index.toLong
        var round = 0
        while (round < 100) {
          value = (value * 31 + 17) ^ (value >>> 3)
          round += 1
        }
        total += value.toInt
      } else total += index + 1L
      index += 1
    }
    total
  }

  private def sum(values: Iterable[Int]): Long = values.iterator.map(_.toLong).sum

  private def mixed(values: Vector[Int], depth: Int, innerN: Int): Vector[Int] = {
    var out = values
    var i   = 0
    while (i < depth) {
      out = out.filter(_ % 2 != 0).flatMap(_ => Vector.range(0, innerN)).map(_ + 1)
      i += 1
    }
    out
  }

  private def checkEval(): Unit = {
    val bench = new StreamAsyncEvalParityBench
    bench.N = 100
    bench.setup()
    val expected = Map(
      "singleton"        -> 42L,
      "drain"            -> 0L,
      "map_1"            -> sum((0 until 100).map(_ + 1)),
      "filter_1"         -> sum((0 until 100).filter(_ % 2 != 0)),
      "flatMap_1"        -> 100L * sum(0 until 100),
      "takeDrop"         -> sum(50 until 100),
      "mapFilterFlatMap" -> 50L * sum(0 until 100),
      "mixed_1"          -> sum(mixed(Vector.range(0, 200), 1, 100)),
      "mixed_2"          -> sum(mixed(Vector.range(0, 100), 2, 20)),
      "mixed_3"          -> sum(mixed(Vector.range(0, 100), 3, 10)),
      "nested_flatMap"   -> 1L,
      "nested_concat"    -> 10001L
    )
    expected.foreach { case (id, value) =>
      checkRepeated(s"eval.$id", value)(bench.runOnce(id))
    }
  }

  private def checkCrossLibraryEval(): Unit = {
    val n        = 100
    val expected = Map(
      "singleton"        -> 42L,
      "drain"            -> 0L,
      "map_1"            -> sum((0 until n).map(_ + 1)),
      "filter_1"         -> sum((0 until n).filter(_ % 2 != 0)),
      "flatMap_1"        -> 100L * sum(0 until 100),
      "takeDrop"         -> sum(n / 2 until n),
      "mapFilterFlatMap" -> 50L * sum(0 until 100),
      "mixed_1"          -> sum(mixed(Vector.range(0, 200), 1, 100)),
      "mixed_2"          -> sum(mixed(Vector.range(0, 100), 2, 20)),
      "mixed_3"          -> sum(mixed(Vector.range(0, 100), 3, 10)),
      "nested_flatMap"   -> 1L,
      "nested_concat"    -> 10001L
    )
    val operations = Map(
      "zb"    -> expected.keys,
      "fs2"   -> expected.keys,
      "kyo"   -> expected.keys,
      "pekko" -> expected.keys
    )
    operations.foreach { case (library, supported) =>
      supported.foreach { operation =>
        val bench = new StreamAsyncEvalBench
        bench.N = n
        bench.cell = s"$library:$operation"
        bench.setup()
        try {
          val checksum = expected(operation)
          checkRepeated(s"cross-library eval $library.$operation", checksum)(bench.evaluate())
        } finally bench.teardown()
      }
    }
  }

  private def checkSetup(): Unit = {
    val bench = new StreamAsyncSetupParityBench
    bench.N = 100
    val expected = Map(
      "map1"           -> sum((0 until 100).map(_ + 1)),
      "map5"           -> sum((0 until 100).map(_ + 5)),
      "map10"          -> sum((0 until 100).map(_ + 10)),
      "map100"         -> sum((0 until 100).map(_ + 100)),
      "filter"         -> sum((0 until 100).filter(_ % 2 == 0)),
      "filterMap"      -> sum((0 until 100).filter(_ % 2 == 0).map(_ + 1)),
      "filterMapChain" -> sum(
        (0 until 100)
          .filter(_ % 2 == 0)
          .map(_ + 1)
          .filter(_ % 3 == 0)
          .map(_ * 2)
          .filter(_ % 5 == 0)
          .map(_ + 3)
          .filter(_ > 0)
          .map(_ - 1)
          .filter(_ < 100000)
          .map(_ + 7)
      ),
      "takeDrop" -> sum(50 until 100),
      "drain"    -> sum(0 until 100)
    )
    expected.foreach { case (id, value) =>
      checkRepeated(s"setup.$id", value)(bench.runOnce(id))
    }
  }

  private def checkCrossLibrarySetup(): Unit = {
    val n        = 100
    val expected = Map(
      "map1"           -> sum((0 until n).map(_ + 1)),
      "map5"           -> sum((0 until n).map(_ + 5)),
      "map10"          -> sum((0 until n).map(_ + 10)),
      "map100"         -> sum((0 until n).map(_ + 100)),
      "filter"         -> sum((0 until n).filter(_ % 2 == 0)),
      "filterMap"      -> sum((0 until n).filter(_ % 2 == 0).map(_ + 1)),
      "filterMapChain" -> sum(
        (0 until n)
          .filter(_ % 2 == 0)
          .map(_ + 1)
          .filter(_ % 3 == 0)
          .map(_ * 2)
          .filter(_ % 5 == 0)
          .map(_ + 3)
          .filter(_ > 0)
          .map(_ - 1)
          .filter(_ < 100000)
          .map(_ + 7)
      ),
      "takeDrop" -> sum(n / 2 until n),
      "drain"    -> sum(0 until n)
    )
    Vector("zb", "fs2", "kyo", "pekko").foreach { library =>
      val bench = new StreamAsyncSetupBench
      bench.N = n
      bench.library = library
      bench.setup()
      try
        expected.foreach { case (operation, value) =>
          bench.operation = operation
          checkRepeated(s"cross-library setup $library.$operation", value)(bench.checksum())
        }
      finally bench.teardown()
    }
  }

  private def checkPipeline(): Unit = {
    val bench    = new StreamAsyncPipelineParityBench
    val n        = 10000
    val expected = Map(
      "filterMap"      -> sum((0 until n).filter(_ % 2 == 0).map(_ + 1)),
      "map"            -> sum((0 until n).map(_ * 2)),
      "filter"         -> sum((0 until n).filter(_ % 2 == 0)),
      "chainedMaps"    -> sum((0 until n).map(_ + 5)),
      "flatMap"        -> sum((0 until n).map(_ * 2)),
      "take"           -> sum(0 until n / 2),
      "takeWhile"      -> sum(0 until n / 2),
      "concat"         -> sum(0 until n),
      "drain"          -> 1L,
      "chainedMaps10"  -> sum((0 until n).map(_ + 10)),
      "chainedMaps100" -> sum((0 until n).map(_ + 100)),
      "filterMapChain" -> sum(
        (0 until n)
          .filter(_ % 2 == 0)
          .map(_ + 1)
          .filter(_ % 3 == 0)
          .map(_ * 2)
          .filter(_ % 5 == 0)
          .map(_ + 3)
          .filter(_ > 0)
          .map(_ - 1)
          .filter(_ < 100000)
          .map(_ + 7)
      ),
      "takeDrop" -> sum(n / 2 until n)
    )
    expected.foreach { case (id, value) =>
      checkRepeated(s"pipeline.$id", value)(bench.runOnce(id))
    }
  }

  private def checkCrossLibraryPipeline(): Unit = {
    val n        = 100
    val expected = Map(
      "filterMap"      -> sum((0 until n).filter(_ % 2 == 0).map(_ + 1)),
      "map"            -> sum((0 until n).map(_ * 2)),
      "filter"         -> sum((0 until n).filter(_ % 2 == 0)),
      "chainedMaps"    -> sum((0 until n).map(_ + 5)),
      "flatMap"        -> sum((0 until n).map(_ * 2)),
      "take"           -> sum(0 until n / 2),
      "takeWhile"      -> sum(0 until n / 2),
      "concat"         -> sum(0 until n),
      "drain"          -> 0L,
      "chainedMaps10"  -> sum((0 until n).map(_ + 10)),
      "chainedMaps100" -> sum((0 until n).map(_ + 100)),
      "filterMapChain" -> sum(
        (0 until n)
          .filter(_ % 2 == 0)
          .map(_ + 1)
          .filter(_ % 3 == 0)
          .map(_ * 2)
          .filter(_ % 5 == 0)
          .map(_ + 3)
          .filter(_ > 0)
          .map(_ - 1)
          .filter(_ < 100000)
          .map(_ + 7)
      ),
      "takeDrop" -> sum(n / 2 until n)
    )
    Vector("zb", "fs2", "kyo", "pekko").foreach { library =>
      val bench = new StreamAsyncPipelineBench
      bench.N = n
      bench.library = library
      bench.setup()
      try
        expected.foreach { case (operation, value) =>
          bench.operation = operation
          checkRepeated(s"cross-library pipeline $library.$operation", value)(bench.evaluate())
        }
      finally bench.teardown()
    }

    Vector("zb", "fs2", "kyo", "pekko").foreach { library =>
      val bench = new StreamAsyncPipelineBench
      bench.N = n
      bench.library = library
      bench.setup()
      try
        require(
          bench.readyEffectInvocationCount() == n,
          s"$library ready-effect source did not invoke exactly one callback per element"
        )
      finally bench.teardown()
    }
  }

  private def checkMicro(): Unit = {
    val n         = 100
    val state     = new StreamAsyncStateAllocationBench
    val syncState = new StreamSyncStateAllocationBench
    state.N = n
    syncState.N = n
    state.setup()
    checkRepeated("state.static-sync-materialize", sum(0 until n))(syncState.staticSyncMaterialize())
    checkRepeated("state.selected-sync-wrapper", sum((0 until n).map(_ + 1)))(syncState.selectedSyncWrapper())
    val stateExpected = Map(
      "dynamic-all-sync"    -> sum(0 until n),
      "lifted-sync"         -> sum(0 until n),
      "native-ready"        -> sum(0 until n),
      "ready-read"          -> 0L,
      "ready-map"           -> sum((0 until n).map(_ + 1)),
      "ready-filter"        -> sum((0 until n).filter(i => (i & 1) == 0)),
      "bulk-chunk"          -> n.toLong,
      "direct-byte-bulk"    -> n.toLong,
      "sync-async-terminal" -> sum(0 until n)
    )
    stateExpected.foreach { case (id, value) =>
      checkRepeated(s"state.$id", value)(state.runOnce(id))
    }

    checkRepeated("isolated resume", 1L)(new StreamAsyncResumeBench().runOnce())
    val cooperativeYield = new StreamAsyncCooperativeYieldBench
    cooperativeYield.N = 2049
    checkRepeated("cooperative yield", sum(0 until cooperativeYield.N))(cooperativeYield.runOnce())

    val suspension = new StreamAsyncSuspensionBench
    suspension.N = 20
    suspension.setupWorker()
    try {
      val expected = Map(
        "first-suspension"          -> 1L,
        "per-element"               -> sum(0 until 20),
        "per-chunk"                 -> 20L,
        "sequential-callback"       -> sum((0 until 20).map(_ + 1)),
        "bounded-parallel-callback" -> sum((0 until 20).map(_ + 1)),
        "lifted-after-async"        -> sum((0 until 20).map(_ + 1)),
        "dynamic-flat-map"          -> sum(0 until 20),
        "sync-ready-callback"       -> sum(0 until 20),
        "async-ready-callback"      -> sum(0 until 20),
        "async-acquire-finalize"    -> sum(0 until 20)
      )
      expected.foreach { case (id, value) =>
        checkRepeated(s"suspension.$id", value)(suspension.runOnce(id))
      }
    } finally suspension.teardownWorker()

    val suspendedConcurrent = new StreamAsyncSuspendedConcurrentBench
    suspendedConcurrent.N = 20
    suspendedConcurrent.setupWorker()
    try
      Seq(1, 8, 16).foreach { parallelism =>
        suspendedConcurrent.parallelism = parallelism
        checkRepeated(
          s"suspension.suspended-map-par.p$parallelism",
          sum((0 until suspendedConcurrent.N).map(_ + 1))
        )(suspendedConcurrent.runOnce())
      }
    finally suspendedConcurrent.teardownWorker()

    val primitive     = new StreamAsyncPrimitiveTerminalBench
    val syncPrimitive = new StreamSyncPrimitiveTerminalBench
    primitive.N = 20
    syncPrimitive.N = 20
    val baseSum = sum(0 until 20)
    checkRepeated("Boolean lane", 10)(syncPrimitive.booleanSyncParity())
    checkRepeated("Byte lane", baseSum)(syncPrimitive.byteSyncParity().toLong)
    checkRepeated("Char lane", baseSum)(syncPrimitive.charSyncParity().toLong)
    checkRepeated("Short lane", baseSum)(syncPrimitive.shortSyncParity().toLong)
    checkRepeated("Int reader", baseSum)(primitive.intReader())
    checkRepeated("Long reader", baseSum)(primitive.longReader())
    checkRepeated("Float reader", baseSum.toFloat)(primitive.floatReader())
    checkRepeated("Double reader", baseSum.toDouble)(primitive.doubleReader())
    checkRepeated("Int callback", sum((0 until 20).map(_ + 1)))(primitive.intCallback())
    checkRepeated("async Boolean semantic", 10)(primitive.semanticBoolean())
    checkRepeated("async Byte semantic", baseSum)(primitive.semanticByte().toLong)
    checkRepeated("async Char semantic", baseSum)(primitive.semanticChar().toLong)
    checkRepeated("async Short semantic", baseSum)(primitive.semanticShort().toLong)
    checkRepeated("async Int semantic", sum((0 until 20).map(_ + 1)))(primitive.semanticInt())
    checkRepeated("async Long semantic", sum((0 until 20).map(_ + 1)))(primitive.semanticLong())
    checkRepeated("async Float semantic", sum((0 until 20).map(_ + 1)).toFloat)(primitive.semanticFloat())
    checkRepeated("async Double semantic", sum((0 until 20).map(_ + 1)).toDouble)(primitive.semanticDouble())
    checkRepeated("drain", true)(primitive.terminalDrain())
    checkRepeated("collect", 20)(primitive.terminalCollect())
    checkRepeated("primitive fold", baseSum)(primitive.terminalPrimitiveFold())
    checkRepeated("reference fold", (0 until 10).mkString)(primitive.terminalReferenceFold())
    checkRepeated("foreach callback", true)(primitive.terminalForeachCallback())
    checkRepeated("short-circuit", true)(primitive.terminalShortCircuit())
    checkRepeated("Sink.mapAsync", baseSum + 1)(primitive.terminalSinkMapAsync())
  }

  private def checkConcurrent(): Unit =
    Vector(1, 8, 16).foreach { parallelism =>
      Vector("light", "heavy").foreach { workload =>
        val expected = concurrentExpected(1000, workload)
        val bench    = new StreamAsyncConcurrentParityBench
        bench.N = 1000
        bench.parallelism = parallelism
        bench.workload = workload
        bench.setup()
        Vector("mapPar", "mergeAll", "flatMapPar").foreach { operator =>
          checkRepeated(s"concurrency.$operator/$parallelism/$workload", expected)(bench.runOnce(operator))
        }
      }
    }

  private def checkCrossLibraryConcurrent(): Unit =
    Map(
      "zb"    -> Vector("mapPar", "mergeAll", "flatMapPar"),
      "fs2"   -> Vector("mapPar", "mergeAll", "flatMapPar"),
      "kyo"   -> Vector("mapPar"),
      "pekko" -> Vector("mapPar", "mergeAll", "flatMapPar")
    ).foreach { case (library, operations) =>
      Vector(1, 8, 16).foreach { parallelism =>
        Vector("light", "heavy").foreach { workload =>
          val expected = concurrentExpected(1000, workload)
          val bench    = new StreamAsyncConcurrentBench
          bench.N = 1000
          bench.parallelism = parallelism
          bench.workload = workload
          operations.foreach { operation =>
            bench.cell = s"$library:$operation"
            bench.setup()
            try
              checkRepeated(
                s"cross-library concurrency $library.$operation/$parallelism/$workload",
                expected
              )(bench.evaluate())
            finally bench.teardown()

            if (library == "kyo" && operation == "mapPar") {
              bench.setup()
              try {
                val (checksum, maximum) = bench.kyoParallelismObservation()
                require(checksum == expected, s"Kyo observed checksum $checksum instead of $expected")
                require(maximum > 0 && maximum <= parallelism, s"Kyo observed $maximum in flight at limit $parallelism")
                require(
                  bench.configuredKyoBufferSize == 16,
                  s"unexpected Kyo buffer size ${bench.configuredKyoBufferSize}"
                )
              } finally bench.teardown()

              val native = new StreamConcurrentBench
              native.N = 1000
              native.parallelism = parallelism
              native.workload = workload
              native.setup()
              checkRepeated(s"native concurrency kyo.mapPar/$parallelism/$workload", expected)(native.kyo_mapPar())
            }
          }
        }
      }
    }

  private def checkPrimitiveRoutes(): Unit =
    Vector(0, 1, 1000).foreach { n =>
      Vector("boolean", "byte", "char", "short", "int", "long", "float", "double").foreach { primitive =>
        val expected = primitive match {
          case "boolean" => (n + 1L) / 2L
          case "byte"    => (0 until n).iterator.map(_.toByte.toLong + 1L).sum
          case "char"    => (0 until n).iterator.map(_.toChar.toLong + 1L).sum
          case "short"   => (0 until n).iterator.map(_.toShort.toLong + 1L).sum
          case "int"     => (0 until n).iterator.map(_.toLong + 1L).sum
          case "long"    => (0 until n).iterator.map(_.toLong + 1L).sum
          case "float"   => (0 until n).iterator.map(_.toFloat.toLong + 1L).sum
          case "double"  => (0 until n).iterator.map(_.toDouble.toLong + 1L).sum
        }
        Vector("stream", "pipeline-stream", "pipeline-sink", "widened").foreach { route =>
          val bench = new StreamPrimitiveSpecializationBench
          bench.N = n
          bench.primitive = primitive
          bench.route = route
          bench.setup()
          checkRepeated(s"primitive sync $primitive/$route/N=$n", expected)(bench.syncPrimitiveRoute())
          checkRepeated(s"primitive async $primitive/$route/N=$n", expected)(bench.asyncPrimitiveRoute())
        }
      }
    }

  private def checkTrackedMaterialization(): Unit = {
    val acquisitions = new AtomicInteger
    val closes       = new AtomicInteger
    val stream       = AsyncParity.trackedSource(0, 4, () => acquisitions.incrementAndGet(), () => closes.incrementAndGet())
    checkRepeated("tracked materialization", 6L)(AsyncParity.fold(stream))
    require(acquisitions.get() == 2, s"tracked materialization acquired ${acquisitions.get()} readers instead of 2")
    require(closes.get() == 2, s"tracked materialization closed ${closes.get()} readers instead of 2")

    val resourceAcquisitions = new AtomicInteger
    val resourceReleases     = new AtomicInteger
    val resource             = Stream.fromAcquireReleaseAsync(
      Async.succeed(resourceAcquisitions.incrementAndGet()),
      (_: Int) => Async.succeed { resourceReleases.incrementAndGet(); () }
    )(_ => AsyncParity.source(0, 4))
    checkRepeated("tracked acquire-release", 6L)(AsyncParity.fold(resource))
    require(
      resourceAcquisitions.get() == 2,
      s"tracked acquire-release acquired ${resourceAcquisitions.get()} resources instead of 2"
    )
    require(
      resourceReleases.get() == 2,
      s"tracked acquire-release released ${resourceReleases.get()} resources instead of 2"
    )
  }

  def main(args: Array[String]): Unit = {
    val checks = Vector[(String, () => Unit)](
      "concurrent"               -> (() => checkConcurrent()),
      "cross-library-concurrent" -> (() => checkCrossLibraryConcurrent()),
      "cross-library-eval"       -> (() => checkCrossLibraryEval()),
      "cross-library-pipeline"   -> (() => checkCrossLibraryPipeline()),
      "cross-library-setup"      -> (() => checkCrossLibrarySetup()),
      "eval"                     -> (() => checkEval()),
      "micro"                    -> (() => checkMicro()),
      "pipeline"                 -> (() => checkPipeline()),
      "primitive-routes"         -> (() => checkPrimitiveRoutes()),
      "setup"                    -> (() => checkSetup()),
      "tracked-materialization"  -> (() => checkTrackedMaterialization())
    )
    val requested = args.toSet
    val unknown   = requested -- checks.iterator.map(_._1).toSet
    require(unknown.isEmpty, s"unknown checks: ${unknown.toVector.sorted.mkString(", ")}")
    checks.foreach { case (name, check) =>
      if (requested.isEmpty || requested(name)) check()
    }
    println("async benchmark correctness: PASS")
  }
}
