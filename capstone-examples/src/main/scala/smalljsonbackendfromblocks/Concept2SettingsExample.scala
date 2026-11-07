package smalljsonbackendfromblocks

import zio.blocks.config._

/**
 * Build a Small JSON Backend from Blocks — Load the Settings
 *
 * Layers a stand-in environment over the YAML file over the defaults, loads the
 * whole `BookstoreConfig`, and prints where each setting came from.
 *
 * Run with: cd capstone-examples && sbt "runMain
 * smalljsonbackendfromblocks.Concept2SettingsExample"
 */
object Concept2SettingsExample {
  def main(args: Array[String]): Unit = {
    val env      = ConfigSource.fromMap(Map("db.file" -> "/tmp/bookstore-tutorial.db"), "env")
    val settings = Settings.layered(env)

    Config.loadWithProvenance[BookstoreConfig](settings) match {
      case Right(loaded) =>
        println(loaded.value)
        println(loaded.dump())
      case Left(errors) => errors.foreach(error => println(error.message))
    }
  }
}
