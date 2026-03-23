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

package zio.blocks.schema.toon

import zio.blocks.schema.SchemaBaseSpec
import zio.blocks.schema.toon.ToonTestUtils._
import zio.test._
import zio.test.Assertion._
import scala.util.Try

object WriterConfigSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("WriterConfigSpec")(
    test("have safe and handy defaults") {
      assert(WriterConfig.indent)(equalTo(2)) &&
      assert(WriterConfig.delimiter)(equalTo(Delimiter.Comma)) &&
      assert(WriterConfig.keyFolding)(equalTo(KeyFolding.Off)) &&
      assert(WriterConfig.flattenDepth)(equalTo(Int.MaxValue)) &&
      assert(WriterConfig.discriminatorField)(equalTo(None))
    },
    test("allow to set values") {
      assert(WriterConfig.withIndent(3).indent)(equalTo(3)) &&
      assert(WriterConfig.withDelimiter(Delimiter.Pipe).delimiter)(equalTo(Delimiter.Pipe)) &&
      assert(WriterConfig.withKeyFolding(KeyFolding.Safe).keyFolding)(equalTo(KeyFolding.Safe)) &&
      assert(WriterConfig.withFlattenDepth(12).flattenDepth)(equalTo(12)) &&
      assert(WriterConfig.withDiscriminatorField(Some("type")).discriminatorField)(isSome(equalTo("type")))
    },
    test("throw exception in case for unsupported values of params") {
      assert(Try(WriterConfig.withIndent(-1)).toEither)(isLeft(hasError("'indent' should be not less than 0"))) &&
      assert(Try(WriterConfig.withFlattenDepth(-1)).toEither)(
        isLeft(hasError("'flattenDepth' should be not less than 0"))
      )
    }
  )
}
