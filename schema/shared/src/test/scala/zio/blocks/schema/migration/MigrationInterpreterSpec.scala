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
      },
      test("TransformElements fails when field is not a Sequence") {
        val input     = DynamicValue.Record(Chunk("nums" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val optic     = DynamicOptic.Field("nums", None)
        val migration = DynamicMigration(Vector(MigrationAction.TransformElements(optic, DynamicSchemaExpr.DefaultValue)))
        assertTrue(migration.apply(input).isLeft)
      },
      test("TransformElements fails when field not found") {
        val input     = DynamicValue.Record(Chunk("x" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val optic     = DynamicOptic.Field("nums", None)
        val migration = DynamicMigration(Vector(MigrationAction.TransformElements(optic, DynamicSchemaExpr.DefaultValue)))
        assertTrue(migration.apply(input).isLeft)
      },
      test("TransformKeys fails when field is not a Map") {
        val input     = DynamicValue.Record(Chunk("m" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val optic     = DynamicOptic.Field("m", None)
        val migration = DynamicMigration(Vector(MigrationAction.TransformKeys(optic, DynamicSchemaExpr.DefaultValue)))
        assertTrue(migration.apply(input).isLeft)
      },
      test("TransformKeys fails when field not found") {
        val input     = DynamicValue.Record(Chunk("x" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val optic     = DynamicOptic.Field("m", None)
        val migration = DynamicMigration(Vector(MigrationAction.TransformKeys(optic, DynamicSchemaExpr.DefaultValue)))
        assertTrue(migration.apply(input).isLeft)
      },
      test("TransformValues fails when field is not a Map") {
        val input     = DynamicValue.Record(Chunk("m" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val optic     = DynamicOptic.Field("m", None)
        val migration = DynamicMigration(Vector(MigrationAction.TransformValues(optic, DynamicSchemaExpr.DefaultValue)))
        assertTrue(migration.apply(input).isLeft)
      },
      test("TransformValues fails when field not found") {
        val input     = DynamicValue.Record(Chunk("x" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val optic     = DynamicOptic.Field("m", None)
        val migration = DynamicMigration(Vector(MigrationAction.TransformValues(optic, DynamicSchemaExpr.DefaultValue)))
        assertTrue(migration.apply(input).isLeft)
      }
    ),
    suite("Mandate and Optionalize")(
      test("Mandate unwraps Some(v) to v") {
        val inner     = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val input = DynamicValue.Record(
          Chunk("count" -> DynamicValue.Variant("Some", inner))
        )
        val optic     = DynamicOptic.Field("count", None)
        val default   = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        val migration = DynamicMigration(Vector(MigrationAction.Mandate(optic, default)))
        val result    = migration.apply(input)
        assertTrue(result.isRight)
        val field = result.toOption.get.asInstanceOf[DynamicValue.Record].fields.find(_._1 == "count").get._2
        assertTrue(field == inner)
      },
      test("Mandate substitutes default for None") {
        val input = DynamicValue.Record(
          Chunk("count" -> DynamicValue.Variant("None", DynamicValue.Record(Chunk.empty)))
        )
        val optic     = DynamicOptic.Field("count", None)
        val default   = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(7)))
        val migration = DynamicMigration(Vector(MigrationAction.Mandate(optic, default)))
        val result    = migration.apply(input)
        assertTrue(result.isRight)
        val field = result.toOption.get.asInstanceOf[DynamicValue.Record].fields.find(_._1 == "count").get._2
        assertTrue(field == DynamicValue.Primitive(PrimitiveValue.Int(7)))
      },
      test("Mandate fails when field is not an Option variant") {
        val input = DynamicValue.Record(
          Chunk("count" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        val optic     = DynamicOptic.Field("count", None)
        val default   = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        val migration = DynamicMigration(Vector(MigrationAction.Mandate(optic, default)))
        assertTrue(migration.apply(input).isLeft)
      },
      test("Mandate fails when field not found") {
        val input     = DynamicValue.Record(Chunk("x" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val optic     = DynamicOptic.Field("count", None)
        val default   = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        val migration = DynamicMigration(Vector(MigrationAction.Mandate(optic, default)))
        assertTrue(migration.apply(input).isLeft)
      },
      test("Optionalize wraps a field value in Some") {
        val input     = DynamicValue.Record(Chunk("x" -> DynamicValue.Primitive(PrimitiveValue.Int(5))))
        val optic     = DynamicOptic.Field("x", None)
        val migration = DynamicMigration(Vector(MigrationAction.Optionalize(optic)))
        val result    = migration.apply(input)
        assertTrue(result.isRight)
        val field = result.toOption.get.asInstanceOf[DynamicValue.Record].fields.find(_._1 == "x").get._2
        assertTrue(field.asInstanceOf[DynamicValue.Variant].caseNameValue == "Some")
      },
      test("Optionalize fails when field not found") {
        val input     = DynamicValue.Record(Chunk("y" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val optic     = DynamicOptic.Field("x", None)
        val migration = DynamicMigration(Vector(MigrationAction.Optionalize(optic)))
        assertTrue(migration.apply(input).isLeft)
      },
      test("Optionalize.reverse throws UnsupportedOperationException") {
        val action = MigrationAction.Optionalize(DynamicOptic.Field("x", None))
        assertTrue(scala.util.Try(action.reverse).isFailure)
      },
      test("Mandate.reverse produces Optionalize") {
        val action  = MigrationAction.Mandate(DynamicOptic.Field("x", None), DynamicSchemaExpr.DefaultValue)
        val reverse = action.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.Optionalize])
      }
    ),
    suite("ChangeType and ConvertPrimitive")(
      test("ChangeType Int to Long via ConvertPrimitive") {
        val input = DynamicValue.Record(
          Chunk("n" -> DynamicValue.Primitive(PrimitiveValue.Int(99)))
        )
        val optic     = DynamicOptic.Field("n", None)
        val expr      = DynamicSchemaExpr.ConvertPrimitive("Int", "Long")
        val migration = DynamicMigration(Vector(MigrationAction.ChangeType(optic, expr)))
        val result    = migration.apply(input)
        assertTrue(result.isRight)
        val field = result.toOption.get.asInstanceOf[DynamicValue.Record].fields.find(_._1 == "n").get._2
        assertTrue(field == DynamicValue.Primitive(PrimitiveValue.Long(99L)))
      },
      test("ChangeType Int to String via ConvertPrimitive") {
        val input = DynamicValue.Record(
          Chunk("n" -> DynamicValue.Primitive(PrimitiveValue.Int(42)))
        )
        val optic     = DynamicOptic.Field("n", None)
        val expr      = DynamicSchemaExpr.ConvertPrimitive("Int", "String")
        val migration = DynamicMigration(Vector(MigrationAction.ChangeType(optic, expr)))
        val result    = migration.apply(input)
        assertTrue(result.isRight)
        val field = result.toOption.get.asInstanceOf[DynamicValue.Record].fields.find(_._1 == "n").get._2
        assertTrue(field == DynamicValue.Primitive(PrimitiveValue.String("42")))
      },
      test("ChangeType String to Int parses successfully") {
        val input = DynamicValue.Record(
          Chunk("n" -> DynamicValue.Primitive(PrimitiveValue.String("123")))
        )
        val optic     = DynamicOptic.Field("n", None)
        val expr      = DynamicSchemaExpr.ConvertPrimitive("String", "Int")
        val migration = DynamicMigration(Vector(MigrationAction.ChangeType(optic, expr)))
        val result    = migration.apply(input)
        assertTrue(result.isRight)
        val field = result.toOption.get.asInstanceOf[DynamicValue.Record].fields.find(_._1 == "n").get._2
        assertTrue(field == DynamicValue.Primitive(PrimitiveValue.Int(123)))
      },
      test("ChangeType String to Int fails on invalid input") {
        val input = DynamicValue.Record(
          Chunk("n" -> DynamicValue.Primitive(PrimitiveValue.String("notanint")))
        )
        val optic     = DynamicOptic.Field("n", None)
        val expr      = DynamicSchemaExpr.ConvertPrimitive("String", "Int")
        val migration = DynamicMigration(Vector(MigrationAction.ChangeType(optic, expr)))
        assertTrue(migration.apply(input).isLeft)
      },
      test("ConvertPrimitive identity (same type) is a no-op") {
        val input = DynamicValue.Record(
          Chunk("n" -> DynamicValue.Primitive(PrimitiveValue.Int(7)))
        )
        val optic     = DynamicOptic.Field("n", None)
        val expr      = DynamicSchemaExpr.ConvertPrimitive("Int", "Int")
        val migration = DynamicMigration(Vector(MigrationAction.ChangeType(optic, expr)))
        val result    = migration.apply(input)
        assertTrue(result.isRight)
        val field = result.toOption.get.asInstanceOf[DynamicValue.Record].fields.find(_._1 == "n").get._2
        assertTrue(field == DynamicValue.Primitive(PrimitiveValue.Int(7)))
      },
      test("ConvertPrimitive fails when no conversion is defined") {
        val input = DynamicValue.Record(
          Chunk("n" -> DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
        )
        val optic     = DynamicOptic.Field("n", None)
        val expr      = DynamicSchemaExpr.ConvertPrimitive("Boolean", "Long")
        val migration = DynamicMigration(Vector(MigrationAction.ChangeType(optic, expr)))
        assertTrue(migration.apply(input).isLeft)
      },
      test("ConvertPrimitive fails when context is not a Primitive") {
        val input = DynamicValue.Record(
          Chunk("n" -> DynamicValue.Record(Chunk.empty))
        )
        val optic     = DynamicOptic.Field("n", None)
        val expr      = DynamicSchemaExpr.ConvertPrimitive("Int", "Long")
        val migration = DynamicMigration(Vector(MigrationAction.ChangeType(optic, expr)))
        assertTrue(migration.apply(input).isLeft)
      },
      test("ChangeType fails when field not found") {
        val input     = DynamicValue.Record(Chunk("x" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val optic     = DynamicOptic.Field("n", None)
        val expr      = DynamicSchemaExpr.ConvertPrimitive("Int", "Long")
        val migration = DynamicMigration(Vector(MigrationAction.ChangeType(optic, expr)))
        assertTrue(migration.apply(input).isLeft)
      },
      test("ChangeType.reverse inverts the ConvertPrimitive direction") {
        val action  = MigrationAction.ChangeType(DynamicOptic.Field("n", None), DynamicSchemaExpr.ConvertPrimitive("Int", "Long"))
        val reverse = action.reverse.asInstanceOf[MigrationAction.ChangeType]
        val expr    = reverse.converter.asInstanceOf[DynamicSchemaExpr.ConvertPrimitive]
        assertTrue(expr.fromTypeId == "Long" && expr.toTypeId == "Int")
      },
      test("ConvertPrimitive.inverse swaps from and to") {
        val expr    = DynamicSchemaExpr.ConvertPrimitive("Int", "Long")
        val inverse = expr.inverse.asInstanceOf[DynamicSchemaExpr.ConvertPrimitive]
        assertTrue(inverse.fromTypeId == "Long" && inverse.toTypeId == "Int")
      }
    ),
    suite("TransformValue")(
      test("TransformValue applies BiTransform forward expression") {
        val input = DynamicValue.Record(
          Chunk("n" -> DynamicValue.Primitive(PrimitiveValue.Int(5)))
        )
        val optic   = DynamicOptic.Field("n", None)
        val forward = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(99)))
        val expr    = DynamicSchemaExpr.BiTransform(forward, DynamicSchemaExpr.Fail("no backward"))
        val migration = DynamicMigration(Vector(MigrationAction.TransformValue(optic, expr)))
        val result    = migration.apply(input)
        assertTrue(result.isRight)
        val field = result.toOption.get.asInstanceOf[DynamicValue.Record].fields.find(_._1 == "n").get._2
        assertTrue(field == DynamicValue.Primitive(PrimitiveValue.Int(99)))
      },
      test("TransformValue fails when field not found") {
        val input     = DynamicValue.Record(Chunk("x" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val optic     = DynamicOptic.Field("n", None)
        val expr      = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        val migration = DynamicMigration(Vector(MigrationAction.TransformValue(optic, expr)))
        assertTrue(migration.apply(input).isLeft)
      },
      test("DefaultValue sentinel fails in evalExpr") {
        val input     = DynamicValue.Record(Chunk("n" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val optic     = DynamicOptic.Field("n", None)
        val migration = DynamicMigration(Vector(MigrationAction.TransformValue(optic, DynamicSchemaExpr.DefaultValue)))
        assertTrue(migration.apply(input).isLeft)
      },
      test("BiTransform.inverse swaps forward and backward") {
        val forward  = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val backward = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2)))
        val expr     = DynamicSchemaExpr.BiTransform(forward, backward)
        val inv      = expr.inverse.asInstanceOf[DynamicSchemaExpr.BiTransform]
        assertTrue(inv.forward == backward && inv.backward == forward)
      },
      test("Literal.inverse returns Fail") {
        val expr = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        assertTrue(expr.inverse.isInstanceOf[DynamicSchemaExpr.Fail])
      },
      test("DefaultValue.inverse returns Fail") {
        assertTrue(DynamicSchemaExpr.DefaultValue.inverse.isInstanceOf[DynamicSchemaExpr.Fail])
      }
    ),
    suite("applyAction path error cases")(
      test("Field(name, Some(next)) fails when current value is not a Record") {
        val optic = DynamicOptic.Field("a", Some(DynamicOptic.Field("b", None)))
        val migration = DynamicMigration(
          Vector(
            MigrationAction.DropField(optic, DynamicSchemaExpr.DefaultValue)
          )
        )
        val notRecord = DynamicValue.Primitive(PrimitiveValue.Int(1))
        assertTrue(migration.apply(notRecord).isLeft)
      },
      test("Case(name, Some(next)) traverses matching variant and applies action") {
        val inner = DynamicValue.Record(
          Chunk("num" -> DynamicValue.Primitive(PrimitiveValue.String("4111")))
        )
        val input = DynamicValue.Variant("CreditCard", inner)
        val optic = DynamicOptic.Case("CreditCard", Some(DynamicOptic.Field("num", None)))
        val migration = DynamicMigration(
          Vector(MigrationAction.DropField(optic, DynamicSchemaExpr.DefaultValue))
        )
        val result = migration.apply(input)
        assertTrue(result.isRight)
        val out = result.toOption.get.asInstanceOf[DynamicValue.Variant].value.asInstanceOf[DynamicValue.Record]
        assertTrue(!out.fields.exists(_._1 == "num"))
      },
      test("Case(name, Some(next)) passes through non-matching variant unchanged") {
        val wire  = DynamicValue.Variant("Wire", DynamicValue.Record(Chunk("acc" -> DynamicValue.Primitive(PrimitiveValue.String("x")))))
        val optic = DynamicOptic.Case("CreditCard", Some(DynamicOptic.Field("num", None)))
        val migration = DynamicMigration(
          Vector(MigrationAction.DropField(optic, DynamicSchemaExpr.DefaultValue))
        )
        val result = migration.apply(wire)
        assertTrue(result.isRight && result.toOption.get == wire)
      },
      test("Case(name, Some(next)) fails when current value is not a Variant") {
        val notVariant = DynamicValue.Primitive(PrimitiveValue.Int(1))
        val optic      = DynamicOptic.Case("CreditCard", Some(DynamicOptic.Field("num", None)))
        val migration = DynamicMigration(
          Vector(MigrationAction.DropField(optic, DynamicSchemaExpr.DefaultValue))
        )
        assertTrue(migration.apply(notVariant).isLeft)
      },
      test("Case(name, None) fails when current value is not a Variant") {
        val notVariant = DynamicValue.Primitive(PrimitiveValue.Int(1))
        val optic      = DynamicOptic.Case("CreditCard", None)
        val migration = DynamicMigration(
          Vector(
            MigrationAction.TransformCase(optic, Vector(MigrationAction.DropField(DynamicOptic.Field("x", None), DynamicSchemaExpr.DefaultValue)))
          )
        )
        assertTrue(migration.apply(notVariant).isLeft)
      },
      test("applyTerminalVariantAction returns Left for unsupported action on Variant") {
        val variant = DynamicValue.Variant("A", DynamicValue.Record(Chunk.empty))
        val optic   = DynamicOptic.Case("A", None)
        val migration = DynamicMigration(
          Vector(MigrationAction.RenameCase(optic, "A", "B"))
        )
        assertTrue(migration.apply(variant).isLeft)
      },
      test("unsupported optic type (Element at root) returns Left") {
        val input     = DynamicValue.Sequence(Chunk(DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val optic     = DynamicOptic.Element(None)
        val migration = DynamicMigration(Vector(MigrationAction.DropField(optic, DynamicSchemaExpr.DefaultValue)))
        assertTrue(migration.apply(input).isLeft)
      }
    ),
    suite("DynamicOptic helpers")(
      test("append builds a nested path") {
        val a = DynamicOptic.Field("a", None)
        val b = DynamicOptic.Field("b", None)
        assertTrue(a.append(b) == DynamicOptic.Field("a", Some(b)))
      },
      test("append on Case delegates recursively") {
        val inner = DynamicOptic.Case("X", None)
        val leaf  = DynamicOptic.Field("f", None)
        assertTrue(inner.append(leaf) == DynamicOptic.Case("X", Some(leaf)))
      },
      test("append on Element delegates recursively") {
        val e    = DynamicOptic.Element(None)
        val leaf = DynamicOptic.Field("f", None)
        assertTrue(e.append(leaf) == DynamicOptic.Element(Some(leaf)))
      },
      test("terminalName throws for non-Field optic") {
        assertTrue(scala.util.Try(DynamicOptic.terminalName(DynamicOptic.Case("X", None))).isFailure)
      },
      test("replaceTerminal is a no-op for non-Field optic") {
        val c = DynamicOptic.Case("X", None)
        assertTrue(DynamicOptic.replaceTerminal(c, "new") == c)
      }
    ),
    suite("Migration type-safe wrapper")(
      test("Migration.++ composes two migrations") {
        import zio.blocks.schema.Schema
        case class A(x: String)
        case class B(x: String, y: Int)
        case class C(x: String, y: Int, z: Boolean)
        implicit val sa: Schema[A] = Schema.derived
        implicit val sb: Schema[B] = Schema.derived
        implicit val sc: Schema[C] = Schema.derived
        val m1 = Migration(
          DynamicMigration(Vector(MigrationAction.AddField(DynamicOptic.Field("y", None), DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))))),
          sa, sb
        )
        val m2 = Migration(
          DynamicMigration(Vector(MigrationAction.AddField(DynamicOptic.Field("z", None), DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(false)))))),
          sb, sc
        )
        val composed = m1 ++ m2
        assertTrue(composed.dynamicMigration.actions.length == 2)
      },
      test("Migration.andThen is the same as ++") {
        import zio.blocks.schema.Schema
        case class P(a: String)
        case class Q(a: String, b: Int)
        implicit val sp: Schema[P] = Schema.derived
        implicit val sq: Schema[Q] = Schema.derived
        val m1 = Migration(
          DynamicMigration(Vector(MigrationAction.AddField(DynamicOptic.Field("b", None), DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))))),
          sp, sq
        )
        val m2 = Migration(DynamicMigration(Vector.empty), sq, sq)
        assertTrue(m1.andThen(m2).dynamicMigration.actions == (m1 ++ m2).dynamicMigration.actions)
      },
      test("Migration.reverse swaps source and target schemas") {
        import zio.blocks.schema.Schema
        case class V1(n: String)
        case class V2(n: String)
        implicit val sv1: Schema[V1] = Schema.derived
        implicit val sv2: Schema[V2] = Schema.derived
        val m = Migration(DynamicMigration(Vector.empty), sv1, sv2)
        val r = m.reverse
        assertTrue(r.sourceSchema == sv2 && r.targetSchema == sv1)
      }
    )
  )
}
