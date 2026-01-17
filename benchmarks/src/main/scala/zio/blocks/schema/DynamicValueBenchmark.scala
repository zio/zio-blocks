/*
 * Copyright 2023 ZIO Blocks Maintainers
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

  implicit lazy val schema: Schema[LinkedList] = Schema.derived
}
