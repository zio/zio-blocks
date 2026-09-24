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

package zio.blocks.streams

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import scala.jdk.CollectionConverters._

/**
 * Checked-in package-I production surface. The source scanner owns the ID
 * format.
 */
object SpecializationConformanceInventory {
  final case class Entry(
    id: String,
    symbol: String,
    representation: String,
    applicability: String,
    platform: String,
    classification: String,
    testCases: Vector[String]
  )

  private val asyncLaneTests = Vector(
    "sync-to-async scalar reads preserve every primitive lane and sentinel-domain extrema",
    "readN and readUpToN preserve every lane",
    "transforms and parallel boundaries preserve sentinel-domain extrema"
  )
  private val pipelineTests = Vector(
    "every factory and application route preserves widened synchronous inputs",
    "every factory and application route preserves widened asynchronous inputs",
    "every Pipeline factory executes both stream and sink application routes",
    "bottom-valued Pipeline map executes both stream and sink application routes",
    "preserving Pipeline routes execute every primitive lane and widened input",
    "map, filter, and collect compose identically through streams and sinks when ready or suspended",
    "asynchronous sink application reconstructs narrow primitive callback inputs",
    "Pipeline applyToSink uses asynchronous drains for every factory and composition"
  )
  private val sinkTests = Vector(
    "all direct-pull behavior families use every exact synchronous lane",
    "all direct-pull behavior families use every exact asynchronous lane",
    "lane-specific writers and numeric sums use authoritative physical pulls",
    "lane-specific writers and numeric sums use authoritative asynchronous pulls",
    "built-ins drain ready readers",
    "primitive lanes never use generic read and collect with specialized builders",
    "total, short-circuit, and specialized fold drains dispatch through the Int lane",
    "specialized sums dispatch through their native primitive lanes",
    "writer sinks consume asynchronous Char and Byte lanes exactly",
    "async sink transforms execute success and typed failure paths"
  )
  private val syncLaneTests = Vector(
    "sync-to-async scalar reads preserve every primitive lane and sentinel-domain extrema",
    "readN and readUpToN preserve every lane"
  )
  private val routeLaneTests = Vector(
    "dynamic preserving and branch-switching routes use every exact lane",
    "scanAsync advertises and preserves an output lane different from its input across a boundary"
  )
  private val symbolicRouteTests = Vector(
    "symbolic Stream aliases drive both operands through every exact primitive lane"
  )

  private val preservingRoutes = Set(
    "buffer",
    "distinct",
    "distinctBy",
    "distinctByAsync",
    "drop",
    "filter",
    "filterAsync",
    "take",
    "takeWhile",
    "takeWhileAsync",
    "tapEach",
    "tapEachAsync"
  )

  private val nonPullRoutes = Set(
    "forcesAsync",
    "fusableSource",
    "jvmType",
    "knownChunk",
    "knownLength",
    "linearSource",
    "render",
    "stackFrame",
    "toString"
  )

  private val streamExactRoutes = Set(
    "close",
    "isClosed",
    "read",
    "readable",
    "readBoolean",
    "readByte",
    "readBytes",
    "readChar",
    "readDouble",
    "readDoubles",
    "readFloat",
    "readFloats",
    "readInt",
    "readInts",
    "readLong",
    "readLongs",
    "readShort",
    "readUpToN",
    "reset",
    "skip",
    "skipElements"
  )
  private val streamStatefulRoutes = Set(
    "applyOrElse",
    "buffer",
    "bufferSize",
    "chunked",
    "collect",
    "cycle",
    "distinct",
    "distinctBy",
    "drop",
    "filter",
    "grouped",
    "intersperse",
    "isDefinedAt",
    "map",
    "mapAccum",
    "mapPar",
    "repeat",
    "repeated",
    "scan",
    "sliding",
    "take",
    "takeWhile",
    "tapEach",
    "via"
  )
  private val streamAsyncRoutes = Set(
    "collectAsync",
    "distinctByAsync",
    "ensuringAsync",
    "filterAsync",
    "flatMapPar",
    "mapAccumAsync",
    "mapAsync",
    "mapParAsync",
    "mergeAll",
    "scanAsync",
    "takeWhileAsync",
    "tapEachAsync"
  )
  private val streamTerminalRoutes = Set(
    "countAsync",
    "existsAsync",
    "findAsync",
    "forallAsync",
    "foreachAsync",
    "headAsync",
    "lastAsync",
    "runAsync",
    "runCollectAsync",
    "runDrainAsync",
    "runFoldAsync",
    "runForeachAsync",
    "startAsync",
    "useReaderManagedAsync",
    "useReaderAsync"
  )
  private val streamErrorRoutes =
    Set("catchAll", "catchDefect", "mapError", "mapErrorAsync", "normalizeRecoveryReader", "orElse", "||")
  private val streamLifecycleRoutes = Set("closeIntReader", "toAsyncIntReader")
  private val streamSourceRoutes    = Set(
    "apply",
    "attempt",
    "attemptAsync",
    "attemptEval",
    "attemptEvalAsync",
    "defer",
    "deferAsync",
    "die",
    "empty",
    "eval",
    "evalAsync",
    "fail",
    "fromAcquireRelease",
    "fromAcquireReleaseAsync",
    "fromArray",
    "fromChunk",
    "fromInputStream",
    "fromInputStreamUnmanaged",
    "fromIterable",
    "fromIterator",
    "fromIteratorAsync",
    "fromJavaReader",
    "fromJavaReaderUnmanaged",
    "fromRange",
    "fromReader",
    "fromReaderAsync",
    "fromResource",
    "range",
    "succeed",
    "suspend",
    "unfold",
    "unfoldAsync",
    "unwrap"
  )
  private val streamCompositionRoutes = Set(
    "&&",
    "++",
    "appendAsync",
    "appendFused",
    "appendSync",
    "acquireReader",
    "acquireReaderAsync",
    "AsyncZipReader",
    "combine",
    "compileToReader",
    "concat",
    "DyingReader",
    "ensuring",
    "ErrorMappedReader",
    "FailedReader",
    "flattenAll",
    "flatMap",
    "foldReaderKind",
    "iterator",
    "iteratorReader",
    "LifecycleWrappingReader",
    "makeReader",
    "materialize",
    "materializeRoot",
    "newReader",
    "reader",
    "rejectReader",
    "rejectSyncReader",
    "syncReader",
    "wrapReader",
    "Reader"
  )

  private val testCaseOwners = Map(
    "all direct-pull behavior families use every exact asynchronous lane"                                   -> "SinkExactLaneSpec",
    "all direct-pull behavior families use every exact synchronous lane"                                    -> "SinkExactLaneSpec",
    "attemptAsync is lazy and exposes every stable primitive lane"                                          -> "RouteSpecializationProofSpec",
    "dynamic preserving and branch-switching routes use every exact lane"                                   -> "RouteSpecializationProofSpec",
    "every factory and application route preserves widened asynchronous inputs"                             -> "PipelineExactLaneSpec",
    "every factory and application route preserves widened synchronous inputs"                              -> "PipelineExactLaneSpec",
    "fromReaderAsync exposes a stable lane without eagerly starting its effect"                             -> "RouteSpecializationProofSpec",
    "lane-specific writers and numeric sums use authoritative asynchronous pulls"                           -> "SinkExactLaneSpec",
    "lane-specific writers and numeric sums use authoritative physical pulls"                               -> "SinkExactLaneSpec",
    "mapAsync and collectAsync preserve all primitive output values across a boundary"                      -> "RouteSpecializationProofSpec",
    "scanAsync advertises and preserves an output lane different from its input across a boundary"          -> "RouteSpecializationProofSpec",
    "symbolic Stream aliases drive both operands through every exact primitive lane"                        -> "RouteSpecializationProofSpec",
    "Pipeline applyToSink uses asynchronous drains for every factory and composition"                       -> "SinkAsyncSpec",
    "Platform factories execute sync and async reader routes"                                               -> "AsyncReaderConformanceSpec",
    "Platform asynchronous map and merge factories execute every exact lane"                                -> "AsyncReaderConformanceSpec",
    "Platform synchronous map and merge factories execute every exact lane"                                 -> "AsyncReaderConformanceSpec",
    "Stream error mapping executes synchronous and asynchronous pull routes"                                -> "StreamSpecializationConformanceSpec",
    "Stream.unfoldAsync is a native async source"                                                           -> "UnfoldAsyncSpec",
    "all ByteBuffer sinks use their asynchronous drains"                                                    -> "NioStreamsSinksSpec",
    "all primitive specialized asynchronous fold lanes succeed and untrust callback StreamError"            -> "AsyncSinkExhaustiveCoverageSpec",
    "async acquire/use/release and Resource wrappers cover success and failures"                            -> "AsyncFacadeCoverageSpec",
    "async sink transforms execute success and typed failure paths"                                         -> "SinkLawsSpec",
    "async stream nodes compose, recover, repeat, zip, defer, unfold and finalize"                          -> "AsyncFacadeCoverageSpec",
    "async terminals preserve all lanes"                                                                    -> "AsyncReaderConformanceSpec",
    "async unary and recovery nodes normalize sync and async replacement kinds"                             -> "AsyncStreamExhaustiveCoverageSpec",
    "asynchronous channel sink writes bytes and preserves typed IOException"                                -> "NioStreamsSinksSpec",
    "asynchronous sink application reconstructs narrow primitive callback inputs"                           -> "PipelineAsyncSpec",
    "bottom-valued Pipeline map executes both stream and sink application routes"                           -> "PipelineLawsSpec",
    "buffered primitive readers implement scalar, bulk, cached, empty, and closed reads"                    -> "AsyncNativeBoundaryCoverageSpec",
    "built-ins drain ready readers"                                                                         -> "SinkAsyncSpec",
    "channel readers validate buffer size and expose the Byte lane"                                         -> "AsyncNioReadersSpec",
    "concat cancellation closes a tail produced after cancellation"                                         -> "AsyncReaderJvmSpec",
    "concat close wakes the original read without a child callback"                                         -> "AsyncReaderJvmSpec",
    "double bulk status preserves NaN payloads and sentinel values"                                         -> "NioReadersWritersSpec",
    "every Pipeline factory executes both stream and sink application routes"                               -> "PipelineLawsSpec",
    "filter tap takeWhile distinct and mapAccum compose"                                                    -> "AsyncInterpreterTransformCoverageSpec",
    "flatMap pulls and emits every primitive through its exact lane"                                        -> "AsyncReaderConformanceSpec",
    "fromChunk round-trips"                                                                                 -> "StreamLawsSpec",
    "fromIterable executes every exact primitive lane"                                                      -> "AsyncReaderConformanceSpec",
    "fromIterable round-trips"                                                                              -> "StreamLawsSpec",
    "Int terminal routes release a synchronous acquisition that wins a cancellation race"                   -> "StreamAsyncTerminalSpec",
    "iterator and Java I/O Stream sources execute their exact primitive lanes"                              -> "StreamSpecializationConformanceSpec",
    "long bulk status preserves the full value domain"                                                      -> "NioReadersWritersSpec",
    "map, filter, and collect compose identically through streams and sinks when ready or suspended"        -> "PipelineAsyncSpec",
    "mapPar preserves every Long value for Long and Int inputs"                                             -> "ConcurrentFullDomainSpec",
    "mapPar preserves raw Double bits for Double and Float inputs"                                          -> "ConcurrentFullDomainSpec",
    "mark normalizes throw, null, ordinary failure and trusted typed failure"                               -> "AsyncSinkExhaustiveCoverageSpec",
    "materialization traverses concat, defer, drop, collect, stateful, map, filter, repeat and take cycles" -> "AsyncStreamExhaustiveCoverageSpec",
    "mergeAll preserves every Long value and raw Double bit pattern"                                        -> "ConcurrentFullDomainSpec",
    "native pending AsyncReader materializes statically and dispatches async transforms and terminal"       -> "StreamAsyncTerminalSpec",
    "non-collect Stream terminals execute specialized readers"                                              -> "StreamSpecializationConformanceSpec",
    "parallel boundaries propagate synchronous and asynchronous failures and close idempotently"            -> "AsyncNativeBoundaryCoverageSpec",
    "preserving Pipeline routes execute every primitive lane and widened input"                             -> "PipelineLawsSpec",
    "primitive lanes never use generic read and collect with specialized builders"                          -> "SinkAsyncSpec",
    "primitive sink values cannot collide with end-of-stream markers"                                       -> "NioStreamsSinksSpec",
    "public async terminals exercise managed success, typed failure, callback throw and close suppression"  -> "AsyncStreamExhaustiveCoverageSpec",
    "read failure identity is sticky across scalar, bulk, and chunk pulls"                                  -> "NioReadersWritersSpec",
    "readN and readUpToN preserve every lane"                                                               -> "AsyncReaderConformanceSpec",
    "readTag preserves all eight physical identities independently of storage lanes"                        -> "SyncInterpreterSpec",
    "readUpToN returns available elements"                                                                  -> "ReadUpToNSpec",
    "replay preserves primary failure and interruption"                                                     -> "ConcurrentShutdownSpec",
    "scan exposes every primitive output lane while pulling a primitive input lane"                         -> "StreamSpecializationConformanceSpec",
    "specialized sums dispatch through their native primitive lanes"                                        -> "SinkAsyncSpec",
    "stable and suspending async sources plus synchronous and scoped sources materialize lazily"            -> "AsyncStreamExhaustiveCoverageSpec",
    "stateful and windowed Stream pull families execute on primitive inputs"                                -> "StreamSpecializationConformanceSpec",
    "stateful and windowed Stream readers execute every primitive lane"                                     -> "StreamSpecializationConformanceSpec",
    "sync-to-async scalar reads preserve every primitive lane and sentinel-domain extrema"                  -> "AsyncReaderConformanceSpec",
    "toSync delegates collection, scalar, bulk, and control operations"                                     -> "AsyncReaderJvmSpec",
    "total, short-circuit, and specialized fold drains dispatch through the Int lane"                       -> "SinkAsyncSpec",
    "transforms and parallel boundaries preserve sentinel-domain extrema"                                   -> "AsyncReaderConformanceSpec",
    "writer sinks consume asynchronous Char and Byte lanes exactly"                                         -> "AsyncSinkExhaustiveCoverageSpec",
    "writes all bytes into buffer"                                                                          -> "NioStreamsSinksSpec",
    "writes all bytes to channel"                                                                           -> "NioStreamsSinksSpec",
    "writes all doubles into buffer"                                                                        -> "NioStreamsSinksSpec",
    "writes all floats into buffer"                                                                         -> "NioStreamsSinksSpec",
    "writes all ints into buffer"                                                                           -> "NioStreamsSinksSpec",
    "writes all longs into buffer"                                                                          -> "NioStreamsSinksSpec",
    "widened intersperse dispatches by its output lane"                                                     -> "StreamSpecializationConformanceSpec",
    "intersperse preserves Long and Double scalar sentinel collisions"                                      -> "StreamSpecializationConformanceSpec",
    "zips two streams of equal length"                                                                      -> "ZipSpec",
    "zip promotes either asynchronous side and preserves positional tuples"                                 -> "StreamAsyncTerminalSpec"
  )

  private def qualifyTestCases(titles: Vector[String]): Vector[String] =
    titles.map(title =>
      s"${testCaseOwners.getOrElse(title, throw new IllegalArgumentException(s"unqualified test case: $title"))}::$title::0"
    )

  private def exactType(token: String): String =
    token.stripPrefix("read").stripSuffix("s") match {
      case "Bytes" => "Byte"
      case other   => other
    }

  private def representationRule(kind: String, token: String, owner: String): String = kind match {
    case "compiledReader"                   => s"compiled JVM reader $owner remains present in the conformance surface"
    case "compiledFactory"                  => s"compiled JVM factory $owner/$token returns a Reader"
    case "compiledDispatch"                 => s"compiled JVM method in $owner dispatches to exact symbol $token"
    case "exact"                            => s"$owner.$token pulls and returns the exact ${exactType(token)} physical lane"
    case "dispatch"                         => s"$owner dispatches to the exact ${exactType(token)} physical input lane"
    case "route" if preservingRoutes(token) =>
      s"$owner.$token preserves the materialized input reader.jvmType"
    case "route" if token == "repeated" =>
      s"$owner.$token preserves Known/Boxed input and boxes LateBound rematerializations"
    case "route" =>
      s"$owner.$token consumes reader.jvmType or fixes its output from invariant result evidence"
    case "metadata" => s"$owner.$token preserves exact physical-read identity independently of storage lane"
    case "reader"   => s"$owner reader $token exposes one stable reader.jvmType for its lifetime"
    case _          => s"$owner factory $token fixes or discovers one stable reader.jvmType before exposure"
  }

  private def streamBehavioralTests(kind: String, token: String, owner: String): Vector[String] =
    if (kind == "route" && nonPullRoutes(token)) Vector.empty
    else if (owner.contains("LazyBlockingReader") || token.contains("LazyBlockingReader"))
      Vector("iterator and Java I/O Stream sources execute their exact primitive lanes")
    else if (kind == "route" && Set("&&", "++", "||")(token))
      symbolicRouteTests
    else if (kind == "route" && (preservingRoutes(token) || streamErrorRoutes(token) || token == "repeated"))
      routeLaneTests
    else if (kind == "route" && (token == "scan" || token == "scanAsync"))
      routeLaneTests
    else if (kind == "route" && (token == "mapAsync" || token == "collectAsync"))
      Vector("mapAsync and collectAsync preserve all primitive output values across a boundary")
    else if (owner.contains("Intersperse") || token.contains("Intersperse"))
      Vector(
        "widened intersperse dispatches by its output lane",
        "intersperse preserves Long and Double scalar sentinel collisions"
      )
    else if (owner.contains("SyncScanInput") || token.contains("SyncScanInput"))
      Vector("scan exposes every primitive output lane while pulling a primitive input lane")
    else if (kind == "reader" && token == "SyncReader")
      Vector(
        "stateful and windowed Stream readers execute every primitive lane",
        "zips two streams of equal length",
        "zip promotes either asynchronous side and preserves positional tuples"
      )
    else if (owner.contains("Zip") || token.contains("Zip") || owner.contains("Lane"))
      Vector(
        "zips two streams of equal length",
        "zip promotes either asynchronous side and preserves positional tuples"
      )
    else if (owner.contains("Stream.chunked") || owner.contains("Stream.scan") || owner.contains("Stream.sliding"))
      Vector("stateful and windowed Stream readers execute every primitive lane")
    else if (streamExactRoutes(token))
      Vector(
        "async terminals preserve all lanes",
        "flatMap pulls and emits every primitive through its exact lane"
      )
    else if (streamStatefulRoutes(token))
      Vector(
        "stateful and windowed Stream pull families execute on primitive inputs",
        "filter tap takeWhile distinct and mapAccum compose",
        "materialization traverses concat, defer, drop, collect, stateful, map, filter, repeat and take cycles"
      )
    else if (streamAsyncRoutes(token))
      Vector(
        "native pending AsyncReader materializes statically and dispatches async transforms and terminal",
        "transforms and parallel boundaries preserve sentinel-domain extrema"
      )
    else if (streamTerminalRoutes(token))
      Vector(
        "non-collect Stream terminals execute specialized readers",
        "public async terminals exercise managed success, typed failure, callback throw and close suppression"
      )
    else if (streamErrorRoutes(token))
      Vector(
        "Stream error mapping executes synchronous and asynchronous pull routes",
        "async unary and recovery nodes normalize sync and async replacement kinds"
      )
    else if (streamLifecycleRoutes(token))
      Vector("Int terminal routes release a synchronous acquisition that wins a cancellation race")
    else if (streamSourceRoutes(token))
      Vector(
        "iterator and Java I/O Stream sources execute their exact primitive lanes",
        "async stream nodes compose, recover, repeat, zip, defer, unfold and finalize",
        "async acquire/use/release and Resource wrappers cover success and failures",
        "stable and suspending async sources plus synchronous and scoped sources materialize lazily",
        "Stream.unfoldAsync is a native async source",
        "fromChunk round-trips",
        "fromIterable round-trips"
      )
    else if (streamCompositionRoutes(token))
      Vector(
        "flatMap pulls and emits every primitive through its exact lane",
        "materialization traverses concat, defer, drop, collect, stateful, map, filter, repeat and take cycles"
      )
    else throw new IllegalArgumentException(s"unclassified Stream specialization symbol: $kind|$token|$owner")

  private def classification(path: String, kind: String, token: String): String =
    if (path.endsWith("/Stream.scala") && kind == "route" && nonPullRoutes(token))
      "NonPull(metadata/rendering)"
    else "PullRoute"

  private def behavioralTests(path: String, kind: String, token: String, owner: String): Vector[String] = {
    val symbol = s"$owner.$token"
    if (path == "jvm-compiled") syncLaneTests
    else if (path.endsWith("/Pipeline.scala")) pipelineTests
    else if (path.endsWith("/NioSinks.scala"))
      Vector(
        "all ByteBuffer sinks use their asynchronous drains",
        "asynchronous channel sink writes bytes and preserves typed IOException",
        "primitive sink values cannot collide with end-of-stream markers",
        "writes all bytes into buffer",
        "writes all ints into buffer",
        "writes all longs into buffer",
        "writes all doubles into buffer",
        "writes all floats into buffer",
        "writes all bytes to channel"
      )
    else if (path.endsWith("/Sink.scala")) sinkTests
    else if (path.endsWith("/Stream.scala")) streamBehavioralTests(kind, token, owner)
    else if (path.endsWith("/ConcurrentBufferedReader.scala"))
      Vector(
        "readUpToN returns available elements",
        "primitive lanes never use generic read and collect with specialized builders"
      )
    else if (path.contains("MapParReader") || symbol.contains("MapPar") || symbol.contains("mapPar"))
      Vector(
        "mapPar preserves every Long value for Long and Int inputs",
        "mapPar preserves raw Double bits for Double and Float inputs"
      )
    else if (path.contains("MergeReader") || symbol.contains("Merge") || symbol.contains("merge"))
      Vector("mergeAll preserves every Long value and raw Double bit pattern")
    else if (path.contains("ByteBuffer") || path.contains("NioReaders"))
      Vector(
        "long bulk status preserves the full value domain",
        "double bulk status preserves NaN payloads and sentinel values"
      )
    else if (path.endsWith("/ChannelReader.scala"))
      Vector(
        "channel readers validate buffer size and expose the Byte lane",
        "read failure identity is sticky across scalar, bulk, and chunk pulls"
      )
    else if (path.endsWith("/ConcurrentShutdown.scala"))
      Vector(
        "replay preserves primary failure and interruption",
        "parallel boundaries propagate synchronous and asynchronous failures and close idempotently"
      )
    else if (path.endsWith("/StreamError.scala"))
      Vector(
        "mark normalizes throw, null, ordinary failure and trusted typed failure",
        "all primitive specialized asynchronous fold lanes succeed and untrust callback StreamError"
      )
    else if (path.endsWith("/SyncBufferedReader.scala"))
      Vector(
        "buffered primitive readers implement scalar, bulk, cached, empty, and closed reads",
        "primitive lanes never use generic read and collect with specialized builders"
      )
    else if (
      path.endsWith("/Platform.scala") || (path.endsWith("/PlatformSpecific.scala") && !path.contains("SinkCompanion"))
    )
      Vector(
        "Platform factories execute sync and async reader routes",
        "Platform asynchronous map and merge factories execute every exact lane",
        "Platform synchronous map and merge factories execute every exact lane"
      )
    else if (path.endsWith("/SinkCompanionPlatformSpecific.scala")) sinkTests
    else if (symbol.contains("AsyncConcatReader"))
      Vector(
        "concat cancellation closes a tail produced after cancellation",
        "concat close wakes the original read without a child callback"
      )
    else if (symbol.contains("SyncToAsyncReader") || symbol.contains("AsyncToSyncReader"))
      Vector(
        "sync-to-async scalar reads preserve every primitive lane and sentinel-domain extrema",
        "toSync delegates collection, scalar, bulk, and control operations"
      )
    else if (owner.contains("FromIterable"))
      Vector("fromIterable executes every exact primitive lane")
    else if (
      path.contains("Mapped") || symbol.contains("Filtered") || symbol
        .contains("Mapped") || symbol.contains("FlatMapped")
    )
      Vector(
        "transforms and parallel boundaries preserve sentinel-domain extrema",
        "flatMap pulls and emits every primitive through its exact lane"
      )
    else if (
      owner.toLowerCase.contains("async") || token.toLowerCase.contains("async") || path.contains("/Async") ||
      path.startsWith("js/")
    )
      asyncLaneTests
    else if (path.endsWith("/OpTag.scala"))
      Vector("readTag preserves all eight physical identities independently of storage lanes")
    else if (
      path.endsWith("/Reader.scala") || path.contains("InternalVersionSpecific") || path.contains("Interpreter") ||
      path.contains("StatefulReader") || path.contains("Platform")
    ) syncLaneTests
    else throw new IllegalArgumentException(s"unmapped specialization inventory family: $path|$kind|$token|$owner")
  }

  private val ids: Vector[String] = {
    var root = Paths.get(System.getProperty("user.dir")).toAbsolutePath
    while (
      root != null &&
      !(Files.isRegularFile(root.resolve("build.sbt")) && Files.isDirectory(root.resolve("streams/shared/src/main")))
    ) root = root.getParent
    if (root == null) throw new IllegalStateException("cannot locate specialization inventory from user.dir")
    Files
      .readAllLines(
        root.resolve("streams/jvm/src/test/resources/specialization-conformance-inventory.txt"),
        StandardCharsets.UTF_8
      )
      .asScala
      .iterator
      .filterNot(_.isEmpty)
      .toVector
  }

  val entries: Vector[Entry] = ids.map { id =>
    val fields   = id.split('|')
    val path     = fields(0)
    val kind     = fields(1)
    val token    = fields(2).replace("%7C", "|")
    val owner    = fields(3)
    val platform =
      if (id.startsWith("jvm/") || id.startsWith("jvm-compiled|")) "JVM"
      else if (id.startsWith("js/")) "JS"
      else "shared"
    val rule          = representationRule(kind, token, owner)
    val symbol        = s"$owner.$token"
    val applicability =
      if (path.endsWith("/AsyncToSyncReader.scala")) "sync"
      else if (owner.toLowerCase.contains("async") || token.toLowerCase.contains("async") || path.contains("/Async"))
        "async"
      else if (owner.toLowerCase.contains("sync") || token.toLowerCase.contains("sync")) "sync"
      else if (kind == "exact" || kind == "dispatch" || (kind == "reader" && token != "Reader")) "sync"
      else "sync and async as materialized"
    Entry(
      id,
      symbol,
      rule,
      applicability,
      platform,
      classification(path, kind, token),
      qualifyTestCases(behavioralTests(path, kind, token, owner))
    )
  }
}
