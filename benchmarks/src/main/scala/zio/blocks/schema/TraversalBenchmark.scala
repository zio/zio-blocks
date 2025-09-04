package zio.blocks.schema

import org.openjdk.jmh.annotations._
import zio.blocks.schema.binding.Binding

class TraversalFoldBenchmark extends BaseBenchmark {
  import zio.blocks.schema.TraversalDomain._

  @Param(Array("1", "10", "100", "1000", "10000"))
  var size: Int = 10

  var ai: Array[Int] = (1 to size).toArray

  @Setup
  def setup(): Unit = ai = (1 to size).toArray

  @Benchmark
  def direct: Int = {
    var res = 0
    var idx = 0
    while (idx < ai.length) {
      res += ai(idx)
      idx += 1
    }
    res
  }

  @Benchmark
  def zioBlocks: Int = a_i.fold[Int](ai)(0, _ + _)
}

class TraversalModifyBenchmark extends BaseBenchmark {
  import zio.blocks.schema.TraversalDomain._

  @Param(Array("1", "10", "100", "1000", "10000"))
  var size: Int = 10

  var ai: Array[Int] = (1 to size).toArray

  @Setup
  def setup(): Unit = ai = (1 to size).toArray

  @Benchmark
  def direct: Array[Int] = {
    val res = new Array[Int](ai.length)
    var idx = 0
    while (idx < ai.length) {
      res(idx) = ai(idx) + 1
      idx += 1
    }
    res
  }

  @Benchmark
  def quicklens: Array[Int] = a_i_quicklens.apply(ai).using(_ + 1)

  @Benchmark
  def zioBlocks: Array[Int] = a_i.modify(ai, _ + 1)
}

object TraversalDomain {
  import com.softwaremill.quicklens._

  val a_i: Traversal[Array[Int], Int] =
    Traversal.seqValues(
      Schema
        .derived[Array[Int]]
        .reflect
        .asSequenceUnknown
        .get
        .sequence
        .asInstanceOf[Reflect.Sequence[Binding, Int, Array]]
    )
  val a_i_quicklens: Array[Int] => PathModify[Array[Int], Int] = modify(_: Array[Int])(_.each)
}
