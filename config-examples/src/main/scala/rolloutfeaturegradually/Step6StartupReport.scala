package rolloutfeaturegradually

import zio.blocks.config._

/**
 * How to Roll Out a Feature Gradually with Flags — Step 6: Catching Typos at
 * Startup
 *
 * Prints near-miss warnings for a flag whose override key has the wrong case,
 * and a table of every flag the service touched, with the payment token kept
 * out of it.
 *
 * Run with: cd config-examples && sbt "runMain
 * rolloutfeaturegradually.Step6StartupReport"
 */
object Step6StartupReport {

  object FreeShippingThreshold extends StaticFlag[Int](35)

  object RecommendationsPanel extends DynamicFlag[Boolean](false, "true@*/10%; false")

  object PaymentToken extends StaticFlag[Secret](Secret("dev-token"))

  def main(args: Array[String]): Unit = {
    FlagSource.Registry.register(
      FlagSource.fromMap(
        Map(
          // Typo: the key is lower-case, so the flag never sees it.
          "rolloutfeaturegradually.step6startupreport.freeshippingthreshold" -> "25",
          "rolloutfeaturegradually.Step6StartupReport.PaymentToken"          -> "tok-live-123"
        ),
        "ops-overrides"
      )
    )

    val flags = List(FreeShippingThreshold.name, RecommendationsPanel.name, PaymentToken.name)
    flags.flatMap(Flag.nearMissWarnings).foreach(println)
    println(Flag.dump())
  }
}
