package zio.blocks.schema

import org.openjdk.jmh.annotations._
import zio.blocks.BaseBenchmark

class DynamicValueBenchmark extends BaseBenchmark {
  var a: DynamicValueDomain.LinkedList =
    DynamicValueDomain.Node(
      "A",
      DynamicValueDomain.Node(
        "B",
        DynamicValueDomain.Node("C", DynamicValueDomain.Node("D", DynamicValueDomain.Node("E", DynamicValueDomain.End)))
      )
    )
  var dv: DynamicValue = DynamicValueDomain.schema.toDynamicValue(a)

  @Benchmark
  def fromDynamicValue: Either[SchemaError, DynamicValueDomain.LinkedList] =
    DynamicValueDomain.schema.fromDynamicValue(dv)

  @Benchmark
  def toDynamicValue: DynamicValue = DynamicValueDomain.schema.toDynamicValue(a)
}

object DynamicValueDomain {
  sealed trait LinkedList

  object End extends LinkedList

  case class Node(value: String, next: LinkedList) extends LinkedList

  // implicit lazy val schema: Schema[LinkedList] = Schema.derived
}
