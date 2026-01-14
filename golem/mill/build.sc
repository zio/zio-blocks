import mill._
import mill.scalalib._

/**
 * Mill plugin build for zio-golem.
 *
 * This build is intentionally independent of the sbt build.
 */
object zioGolemMill extends ScalaModule {
  def scalaVersion = "3.3.7"

  // Publishing coordinates (match the runtime modules)
  def artifactName = "zio-golem-mill"

  def sources = T.sources { millSourcePath / "src" }

  def resources = T.sources { millSourcePath / "resources" }
}

