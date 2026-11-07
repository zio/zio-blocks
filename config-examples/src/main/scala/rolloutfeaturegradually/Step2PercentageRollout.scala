package rolloutfeaturegradually

import zio.blocks.config._

/**
 * How to Roll Out a Feature Gradually with Flags — Step 2: A Percentage Rollout
 *
 * Turns the new recommendations panel on for 10% of readers, shows that each
 * reader always gets the same answer, measures the share across 1000 reader
 * ids, and salts the key so two rollouts pick independent readers.
 *
 * Run with: cd config-examples && sbt "runMain
 * rolloutfeaturegradually.Step2PercentageRollout"
 */
object Step2PercentageRollout {

  object RecommendationsPanel extends DynamicFlag[Boolean](false, "true@*/10%; false")

  object WishlistSharing extends DynamicFlag[Boolean](false, "true@*/10%; false")

  def main(args: Array[String]): Unit = {
    // The same reader id always lands in the same bucket, so the answer never flips.
    println(s"reader-42 bucket: ${Rollout.bucketFor("reader-42")}")
    println(s"reader-42 sees panel: ${(1 to 3).map(_ => RecommendationsPanel("reader-42"))}")

    val readers = (1 to 1000).map(i => s"reader-$i")
    val inPanel = readers.filter(RecommendationsPanel.evaluate(_))
    println(s"readers with the panel: ${inPanel.size} of ${readers.size}")

    // Bucketing hashes only the key, so every 10% rollout picks the same readers.
    val sameKey = readers.filter(WishlistSharing.evaluate(_))
    println(s"in both rollouts (plain key):  ${inPanel.intersect(sameKey).size}")

    // Prefix the key with the feature name to give each rollout its own readers.
    val panelSalted    = readers.filter(id => RecommendationsPanel.evaluate(s"recommendations:$id"))
    val wishlistSalted = readers.filter(id => WishlistSharing.evaluate(s"wishlist:$id"))
    println(s"in both rollouts (salted key): ${panelSalted.intersect(wishlistSalted).size}")
  }
}
