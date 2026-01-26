package zio.blocks.schema.migration

import zio.test._
import zio.test.Assertion._
import zio.blocks.schema.SchemaBaseSpec
import zio.blocks.schema.migration.TypeLevel._

object TypeLevelSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("TypeLevelSpec (Scala 2)")(
    suite("Contains")(
      test("element present at head") {
        // "a" :: "b" :: "c" :: TNil contains "a"
        implicitly[Contains["a" :: "b" :: "c" :: TNil, "a"]]
        assertTrue(true)
      },
      test("element present in middle") {
        // "a" :: "b" :: "c" :: TNil contains "b"
        implicitly[Contains["a" :: "b" :: "c" :: TNil, "b"]]
        assertTrue(true)
      },
      test("element present at end") {
        // "a" :: "b" :: "c" :: TNil contains "c"
        implicitly[Contains["a" :: "b" :: "c" :: TNil, "c"]]
        assertTrue(true)
      },
      test("element absent should not compile") {
        // "a" :: "b" :: "c" :: TNil does NOT contain "d"
        typeCheck {
          """
          import zio.blocks.schema.migration.TypeLevel._
          implicitly[Contains["a" :: "b" :: "c" :: TNil, "d"]]
          """
        }.map(assert(_)(isLeft))
      },
      test("empty list contains nothing") {
        typeCheck {
          """
          import zio.blocks.schema.migration.TypeLevel._
          implicitly[Contains[TNil, "a"]]
          """
        }.map(assert(_)(isLeft))
      },
      test("single element list - present") {
        implicitly[Contains["x" :: TNil, "x"]]
        assertTrue(true)
      },
      test("single element list - absent should not compile") {
        typeCheck {
          """
          import zio.blocks.schema.migration.TypeLevel._
          implicitly[Contains["x" :: TNil, "y"]]
          """
        }.map(assert(_)(isLeft))
      }
    ),
    suite("NotContains")(
      test("empty list does not contain anything") {
        implicitly[NotContains[TNil, "a"]]
        assertTrue(true)
      },
      test("element not in list") {
        implicitly[NotContains["a" :: "b" :: TNil, "c"]]
        assertTrue(true)
      },
      test("element in list should not compile") {
        typeCheck {
          """
          import zio.blocks.schema.migration.TypeLevel._
          implicitly[NotContains["a" :: "b" :: TNil, "a"]]
          """
        }.map(assert(_)(isLeft))
      }
    ),
    suite("IsSubset")(
      test("subset - true case") {
        // ("a") ⊆ ("a", "b")
        implicitly[IsSubset["a" :: TNil, "a" :: "b" :: TNil]]
        assertTrue(true)
      },
      test("subset - false case should not compile") {
        // ("a", "c") ⊄ ("a", "b") because "c" not in ("a", "b")
        typeCheck {
          """
          import zio.blocks.schema.migration.TypeLevel._
          implicitly[IsSubset["a" :: "c" :: TNil, "a" :: "b" :: TNil]]
          """
        }.map(assert(_)(isLeft))
      },
      test("empty is subset of all") {
        implicitly[IsSubset[TNil, "a" :: "b" :: TNil]]
        assertTrue(true)
      },
      test("empty is subset of empty") {
        implicitly[IsSubset[TNil, TNil]]
        assertTrue(true)
      },
      test("equal sets are subsets of each other") {
        implicitly[IsSubset["a" :: "b" :: TNil, "a" :: "b" :: TNil]]
        implicitly[IsSubset["a" :: "b" :: TNil, "b" :: "a" :: TNil]]
        assertTrue(true)
      },
      test("superset is not subset") {
        // ("a", "b", "c") ⊄ ("a", "b")
        typeCheck {
          """
          import zio.blocks.schema.migration.TypeLevel._
          implicitly[IsSubset["a" :: "b" :: "c" :: TNil, "a" :: "b" :: TNil]]
          """
        }.map(assert(_)(isLeft))
      },
      test("disjoint sets - not subset") {
        typeCheck {
          """
          import zio.blocks.schema.migration.TypeLevel._
          implicitly[IsSubset["x" :: "y" :: TNil, "a" :: "b" :: TNil]]
          """
        }.map(assert(_)(isLeft))
      },
      test("single element subset") {
        implicitly[IsSubset["b" :: TNil, "a" :: "b" :: "c" :: TNil]]
        assertTrue(true)
      }
    ),
    suite("SubsetEvidence (alias)")(
      test("empty subset evidence exists") {
        implicitly[SubsetEvidence[TNil, "a" :: "b" :: TNil]]
        assertTrue(true)
      },
      test("single element subset evidence exists") {
        implicitly[SubsetEvidence["a" :: TNil, "a" :: "b" :: TNil]]
        assertTrue(true)
      },
      test("full subset evidence exists") {
        implicitly[SubsetEvidence["a" :: "b" :: TNil, "a" :: "b" :: "c" :: TNil]]
        assertTrue(true)
      },
      test("non-subset should not compile") {
        typeCheck {
          """
          import zio.blocks.schema.migration.TypeLevel._
          implicitly[SubsetEvidence["a" :: "x" :: TNil, "a" :: "b" :: TNil]]
          """
        }.map(assert(_)(isLeft))
      }
    ),
    suite("Append")(
      test("append to empty list") {
        val ev = implicitly[Append.Aux[TNil, "a", "a" :: TNil]]
        assertTrue(ev != null)
      },
      test("append to non-empty list") {
        val ev = implicitly[Append.Aux["a" :: TNil, "b", "a" :: "b" :: TNil]]
        assertTrue(ev != null)
      },
      test("append to two-element list") {
        val ev = implicitly[Append.Aux["a" :: "b" :: TNil, "c", "a" :: "b" :: "c" :: TNil]]
        assertTrue(ev != null)
      }
    ),
    suite("Prepend")(
      test("prepend to empty list") {
        val ev = implicitly[Prepend.Aux["a", TNil, "a" :: TNil]]
        assertTrue(ev != null)
      },
      test("prepend to non-empty list") {
        val ev = implicitly[Prepend.Aux["a", "b" :: TNil, "a" :: "b" :: TNil]]
        assertTrue(ev != null)
      }
    ),
    suite("Difference")(
      test("difference of empty list is empty") {
        val ev = implicitly[Difference.Aux[TNil, "a" :: "b" :: TNil, TNil]]
        assertTrue(ev != null)
      },
      test("difference with empty removal set") {
        // ("a", "b") \ () = ("a", "b")
        val ev = implicitly[Difference.Aux["a" :: "b" :: TNil, TNil, "a" :: "b" :: TNil]]
        assertTrue(ev != null)
      },
      test("some elements removed") {
        // ("a", "b", "c") \ ("b") = ("a", "c")
        val ev = implicitly[Difference.Aux["a" :: "b" :: "c" :: TNil, "b" :: TNil, "a" :: "c" :: TNil]]
        assertTrue(ev != null)
      },
      test("no elements removed (disjoint)") {
        // ("a", "b") \ ("c", "d") = ("a", "b")
        val ev = implicitly[Difference.Aux["a" :: "b" :: TNil, "c" :: "d" :: TNil, "a" :: "b" :: TNil]]
        assertTrue(ev != null)
      },
      test("all elements removed") {
        // ("a") \ ("a") = ()
        val ev = implicitly[Difference.Aux["a" :: TNil, "a" :: TNil, TNil]]
        assertTrue(ev != null)
      },
      test("multiple elements removed") {
        // ("a", "b", "c", "d") \ ("b", "d") = ("a", "c")
        val ev = implicitly[Difference.Aux["a" :: "b" :: "c" :: "d" :: TNil, "b" :: "d" :: TNil, "a" :: "c" :: TNil]]
        assertTrue(ev != null)
      }
    ),
    suite("Intersect")(
      test("intersect with empty - left") {
        val ev = implicitly[Intersect.Aux[TNil, "a" :: "b" :: TNil, TNil]]
        assertTrue(ev != null)
      },
      test("intersect with empty - right") {
        val ev = implicitly[Intersect.Aux["a" :: "b" :: TNil, TNil, TNil]]
        assertTrue(ev != null)
      },
      test("some common elements") {
        // ("a", "b") ∩ ("b", "c") = ("b")
        val ev = implicitly[Intersect.Aux["a" :: "b" :: TNil, "b" :: "c" :: TNil, "b" :: TNil]]
        assertTrue(ev != null)
      },
      test("no common elements") {
        // ("a") ∩ ("b") = ()
        val ev = implicitly[Intersect.Aux["a" :: TNil, "b" :: TNil, TNil]]
        assertTrue(ev != null)
      },
      test("all common elements (identical lists)") {
        // ("a", "b") ∩ ("a", "b") = ("a", "b")
        val ev = implicitly[Intersect.Aux["a" :: "b" :: TNil, "a" :: "b" :: TNil, "a" :: "b" :: TNil]]
        assertTrue(ev != null)
      },
      test("partial overlap") {
        // ("a", "b", "c") ∩ ("b", "c", "d") = ("b", "c")
        val ev = implicitly[Intersect.Aux["a" :: "b" :: "c" :: TNil, "b" :: "c" :: "d" :: TNil, "b" :: "c" :: TNil]]
        assertTrue(ev != null)
      }
    ),
    suite("Union")(
      test("union with empty - right") {
        // ("a", "b") ∪ () = ("a", "b")
        val ev = implicitly[Union.Aux["a" :: "b" :: TNil, TNil, "a" :: "b" :: TNil]]
        assertTrue(ev != null)
      },
      test("union of disjoint sets") {
        // ("a") ∪ ("b") - result should contain both
        // Note: Union adds to front, so ("a") ∪ ("b") = ("b", "a")
        val ev = implicitly[Union.Aux["a" :: TNil, "b" :: TNil, "b" :: "a" :: TNil]]
        assertTrue(ev != null)
      },
      test("union of overlapping sets skips duplicates") {
        // ("a", "b") ∪ ("b", "c") - "b" already in left, so skip it
        // Result: ("c", "a", "b") - "c" is added to front
        val ev = implicitly[Union.Aux["a" :: "b" :: TNil, "b" :: "c" :: TNil, "c" :: "a" :: "b" :: TNil]]
        assertTrue(ev != null)
      },
      test("union of identical sets") {
        // ("a", "b") ∪ ("a", "b") = ("a", "b") - all elements already present
        val ev = implicitly[Union.Aux["a" :: "b" :: TNil, "a" :: "b" :: TNil, "a" :: "b" :: TNil]]
        assertTrue(ev != null)
      }
    ),
    suite("Size")(
      test("empty list has size 0") {
        val size = implicitly[Size[TNil]]
        assertTrue(size.value == 0)
      },
      test("single element has size 1") {
        val size = implicitly[Size["a" :: TNil]]
        assertTrue(size.value == 1)
      },
      test("two elements has size 2") {
        val size = implicitly[Size["a" :: "b" :: TNil]]
        assertTrue(size.value == 2)
      },
      test("three elements has size 3") {
        val size = implicitly[Size["a" :: "b" :: "c" :: TNil]]
        assertTrue(size.value == 3)
      }
    ),
    suite("TListEquals")(
      test("identical lists are equal") {
        implicitly[TListEquals["a" :: "b" :: TNil, "a" :: "b" :: TNil]]
        assertTrue(true)
      },
      test("same elements different order are equal") {
        implicitly[TListEquals["a" :: "b" :: TNil, "b" :: "a" :: TNil]]
        assertTrue(true)
      },
      test("different lists are not equal") {
        typeCheck {
          """
          import zio.blocks.schema.migration.TypeLevel._
          implicitly[TListEquals["a" :: "b" :: TNil, "a" :: "c" :: TNil]]
          """
        }.map(assert(_)(isLeft))
      },
      test("subset is not equal to superset") {
        typeCheck {
          """
          import zio.blocks.schema.migration.TypeLevel._
          implicitly[TListEquals["a" :: TNil, "a" :: "b" :: TNil]]
          """
        }.map(assert(_)(isLeft))
      },
      test("empty lists are equal") {
        implicitly[TListEquals[TNil, TNil]]
        assertTrue(true)
      }
    ),
    suite("Type aliases")(
      test("TList1 creates single-element list") {
        implicitly[TList1["a"] =:= ("a" :: TNil)]
        assertTrue(true)
      },
      test("TList2 creates two-element list") {
        implicitly[TList2["a", "b"] =:= ("a" :: "b" :: TNil)]
        assertTrue(true)
      },
      test("TList3 creates three-element list") {
        implicitly[TList3["a", "b", "c"] =:= ("a" :: "b" :: "c" :: TNil)]
        assertTrue(true)
      }
    ),
    suite("Type inequality (=:!=)")(
      test("different types are unequal") {
        implicitly["a" =:!= "b"]
        assertTrue(true)
      },
      test("same types should not have inequality evidence") {
        typeCheck {
          """
          import zio.blocks.schema.migration.TypeLevel._
          implicitly["a" =:!= "a"]
          """
        }.map(assert(_)(isLeft))
      }
    )
  )
}
