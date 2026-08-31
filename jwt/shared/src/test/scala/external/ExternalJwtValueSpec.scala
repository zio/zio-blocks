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

package external

import zio.blocks.jwt._
import zio.blocks.chunk.Chunk
import zio.test._

object ExternalJwtValueSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("ExternalJwtValueSpec")(
    test("external package can construct and use JwtValue public ADT") {
      val vStr   = JwtValue.Str("hello")
      val vNum   = JwtValue.Num("42")
      val vBool  = JwtValue.Bool(true)
      val vNull  = JwtValue.Null
      val vArr   = JwtValue.Arr(Chunk(vStr, vNum))
      val vObj   = JwtValue.Obj(Map("k" -> vStr))
      val claims = JwtClaims(extra =
        Map(
          "extStr"  -> vStr,
          "extNum"  -> vNum,
          "extBool" -> vBool,
          "extNull" -> vNull,
          "extArr"  -> vArr,
          "extObj"  -> vObj
        )
      )
      val key     = Array.fill(32)(0x01.toByte)
      val token   = Jwt.sign(claims, key, Algorithm.HS256)
      val decoded = token.flatMap(t => Jwt.decode(t, key, Algorithm.HS256))
      assertTrue(decoded.map(_.extra.get("extStr")) == Right(Some(vStr))) &&
      assertTrue(decoded.map(_.extra.get("extObj")) == Right(Some(vObj)))
    }
  )
}
