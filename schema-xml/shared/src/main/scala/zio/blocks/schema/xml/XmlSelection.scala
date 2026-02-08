package zio.blocks.schema.xml

import zio.blocks.chunk.{Chunk, ChunkBuilder}
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue}

/**
 * A wrapper around `Either[XmlError, Chunk[Xml]]` that provides fluent chaining
 * for XML navigation and querying operations.
 *
 * XmlSelection enables a fluent API style for navigating through XML
 * structures:
 * {{{
 *   xml.get("users").elements.apply(0).get("name").texts
 * }}}
 *
 * The selection can contain zero, one, or multiple XML values, supporting both
 * single-value navigation and multi-value queries.
 */
final case class XmlSelection(either: Either[XmlError, Chunk[Xml]]) extends AnyVal {

  // ─────────────────────────────────────────────────────────────────────────
  // Basic operations
  // ─────────────────────────────────────────────────────────────────────────

  /** Returns true if the selection is successful (contains values). */
  def isSuccess: Boolean = either.isRight

  /** Returns true if the selection is a failure. */
  def isFailure: Boolean = either.isLeft

  /** Returns the error if this is a failure, otherwise None. */
  def error: Option[XmlError] = either match {
    case Left(e) => new Some(e)
    case _       => None
  }

  /** Returns the selected values if successful, otherwise None. */
  def values: Option[Chunk[Xml]] = either match {
    case Right(v) => new Some(v)
    case _        => None
  }

  /** Returns the selected values as a Chunk, or an empty Chunk on failure. */
  def toChunk: Chunk[Xml] = either match {
    case Right(v) => v
    case _        => Chunk.empty
  }

  /**
   * Returns the single selected value, or fails if there are 0 or more than 1
   * values.
   */
  def one: Either[XmlError, Xml] = either match {
    case Right(v) =>
      val len = v.length
      if (len == 1) new Right(v.head)
      else new Left(XmlError(s"Expected single value but got $len"))
    case l => l.asInstanceOf[Either[XmlError, Xml]]
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Size Operations
  // ─────────────────────────────────────────────────────────────────────────

  /** Returns true if this selection is empty (no values or error). */
  def isEmpty: Boolean = either match {
    case Right(v) => v.isEmpty
    case _        => true
  }

  /** Returns true if this selection contains at least one value. */
  def nonEmpty: Boolean = !isEmpty

  /** Returns the number of selected values (0 on error). */
  def size: Int = either match {
    case Right(v) => v.length
    case _        => 0
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Terminal Operations
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Returns any single value from the selection. Fails if the selection is
   * empty or an error.
   */
  def any: Either[XmlError, Xml] = either match {
    case Right(v) =>
      if (v.isEmpty) new Left(XmlError("Expected at least one value but got none"))
      else new Right(v.head)
    case l => l.asInstanceOf[Either[XmlError, Xml]]
  }

  /**
   * Returns all selected values condensed into a single Xml. If there are
   * multiple values, wraps them in an element. Fails if the selection is empty
   * or an error.
   */
  def all: Either[XmlError, Xml] = either match {
    case Right(v) =>
      if (v.isEmpty) new Left(XmlError("Expected at least one value but got none"))
      else {
        new Right({
          if (v.length == 1) v.head
          else Xml.Element(XmlName("root"), Chunk.empty, v)
        })
      }
    case l => l.asInstanceOf[Either[XmlError, Xml]]
  }

  /**
   * Returns all selected values as an XML element with children.
   */
  def toArray: Either[XmlError, Xml] = either match {
    case Right(v) => new Right(Xml.Element(XmlName("root"), Chunk.empty, v))
    case l        => l.asInstanceOf[Either[XmlError, Xml]]
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Type Filtering (keeps only matching types)
  // ─────────────────────────────────────────────────────────────────────────

  /** Keeps only element values. */
  def elements: XmlSelection = filter(XmlType.Element)

  /** Keeps only text values. */
  def texts: XmlSelection = filter(XmlType.Text)

  /** Keeps only CDATA values. */
  def cdatas: XmlSelection = filter(XmlType.CData)

  /** Keeps only comment values. */
  def comments: XmlSelection = filter(XmlType.Comment)

  /** Keeps only processing instruction values. */
  def processingInstructions: XmlSelection = filter(XmlType.ProcessingInstruction)

  // ─────────────────────────────────────────────────────────────────────────
  // Navigation
  // ─────────────────────────────────────────────────────────────────────────

  /** Navigates to child elements with the given name. */
  def get(name: String): XmlSelection = flatMap { xml =>
    xml match {
      case Xml.Element(_, _, children) =>
        val matching = children.collect {
          case e @ Xml.Element(n, _, _) if n.localName == name => e
        }
        XmlSelection.succeedMany(matching)
      case _ => XmlSelection.empty
    }
  }

  /** Navigates to the nth child (any type). */
  def apply(index: Int): XmlSelection = flatMap { xml =>
    xml match {
      case Xml.Element(_, _, children) =>
        if (index >= 0 && index < children.length) {
          XmlSelection.succeed(children(index))
        } else {
          XmlSelection.empty
        }
      case _ => XmlSelection.empty
    }
  }

  /**
   * Navigates to all descendant elements with the given name (recursive
   * search). Does not include the starting element itself, only its
   * descendants.
   */
  def descendant(name: String): XmlSelection = flatMap { xml =>
    def findDescendants(node: Xml): Chunk[Xml] = node match {
      case Xml.Element(n, _, children) =>
        val self = if (n.localName == name) Chunk.single(node) else Chunk.empty
        self ++ children.flatMap(findDescendants)
      case _ => Chunk.empty
    }
    // Only search in children, not the starting element itself
    xml match {
      case Xml.Element(_, _, children) =>
        XmlSelection.succeedMany(children.flatMap(findDescendants))
      case _ => XmlSelection.empty
    }
  }

  /** Navigates using a DynamicOptic path. */
  def get(path: DynamicOptic): XmlSelection =
    path.nodes.foldLeft(this) { (sel, node) =>
      node match {
        case DynamicOptic.Node.Field(name)   => sel.get(name)
        case DynamicOptic.Node.AtIndex(idx)  => sel(idx)
        case DynamicOptic.Node.AtMapKey(key) =>
          key match {
            case DynamicValue.Primitive(PrimitiveValue.String(s)) => sel.getAttribute(s)
            case _                                                => sel
          }
        case _ => sel
      }
    }

  /** Gets an attribute value by name (internal helper). */
  private def getAttribute(name: String): XmlSelection = flatMap { xml =>
    xml match {
      case Xml.Element(_, attrs, _) =>
        attrs.collectFirst {
          case (n, value) if n.localName == name => Xml.Text(value)
        }
          .map(XmlSelection.succeed)
          .getOrElse(XmlSelection.empty)
      case _ => XmlSelection.empty
    }
  }

  /**
   * Extracts text content from the single selected element. For Text/CData
   * nodes, returns the content directly. For Element nodes, returns
   * concatenated text of all child text nodes.
   */
  def text: Either[XmlError, String] = one.flatMap {
    case Xml.Text(value)             => Right(value)
    case Xml.CData(value)            => Right(value)
    case Xml.Element(_, _, children) =>
      val texts = children.collect {
        case Xml.Text(v)  => v
        case Xml.CData(v) => v
      }
      if (texts.nonEmpty) Right(texts.mkString)
      else Left(XmlError("No text content found"))
    case other => Left(XmlError(s"Expected text content but got ${other.xmlType}"))
  }

  /**
   * Concatenates all text content from the selection. Never fails; returns
   * empty string if no text is found.
   */
  def textContent: String = toChunk.flatMap {
    case Xml.Text(v)                 => Chunk.single(v)
    case Xml.CData(v)                => Chunk.single(v)
    case Xml.Element(_, _, children) =>
      children.collect {
        case Xml.Text(v)  => v
        case Xml.CData(v) => v
      }
    case _ => Chunk.empty
  }.mkString

  // ─────────────────────────────────────────────────────────────────────────
  // Combinators
  // ─────────────────────────────────────────────────────────────────────────

  /** Maps a function over all selected values. */
  def map(f: Xml => Xml): XmlSelection = new XmlSelection(either match {
    case Right(v) => new Right(v.map(f))
    case l        => l
  })

  /** FlatMaps a function over all selected values, combining results. */
  def flatMap(f: Xml => XmlSelection): XmlSelection = new XmlSelection(either match {
    case Right(v1) =>
      new Right({
        val len = v1.length
        if (len == 0) Chunk.empty
        else {
          val builder = ChunkBuilder.make[Xml]()
          var idx     = 0
          while (idx < len) {
            f(v1(idx)).either match {
              case Right(v2) => builder.addAll(v2)
              case l         => return new XmlSelection(l)
            }
            idx += 1
          }
          builder.result()
        }
      })
    case l => l
  })

  /** Filters selected values based on a predicate. */
  def filter(p: Xml => Boolean): XmlSelection = new XmlSelection(either match {
    case Right(v) => new Right(v.filter(p))
    case l        => l
  })

  /** Returns this selection if successful, otherwise the alternative. */
  def orElse(alternative: => XmlSelection): XmlSelection =
    if (either.isRight) this
    else alternative

  /** Returns this selection's values, or the default on failure. */
  def getOrElse(default: => Chunk[Xml]): Chunk[Xml] = either match {
    case Right(v) => v
    case _        => default
  }

  /** Combines two selections, concatenating their values. */
  def ++(other: XmlSelection): XmlSelection = new XmlSelection(either match {
    case Right(v1) =>
      other.either match {
        case Right(v2) => new Right(v1 ++ v2)
        case l         => l
      }
    case l @ Left(e1) =>
      other.either match {
        case Left(e2) => new Left(e1.atSpan(DynamicOptic.Node.Field(e2.getMessage)))
        case _        => l
      }
  })

  // ─────────────────────────────────────────────────────────────────────────
  // Type-Directed Extraction
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Narrows the single selected value to the specified XML type. Fails if there
   * are 0 or more than 1 values, or if the value doesn't match.
   */
  def as(xmlType: XmlType): Either[XmlError, xmlType.Type] = either match {
    case Right(v) =>
      new Left(XmlError {
        val len = v.length
        if (len == 1) {
          val xml = v.head
          if (xml.xmlType eq xmlType) return new Right(xml.asInstanceOf[xmlType.Type])
          else s"Expected $xmlType but got ${xml.xmlType}"
        } else s"Expected single value but got $len"
      })
    case l => l.asInstanceOf[Either[XmlError, xmlType.Type]]
  }

  /**
   * Extracts the underlying Scala value from the single selected XML value.
   * Fails if there are 0 or more than 1 values, or if the value doesn't match.
   */
  def unwrap(xmlType: XmlType): Either[XmlError, xmlType.Unwrap] = either match {
    case Right(v) =>
      new Left(XmlError {
        val len = v.length
        if (len == 1) {
          val xml = v.head
          xml.unwrap(xmlType) match {
            case Some(x) => return new Right(x)
            case _       => s"Cannot unwrap ${xml.xmlType} as $xmlType"
          }
        } else s"Expected single value but got $len"
      })
    case l => l.asInstanceOf[Either[XmlError, xmlType.Unwrap]]
  }
}

object XmlSelection {

  /** Creates a successful selection with a single value. */
  def succeed(xml: Xml): XmlSelection = new XmlSelection(new Right(Chunk.single(xml)))

  /** Creates a successful selection with multiple values. */
  def succeedMany(xmls: Chunk[Xml]): XmlSelection = new XmlSelection(new Right(xmls))

  /** Creates a failed selection with an error. */
  def fail(error: XmlError): XmlSelection = new XmlSelection(new Left(error))

  /** An empty successful selection. */
  val empty: XmlSelection = new XmlSelection(new Right(Chunk.empty))
}
