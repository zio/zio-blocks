package zio.blocks.schema

import org.openjdk.jmh.annotations._
import zio.blocks.schema.binding.Binding

class TraversalFoldBenchmark extends BaseBenchmark {
  @Param(Array("1", "10", "100", "1000", "10000"))
  var size: Int = 10

  var ai: Array[Int] = (1 to size).toArray

  @Setup
  def setup(): Unit = ai = (1 to size).toArray

  @Benchmark
  def direct: Int = {
    var res = 0
    var i   = 0
    while (i < ai.length) {
      res += ai(i)
      i += 1
    }
    res
  }

  @Benchmark
  def zioBlocks: Int = TraversalDomain.a_i.fold[Int](ai)(0, _ + _)
}

class TraversalModifyBenchmark extends BaseBenchmark {
  @Param(Array("1", "10", "100", "1000", "10000"))
  var size: Int = 10

  var ai: Array[Int] = (1 to size).toArray

  @Setup
  def setup(): Unit = ai = (1 to size).toArray

  @Benchmark
  def direct: Array[Int] = {
    val res = new Array[Int](ai.length)
    var i   = 0
    while (i < ai.length) {
      res(i) = ai(i) + 1
      i += 1
    }
    res
  }

  @Benchmark
  def quicklens: Array[Int] = {
    import com.softwaremill.quicklens._

    TraversalDomain.a_i_quicklens.apply(ai).using(_ + 1)
  }

  @Benchmark
  def zioBlocks: Array[Int] = TraversalDomain.a_i.modify(ai, _ + 1)
}

object TraversalDomain {
  import com.softwaremill.quicklens._

  val a_i: Traversal[Array[Int], Int]                          = Traversal.arrayValues(Reflect.int[Binding])
  val a_i_quicklens: Array[Int] => PathModify[Array[Int], Int] = modify(_: Array[Int])(_.each)
}
