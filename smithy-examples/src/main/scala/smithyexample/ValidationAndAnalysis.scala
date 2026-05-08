package smithyexample

import zio.blocks.smithy._

/**
 * Smithy — Step 3: Validation and Analysis
 *
 * This example demonstrates how to analyze Smithy models for completeness, find
 * deprecated shapes, check for documentation, and validate API contracts.
 *
 * Run with: sbt "smithy-examples/runMain smithyexample.ValidationAndAnalysis"
 */
@main def ValidationAndAnalysis(): Unit = {
  val smithyText = """$version: "2"
namespace bookstore.api

@deprecated(message: "Use SearchBooksV2 instead")
@http(method: "GET", uri: "/books/search")
operation SearchBooks {
  input: SearchBooksInput
  output: SearchBooksOutput
}

@http(method: "GET", uri: "/v2/books/search")
operation SearchBooksV2 {
  input: SearchBooksV2Input
  output: SearchBooksV2Output
}

@documentation("Request to search for books")
structure SearchBooksInput {
  @required
  @documentation("Search query string")
  query: String
}

structure SearchBooksV2Input {
  @required
  query: String
}

@documentation("A book in the store catalog")
structure Book {
  @required
  @documentation("Unique ISBN identifier")
  isbn: String

  @required
  title: String

  price: Double
}

structure BookV2 {
  @required
  isbn: String

  @required
  title: String

  @required
  @documentation("Price in cents to avoid floating point issues")
  priceInCents: Long

  @required
  available: Boolean
}

structure SearchBooksOutput {
  @required
  books: BookList
}

structure SearchBooksV2Output {
  @required
  books: BookList
}

list BookList {
  member: Book
}

@error("client")
structure BookNotFound {
  @required
  message: String
}
"""

  println("=== API Contract Analysis ===\n")

  SmithyModel.parse(smithyText) match {
    case Right(model) =>
      println(s"✓ Parsed model: ${model.namespace}\n")

      println("=== Finding Deprecated Shapes ===\n")
      val deprecated = model.shapes.filter { shapeDef =>
        shapeDef.shape.traits.exists(_.id.name == "deprecated")
      }

      if (deprecated.nonEmpty) {
        println(s"⚠ Found ${deprecated.length} deprecated shape(s):")
        deprecated.foreach { shapeDef =>
          println(s"  - ${shapeDef.name}")
          shapeDef.shape.traits
            .find(_.id.name == "deprecated")
            .foreach { deprecatedTrait =>
              println(s"    Reason: ${deprecatedTrait.value.map(v => prettyPrintValue(v)).getOrElse("No details")}")
            }
        }
        println()
      } else {
        println("✓ No deprecated shapes found\n")
      }

      println("=== Documentation Coverage ===\n")
      val documented = model.shapes.filter { shapeDef =>
        shapeDef.shape.traits.exists(_.id.name == "documentation")
      }

      val undocumented = model.shapes.filter { shapeDef =>
        !shapeDef.shape.traits.exists(_.id.name == "documentation")
      }

      println(s"Documented shapes: ${documented.length} / ${model.shapes.length}")
      println(s"Coverage: ${(documented.length * 100) / model.shapes.length}%\n")

      if (undocumented.nonEmpty) {
        println("⚠ Undocumented shapes:")
        undocumented.foreach { shapeDef =>
          println(s"  - ${shapeDef.name}")
        }
        println()
      }

      println("=== Required Fields Check ===\n")
      model.shapes.foreach { shapeDef =>
        shapeDef.shape match {
          case struct: StructureShape =>
            val requiredFields = struct.members.filter(m => m.traits.exists(_.id.name == "required"))

            if (requiredFields.length > 0) {
              println(s"${shapeDef.name}:")
              println(s"  Total fields: ${struct.members.length}")
              println(s"  Required: ${requiredFields.length}")
              println(s"  Optional: ${struct.members.length - requiredFields.length}")
              println()
            }

          case _ =>
        }
      }

      println("=== HTTP Binding Validation ===\n")
      model.shapes.foreach { shapeDef =>
        shapeDef.shape match {
          case op: OperationShape =>
            val httpTrait = op.traits.find(_.id.name == "http")
            httpTrait match {
              case Some(httpTraitApp) =>
                println(s"Operation: ${shapeDef.name}")
                println(s"  HTTP Binding: ✓ Configured")
                httpTraitApp.value.foreach {
                  case NodeValue.Object(obj) =>
                    obj.collectFirst { case ("method", NodeValue.String(m)) =>
                      println(s"    Method: $m")
                    }
                    obj.collectFirst { case ("uri", NodeValue.String(u)) =>
                      println(s"    URI: $u")
                    }
                  case _ =>
                }
                println()

              case None =>
                println(s"Operation: ${shapeDef.name}")
                println(s"  HTTP Binding: ✗ Missing")
                println()
            }

          case _ =>
        }
      }

      println("=== Shape Type Distribution ===\n")
      val shapesByType = model.shapes.groupBy { shapeDef =>
        shapeDef.shape match {
          case _: StructureShape => "Structure"
          case _: OperationShape => "Operation"
          case _: ServiceShape   => "Service"
          case _: ListShape      => "List"
          case _: EnumShape      => "Enum"
          case _: IntEnumShape   => "IntEnum"
          case _: StringShape    => "String"
          case _: BlobShape      => "Blob"
          case _: BooleanShape   => "Boolean"
          case _: IntegerShape   => "Integer"
          case _: LongShape      => "Long"
          case _: DoubleShape    => "Double"
          case _: FloatShape     => "Float"
          case _: TimestampShape => "Timestamp"
          case _: DocumentShape  => "Document"
          case _: MapShape       => "Map"
          case _: UnionShape     => "Union"
          case _: ResourceShape  => "Resource"
          case _                 => "Other"
        }
      }

      shapesByType.toSeq.sortBy(_._1).foreach { case (shapeType, shapes) =>
        println(s"$shapeType: ${shapes.length}")
      }
      println()

    case Left(error) =>
      println(s"✗ Parse error: ${error.message}")
  }

  def prettyPrintValue(value: NodeValue): String =
    value match {
      case NodeValue.String(s)       => s"\"$s\""
      case NodeValue.Number(n)       => n.toString()
      case NodeValue.Boolean(b)      => b.toString()
      case NodeValue.Null            => "null"
      case NodeValue.Array(items)    => s"[${items.map(prettyPrintValue).mkString(", ")}]"
      case NodeValue.Object(entries) =>
        val pairs = entries.map { case (k, v) =>
          s"$k: ${prettyPrintValue(v)}"
        }
        s"{${pairs.mkString(", ")}}"
    }
}
