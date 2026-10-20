package rolloutfeaturegradually

import zio.blocks.config._

/**
 * How to Roll Out a Feature Gradually with Flags — Step 5: Checking an
 * Expression Before You Ship It
 *
 * Runs Rollout.validate on candidate expressions and refuses to apply one that
 * does not parse or comes back with warnings.
 *
 * Run with: cd config-examples && sbt "runMain
 * rolloutfeaturegradually.Step5Validation"
 */
object Step5Validation {

  object RecommendationsPanel extends DynamicFlag[Boolean](false, "true@*/10%; false")

  def ship(expression: String): Unit =
    Rollout.validate(expression) match {
      case Left(error)                          => println(s"rejected '$expression': ${error.message}")
      case Right(warnings) if warnings.nonEmpty => println(s"held back '$expression': ${warnings.mkString("; ")}")
      case Right(_)                             =>
        RecommendationsPanel.update(expression)
        println(s"shipped '$expression'")
    }

  def main(args: Array[String]): Unit = {
    ship("false; true@*/eu/50%")
    ship("true@*/eu/60%; true@*/eu/50%; false")
    ship("true@*/eu/150%; false")
    ship("true@*/eu/50%; false")
    println(s"expression: ${RecommendationsPanel.expression}")
  }
}
