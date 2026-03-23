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

object ScalaEmitterTraitSpec extends ZIOSpecDefault {
  def spec =
    suite("ScalaEmitter.emitTrait")(
      test("simple trait with no members") {
        val t      = Trait("Serializable")
        val result = ScalaEmitter.emitTrait(t, EmitterConfig.default)
        assertTrue(result == "trait Serializable")
      },
      test("trait with type params") {
        val t = Trait(
          "Container",
          typeParams = List(TypeParam("A", variance = Variance.Covariant))
        )
        val result = ScalaEmitter.emitTrait(t, EmitterConfig.default)
        assertTrue(result == "trait Container[+A]")
      },
      test("trait with extends") {
        val t = Trait(
          "Named",
          extendsTypes = List(TypeRef("HasId"), TypeRef("HasName"))
        )
        val result = ScalaEmitter.emitTrait(t, EmitterConfig.default)
        assertTrue(result == "trait Named extends HasId with HasName")
      },
      test("trait with members") {
        val t = Trait(
          "HasId",
          members = List(
            ObjectMember.DefMember(Method("id", returnType = TypeRef.Long))
          )
        )
        val result = ScalaEmitter.emitTrait(t, EmitterConfig.default)
        assertTrue(
          result ==
            """|trait HasId {
               |  def id: Long
               |}""".stripMargin
        )
      },
      test("trait with companion") {
        val t = Trait(
          "Showable",
          companion = Some(
            CompanionObject(
              members = List(
                ObjectMember.ValMember("empty", TypeRef("Showable"), "null")
              )
            )
          )
        )
        val result = ScalaEmitter.emitTrait(t, EmitterConfig.default)
        assertTrue(
          result ==
            """|trait Showable
               |
               |object Showable {
               |  val empty: Showable = null
               |}""".stripMargin
        )
      },
      test("trait with doc") {
        val t      = Trait("Marker", doc = Some("A marker trait"))
        val result = ScalaEmitter.emitTrait(t, EmitterConfig.default)
        assertTrue(
          result ==
            """|/** A marker trait */
               |trait Marker""".stripMargin
        )
      },
      test("trait with annotations") {
        val t = Trait(
          "Event",
          annotations = List(Annotation("deprecated"))
        )
        val result = ScalaEmitter.emitTrait(t, EmitterConfig.default)
        assertTrue(
          result ==
            """|@deprecated
               |trait Event""".stripMargin
        )
      },
      test("trait emitted via emitTypeDefinition") {
        val t: TypeDefinition = Trait("Marker")
        val result            = ScalaEmitter.emitTypeDefinition(t, EmitterConfig.default)
        assertTrue(result == "trait Marker")
      },
      test("trait with selfType and no members") {
        val t = Trait(
          "Service",
          selfType = Some(TypeRef("Logging"))
        )
        val result = ScalaEmitter.emitTrait(t, EmitterConfig.default)
        assertTrue(
          result ==
            """|trait Service {
               |  self: Logging =>
               |}""".stripMargin
        )
      },
      test("trait with selfType and members") {
        val t = Trait(
          "Service",
          selfType = Some(TypeRef("Logging")),
          members = List(
            ObjectMember.DefMember(Method("run", returnType = TypeRef.Unit))
          )
        )
        val result = ScalaEmitter.emitTrait(t, EmitterConfig.default)
        assertTrue(
          result ==
            """|trait Service {
               |  self: Logging =>
               |  def run: Unit
               |}""".stripMargin
        )
      },
      test("trait with multiple type params and members") {
        val t = Trait(
          "Applicative",
          typeParams = List(TypeParam("F")),
          members = List(
            ObjectMember.DefMember(
              Method(
                "pure",
                typeParams = List(TypeParam("A")),
                params = List(ParamList(List(MethodParam("a", TypeRef("A"))))),
                returnType = TypeRef("F", List(TypeRef("A")))
              )
            )
          )
        )
        val result = ScalaEmitter.emitTrait(t, EmitterConfig.default)
        assertTrue(
          result.contains("trait Applicative[F]"),
          result.contains("def pure[A](a: A): F[A]")
        )
      },
      test("trait with multiple extends and type params") {
        val t = Trait(
          "Service",
          typeParams = List(TypeParam("F", Variance.Covariant), TypeParam("A")),
          extendsTypes = List(TypeRef("HasId"), TypeRef("HasName"), TypeRef("Serializable"))
        )
        val result = ScalaEmitter.emitTrait(t, EmitterConfig.default)
        assertTrue(result == "trait Service[+F, A] extends HasId with HasName with Serializable")
      },
      test("trait emitted with indent") {
        val t      = Trait("Inner")
        val result = ScalaEmitter.emitTrait(t, EmitterConfig.default, indent = 1)
        assertTrue(result == "  trait Inner")
      },
      test("trait with companion and members") {
        val t = Trait(
          "Codec",
          typeParams = List(TypeParam("A")),
          members = List(
            ObjectMember.DefMember(Method("encode", returnType = TypeRef.String)),
            ObjectMember.DefMember(Method("decode", returnType = TypeRef("A")))
          ),
          companion = Some(
            CompanionObject(
              members = List(
                ObjectMember.ValMember("version", TypeRef.Int, "1")
              )
            )
          )
        )
        val result = ScalaEmitter.emitTrait(t, EmitterConfig.default)
        assertTrue(
          result.contains("trait Codec[A] {"),
          result.contains("def encode: String"),
          result.contains("def decode: A"),
          result.contains("object Codec {"),
          result.contains("val version: Int = 1")
        )
      }
    )
}
