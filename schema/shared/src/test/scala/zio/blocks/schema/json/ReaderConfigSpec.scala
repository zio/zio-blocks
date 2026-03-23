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

package zio.blocks.schema.json

import zio.blocks.schema.SchemaBaseSpec
import zio.blocks.schema.json.JsonTestUtils._
import zio.test.Assertion._
import zio.test._
import scala.util.Try

object ReaderConfigSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("ReaderConfigSpec")(
    test("have safe and handy defaults") {
      assert(ReaderConfig.maxBufSize)(equalTo(33554432)) &&
      assert(ReaderConfig.maxCharBufSize)(equalTo(4194304)) &&
      assert(ReaderConfig.preferredBufSize)(equalTo(32768)) &&
      assert(ReaderConfig.preferredCharBufSize)(equalTo(4096))
    },
    test("allow to set values") {
      assert(ReaderConfig.withMaxBufSize(32768).maxBufSize)(equalTo(32768)) &&
      assert(ReaderConfig.withMaxCharBufSize(4096).maxCharBufSize)(equalTo(4096)) &&
      assert(ReaderConfig.withPreferredBufSize(12).preferredBufSize)(equalTo(12)) &&
      assert(ReaderConfig.withPreferredCharBufSize(0).preferredCharBufSize)(equalTo(0))
    },
    test("throw exception in case for unsupported values of params") {
      assert(Try(ReaderConfig.withMaxBufSize(32767)).toEither)(
        isLeft(hasError("'maxBufSize' should be not less than 'preferredBufSize'"))
      ) &&
      assert(Try(ReaderConfig.withMaxBufSize(2147483646)).toEither)(
        isLeft(hasError("'maxBufSize' should be not greater than 2147483645"))
      ) &&
      assert(Try(ReaderConfig.withMaxCharBufSize(4095)).toEither)(
        isLeft(hasError("'maxCharBufSize' should be not less than 'preferredCharBufSize'"))
      ) &&
      assert(Try(ReaderConfig.withMaxCharBufSize(2147483646)).toEither)(
        isLeft(hasError("'maxCharBufSize' should be not greater than 2147483645"))
      ) &&
      assert(Try(ReaderConfig.withPreferredBufSize(11)).toEither)(
        isLeft(hasError("'preferredBufSize' should be not less than 12"))
      ) &&
      assert(Try(ReaderConfig.withPreferredBufSize(33554433)).toEither)(
        isLeft(hasError("'preferredBufSize' should be not greater than 'maxBufSize'"))
      ) &&
      assert(Try(ReaderConfig.withPreferredCharBufSize(-1)).toEither)(
        isLeft(hasError("'preferredCharBufSize' should be not less than 0"))
      ) &&
      assert(Try(ReaderConfig.withPreferredCharBufSize(4194305)).toEither)(
        isLeft(hasError("'preferredCharBufSize' should be not greater than 'maxCharBufSize'"))
      )
    }
  )
}
