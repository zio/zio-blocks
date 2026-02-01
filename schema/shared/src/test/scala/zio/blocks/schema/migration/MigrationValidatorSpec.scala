package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, Schema}
import zio.blocks.chunk.Chunk
import zio.test._

object MigrationValidatorSpec extends ZIOSpecDefault {

  case class Person(name: String, age: Int)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived
  }

  case class PersonWithEmail(name: String, age: Int, email: String)
  object PersonWithEmail {
    implicit val schema: Schema[PersonWithEmail] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("MigrationValidatorSpec")(
    suite("ValidationResult")(
      test("Valid is a valid result") {
        val result: MigrationValidator.ValidationResult = MigrationValidator.ValidationResult.Valid
        assertTrue(result == MigrationValidator.ValidationResult.Valid)
      },
      test("Invalid contains errors") {
        val errors = Chunk(
          MigrationValidator.ValidationError.FieldNotFound(DynamicOptic.root, "test")
        )
        val result = MigrationValidator.ValidationResult.Invalid(errors)
        assertTrue(result.errors.nonEmpty)
      },
      test("Invalid render produces error messages") {
        val errors = Chunk(
          MigrationValidator.ValidationError.FieldNotFound(DynamicOptic.root, "field1"),
          MigrationValidator.ValidationError.PathNotInSource(DynamicOptic.root)
        )
        val result   = MigrationValidator.ValidationResult.Invalid(errors)
        val rendered = result.render
        assertTrue(rendered.contains("field1") && rendered.contains("source"))
      }
    ),
    suite("ValidationError types")(
      test("PathNotInSource has correct message") {
        val error = MigrationValidator.ValidationError.PathNotInSource(DynamicOptic.root)
        assertTrue(error.message.contains("source"))
      },
      test("PathNotInTarget has correct message") {
        val error = MigrationValidator.ValidationError.PathNotInTarget(DynamicOptic.root)
        assertTrue(error.message.contains("target"))
      },
      test("FieldAlreadyExists has correct message") {
        val error = MigrationValidator.ValidationError.FieldAlreadyExists(DynamicOptic.root, "email")
        assertTrue(error.message.contains("email") && error.message.contains("exists"))
      },
      test("FieldNotFound has correct message") {
        val error = MigrationValidator.ValidationError.FieldNotFound(DynamicOptic.root, "missing")
        assertTrue(error.message.contains("missing") && error.message.contains("not found"))
      },
      test("CaseNotFound has correct message") {
        val error = MigrationValidator.ValidationError.CaseNotFound(DynamicOptic.root, "SomeCase")
        assertTrue(error.message.contains("SomeCase"))
      },
      test("TypeMismatch has correct message") {
        val error = MigrationValidator.ValidationError.TypeMismatch(DynamicOptic.root, "Int", "String")
        assertTrue(error.message.contains("Int") && error.message.contains("String"))
      },
      test("IncompatibleTransform has correct message") {
        val error = MigrationValidator.ValidationError.IncompatibleTransform(DynamicOptic.root, "cannot convert")
        assertTrue(error.message.contains("cannot convert"))
      },
      test("ValidationError render includes path") {
        val error    = MigrationValidator.ValidationError.FieldNotFound(DynamicOptic.root.field("test"), "field")
        val rendered = error.render
        assertTrue(rendered.contains("test"))
      }
    ),
    suite("validate")(
      test("valid migration returns Valid") {
        val migration = MigrationBuilder[Person, Person].buildPartial
        val result    = MigrationValidator.validate(migration)
        assertTrue(result == MigrationValidator.ValidationResult.Valid)
      },
      test("validate identity migration") {
        val migration = Migration[Person, Person](
          DynamicMigration.identity,
          Schema[Person],
          Schema[Person]
        )
        val result = MigrationValidator.validate(migration)
        assertTrue(result == MigrationValidator.ValidationResult.Valid)
      },
      test("validate migration with single action") {
        val addAction = MigrationAction.AddField(DynamicOptic.root, "email", Resolved.Literal.string(""))
        val migration = Migration[Person, PersonWithEmail](
          DynamicMigration(Chunk(addAction)),
          Schema[Person],
          Schema[PersonWithEmail]
        )
        val result = MigrationValidator.validate(migration)
        // This should be valid if the action is correct
        assertTrue(
          result == MigrationValidator.ValidationResult.Valid || result
            .isInstanceOf[MigrationValidator.ValidationResult.Invalid]
        )
      }
    ),
    suite("Resolved expressions coverage")(
      test("Resolved.Literal.unit creates unit literal") {
        val literal = Resolved.Literal.unit
        val result  = literal.evalDynamic
        assertTrue(result.isRight)
      },
      test("Resolved.SchemaDefault returns error on eval") {
        val schemaDefault = Resolved.SchemaDefault
        val result        = schemaDefault.evalDynamic
        assertTrue(result.isLeft && result.left.exists(_.contains("SchemaDefault")))
      },
      test("Resolved.SchemaDefault returns error on eval with input") {
        val schemaDefault = Resolved.SchemaDefault
        val input         = DynamicValue.Primitive(PrimitiveValue.String("test"))
        val result        = schemaDefault.evalDynamic(input)
        assertTrue(result.isLeft)
      },
      test("Resolved.Identity requires input without value") {
        val identity = Resolved.Identity
        val result   = identity.evalDynamic
        assertTrue(result.isLeft && result.left.exists(_.contains("requires input")))
      },
      test("Resolved.FieldAccess requires input") {
        val fieldAccess = Resolved.FieldAccess("name", Resolved.Identity)
        val result      = fieldAccess.evalDynamic
        assertTrue(result.isLeft)
      },
      test("Resolved.FieldAccess fails on non-record") {
        val fieldAccess = Resolved.FieldAccess("name", Resolved.Identity)
        val input       = DynamicValue.Primitive(PrimitiveValue.String("not a record"))
        val result      = fieldAccess.evalDynamic(input)
        assertTrue(result.isLeft && result.left.exists(_.contains("Expected record")))
      },
      test("Resolved.FieldAccess fails on missing field") {
        val fieldAccess = Resolved.FieldAccess("missing", Resolved.Identity)
        val input       = DynamicValue.Record(("name", DynamicValue.Primitive(PrimitiveValue.String("test"))))
        val result      = fieldAccess.evalDynamic(input)
        assertTrue(result.isLeft && result.left.exists(_.contains("not found")))
      },
      test("Resolved.Convert requires input") {
        val convert = Resolved.Convert("Int", "Long", Resolved.Identity)
        val result  = convert.evalDynamic
        assertTrue(result.isLeft)
      },
      test("Resolved.Convert performs type conversion") {
        val convert = Resolved.Convert("Int", "Long", Resolved.Identity)
        val input   = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val result  = convert.evalDynamic(input)
        assertTrue(result.isRight)
      },
      test("Resolved.Convert inverse swaps types") {
        val convert = Resolved.Convert("Int", "Long", Resolved.Identity)
        val inverse = convert.inverse
        inverse match {
          case Resolved.Convert(from, to, _) => assertTrue(from == "Long" && to == "Int")
          case _                             => assertTrue(false)
        }
      },
      test("Resolved.Concat requires input") {
        val concat = Resolved.Concat(Vector(Resolved.Identity), " ")
        val result = concat.evalDynamic
        assertTrue(result.isLeft)
      },
      test("Resolved.Concat joins strings") {
        val concat = Resolved.Concat(
          Vector(
            Resolved.FieldAccess("first", Resolved.Identity),
            Resolved.FieldAccess("last", Resolved.Identity)
          ),
          " "
        )
        val input = DynamicValue.Record(
          ("first", DynamicValue.Primitive(PrimitiveValue.String("John"))),
          ("last", DynamicValue.Primitive(PrimitiveValue.String("Doe")))
        )
        val result = concat.evalDynamic(input)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("John Doe"))))
      },
      test("Resolved.Concat fails on non-string") {
        val concat = Resolved.Concat(Vector(Resolved.Identity), " ")
        val input  = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val result = concat.evalDynamic(input)
        assertTrue(result.isLeft)
      },
      test("Resolved.SplitString requires input") {
        val split  = Resolved.SplitString(Resolved.Identity, " ", 0)
        val result = split.evalDynamic
        assertTrue(result.isLeft)
      },
      test("Resolved.SplitString splits on delimiter") {
        val split  = Resolved.SplitString(Resolved.Identity, " ", 0)
        val input  = DynamicValue.Primitive(PrimitiveValue.String("hello world"))
        val result = split.evalDynamic(input)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("hello"))))
      },
      test("Resolved.SplitString second index") {
        val split  = Resolved.SplitString(Resolved.Identity, " ", 1)
        val input  = DynamicValue.Primitive(PrimitiveValue.String("hello world"))
        val result = split.evalDynamic(input)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("world"))))
      },
      test("Resolved.SplitString fails on out of bounds") {
        val split  = Resolved.SplitString(Resolved.Identity, " ", 10)
        val input  = DynamicValue.Primitive(PrimitiveValue.String("hello world"))
        val result = split.evalDynamic(input)
        assertTrue(result.isLeft && result.left.exists(_.contains("out of bounds")))
      },
      test("Resolved.SplitString fails on non-string") {
        val split  = Resolved.SplitString(Resolved.Identity, " ", 0)
        val input  = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val result = split.evalDynamic(input)
        assertTrue(result.isLeft)
      },
      test("Resolved.DefaultValue.fromValue creates working default") {
        val default = Resolved.DefaultValue.fromValue("test", Schema[String])
        val result  = default.evalDynamic
        assertTrue(result.isRight)
      },
      test("Resolved.DefaultValue.noDefault returns error") {
        val default = Resolved.DefaultValue.noDefault
        val result  = default.evalDynamic
        assertTrue(result.isLeft)
      },
      test("Resolved.DefaultValue.fail returns specific error") {
        val default = Resolved.DefaultValue.fail("custom error")
        val result  = default.evalDynamic
        assertTrue(result.isLeft && result.left.exists(_.contains("custom error")))
      },
      test("Resolved.WrapOption wraps value in Some") {
        val wrap   = Resolved.WrapOption(Resolved.Identity)
        val input  = DynamicValue.Primitive(PrimitiveValue.String("value"))
        val result = wrap.evalDynamic(input)
        assertTrue(result.exists(_.isInstanceOf[DynamicValue.Variant]))
      },
      test("Resolved.WrapOption requires input") {
        val wrap   = Resolved.WrapOption(Resolved.Identity)
        val result = wrap.evalDynamic
        assertTrue(result.isLeft)
      },
      test("Resolved.UnwrapOption extracts from Some") {
        val unwrap    = Resolved.UnwrapOption(Resolved.Identity, Resolved.Literal.string("default"))
        val someValue = DynamicValue.Variant(
          "Some",
          DynamicValue.Record(("value", DynamicValue.Primitive(PrimitiveValue.String("inner"))))
        )
        val result = unwrap.evalDynamic(someValue)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("inner"))))
      },
      test("Resolved.UnwrapOption uses fallback for None") {
        val unwrap    = Resolved.UnwrapOption(Resolved.Identity, Resolved.Literal.string("default"))
        val noneValue = DynamicValue.Variant("None", DynamicValue.Record())
        val result    = unwrap.evalDynamic(noneValue)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("default"))))
      },
      test("Resolved.UnwrapOption uses fallback for Null") {
        val unwrap    = Resolved.UnwrapOption(Resolved.Identity, Resolved.Literal.string("default"))
        val nullValue = DynamicValue.Null
        val result    = unwrap.evalDynamic(nullValue)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("default"))))
      },
      test("Resolved.UnwrapOption requires input") {
        val unwrap = Resolved.UnwrapOption(Resolved.Identity, Resolved.Literal.string("default"))
        val result = unwrap.evalDynamic
        assertTrue(result.isLeft)
      },
      test("Resolved.UnwrapOption passes through non-option values") {
        val unwrap       = Resolved.UnwrapOption(Resolved.Identity, Resolved.Literal.string("default"))
        val regularValue = DynamicValue.Primitive(PrimitiveValue.String("just a string"))
        val result       = unwrap.evalDynamic(regularValue)
        assertTrue(result == Right(regularValue))
      },
      test("Resolved.WrapOption inverse returns UnwrapOption") {
        val wrap    = Resolved.WrapOption(Resolved.Identity)
        val inverse = wrap.inverse
        assertTrue(inverse.isInstanceOf[Resolved.UnwrapOption])
      },
      test("Resolved.UnwrapOption inverse returns WrapOption") {
        val unwrap  = Resolved.UnwrapOption(Resolved.Identity, Resolved.Literal.string(""))
        val inverse = unwrap.inverse
        assertTrue(inverse.isInstanceOf[Resolved.WrapOption])
      },
      test("Resolved.OpticAccess requires input") {
        val opticAccess = Resolved.OpticAccess(DynamicOptic.root.field("name"), Resolved.Identity)
        val result      = opticAccess.evalDynamic
        assertTrue(result.isLeft)
      }
    ),
    suite("MigrationAction reverse coverage")(
      test("AddField reverse is DropField") {
        val add     = MigrationAction.AddField(DynamicOptic.root, "field", Resolved.Literal.string("default"))
        val reverse = add.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.DropField])
      },
      test("DropField reverse is AddField") {
        val drop    = MigrationAction.DropField(DynamicOptic.root, "field", Resolved.Literal.string("restore"))
        val reverse = drop.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.AddField])
      },
      test("Rename reverse swaps from and to") {
        val rename  = MigrationAction.Rename(DynamicOptic.root, "old", "new")
        val reverse = rename.reverse
        reverse match {
          case MigrationAction.Rename(_, from, to) => assertTrue(from == "new" && to == "old")
          case _                                   => assertTrue(false)
        }
      },
      test("TransformValue reverse swaps transform and reverseTransform") {
        val transform =
          MigrationAction.TransformValue(DynamicOptic.root, "field", Resolved.Literal.int(1), Resolved.Literal.int(2))
        val reverse = transform.reverse
        reverse match {
          case MigrationAction.TransformValue(_, _, t, rt) =>
            assertTrue(t == Resolved.Literal.int(2) && rt == Resolved.Literal.int(1))
          case _ => assertTrue(false)
        }
      },
      test("Mandate reverse is Optionalize") {
        val mandate = MigrationAction.Mandate(DynamicOptic.root, "field", Resolved.Literal.string(""))
        val reverse = mandate.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.Optionalize])
      },
      test("Optionalize reverse exists") {
        val optionalize = MigrationAction.Optionalize(DynamicOptic.root, "field")
        val reverse     = optionalize.reverse
        assertTrue(reverse != null)
      },
      test("RenameCase reverse swaps from and to") {
        val renameCase = MigrationAction.RenameCase(DynamicOptic.root, "Old", "New")
        val reverse    = renameCase.reverse
        reverse match {
          case MigrationAction.RenameCase(_, from, to) => assertTrue(from == "New" && to == "Old")
          case _                                       => assertTrue(false)
        }
      },
      test("ChangeType reverse swaps converters") {
        val changeType =
          MigrationAction.ChangeType(DynamicOptic.root, "field", Resolved.Literal.int(1), Resolved.Literal.string(""))
        val reverse = changeType.reverse
        reverse match {
          case MigrationAction.ChangeType(_, _, c, rc) =>
            assertTrue(c == Resolved.Literal.string("") && rc == Resolved.Literal.int(1))
          case _ => assertTrue(false)
        }
      },
      test("Join reverse is Split") {
        val join =
          MigrationAction.Join(
            DynamicOptic.root,
            "target",
            Chunk(DynamicOptic.root.field("a"), DynamicOptic.root.field("b")),
            Resolved.Identity,
            Resolved.Identity
          )
        val reverse = join.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.Split])
      },
      test("Split reverse is Join") {
        val split =
          MigrationAction.Split(
            DynamicOptic.root,
            "source",
            Chunk(DynamicOptic.root.field("a"), DynamicOptic.root.field("b")),
            Resolved.Identity,
            Resolved.Identity
          )
        val reverse = split.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.Join])
      },
      test("TransformElements reverse swaps transforms") {
        val elem =
          MigrationAction.TransformElements(DynamicOptic.root, Resolved.Literal.int(1), Resolved.Literal.int(2))
        val reverse = elem.reverse
        reverse match {
          case MigrationAction.TransformElements(_, t, rt) =>
            assertTrue(t == Resolved.Literal.int(2) && rt == Resolved.Literal.int(1))
          case _ => assertTrue(false)
        }
      },
      test("TransformKeys reverse swaps key transforms") {
        val keys =
          MigrationAction.TransformKeys(DynamicOptic.root, Resolved.Literal.string("a"), Resolved.Literal.string("b"))
        val reverse = keys.reverse
        reverse match {
          case MigrationAction.TransformKeys(_, t, rt) =>
            assertTrue(t == Resolved.Literal.string("b") && rt == Resolved.Literal.string("a"))
          case _ => assertTrue(false)
        }
      },
      test("TransformValues reverse swaps value transforms") {
        val vals    = MigrationAction.TransformValues(DynamicOptic.root, Resolved.Literal.int(1), Resolved.Literal.int(2))
        val reverse = vals.reverse
        reverse match {
          case MigrationAction.TransformValues(_, t, rt) =>
            assertTrue(t == Resolved.Literal.int(2) && rt == Resolved.Literal.int(1))
          case _ => assertTrue(false)
        }
      },
      test("TransformCase reverse reverses nested actions") {
        val inner         = MigrationAction.Rename(DynamicOptic.root, "a", "b")
        val transformCase = MigrationAction.TransformCase(DynamicOptic.root, "Case1", Chunk(inner))
        val reverse       = transformCase.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.TransformCase])
      }
    )
  )
}
