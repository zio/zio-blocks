package zio.blocks.schema.comptime.internal

import zio.blocks.schema.SchemaBaseSpec
import zio.test._

/**
 * Exhaustive tests for AllowsErrorMessages rendering.
 *
 * Each test suite covers one render method and verifies:
 *   - Required content (type names, paths, grammar nodes) is present
 *   - The message has the standard header ("Allows Error") and footer ("─" ×
 *     10)
 *   - Hints appear only when supplied
 *   - color=false produces no ANSI escape sequences
 *   - color=true produces ANSI escape sequences
 */
object AllowsErrorMessagesSpec extends SchemaBaseSpec {

  private val ESC = "\u001b["

  def spec: Spec[TestEnvironment, Any] = suite("AllowsErrorMessages")(
    shapeViolationSuite,
    mutualRecursionSuite,
    unknownGrammarNodeSuite,
    multipleViolationsSuite,
    colorHandlingSuite
  )

  // ──────────────────────────────────────────────────────────────────────────
  // renderShapeViolation
  // ──────────────────────────────────────────────────────────────────────────

  val shapeViolationSuite = suite("renderShapeViolation")(
    test("contains the path") {
      val msg = AllowsErrorMessages.renderShapeViolation(
        "Order.items.<element>",
        "Record(OrderItem)",
        "Primitive | Sequence[Primitive]",
        "",
        color = false
      )
      assertTrue(msg.contains("Order.items.<element>"))
    },
    test("contains Found label and value") {
      val msg = AllowsErrorMessages.renderShapeViolation(
        "X.field",
        "Record(Inner)",
        "Primitive",
        "",
        color = false
      )
      assertTrue(msg.contains("Found"), msg.contains("Record(Inner)"))
    },
    test("contains Required label and value") {
      val msg = AllowsErrorMessages.renderShapeViolation(
        "X.field",
        "Record(Inner)",
        "Primitive | Sequence[Primitive]",
        "",
        color = false
      )
      assertTrue(msg.contains("Required"), msg.contains("Primitive | Sequence[Primitive]"))
    },
    test("contains Hint when supplied") {
      val msg = AllowsErrorMessages.renderShapeViolation(
        "X.field",
        "Record(Inner)",
        "Primitive",
        "flatten Inner into X",
        color = false
      )
      assertTrue(msg.contains("Hint"), msg.contains("flatten Inner into X"))
    },
    test("does NOT contain Hint when empty") {
      val msg = AllowsErrorMessages.renderShapeViolation(
        "X.field",
        "Record(Inner)",
        "Primitive",
        "",
        color = false
      )
      assertTrue(!msg.contains("Hint"))
    },
    test("has Allows Error header") {
      val msg = AllowsErrorMessages.renderShapeViolation(
        "A.b",
        "Dynamic",
        "Primitive",
        "",
        color = false
      )
      assertTrue(msg.contains("Allows Error"))
    },
    test("has footer line of dashes") {
      val msg = AllowsErrorMessages.renderShapeViolation(
        "A.b",
        "Dynamic",
        "Primitive",
        "",
        color = false
      )
      assertTrue(msg.contains("─" * 10))
    },
    test("path <root> renders literally") {
      val msg = AllowsErrorMessages.renderShapeViolation(
        "<root>",
        "Variant(Ev)",
        "Record[Primitive]",
        "",
        color = false
      )
      assertTrue(msg.contains("<root>"))
    },
    test("no ANSI when color=false") {
      val msg = AllowsErrorMessages.renderShapeViolation(
        "A.b",
        "Dynamic",
        "Primitive",
        "some hint",
        color = false
      )
      assertTrue(!msg.contains(ESC))
    }
  )

  // ──────────────────────────────────────────────────────────────────────────
  // renderMutualRecursion
  // ──────────────────────────────────────────────────────────────────────────

  val mutualRecursionSuite = suite("renderMutualRecursion")(
    test("contains the type name") {
      val msg = AllowsErrorMessages.renderMutualRecursion(
        "Forest",
        List("Forest", "Tree", "Forest"),
        color = false
      )
      assertTrue(msg.contains("Forest"))
    },
    test("contains the cycle path with arrows") {
      val msg = AllowsErrorMessages.renderMutualRecursion(
        "Forest",
        List("Forest", "Tree", "Forest"),
        color = false
      )
      assertTrue(msg.contains("Tree"))
    },
    test("mentions mutual recursion") {
      val msg = AllowsErrorMessages.renderMutualRecursion(
        "A",
        List("A", "B", "A"),
        color = false
      )
      assertTrue(msg.contains("Mutually recursive") || msg.contains("mutual"))
    },
    test("contains Fix section") {
      val msg = AllowsErrorMessages.renderMutualRecursion(
        "A",
        List("A", "B", "A"),
        color = false
      )
      assertTrue(msg.contains("Fix"))
    },
    test("has Allows Error header") {
      val msg = AllowsErrorMessages.renderMutualRecursion(
        "X",
        List("X", "Y", "X"),
        color = false
      )
      assertTrue(msg.contains("Allows Error"))
    },
    test("has footer") {
      val msg = AllowsErrorMessages.renderMutualRecursion(
        "X",
        List("X", "Y", "X"),
        color = false
      )
      assertTrue(msg.contains("─" * 10))
    },
    test("no ANSI when color=false") {
      val msg = AllowsErrorMessages.renderMutualRecursion(
        "Forest",
        List("Forest", "Tree", "Forest"),
        color = false
      )
      assertTrue(!msg.contains(ESC))
    }
  )

  // ──────────────────────────────────────────────────────────────────────────
  // renderUnknownGrammarNode
  // ──────────────────────────────────────────────────────────────────────────

  val unknownGrammarNodeSuite = suite("renderUnknownGrammarNode")(
    test("contains the node type string") {
      val msg = AllowsErrorMessages.renderUnknownGrammarNode("MyCustomType", color = false)
      assertTrue(msg.contains("MyCustomType"))
    },
    test("lists valid grammar nodes") {
      val msg = AllowsErrorMessages.renderUnknownGrammarNode("Foo", color = false)
      assertTrue(
        msg.contains("Primitive"),
        msg.contains("Record"),
        msg.contains("Variant"),
        msg.contains("Sequence"),
        msg.contains("Self")
      )
    },
    test("contains Fix section") {
      val msg = AllowsErrorMessages.renderUnknownGrammarNode("Foo", color = false)
      assertTrue(msg.contains("Fix"))
    },
    test("has Allows Error header") {
      val msg = AllowsErrorMessages.renderUnknownGrammarNode("Foo", color = false)
      assertTrue(msg.contains("Allows Error"))
    },
    test("has footer") {
      val msg = AllowsErrorMessages.renderUnknownGrammarNode("Foo", color = false)
      assertTrue(msg.contains("─" * 10))
    },
    test("no ANSI when color=false") {
      val msg = AllowsErrorMessages.renderUnknownGrammarNode("Foo", color = false)
      assertTrue(!msg.contains(ESC))
    }
  )

  // ──────────────────────────────────────────────────────────────────────────
  // renderMultipleViolations
  // ──────────────────────────────────────────────────────────────────────────

  val multipleViolationsSuite = suite("renderMultipleViolations")(
    test("single violation is returned unchanged") {
      val v   = AllowsErrorMessages.renderShapeViolation("A.b", "Dynamic", "Primitive", "", color = false)
      val msg = AllowsErrorMessages.renderMultipleViolations(List(v))
      assertTrue(msg == v)
    },
    test("two violations are both present") {
      val v1  = AllowsErrorMessages.renderShapeViolation("A.b", "Dynamic", "Primitive", "", color = false)
      val v2  = AllowsErrorMessages.renderShapeViolation("A.c", "Record(X)", "Primitive", "", color = false)
      val msg = AllowsErrorMessages.renderMultipleViolations(List(v1, v2))
      assertTrue(msg.contains("A.b"), msg.contains("A.c"))
    },
    test("violations are separated") {
      val v1  = AllowsErrorMessages.renderShapeViolation("A.b", "Dynamic", "Primitive", "", color = false)
      val v2  = AllowsErrorMessages.renderShapeViolation("A.c", "Record(X)", "Primitive", "", color = false)
      val msg = AllowsErrorMessages.renderMultipleViolations(List(v1, v2))
      // Each violation has its own header
      assertTrue(msg.indexOf("Allows Error") != msg.lastIndexOf("Allows Error"))
    }
  )

  // ──────────────────────────────────────────────────────────────────────────
  // Color handling — every render* method tested with color=true
  // ──────────────────────────────────────────────────────────────────────────

  val colorHandlingSuite = suite("color handling")(
    test("renderShapeViolation with color=true contains ANSI sequences") {
      val msg = AllowsErrorMessages.renderShapeViolation(
        "A.b",
        "Dynamic",
        "Primitive",
        "some hint",
        color = true
      )
      assertTrue(msg.contains(ESC))
    },
    test("renderMutualRecursion with color=true contains ANSI sequences") {
      val msg = AllowsErrorMessages.renderMutualRecursion(
        "Forest",
        List("Forest", "Tree", "Forest"),
        color = true
      )
      assertTrue(msg.contains(ESC))
    },
    test("renderUnknownGrammarNode with color=true contains ANSI sequences") {
      val msg = AllowsErrorMessages.renderUnknownGrammarNode("Foo", color = true)
      assertTrue(msg.contains(ESC))
    },
    test("all messages have header even with color=true") {
      val msgs = List(
        AllowsErrorMessages.renderShapeViolation("A.b", "D", "P", "", color = true),
        AllowsErrorMessages.renderMutualRecursion("X", List("X", "Y", "X"), color = true),
        AllowsErrorMessages.renderUnknownGrammarNode("Foo", color = true)
      )
      assertTrue(msgs.forall(_.contains("Allows Error")))
    },
    test("all messages have footer even with color=true") {
      val msgs = List(
        AllowsErrorMessages.renderShapeViolation("A.b", "D", "P", "", color = true),
        AllowsErrorMessages.renderMutualRecursion("X", List("X", "Y", "X"), color = true),
        AllowsErrorMessages.renderUnknownGrammarNode("Foo", color = true)
      )
      // Footer is gray dashes — strip ANSI and check for dashes
      assertTrue(msgs.forall { msg =>
        val stripped = msg.replaceAll("\u001b\\[[0-9;]*m", "")
        stripped.contains("─" * 10)
      })
    }
  )
}
