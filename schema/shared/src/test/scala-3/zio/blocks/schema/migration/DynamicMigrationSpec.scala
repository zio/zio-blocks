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
import zio.blocks.schema.{DynamicOptic => _, _}
import zio.test._

/**
 * Verifies the pure interpreter: Macro S => A extraction → Builder →
 * DynamicMigration → apply(DynamicValue).
 */
object DynamicMigrationSpec extends SchemaBaseSpec {

  case class PersonV1(name: String, age: Int)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived
  }

  case class PersonV2(name: String, age: Int, email: String)
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("DynamicMigrationSpec")(
    suite("interpreter")(
      test("PersonV1 to PersonV2: addField adds email with default") {
        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .addField(
            (p: PersonV2) => p.email,
            DynamicSchemaExpr.Literal(
              DynamicValue.Primitive(PrimitiveValue.String(""))
            )
          )
          .buildPartial

        val v1        = PersonV1("Alice", 30)
        val dynamicIn = PersonV1.schema.toDynamicValue(v1)
        val result    = migration.dynamicMigration.apply(dynamicIn)

        assertTrue(result.isRight)
        val dynamicOut = result.toOption.get
        val fields     = dynamicOut.asInstanceOf[DynamicValue.Record].fields
        val names      = fields.map(_._1)
        assertTrue(names.toSeq == Seq("name", "age", "email"))
        val emailVal = fields.find(_._1 == "email").map(_._2)
        assertTrue(
          emailVal.contains(
            DynamicValue.Primitive(PrimitiveValue.String(""))
          )
        )
      },
      test("PersonV1 to PersonV2: full round-trip via typed Migration.apply") {
        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .addField(
            (p: PersonV2) => p.email,
            DynamicSchemaExpr.Literal(
              DynamicValue.Primitive(PrimitiveValue.String("noreply@example.com"))
            )
          )
          .buildPartial

        val v1    = PersonV1("Bob", 25)
        val typed = migration.apply(v1)
        assertTrue(typed.isRight)
        val v2 = typed.toOption.get
        assertTrue(v2.name == "Bob" && v2.age == 25 && v2.email == "noreply@example.com")
      },
      test("renameField renames name to fullName") {
        case class Old(name: String, age: Int)
        object Old { implicit val schema: Schema[Old] = Schema.derived }
        case class New(fullName: String, age: Int)
        object New { implicit val schema: Schema[New] = Schema.derived }

        val migration = Migration
          .newBuilder[Old, New]
          .renameField((o: Old) => o.name, (n: New) => n.fullName)
          .buildPartial

        val dynamicIn = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Carol")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(28))
          )
        )
        val result = migration.dynamicMigration.apply(dynamicIn)
        assertTrue(result.isRight)
        val out = result.toOption.get.asInstanceOf[DynamicValue.Record]
        assertTrue(out.fields.exists(_._1 == "fullName"))
        assertTrue(!out.fields.exists(_._1 == "name"))
        val fullNameVal = out.fields.find(_._1 == "fullName").map(_._2)
        assertTrue(
          fullNameVal.contains(
            DynamicValue.Primitive(PrimitiveValue.String("Carol"))
          )
        )
      },
      test("path failure returns Left with MigrationError containing path information") {
        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .addField(
            (p: PersonV2) => p.email,
            DynamicSchemaExpr.Literal(
              DynamicValue.Primitive(PrimitiveValue.String(""))
            )
          )
          .buildPartial

        val notARecord = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val result     = migration.dynamicMigration.apply(notARecord)
        assertTrue(result.isLeft)
        val err = result.left.toOption.get
        assertTrue(err.message.contains("Expected Record") || err.toString.contains("Expected Record"))
      }
    ),
    suite("enum operations")(
      test("RenameCase renames a variant case") {
        val creditCard  = DynamicValue.Variant("CreditCard", DynamicValue.Primitive(PrimitiveValue.String("4111")))
        val input       = DynamicValue.Record(Chunk("payment" -> creditCard))
        val renameOptic = DynamicOptic.Field("payment", None)
        val migration   = DynamicMigration(Vector(MigrationAction.RenameCase(renameOptic, "CreditCard", "Card")))

        val result = migration.apply(input)
        assertTrue(result.isRight)
        val out          = result.toOption.get.asInstanceOf[DynamicValue.Record]
        val paymentField = out.fields.find(_._1 == "payment").map(_._2)
        assertTrue(paymentField.exists {
          case DynamicValue.Variant("Card", _) => true
          case _                               => false
        })
      },
      test("RenameCase is a no-op for a different case") {
        val wire      = DynamicValue.Variant("WireTransfer", DynamicValue.Primitive(PrimitiveValue.String("routing123")))
        val input     = DynamicValue.Record(Chunk("payment" -> wire))
        val optic     = DynamicOptic.Field("payment", None)
        val migration = DynamicMigration(Vector(MigrationAction.RenameCase(optic, "CreditCard", "Card")))

        val result = migration.apply(input)
        assertTrue(result.isRight)
        val out          = result.toOption.get.asInstanceOf[DynamicValue.Record]
        val paymentField = out.fields.find(_._1 == "payment").map(_._2)
        assertTrue(paymentField.exists {
          case DynamicValue.Variant("WireTransfer", _) => true
          case _                                       => false
        })
      },
      test("RenameCase.reverse renames back") {
        val action  = MigrationAction.RenameCase(DynamicOptic.Field("payment", None), "CreditCard", "Card")
        val reverse = action.reverse.asInstanceOf[MigrationAction.RenameCase]
        assertTrue(reverse.from == "Card" && reverse.to == "CreditCard")
      },
      test("TransformCase applies sub-actions to the matching case's inner value") {
        val innerRecord = DynamicValue.Record(
          Chunk(
            "number" -> DynamicValue.Primitive(PrimitiveValue.String("4111")),
            "exp"    -> DynamicValue.Primitive(PrimitiveValue.String("12/25"))
          )
        )
        val input = DynamicValue.Record(
          Chunk("payment" -> DynamicValue.Variant("CreditCard", innerRecord))
        )

        val dropExpOptic   = DynamicOptic.Field("exp", None)
        val subAction      = MigrationAction.DropField(dropExpOptic, DynamicSchemaExpr.DefaultValue)
        val transformOptic = DynamicOptic.Field(
          "payment",
          Some(DynamicOptic.Case("CreditCard", None))
        )
        val migration = DynamicMigration(Vector(MigrationAction.TransformCase(transformOptic, Vector(subAction))))

        val result = migration.apply(input)
        assertTrue(result.isRight)
        val out          = result.toOption.get.asInstanceOf[DynamicValue.Record]
        val paymentField = out.fields.find(_._1 == "payment").map(_._2)
        assertTrue(paymentField.exists {
          case DynamicValue.Variant("CreditCard", inner: DynamicValue.Record) =>
            !inner.fields.exists(_._1 == "exp") && inner.fields.exists(_._1 == "number")
          case _ => false
        })
      },
      test("TransformCase is a no-op for a different case") {
        val wire = DynamicValue.Variant(
          "WireTransfer",
          DynamicValue.Record(Chunk("account" -> DynamicValue.Primitive(PrimitiveValue.String("123"))))
        )
        val input          = DynamicValue.Record(Chunk("payment" -> wire))
        val transformOptic = DynamicOptic.Field(
          "payment",
          Some(DynamicOptic.Case("CreditCard", None))
        )
        val migration = DynamicMigration(
          Vector(
            MigrationAction.TransformCase(
              transformOptic,
              Vector(MigrationAction.DropField(DynamicOptic.Field("account", None), DynamicSchemaExpr.DefaultValue))
            )
          )
        )

        val result = migration.apply(input)
        assertTrue(result.isRight)
        val out          = result.toOption.get.asInstanceOf[DynamicValue.Record]
        val paymentField = out.fields.find(_._1 == "payment").map(_._2)
        assertTrue(paymentField.exists {
          case DynamicValue.Variant("WireTransfer", inner: DynamicValue.Record) =>
            inner.fields.exists(_._1 == "account")
          case _ => false
        })
      },
      test("MigrationError.renderOptic produces the correct path string") {
        val optic =
          DynamicOptic.Field("payment", Some(DynamicOptic.Case("CreditCard", Some(DynamicOptic.Field("number", None)))))
        assertTrue(MigrationError.renderOptic(optic) == ".payment.when[CreditCard].number")
      },
      test("error on wrong type includes path information") {
        val optic      = DynamicOptic.Field("payment", None)
        val action     = MigrationAction.RenameCase(optic, "CreditCard", "Card")
        val notVariant = DynamicValue.Record(Chunk("payment" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val migration  = DynamicMigration(Vector(action))
        val result     = migration.apply(notVariant)
        assertTrue(result.isLeft)
        val err = result.left.toOption.get
        assertTrue(err.toString.contains(".payment"))
      }
    )
  )
}
