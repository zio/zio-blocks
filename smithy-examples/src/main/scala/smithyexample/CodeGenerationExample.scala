package smithyexample

import zio.blocks.smithy._

/**
 * Smithy — Step 3: Code Generation from Models
 *
 * This example demonstrates how to use Smithy models to generate client and
 * server stubs, showing the foundation for code generation tools.
 *
 * Run with: sbt "smithy-examples/runMain smithyexample.CodeGenerationExample"
 */
@main def CodeGenerationExample(): Unit = {
  val smithyText = """$version: "2"
namespace petstore.api

service PetStore {
  operations: [ListPets, GetPet, CreatePet, DeletePet]
}

@http(method: "GET", uri: "/pets")
operation ListPets {
  input: ListPetsInput
  output: ListPetsOutput
}

structure ListPetsInput {
  limit: Integer
}

structure ListPetsOutput {
  @required
  pets: PetList
}

list PetList {
  member: Pet
}

@http(method: "GET", uri: "/pets/{id}")
operation GetPet {
  input: GetPetInput
  output: Pet
  errors: [PetNotFound]
}

structure GetPetInput {
  @required
  id: String
}

structure PetNotFound {
  @required
  message: String
}

@http(method: "POST", uri: "/pets")
operation CreatePet {
  input: CreatePetInput
  output: Pet
}

structure CreatePetInput {
  @required
  name: String

  @required
  species: String

  age: Integer
}

@http(method: "DELETE", uri: "/pets/{id}")
operation DeletePet {
  input: DeletePetInput
  output: DeletePetOutput
  errors: [PetNotFound]
}

structure DeletePetInput {
  @required
  id: String
}

structure DeletePetOutput {}

structure Pet {
  @required
  id: String

  @required
  name: String

  @required
  species: String

  age: Integer
}
"""

  println("=== Code Generation Tool ===\n")
  println("Parsing Smithy model and generating code stubs...\n")

  SmithyModel.parse(smithyText) match {
    case Right(model) =>
      println(s"✓ Parsed service model: ${model.namespace}\n")

      // Find the service shape
      val serviceOpt = model.shapes.find(_.name == "PetStore")

      serviceOpt match {
        case Some(serviceDef) =>
          serviceDef.shape match {
            case service: ServiceShape =>
              println(s"=== Generating TypeScript Client ===\n")
              println("// Auto-generated TypeScript client")
              println("export class PetStoreClient {")
              println("  private baseUrl: string;")
              println()
              println("  constructor(baseUrl: string) {")
              println("    this.baseUrl = baseUrl;")
              println("  }")
              println()

              // Generate methods for each operation
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

                      println(
                        s"  async ${toLowerCamelCase(opDef.name)}(input: ${op.input.map(_.name).getOrElse("void")}): Promise<${op.output.map(_.name).getOrElse("void")}> {"
                      )
                      println(s"    return this.request('$method', '...', input);")
                      println("  }")
                      println()

                    case _ =>
                  }
                }
              }

              println("}")
              println()

              println(s"=== Generating Scala Server Trait ===\n")
              println("// Auto-generated Scala service trait")
              println("trait PetStoreService {")
              println()

              service.operations.foreach { opId =>
                model.findShape(opId.name).foreach { opDef =>
                  opDef.shape match {
                    case op: OperationShape =>
                      val inputType  = op.input.map(_.name).getOrElse("Unit")
                      val outputType = op.output.map(_.name).getOrElse("Unit")
                      println(s"  def ${toLowerCamelCase(opDef.name)}(req: $inputType): IO[Throwable, $outputType]")
                      println()

                    case _ =>
                  }
                }
              }

              println("}")
              println()

              println(s"=== Operation Summary ===\n")
              service.operations.foreach { opId =>
                model.findShape(opId.name).foreach { opDef =>
                  opDef.shape match {
                    case op: OperationShape =>
                      println(s"Operation: ${opDef.name}")
                      op.input.foreach(in => println(s"  Input: $in"))
                      op.output.foreach(out => println(s"  Output: $out"))
                      if (op.errors.nonEmpty) {
                        println(s"  Errors: ${op.errors.map(_.name).mkString(", ")}")
                      }
                      println()

                    case _ =>
                  }
                }
              }

            case _ => println("Service shape is not a service")
          }

        case None => println("Could not find PetStore service")
      }

    case Left(error) =>
      println(s"✗ Parse error: ${error.message}")
  }

  def toLowerCamelCase(str: String): String =
    str.head.toLower.toString + str.tail
}
