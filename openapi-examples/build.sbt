name                     := "openapi-examples"
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

// Scala 3.9+ uses the `zio-blocks-next-*` artifacts (built with Scala 3.9.0, unreadable by older Scala 3 compilers); older Scala versions use `zio-blocks-*`.
def zioBlocks(module: String) = Def.setting {
  val prefix = CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((3, minor)) if minor >= 9 => "zio-blocks-next"
    case _                              => "zio-blocks"
  }
  "dev.zio" %% s"$prefix-$module" % zioBlocksVersion
}

libraryDependencies ++= Seq(
  zioBlocks("openapi").value,
  zioBlocks("schema-yaml").value,
  zioBlocks("codegen").value
)

// The generator in `generatescalatypesfromopenapi.CompleteExample` lives in this root project. `bookstore-app` runs it
// before every compile, turning `src/main/openapi/bookstore.json` into Scala source, and compiles the result.
// The project is referred to by id: a direct `lazy val` reference would make the two projects depend on each other.
val generator = LocalProject("openapi-examples")

lazy val bookstoreApp = (project in file("bookstore-app"))
  .settings(
    publish / skip := true,
    run / fork     := true,
    // The generator is run with its default Scala 3 output (`derives`, `enum`), and the app code uses the generated
    // types, so nothing in this project is compiled on Scala 2.13.
    Compile / sources := {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, _)) => Seq.empty
        case _            => (Compile / sources).value
      }
    },
    libraryDependencies ++= Seq(
      zioBlocks("schema").value,
      zioBlocks("openapi").value
    ),
    // The app reads the spec's examples at run time, so the spec is also on its classpath.
    Compile / unmanagedResourceDirectories += (generator / baseDirectory).value / "src" / "main" / "openapi",
    Compile / sourceGenerators += Def.task {
      val spec      = (generator / baseDirectory).value / "src" / "main" / "openapi" / "bookstore.json"
      val outputDir = (Compile / sourceManaged).value / "openapi"
      val classpath = (generator / Compile / fullClasspath).value.files
      (generator / Compile / runner).value
        .run(
          "generatescalatypesfromopenapi.CompleteExample",
          classpath,
          Seq(spec.getPath, outputDir.getPath),
          streams.value.log
        )
        .get
      (outputDir ** "*.scala").get
    }.taskValue
  )

// `service-app` compiles the service trait that the H-66 guide generates from `bookstore-service.json`. The trait
// refers to types of the same spec, so the data types generator runs on that spec first and both outputs land in
// package `com.example.bookstore`. Like the apps above, nothing in this project is compiled on Scala 2.13.
lazy val serviceApp = (project in file("service-app"))
  .settings(
    publish / skip := true,
    run / fork     := true,
    Compile / sources := {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, _)) => Seq.empty
        case _            => (Compile / sources).value
      }
    },
    libraryDependencies += zioBlocks("schema").value,
    Compile / sourceGenerators += Def.task {
      val spec      = (generator / baseDirectory).value / "src" / "main" / "openapi" / "bookstore-service.json"
      val outputDir = (Compile / sourceManaged).value / "openapi"
      val classpath = (generator / Compile / fullClasspath).value.files
      val generate  = (generator / Compile / runner).value
      val log       = streams.value.log
      generate
        .run("generatescalatypesfromopenapi.CompleteExample", classpath, Seq(spec.getPath, outputDir.getPath), log)
        .get
      generate
        .run("generateservicetraits.CompleteExample", classpath, Seq(spec.getPath, outputDir.getPath), log)
        .get
      (outputDir ** "*.scala").get
    }.taskValue
  )
