package smithyexample

import zio.blocks.smithy._

/**
 * Smithy — Complete Example: E-Commerce API
 *
 * A complete real-world example showing a full e-commerce API model with
 * parsing, querying, analysis, and code generation in one comprehensive
 * workflow.
 *
 * Run with: sbt "smithy-examples/runMain smithyexample.CompleteECommerceAPI"
 */
@main def CompleteECommerceAPI(): Unit = {
  val ecommerceSmithyText = """$version: "2"
namespace ecommerce.api

service ECommerceAPI {
  version: "1.0.0"
  operations: [ListProducts, GetProduct, CreateOrder, GetOrder]
}

@http(method: "GET", uri: "/api/v1/products")
operation ListProducts {
  input: ListProductsInput
  output: ListProductsOutput
}

structure ListProductsInput {
  category: String
  limit: Integer
}

structure ListProductsOutput {
  @required
  products: ProductList

  @required
  total: Integer
}

list ProductList {
  member: Product
}

@http(method: "GET", uri: "/api/v1/products/{id}")
operation GetProduct {
  input: GetProductInput
  output: Product
  errors: [ProductNotFound]
}

structure GetProductInput {
  @required
  id: String
}

structure ProductNotFound {
  @required
  message: String
}

@documentation("A product available for purchase")
structure Product {
  @required
  @documentation("Unique product ID")
  id: String

  @required
  @documentation("Product name")
  name: String

  @documentation("Product description")
  description: String

  @required
  @documentation("Price in cents (e.g., 9999 = $99.99)")
  priceInCents: Long

  @required
  @documentation("Whether product is currently in stock")
  inStock: Boolean

  @required
  @documentation("Number of items in stock")
  stockQuantity: Integer

  category: String
}

@http(method: "POST", uri: "/api/v1/orders")
operation CreateOrder {
  input: CreateOrderInput
  output: Order
  errors: [InvalidRequest, InsufficientStock]
}

structure CreateOrderInput {
  @required
  items: OrderItemList

  @required
  customerEmail: String

  shippingAddress: Address
}

list OrderItemList {
  member: OrderItem
}

structure OrderItem {
  @required
  productId: String

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
}

structure InvalidRequest {
  @required
  message: String

  field: String
}

structure InsufficientStock {
  @required
  productId: String

  @required
  requested: Integer

  @required
  available: Integer
}

@http(method: "GET", uri: "/api/v1/orders/{id}")
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
}

@documentation("An order placed by a customer")
structure Order {
  @required
  @documentation("Unique order ID")
  id: String

  @required
  @documentation("Order creation timestamp (Unix seconds)")
  createdAt: Long

  @required
  @documentation("Customer email")
  customerEmail: String

  @required
  items: OrderItemList

  @required
  @documentation("Total order amount in cents")
  totalInCents: Long

  @required
  status: OrderStatus

  shippingAddress: Address
}

enum OrderStatus {
  PENDING
  CONFIRMED
  SHIPPED
  DELIVERED
  CANCELLED
}
"""

  println("╔════════════════════════════════════════════════════════════╗")
  println("║        Complete E-Commerce API Example                     ║")
  println("║   Parsing, Analysis, and Code Generation with Smithy       ║")
  println("╚════════════════════════════════════════════════════════════╝\n")

  SmithyModel.parse(ecommerceSmithyText) match {
    case Right(model) =>
      println(s"✓ Successfully parsed E-Commerce API model")
      println(s"  Namespace: ${model.namespace}")
      println(s"  Total shapes: ${model.shapes.length}\n")

      // Step 1: Analyze Service
      println("═══════════════════════════════════════════════════════")
      println("STEP 1: Service Analysis")
      println("═══════════════════════════════════════════════════════\n")

      model.findShape("ECommerceAPI").foreach { serviceDef =>
        serviceDef.shape match {
          case service: ServiceShape =>
            println(s"Service: ${serviceDef.name}")
            println(s"Version: ${service.version.getOrElse("N/A")}")
            println(s"Operations: ${service.operations.length}\n")

            println("Available Operations:")
            service.operations.foreach { opId =>
              model.findShape(opId.name).foreach { opDef =>
                opDef.shape match {
                  case op: OperationShape =>
                    val httpTrait = op.traits.find(_.id.name == "http")
                    val method    = httpTrait
                      .flatMap(_.value)
                      .map {
                        case NodeValue.Object(obj) =>
                          obj.collectFirst { case ("method", NodeValue.String(m)) => m }
                            .getOrElse("GET")
                        case _ => "GET"
                      }
                      .getOrElse("GET")

                    val uri = httpTrait
                      .flatMap(_.value)
                      .map {
                        case NodeValue.Object(obj) =>
                          obj.collectFirst { case ("uri", NodeValue.String(u)) => u }
                            .getOrElse("/")
                        case _ => "/"
                      }
                      .getOrElse("/")

                    println(s"  • $method $uri  (${opDef.name})")

                  case _ =>
                }
              }
            }
            println()

          case _ =>
        }
      }

      // Step 2: Structure Analysis
      println("═══════════════════════════════════════════════════════")
      println("STEP 2: Data Structure Analysis")
      println("═══════════════════════════════════════════════════════\n")

      println("Core Structures:")
      model.shapes.foreach { shapeDef =>
        shapeDef.shape match {
          case struct: StructureShape =>
            val doc = struct.traits
              .find(_.id.name == "documentation")
              .flatMap(_.value)
              .map {
                case NodeValue.String(s) => s
                case _                   => "No description"
              }
              .getOrElse("No description")

            val required = struct.members.count(m => m.traits.exists(_.id.name == "required"))
            println(s"  • ${shapeDef.name}")
            println(s"    ${doc}")
            println(s"    Fields: ${struct.members.length} (required: $required)\n")

          case _ =>
        }
      }

      // Step 3: Error Handling
      println("═══════════════════════════════════════════════════════")
      println("STEP 3: Error Handling Analysis")
      println("═══════════════════════════════════════════════════════\n")

      val errors = model.shapes.filter { shapeDef =>
        shapeDef.shape.traits.exists(_.id.name == "error")
      }

      println(s"Defined Errors: ${errors.length}\n")
      errors.foreach { shapeDef =>
        println(s"  • ${shapeDef.name}")
      }
      println()

      // Step 4: Code Generation
      println("═══════════════════════════════════════════════════════")
      println("STEP 4: Generated TypeScript Client Stubs")
      println("═══════════════════════════════════════════════════════\n")

      println("// Auto-generated TypeScript client for E-Commerce API")
      println("export class ECommerceAPIClient {")
      println("  private baseUrl: string;")
      println()
      println("  constructor(baseUrl: string) {")
      println("    this.baseUrl = baseUrl;")
      println("  }")
      println()

      model.findShape("ECommerceAPI").foreach { serviceDef =>
        serviceDef.shape match {
          case service: ServiceShape =>
            service.operations.foreach { opId =>
              model.findShape(opId.name).foreach { opDef =>
                opDef.shape match {
                  case op: OperationShape =>
                    val methodName = opId.name.charAt(0).toLower.toString + opId.name.substring(1)
                    val inputType  = op.input.map(_.name).getOrElse("void")
                    val outputType = op.output.map(_.name).getOrElse("void")

                    println(s"  async $methodName(input: $inputType): Promise<$outputType> {")
                    println("    // Implementation auto-generated by Smithy code generator")
                    println("    return {} as $outputType;")
                    println("  }")
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

      // Step 5: Export to IDL
      println("═══════════════════════════════════════════════════════")
      println("STEP 5: Model Export (First 50 lines of serialized IDL)")
      println("═══════════════════════════════════════════════════════\n")

      val idl   = model.prettyPrint(indent = 2)
      val lines = idl.split("\n")
      lines.take(50).foreach(println)
      if (lines.length > 50) {
        println(s"\n... (${lines.length - 50} more lines)")
      }
      println()

      // Summary
      println("═══════════════════════════════════════════════════════")
      println("SUMMARY")
      println("═══════════════════════════════════════════════════════\n")

      println(s"✓ Model successfully processed")
      println(s"  - Total shapes: ${model.shapes.length}")
      println(s"  - Structures: ${model.shapes.count(_.shape.isInstanceOf[StructureShape])}")
      println(s"  - Operations: ${model.shapes.count(_.shape.isInstanceOf[OperationShape])}")
      println(
        s"  - Enums: ${model.shapes.count(s => s.shape.isInstanceOf[EnumShape] || s.shape.isInstanceOf[IntEnumShape])}"
      )
      println(s"  - Errors: ${errors.length}\n")

    case Left(error) =>
      println(s"✗ Parse error at line ${error.line}, column ${error.column}")
      println(s"  Message: ${error.message}")
  }
}
