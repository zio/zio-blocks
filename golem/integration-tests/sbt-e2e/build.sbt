import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import cloud.golem.sbt.GolemPlugin
import cloud.golem.sbt.GolemPlugin.autoImport._

import sbt._

ThisBuild / scalaVersion := "3.7.4"

lazy val printManifest = taskKey[Unit]("Ensure BridgeSpec manifest and write its contents to target/bridge-spec.properties")
lazy val printShim = taskKey[Unit]("Generate Scala shim and report its output path")

lazy val root = (project in file("."))
  .enablePlugins(ScalaJSPlugin, GolemPlugin)
  .settings(
    // Minimal Scala.js setup; we’re not linking or running here, just validating generator wiring.
    scalaJSUseMainModuleInitializer := false,

    // Configure primitives explicitly (no example defaults in plugin code).
    golemAppName := "fixture-app",
    golemComponent := "fixture:component",
    golemBundleFileName := "scala-autowired.js",

    printManifest := {
      val f = golemEnsureBridgeSpecManifest.value
      val out = target.value / "bridge-spec.properties"
      IO.copyFile(f, out)
      streams.value.log.info(s"Wrote ${out.getAbsolutePath}")
    },

    printShim := {
      val files = golemGenerateScalaShim.value
      streams.value.log.info(s"Generated shim files: ${files.map(_.getAbsolutePath).mkString(", ")}")
    }
  )


