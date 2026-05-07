package smithyexample

import zio.blocks.smithy._

/**
 * Smithy — Step 2: Building Models Programmatically
 *
 * This example demonstrates how to construct Smithy models in code by creating
 * shapes, adding traits, and assembling them into a complete API model.
 *
 * Run with: sbt "smithy-examples/runMain smithyexample.BuildingModelsAndTraits"
 */
@main def BuildingModelsAndTraits(): Unit = {
  println("=== Building a Smithy Model Programmatically ===\n")

  // Define a Product structure with members
  val productStructure = StructureShape(
    name = "Product",
    traits = List(
      TraitApplication.documentation("A product in the catalog")
    ),
    members = List(
      MemberDefinition(
        name = "id",
        target = ShapeId("smithy.api", "String"),
        traits = List(TraitApplication.required)
      ),
      MemberDefinition(
        name = "name",
        target = ShapeId("smithy.api", "String"),
        traits = List(TraitApplication.required)
      ),
      MemberDefinition(
        name = "price",
        target = ShapeId("smithy.api", "Double"),
        traits = Nil
      ),
      MemberDefinition(
        name = "inStock",
        target = ShapeId("smithy.api", "Boolean"),
        traits = Nil
      )
    )
  )

  // Define a GetProduct operation
  val getProductOperation = OperationShape(
    name = "GetProduct",
    traits = List(
      TraitApplication.http("GET", "/products/{id}"),
      TraitApplication.documentation("Retrieve a product by ID")
    ),
    input = Some(ShapeId("example.api", "GetProductInput")),
    output = Some(ShapeId("example.api", "Product")),
    errors = List(ShapeId("example.api", "ProductNotFound"))
  )

  // Define input shape
  val getProductInput = StructureShape(
    name = "GetProductInput",
    traits = Nil,
    members = List(
      MemberDefinition(
        name = "id",
        target = ShapeId("smithy.api", "String"),
        traits = List(TraitApplication.required)
      )
    )
  )

  // Define error shape
  val productNotFound = StructureShape(
    name = "ProductNotFound",
    traits = List(
      TraitApplication.error("client")
    ),
    members = List(
      MemberDefinition(
        name = "message",
        target = ShapeId("smithy.api", "String"),
        traits = List(TraitApplication.required)
      )
    )
  )

  // Define service
  val productService = ServiceShape(
    name = "ProductService",
    traits = List(
      TraitApplication.documentation("Product catalog API")
    ),
    version = Some("1.0.0"),
    operations = List(
      ShapeId("example.api", "GetProduct")
    ),
    resources = Nil,
    errors = Nil
  )

  // Assemble into a complete model
  val model = SmithyModel(
    version = "2.0",
    namespace = "example.api",
    useStatements = Nil,
    metadata = Map(
      "apiVersion" -> NodeValue.String("1.0.0"),
      "author"     -> NodeValue.String("Example Corp")
    ),
    shapes = List(
      ShapeDefinition("Product", productStructure),
      ShapeDefinition("GetProductInput", getProductInput),
      ShapeDefinition("ProductNotFound", productNotFound),
      ShapeDefinition("GetProduct", getProductOperation),
      ShapeDefinition("ProductService", productService)
    )
  )

  println("✓ Model created with:")
  println(s"  - Namespace: ${model.namespace}")
  println(s"  - Version: ${model.version}")
  println(s"  - Shapes: ${model.shapes.length}")
  println(s"  - Metadata entries: ${model.metadata.size}\n")

  // Inspect the constructed model
  println("=== Model Contents ===\n")
  model.shapes.foreach { shapeDef =>
    shapeDef.shape match {
      case struct: StructureShape =>
        println(s"Structure: ${shapeDef.name}")
        println(s"  Members: ${struct.members.map(_.name).mkString(", ")}")
        println(s"  Traits: ${struct.traits.length}")
        println()

      case service: ServiceShape =>
        println(s"Service: ${shapeDef.name}")
        println(s"  Operations: ${service.operations.map(_.name).mkString(", ")}")
        println()

      case op: OperationShape =>
        println(s"Operation: ${shapeDef.name}")
        op.input.foreach(in => println(s"  Input: ${in.name}"))
        op.output.foreach(out => println(s"  Output: ${out.name}"))
        if (op.errors.nonEmpty) {
          println(s"  Errors: ${op.errors.map(_.name).mkString(", ")}")
        }
        println()

      case _ =>
        println(s"Shape: ${shapeDef.name}")
        println()
    }
  }

  // Serialize back to IDL
  println("=== Serialized IDL ===\n")
  val idl = model.prettyPrint(indent = 2)
  println(idl)
}
