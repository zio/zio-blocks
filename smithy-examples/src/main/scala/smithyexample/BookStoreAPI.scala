/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package smithyexample

import zio.blocks.smithy._

/**
 * Smithy — Real-World Example: Online Book Store API
 *
 * A complete example showing an online book store API model with operations for
 * browsing catalog, managing shopping carts, and processing orders.
 * Demonstrates parsing, querying, code generation, and service analysis.
 *
 * Run with: sbt "smithy-examples/runMain smithyexample.BookStoreAPI"
 */
@main def BookStoreAPI(): Unit = {
  val bookStoreSmithyText = """$version: "2"
namespace bookstore.api

service BookStoreService {
  version: "3.0.0"
  operations: [SearchBooks, GetBook, GetAuthor, CreateOrder, GetOrder]
}

@http(method: "GET", uri: "/api/v3/search")
operation SearchBooks {
  input: SearchBooksInput
  output: SearchBooksOutput
  errors: [InvalidSearchQuery]
}

structure SearchBooksInput {
  @required
  query: String

  category: String
  minPrice: Double
  maxPrice: Double
  limit: Integer
  offset: Integer
}

structure SearchBooksOutput {
  @required
  books: BookList

  @required
  total: Integer

  @required
  limit: Integer
}

list BookList {
  member: Book
}

@documentation("A book available in the store catalog")
structure Book {
  @required
  @documentation("Unique ISBN identifier")
  isbn: String

  @required
  @documentation("Book title")
  title: String

  @required
  author: String

  @required
  @documentation("Category or genre")
  category: String

  @required
  @documentation("Price in USD")
  price: Double

  @required
  @documentation("Stock quantity available")
  stockQuantity: Integer

  @documentation("URL to book cover image")
  coverImageUrl: String

  @documentation("Publication year")
  publicationYear: Integer

  @documentation("Brief description of the book")
  description: String
}

@http(method: "GET", uri: "/api/v3/books/{isbn}")
operation GetBook {
  input: GetBookInput
  output: Book
  errors: [BookNotFound]
}

structure GetBookInput {
  @required
  isbn: String
}

structure BookNotFound {
  @required
  message: String

  @required
  isbn: String
}

@http(method: "GET", uri: "/api/v3/authors/{name}")
operation GetAuthor {
  input: GetAuthorInput
  output: AuthorProfile
  errors: [AuthorNotFound]
}

structure GetAuthorInput {
  @required
  name: String
}

@documentation("Author profile with bibliography")
structure AuthorProfile {
  @required
  name: String

  @required
  biography: String

  @required
  books: BookList

  website: String
}

structure AuthorNotFound {
  @required
  message: String

  @required
  authorName: String
}

@http(method: "POST", uri: "/api/v3/orders")
operation CreateOrder {
  input: CreateOrderInput
  output: Order
  errors: [InvalidCart, OutOfStock, PaymentFailed]
}

structure CreateOrderInput {
  @required
  items: OrderItemList

  @required
  customerEmail: String

  @required
  shippingAddress: Address

  promoCode: String
}

list OrderItemList {
  member: OrderItem
}

structure OrderItem {
  @required
  isbn: String

  @required
  quantity: Integer
}

structure Address {
  @required
  street: String

  @required
  city: String

  @required
  state: String

  @required
  zipCode: String

  country: String
}

@documentation("A customer order for books")
structure Order {
  @required
  @documentation("Unique order identifier")
  id: String

  @required
  @documentation("Order creation timestamp (Unix seconds)")
  createdAt: Long

  @required
  customerEmail: String

  @required
  items: OrderItemList

  @required
  @documentation("Total order amount in USD")
  totalAmount: Double

  @required
  status: OrderStatus

  @required
  shippingAddress: Address

  @documentation("Expected delivery date (Unix seconds)")
  estimatedDeliveryDate: Long
}

enum OrderStatus {
  PENDING
  CONFIRMED
  PROCESSING
  SHIPPED
  DELIVERED
  CANCELLED
}

structure InvalidCart {
  @required
  message: String

  reason: String
}

structure OutOfStock {
  @required
  message: String

  @required
  isbn: String

  @required
  requestedQuantity: Integer

  @required
  availableQuantity: Integer
}

structure PaymentFailed {
  @required
  message: String

  errorCode: String
}

@http(method: "GET", uri: "/api/v3/orders/{id}")
operation GetOrder {
  input: GetOrderInput
  output: Order
  errors: [OrderNotFound]
}

structure GetOrderInput {
  @required
  id: String
}

structure OrderNotFound {
  @required
  message: String

  @required
  orderId: String
}
"""

  println("╔════════════════════════════════════════════════════════════╗")
  println("║         Online Book Store API Example                      ║")
  println("║   Complete Smithy Model with Parsing & Code Generation     ║")
  println("╚════════════════════════════════════════════════════════════╝\n")

  SmithyModel.parse(bookStoreSmithyText) match {
    case Right(model) =>
      println(s"✓ Successfully parsed Book Store API")
      println(s"  Namespace: ${model.namespace}")
      println(s"  Total shapes: ${model.shapes.length}\n")

      // Analyze service
      println("═══════════════════════════════════════════════════════")
      println("SERVICE OVERVIEW")
      println("═══════════════════════════════════════════════════════\n")

      model.findShape("BookStoreService").foreach { serviceDef =>
        serviceDef.shape match {
          case service: ServiceShape =>
            println(s"Service: ${serviceDef.name}")
            println(s"Operations: ${service.operations.length}")
            println(s"Available Endpoints:\n")

            service.operations.foreach { opId =>
              model.findShape(opId.name).foreach { opDef =>
                opDef.shape match {
                  case op: OperationShape =>
                    val httpTrait = op.traits.find(_.id.name == "http")
                    val method    = httpTrait
                      .flatMap(_.value)
                      .map {
                        case NodeValue.Object(obj) =>
                          obj.collectFirst { case ("method", NodeValue.String(m)) => m }.getOrElse("GET")
                        case _ => "GET"
                      }
                      .getOrElse("GET")

                    val uri = httpTrait
                      .flatMap(_.value)
                      .map {
                        case NodeValue.Object(obj) =>
                          obj.collectFirst { case ("uri", NodeValue.String(u)) => u }.getOrElse("/")
                        case _ => "/"
                      }
                      .getOrElse("/")

                    println(s"  • $method $uri (${opDef.name})")

                  case _ =>
                }
              }
            }
            println()

          case _ =>
        }
      }

      // Analyze data structures
      println("═══════════════════════════════════════════════════════")
      println("CORE ENTITIES")
      println("═══════════════════════════════════════════════════════\n")

      println("Main business objects:\n")
      model.shapes.foreach { shapeDef =>
        shapeDef.shape match {
          case struct: StructureShape =>
            val doc = struct.traits
              .find(_.id.name == "documentation")
              .flatMap(_.value)
              .map {
                case NodeValue.String(s) => s
                case _                   => ""
              }
              .getOrElse("")

            val required = struct.members.count(m => m.traits.exists(_.id.name == "required"))

            if (doc.nonEmpty || struct.name == "Book" || struct.name == "Order" || struct.name == "AuthorProfile") {
              println(s"  • ${shapeDef.name}")
              if (doc.nonEmpty) println(s"    $doc")
              println(s"    Fields: ${struct.members.length} (${required} required)\n")
            }

          case _ =>
        }
      }

      // Analyze error types
      println("═══════════════════════════════════════════════════════")
      println("ERROR HANDLING")
      println("═══════════════════════════════════════════════════════\n")

      val errorShapes =
        model.shapes.filter(s =>
          s.name.endsWith("NotFound") || s.name.endsWith("Error") || s.name.endsWith("Failed") || s.name.endsWith(
            "Invalid"
          ) || s.name.endsWith("OutOfStock")
        )
      println(s"${errorShapes.length} error types defined:\n")
      errorShapes.foreach { shapeDef =>
        println(s"  • ${shapeDef.name}")
      }
      println()

      // Generate client code snippet
      println("═══════════════════════════════════════════════════════")
      println("GENERATED CLIENT CODE (Scala)")
      println("═══════════════════════════════════════════════════════\n")

      println("// Auto-generated Scala client interface")
      println("trait BookStoreClient {")
      println()

      model.findShape("BookStoreService").foreach { serviceDef =>
        serviceDef.shape match {
          case service: ServiceShape =>
            service.operations.foreach { opId =>
              model.findShape(opId.name).foreach { opDef =>
                opDef.shape match {
                  case op: OperationShape =>
                    val methodName = opId.name.charAt(0).toLower.toString + opId.name.substring(1)
                    val inputType  = op.input.map(_.name).getOrElse("Unit")
                    val outputType = op.output.map(_.name).getOrElse("Unit")

                    println(s"  def $methodName(req: $inputType): IO[Throwable, $outputType]")
                    println()

                  case _ =>
                }
              }
            }

          case _ =>
        }
      }

      println("}")
      println()

      // Statistics
      println("═══════════════════════════════════════════════════════")
      println("API STATISTICS")
      println("═══════════════════════════════════════════════════════\n")

      println(f"  Total Shapes: ${model.shapes.length}")

      val shapesByType = model.shapes.groupBy { shapeDef =>
        shapeDef.shape match {
          case _: StructureShape => "Structures"
          case _: OperationShape => "Operations"
          case _: ServiceShape   => "Services"
          case _: ListShape      => "Lists"
          case _: EnumShape      => "Enums"
          case _                 => "Other"
        }
      }

      shapesByType.toSeq.sortBy(_._1).foreach { case (shapeType, shapes) =>
        println(f"    - $shapeType: ${shapes.length}")
      }

      val totalDocumentedFields = model.shapes.foldLeft(0) { case (acc, shapeDef) =>
        shapeDef.shape match {
          case struct: StructureShape =>
            acc + struct.members.count(m => m.traits.exists(_.id.name == "documentation"))
          case _ => acc
        }
      }

      val totalFields = model.shapes.foldLeft(0) { case (acc, shapeDef) =>
        shapeDef.shape match {
          case struct: StructureShape => acc + struct.members.length
          case _                      => acc
        }
      }

      println(f"\n  Documentation Coverage:")
      println(f"    - Total Fields: $totalFields")
      println(s"    - Documented: $totalDocumentedFields (${
          if (totalFields > 0) (totalDocumentedFields * 100) / totalFields else 0
        }%)")
      println()

    case Left(error) =>
      println(s"✗ Parse error at line ${error.line}, column ${error.column}")
      println(s"  Message: ${error.message}")
  }
}
