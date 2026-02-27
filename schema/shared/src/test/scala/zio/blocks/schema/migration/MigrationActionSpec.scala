package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, SchemaBaseSpec}
import zio.test.Assertion._
import zio.test._

object MigrationActionSpec extends SchemaBaseSpec {

  private def prim(s: String): DynamicValue = new DynamicValue.Primitive(new PrimitiveValue.String(s))
  private def primInt(i: Int): DynamicValue  = new DynamicValue.Primitive(new PrimitiveValue.Int(i))

  def spec: Spec[TestEnvironment, Any] = suite("MigrationActionSpec")(
    reverseSuite,
    dynamicValueTransformSuite
  )

  private val reverseSuite = suite("reverse")(
    test("AddField.reverse is DropField") {
      val action = MigrationAction.AddField(DynamicOptic.root.field("x"), primInt(0))
      assert(action.reverse)(equalTo(
        MigrationAction.DropField(DynamicOptic.root.field("x"), primInt(0))
      ))
    },
    test("DropField.reverse is AddField") {
      val action = MigrationAction.DropField(DynamicOptic.root.field("x"), primInt(0))
      assert(action.reverse)(equalTo(
        MigrationAction.AddField(DynamicOptic.root.field("x"), primInt(0))
      ))
    },
    test("Rename.reverse swaps names") {
      val action = MigrationAction.Rename(DynamicOptic.root, "a", "b")
      assert(action.reverse)(equalTo(
        MigrationAction.Rename(DynamicOptic.root, "b", "a")
      ))
    },
    test("Mandate.reverse is Optionalize") {
      val action = MigrationAction.Mandate(
        DynamicOptic.root.field("x"),
        DynamicOptic.root.field("x"),
        primInt(0)
      )
      val rev = action.reverse
      assert(rev.isInstanceOf[MigrationAction.Optionalize])(isTrue)
    },
    test("Optionalize.reverse is Mandate") {
      val action = MigrationAction.Optionalize(
        DynamicOptic.root.field("x"),
        DynamicOptic.root.field("x")
      )
      val rev = action.reverse
      assert(rev.isInstanceOf[MigrationAction.Mandate])(isTrue)
    },
    test("RenameCase.reverse swaps names") {
      val action = MigrationAction.RenameCase(DynamicOptic.root, "A", "B")
      assert(action.reverse)(equalTo(
        MigrationAction.RenameCase(DynamicOptic.root, "B", "A")
      ))
    },
    test("TransformCase.reverse reverses sub-actions and swaps case names") {
      val action = MigrationAction.TransformCase(
        DynamicOptic.root,
        "CaseA",
        "CaseB",
        Vector(
          MigrationAction.AddField(DynamicOptic.root.field("x"), primInt(0)),
          MigrationAction.Rename(DynamicOptic.root, "y", "z")
        )
      )
      val rev = action.reverse.asInstanceOf[MigrationAction.TransformCase]
      assert(rev.caseName)(equalTo("CaseB")) &&
      assert(rev.targetCaseName)(equalTo("CaseA")) &&
      assert(rev.actions.length)(equalTo(2))
    },
    test("Join.reverse is Split with swapped fields") {
      val action = MigrationAction.Join(
        DynamicOptic.root,
        Vector("first", "last"),
        "fullName",
        DynamicValueTransform.ConcatFields(Vector("first", "last"), " "),
        Some(DynamicValueTransform.SplitString(" ", Vector("first", "last")))
      )
      val rev = action.reverse
      assert(rev.isInstanceOf[MigrationAction.Split])(isTrue) &&
      assert(rev.asInstanceOf[MigrationAction.Split].sourceField)(equalTo("fullName")) &&
      assert(rev.asInstanceOf[MigrationAction.Split].targetFields)(equalTo(Vector("first", "last")))
    },
    test("Split.reverse is Join with swapped fields") {
      val action = MigrationAction.Split(
        DynamicOptic.root,
        "fullName",
        Vector("first", "last"),
        DynamicValueTransform.SplitString(" ", Vector("first", "last")),
        Some(DynamicValueTransform.ConcatFields(Vector("first", "last"), " "))
      )
      val rev = action.reverse
      assert(rev.isInstanceOf[MigrationAction.Join])(isTrue) &&
      assert(rev.asInstanceOf[MigrationAction.Join].sourceFields)(equalTo(Vector("first", "last"))) &&
      assert(rev.asInstanceOf[MigrationAction.Join].targetField)(equalTo("fullName"))
    },
    test("double reverse returns original action for all action types") {
      val actions: Vector[MigrationAction] = Vector(
        MigrationAction.AddField(DynamicOptic.root.field("x"), primInt(0)),
        MigrationAction.DropField(DynamicOptic.root.field("x"), primInt(0)),
        MigrationAction.Rename(DynamicOptic.root, "a", "b"),
        MigrationAction.RenameCase(DynamicOptic.root, "A", "B"),
        MigrationAction.Optionalize(DynamicOptic.root.field("x"), DynamicOptic.root.field("x")),
        MigrationAction.Join(
          DynamicOptic.root, Vector("a", "b"), "ab",
          DynamicValueTransform.ConcatFields(Vector("a", "b"), ""),
          Some(DynamicValueTransform.SplitString("", Vector("a", "b")))
        ),
        MigrationAction.Split(
          DynamicOptic.root, "ab", Vector("a", "b"),
          DynamicValueTransform.SplitString("", Vector("a", "b")),
          Some(DynamicValueTransform.ConcatFields(Vector("a", "b"), ""))
        )
      )
      assert(actions.forall(a => a.reverse.reverse == a))(isTrue)
    }
  )

  private val dynamicValueTransformSuite = suite("DynamicValueTransform")(
    test("Constant always returns the constant value") {
      val t = DynamicValueTransform.Constant(prim("hello"))
      assert(t(primInt(42)))(isRight(equalTo(prim("hello"))))
    },
    test("Identity returns input unchanged") {
      val t = DynamicValueTransform.Identity
      assert(t(primInt(42)))(isRight(equalTo(primInt(42))))
    },
    test("NumericToString converts Int to String") {
      val t = DynamicValueTransform.NumericToString
      assert(t(primInt(42)))(isRight(equalTo(prim("42"))))
    },
    test("StringToInt converts String to Int") {
      val t = DynamicValueTransform.StringToInt
      assert(t(prim("42")))(isRight(equalTo(primInt(42))))
    },
    test("StringToInt fails on non-numeric string") {
      val t = DynamicValueTransform.StringToInt
      assert(t(prim("abc")))(isLeft)
    },
    test("IntToLong converts Int to Long") {
      val t = DynamicValueTransform.IntToLong
      assert(t(primInt(42)))(isRight(equalTo(
        new DynamicValue.Primitive(new PrimitiveValue.Long(42L))
      )))
    },
    test("LongToInt converts Long to Int") {
      val t = DynamicValueTransform.LongToInt
      assert(t(new DynamicValue.Primitive(new PrimitiveValue.Long(42L))))(isRight(equalTo(primInt(42))))
    },
    test("ConcatFields concatenates string fields") {
      val t     = DynamicValueTransform.ConcatFields(Vector("first", "last"), " ")
      val input = new DynamicValue.Record(Chunk(
        ("first", prim("John")),
        ("last", prim("Doe"))
      ))
      assert(t(input))(isRight(equalTo(prim("John Doe"))))
    },
    test("SplitString splits a string into named fields") {
      val t     = DynamicValueTransform.SplitString(" ", Vector("first", "last"))
      val input = prim("John Doe")
      val expected = new DynamicValue.Record(Chunk(
        ("first", prim("John")),
        ("last", prim("Doe"))
      ))
      assert(t(input))(isRight(equalTo(expected)))
    },
    test("SplitString fails on non-string primitive") {
      val t = DynamicValueTransform.SplitString(" ", Vector("a", "b"))
      assert(t(primInt(42)))(isLeft)
    },
    test("Compose chains transformations") {
      val t = DynamicValueTransform.Compose(Vector(
        DynamicValueTransform.NumericToString,
        DynamicValueTransform.Constant(prim("done"))
      ))
      assert(t(primInt(42)))(isRight(equalTo(prim("done"))))
    }
  )
}
