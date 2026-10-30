name                     := "smithy-examples"
version                  := "0.1.0"
ThisBuild / scalaVersion := "3.9.0"

publish / skip := true
run / fork     := true

ThisBuild / scalacOptions ++= Seq("-deprecation", "-encoding", "UTF-8", "-feature", "-unchecked")

// Scala 3.8+ artifacts are published with `@experimental` APIs, which consumers must opt into.
ThisBuild / scalacOptions ++= {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((3, minor)) if minor >= 8 => Seq("-experimental")
    case _                              => Seq.empty
  }
}

val zioBlocksVersion = "0.017"

// For Scala 3.10+, artifacts are published as `zio-blocks-next-*`; older Scala versions use `zio-blocks-*`.
def zioBlocks(module: String) = Def.setting {
  val prefix = CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((3, minor)) if minor >= 10 => "zio-blocks-next"
    case _                               => "zio-blocks"
  }
  "dev.zio" %% s"$prefix-$module" % zioBlocksVersion
}

libraryDependencies ++= Seq(
  zioBlocks("smithy").value,
  zioBlocks("codegen").value
)

// The generator in `generatescalafromsmithy.CompleteExample` lives in this root project. `orders-app` runs it
// before every compile, turning `src/main/smithy/orders.smithy` into Scala source, and compiles the result.
// The project is referred to by id: a direct `lazy val` reference would make the two projects depend on each other.
val generator = LocalProject("smithy-examples")

lazy val ordersApp = (project in file("orders-app"))
  .settings(
    publish / skip := true,
    run / fork     := true,
    libraryDependencies += zioBlocks("schema").value,
    Compile / sourceGenerators += Def.task {
      val model     = (generator / baseDirectory).value / "src" / "main" / "smithy" / "orders.smithy"
      val outputDir = (Compile / sourceManaged).value / "smithy"
      val classpath = (generator / Compile / fullClasspath).value.files
      (generator / Compile / runner).value
        .run(
          "generatescalafromsmithy.CompleteExample",
          classpath,
          Seq(model.getPath, outputDir.getPath),
          streams.value.log
        )
        .get
      (outputDir ** "*.scala").get
    }.taskValue
  )
