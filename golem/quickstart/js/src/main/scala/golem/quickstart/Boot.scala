package golem.quickstart

/**
 * Scala.js entry point.
 *
 * We rely on this to eagerly register agent implementations when the JS module
 * is loaded. This allows golem-cli to discover agent types during wrapper
 * generation.
 */
object Boot {
  def main(args: Array[String]): Unit = ()
}
