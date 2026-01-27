package zio.blocks.schema

import zio.test._

object DynamicValueRegressionSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("DynamicValueRegressionSpec")(
    suite("PR #855 Regression Tests")(
      suite("Fix #5: Elements case preserves unmodified elements")(
        test("modify with .elements preserves unmatched elements") {
          val seq = DynamicValue.Sequence(
            Vector(
              DynamicValue.Record(Vector("x" -> DynamicValue.int(1))),
              DynamicValue.int(2),
              DynamicValue.Record(Vector("x" -> DynamicValue.int(3)))
            )
          )

          val path   = DynamicOptic.root.elements.field("x")
          val result = seq.modify(path)(_ => DynamicValue.int(99))

          assertTrue(
            result == DynamicValue.Sequence(
              Vector(
                DynamicValue.Record(Vector("x" -> DynamicValue.int(99))),
                DynamicValue.int(2),
                DynamicValue.Record(Vector("x" -> DynamicValue.int(99)))
              )
            )
          )
        },
        test("modify with .elements returns original when no matches") {
          val seq = DynamicValue.Sequence(
            Vector(
              DynamicValue.int(1),
              DynamicValue.int(2)
            )
          )

          val path   = DynamicOptic.root.elements.field("x")
          val result = seq.modify(path)(_ => DynamicValue.int(99))

          assertTrue(result eq seq)
        },
        test("modify with .elements preserves sequence length") {
          val seq = DynamicValue.Sequence(
            Vector(
              DynamicValue.Record(Vector("x" -> DynamicValue.int(1))),
              DynamicValue.string("not a record"),
              DynamicValue.Record(Vector("y" -> DynamicValue.int(2))),
              DynamicValue.Record(Vector("x" -> DynamicValue.int(3)))
            )
          )

          val path   = DynamicOptic.root.elements.field("x")
          val result = seq.modify(path)(_ => DynamicValue.int(99))

          result match {
            case DynamicValue.Sequence(elems) =>
              assertTrue(elems.length == 4)
            case _ => assertTrue(false)
          }
        }
      ),
      suite("Fix #6: MapKeys/MapValues preserve unmodified entries")(
        test("modify with .mapValues preserves unmatched entries") {
          val map = DynamicValue.Map(
            Vector(
              DynamicValue.string("a") -> DynamicValue.Record(Vector("x" -> DynamicValue.int(1))),
              DynamicValue.string("b") -> DynamicValue.int(2)
            )
          )

          val path   = DynamicOptic.root.mapValues.field("x")
          val result = map.modify(path)(_ => DynamicValue.int(99))

          assertTrue(
            result == DynamicValue.Map(
              Vector(
                DynamicValue.string("a") -> DynamicValue.Record(Vector("x" -> DynamicValue.int(99))),
                DynamicValue.string("b") -> DynamicValue.int(2)
              )
            )
          )
        },
        test("modify with .mapKeys preserves unmatched entries") {
          val map = DynamicValue.Map(
            Vector(
              DynamicValue.Record(Vector("id" -> DynamicValue.int(1))) -> DynamicValue.string("a"),
              DynamicValue.int(2)                                      -> DynamicValue.string("b")
            )
          )

          val path   = DynamicOptic.root.mapKeys.field("id")
          val result = map.modify(path)(_ => DynamicValue.int(99))

          result match {
            case DynamicValue.Map(entries) =>
              assertTrue(entries.length == 2)
            case _ => assertTrue(false)
          }
        },
        test("modify with .mapValues returns original when no matches") {
          val map = DynamicValue.Map(
            Vector(
              DynamicValue.string("a") -> DynamicValue.int(1),
              DynamicValue.string("b") -> DynamicValue.int(2)
            )
          )

          val path   = DynamicOptic.root.mapValues.field("x")
          val result = map.modify(path)(_ => DynamicValue.int(99))

          assertTrue(result eq map)
        }
      ),
      suite("Fix #3: Custom merge strategy Variant recursion")(
        test("Custom merge strategy with default recursion recurses into Variants") {
          val left = DynamicValue.Variant(
            "Some",
            DynamicValue.Record(Vector("a" -> DynamicValue.int(1)))
          )
          val right = DynamicValue.Variant(
            "Some",
            DynamicValue.Record(Vector("b" -> DynamicValue.int(2)))
          )

          val strategy = DynamicValueMergeStrategy.Custom(
            f = (_, _, r) => r
          )

          val result = left.merge(right, strategy)

          assertTrue(
            result == DynamicValue.Variant(
              "Some",
              DynamicValue.Record(
                Vector(
                  "a" -> DynamicValue.int(1),
                  "b" -> DynamicValue.int(2)
                )
              )
            )
          )
        },
        test("Custom default recursion matches Auto for Variant handling") {
          val left = DynamicValue.Variant(
            "Test",
            DynamicValue.Record(Vector("x" -> DynamicValue.int(1), "y" -> DynamicValue.int(2)))
          )
          val right = DynamicValue.Variant(
            "Test",
            DynamicValue.Record(Vector("y" -> DynamicValue.int(3), "z" -> DynamicValue.int(4)))
          )

          val customResult = left.merge(right, DynamicValueMergeStrategy.Custom(f = (_, _, r) => r))
          val autoResult   = left.merge(right, DynamicValueMergeStrategy.Auto)

          assertTrue(customResult == autoResult)
        }
      ),
      suite("Fix #1: fromKV reconstructs nested structures")(
        test("fromKV reconstructs nested records") {
          val kvs = Seq(
            (DynamicOptic.root.field("a").field("b"), DynamicValue.int(1)),
            (DynamicOptic.root.field("a").field("c"), DynamicValue.int(2))
          )

          val result = DynamicValue.fromKVUnsafe(kvs)

          assertTrue(
            result == DynamicValue.Record(
              Vector(
                "a" -> DynamicValue.Record(
                  Vector(
                    "b" -> DynamicValue.int(1),
                    "c" -> DynamicValue.int(2)
                  )
                )
              )
            )
          )
        },
        test("fromKV reconstructs deeply nested records") {
          val kvs = Seq(
            (DynamicOptic.root.field("a").field("b").field("c"), DynamicValue.int(1))
          )

          val result = DynamicValue.fromKVUnsafe(kvs)

          assertTrue(
            result == DynamicValue.Record(
              Vector(
                "a" -> DynamicValue.Record(
                  Vector(
                    "b" -> DynamicValue.Record(
                      Vector(
                        "c" -> DynamicValue.int(1)
                      )
                    )
                  )
                )
              )
            )
          )
        },
        test("toKV and fromKV round-trip for nested records") {
          val original = DynamicValue.Record(
            Vector(
              "user" -> DynamicValue.Record(
                Vector(
                  "name"  -> DynamicValue.string("Alice"),
                  "email" -> DynamicValue.string("alice@example.com")
                )
              ),
              "active" -> DynamicValue.boolean(true)
            )
          )

          val kvs           = original.toKV
          val reconstructed = DynamicValue.fromKVUnsafe(kvs)

          assertTrue(reconstructed == original)
        },
        test("fromKV reconstructs sequences with indices") {
          val kvs = Seq(
            (DynamicOptic.root.field("items").at(0), DynamicValue.int(1)),
            (DynamicOptic.root.field("items").at(1), DynamicValue.int(2))
          )

          val result = DynamicValue.fromKVUnsafe(kvs)

          assertTrue(
            result == DynamicValue.Record(
              Vector(
                "items" -> DynamicValue.Sequence(
                  Vector(
                    DynamicValue.int(1),
                    DynamicValue.int(2)
                  )
                )
              )
            )
          )
        },
        test("fromKV handles empty input") {
          val result = DynamicValue.fromKVUnsafe(Seq.empty)
          assertTrue(result == DynamicValue.Record.empty)
        }
      )
    )
  )
}
