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

package zio.blocks.schema

import zio.test._

object OpticLiteralSchemaSpec extends SchemaBaseSpec {

  case class Tag(value: String)

  object Tag {
    implicit val schema: Schema[Tag] = Schema.string.transform(Tag(_), _.value)
  }

  case class Item(tag: Tag, qty: Int)

  object Item extends CompanionOptics[Item] {
    implicit val schema: Schema[Item] = Schema.derived
    val tag: Lens[Item, Tag]          = $(_.tag)
    val qty: Lens[Item, Int]          = $(_.qty)
  }

  private def literalSchema(expr: SchemaExpr[Item, Boolean]): Option[Schema[_]] = expr.dynamic match {
    case DynamicSchemaExpr.Relational(_, DynamicSchemaExpr.Literal(_, schema), _) => Some(schema)
    case _                                                                        => None
  }

  private def usesFocusSchema(expr: SchemaExpr[Item, Boolean], focus: Reflect.Bound[_]): Boolean =
    literalSchema(expr).exists(_.reflect eq focus)

  private val item = Item(Tag("a"), 2)

  def spec: Spec[TestEnvironment, Any] = suite("OpticLiteralSchemaSpec")(
    test("a literal comparison describes the literal with the optic's focus schema") {
      assertTrue(
        usesFocusSchema(Item.qty === 1, Item.qty.focus),
        usesFocusSchema(Item.qty != 1, Item.qty.focus),
        usesFocusSchema(Item.qty > 1, Item.qty.focus),
        usesFocusSchema(Item.qty >= 1, Item.qty.focus),
        usesFocusSchema(Item.qty < 1, Item.qty.focus),
        usesFocusSchema(Item.qty <= 1, Item.qty.focus),
        usesFocusSchema(Item.tag === Tag("a"), Item.tag.focus)
      )
    },
    test("a Schema in scope where the comparison is written is not used for the literal") {
      implicit val upperCase: Schema[Tag] = Schema.string.transform(s => Tag(s.toUpperCase), _.value)
      val expr                            = Item.tag === Tag("a")
      assertTrue(
        usesFocusSchema(expr, Item.tag.focus),
        !literalSchema(expr).exists(_.reflect eq upperCase.reflect),
        expr.eval(item) == Right(Seq(true))
      )
    },
    test("the source and focus schemas are built once per optic") {
      assertTrue(
        Item.qty.focusSchema eq Item.qty.focusSchema,
        Item.qty.sourceSchema eq Item.qty.sourceSchema,
        literalSchema(Item.qty === 1).exists(s => literalSchema(Item.qty > 1).exists(_ eq s))
      )
    },
    test("literal comparisons evaluate as before") {
      assertTrue(
        (Item.qty === 2).eval(item) == Right(Seq(true)),
        (Item.qty != 2).eval(item) == Right(Seq(false)),
        (Item.qty > 1).eval(item) == Right(Seq(true)),
        (Item.qty >= 3).eval(item) == Right(Seq(false)),
        (Item.qty < 3).eval(item) == Right(Seq(true)),
        (Item.qty <= 1).eval(item) == Right(Seq(false)),
        (Item.tag === Tag("b")).eval(item) == Right(Seq(false))
      )
    }
  )
}
