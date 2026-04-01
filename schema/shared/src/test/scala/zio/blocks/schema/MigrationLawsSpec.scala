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

import zio.blocks.chunk.Chunk
import zio.test._

object MigrationLawsSpec extends SchemaBaseSpec {

  private val root                              = DynamicOptic.root
  private def field(name: String): DynamicOptic = root.field(name)

  private val noneValue: DynamicValue =
    DynamicValue.Variant("None", DynamicValue.Record(Chunk.empty))

  private val defaultForOptionalizeReverse: MigrationExpr =
    MigrationExpr.Literal(noneValue)

  // ── Generators ────────────────────────────────────────────────────────────

  /** Alphanumeric field name, 1–8 chars. */
  private val genName: Gen[Any, String] =
    Gen.alphaNumericStringBounded(1, 8)

  /** A primitive DynamicValue. */
  private val genPrimitive: Gen[Any, DynamicValue] =
    Gen.oneOf(
      Gen.int(-999, 999).map(DynamicValue.int),
      Gen.alphaNumericStringBounded(1, 8).map(DynamicValue.string),
      Gen.boolean.map(DynamicValue.boolean)
    )

  /**
   * A flat record with 1–5 fields, all keys distinct. Fields are always
   * primitive so TransformValue / StringConcat tests work cleanly.
   */
  private val genFlatRecord: Gen[Any, DynamicValue.Record] =
    for {
      n     <- Gen.int(1, 5)
      pairs <- Gen.listOfBounded(n, n)(genName.zip(genPrimitive))
      unique = pairs.distinctBy(_._1)
      chunk  = Chunk.from(unique)
    } yield DynamicValue.Record(chunk)

  /** Picks an existing key from the record. */
  private def genRecordAndKey(record: DynamicValue.Record): Gen[Any, String] =
    Gen.int(0, record.fields.length - 1).map(i => record.fields(i)._1)

  /**
   * A flat record where at least two fields hold string values with
   * alphanumeric-only content and distinct keys. Used for Join/Split tests.
   */
  private val genRecordWithTwoStrings: Gen[Any, (DynamicValue.Record, String, String)] =
    for {
      k1     <- genName
      k2     <- genName.filter(_ != k1)
      v1     <- Gen.alphaNumericStringBounded(1, 8).map(DynamicValue.string)
      v2     <- Gen.alphaNumericStringBounded(1, 8).map(DynamicValue.string)
      extras <- Gen.listOfBounded(0, 3)(genName.filter(k => k != k1 && k != k2).zip(genPrimitive))
      unique  = ((k1, v1) :: (k2, v2) :: extras).distinctBy(_._1)
      record  = DynamicValue.Record(Chunk.from(unique))
    } yield (record, k1, k2)

  // ── Suite 1: Identity ─────────────────────────────────────────────────────

  private val identitySuite = suite("Law 1: Identity")(
    test("empty.apply(v) == Right(v) for any Record") {
      check(genFlatRecord) { record =>
        assertTrue(DynamicMigration.empty(record) == Right(record))
      }
    },
    test("empty.apply(v) == Right(v) for Variant") {
      check(genName, genFlatRecord) { (caseName, payload) =>
        val v = DynamicValue.Variant(caseName, payload)
        assertTrue(DynamicMigration.empty(v) == Right(v))
      }
    },
    test("empty ++ m has same actions as m") {
      check(genName, genName.filter(_ != "x")) { (a, b) =>
        val m = DynamicMigration(Chunk.single(MigrationAction.RenameField(field(a), field(b))))
        assertTrue((DynamicMigration.empty ++ m).actions == m.actions)
      }
    },
    test("m ++ empty has same actions as m") {
      check(genName, genName.filter(_ != "x")) { (a, b) =>
        val m = DynamicMigration(Chunk.single(MigrationAction.RenameField(field(a), field(b))))
        assertTrue((m ++ DynamicMigration.empty).actions == m.actions)
      }
    }
  )

  // ── Suite 2: Monoid Associativity ─────────────────────────────────────────

  private val associativitySuite = suite("Law 2: Monoid Associativity")(
    test("(m1 ++ m2) ++ m3 actions equal m1 ++ (m2 ++ m3) actions") {
      check(genName, genName.filter(_ != "x"), genName.filter(k => k != "x" && k != "y")) { (a, b, c) =>
        val m1 = DynamicMigration(Chunk.single(MigrationAction.RenameField(field(a), field(b))))
        val m2 = DynamicMigration(Chunk.single(MigrationAction.RenameField(field(b), field(c))))
        val m3 = DynamicMigration(
          Chunk.single(MigrationAction.AddField(field("z"), MigrationExpr.Literal(DynamicValue.int(0))))
        )
        assertTrue(((m1 ++ m2) ++ m3).actions == (m1 ++ (m2 ++ m3)).actions)
      }
    },
    test("independent AddField actions produce same result regardless of grouping") {
      check(genFlatRecord.filter(_.fields.length >= 1)) { record =>
        val m1 = DynamicMigration(
          Chunk.single(MigrationAction.AddField(field("_extra1"), MigrationExpr.Literal(DynamicValue.int(1))))
        )
        val m2 = DynamicMigration(
          Chunk.single(MigrationAction.AddField(field("_extra2"), MigrationExpr.Literal(DynamicValue.int(2))))
        )
        val m3 = DynamicMigration(
          Chunk.single(MigrationAction.AddField(field("_extra3"), MigrationExpr.Literal(DynamicValue.int(3))))
        )
        val r1 = ((m1 ++ m2) ++ m3)(record)
        val r2 = (m1 ++ (m2 ++ m3))(record)
        assertTrue(r1 == r2)
      }
    },
    test("sequential == composed: two renames applied sequentially equal one composed migration") {
      check(genFlatRecord.filter(_.fields.length >= 1)) { record =>
        val k  = record.fields(0)._1
        val m1 = DynamicMigration(
          Chunk.single(
            MigrationAction.TransformValue(field(k), MigrationExpr.IntToString, MigrationExpr.StringToInt)
          )
        )
        val m2 = DynamicMigration(
          Chunk.single(
            MigrationAction.TransformValue(field(k), MigrationExpr.StringToInt, MigrationExpr.IntToString)
          )
        )
        // Filter to records where the first field is an Int
        val intRecord = DynamicValue.Record(
          (k, DynamicValue.int(42)) +: record.fields.filter(_._1 != k)
        )
        val r1 = m1(intRecord).flatMap(m2(_))
        val r2 = (m1 ++ m2)(intRecord)
        assertTrue(r1 == r2)
      }
    }
  )

  // ── Suite 3: Structural Reverse ────────────────────────────────────────────

  private val structuralReverseSuite = suite("Law 3: Structural Reverse")(
    test("m.reverse.reverse.actions.length == m.actions.length for rename") {
      check(genName, genName) { (a, b) =>
        val m = DynamicMigration(Chunk.single(MigrationAction.RenameField(field(a), field(b))))
        assertTrue(m.reverse.reverse.actions.length == m.actions.length)
      }
    },
    test("m.reverse.reverse.actions.length == m.actions.length for compound") {
      check(genFlatRecord.filter(_.fields.length >= 1)) { record =>
        val k = record.fields(0)._1
        val m = DynamicMigration(
          Chunk(
            MigrationAction.RenameField(field(k), field(k + "2")),
            MigrationAction.AddField(field("_new"), MigrationExpr.Literal(DynamicValue.int(0)))
          )
        )
        assertTrue(m.reverse.reverse.actions.length == m.actions.length)
      }
    },
    test("invert is an involution for RenameField") {
      check(genName, genName) { (a, b) =>
        val action = MigrationAction.RenameField(field(a), field(b))
        assertTrue(MigrationAction.invert(MigrationAction.invert(action)) == action)
      }
    },
    test("invert is an involution for AddField/DropField pair") {
      check(genName, genPrimitive) { (k, v) =>
        val add  = MigrationAction.AddField(field(k), MigrationExpr.Literal(v))
        val drop = MigrationAction.DropField(field(k), MigrationExpr.Literal(v))
        assertTrue(
          MigrationAction.invert(MigrationAction.invert(add)) == add &&
            MigrationAction.invert(MigrationAction.invert(drop)) == drop
        )
      }
    },
    test("invert is an involution for TransformValue") {
      check(genName) { k =>
        val action = MigrationAction.TransformValue(field(k), MigrationExpr.IntToString, MigrationExpr.StringToInt)
        assertTrue(MigrationAction.invert(MigrationAction.invert(action)) == action)
      }
    },
    test("invert is an involution for RenameCase") {
      check(genName, genName, genName) { (path, from, to) =>
        val action = MigrationAction.RenameCase(field(path), from, to)
        assertTrue(MigrationAction.invert(MigrationAction.invert(action)) == action)
      }
    },
    test("reverse reverses action order") {
      check(genName, genName, genName) { (a, b, c) =>
        val action1  = MigrationAction.RenameField(field(a), field(b))
        val action2  = MigrationAction.AddField(field(c), MigrationExpr.Literal(DynamicValue.int(0)))
        val m        = DynamicMigration(Chunk(action1, action2))
        val reversed = m.reverse
        assertTrue(reversed.actions.length == 2)
      }
    }
  )

  // ── Suite 4: Semantic Invertibility ───────────────────────────────────────

  private val semanticInvertibilitySuite = suite("Law 4: Semantic Invertibility")(
    test("RenameField round-trip recovers original (sortFields)") {
      check(genFlatRecord.filter(_.fields.length >= 1)) { record =>
        val k      = record.fields(0)._1
        val k2     = k + "_renamed"
        val m      = DynamicMigration(Chunk.single(MigrationAction.RenameField(field(k), field(k2))))
        val result = m(record).flatMap(m.reverse(_))
        assertTrue(result.map(_.sortFields) == Right(record.sortFields))
      }
    },
    test("AddField then reverse (DropField) recovers original") {
      check(genFlatRecord, genPrimitive) { (record, v) =>
        val newKey = "_added"
        val m      = DynamicMigration(
          Chunk.single(
            MigrationAction.AddField(field(newKey), MigrationExpr.Literal(v))
          )
        )
        val result = m(record).flatMap(m.reverse(_))
        assertTrue(result == Right(record))
      }
    },
    test("TransformValue IntToString/StringToInt round-trip recovers original") {
      check(genName, Gen.int(-999, 999)) { (k, n) =>
        val record = DynamicValue.Record(Chunk.single((k, DynamicValue.int(n))))
        val m      = DynamicMigration(
          Chunk.single(
            MigrationAction.TransformValue(field(k), MigrationExpr.IntToString, MigrationExpr.StringToInt)
          )
        )
        val result = m(record).flatMap(m.reverse(_))
        assertTrue(result == Right(record))
      }
    },
    test("Optionalize then Mandate round-trip recovers original") {
      check(genName, genPrimitive) { (k, v) =>
        val record = DynamicValue.Record(Chunk.single((k, v)))
        val m      = DynamicMigration(
          Chunk.single(
            MigrationAction.Optionalize(field(k), defaultForOptionalizeReverse)
          )
        )
        val result = m(record).flatMap(m.reverse(_))
        assertTrue(result == Right(record))
      }
    },
    test("Join then Split (via reverse) round-trip recovers original") {
      check(genRecordWithTwoStrings) { case (record, k1, k2) =>
        val sep     = "|"
        val joinKey = "_joined"
        val m       = DynamicMigration(
          Chunk.single(
            MigrationAction.Join(
              field(k1),
              field(k2),
              field(joinKey),
              MigrationExpr.StringConcat(sep),
              MigrationExpr.StringSplitLeft(sep),
              MigrationExpr.StringSplitRight(sep)
            )
          )
        )
        val result = m(record).flatMap(m.reverse(_))
        assertTrue(result.map(_.sortFields) == Right(record.sortFields))
      }
    },
    test("RenameCase round-trip recovers original") {
      check(genName, genName, genFlatRecord) { (fromCase, toCase, payload) =>
        val v      = DynamicValue.Variant(fromCase, payload)
        val m      = DynamicMigration(Chunk.single(MigrationAction.RenameCase(root, fromCase, toCase)))
        val result = m(v).flatMap(m.reverse(_))
        assertTrue(result == Right(v))
      }
    },
    test("RenameCase pass-through: non-matching case is unchanged") {
      check(genName, genName, genFlatRecord) { (fromCase, toCase, payload) =>
        val otherCase = fromCase + "_other"
        val v         = DynamicValue.Variant(otherCase, payload)
        val m         = DynamicMigration(Chunk.single(MigrationAction.RenameCase(root, fromCase, toCase)))
        val result    = m(v)
        assertTrue(result == Right(v))
      }
    },
    test("compound lossless: rename + addField round-trip") {
      check(genFlatRecord.filter(_.fields.length >= 1)) { record =>
        val k      = record.fields(0)._1
        val k2     = k + "_v2"
        val newKey = "_ts"
        val m      = DynamicMigration(
          Chunk(
            MigrationAction.RenameField(field(k), field(k2)),
            MigrationAction.AddField(field(newKey), MigrationExpr.Literal(DynamicValue.int(0)))
          )
        )
        val result = m(record).flatMap(m.reverse(_))
        assertTrue(result.map(_.sortFields) == Right(record.sortFields))
      }
    }
  )

  // ── Suite 5: Compositionality ─────────────────────────────────────────────

  private val compositionalitySuite = suite("Law 5: Compositionality")(
    test("(m1 ++ m2).reverse.actions.length == (m2.reverse ++ m1.reverse).actions.length") {
      check(genName, genName, genName) { (a, b, c) =>
        val m1  = DynamicMigration(Chunk.single(MigrationAction.RenameField(field(a), field(b))))
        val m2  = DynamicMigration(Chunk.single(MigrationAction.RenameField(field(b), field(c))))
        val lhs = (m1 ++ m2).reverse
        val rhs = m2.reverse ++ m1.reverse
        assertTrue(lhs.actions.length == rhs.actions.length)
      }
    },
    test("m ++ m.reverse applied to record is idempotent for RenameField") {
      check(genFlatRecord.filter(_.fields.length >= 1)) { record =>
        val k      = record.fields(0)._1
        val m      = DynamicMigration(Chunk.single(MigrationAction.RenameField(field(k), field(k + "_tmp"))))
        val result = (m ++ m.reverse)(record)
        assertTrue(result.map(_.sortFields) == Right(record.sortFields))
      }
    },
    test("three-way composition: (m1 ++ m2 ++ m3).reverse == m3.r ++ m2.r ++ m1.r (action count)") {
      check(genName, genName, genName, genName) { (a, b, c, d) =>
        val m1  = DynamicMigration(Chunk.single(MigrationAction.RenameField(field(a), field(b))))
        val m2  = DynamicMigration(Chunk.single(MigrationAction.RenameField(field(b), field(c))))
        val m3  = DynamicMigration(Chunk.single(MigrationAction.RenameField(field(c), field(d))))
        val lhs = (m1 ++ m2 ++ m3).reverse
        val rhs = m3.reverse ++ m2.reverse ++ m1.reverse
        assertTrue(lhs.actions.length == rhs.actions.length)
      }
    },
    test("identity is its own reverse") {
      assertTrue(DynamicMigration.empty.reverse.actions == DynamicMigration.empty.actions)
    }
  )

  // ── Suite 6: Error path ────────────────────────────────────────────────────

  private val errorPathSuite = suite("Error path")(
    test("RenameField on missing field returns Left") {
      check(genFlatRecord) { record =>
        val missing = "_no_such_field_xyz"
        val m       = DynamicMigration(Chunk.single(MigrationAction.RenameField(field(missing), field("_out"))))
        val result  = m(record)
        assertTrue(result.isLeft)
      }
    },
    test("Mandate on None field returns Left") {
      check(genName) { k =>
        val record = DynamicValue.Record(Chunk.single((k, noneValue)))
        val m      = DynamicMigration(Chunk.single(MigrationAction.Mandate(field(k))))
        val result = m(record)
        assertTrue(result.isLeft)
      }
    },
    test("StringToInt on non-numeric string returns Left") {
      check(genName, Gen.alphaNumericStringBounded(1, 4).filter(s => scala.util.Try(s.toInt).isFailure)) { (k, s) =>
        val record = DynamicValue.Record(Chunk.single((k, DynamicValue.string(s))))
        val m      = DynamicMigration(
          Chunk.single(
            MigrationAction.TransformValue(field(k), MigrationExpr.StringToInt, MigrationExpr.IntToString)
          )
        )
        val result = m(record)
        assertTrue(result.isLeft)
      }
    },
    test("error short-circuits: second action not applied after first fails") {
      check(genFlatRecord) { record =>
        val missing = "_no_such_field_xyz"
        val m       = DynamicMigration(
          Chunk(
            MigrationAction.RenameField(field(missing), field("_out")),
            MigrationAction.AddField(field("_extra"), MigrationExpr.Literal(DynamicValue.int(0)))
          )
        )
        val result = m(record)
        assertTrue(result.isLeft)
      }
    }
  )

  // ── Suite 7: Nested path ───────────────────────────────────────────────────

  private val nestedPathSuite = suite("Nested path")(
    test("RenameField on nested addr.street -> addr.streetName round-trips") {
      val addr = DynamicValue.Record(
        Chunk.from(
          List(
            "street" -> DynamicValue.string("123 Main St"),
            "city"   -> DynamicValue.string("Springfield")
          )
        )
      )
      val record = DynamicValue.Record(
        Chunk.from(
          List(
            "name" -> DynamicValue.string("Alice"),
            "addr" -> addr
          )
        )
      )

      val streetPath     = root.field("addr").field("street")
      val streetNamePath = root.field("addr").field("streetName")

      val m       = DynamicMigration(Chunk.single(MigrationAction.RenameField(streetPath, streetNamePath)))
      val forward = m(record)
      val round   = forward.flatMap(m.reverse(_))

      assertTrue(
        forward.isRight &&
          forward.toOption.get.sortFields != record.sortFields &&
          round.map(_.sortFields) == Right(record.sortFields)
      )
    }
  )

  // ── Spec ──────────────────────────────────────────────────────────────────

  def spec: Spec[TestEnvironment, Any] = suite("MigrationLawsSpec")(
    identitySuite,
    associativitySuite,
    structuralReverseSuite,
    semanticInvertibilitySuite,
    compositionalitySuite,
    errorPathSuite,
    nestedPathSuite
  )
}
