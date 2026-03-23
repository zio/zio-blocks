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

package zio.http

import zio.test._

object CharsetJvmSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("Charset JVM")(
    suite("toJava")(
      test("UTF8 maps to StandardCharsets.UTF_8") {
        assertTrue(Charset.UTF8.toJava == java.nio.charset.StandardCharsets.UTF_8)
      },
      test("ASCII maps to StandardCharsets.US_ASCII") {
        assertTrue(Charset.ASCII.toJava == java.nio.charset.StandardCharsets.US_ASCII)
      },
      test("ISO_8859_1 maps to StandardCharsets.ISO_8859_1") {
        assertTrue(Charset.ISO_8859_1.toJava == java.nio.charset.StandardCharsets.ISO_8859_1)
      },
      test("UTF16 maps to StandardCharsets.UTF_16") {
        assertTrue(Charset.UTF16.toJava == java.nio.charset.StandardCharsets.UTF_16)
      },
      test("UTF16BE maps to StandardCharsets.UTF_16BE") {
        assertTrue(Charset.UTF16BE.toJava == java.nio.charset.StandardCharsets.UTF_16BE)
      },
      test("UTF16LE maps to StandardCharsets.UTF_16LE") {
        assertTrue(Charset.UTF16LE.toJava == java.nio.charset.StandardCharsets.UTF_16LE)
      }
    )
  )
}
