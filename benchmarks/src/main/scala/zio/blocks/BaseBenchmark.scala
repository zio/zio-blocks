package zio.blocks

import org.openjdk.jmh.annotations.{Scope => JScope, _}
import java.util.concurrent.TimeUnit

@State(JScope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
  value = 1,
  jvmArgs = Array(
    "-server",
    "-Xnoclassgc",
    "-Xms3g",
    "-Xmx3g",
    "-Xss4m",
    "-XX:NewSize=2g",
    "-XX:MaxNewSize=2g",
    "-XX:InitialCodeCacheSize=512m",
    "-XX:ReservedCodeCacheSize=512m",
    "-XX:NonNMethodCodeHeapSize=32m",
    "-XX:NonProfiledCodeHeapSize=240m",
    "-XX:ProfiledCodeHeapSize=240m",
    "-XX:TLABSize=16m",
    "-XX:-ResizeTLAB",
    "-XX:+UseParallelGC",
    "-XX:-UseAdaptiveSizePolicy",
    "-XX:MaxInlineLevel=20",
    "-XX:InlineSmallCode=2500", // Use defaults from Open JDK 17+
    "-XX:+AlwaysPreTouch",
    "-XX:+UseTransparentHugePages",
    "-XX:-UseDynamicNumberOfGCThreads",
    "-XX:+UseNUMA",
    "-XX:-UseAdaptiveNUMAChunkSizing",
    "-XX:+PerfDisableSharedMem", // See https://github.com/Simonis/mmap-pause#readme
    "-XX:-UseDynamicNumberOfCompilerThreads",
    "-XX:-UsePerfData",
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+TrustFinalNonStaticFields",
    "--add-modules",
    "jdk.incubator.vector",
    "--add-exports",
    "java.base/jdk.internal.vm.vector=ALL-UNNAMED"
  )
)
abstract class BaseBenchmark
