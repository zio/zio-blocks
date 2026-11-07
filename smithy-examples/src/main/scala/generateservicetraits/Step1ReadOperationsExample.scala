//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-next-smithy:0.0.56
//> using dep dev.zio::zio-blocks-next-codegen:0.0.56
//> using options -experimental
//> using file ServiceMapping.scala
// Run from this folder:  scala-cli run Step1ReadOperationsExample.scala
// Or from smithy-examples:  sbt "runMain generateservicetraits.Step1ReadOperationsExample"

package generateservicetraits

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import zio.blocks.smithy._

/**
 * Generate Scala Service Traits from a Smithy Model — Read the Operations
 *
 * Parses the order service model and prints each operation with its input,
 * output and errors.
 */
object Step1ReadOperationsExample {
  def main(args: Array[String]): Unit = {
    val path = if (args.length > 0) args(0) else "src/main/smithy/order-service.smithy"
    val text = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
    val model = SmithyModel.parse(text) match {
      case Right(parsed) => parsed
      case Left(error)   => throw new IllegalArgumentException(error.formatMessage)
    }
    ServiceMapping.operations(model).foreach { op =>
      val input  = op.input.map(_.name).getOrElse("none")
      val output = op.output.map(_.name).getOrElse("none")
      val errors = op.errors.map(_.name).mkString(", ")
      println(s"${op.name}: input=$input, output=$output, errors=[$errors]")
    }
  }
}
