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

package zio.blocks.config

import zio.test._

object SecretSpec extends ZIOSpecDefault {

  def spec = suite("SecretSpec")(
    test("Secret.apply and unwrap round-trip") {
      val secret = Secret(123)
      assertTrue(Secret.unwrap(secret) == 123)
    },
    test("Secret reader parses underlying values") {
      val reader = implicitly[Flag.Reader[Secret[Int]]]
      assertTrue(reader.parse("port", "8080") == Right(Secret(8080)))
    },
    test("Secret reader reports a secret type name") {
      val reader = implicitly[Flag.Reader[Secret[String]]]
      assertTrue(reader.typeName == "Secret[String]")
    },
    test("Secret displayable redacts values") {
      val displayable = Secret.displayable[String]
      assertTrue(displayable.display(Secret("token")) == "<secret>")
    }
  )
}
