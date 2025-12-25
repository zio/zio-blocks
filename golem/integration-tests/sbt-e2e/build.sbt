import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import cloud.golem.sbt.GolemPlugin
import cloud.golem.sbt.GolemPlugin.autoImport._

import sbt._

ThisBuild / scalaVersion := "3.7.4"

lazy val printBridge = taskKey[Unit]("Generate the TypeScript bridge and write it to target/generated-main.ts")

lazy val root = (project in file("."))
  .enablePlugins(ScalaJSPlugin, GolemPlugin)
  .settings(
    // Minimal Scala.js setup; we’re not linking or running here, just validating generator wiring.
    scalaJSUseMainModuleInitializer := false,

    // Configure primitives explicitly (no example defaults in plugin code).
    golemAppName := "fixture-app",
    golemComponent := "fixture:component",
    golemBundleFileName := "scala-autowired.js",

    // Needed only for the provider-class (so it can compile against BridgeSpec types).
    libraryDependencies += "dev.zio" % "zio-golem-tooling-core" % "0.0.0-SNAPSHOT",

    // Metadata-driven bridge generation: sbt plugin loads this class from the project classpath and calls get().
    golemBridgeSpecProviderClass := "FixtureBridgeSpecProvider",

    printBridge := {
      val out = golemGenerateBridgeMainTs.value
      val file = target.value / "generated-main.ts"
      IO.write(file, out)
      streams.value.log.info(s"Wrote ${file.getAbsolutePath}")
    }
  )


