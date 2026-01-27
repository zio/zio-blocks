package zio.blocks.typeid

import zio.test._

/**
 * Comprehensive tests for the Owner data structure and its operations. Covers
 * all methods: parent, isRoot, asString, isPrefixOf, toString, parse, pkgs.
 */
object OwnerSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("OwnerSpec")(
    suite("Owner construction")(
      test("Root owner has no segments") {
        assertTrue(Owner.Root.segments.isEmpty)
      },
      test("pkg creates single package segment") {
        val owner = Owner.pkg("scala")
        assertTrue(
          owner.segments.size == 1,
          owner.segments.head == Owner.Package("scala")
        )
      },
      test("pkgs creates multiple package segments") {
        val owner = Owner.pkgs("java", "lang")
        assertTrue(
          owner.segments.size == 2,
          owner.segments == List(Owner.Package("java"), Owner.Package("lang"))
        )
      },
      test("parse creates owner from dot-separated string") {
        val owner = Owner.parse("zio.blocks.typeid")
        assertTrue(
          owner.segments.size == 3,
          owner.segments == List(
            Owner.Package("zio"),
            Owner.Package("blocks"),
            Owner.Package("typeid")
          )
        )
      },
      test("parse empty string returns Root") {
        val owner = Owner.parse("")
        assertTrue(owner == Owner.Root)
      }
    ),
    suite("Owner.parent")(
      test("parent of Root is None") {
        assertTrue(Owner.Root.parent.isEmpty)
      },
      test("parent of single-segment owner is Root") {
        val owner = Owner.pkg("scala")
        assertTrue(owner.parent.contains(Owner.Root))
      },
      test("parent of multi-segment owner drops last segment") {
        val owner    = Owner.pkgs("java", "lang", "String")
        val expected = Owner.pkgs("java", "lang")
        assertTrue(owner.parent.contains(expected))
      }
    ),
    suite("Owner.isRoot")(
      test("Root is root") {
        assertTrue(Owner.Root.isRoot)
      },
      test("non-empty owner is not root") {
        assertTrue(!Owner.pkg("scala").isRoot)
      }
    ),
    suite("Owner.asString")(
      test("Root asString is empty") {
        assertTrue(Owner.Root.asString == "")
      },
      test("single segment asString is segment name") {
        assertTrue(Owner.pkg("scala").asString == "scala")
      },
      test("multiple segments asString is dot-separated") {
        val owner = Owner.pkgs("java", "time")
        assertTrue(owner.asString == "java.time")
      }
    ),
    suite("Owner.toString")(
      test("toString equals asString") {
        val owner = Owner.pkgs("zio", "blocks")
        assertTrue(owner.toString == owner.asString)
      }
    ),
    suite("Owner.isPrefixOf")(
      test("Root is prefix of everything") {
        val owner = Owner.pkgs("scala", "collection", "immutable")
        assertTrue(Owner.Root.isPrefixOf(owner))
      },
      test("owner is prefix of itself") {
        val owner = Owner.pkgs("java", "lang")
        assertTrue(owner.isPrefixOf(owner))
      },
      test("parent is prefix of child") {
        val parent = Owner.pkgs("java", "lang")
        val child  = Owner.pkgs("java", "lang", "String")
        assertTrue(parent.isPrefixOf(child))
      },
      test("child is not prefix of parent") {
        val parent = Owner.pkgs("java", "lang")
        val child  = Owner.pkgs("java", "lang", "String")
        assertTrue(!child.isPrefixOf(parent))
      },
      test("unrelated owners are not prefixes") {
        val owner1 = Owner.pkgs("java", "time")
        val owner2 = Owner.pkgs("scala", "math")
        assertTrue(!owner1.isPrefixOf(owner2))
      }
    ),
    suite("Owner./ operator")(
      test("appending segment to Root") {
        val owner = Owner.Root / Owner.Package("scala")
        assertTrue(owner == Owner.pkg("scala"))
      },
      test("appending Term segment") {
        val owner = Owner.pkg("scala") / Owner.Term("Predef")
        assertTrue(
          owner.segments.size == 2,
          owner.segments(1) == Owner.Term("Predef")
        )
      },
      test("appending Type segment") {
        val owner = Owner.pkg("scala") / Owner.Type("Int")
        assertTrue(
          owner.segments.size == 2,
          owner.segments(1) == Owner.Type("Int")
        )
      },
      test("appending Local segment") {
        val local = Owner.Local(0)
        val owner = Owner.pkg("test") / local
        assertTrue(
          owner.segments.size == 2,
          local.name == "<local0>"
        )
      }
    ),
    suite("Segment.show")(
      test("Package segment shows name") {
        val seg: Owner.Segment = Owner.Package("scala")
        assertTrue(seg.show == "scala")
      },
      test("Term segment shows name") {
        val seg: Owner.Segment = Owner.Term("foo")
        assertTrue(seg.show == "foo")
      },
      test("Type segment shows name") {
        val seg: Owner.Segment = Owner.Type("Bar")
        assertTrue(seg.show == "Bar")
      },
      test("Local segment shows formatted name") {
        val seg: Owner.Segment = Owner.Local(5)
        assertTrue(seg.show == "<local5>")
      }
    )
  )
}
