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

  // Define a Book structure with members
  val bookStructure = StructureShape(
    name = "Book",
    traits = List(
      TraitApplication.documentation("A book in the store catalog")
    ),
    members = List(
      MemberDefinition(
        name = "isbn",
        target = ShapeId("smithy.api", "String"),
        traits = List(TraitApplication.required)
      ),
      MemberDefinition(
        name = "title",
        target = ShapeId("smithy.api", "String"),
        traits = List(TraitApplication.required)
      ),
      MemberDefinition(
        name = "author",
        target = ShapeId("smithy.api", "String"),
        traits = List(TraitApplication.required)
      ),
      MemberDefinition(
        name = "price",
        target = ShapeId("smithy.api", "Double"),
        traits = Nil
      )
    )
  )

  // Define a GetBook operation
  val getBookOperation = OperationShape(
    name = "GetBook",
    traits = List(
      TraitApplication.http("GET", "/books/{isbn}"),
      TraitApplication.documentation("Retrieve a book by ISBN")
    ),
    input = Some(ShapeId("bookstore.api", "GetBookInput")),
    output = Some(ShapeId("bookstore.api", "Book")),
    errors = List(ShapeId("bookstore.api", "BookNotFound"))
  )

  // Define input shape
  val getBookInput = StructureShape(
    name = "GetBookInput",
    traits = Nil,
    members = List(
      MemberDefinition(
        name = "isbn",
        target = ShapeId("smithy.api", "String"),
        traits = List(TraitApplication.required)
      )
    )
  )

  // Define error shape
  val bookNotFound = StructureShape(
    name = "BookNotFound",
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
  val bookStoreService = ServiceShape(
    name = "BookStore",
    traits = List(
      TraitApplication.documentation("Book store catalog API")
    ),
    version = Some("1.0.0"),
    operations = List(
      ShapeId("bookstore.api", "GetBook")
    ),
    resources = Nil,
    errors = Nil
  )

  // Assemble into a complete model
  val model = SmithyModel(
    version = "2",
    namespace = "bookstore.api",
    useStatements = Nil,
    metadata = Map(
      "apiVersion" -> NodeValue.String("1.0.0"),
      "author"     -> NodeValue.String("Book Store Corp")
    ),
    shapes = List(
      ShapeDefinition("Book", bookStructure),
      ShapeDefinition("GetBookInput", getBookInput),
      ShapeDefinition("BookNotFound", bookNotFound),
      ShapeDefinition("GetBook", getBookOperation),
      ShapeDefinition("BookStore", bookStoreService)
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
