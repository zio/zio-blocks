package rolloutfeaturegradually

import zio.blocks.config._

/**
 * How to Roll Out a Feature Gradually with Flags — Step 7: Testing Code That
 * Uses Flags
 *
 * A helper that registers sources for one block of code and clears the global
 * registry before and after it, used to check the panel decision for two
 * different expressions.
 *
 * Run with: cd config-examples && sbt "runMain
 * rolloutfeaturegradually.Step7Testing"
 */
object Step7Testing {

  object RecommendationsPanel extends DynamicFlag[Boolean](false, "false")

  def withFlagSources[A](sources: FlagSource*)(body: => A): A = {
    FlagSource.Registry.clear()
    sources.foreach(FlagSource.Registry.register)
    try body
    finally FlagSource.Registry.clear()
  }

  def showPanel(readerId: String, region: String): Boolean = RecommendationsPanel(readerId, region)

  def main(args: Array[String]): Unit = {
    val everyoneInEu = withFlagSources(FlagSource.fromMap(Map(RecommendationsPanel.name -> "true@*/eu; false"))) {
      RecommendationsPanel.reload()
      (showPanel("reader-42", "eu"), showPanel("reader-42", "us"))
    }
    println(s"eu, us with 'true@*/eu; false': $everyoneInEu")

    val nobody = withFlagSources(FlagSource.fromMap(Map(RecommendationsPanel.name -> "false"))) {
      RecommendationsPanel.reload()
      (showPanel("reader-42", "eu"), showPanel("reader-42", "us"))
    }
    println(s"eu, us with 'false':            $nobody")
    println(s"sources left registered: ${FlagSource.Registry.all.size}")
  }
}
