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

package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicValue, PrimitiveValue, SchemaBaseSpec}
import zio.test._

/**
 * Cross-version interpreter tests (Scala 2.13 and 3.x) for enum and collection
 * operations. Builder-macro tests live in DynamicMigrationSpec (scala-3 only)
 * because they rely on inline methods.
 */
object MigrationInterpreterSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("MigrationInterpreterSpec")(
    suite("enum — RenameCase")(
      test("renames the matching variant case") {
        val input = DynamicValue.Record(
          Chunk("payment" -> DynamicValue.Variant("CreditCard", DynamicValue.Primitive(PrimitiveValue.String("4111"))))
        )
        val optic     = DynamicOptic.Field("payment", None)
        val migration = DynamicMigration(Vector(MigrationAction.RenameCase(optic, "CreditCard", "Card")))
        val result    = migration.apply(input)
        assertTrue(result.isRight)
        val field = result.toOption.get.asInstanceOf[DynamicValue.Record].fields.find(_._1 == "payment").get._2
        assertTrue(field.asInstanceOf[DynamicValue.Variant].caseNameValue == "Card")
      },
      test("is a no-op when the case name does not match") {
        val wire      = DynamicValue.Variant("WireTransfer", DynamicValue.Primitive(PrimitiveValue.String("routing")))
        val input     = DynamicValue.Record(Chunk("payment" -> wire))
        val optic     = DynamicOptic.Field("payment", None)
        val migration =
          DynamicMigration(Vector(MigrationAction.RenameCase(optic, "CreditCard", "Card")))
        val result = migration.apply(input)
        assertTrue(result.isRight)
        val field = result.toOption.get.asInstanceOf[DynamicValue.Record].fields.find(_._1 == "payment").get._2
        assertTrue(field.asInstanceOf[DynamicValue.Variant].caseNameValue == "WireTransfer")
      },
      test("structural reverse swaps from and to") {
        val action  = MigrationAction.RenameCase(DynamicOptic.Field("payment", None), "CreditCard", "Card")
        val reverse = action.reverse.asInstanceOf[MigrationAction.RenameCase]
        assertTrue(reverse.from == "Card" && reverse.to == "CreditCard")
      },
      test("reverse round-trips the rename") {
        val input = DynamicValue.Record(
          Chunk("payment" -> DynamicValue.Variant("CreditCard", DynamicValue.Primitive(PrimitiveValue.String("4111"))))
        )
        val optic     = DynamicOptic.Field("payment", None)
        val migration = DynamicMigration(Vector(MigrationAction.RenameCase(optic, "CreditCard", "Card")))
        val result    = migration.apply(input).flatMap(out => migration.reverse.apply(out))
        assertTrue(result.isRight)
        val field = result.toOption.get.asInstanceOf[DynamicValue.Record].fields.find(_._1 == "payment").get._2
        assertTrue(field.asInstanceOf[DynamicValue.Variant].caseNameValue == "CreditCard")
      }
    ),
    suite("enum — TransformCase (field-level)")(
      test("applies sub-actions to the matching case's inner value") {
        val inner = DynamicValue.Record(
          Chunk(
            "number" -> DynamicValue.Primitive(PrimitiveValue.String("4111")),
            "exp"    -> DynamicValue.Primitive(PrimitiveValue.String("12/25"))
          )
        )
        val input     = DynamicValue.Record(Chunk("payment" -> DynamicValue.Variant("CreditCard", inner)))
        val subAction = MigrationAction.DropField(DynamicOptic.Field("exp", None), DynamicSchemaExpr.DefaultValue)
        val optic     = DynamicOptic.Field("payment", Some(DynamicOptic.Case("CreditCard", None)))
        val migration = DynamicMigration(Vector(MigrationAction.TransformCase(optic, Vector(subAction))))
        val result    = migration.apply(input)
        assertTrue(result.isRight)
        val innerOut = result.toOption.get
          .asInstanceOf[DynamicValue.Record]
          .fields
          .find(_._1 == "payment")
          .get
          ._2
          .asInstanceOf[DynamicValue.Variant]
          .value
          .asInstanceOf[DynamicValue.Record]
        assertTrue(!innerOut.fields.exists(_._1 == "exp") && innerOut.fields.exists(_._1 == "number"))
      },
      test("is a no-op for a different case") {
        val wire =
          DynamicValue.Variant(
            "WireTransfer",
            DynamicValue.Record(Chunk("account" -> DynamicValue.Primitive(PrimitiveValue.String("123"))))
          )
        val input     = DynamicValue.Record(Chunk("payment" -> wire))
        val subAction = MigrationAction.DropField(DynamicOptic.Field("account", None), DynamicSchemaExpr.DefaultValue)
        val optic     = DynamicOptic.Field("payment", Some(DynamicOptic.Case("CreditCard", None)))
        val migration = DynamicMigration(Vector(MigrationAction.TransformCase(optic, Vector(subAction))))
        val result    = migration.apply(input)
        assertTrue(result.isRight)
        val innerOut = result.toOption.get
          .asInstanceOf[DynamicValue.Record]
          .fields
          .find(_._1 == "payment")
          .get
          ._2
          .asInstanceOf[DynamicValue.Variant]
          .value
          .asInstanceOf[DynamicValue.Record]
        assertTrue(innerOut.fields.exists(_._1 == "account"))
      }
    ),
    suite("MigrationError path rendering")(
      test("renders nested field path") {
        val optic = DynamicOptic.Field("a", Some(DynamicOptic.Field("b", None)))
        assertTrue(MigrationError.renderOptic(optic) == ".a.b")
      },
      test("renders Case optic") {
        val optic = DynamicOptic.Field("payment", Some(DynamicOptic.Case("CreditCard", None)))
        assertTrue(MigrationError.renderOptic(optic) == ".payment.when[CreditCard]")
      },
      test("renders Element optic") {
        val optic = DynamicOptic.Field("items", Some(DynamicOptic.Element(None)))
        assertTrue(MigrationError.renderOptic(optic) == ".items.each")
      },
      test("renders Key optic") {
        val optic = DynamicOptic.Field("map", Some(DynamicOptic.Key(None)))
        assertTrue(MigrationError.renderOptic(optic) == ".map.key")
      },
      test("renders Value optic") {
        val optic = DynamicOptic.Field("map", Some(DynamicOptic.Value(None)))
        assertTrue(MigrationError.renderOptic(optic) == ".map.value")
      },
      test("error toString includes path") {
        val optic =
          DynamicOptic.Field("payment", Some(DynamicOptic.Case("CreditCard", Some(DynamicOptic.Field("number", None)))))
        val err = MigrationError(optic, "Rename failed")
        assertTrue(err.toString == "Rename failed at .payment.when[CreditCard].number")
      }
    ),
    suite("collection operations")(
      test("TransformElements applies expr to each sequence element") {
        val elems = Chunk(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2)),
          DynamicValue.Primitive(PrimitiveValue.Int(3))
        )
        val input       = DynamicValue.Record(Chunk("nums" -> DynamicValue.Sequence(elems)))
        val replacement =
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        val optic     = DynamicOptic.Field("nums", None)
        val migration = DynamicMigration(Vector(MigrationAction.TransformElements(optic, replacement)))
        val result    = migration.apply(input)
        assertTrue(result.isRight)
        val seq = result.toOption.get
          .asInstanceOf[DynamicValue.Record]
          .fields
          .find(_._1 == "nums")
          .get
          ._2
          .asInstanceOf[DynamicValue.Sequence]
        assertTrue(
          seq.elements.length == 3 &&
            seq.elements.forall(_ == DynamicValue.Primitive(PrimitiveValue.Int(0)))
        )
      },
      test("TransformKeys replaces every map key") {
        val entries = Chunk(
          DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.String("b")) -> DynamicValue.Primitive(PrimitiveValue.Int(2))
        )
        val input     = DynamicValue.Record(Chunk("m" -> DynamicValue.Map(entries)))
        val newKey    = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("x")))
        val optic     = DynamicOptic.Field("m", None)
        val migration = DynamicMigration(Vector(MigrationAction.TransformKeys(optic, newKey)))
        val result    = migration.apply(input)
        assertTrue(result.isRight)
        val map = result.toOption.get
          .asInstanceOf[DynamicValue.Record]
          .fields
          .find(_._1 == "m")
          .get
          ._2
          .asInstanceOf[DynamicValue.Map]
        assertTrue(map.entries.forall(_._1 == DynamicValue.Primitive(PrimitiveValue.String("x"))))
        assertTrue(map.entries(0)._2 == DynamicValue.Primitive(PrimitiveValue.Int(1)))
        assertTrue(map.entries(1)._2 == DynamicValue.Primitive(PrimitiveValue.Int(2)))
      },
      test("TransformValues replaces every map value") {
        val entries = Chunk(
          DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.String("b")) -> DynamicValue.Primitive(PrimitiveValue.Int(2))
        )
        val input     = DynamicValue.Record(Chunk("m" -> DynamicValue.Map(entries)))
        val newValue  = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(99)))
        val optic     = DynamicOptic.Field("m", None)
        val migration = DynamicMigration(Vector(MigrationAction.TransformValues(optic, newValue)))
        val result    = migration.apply(input)
        assertTrue(result.isRight)
        val map = result.toOption.get
          .asInstanceOf[DynamicValue.Record]
          .fields
          .find(_._1 == "m")
          .get
          ._2
          .asInstanceOf[DynamicValue.Map]
        assertTrue(map.entries.forall(_._2 == DynamicValue.Primitive(PrimitiveValue.Int(99))))
        assertTrue(map.entries(0)._1 == DynamicValue.Primitive(PrimitiveValue.String("a")))
        assertTrue(map.entries(1)._1 == DynamicValue.Primitive(PrimitiveValue.String("b")))
      },
      test("TransformElements propagates sub-expression failure") {
        val elems     = Chunk(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val input     = DynamicValue.Record(Chunk("nums" -> DynamicValue.Sequence(elems)))
        val fail      = DynamicSchemaExpr.Fail("boom")
        val optic     = DynamicOptic.Field("nums", None)
        val migration = DynamicMigration(Vector(MigrationAction.TransformElements(optic, fail)))
        val result    = migration.apply(input)
        assertTrue(result.isLeft)
        assertTrue(result.left.toOption.get.message == "boom")
      }
    )
  )
}
