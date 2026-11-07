package rolloutfeaturegradually

import zio.blocks.config._

/**
 * How to Roll Out a Feature Gradually with Flags — Step 3: Targeting a Region
 *
 * Offers the recommendations panel to half of the readers in the eu region and
 * to nobody else, passing the region as a path attribute after the reader id.
 *
 * Run with: cd config-examples && sbt "runMain
 * rolloutfeaturegradually.Step3RegionTargeting"
 */
object Step3RegionTargeting {

  object RecommendationsPanel extends DynamicFlag[Boolean](false, "true@*/eu/50%; false")

  def main(args: Array[String]): Unit = {
    val readers = (1 to 1000).map(i => s"reader-$i")

    // The path is "<reader id>/<region>", so the selector has two segments.
    val eu = readers.count(id => RecommendationsPanel.evaluate(id, "eu"))
    val us = readers.count(id => RecommendationsPanel.evaluate(id, "us"))
    println(s"eu readers with the panel: $eu of ${readers.size}")
    println(s"us readers with the panel: $us of ${readers.size}")

    // Forgetting the region gives a one-segment path, which never matches */eu.
    val noRegion = readers.count(id => RecommendationsPanel.evaluate(id))
    println(s"readers with no region:    $noRegion of ${readers.size}")
  }
}
