package zio.blocks.schema.comptime.internal

/**
 * Shared error message rendering for Allows compile-time shape constraints.
 *
 * All macro error messages — in both Scala 2 and Scala 3 — are rendered here.
 * This guarantees a consistent, beautiful, and actionable user experience
 * across compiler versions.
 *
 * Every `render*` method accepts a `color: Boolean` parameter that enables ANSI
 * color sequences. At macro expansion time callers pass
 * `AllowsErrorMessages.Colors.shouldUseColor`.
 */
private[comptime] object AllowsErrorMessages {

  // ───────────────────────────────────────────────────────────────────────────
  // Terminal Colors
  // ───────────────────────────────────────────────────────────────────────────

  object Colors {
    def shouldUseColor: Boolean = {
      val noColor     = sys.env.get("NO_COLOR").exists(_.nonEmpty)
      val sbtNoFormat = sys.props.get("sbt.log.noformat").contains("true")
      !noColor && !sbtNoFormat
    }

    val Reset  = "\u001b[0m"
    val Bold   = "\u001b[1m"
    val Red    = "\u001b[31m"
    val Green  = "\u001b[32m"
    val Yellow = "\u001b[33m"
    val Blue   = "\u001b[34m"
    val Cyan   = "\u001b[36m"
    val Gray   = "\u001b[90m"

    def red(s: String, color: Boolean): String    = if (color) s"$Red$s$Reset" else s
    def green(s: String, color: Boolean): String  = if (color) s"$Green$s$Reset" else s
    def yellow(s: String, color: Boolean): String = if (color) s"$Yellow$s$Reset" else s
    def blue(s: String, color: Boolean): String   = if (color) s"$Blue$s$Reset" else s
    def cyan(s: String, color: Boolean): String   = if (color) s"$Cyan$s$Reset" else s
    def gray(s: String, color: Boolean): String   = if (color) s"$Gray$s$Reset" else s
    def bold(s: String, color: Boolean): String   = if (color) s"$Bold$s$Reset" else s
  }

  // ───────────────────────────────────────────────────────────────────────────
  // Common Formatting Helpers
  // ───────────────────────────────────────────────────────────────────────────

  private val lineWidth = 80

  private def header(title: String, color: Boolean): String = {
    import Colors._
    val sep = "─" * (lineWidth - title.length - 4)
    s"${gray("──", color)} ${bold(title, color)} ${gray(sep, color)}"
  }

  private def footer(color: Boolean): String = {
    import Colors._
    gray("─" * lineWidth, color)
  }

  // ───────────────────────────────────────────────────────────────────────────
  // E1: Field-level shape violation
  //
  // Emitted when a specific field of a record does not satisfy the grammar.
  // `path`     — dot-separated path to the offending position, e.g. "Order.items.<element>"
  // `found`    — description of the actual type, e.g. "Record(OrderItem)"
  // `required` — description of the required grammar, e.g. "Primitive | Sequence[Primitive]"
  // `hint`     — optional extra explanation (may be empty)
  // ───────────────────────────────────────────────────────────────────────────

  /**
   * Render a schema shape violation at a specific field path.
   *
   * @param path
   *   dot-separated path to the offending position (e.g.
   *   "Order.items.&lt;element&gt;")
   * @param found
   *   description of the actual type found (e.g. "Record(OrderItem)")
   * @param required
   *   description of the required grammar shape (e.g. "Primitive |
   *   Sequence[Primitive]")
   * @param hint
   *   optional extra fix suggestion; empty string means no hint block is shown
   * @param color
   *   whether to emit ANSI color codes
   */
  def renderShapeViolation(
    path: String,
    found: String,
    required: String,
    hint: String,
    color: Boolean
  ): String = {
    import Colors._
    val hintSection =
      if (hint.nonEmpty)
        s"""
           |
           |  ${yellow("Hint:", color)} $hint""".stripMargin
      else ""
    s"""${header("Allows Error", color)}
       |
       |  ${bold("Shape violation", color)} at ${cyan(path, color)}
       |
       |    ${bold("Found:   ", color)} $found
       |    ${bold("Required:", color)} $required$hintSection
       |
       |${footer(color)}""".stripMargin
  }

  // ───────────────────────────────────────────────────────────────────────────
  // E2: Mutual recursion detected
  //
  // Emitted when the macro detects two distinct types that refer to each other
  // (e.g. Forest → Tree → Forest), which would require unbounded unrolling.
  // `typeName`  — the root type being checked (e.g. "Forest")
  // `cyclePath` — the detected cycle, e.g. List("Forest", "Tree", "Forest")
  // ───────────────────────────────────────────────────────────────────────────

  /**
   * Render a mutual recursion error.
   *
   * @param typeName
   *   the type at which the cycle was first detected
   * @param cyclePath
   *   the full cycle as a list of type names; the last element repeats the
   *   first (e.g. `List("Forest", "Tree", "Forest")`)
   * @param color
   *   whether to emit ANSI color codes
   */
  def renderMutualRecursion(
    typeName: String,
    cyclePath: List[String],
    color: Boolean
  ): String = {
    import Colors._
    val arrow    = gray(" → ", color)
    val rendered = cyclePath.mkString(arrow)
    s"""${header("Allows Error", color)}
       |
       |  ${bold("Mutually recursive types", color)} are not supported by ${cyan("Allows", color)}.
       |
       |  ${bold("Type:", color)}  ${cyan(typeName, color)}
       |  ${bold("Cycle:", color)} $rendered
       |
       |  ${yellow("Fix:", color)} Flatten the mutual recursion, or use ${cyan("DynamicValue", color)} for the
       |    recursive position and accept ${cyan("Dynamic", color)} in the grammar.
       |
       |${footer(color)}""".stripMargin
  }

  // ───────────────────────────────────────────────────────────────────────────
  // E3: Unknown grammar node
  //
  // Emitted when the macro encounters a type in the `S` phantom type parameter
  // that is not a recognised Allows grammar node.
  // `nodeType` — the unrecognised type as shown by the compiler
  // ───────────────────────────────────────────────────────────────────────────

  /**
   * Render an unknown grammar node error.
   *
   * @param nodeType
   *   the string representation of the unrecognised grammar node type
   * @param color
   *   whether to emit ANSI color codes
   */
  def renderUnknownGrammarNode(nodeType: String, color: Boolean): String = {
    import Colors._
    s"""${header("Allows Error", color)}
       |
       |  ${bold("Unknown grammar node:", color)} ${cyan(nodeType, color)}
       |
       |  The type ${cyan("S", color)} in ${cyan("Allows[A, S]", color)} must be composed exclusively of
       |  the grammar nodes exported by ${cyan("Allows", color)}:
       |
       |    ${cyan("Primitive", color)}  ${cyan("Primitive.Int", color)}  ${cyan("Primitive.String", color)}  ${cyan(
        "…",
        color
      )}
       |    ${cyan("Record[A]", color)}  ${cyan("Sequence[A]", color)}  ${cyan("Map[K,V]", color)}
       |    ${cyan("Optional[A]", color)}  ${cyan("Wrapped[A]", color)}  ${cyan("Dynamic", color)}  ${cyan(
        "Self",
        color
      )}
       |    ${cyan("A | B", color)} (union)
       |
       |  Note: sealed traits / enums are automatically unwrapped — no ${cyan("Variant", color)} node needed.
       |
       |  ${yellow("Fix:", color)} Replace ${cyan(nodeType, color)} with one of the nodes above.
       |
       |${footer(color)}""".stripMargin
  }

  // ───────────────────────────────────────────────────────────────────────────
  // Combine multiple violations into one compound message
  //
  // When several fields fail in the same compilation pass, the macro collects
  // all individual `renderShapeViolation` strings and joins them here so the
  // user sees every problem at once.
  // ───────────────────────────────────────────────────────────────────────────

  /**
   * Combine multiple rendered violation messages into a single compiler error.
   *
   * @param violations
   *   individual messages produced by `renderShapeViolation` or
   *   `renderMutualRecursion`; must be non-empty
   * @param color
   *   whether to emit ANSI color codes
   */
  def renderMultipleViolations(violations: List[String]): String =
    violations.mkString("\n\n")
}
