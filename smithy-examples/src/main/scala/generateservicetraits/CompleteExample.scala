//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-next-smithy:0.0.56
//> using dep dev.zio::zio-blocks-next-codegen:0.0.56
//> using options -experimental
//> using file ServiceMapping.scala
// Run from this folder:  scala-cli run CompleteExample.scala
// Or from smithy-examples:  sbt "runMain generateservicetraits.CompleteExample"

package generateservicetraits

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import zio.blocks.smithy._
import zio.blocks.codegen.emit._

/**
 * Generate Scala Service Traits from a Smithy Model — Putting It Together
 *
 * Reads the order service model, maps it with `ServiceMapping` and writes the
 * Scala 3 source of the data types, the error type and the service trait to
 * `OrderService.scala`.
 */
object CompleteExample {
  def main(args: Array[String]): Unit = {
    val modelPath: String = if (args.length > 0) args(0) else "src/main/smithy/order-service.smithy"
    val outputDir: String = if (args.length > 1) args(1) else "target/generated-order-service"
    val text              = new String(Files.readAllBytes(Paths.get(modelPath)), StandardCharsets.UTF_8)

    val model: SmithyModel = SmithyModel.parse(text) match {
      case Right(parsed) => parsed
      case Left(error)   => throw new IllegalArgumentException(error.formatMessage)
    }

    val outputFile = Paths.get(outputDir, "OrderService.scala")
    Files.createDirectories(Paths.get(outputDir))
    Files.write(
      outputFile,
      ScalaEmitter.emit(ServiceMapping.scalaFile(model), EmitterConfig.default).getBytes(StandardCharsets.UTF_8)
    )
    println(s"Generated ${ServiceMapping.service(model).name} into $outputFile")
  }
}
