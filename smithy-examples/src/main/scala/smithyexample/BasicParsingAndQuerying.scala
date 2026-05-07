package smithyexample

import zio.blocks.smithy._

/**
 * Smithy — Step 1: Basic Parsing and Querying
 *
 * This example demonstrates how to parse Smithy IDL text, find shapes by name,
 * and access their structure and metadata.
 *
 * Run with: sbt "smithy-examples/runMain smithyexample.BasicParsingAndQuerying"
 */
@main def BasicParsingAndQuerying(): Unit = {
  val smithyText = """$version: "2"
namespace example.api

@documentation("A user in the system")
structure User {
  @required
  @documentation("Unique user identifier")
  id: String

  @required
  name: String

  @documentation("User email address")
  email: String
}

operation GetUser {
  @http(method: "GET", uri: "/users/{id}")
  input: GetUserInput
  output: User
  errors: [UserNotFound]
}

structure GetUserInput {
  @required
  @httpLabel
  id: String
}

@error("client")
structure UserNotFound {
  @required
  message: String
}
"""

  println("=== Parsing Smithy IDL ===\n")
  SmithyModel.parse(smithyText) match {
    case Right(model) =>
      println(s"✓ Successfully parsed model")
      println(s"  Namespace: ${model.namespace}")
      println(s"  Total shapes: ${model.shapes.length}\n")

      println("=== Finding the User Structure ===\n")
      model.findShape("User").foreach { userDef =>
        userDef.shape match {
          case struct: StructureShape =>
            println(s"✓ Found shape: ${userDef.name}")
            println(s"  Type: Structure")
            println(s"  Members: ${struct.members.length}")
            println(s"  Traits: ${struct.traits.length}\n")

            println("  Members:")
            struct.members.foreach { member =>
              val isRequired = member.traits.exists(_.id.name == "required")
              println(s"    - ${member.name}: ${member.target.name} (required: $isRequired)")
            }
            println()

          case _ => println("Found shape but it's not a structure")
        }
      }

      println("=== Finding the Operation ===\n")
      model.findShape("GetUser").foreach { opDef =>
        opDef.shape match {
          case op: OperationShape =>
            println(s"✓ Found shape: ${opDef.name}")
            println(s"  Type: Operation")
            op.input.foreach(in => println(s"  Input: ${in.name}"))
            op.output.foreach(out => println(s"  Output: ${out.name}"))
            println(s"  Errors: ${op.errors.map(_.name).mkString(", ")}")
            println()

          case _ => println("Found shape but it's not an operation")
        }
      }

      println("=== All Shape Identifiers ===\n")
      val allIds = model.allShapeIds
      println(s"Total shapes in model: ${allIds.length}")
      allIds.foreach(id => println(s"  - ${id.namespace}#${id.name}"))
      println()

    case Left(error) =>
      println(s"✗ Parse error at line ${error.line}, column ${error.column}")
      println(s"  Message: ${error.message}")
  }
}
