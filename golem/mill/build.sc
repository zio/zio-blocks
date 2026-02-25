import mill._
import mill.scalalib._

/**
 * Mill plugin build for zio-golem.
 *
 * This build is intentionally independent of the sbt build.
 *
 * Mill's own core libraries (scalalib, scalajslib) are available on the classpath
 * automatically when this build.sc is evaluated by Mill. The GolemAutoRegister trait
 * extends ScalaJSModule and references scalajslib API types.
 */
object zioGolemMill extends ScalaModule {
  def scalaVersion = "3.3.7"

  // Publishing coordinates (match the runtime modules)
  def artifactName = "zio-golem-mill"

  def sources = T.sources { millSourcePath / "src" }

  def resources = T.sources { millSourcePath / "resources" }

  def ivyDeps = Agg(
    ivy"org.scalameta::scalameta:4.14.5"
  )
}
