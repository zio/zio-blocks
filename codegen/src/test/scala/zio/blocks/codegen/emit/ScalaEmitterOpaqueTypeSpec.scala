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

package zio.blocks.codegen.emit

import zio.test._
import zio.blocks.codegen.ir._

object ScalaEmitterOpaqueTypeSpec extends ZIOSpecDefault {
  def spec =
    suite("ScalaEmitter.emitOpaqueType")(
      test("simple opaque type in Scala 3") {
        val ot     = OpaqueType("UserId", TypeRef.String)
        val result = ScalaEmitter.emitOpaqueType(ot, EmitterConfig.default)
        assertTrue(result == "opaque type UserId = String")
      },
      test("opaque type with upper bound in Scala 3") {
        val ot = OpaqueType(
          "Age",
          underlyingType = TypeRef.Int,
          upperBound = Some(TypeRef.Int)
        )
        val result = ScalaEmitter.emitOpaqueType(ot, EmitterConfig.default)
        assertTrue(result == "opaque type Age <: Int = Int")
      },
      test("opaque type falls back to type alias in Scala 2") {
        val ot     = OpaqueType("UserId", TypeRef.String)
        val result = ScalaEmitter.emitOpaqueType(ot, EmitterConfig.scala2)
        assertTrue(result == "type UserId = String")
      },
      test("opaque type with companion in Scala 3") {
        val ot = OpaqueType(
          "UserId",
          underlyingType = TypeRef.String,
          companion = Some(
            CompanionObject(
              members = List(
                ObjectMember.DefMember(
                  Method(
                    "apply",
                    params = List(ParamList(List(MethodParam("value", TypeRef.String)))),
                    returnType = TypeRef("UserId")
                  )
                )
              )
            )
          )
        )
        val result = ScalaEmitter.emitOpaqueType(ot, EmitterConfig.default)
        assertTrue(
          result ==
            """|opaque type UserId = String
               |
               |object UserId {
               |  def apply(value: String): UserId
               |}""".stripMargin
        )
      },
      test("opaque type with doc") {
        val ot     = OpaqueType("UserId", TypeRef.String, doc = Some("A user identifier"))
        val result = ScalaEmitter.emitOpaqueType(ot, EmitterConfig.default)
        assertTrue(
          result ==
            """|/** A user identifier */
               |opaque type UserId = String""".stripMargin
        )
      },
      test("opaque type emitted via emitTypeDefinition") {
        val ot: TypeDefinition = OpaqueType("UserId", TypeRef.String)
        val result             = ScalaEmitter.emitTypeDefinition(ot, EmitterConfig.default)
        assertTrue(result == "opaque type UserId = String")
      },
      test("opaque type with annotations") {
        val ot = OpaqueType(
          "Token",
          underlyingType = TypeRef.String,
          annotations = List(Annotation("newtype"))
        )
        val result = ScalaEmitter.emitOpaqueType(ot, EmitterConfig.default)
        assertTrue(
          result.contains("@newtype"),
          result.contains("opaque type Token = String")
        )
      },
      test("opaque type with complex underlying type") {
        val ot     = OpaqueType("Ids", underlyingType = TypeRef.list(TypeRef.Long))
        val result = ScalaEmitter.emitOpaqueType(ot, EmitterConfig.default)
        assertTrue(result == "opaque type Ids = List[Long]")
      },
      test("opaque type with upper bound in Scala 2 omits upper bound") {
        val ot = OpaqueType(
          "Age",
          underlyingType = TypeRef.Int,
          upperBound = Some(TypeRef.Int)
        )
        val result = ScalaEmitter.emitOpaqueType(ot, EmitterConfig.scala2)
        assertTrue(result == "type Age <: Int = Int")
      },
      test("opaque type indented") {
        val ot     = OpaqueType("Token", underlyingType = TypeRef.String)
        val result = ScalaEmitter.emitOpaqueType(ot, EmitterConfig.default, indent = 1)
        assertTrue(result == "  opaque type Token = String")
      }
    )
}
