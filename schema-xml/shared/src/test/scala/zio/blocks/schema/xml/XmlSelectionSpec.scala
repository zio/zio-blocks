package zio.blocks.schema.xml

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, SchemaBaseSpec}
import zio.test._

object XmlSelectionSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("XmlSelectionSpec")(
    suite("constructors and basic operations")(
      test("succeed creates successful selection with single value") {
        val xml       = Xml.Element("test")
        val selection = XmlSelection.succeed(xml)
        assertTrue(
          selection.isSuccess,
          !selection.isFailure,
          selection.error.isEmpty,
          selection.values.isDefined,
          selection.size == 1
        )
      },
      test("succeedMany creates successful selection with multiple values") {
        val xmls      = Chunk(Xml.Element("a"), Xml.Element("b"))
        val selection = XmlSelection.succeedMany(xmls)
        assertTrue(
          selection.isSuccess,
          selection.size == 2
        )
      },
      test("fail creates failed selection") {
        val selection = XmlSelection.fail(XmlError("test error"))
        assertTrue(
          !selection.isSuccess,
          selection.isFailure,
          selection.error.isDefined,
          selection.values.isEmpty,
          selection.size == 0
        )
      },
      test("empty is successful but contains no values") {
        val selection = XmlSelection.empty
        assertTrue(
          selection.isSuccess,
          selection.isEmpty,
          selection.size == 0
        )
      }
    ),
    suite("terminal operations")(
      test("one returns single value when exactly one exists") {
        val xml    = Xml.Element("test")
        val result = XmlSelection.succeed(xml).one
        assertTrue(
          result.isRight,
          result == Right(xml)
        )
      },
      test("one fails when selection is empty") {
        val result = XmlSelection.empty.one
        assertTrue(result.isLeft)
      },
      test("one fails when selection has multiple values") {
        val selection = XmlSelection.succeedMany(Chunk(Xml.Element("a"), Xml.Element("b")))
        val result    = selection.one
        assertTrue(result.isLeft)
      },
      test("any returns first value when values exist") {
        val xml1   = Xml.Element("a")
        val xml2   = Xml.Element("b")
        val result = XmlSelection.succeedMany(Chunk(xml1, xml2)).any
        assertTrue(
          result.isRight,
          result == Right(xml1)
        )
      },
      test("any fails when selection is empty") {
        val result = XmlSelection.empty.any
        assertTrue(result.isLeft)
      },
      test("all returns single value when one exists") {
        val xml    = Xml.Element("test")
        val result = XmlSelection.succeed(xml).all
        assertTrue(
          result.isRight,
          result == Right(xml)
        )
      },
      test("all wraps multiple values in element") {
        val xml1   = Xml.Element("a")
        val xml2   = Xml.Element("b")
        val result = XmlSelection.succeedMany(Chunk(xml1, xml2)).all
        assertTrue(result.isRight)
        result match {
          case Right(Xml.Element(name, _, children)) =>
            assertTrue(
              name.localName == "root",
              children == Chunk(xml1, xml2)
            )
          case _ => assertTrue(false)
        }
      },
      test("all fails when selection is empty") {
        val result = XmlSelection.empty.all
        assertTrue(result.isLeft)
      },
      test("toArray wraps values in element") {
        val xml1   = Xml.Element("a")
        val xml2   = Xml.Element("b")
        val result = XmlSelection.succeedMany(Chunk(xml1, xml2)).toArray
        assertTrue(result.isRight)
        result match {
          case Right(Xml.Element(name, _, children)) =>
            assertTrue(
              name.localName == "root",
              children == Chunk(xml1, xml2)
            )
          case _ => assertTrue(false)
        }
      }
    ),
    suite("size operations")(
      test("isEmpty returns true for empty selection") {
        assertTrue(XmlSelection.empty.isEmpty)
      },
      test("isEmpty returns true for failed selection") {
        assertTrue(XmlSelection.fail(XmlError("error")).isEmpty)
      },
      test("isEmpty returns false for non-empty selection") {
        assertTrue(!XmlSelection.succeed(Xml.Element("test")).isEmpty)
      },
      test("nonEmpty returns true for non-empty selection") {
        assertTrue(XmlSelection.succeed(Xml.Element("test")).nonEmpty)
      },
      test("nonEmpty returns false for empty selection") {
        assertTrue(!XmlSelection.empty.nonEmpty)
      },
      test("size returns correct count") {
        assertTrue(
          XmlSelection.empty.size == 0,
          XmlSelection.succeed(Xml.Element("test")).size == 1,
          XmlSelection.succeedMany(Chunk(Xml.Element("a"), Xml.Element("b"))).size == 2
        )
      }
    ),
    suite("type filtering")(
      test("elements keeps only element nodes") {
        val selection = XmlSelection.succeedMany(
          Chunk(
            Xml.Element("a"),
            Xml.Text("text"),
            Xml.Element("b"),
            Xml.Comment("comment")
          )
        )
        val filtered = selection.elements
        assertTrue(
          filtered.size == 2,
          filtered.toChunk.forall(_.xmlType == XmlType.Element)
        )
      },
      test("texts keeps only text nodes") {
        val selection = XmlSelection.succeedMany(
          Chunk(
            Xml.Element("a"),
            Xml.Text("text1"),
            Xml.Text("text2"),
            Xml.Comment("comment")
          )
        )
        val filtered = selection.texts
        assertTrue(
          filtered.size == 2,
          filtered.toChunk.forall(_.xmlType == XmlType.Text)
        )
      },
      test("cdatas keeps only CDATA nodes") {
        val selection = XmlSelection.succeedMany(
          Chunk(
            Xml.Text("text"),
            Xml.CData("data1"),
            Xml.CData("data2")
          )
        )
        val filtered = selection.cdatas
        assertTrue(
          filtered.size == 2,
          filtered.toChunk.forall(_.xmlType == XmlType.CData)
        )
      },
      test("comments keeps only comment nodes") {
        val selection = XmlSelection.succeedMany(
          Chunk(
            Xml.Element("a"),
            Xml.Comment("comment1"),
            Xml.Comment("comment2")
          )
        )
        val filtered = selection.comments
        assertTrue(
          filtered.size == 2,
          filtered.toChunk.forall(_.xmlType == XmlType.Comment)
        )
      },
      test("processingInstructions keeps only PI nodes") {
        val selection = XmlSelection.succeedMany(
          Chunk(
            Xml.ProcessingInstruction("target1", "data1"),
            Xml.Element("a"),
            Xml.ProcessingInstruction("target2", "data2")
          )
        )
        val filtered = selection.processingInstructions
        assertTrue(
          filtered.size == 2,
          filtered.toChunk.forall(_.xmlType == XmlType.ProcessingInstruction)
        )
      }
    ),
    suite("navigation")(
      test("get(name) finds child elements by name") {
        val child1 = Xml.Element("child")
        val child2 = Xml.Element("other")
        val parent = Xml.Element(
          XmlName("parent"),
          Chunk.empty,
          Chunk(child1, Xml.Text("text"), child2)
        )
        val selection = XmlSelection.succeed(parent).get("child")
        assertTrue(
          selection.size == 1,
          selection.toChunk.head == child1
        )
      },
      test("get(name) returns empty when no matching children") {
        val parent    = Xml.Element("parent", Xml.Text("text"))
        val selection = XmlSelection.succeed(parent).get("nonexistent")
        assertTrue(selection.isEmpty)
      },
      test("apply(index) gets nth child") {
        val child1 = Xml.Text("first")
        val child2 = Xml.Element("second")
        val child3 = Xml.Text("third")
        val parent = Xml.Element(
          XmlName("parent"),
          Chunk.empty,
          Chunk(child1, child2, child3)
        )
        val selection = XmlSelection.succeed(parent)(1)
        assertTrue(
          selection.size == 1,
          selection.toChunk.head == child2
        )
      },
      test("apply(index) returns empty when index out of bounds") {
        val parent    = Xml.Element("parent", Xml.Text("text"))
        val selection = XmlSelection.succeed(parent)(5)
        assertTrue(selection.isEmpty)
      },
      test("get(DynamicOptic) navigates using field") {
        val child  = Xml.Element("child")
        val parent = Xml.Element("parent", child)
        val path   = DynamicOptic.root.field("child")
        val result = XmlSelection.succeed(parent).get(path)
        assertTrue(
          result.size == 1,
          result.toChunk.head == child
        )
      },
      test("get(DynamicOptic) navigates using index") {
        val child1 = Xml.Element("first")
        val child2 = Xml.Element("second")
        val parent = Xml.Element("parent", child1, child2)
        val path   = DynamicOptic.root.at(0)
        val result = XmlSelection.succeed(parent).get(path)
        assertTrue(
          result.size == 1,
          result.toChunk.head == child1
        )
      },
      test("get(DynamicOptic) navigates complex paths") {
        val grandchild = Xml.Element("grandchild")
        val child      = Xml.Element("child", grandchild)
        val parent     = Xml.Element("parent", child)
        val path       = DynamicOptic.root.field("child").field("grandchild")
        val result     = XmlSelection.succeed(parent).get(path)
        assertTrue(
          result.size == 1,
          result.toChunk.head == grandchild
        )
      }
    ),
    suite("combinators")(
      test("map transforms all values") {
        val selection = XmlSelection.succeedMany(
          Chunk(Xml.Text("a"), Xml.Text("b"))
        )
        val mapped = selection.map {
          case Xml.Text(value) => Xml.Text(value.toUpperCase)
          case other           => other
        }
        assertTrue(
          mapped.size == 2,
          mapped.toChunk(0) == Xml.Text("A"),
          mapped.toChunk(1) == Xml.Text("B")
        )
      },
      test("flatMap combines results") {
        val elem1     = Xml.Element("parent1", Xml.Element("child1"))
        val elem2     = Xml.Element("parent2", Xml.Element("child2"))
        val selection = XmlSelection
          .succeedMany(Chunk(elem1, elem2))
          .flatMap(xml => XmlSelection.succeed(xml).get("child1"))
        assertTrue(selection.size == 1)
      },
      test("filter keeps matching values") {
        val selection = XmlSelection.succeedMany(
          Chunk(Xml.Element("a"), Xml.Text("text"), Xml.Element("b"))
        )
        val filtered = selection.filter(_.xmlType == XmlType.Element)
        assertTrue(
          filtered.size == 2,
          filtered.toChunk.forall(_.xmlType == XmlType.Element)
        )
      },
      test("orElse returns first if successful") {
        val first  = XmlSelection.succeed(Xml.Element("first"))
        val second = XmlSelection.succeed(Xml.Element("second"))
        val result = first.orElse(second)
        assertTrue(
          result.size == 1,
          result.toChunk.head == Xml.Element("first")
        )
      },
      test("orElse returns alternative if first fails") {
        val first  = XmlSelection.fail(XmlError("error"))
        val second = XmlSelection.succeed(Xml.Element("second"))
        val result = first.orElse(second)
        assertTrue(
          result.size == 1,
          result.toChunk.head == Xml.Element("second")
        )
      },
      test("++ concatenates selections") {
        val sel1   = XmlSelection.succeed(Xml.Element("a"))
        val sel2   = XmlSelection.succeed(Xml.Element("b"))
        val result = sel1 ++ sel2
        assertTrue(
          result.size == 2,
          result.toChunk(0) == Xml.Element("a"),
          result.toChunk(1) == Xml.Element("b")
        )
      },
      test("++ propagates errors correctly") {
        val err1 = XmlSelection.fail(XmlError("error1"))
        val err2 = XmlSelection.fail(XmlError("error2"))
        val sel  = XmlSelection.succeed(Xml.Element("a"))

        assertTrue(
          (err1 ++ sel).isFailure,
          (sel ++ err1).isFailure,
          (err1 ++ err2).isFailure
        )
      }
    ),
    suite("type-directed extraction")(
      test("as narrows to specific type when single value matches") {
        val elem      = Xml.Element("test")
        val selection = XmlSelection.succeed(elem)
        val result    = selection.as(XmlType.Element)
        assertTrue(result.isRight)
        result match {
          case Right(e: Xml.Element) => assertTrue(e == elem)
          case _                     => assertTrue(false)
        }
      },
      test("as fails when type doesn't match") {
        val text      = Xml.Text("test")
        val selection = XmlSelection.succeed(text)
        val result    = selection.as(XmlType.Element)
        assertTrue(result.isLeft)
      },
      test("as fails when selection is empty") {
        val result = XmlSelection.empty.as(XmlType.Element)
        assertTrue(result.isLeft)
      },
      test("as fails when selection has multiple values") {
        val selection = XmlSelection.succeedMany(Chunk(Xml.Element("a"), Xml.Element("b")))
        val result    = selection.as(XmlType.Element)
        assertTrue(result.isLeft)
      },
      test("unwrap extracts underlying value when single value matches") {
        val text      = Xml.Text("content")
        val selection = XmlSelection.succeed(text)
        val result    = selection.unwrap(XmlType.Text)
        assertTrue(
          result.isRight,
          result == Right("content")
        )
      },
      test("unwrap extracts element components") {
        val elem = Xml.Element(
          XmlName("test"),
          Chunk((XmlName("attr"), "value")),
          Chunk(Xml.Text("child"))
        )
        val selection = XmlSelection.succeed(elem)
        val result    = selection.unwrap(XmlType.Element)
        assertTrue(result.isRight)
        result match {
          case Right((name, attrs, children)) =>
            assertTrue(
              name.localName == "test",
              attrs.length == 1,
              children.length == 1
            )
          case _ => assertTrue(false)
        }
      },
      test("unwrap fails when type doesn't match") {
        val text      = Xml.Text("test")
        val selection = XmlSelection.succeed(text)
        val result    = selection.unwrap(XmlType.Element)
        assertTrue(result.isLeft)
      }
    ),
    suite("edge cases and error handling")(
      test("operations on failed selection propagate error") {
        val failed = XmlSelection.fail(XmlError("initial error"))
        assertTrue(
          failed.map(identity).isFailure,
          failed.filter(_ => true).isFailure,
          failed.get("test").isFailure,
          failed(0).isFailure
        )
      },
      test("flatMap short-circuits on first error") {
        val selection = XmlSelection.succeedMany(Chunk(Xml.Element("a"), Xml.Element("b")))
        var callCount = 0
        val result    = selection.flatMap { _ =>
          callCount += 1
          if (callCount == 1) XmlSelection.fail(XmlError("error"))
          else XmlSelection.succeed(Xml.Element("x"))
        }
        assertTrue(
          result.isFailure,
          callCount == 1
        )
      },
      test("toChunk returns empty chunk on failure") {
        val failed = XmlSelection.fail(XmlError("error"))
        assertTrue(failed.toChunk.isEmpty)
      },
      test("chaining operations maintains type safety") {
        val container = Xml.Element(
          "container",
          Xml.Text("text"),
          Xml.Element("item1", Xml.Text("value1")),
          Xml.Element("item2", Xml.Text("value2")),
          Xml.Comment("comment")
        )
        val result = XmlSelection
          .succeed(container)
          .flatMap(xml => XmlSelection.succeedMany(xml.as(XmlType.Element).map(_.children).getOrElse(Chunk.empty)))
          .elements
          .filter(_.as(XmlType.Element).exists(_.name.localName.startsWith("item")))
          .size

        assertTrue(result == 2)
      }
    )
  )
}
