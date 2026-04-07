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
import zio.blocks.schema._
import zio.blocks.schema.migration.MigrationBuilderCompanion._
import zio.test.Assertion._
import zio.test._

object MigrationSpec extends SchemaBaseSpec {

  // ─────────────────────────────────────────────────────────────────────────
  // Test model — simple types used across all suites
  // ─────────────────────────────────────────────────────────────────────────

  case class PersonV1(name: String, age: Int)
  case class PersonV2(fullName: String, age: Int, active: Boolean)
  case class PersonV3(fullName: String, age: Long, active: Boolean)

  case class WithOptional(id: Int, label: Option[String])
  case class WithMandatory(id: Int, label: String)

  case class HasList(id: Int, tags: List[String])
  case class HasNestedLists(groups: List[HasList])
  case class HasMap(id: Int, counts: Map[String, Int])

  sealed trait Color
  case object Red   extends Color
  case object Blue  extends Color
  case object Green extends Color

  case class Painted(color: Color, value: Int)

  case class FullName(first: String, last: String)
  case class Combined(fullName: String, value: Int)
  case class Split(first: String, last: String, value: Int)

  object PersonV1 { implicit val schema: Schema[PersonV1] = Schema.derived }
  object PersonV2 {
    implicit val schema: Schema[PersonV2] =
      Schema.derived[PersonV2].defaultValue(PersonV2("", 0, active = false))
  }
  object PersonV3 { implicit val schema: Schema[PersonV3] = Schema.derived }
  object WithOptional { implicit val schema: Schema[WithOptional] = Schema.derived }
  object WithMandatory {
    implicit val schema: Schema[WithMandatory] =
      Schema.derived[WithMandatory].defaultValue(WithMandatory(0, "default"))
  }
  object HasList extends CompanionOptics[HasList] { implicit val schema: Schema[HasList] = Schema.derived }
  object HasNestedLists extends CompanionOptics[HasNestedLists] {
    implicit val schema: Schema[HasNestedLists] = Schema.derived
  }
  object HasMap  { implicit val schema: Schema[HasMap]  = Schema.derived }
  object Color   { implicit val schema: Schema[Color]   = Schema.derived }
  object Painted { implicit val schema: Schema[Painted] = Schema.derived }
  object FullName { implicit val schema: Schema[FullName] = Schema.derived }
  object Combined { implicit val schema: Schema[Combined] = Schema.derived }
  object Split    { implicit val schema: Schema[Split]    = Schema.derived }

  // ─────────────────────────────────────────────────────────────────────────
  // Convenience: build DynamicValue records directly
  // ─────────────────────────────────────────────────────────────────────────

  private def str(s: String): DynamicValue  = new DynamicValue.Primitive(new PrimitiveValue.String(s))
  private def int(i: Int): DynamicValue     = new DynamicValue.Primitive(new PrimitiveValue.Int(i))
  private def long(l: Long): DynamicValue   = new DynamicValue.Primitive(new PrimitiveValue.Long(l))
  private def bool(b: Boolean): DynamicValue = new DynamicValue.Primitive(new PrimitiveValue.Boolean(b))
  private val ptInt    = PrimitiveType.Int(Validation.None)
  private val ptLong   = PrimitiveType.Long(Validation.None)
  private val ptString = PrimitiveType.String(Validation.None)
  private def rec(fields: (String, DynamicValue)*): DynamicValue =
    new DynamicValue.Record(Chunk.from(fields))
  private def some(v: DynamicValue): DynamicValue =
    new DynamicValue.Variant("Some", new DynamicValue.Record(Chunk.single(("value", v))))
  private def none: DynamicValue =
    new DynamicValue.Variant("None", new DynamicValue.Record(Chunk.empty))
  private def seq(elems: DynamicValue*): DynamicValue =
    new DynamicValue.Sequence(Chunk.from(elems))

  // ─────────────────────────────────────────────────────────────────────────
  // Spec
  // ─────────────────────────────────────────────────────────────────────────

  def spec: Spec[TestEnvironment, Any] = suite("MigrationSpec")(
    suite("DynamicMigration laws")(
      test("identity: no actions returns value unchanged") {
        val dv     = rec("name" -> str("Alice"), "age" -> int(30))
        val result = DynamicMigration.identity.apply(dv)
        assert(result)(isRight(equalTo(dv)))
      },
      test("associativity: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)") {
        val dv = rec("x" -> str("hello"))
        val m1 = new DynamicMigration(Chunk.single(MigrationAction.RenameField(
          DynamicOptic.root.field("x"), "y"
        )))
        val m2 = new DynamicMigration(Chunk.single(MigrationAction.RenameField(
          DynamicOptic.root.field("y"), "z"
        )))
        val m3 = new DynamicMigration(Chunk.single(MigrationAction.TransformValue(
          DynamicOptic.root.field("z"),
          ValueExpr.Constant(str("world"))
        )))
        val left  = (m1 ++ m2) ++ m3
        val right = m1 ++ (m2 ++ m3)
        assert(left.apply(dv))(equalTo(right.apply(dv)))
      },
      test("++ with identity is a no-op") {
        val m  = new DynamicMigration(Chunk.single(MigrationAction.RenameField(
          DynamicOptic.root.field("a"), "b"
        )))
        val dv = rec("a" -> int(1))
        assert((m ++ DynamicMigration.identity).apply(dv))(equalTo(m.apply(dv))) &&
        assert((DynamicMigration.identity ++ m).apply(dv))(equalTo(m.apply(dv)))
      },
      test("structural reverse: reverse.reverse == original") {
        val m = new DynamicMigration(Chunk.from(List(
          MigrationAction.RenameField(DynamicOptic.root.field("name"), "fullName"),
          MigrationAction.ChangeType(
            DynamicOptic.root.field("age"),
            ValueExpr.PrimitiveConvert(ptInt, ptLong)
          ),
          MigrationAction.Optionalize(DynamicOptic.root.field("active"))
        )))
        assert(m.reverse.reverse.actions)(equalTo(m.actions))
      }
    ),
    suite("AddField")(
      test("inserts a constant field into a record") {
        val path   = DynamicOptic.root.field("active")
        val action = MigrationAction.AddField(path, ValueExpr.Constant(bool(true)))
        val dv     = rec("name" -> str("Alice"))
        val result = new DynamicMigration(Chunk.single(action)).apply(dv)
        assert(result)(isRight) &&
        assert(result.toOption.get.asInstanceOf[DynamicValue.Record].fields.map(_._1).contains("active"))(
          isTrue
        )
      }
    ),
    suite("DropField")(
      test("removes a field from a record") {
        val path   = DynamicOptic.root.field("age")
        val action = MigrationAction.DropField(path)
        val dv     = rec("name" -> str("Alice"), "age" -> int(30))
        val result = new DynamicMigration(Chunk.single(action)).apply(dv)
        assert(result)(isRight) &&
        assert(result.toOption.get.asInstanceOf[DynamicValue.Record].fields.map(_._1).contains("age"))(
          isFalse
        )
      }
    ),
    suite("RenameField")(
      test("renames a field in-place preserving position") {
        val path   = DynamicOptic.root.field("name")
        val action = MigrationAction.RenameField(path, "fullName")
        val dv     = rec("name" -> str("Alice"), "age" -> int(30))
        val result = new DynamicMigration(Chunk.single(action)).apply(dv)
        val fields = result.toOption.get.asInstanceOf[DynamicValue.Record].fields
        // "fullName" is now first, preserving order
        assert(result)(isRight) &&
        assert(fields.head._1)(equalTo("fullName")) &&
        assert(fields.head._2)(equalTo(str("Alice")))
      },
      test("fails when path does not end in a Field node") {
        val path   = DynamicOptic.root.at(0)
        val action = MigrationAction.RenameField(path, "x")
        val dv     = rec("a" -> int(1))
        assert(new DynamicMigration(Chunk.single(action)).apply(dv))(isLeft)
      }
    ),
    suite("TransformValue")(
      test("replaces a field value with a constant") {
        val path   = DynamicOptic.root.field("age")
        val action = MigrationAction.TransformValue(path, ValueExpr.Constant(int(99)))
        val dv     = rec("name" -> str("Alice"), "age" -> int(30))
        val result = new DynamicMigration(Chunk.single(action)).apply(dv)
        assert(result)(isRight(equalTo(rec("name" -> str("Alice"), "age" -> int(99)))))
      }
    ),
    suite("Mandate")(
      test("unwraps Some(x) to x") {
        val path   = DynamicOptic.root.field("label")
        val action = MigrationAction.Mandate(path, ValueExpr.Constant(str("default")))
        val dv     = rec("id" -> int(1), "label" -> some(str("hello")))
        val result = new DynamicMigration(Chunk.single(action)).apply(dv)
        assert(result)(isRight(equalTo(rec("id" -> int(1), "label" -> str("hello")))))
      },
      test("uses default when None") {
        val path   = DynamicOptic.root.field("label")
        val action = MigrationAction.Mandate(path, ValueExpr.Constant(str("default")))
        val dv     = rec("id" -> int(1), "label" -> none)
        val result = new DynamicMigration(Chunk.single(action)).apply(dv)
        assert(result)(isRight(equalTo(rec("id" -> int(1), "label" -> str("default")))))
      },
      test("fails on non-Option value") {
        val path   = DynamicOptic.root.field("id")
        val action = MigrationAction.Mandate(path, ValueExpr.Constant(int(0)))
        val dv     = rec("id" -> int(1))
        assert(new DynamicMigration(Chunk.single(action)).apply(dv))(isLeft)
      }
    ),
    suite("Optionalize")(
      test("wraps a value in Some") {
        val path   = DynamicOptic.root.field("label")
        val action = MigrationAction.Optionalize(path)
        val dv     = rec("id" -> int(1), "label" -> str("hello"))
        val result = new DynamicMigration(Chunk.single(action)).apply(dv)
        assert(result)(isRight(equalTo(rec("id" -> int(1), "label" -> some(str("hello"))))))
      }
    ),
    suite("ChangeType")(
      test("widens Int to Long") {
        val path   = DynamicOptic.root.field("age")
        val conv   = ValueExpr.PrimitiveConvert(ptInt, ptLong)
        val action = MigrationAction.ChangeType(path, conv)
        val dv     = rec("name" -> str("Alice"), "age" -> int(30))
        val result = new DynamicMigration(Chunk.single(action)).apply(dv)
        assert(result)(isRight(equalTo(rec("name" -> str("Alice"), "age" -> long(30L)))))
      },
      test("converts Int to String") {
        val path   = DynamicOptic.root.field("age")
        val conv   = ValueExpr.PrimitiveConvert(ptInt, ptString)
        val action = MigrationAction.ChangeType(path, conv)
        val dv     = rec("age" -> int(42))
        val result = new DynamicMigration(Chunk.single(action)).apply(dv)
        assert(result)(isRight(equalTo(rec("age" -> str("42")))))
      },
      test("converts String to Int") {
        val path   = DynamicOptic.root.field("age")
        val conv   = ValueExpr.PrimitiveConvert(ptString, ptInt)
        val action = MigrationAction.ChangeType(path, conv)
        val dv     = rec("age" -> str("42"))
        val result = new DynamicMigration(Chunk.single(action)).apply(dv)
        assert(result)(isRight(equalTo(rec("age" -> int(42)))))
      },
      test("fails on unparseable string") {
        val path   = DynamicOptic.root.field("age")
        val conv   = ValueExpr.PrimitiveConvert(ptString, ptInt)
        val action = MigrationAction.ChangeType(path, conv)
        val dv     = rec("age" -> str("not-a-number"))
        assert(new DynamicMigration(Chunk.single(action)).apply(dv))(isLeft)
      }
    ),
    suite("Join")(
      test("concatenates two string fields with separator") {
        val left    = DynamicOptic.root.field("first")
        val right   = DynamicOptic.root.field("last")
        val target  = DynamicOptic.root.field("fullName")
        val action  = MigrationAction.Join(left, right, target, ValueExpr.Concat(" "))
        val dv      = rec("first" -> str("John"), "last" -> str("Doe"))
        val result  = new DynamicMigration(Chunk.single(action)).apply(dv)
        val fields  = result.toOption.get.asInstanceOf[DynamicValue.Record].fields
        assert(result)(isRight) &&
        assert(fields.map(_._1).contains("first"))(isFalse) &&
        assert(fields.map(_._1).contains("last"))(isFalse) &&
        assert(fields.find(_._1 == "fullName").map(_._2))(isSome(equalTo(str("John Doe"))))
      }
    ),
    suite("Split")(
      test("splits a string field on separator") {
        val from    = DynamicOptic.root.field("fullName")
        val toLeft  = DynamicOptic.root.field("first")
        val toRight = DynamicOptic.root.field("last")
        val action  = MigrationAction.Split(from, toLeft, toRight, ValueExpr.StringSplit(" "))
        val dv      = rec("fullName" -> str("John Doe"), "value" -> int(1))
        val result  = new DynamicMigration(Chunk.single(action)).apply(dv)
        val fields  = result.toOption.get.asInstanceOf[DynamicValue.Record].fields
        assert(result)(isRight) &&
        assert(fields.map(_._1).contains("fullName"))(isFalse) &&
        assert(fields.find(_._1 == "first").map(_._2))(isSome(equalTo(str("John")))) &&
        assert(fields.find(_._1 == "last").map(_._2))(isSome(equalTo(str("Doe"))))
      },
      test("split with no separator found puts full string left and empty right") {
        val from    = DynamicOptic.root.field("name")
        val toLeft  = DynamicOptic.root.field("first")
        val toRight = DynamicOptic.root.field("rest")
        val action  = MigrationAction.Split(from, toLeft, toRight, ValueExpr.StringSplit(" "))
        val dv      = rec("name" -> str("Alice"))
        val result  = new DynamicMigration(Chunk.single(action)).apply(dv)
        val fields  = result.toOption.get.asInstanceOf[DynamicValue.Record].fields
        assert(result)(isRight) &&
        assert(fields.find(_._1 == "first").map(_._2))(isSome(equalTo(str("Alice")))) &&
        assert(fields.find(_._1 == "rest").map(_._2))(isSome(equalTo(str(""))))
      }
    ),
    suite("TransformElements")(
      test("applies a constant transform to each sequence element") {
        val path   = DynamicOptic.root.field("tags")
        val action = MigrationAction.TransformElements(path, ValueExpr.Constant(str("X")))
        val dv     = rec("id" -> int(1), "tags" -> seq(str("a"), str("b"), str("c")))
        val result = new DynamicMigration(Chunk.single(action)).apply(dv)
        val tags   = result.toOption.get.asInstanceOf[DynamicValue.Record].fields
          .find(_._1 == "tags").get._2.asInstanceOf[DynamicValue.Sequence].elements
        assert(result)(isRight) &&
        assert(tags)(equalTo(Chunk.from(List(str("X"), str("X"), str("X")))))
      },
      test("fails when path does not address a Sequence") {
        val path   = DynamicOptic.root.field("id")
        val action = MigrationAction.TransformElements(path, ValueExpr.Constant(int(0)))
        val dv     = rec("id" -> int(1))
        assert(new DynamicMigration(Chunk.single(action)).apply(dv))(isLeft)
      }
    ),
    suite("TransformKeys")(
      test("applies a PrimitiveConvert to each map key (String→String via Constant)") {
        val path   = DynamicOptic.root.field("counts")
        val action = MigrationAction.TransformKeys(path, ValueExpr.Constant(str("k")))
        val mapDv  = new DynamicValue.Map(Chunk.from(List(str("a") -> int(1), str("b") -> int(2))))
        val dv     = rec("id" -> int(1), "counts" -> mapDv)
        val result = new DynamicMigration(Chunk.single(action)).apply(dv)
        assert(result)(isRight)
      }
    ),
    suite("TransformValues")(
      test("applies a constant transform to each map value") {
        val path   = DynamicOptic.root.field("counts")
        val action = MigrationAction.TransformValues(path, ValueExpr.Constant(int(0)))
        val mapDv  = new DynamicValue.Map(Chunk.from(List(str("a") -> int(1), str("b") -> int(2))))
        val dv     = rec("id" -> int(1), "counts" -> mapDv)
        val result = new DynamicMigration(Chunk.single(action)).apply(dv)
        val entries = result.toOption.get.asInstanceOf[DynamicValue.Record].fields
          .find(_._1 == "counts").get._2.asInstanceOf[DynamicValue.Map].entries
        assert(result)(isRight) &&
        assert(entries.map(_._2))(equalTo(Chunk.from(List(int(0), int(0)))))
      }
    ),
    suite("RenameCase")(
      test("renames a matching variant case") {
        val action = MigrationAction.RenameCase("Red", "Crimson")
        val dv     = new DynamicValue.Variant("Red", new DynamicValue.Record(Chunk.empty))
        val result = new DynamicMigration(Chunk.single(action)).apply(dv)
        assert(result)(isRight(equalTo(
          new DynamicValue.Variant("Crimson", new DynamicValue.Record(Chunk.empty))
        )))
      },
      test("is a no-op when case name does not match") {
        val action = MigrationAction.RenameCase("Red", "Crimson")
        val dv     = new DynamicValue.Variant("Blue", new DynamicValue.Record(Chunk.empty))
        assert(new DynamicMigration(Chunk.single(action)).apply(dv))(isRight(equalTo(dv)))
      }
    ),
    suite("TransformCase")(
      test("transforms the inner value of a matching case") {
        val action = MigrationAction.TransformCase("Some", ValueExpr.Constant(str("replaced")))
        val dv     = new DynamicValue.Variant("Some", str("original"))
        val result = new DynamicMigration(Chunk.single(action)).apply(dv)
        assert(result)(isRight(equalTo(new DynamicValue.Variant("Some", str("replaced")))))
      },
      test("is a no-op when case name does not match") {
        val action = MigrationAction.TransformCase("Some", ValueExpr.Constant(str("replaced")))
        val dv     = new DynamicValue.Variant("None", new DynamicValue.Record(Chunk.empty))
        assert(new DynamicMigration(Chunk.single(action)).apply(dv))(isRight(equalTo(dv)))
      }
    ),
    suite("reverse")(
      test("AddField reverses to DropField") {
        val path = DynamicOptic.root.field("x")
        val m    = new DynamicMigration(Chunk.single(MigrationAction.AddField(path, ValueExpr.Constant(int(1)))))
        m.reverse.actions.head match {
          case MigrationAction.DropField(p) => assert(p)(equalTo(path))
          case other                        => assert(other.toString)(equalTo("DropField"))
        }
      },
      test("RenameField reverses by swapping path and newName") {
        val path = DynamicOptic.root.field("name")
        val m    = new DynamicMigration(Chunk.single(MigrationAction.RenameField(path, "fullName")))
        m.reverse.actions.head match {
          case MigrationAction.RenameField(p, n) =>
            assert(p)(equalTo(DynamicOptic.root.field("fullName"))) &&
            assert(n)(equalTo("name"))
          case other => assert(other.toString)(equalTo("RenameField"))
        }
      },
      test("ChangeType reverses by swapping from/to") {
        val path = DynamicOptic.root.field("age")
        val conv = ValueExpr.PrimitiveConvert(ptInt, ptLong)
        val m    = new DynamicMigration(Chunk.single(MigrationAction.ChangeType(path, conv)))
        m.reverse.actions.head match {
          case MigrationAction.ChangeType(_, ValueExpr.PrimitiveConvert(f, t)) =>
            assert(f)(equalTo(ptLong: PrimitiveType[_])) &&
            assert(t)(equalTo(ptInt: PrimitiveType[_]))
          case other => assert(other.toString)(equalTo("ChangeType(reversed)"))
        }
      },
      test("Mandate reverses to Optionalize") {
        val path = DynamicOptic.root.field("label")
        val m    = new DynamicMigration(Chunk.single(MigrationAction.Mandate(path, ValueExpr.DefaultValue)))
        m.reverse.actions.head match {
          case MigrationAction.Optionalize(p) => assert(p)(equalTo(path))
          case other                          => assert(other.toString)(equalTo("Optionalize"))
        }
      },
      test("Optionalize reverses to Mandate(DefaultValue)") {
        val path = DynamicOptic.root.field("label")
        val m    = new DynamicMigration(Chunk.single(MigrationAction.Optionalize(path)))
        m.reverse.actions.head match {
          case MigrationAction.Mandate(p, ValueExpr.DefaultValue) => assert(p)(equalTo(path))
          case other                                              => assert(other.toString)(equalTo("Mandate"))
        }
      },
      test("RenameCase reverses by swapping names") {
        val m = new DynamicMigration(Chunk.single(MigrationAction.RenameCase("Red", "Crimson")))
        m.reverse.actions.head match {
          case MigrationAction.RenameCase(f, t) =>
            assert(f)(equalTo("Crimson")) && assert(t)(equalTo("Red"))
          case other => assert(other.toString)(equalTo("RenameCase"))
        }
      },
      test("reverse actions are in reversed order") {
        val m = new DynamicMigration(Chunk.from(List(
          MigrationAction.RenameField(DynamicOptic.root.field("a"), "b"),
          MigrationAction.RenameField(DynamicOptic.root.field("b"), "c")
        )))
        val rev = m.reverse
        assert(rev.actions.length)(equalTo(2)) &&
        (rev.actions(0) match {
          case MigrationAction.RenameField(p, _) => assert(p)(equalTo(DynamicOptic.root.field("c")))
          case _                                  => assert(false)(isTrue)
        }) &&
        (rev.actions(1) match {
          case MigrationAction.RenameField(p, _) => assert(p)(equalTo(DynamicOptic.root.field("b")))
          case _                                  => assert(false)(isTrue)
        })
      }
    ),
    suite("Migration[A, B] typed layer")(
      test("applies a rename and correctly round-trips through Schema") {
        val m = MigrationBuilder[PersonV1, PersonV2](PersonV1.schema, PersonV2.schema)
          .withAction(MigrationAction.RenameField(DynamicOptic.root.field("name"), "fullName"))
          .withAction(MigrationAction.AddField(
            DynamicOptic.root.field("active"),
            ValueExpr.DefaultValue
          ))
          .build
        val result = m.apply(PersonV1("Alice", 30))
        assert(result)(isRight(equalTo(PersonV2("Alice", 30, active = false))))
      },
      test("applies a ChangeType migration Int → Long") {
        val m = MigrationBuilder[PersonV2, PersonV3](PersonV2.schema, PersonV3.schema)
          .withAction(MigrationAction.ChangeType(
            DynamicOptic.root.field("age"),
            ValueExpr.PrimitiveConvert(ptInt, ptLong)
          ))
          .build
        val result = m.apply(PersonV2("Alice", 30, active = true))
        assert(result)(isRight(equalTo(PersonV3("Alice", 30L, active = true))))
      },
      test("identity migration round-trips value unchanged") {
        val m      = Migration.identity(PersonV1.schema)
        val result = m.apply(PersonV1("Alice", 30))
        assert(result)(isRight(equalTo(PersonV1("Alice", 30))))
      },
      test("andThen composes two migrations") {
        val m1 = MigrationBuilder[PersonV1, PersonV2](PersonV1.schema, PersonV2.schema)
          .withAction(MigrationAction.RenameField(DynamicOptic.root.field("name"), "fullName"))
          .withAction(MigrationAction.AddField(
            DynamicOptic.root.field("active"),
            ValueExpr.DefaultValue
          ))
          .build
        val m2 = MigrationBuilder[PersonV2, PersonV3](PersonV2.schema, PersonV3.schema)
          .withAction(MigrationAction.ChangeType(
            DynamicOptic.root.field("age"),
            ValueExpr.PrimitiveConvert(ptInt, ptLong)
          ))
          .build
        val result = m1.andThen(m2).apply(PersonV1("Bob", 25))
        assert(result)(isRight(equalTo(PersonV3("Bob", 25L, active = false))))
      },
      test("++ composes two migrations") {
        val m1 = MigrationBuilder[PersonV1, PersonV2](PersonV1.schema, PersonV2.schema)
          .withAction(MigrationAction.RenameField(DynamicOptic.root.field("name"), "fullName"))
          .withAction(MigrationAction.AddField(
            DynamicOptic.root.field("active"),
            ValueExpr.DefaultValue
          ))
          .build
        val m2 = MigrationBuilder[PersonV2, PersonV3](PersonV2.schema, PersonV3.schema)
          .withAction(MigrationAction.ChangeType(
            DynamicOptic.root.field("age"),
            ValueExpr.PrimitiveConvert(ptInt, ptLong)
          ))
          .build
        val result = (m1 ++ m2).apply(PersonV1("Bob", 25))
        assert(result)(isRight(equalTo(PersonV3("Bob", 25L, active = false))))
      },
      test("reverse of typed migration swaps fromSchema and toSchema") {
        val m   = MigrationBuilder[PersonV1, PersonV2](PersonV1.schema, PersonV2.schema).build
        val rev = m.reverse
        assert(rev.fromSchema)(equalTo(PersonV2.schema)) &&
        assert(rev.toSchema)(equalTo(PersonV1.schema))
      },
      test("Optionalize migration wraps a field in Some") {
        val m = MigrationBuilder[WithMandatory, WithOptional](
          WithMandatory.schema,
          WithOptional.schema
        ).withAction(MigrationAction.Optionalize(DynamicOptic.root.field("label"))).build
        val result = m.apply(WithMandatory(1, "hello"))
        assert(result)(isRight(equalTo(WithOptional(1, Some("hello")))))
      },
      test("Mandate migration with DefaultValue resolves from target schema") {
        val m = MigrationBuilder[WithOptional, WithMandatory](
          WithOptional.schema,
          WithMandatory.schema
        ).withAction(MigrationAction.Mandate(
          DynamicOptic.root.field("label"),
          ValueExpr.DefaultValue
        )).build
        // None case: should use the default from WithMandatory.schema
        val resultNone = m.apply(WithOptional(1, None))
        assert(resultNone)(isRight(equalTo(WithMandatory(1, "default")))) &&
        // Some case: should unwrap the value
        assert(m.apply(WithOptional(1, Some("hello"))))(isRight(equalTo(WithMandatory(1, "hello"))))
      }
    ),
    suite("MigrationBuilder low-level DSL")(
      test("buildDynamic returns a DynamicMigration without schema info") {
        val dm = MigrationBuilder[PersonV1, PersonV2](PersonV1.schema, PersonV2.schema)
          .withAction(MigrationAction.RenameField(DynamicOptic.root.field("name"), "fullName"))
          .buildDynamic
        assert(dm.actions.length)(equalTo(1))
      },
      test("withActions appends multiple actions") {
        val extra = Chunk.from(List(
          MigrationAction.RenameField(DynamicOptic.root.field("a"), "b"),
          MigrationAction.RenameField(DynamicOptic.root.field("b"), "c")
        ))
        val b = MigrationBuilder[PersonV1, PersonV1](PersonV1.schema, PersonV1.schema)
          .withActions(extra)
        assert(b.actions.length)(equalTo(2))
      },
      test("build validates action paths against source and target schemas") {
        val ex =
          try {
            MigrationBuilder[PersonV1, PersonV2](PersonV1.schema, PersonV2.schema)
              .addField(_.fullName, ValueExpr.DefaultValue)
              .transformElements(_.name, ValueExpr.Constant(str("x")))
              .build
            throw new RuntimeException("expected build to fail")
          } catch {
            case ex: IllegalArgumentException => ex
          }
        assert(ex.getMessage)(containsString("must be a sequence"))
      },
      test("buildPartial skips validation") {
        val result =
          MigrationBuilder[PersonV1, PersonV2](PersonV1.schema, PersonV2.schema)
            .transformElements(_.name, ValueExpr.Constant(str("x")))
            .buildPartial
        assert(result.migration.actions.length)(equalTo(1))
      }
    ),
    suite("Macro DSL")(
      test("selector macros build actions without exposing DynamicOptic") {
        val migration = MigrationBuilder[PersonV1, PersonV2](PersonV1.schema, PersonV2.schema)
          .renameField(_.name, "fullName")
          .addField(_.active, ValueExpr.DefaultValue)
          .build

        assert(migration.apply(PersonV1("Alice", 30)))(isRight(equalTo(PersonV2("Alice", 30, active = false))))
      },
      test("selector macros support nested each traversals") {
        val migration = MigrationBuilder[HasNestedLists, HasNestedLists](HasNestedLists.schema, HasNestedLists.schema)
          .transformValue(_.groups.each.tags.each, ValueExpr.Constant(str("patched")))
          .buildPartial

        val path = migration.migration.actions.head.asInstanceOf[MigrationAction.TransformValue].path
        assert(path.toScalaString)(equalTo(".groups.each.tags.each"))
      },
    ),
    suite("Serialization")(
      test("ValueExpr round-trips through DynamicValue") {
        val expr = ValueExpr.PrimitiveConvert(ptInt, ptLong): ValueExpr
        val roundTripped =
          ValueExpr.schema.fromDynamicValue(ValueExpr.schema.toDynamicValue(expr))
        assert(roundTripped)(isRight(equalTo(expr)))
      },
      test("MigrationAction round-trips through DynamicValue") {
        val action: MigrationAction =
          MigrationAction.AddField(DynamicOptic.root.field("active"), ValueExpr.DefaultValue)
        val roundTripped =
          MigrationAction.schema.fromDynamicValue(MigrationAction.schema.toDynamicValue(action))
        assert(roundTripped)(isRight(equalTo(action)))
      },
      test("DynamicMigration round-trips through DynamicValue") {
        val migration = new DynamicMigration(Chunk.from(List(
          MigrationAction.RenameField(DynamicOptic.root.field("name"), "fullName"),
          MigrationAction.AddField(DynamicOptic.root.field("active"), ValueExpr.DefaultValue)
        )))
        val roundTripped =
          DynamicMigration.schema.fromDynamicValue(DynamicMigration.schema.toDynamicValue(migration))
        assert(roundTripped)(isRight(equalTo(migration)))
      },
      test("MigrationError round-trips through DynamicValue") {
        val error = MigrationError("boom", DynamicOptic.root.field("name"))
        val roundTripped =
          MigrationError.schema.fromDynamicValue(MigrationError.schema.toDynamicValue(error))
        assert(roundTripped)(isRight(equalTo(error)))
      }
    ),
    suite("ValueExpr evaluation")(
      test("DefaultValue fails at DynamicMigration level with clear message") {
        val action = MigrationAction.AddField(DynamicOptic.root.field("x"), ValueExpr.DefaultValue)
        val dv     = rec("a" -> int(1))
        val result = new DynamicMigration(Chunk.single(action)).apply(dv)
        assert(result)(isLeft) &&
        assert(result.left.toOption.map(_.message))(isSome(containsString("DefaultValue requires target schema")))
      },
      test("Concat fails as a unary transform") {
        val action = MigrationAction.TransformValue(
          DynamicOptic.root.field("x"),
          ValueExpr.Concat("-")
        )
        val dv = rec("x" -> str("hello"))
        assert(new DynamicMigration(Chunk.single(action)).apply(dv))(isLeft)
      },
      test("StringSplit as unary produces a Sequence") {
        val action = MigrationAction.TransformValue(
          DynamicOptic.root.field("csv"),
          ValueExpr.StringSplit(",")
        )
        val dv     = rec("csv" -> str("a,b,c"))
        val result = new DynamicMigration(Chunk.single(action)).apply(dv)
        assert(result)(isRight) &&
        assert(
          result.toOption.get.asInstanceOf[DynamicValue.Record]
            .fields.find(_._1 == "csv").get._2
            .asInstanceOf[DynamicValue.Sequence].elements.length
        )(equalTo(3))
      }
    )
  )
}
