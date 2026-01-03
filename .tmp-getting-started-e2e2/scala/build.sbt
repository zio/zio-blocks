import org.scalajs.linker.interface.ModuleKind

ThisBuild / scalaVersion := "3.7.4"

lazy val root = project
  .in(file("."))
  .enablePlugins(org.scalajs.sbtplugin.ScalaJSPlugin, cloud.golem.sbt.GolemPlugin)
  .settings(
    scalaJSUseMainModuleInitializer := false,
    Compile / scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.ESModule)),
    scalacOptions += "-experimental",
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio-golem-core"  % "0.0.0-SNAPSHOT",
      "dev.zio" %%% "zio-golem-model" % "0.0.0-SNAPSHOT",
      "dev.zio" %% "zio-golem-macros" % "0.0.0-SNAPSHOT"
    ),
    cloud.golem.sbt.GolemPlugin.autoImport.golemBundleFileName := "scala.js",
    cloud.golem.sbt.GolemPlugin.autoImport.golemAgentGuestWasmFile :=
      (baseDirectory.value / ".." / "app" / "wasm" / "agent_guest.wasm").getCanonicalFile
  )
