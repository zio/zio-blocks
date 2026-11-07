//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-next-smithy:0.0.56
//> using dep dev.zio::zio-blocks-next-codegen:0.0.56
//> using options -experimental
//> using file ServiceMapping.scala
// Run from this folder:  scala-cli run Step2ServiceTraitExample.scala
// Or from smithy-examples:  sbt "runMain generateservicetraits.Step2ServiceTraitExample"

package generateservicetraits

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import zio.blocks.smithy._
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

/**
 * Generate Scala Service Traits from a Smithy Model — Map Operations to Methods
 *
 * Maps each operation of the order service to a method of the service trait and
 * prints the emitted trait.
 */
object Step2ServiceTraitExample {
  def main(args: Array[String]): Unit = {
    val path  = if (args.length > 0) args(0) else "src/main/smithy/order-service.smithy"
    val text  = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
    val model = SmithyModel.parse(text) match {
      case Right(parsed) => parsed
      case Left(error)   => throw new IllegalArgumentException(error.formatMessage)
    }
    val file = ScalaFile(PackageDecl(model.namespace), types = List(ServiceMapping.serviceTrait(model)))
    println(ScalaEmitter.emit(file, EmitterConfig.default))
  }
}
