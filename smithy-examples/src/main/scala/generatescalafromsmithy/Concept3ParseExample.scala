//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-smithy:0.0.56
//> using dep dev.zio::zio-blocks-codegen:0.0.56
//> using options -experimental
//> using file OrdersModel.scala
// Run from this folder:  scala-cli run Concept3ParseExample.scala OrdersModel.scala
// Or from smithy-examples:  sbt "runMain generatescalafromsmithy.Concept3ParseExample"

package generatescalafromsmithy

import zio.blocks.smithy._
import zio.blocks.codegen.ir._
import zio.blocks.codegen.emit._

/**
 * Generate Scala Code from a Smithy Model — 3. Parse the Model
 *
 * Parses the order contract with `SmithyModel.parse`, then prints its shapes
 * and the members of `Order`.
 */
object Concept3ParseExample {
  def main(args: Array[String]): Unit = {
    import OrdersModel.ordersSmithy

    val model: SmithyModel = SmithyModel.parse(ordersSmithy) match {
      case Right(parsed) => parsed
      case Left(error)   => throw new IllegalArgumentException(error.formatMessage)
    }

    println(s"parse error: ${SmithyModel.parse("namespace com.acme.orders").left.map(_.formatMessage)}")

    model.shapes.foreach { definition =>
      println(s"${definition.name}: ${definition.shape.getClass.getSimpleName}")
    }

    val orderShape: StructureShape =
      model.shapes.collectFirst { case ShapeDefinition("Order", shape: StructureShape) => shape }.get

    orderShape.members.foreach { member =>
      val required = member.traits.contains(TraitApplication.required)
      println(s"${member.name}: target=${member.target}, required=$required")
    }
  }
}
