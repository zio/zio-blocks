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

package zio.blocks.schema.csv

import zio.blocks.schema._
import zio.test._

import java.nio.CharBuffer

object CsvWrapperCodecSpec extends SchemaBaseSpec {

  case class Name(value: String) extends AnyVal

  object Name {
    implicit val schema: Schema[Name] = Schema.derived
  }

  case class Age(value: Int) extends AnyVal

  object Age {
    implicit val schema: Schema[Age] = Schema.derived
  }

  private def deriveCodec[A](implicit s: Schema[A]): CsvCodec[A] =
    s.derive(CsvFormat)

  private def roundTrip[A](codec: CsvCodec[A], value: A): Either[SchemaError, A] = {
    val buf = CharBuffer.allocate(1024)
    codec.encode(value, buf)
    buf.flip()
    codec.decode(buf)
  }

  def spec = suite("CsvWrapperCodecSpec")(
    test("Name wrapper round-trips") {
      val codec = deriveCodec[Name]
      val value = Name("Alice")
      assertTrue(roundTrip(codec, value) == Right(value))
    },
    test("Age wrapper round-trips") {
      val codec = deriveCodec[Age]
      val value = Age(30)
      assertTrue(roundTrip(codec, value) == Right(value))
    },
    test("Name wrapper headerNames delegates to wrapped") {
      val codec = deriveCodec[Name]
      assertTrue(codec.headerNames == IndexedSeq("value"))
    }
  )
}
