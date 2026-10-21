package rolloutfeaturegradually

import java.util.concurrent.{Executors, TimeUnit}

import zio.blocks.config._

/**
 * How to Roll Out a Feature Gradually with Flags — Step 4: Changing the Rollout
 * While the Service Runs
 *
 * Widens the rollout with update, rejects a malformed expression, reloads the
 * expression from a registered source, reads the update history, counters and
 * parse-error count, and drives reload from a ScheduledExecutorService.
 *
 * Run with: cd config-examples && sbt "runMain
 * rolloutfeaturegradually.Step4RuntimeChanges"
 */
object Step4RuntimeChanges {

  object RecommendationsPanel extends DynamicFlag[Boolean](false, "true@*/10%; false")

  def main(args: Array[String]): Unit = {
    val readers = (1 to 1000).map(i => s"reader-$i")
    val cohort  = readers.filter(RecommendationsPanel.evaluate(_))

    // Widen from 10% to 25%: everyone who had the panel keeps it.
    println(s"update to 25%:  ${RecommendationsPanel.update("true@*/25%; false")}")
    val widened = readers.filter(RecommendationsPanel.evaluate(_))
    println(s"readers with the panel: ${widened.size}, original cohort kept: ${cohort.forall(widened.contains)}")

    // A malformed expression is rejected and the old one stays in place.
    println(s"update to 150%: ${RecommendationsPanel.update("true@*/150%; false")}")
    println(s"expression:     ${RecommendationsPanel.expression}")

    // Reload reads the expression from the registered sources.
    println(s"reload, no source:  ${RecommendationsPanel.reload()}")
    FlagSource.Registry.register(
      FlagSource.fromMap(Map(RecommendationsPanel.name -> "true@*/50%; false"), "flag-service")
    )
    println(s"reload:             ${RecommendationsPanel.reload()}")
    println(s"reload again:       ${RecommendationsPanel.reload()}")
    FlagSource.Registry.register(FlagSource.fromMap(Map(RecommendationsPanel.name -> "true@*/fifty%"), "flag-service"))
    println(s"reload, bad value:  ${RecommendationsPanel.reload()}")

    RecommendationsPanel.updateHistory.foreach(r => println(s"history: ${r.oldExpression} -> ${r.newExpression}"))

    // apply counts each key; evaluate does not.
    RecommendationsPanel("reader-1")
    RecommendationsPanel("reader-1")
    RecommendationsPanel("reader-2")
    println(s"counters: ${RecommendationsPanel.counters.toList.sorted}")

    // A value the reader cannot parse falls back to the default and is counted.
    RecommendationsPanel.update("maybe")
    println(
      s"reader-1 sees panel: ${RecommendationsPanel("reader-1")}, parse errors: ${RecommendationsPanel.parseErrorCount}"
    )

    // Nothing reloads on its own: schedule it yourself.
    FlagSource.Registry.register(
      FlagSource.fromMap(Map(RecommendationsPanel.name -> "true@*/75%; false"), "flag-service")
    )
    val scheduler = Executors.newSingleThreadScheduledExecutor()
    scheduler.scheduleAtFixedRate(
      () =>
        RecommendationsPanel.reload() match {
          case Flag.ReloadResult.Updated(from, to) => println(s"rollout changed: $from -> $to")
          case Flag.ReloadResult.Failed(error)     => println(s"rollout rejected: ${error.message}")
          case Flag.ReloadResult.Unchanged         => ()
          case Flag.ReloadResult.NoSource          => println("no source provides the rollout")
        },
      0,
      30,
      TimeUnit.SECONDS
    )
    Thread.sleep(500)
    scheduler.shutdown()
  }
}
