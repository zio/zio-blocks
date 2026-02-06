package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object SelectorCoverageSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("SelectorCoverageSpec")(
    suite("Root selector")(
      test("toOptic returns root optic") {
        val sel = Selector.root[String]
        assertTrue(sel.toOptic.nodes.isEmpty)
      },
      test("Root equals itself") {
        assertTrue(Selector.Root[Int]() == Selector.Root[Int]())
      }
    ),
    suite("Field selector")(
      test("toOptic returns single field") {
        val sel   = Selector.field[Any, String]("name")
        val optic = sel.toOptic
        assertTrue(optic.nodes.length == 1 && optic.nodes.head == DynamicOptic.Node.Field("name"))
      },
      test("Field equality") {
        assertTrue(Selector.Field[Any, String]("x") == Selector.Field[Any, String]("x"))
      }
    ),
    suite("Composed selector")(
      test("composes two selectors") {
        val sel1     = Selector.field[Any, Any]("person")
        val sel2     = Selector.field[Any, String]("name")
        val composed = sel1.andThen(sel2)
        val optic    = composed.toOptic
        assertTrue(
          optic.nodes.length == 2 &&
            optic.nodes(0) == DynamicOptic.Node.Field("person") &&
            optic.nodes(1) == DynamicOptic.Node.Field("name")
        )
      },
      test(">>> is alias for andThen") {
        val sel1     = Selector.field[Any, Any]("a")
        val sel2     = Selector.field[Any, String]("b")
        val composed = sel1 >>> sel2
        assertTrue(composed.toOptic.nodes.length == 2)
      },
      test("three-level composition") {
        val s1       = Selector.field[Any, Any]("a")
        val s2       = Selector.field[Any, Any]("b")
        val s3       = Selector.field[Any, String]("c")
        val composed = (s1 >>> s2) >>> s3
        assertTrue(composed.toOptic.nodes.length == 3)
      }
    ),
    suite("Elements selector")(
      test("toOptic returns elements node") {
        val sel   = Selector.elements[Int, Int]
        val optic = sel.toOptic
        assertTrue(optic.nodes.length == 1 && optic.nodes.head == DynamicOptic.Node.Elements)
      },
      test("Elements equality") {
        assertTrue(Selector.Elements[Int, Int]() == Selector.Elements[Int, Int]())
      }
    ),
    suite("MapKeys selector")(
      test("toOptic returns mapKeys node") {
        val sel   = Selector.mapKeys[String, Int]
        val optic = sel.toOptic
        assertTrue(optic.nodes.length == 1 && optic.nodes.head == DynamicOptic.Node.MapKeys)
      },
      test("MapKeys equality") {
        assertTrue(Selector.MapKeys[String, Int]() == Selector.MapKeys[String, Int]())
      }
    ),
    suite("MapValues selector")(
      test("toOptic returns mapValues node") {
        val sel   = Selector.mapValues[String, Int]
        val optic = sel.toOptic
        assertTrue(optic.nodes.length == 1 && optic.nodes.head == DynamicOptic.Node.MapValues)
      },
      test("MapValues equality") {
        assertTrue(Selector.MapValues[String, Int]() == Selector.MapValues[String, Int]())
      }
    ),
    suite("Optional selector")(
      test("Optional wraps inner selector") {
        val inner = Selector.field[String, Int]("age")
        val opt   = Selector.Optional(inner)
        assertTrue(opt.toOptic.nodes.length == 1 && opt.toOptic.nodes.head == DynamicOptic.Node.Field("age"))
      },
      test("Optional preserves composed path") {
        val inner = Selector.field[Any, Any]("a").andThen(Selector.field[Any, String]("b"))
        val opt   = Selector.Optional(inner)
        assertTrue(opt.toOptic.nodes.length == 2)
      }
    ),
    suite("Composed with special selectors")(
      test("field then elements") {
        val sel   = Selector.field[Any, Seq[Int]]("items").andThen(Selector.elements[Int, Int])
        val optic = sel.toOptic
        assertTrue(
          optic.nodes.length == 2 &&
            optic.nodes(0) == DynamicOptic.Node.Field("items") &&
            optic.nodes(1) == DynamicOptic.Node.Elements
        )
      },
      test("field then mapKeys") {
        val sel   = Selector.field[Any, Map[String, Int]]("data").andThen(Selector.mapKeys[String, Int])
        val optic = sel.toOptic
        assertTrue(
          optic.nodes.length == 2 &&
            optic.nodes(1) == DynamicOptic.Node.MapKeys
        )
      },
      test("field then mapValues") {
        val sel   = Selector.field[Any, Map[String, Int]]("data").andThen(Selector.mapValues[String, Int])
        val optic = sel.toOptic
        assertTrue(
          optic.nodes.length == 2 &&
            optic.nodes(1) == DynamicOptic.Node.MapValues
        )
      }
    )
  )
}
