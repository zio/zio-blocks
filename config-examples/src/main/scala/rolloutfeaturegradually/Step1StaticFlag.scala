package rolloutfeaturegradually

import zio.blocks.config._

/**
 * How to Roll Out a Feature Gradually with Flags — Step 1: A Restart-Only Knob
 *
 * Declares the bookstore's free-shipping threshold as a StaticFlag, overrides
 * it from a registered FlagSource, and shows that a value which does not parse
 * stops the flag at first touch.
 *
 * Run with: cd config-examples && sbt "runMain
 * rolloutfeaturegradually.Step1StaticFlag"
 */
object Step1StaticFlag {

  // Orders at or above this many dollars ship for free.
  object FreeShippingThreshold extends StaticFlag[Int](35)

  // The most books a reader can put in one order.
  object MaxBooksPerOrder extends StaticFlag[Int](20)

  def main(args: Array[String]): Unit = {
    // Register every source before touching any flag.
    FlagSource.Registry.register(
      FlagSource.fromMap(
        Map(
          "rolloutfeaturegradually.Step1StaticFlag.FreeShippingThreshold" -> "25",
          "rolloutfeaturegradually.Step1StaticFlag.MaxBooksPerOrder"      -> "ten"
        ),
        "ops-overrides"
      )
    )

    println(s"name:      ${FreeShippingThreshold.name}")
    println(s"value:     ${FreeShippingThreshold()}")
    println(s"source:    ${FreeShippingThreshold.source}")

    try MaxBooksPerOrder()
    catch {
      case e: ExceptionInInitializerError => println(s"rejected:  ${e.getCause.getMessage}")
    }
  }
}
