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

package as

import zio.blocks.schema.{As, Into, SchemaError}
import util.ShowExpr.show

// As.apply(intoAB, intoBA) builds a bidirectional As from two custom Into instances.
// Use this when macro derivation is not possible or the conversion logic is custom.
object AsManualConstructionExample extends App {

  val toStr: Into[Int, String] = n => Right(n.toString)
  val toInt: Into[String, Int] = s =>
    s.toIntOption match {
      case Some(n) => Right(n)
      case None    => Left(SchemaError(s"not a valid integer: '$s'"))
    }

  val intStringAs: As[Int, String] = As(toStr, toInt)

  // Forward: Int → String always succeeds
  show(intStringAs.into(42))

  // Reverse: String → Int succeeds for a valid integer string
  show(intStringAs.from("100"))

  // Reverse: String → Int fails for a non-integer string
  show(intStringAs.from("not-a-number"))
}

// As.apply[A, B] with no arguments summons an implicit As[A, B] already in scope.
// This retrieves the instance by type rather than by variable name.
object AsSummoningExample extends App {

  case class Metric(value: Int)
  case class Imperial(value: Long)

  implicit val conv: As[Metric, Imperial] = As.derived[Metric, Imperial]

  // Summon the implicit instance by type
  val summoned: As[Metric, Imperial] = As[Metric, Imperial]

  show(summoned.into(Metric(100)))
  show(summoned.from(Imperial(200L)))
  show(summoned.from(Imperial(Long.MaxValue)))
}
