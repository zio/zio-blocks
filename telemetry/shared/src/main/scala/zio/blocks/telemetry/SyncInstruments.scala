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

package zio.blocks.telemetry

private[telemetry] object SyncInstrumentsHelper {

  def buildPooledAttributes(attrs: Seq[(String, Any)]): Attributes = {
    val builder = AttributeBuilderPool.get()
    var i       = 0
    while (i < attrs.length) {
      val (k, v) = attrs(i)
      v match {
        case s: String  => builder.put(k, s)
        case l: Long    => builder.put(k, l)
        case i: Int     => builder.put(k, i.toLong)
        case d: Double  => builder.put(k, d)
        case b: Boolean => builder.put(k, b)
        case other      => builder.put(k, other.toString)
      }
      i += 1
    }
    builder.build
  }

  def mapToAttributes(map: Map[String, AttributeValue]): Attributes = {
    val builder = Attributes.builder
    map.foreach { case (k, v) =>
      v match {
        case AttributeValue.StringValue(s)       => builder.put(k, s)
        case AttributeValue.BooleanValue(b)      => builder.put(k, b)
        case AttributeValue.LongValue(l)         => builder.put(k, l)
        case AttributeValue.DoubleValue(d)       => builder.put(k, d)
        case AttributeValue.StringSeqValue(seq)  => builder.put(AttributeKey.stringSeq(k), seq)
        case AttributeValue.LongSeqValue(seq)    => builder.put(AttributeKey.longSeq(k), seq)
        case AttributeValue.DoubleSeqValue(seq)  => builder.put(AttributeKey.doubleSeq(k), seq)
        case AttributeValue.BooleanSeqValue(seq) => builder.put(AttributeKey.booleanSeq(k), seq)
      }
    }
    builder.build
  }

  def listFromJava[A](javaList: java.util.ArrayList[A]): List[A] = {
    var result: List[A] = Nil
    var i               = javaList.size() - 1
    while (i >= 0) {
      result = javaList.get(i) :: result
      i -= 1
    }
    result
  }
}
