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

package into

import zio.blocks.schema.{Into, SchemaError}
import util.ShowExpr.show

// Demonstrates that Into collects ALL field errors into one SchemaError
// rather than stopping at the first failure.
object IntoErrorAccumulationExample extends App {

  final class Email private (val value: String) {
    override def toString: String = s"Email($value)"
  }
  object Email {
    def apply(value: String): Email = new Email(value)
  }

  implicit val stringToEmail: Into[String, Email] = { s =>
    if (s.contains("@")) Right(Email(s))
    else Left(SchemaError(s"Invalid email address: '$s'"))
  }

  case class RawRecord(id: Long, email: String, score: Double)
  case class ValidRecord(id: Int, email: Email, score: Int)

  val validate = Into.derived[RawRecord, ValidRecord]

  // Three fields, three independent failures:
  //   id:    Long.MaxValue overflows Int
  //   email: missing '@'
  //   score: 7.5 is not a whole number
  show(validate.into(RawRecord(Long.MaxValue, "not-an-email", 7.5)))

  // Only score fails — id and email are valid
  show(validate.into(RawRecord(42L, "carol@example.com", 7.5)))

  // All fields valid
  show(validate.into(RawRecord(42L, "carol@example.com", 100.0)))
}
