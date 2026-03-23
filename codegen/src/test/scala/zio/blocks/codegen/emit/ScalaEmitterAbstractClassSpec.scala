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

object ScalaEmitterAbstractClassSpec extends ZIOSpecDefault {
  def spec =
    suite("ScalaEmitter.emitAbstractClass")(
      test("simple abstract class with no fields") {
        val ac     = AbstractClass("Base")
        val result = ScalaEmitter.emitAbstractClass(ac, EmitterConfig.default)
        assertTrue(result == "abstract class Base")
      },
      test("abstract class with fields") {
        val ac = AbstractClass(
          "Entity",
          fields = List(Field("id", TypeRef.Long))
        )
        val result = ScalaEmitter.emitAbstractClass(ac, EmitterConfig.default)
        assertTrue(
          result ==
            """|abstract class Entity(
               |  id: Long,
               |)""".stripMargin
        )
      },
      test("abstract class with type params and extends") {
        val ac = AbstractClass(
          "Container",
          typeParams = List(TypeParam("A")),
          extendsTypes = List(TypeRef("Serializable"))
        )
        val result = ScalaEmitter.emitAbstractClass(ac, EmitterConfig.default)
        assertTrue(result == "abstract class Container[A] extends Serializable")
      },
      test("abstract class with abstract member") {
        val ac = AbstractClass(
          "Base",
          members = List(
            ObjectMember.DefMember(Method("name", returnType = TypeRef.String))
          )
        )
        val result = ScalaEmitter.emitAbstractClass(ac, EmitterConfig.default)
        assertTrue(
          result ==
            """|abstract class Base {
               |  def name: String
               |}""".stripMargin
        )
      },
      test("abstract class with doc and annotations") {
        val ac = AbstractClass(
          "Base",
          annotations = List(Annotation("serializable")),
          doc = Some("Base entity")
        )
        val result = ScalaEmitter.emitAbstractClass(ac, EmitterConfig.default)
        assertTrue(
          result ==
            """|/** Base entity */
               |@serializable
               |abstract class Base""".stripMargin
        )
      },
      test("abstract class with companion") {
        val ac = AbstractClass(
          "Base",
          companion = Some(
            CompanionObject(
              members = List(
                ObjectMember.ValMember("version", TypeRef.Int, "1")
              )
            )
          )
        )
        val result = ScalaEmitter.emitAbstractClass(ac, EmitterConfig.default)
        assertTrue(
          result ==
            """|abstract class Base
               |
               |object Base {
               |  val version: Int = 1
               |}""".stripMargin
        )
      },
      test("abstract class emitted via emitTypeDefinition") {
        val ac: TypeDefinition = AbstractClass("Base")
        val result             = ScalaEmitter.emitTypeDefinition(ac, EmitterConfig.default)
        assertTrue(result == "abstract class Base")
      },
      test("sealed abstract class (isSealed flag not yet emitted)") {
        val ac     = AbstractClass("Entity", fields = List(Field("id", TypeRef.Long)), isSealed = true)
        val result = ScalaEmitter.emitAbstractClass(ac, EmitterConfig.default)
        assertTrue(
          result.contains("abstract class Entity("),
          result.contains("id: Long")
        )
      },
      test("sealed abstract class with type params") {
        val ac = AbstractClass(
          "Container",
          typeParams = List(TypeParam("A", Variance.Covariant)),
          isSealed = true
        )
        val result = ScalaEmitter.emitAbstractClass(ac, EmitterConfig.default)
        assertTrue(result.contains("abstract class Container[+A]"))
      },
      test("sealed abstract class with fields and members") {
        val ac = AbstractClass(
          "Entity",
          fields = List(Field("id", TypeRef.Long)),
          members = List(
            ObjectMember.DefMember(Method("name", returnType = TypeRef.String))
          ),
          isSealed = true
        )
        val result = ScalaEmitter.emitAbstractClass(ac, EmitterConfig.default)
        assertTrue(
          result.contains("abstract class Entity("),
          result.contains("id: Long"),
          result.contains("def name: String")
        )
      },
      test("abstract class with extends and fields") {
        val ac = AbstractClass(
          "Animal",
          fields = List(Field("name", TypeRef.String)),
          extendsTypes = List(TypeRef("Serializable"))
        )
        val result = ScalaEmitter.emitAbstractClass(ac, EmitterConfig.default)
        assertTrue(
          result.contains("abstract class Animal("),
          result.contains("name: String"),
          result.contains("extends Serializable")
        )
      },
      test("abstract class with multiple fields and trailing commas") {
        val ac = AbstractClass(
          "Point",
          fields = List(
            Field("x", TypeRef.Int),
            Field("y", TypeRef.Int),
            Field("z", TypeRef.Int)
          )
        )
        val result = ScalaEmitter.emitAbstractClass(ac, EmitterConfig.default)
        assertTrue(
          result.contains("x: Int,"),
          result.contains("y: Int,"),
          result.contains("z: Int,")
        )
      },
      test("abstract class with no trailing commas in Scala 2") {
        val ac = AbstractClass(
          "Point",
          fields = List(
            Field("x", TypeRef.Int),
            Field("y", TypeRef.Int)
          )
        )
        val result = ScalaEmitter.emitAbstractClass(ac, EmitterConfig.scala2)
        val lines  = result.split("\n")
        assertTrue(
          result.contains("x: Int,"),
          !lines.last.trim.startsWith("y: Int,")
        )
      },
      test("abstract class indented") {
        val ac     = AbstractClass("Inner")
        val result = ScalaEmitter.emitAbstractClass(ac, EmitterConfig.default, indent = 2)
        assertTrue(result == "    abstract class Inner")
      }
    )
}
