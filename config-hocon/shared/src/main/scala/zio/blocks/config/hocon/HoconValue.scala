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

package zio.blocks.config.hocon

/**
 * A sealed trait representing a HOCON value node.
 */
sealed trait HoconValue

object HoconValue {
  final case class Obj(fields: Map[String, HoconValue]) extends HoconValue
  final case class Arr(elements: Seq[HoconValue])       extends HoconValue
  final case class Str(value: String)                   extends HoconValue
  final case class Num(value: Double)                   extends HoconValue
  final case class Bool(value: Boolean)                 extends HoconValue
  case object Null                                      extends HoconValue

  /**
   * Flatten a HoconValue into a map of dot-separated key-value pairs. Objects
   * are recursively flattened. Arrays are indexed numerically (e.g. "arr.0",
   * "arr.1"). Null values are omitted.
   */
  def flatten(value: HoconValue): Map[String, String] =
    flattenRec(value, "")

  private def flattenRec(value: HoconValue, prefix: String): Map[String, String] =
    value match {
      case Obj(fields) =>
        fields.foldLeft(Map.empty[String, String]) { case (acc, (key, v)) =>
          val newKey = if (prefix.isEmpty) key else s"$prefix.$key"
          acc ++ flattenRec(v, newKey)
        }
      case Arr(elements) =>
        elements.zipWithIndex.foldLeft(Map.empty[String, String]) { case (acc, (v, idx)) =>
          val newKey = if (prefix.isEmpty) idx.toString else s"$prefix.$idx"
          acc ++ flattenRec(v, newKey)
        }
      case Str(s) => Map(prefix -> s)
      case Num(n) =>
        val s = if (n == n.toLong && !n.isInfinite) n.toLong.toString else n.toString
        Map(prefix -> s)
      case Bool(b) => Map(prefix -> b.toString)
      case Null    => Map.empty
    }

  /**
   * Deep-merge two HoconValues. When both are Obj, fields are merged
   * recursively with the right side winning on conflicts. Otherwise the right
   * side replaces the left.
   */
  def deepMerge(left: HoconValue, right: HoconValue): HoconValue =
    (left, right) match {
      case (Obj(lf), Obj(rf)) =>
        val merged = rf.foldLeft(lf) { case (acc, (k, rv)) =>
          acc.get(k) match {
            case Some(lv) => acc.updated(k, deepMerge(lv, rv))
            case None     => acc.updated(k, rv)
          }
        }
        Obj(merged)
      case (_, r) => r
    }
}
