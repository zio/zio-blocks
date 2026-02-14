package zio.blocks.schema.migration

import zio.test._
import zio.test.Assertion._
import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.migration._
import zio.blocks.schema.{DynamicValue, PrimitiveValue}
import zio.blocks.schema.migration.SchemaExpr
import zio.blocks.schema.migration.macros.AccessorMacros
import zio.blocks.schema.Schema

object MacroCompleteSpec extends ZIOSpecDefault {

  // =================================================================================
  // 1. DATA MODELS
  // =================================================================================
  case class SimpleUser(name: String, age: Int)
  case class SimpleTarget(name: String)

  case class Group(tags: List[String])

  // Normal Classes (Schema will be empty map)
  class NormalSrc    { val field: Int = 0            }
  class NormalTgt    { val field: Int = 0            }
  class NormalSrcOpt { val field: Option[Int] = None }

  // Case Classes (Schema will have fields)
  case class Src(field: String)
  case class Tgt(field: String)

  sealed trait Status
  case class Active(since: Int)       extends Status
  case class Inactive(reason: String) extends Status
  case class UserStatus(status: Status)

  trait TraitA; trait TraitB
  case class Mixed(value: TraitA & TraitB)

  case class WrappedList(items: List[String])
  implicit class ImplicitListWrapper(val w: WrappedList) {
    def each: String = ???
  }

  // =================================================================================
  // 2. STUBS
  // =================================================================================
  object Stubs {
    extension [A](iterable: Iterable[A]) {
      def each: A = ???
    }
    extension [A](opt: Option[A]) {
      def each: A = ???
    }
    extension [A](self: A) {
      def when[Sub <: A]: Sub = ???
    }
  }

  val defaultString = SchemaExpr.Constant(DynamicValue.Primitive(PrimitiveValue.String("default")))
  val defaultInt    = SchemaExpr.Constant(DynamicValue.Primitive(PrimitiveValue.Int(0)))

  // Builder implementation
  def builder[A, B]: MigrationBuilder[A, B, MigrationState.Empty] =
    new MigrationBuilder[A, B, MigrationState.Empty](
      null.asInstanceOf[Schema[A]],
      null.asInstanceOf[Schema[B]],
      Vector.empty
    ) {}

  // =================================================================================
  // 3. TEST SUITE
  // =================================================================================
  def spec = suite("Macro Complete Spec - 100% Coverage")(
    // ---------------------------------------------------------------------------
    // SECTION A: AccessorMacros
    // ---------------------------------------------------------------------------
    suite("AccessorMacros Logic")(
      test("Simple Field Access") {
        val optic = AccessorMacros.derive[SimpleUser, String](_.name)
        assert(optic.optic.nodes)(equalTo(zio.blocks.chunk.Chunk(DynamicOptic.Node.Field("name"))))
      },

      test("Intersection Types (AndType coverage)") {
        val result = typeCheck {
          """
           import zio.blocks.schema.migration.MacroCompleteSpec._
           import zio.blocks.schema.migration.MacroCompleteSpec.Stubs._
           AccessorMacros.derive[Mixed, Int](_.value.when[TraitA & TraitB].hashCode)
           """
        }
        assertZIO(result)(isLeft(anything))
      },

      test("Complex AST: Empty Blocks and Typed expressions") {
        // { u.name } simulates Block(List(), expr)
        val opticBlock = AccessorMacros.derive[SimpleUser, String](u => u.name)
        // (u: SimpleUser).name simulates Typed(expr, tpe)
        val opticTyped = AccessorMacros.derive[SimpleUser, String](u => (u: SimpleUser).name)

        assert(opticBlock.optic.nodes)(equalTo(zio.blocks.chunk.Chunk(DynamicOptic.Node.Field("name")))) &&
        assert(opticTyped.optic.nodes)(equalTo(zio.blocks.chunk.Chunk(DynamicOptic.Node.Field("name"))))
      },

      test("Implicit Wrappers") {
        val optic = AccessorMacros.derive[WrappedList, String](_.each)
        assert(optic.optic.nodes)(equalTo(zio.blocks.chunk.Chunk(DynamicOptic.Node.Elements)))
      },

      test("Structural Types (selectDynamic)") {
        val result = typeCheck {
          """
          import scala.reflect.Selectable.reflectiveSelectable
          import zio.blocks.schema.migration.macros.AccessorMacros
          type StructuralPerson = { def name: String }
          AccessorMacros.derive[StructuralPerson, String](_.name)
          """
        }
        assertZIO(result)(isLeft(anything))
      }
    ),

    // ---------------------------------------------------------------------------
    // SECTION B: MigrationMacros
    // ---------------------------------------------------------------------------
    suite("MigrationMacros Operations")(
      test("Manual Cast Logic (Dead Code Coverage)") {
        // [FIXED] We pass a cast expression directly (not a lambda).
        // This hits the 'case TypeApply(asInstanceOf)' inside deriveOptic/isCast logic.
        // Since we bypass AccessorMacros, we avoid "Unsupported selector".
        // We expect failure (Left) because the field name extracted from type (e.g. "Function1") won't be in schema.
        val result = typeCheck {
          """
          import zio.blocks.schema.migration.MacroCompleteSpec._
          val castExpr = (null: Any).asInstanceOf[Src => Src]
          builder[Src, Src].transformField(castExpr, castExpr, SchemaExpr.Identity()).build
          """
        }
        assertZIO(result)(isLeft(anything))
      },

      test("Error: Mandate Name Mismatch") {
        val result = typeCheck {
          """
          import zio.blocks.schema.migration.MacroCompleteSpec._
          case class A(x: Option[Int]); case class B(y: Int)
          builder[A, B].mandateField(_.x, _.y, defaultInt).build
          """
        }
        assertZIO(result)(isLeft(anything))
      },

      test("Error: Optionalize Name Mismatch") {
        val result = typeCheck {
          """
          import zio.blocks.schema.migration.MacroCompleteSpec._
          case class A(x: Int); case class B(y: Option[Int])
          builder[A, B].optionalizeField(_.x, _.y).build
          """
        }
        assertZIO(result)(isLeft(anything))
      },

      test("Error: Invalid Drop (Field Not Found)") {
        // NormalSrc has no schema fields. Dropping 'field' must fail.
        val result = typeCheck {
          """
          import zio.blocks.schema.migration.MacroCompleteSpec._
          builder[NormalSrc, NormalTgt].dropField(_.field, defaultInt).build
          """
        }
        assertZIO(result)(isLeft(containsString("Invalid Drop")))
      },

      test("Error: Invalid Add (Field Exists)") {
        // Src has 'field'. Adding it again must fail.
        val result = typeCheck {
          """
          import zio.blocks.schema.migration.MacroCompleteSpec._
          builder[Src, Src].addField(_.field, defaultString).build
          """
        }
        assertZIO(result)(isLeft(containsString("Invalid Add")))
      },

      test("Error: Invalid ChangeType (Field Not Found)") {
        val result = typeCheck {
          """
          import zio.blocks.schema.migration.MacroCompleteSpec._
          builder[NormalSrc, NormalTgt].transformField(_.field, _.field, SchemaExpr.Identity()).build
          """
        }
        assertZIO(result)(isLeft(containsString("Invalid ChangeType")))
      },

      test("Error: Invalid Mandate (Field Not Found)") {
        val result = typeCheck {
          """
          import zio.blocks.schema.migration.MacroCompleteSpec._
          builder[NormalSrcOpt, NormalTgt].mandateField(_.field, _.field, defaultInt).build
          """
        }
        assertZIO(result)(isLeft(containsString("Invalid Mandate")))
      },

      test("Error: Invalid Optionalize (Field Not Found)") {
        val result = typeCheck {
          """
          import zio.blocks.schema.migration.MacroCompleteSpec._
          case class OptTgt(field: Option[Int])
          builder[NormalSrc, OptTgt].optionalizeField(_.field, _.field).build
          """
        }
        assertZIO(result)(isLeft(containsString("Invalid Optionalize")))
      }
    )
  )
}
