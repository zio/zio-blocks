package zio.blocks.schema.xml

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, SchemaBaseSpec}
import zio.test._

object XmlPatchSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("XmlPatchSpec")(
    suite("Add operation")(
      test("add element before target") {
        val xml   = Xml.Element("root", Xml.Element("a"), Xml.Element("b"))
        val patch = XmlPatch.add(
          DynamicOptic.root.field("a"),
          Xml.Element("new"),
          XmlPatch.Position.Before
        )
        val result = patch(xml)
        assertTrue(result.isRight)
        result match {
          case Right(Xml.Element(_, _, children)) =>
            assertTrue(
              children.length == 3,
              children(0).asInstanceOf[Xml.Element].name.localName == "new",
              children(1).asInstanceOf[Xml.Element].name.localName == "a",
              children(2).asInstanceOf[Xml.Element].name.localName == "b"
            )
          case _ => assertTrue(false)
        }
      },
      test("add element after target") {
        val xml   = Xml.Element("root", Xml.Element("a"), Xml.Element("b"))
        val patch = XmlPatch.add(
          DynamicOptic.root.field("a"),
          Xml.Element("new"),
          XmlPatch.Position.After
        )
        val result = patch(xml)
        assertTrue(result.isRight)
        result match {
          case Right(Xml.Element(_, _, children)) =>
            assertTrue(
              children.length == 3,
              children(0).asInstanceOf[Xml.Element].name.localName == "a",
              children(1).asInstanceOf[Xml.Element].name.localName == "new",
              children(2).asInstanceOf[Xml.Element].name.localName == "b"
            )
          case _ => assertTrue(false)
        }
      },
      test("add element as first child (prepend)") {
        val xml   = Xml.Element("root", Xml.Element("a"), Xml.Element("b"))
        val patch = XmlPatch.add(
          DynamicOptic.root,
          Xml.Element("new"),
          XmlPatch.Position.PrependChild
        )
        val result = patch(xml)
        assertTrue(result.isRight)
        result match {
          case Right(Xml.Element(_, _, children)) =>
            assertTrue(
              children.length == 3,
              children(0).asInstanceOf[Xml.Element].name.localName == "new",
              children(1).asInstanceOf[Xml.Element].name.localName == "a",
              children(2).asInstanceOf[Xml.Element].name.localName == "b"
            )
          case _ => assertTrue(false)
        }
      },
      test("add element as last child (append)") {
        val xml   = Xml.Element("root", Xml.Element("a"), Xml.Element("b"))
        val patch = XmlPatch.add(
          DynamicOptic.root,
          Xml.Element("new"),
          XmlPatch.Position.AppendChild
        )
        val result = patch(xml)
        assertTrue(result.isRight)
        result match {
          case Right(Xml.Element(_, _, children)) =>
            assertTrue(
              children.length == 3,
              children(0).asInstanceOf[Xml.Element].name.localName == "a",
              children(1).asInstanceOf[Xml.Element].name.localName == "b",
              children(2).asInstanceOf[Xml.Element].name.localName == "new"
            )
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Remove operation")(
      test("remove element by path") {
        val xml    = Xml.Element("root", Xml.Element("a"), Xml.Element("b"), Xml.Element("c"))
        val patch  = XmlPatch.remove(DynamicOptic.root.field("b"))
        val result = patch(xml)
        assertTrue(result.isRight)
        result match {
          case Right(Xml.Element(_, _, children)) =>
            assertTrue(
              children.length == 2,
              children(0).asInstanceOf[Xml.Element].name.localName == "a",
              children(1).asInstanceOf[Xml.Element].name.localName == "c"
            )
          case _ => assertTrue(false)
        }
      },
      test("remove fails when path not found") {
        val xml    = Xml.Element("root", Xml.Element("a"))
        val patch  = XmlPatch.remove(DynamicOptic.root.field("nonexistent"))
        val result = patch(xml)
        assertTrue(result.isLeft)
      }
    ),
    suite("Replace operation")(
      test("replace element with new content") {
        val xml   = Xml.Element("root", Xml.Element("a"), Xml.Element("b"))
        val patch = XmlPatch.replace(
          DynamicOptic.root.field("a"),
          Xml.Element("replaced")
        )
        val result = patch(xml)
        assertTrue(result.isRight)
        result match {
          case Right(Xml.Element(_, _, children)) =>
            assertTrue(
              children.length == 2,
              children(0).asInstanceOf[Xml.Element].name.localName == "replaced",
              children(1).asInstanceOf[Xml.Element].name.localName == "b"
            )
          case _ => assertTrue(false)
        }
      },
      test("replace fails when path not found") {
        val xml   = Xml.Element("root", Xml.Element("a"))
        val patch = XmlPatch.replace(
          DynamicOptic.root.field("nonexistent"),
          Xml.Element("new")
        )
        val result = patch(xml)
        assertTrue(result.isLeft)
      }
    ),
    suite("SetAttribute operation")(
      test("set attribute on element") {
        val xml    = Xml.Element("root", Xml.Element("a"))
        val patch  = XmlPatch.setAttribute(DynamicOptic.root.field("a"), "id", "123")
        val result = patch(xml)
        assertTrue(result.isRight)
        result match {
          case Right(Xml.Element(_, _, children)) =>
            children.head match {
              case Xml.Element(_, attrs, _) =>
                assertTrue(
                  attrs.exists { case (name, value) =>
                    name.localName == "id" && value == "123"
                  }
                )
              case _ => assertTrue(false)
            }
          case _ => assertTrue(false)
        }
      },
      test("set attribute replaces existing attribute") {
        val xml = Xml.Element(
          XmlName("root"),
          Chunk.empty,
          Chunk(Xml.Element(XmlName("a"), Chunk((XmlName("id"), "old")), Chunk.empty))
        )
        val patch  = XmlPatch.setAttribute(DynamicOptic.root.field("a"), "id", "new")
        val result = patch(xml)
        assertTrue(result.isRight)
        result match {
          case Right(Xml.Element(_, _, children)) =>
            children.head match {
              case Xml.Element(_, attrs, _) =>
                assertTrue(
                  attrs.length == 1,
                  attrs.exists { case (name, value) =>
                    name.localName == "id" && value == "new"
                  }
                )
              case _ => assertTrue(false)
            }
          case _ => assertTrue(false)
        }
      }
    ),
    suite("RemoveAttribute operation")(
      test("remove attribute from element") {
        val xml = Xml.Element(
          XmlName("root"),
          Chunk.empty,
          Chunk(Xml.Element(XmlName("a"), Chunk((XmlName("id"), "123")), Chunk.empty))
        )
        val patch  = XmlPatch.removeAttribute(DynamicOptic.root.field("a"), "id")
        val result = patch(xml)
        assertTrue(result.isRight)
        result match {
          case Right(Xml.Element(_, _, children)) =>
            children.head match {
              case Xml.Element(_, attrs, _) =>
                assertTrue(attrs.isEmpty)
              case _ => assertTrue(false)
            }
          case _ => assertTrue(false)
        }
      },
      test("remove attribute fails when attribute not found") {
        val xml    = Xml.Element("root", Xml.Element("a"))
        val patch  = XmlPatch.removeAttribute(DynamicOptic.root.field("a"), "nonexistent")
        val result = patch(xml)
        assertTrue(result.isLeft)
      }
    ),
    suite("Composite patches")(
      test("apply multiple operations in sequence") {
        val xml   = Xml.Element("root", Xml.Element("a"), Xml.Element("b"))
        val patch = XmlPatch.add(
          DynamicOptic.root,
          Xml.Element("c"),
          XmlPatch.Position.AppendChild
        ) ++ XmlPatch.setAttribute(DynamicOptic.root.field("a"), "id", "1")
        val result = patch(xml)
        assertTrue(result.isRight)
        result match {
          case Right(Xml.Element(_, _, resultChildren)) =>
            assertTrue(
              resultChildren.length == 3,
              resultChildren(2).asInstanceOf[Xml.Element].name.localName == "c"
            )
            resultChildren.head match {
              case Xml.Element(_, resultAttrs, _) =>
                assertTrue(resultAttrs.exists { case (name, value) =>
                  name.localName == "id" && value == "1"
                })
              case _ => assertTrue(false)
            }
          case _ => assertTrue(false)
        }
      },
      test("empty patch is identity") {
        val xml    = Xml.Element("root", Xml.Element("a"))
        val result = XmlPatch.empty.apply(xml)
        assertTrue(
          result.isRight,
          result == Right(xml)
        )
      }
    ),
    suite("Error handling")(
      test("add fails when target path not found") {
        val xml   = Xml.Element("root", Xml.Element("a"))
        val patch = XmlPatch.add(
          DynamicOptic.root.field("nonexistent"),
          Xml.Element("new"),
          XmlPatch.Position.After
        )
        val result = patch(xml)
        assertTrue(result.isLeft)
      },
      test("setAttribute fails when target is not an element") {
        val xml    = Xml.Text("test")
        val patch  = XmlPatch.setAttribute(DynamicOptic.root, "id", "123")
        val result = patch(xml)
        assertTrue(result.isLeft)
      },
      test("add PrependChild fails when target is not an element") {
        val xml    = Xml.Text("test")
        val patch  = XmlPatch.add(DynamicOptic.root, Xml.Element("new"), XmlPatch.Position.PrependChild)
        val result = patch(xml)
        assertTrue(result.isLeft)
      },
      test("add Before fails when applied at root (no parent context)") {
        val xml    = Xml.Element("root")
        val patch  = XmlPatch.add(DynamicOptic.root, Xml.Element("new"), XmlPatch.Position.Before)
        val result = patch(xml)
        assertTrue(result.isLeft)
      },
      test("remove fails when applied at root (no parent context)") {
        val xml    = Xml.Element("root")
        val patch  = XmlPatch.remove(DynamicOptic.root)
        val result = patch(xml)
        assertTrue(result.isLeft)
      },
      test("removeAttribute fails when target is not an element") {
        val xml    = Xml.Text("test")
        val patch  = XmlPatch.removeAttribute(DynamicOptic.root, "id")
        val result = patch(xml)
        assertTrue(result.isLeft)
      }
    ),
    suite("AtIndex navigation")(
      test("navigate by index and replace child") {
        val xml    = Xml.Element("root", Xml.Element("a"), Xml.Element("b"), Xml.Element("c"))
        val patch  = XmlPatch.replace(DynamicOptic.root.at(1), Xml.Element("replaced"))
        val result = patch(xml)
        assertTrue(result.isRight)
        result match {
          case Right(Xml.Element(_, _, children)) =>
            assertTrue(
              children.length == 3,
              children(1).asInstanceOf[Xml.Element].name.localName == "replaced"
            )
          case _ => assertTrue(false)
        }
      },
      test("add before element at index") {
        val xml    = Xml.Element("root", Xml.Element("a"), Xml.Element("b"))
        val patch  = XmlPatch.add(DynamicOptic.root.at(1), Xml.Element("new"), XmlPatch.Position.Before)
        val result = patch(xml)
        assertTrue(result.isRight)
        result match {
          case Right(Xml.Element(_, _, children)) =>
            assertTrue(
              children.length == 3,
              children(1).asInstanceOf[Xml.Element].name.localName == "new",
              children(2).asInstanceOf[Xml.Element].name.localName == "b"
            )
          case _ => assertTrue(false)
        }
      },
      test("add after element at index") {
        val xml    = Xml.Element("root", Xml.Element("a"), Xml.Element("b"))
        val patch  = XmlPatch.add(DynamicOptic.root.at(0), Xml.Element("new"), XmlPatch.Position.After)
        val result = patch(xml)
        assertTrue(result.isRight)
        result match {
          case Right(Xml.Element(_, _, children)) =>
            assertTrue(
              children.length == 3,
              children(1).asInstanceOf[Xml.Element].name.localName == "new"
            )
          case _ => assertTrue(false)
        }
      },
      test("remove element at index") {
        val xml    = Xml.Element("root", Xml.Element("a"), Xml.Element("b"), Xml.Element("c"))
        val patch  = XmlPatch.remove(DynamicOptic.root.at(1))
        val result = patch(xml)
        assertTrue(result.isRight)
        result match {
          case Right(Xml.Element(_, _, children)) =>
            assertTrue(
              children.length == 2,
              children(0).asInstanceOf[Xml.Element].name.localName == "a",
              children(1).asInstanceOf[Xml.Element].name.localName == "c"
            )
          case _ => assertTrue(false)
        }
      },
      test("index out of bounds error") {
        val xml    = Xml.Element("root", Xml.Element("a"))
        val patch  = XmlPatch.replace(DynamicOptic.root.at(5), Xml.Element("new"))
        val result = patch(xml)
        assertTrue(result.isLeft)
      },
      test("negative index error") {
        val xml    = Xml.Element("root", Xml.Element("a"))
        val patch  = XmlPatch.replace(DynamicOptic.root.at(-1), Xml.Element("new"))
        val result = patch(xml)
        assertTrue(result.isLeft)
      },
      test("nested AtIndex navigation") {
        val xml = Xml.Element(
          "root",
          Xml.Element("level1", Xml.Element("level2-a"), Xml.Element("level2-b")),
          Xml.Element("other")
        )
        val patch  = XmlPatch.replace(DynamicOptic.root.at(0).at(1), Xml.Element("replaced"))
        val result = patch(xml)
        assertTrue(result.isRight)
        result match {
          case Right(Xml.Element(_, _, children)) =>
            children.head match {
              case Xml.Element(_, _, nested) =>
                assertTrue(
                  nested.length == 2,
                  nested(1).asInstanceOf[Xml.Element].name.localName == "replaced"
                )
              case _ => assertTrue(false)
            }
          case _ => assertTrue(false)
        }
      },
      test("atIndex on non-element fails") {
        val xml    = Xml.Element("root", Xml.Text("text"))
        val patch  = XmlPatch.replace(DynamicOptic.root.at(0).at(0), Xml.Element("new"))
        val result = patch(xml)
        assertTrue(result.isLeft)
      }
    ),
    suite("Nested field navigation")(
      test("navigate nested fields and replace") {
        val xml = Xml.Element(
          "root",
          Xml.Element("level1", Xml.Element("level2", Xml.Text("old")))
        )
        val patch  = XmlPatch.replace(DynamicOptic.root.field("level1").field("level2"), Xml.Text("new"))
        val result = patch(xml)
        assertTrue(result.isRight)
        result match {
          case Right(Xml.Element(_, _, children)) =>
            children.head match {
              case Xml.Element(_, _, nested) =>
                nested.head match {
                  case Xml.Text(value) => assertTrue(value == "new")
                  case _               => assertTrue(false)
                }
              case _ => assertTrue(false)
            }
          case _ => assertTrue(false)
        }
      },
      test("nested field not found error") {
        val xml    = Xml.Element("root", Xml.Element("level1", Xml.Element("wrong")))
        val patch  = XmlPatch.replace(DynamicOptic.root.field("level1").field("missing"), Xml.Text("new"))
        val result = patch(xml)
        assertTrue(result.isLeft)
      },
      test("field navigation on non-element fails") {
        val xml    = Xml.Element("root", Xml.Element("a", Xml.Text("text")))
        val patch  = XmlPatch.replace(DynamicOptic.root.field("a").field("inner"), Xml.Element("new"))
        val result = patch(xml)
        assertTrue(result.isLeft)
      }
    ),
    suite("isEmpty")(
      test("empty patch returns true") {
        val isEmpty = XmlPatch.empty.isEmpty
        assertTrue(isEmpty)
      },
      test("non-empty patch returns false") {
        val patch         = XmlPatch.add(DynamicOptic.root, Xml.Element("a"), XmlPatch.Position.AppendChild)
        val isEmptyResult = patch.isEmpty
        assertTrue(!isEmptyResult)
      }
    )
  )
}
