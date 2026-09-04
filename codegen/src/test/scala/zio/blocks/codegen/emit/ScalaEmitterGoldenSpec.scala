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

import zio.blocks.codegen.ir._
import zio.test._

object ScalaEmitterGoldenSpec extends ZIOSpecDefault {

  private val goldenFile: ScalaFile = ScalaFile(
    packageDecl = PackageDecl("com.example"),
    imports = List(
      Import.GroupImport("com.example", List("b", "a")),
      Import.RenameImport("com.legacy", "Old", "New"),
      Import.SingleImport("zio", "Chunk"),
      Import.WildcardImport("scala.collection")
    ),
    types = List(
      CaseClass(
        name = "User",
        fields = List(
          Field("id", TypeRef.String, annotations = List(Annotation("required"))),
          Field("name", TypeRef.String, defaultValue = Some("\"anon\"")),
          Field("tags", TypeRef("List", List(TypeRef.String))),
          Field("choice", TypeRef("|", List(TypeRef.Int, TypeRef.String))),
          Field("both", TypeRef("&", List(TypeRef("A"), TypeRef("B"))))
        ),
        typeParams = List(TypeParam("A", Variance.Covariant, upperBound = Some(TypeRef("AnyRef")))),
        extendsTypes = List(TypeRef("Product"), TypeRef("Serializable")),
        derives = List("CanEqual"),
        annotations = List(Annotation("deprecated", List(("message", "\"use v2\"")))),
        companion = Some(
          CompanionObject(
            List(
              ObjectMember.ValMember("defaultLimit", TypeRef.Int, "100"),
              ObjectMember.DefMember(
                Method(
                  name = "identity",
                  typeParams = List(TypeParam("A")),
                  params = List(ParamList(List(MethodParam("x", TypeRef("A"))))),
                  returnType = TypeRef("A"),
                  body = Some("x")
                )
              )
            )
          )
        ),
        doc = Some("A user.")
      ),
      SealedTrait(
        name = "Shape",
        cases = List(
          SealedTraitCase.CaseClassCase(CaseClass("Circle", List(Field("r", TypeRef.Double)))),
          SealedTraitCase.CaseObjectCase("Unit")
        )
      ),
      Enum(
        name = "Color",
        cases = List(
          EnumCase.SimpleCase("Red"),
          EnumCase.SimpleCase("Green"),
          EnumCase.ParameterizedCase(
            "Custom",
            List(Field("rgb", TypeRef.Int, annotations = List(Annotation("range", List(("min", "0"))))))
          )
        )
      ),
      TypeAlias(
        name = "UserId",
        typeParams = List(TypeParam("A")),
        typeRef = TypeRef.String
      ),
      ObjectDef(
        name = "Codecs",
        members = List(ObjectMember.ValMember("version", TypeRef.String, "\"1.0\"")),
        extendsTypes = List(TypeRef("Serializable"))
      )
    )
  )

  def spec = suite("ScalaEmitterGolden")(
    test("full-file emission is byte-identical to the golden snapshot") {
      val result = ScalaEmitter.emit(goldenFile, EmitterConfig.default)
      assertTrue(
        result ==
          """|package com.example
             |
             |import com.example.{b, a}
             |import com.legacy.{Old as New}
             |import scala.collection.*
             |import zio.Chunk
             |
             |/** A user. */
             |@deprecated(message = "use v2")
             |case class User[+A <: AnyRef](
             |  @required
             |  id: String,
             |  name: String = "anon",
             |  tags: List[String],
             |  choice: Int | String,
             |  both: A & B,
             |) extends Product with Serializable derives CanEqual
             |
             |object User {
             |  val defaultLimit: Int = 100
             |  def identity[A](x: A): A = x
             |}
             |
             |sealed trait Shape
             |
             |object Shape {
             |  case class Circle(
             |    r: Double,
             |  ) extends Shape
             |  case object Unit extends Shape
             |}
             |
             |enum Color {
             |  case Red
             |  case Green
             |  case Custom(@range(min = 0) rgb: Int)
             |}
             |
             |type UserId[A] = String
             |
             |object Codecs extends Serializable {
             |  val version: String = "1.0"
             |}
             |""".stripMargin
      )
    }
  )
}
