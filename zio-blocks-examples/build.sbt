ThisBuild / version      := "0.1.0"
ThisBuild / scalaVersion := "3.9.0"

val zioBlocksVersion = "0.0.56"

// For Scala 3.10+, artifacts are published as `zio-blocks-next-*`; older Scala versions use `zio-blocks-*`.
def zioBlocks(module: String) = Def.setting {
  val prefix = CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((3, minor)) if minor >= 10 => "zio-blocks-next"
    case _                               => "zio-blocks"
  }
  "dev.zio" %% s"$prefix-$module" % zioBlocksVersion
}

// The same artifact as `zioBlocks`, cross-published for Scala.js: `%%%` adds the `_sjs1` suffix.
def zioBlocksCross(module: String) = Def.setting {
  val prefix = CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((3, minor)) if minor >= 10 => "zio-blocks-next"
    case _                               => "zio-blocks"
  }
  "dev.zio" %%% s"$prefix-$module" % zioBlocksVersion
}

lazy val commonSettings = Seq(
  publish / skip := true,
  run / fork     := true,
  scalacOptions ++= Seq("-deprecation", "-encoding", "UTF-8", "-feature", "-unchecked"),
  // Scala 3.8+ artifacts are published with `@experimental` APIs, which consumers must opt into.
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((3, minor)) if minor >= 8 => Seq("-experimental")
      case _                              => Seq.empty
    }
  }
)

lazy val root = (project in file("."))
  .aggregate(effectIntegration, gettingStarted, ironRefinedTypes, paginatedApi, jsonLines, shareOneModelJVM, shareOneModelJS)
  .settings(
    commonSettings,
    name := "zio-blocks-examples",
    libraryDependencies ++= Seq(
      zioBlocks("ringbuffer").value
    )
  )

// Examples for "How to Use ZIO Blocks in a ZIO or cats-effect Application": the one build here that
// depends on ZIO and cats-effect at once. Run with: sbt "effectIntegration/runMain effectintegration.<Object>"
lazy val effectIntegration = (project in file("effect-integration"))
  .settings(
    commonSettings,
    name := "effect-integration",
    libraryDependencies ++= Seq(
      zioBlocks("schema").value,
      zioBlocks("sql").value,
      zioBlocks("streams").value,
      zioBlocks("async").value,
      zioBlocks("scope").value,
      "org.xerial"     % "sqlite-jdbc" % "3.53.4.0",
      "dev.zio"       %% "zio"         % "2.1.27",
      "dev.zio"       %% "zio-streams" % "2.1.27",
      "org.typelevel" %% "cats-effect" % "3.7.1",
      "co.fs2"        %% "fs2-core"    % "3.14.0"
    )
  )

// The program of "Your First Five Minutes" (docs/getting-started/first-five-minutes.md): Schema, Scope and Chunk together.
// Run with: sbt "gettingStarted/runMain gettingstarted.FirstFiveMinutes"
lazy val gettingStarted = (project in file("getting-started"))
  .settings(
    commonSettings,
    name := "getting-started",
    libraryDependencies ++= Seq(
      zioBlocks("schema").value,
      zioBlocks("scope").value
    )
  )

// Examples for "How to Use Iron Refined Types and Typed IDs with Schema": Iron 3.4 is built with Scala 3.9,
// where ZIO Blocks is `zio-blocks-next-*`. Iron's module depends on `zio-blocks-schema` (same packages),
// so that artifact is excluded. Run with: sbt "ironRefinedTypes/runMain ironrefinedtypes.<Object>"
lazy val ironRefinedTypes = (project in file("iron-refined-types"))
  .settings(
    commonSettings,
    name := "iron-refined-types",
    libraryDependencies ++= Seq(
      "dev.zio"            %% "zio-blocks-next-schema" % zioBlocksVersion,
      ("io.github.iltotore" %% "iron-zio-blocks-schema" % "3.4.1").exclude("dev.zio", "zio-blocks-schema_3")
    )
  )

// Examples for "How to Turn a Paginated API into a Stream": Stream.unfold and Stream.unfoldAsync walk a fake
// tracker API page by page, decode each item with Schema, and report errors as typed values. Run with:
// sbt "paginatedApi/runMain paginatedapi.<Object>"
lazy val paginatedApi = (project in file("paginated-api"))
  .settings(
    commonSettings,
    name := "paginated-api",
    libraryDependencies ++= Seq(
      zioBlocks("schema").value,
      zioBlocks("streams").value,
      zioBlocks("async").value
    )
  )

// Examples for "How to Read and Write JSON Lines (NDJSON) with Streams": one JSON value per line, read and written as a stream.
// Run with: sbt "jsonLines/runMain readandwritejsonlines.<Object>"
lazy val jsonLines = (project in file("json-lines"))
  .settings(
    commonSettings,
    name := "json-lines",
    libraryDependencies ++= Seq(
      zioBlocks("schema").value,
      zioBlocks("streams").value
    )
  )

// Examples for "Share One Model Between a Scala.js Page and a JVM Server": one Schema-based model, cross-built for the
// JVM (the server encodes it) and Scala.js (the client decodes it under Node). The server first, then the client:
//   shareOneModelJVM/runMain shareonemodel.Concept4ServeJsonExample   (keep it running)
//   shareOneModelJS/run                                 (in another terminal)
lazy val shareOneModel = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("share-one-model"))
  .settings(
    commonSettings,
    name := "share-one-model",
    libraryDependencies ++= Seq(
      zioBlocksCross("schema").value
    )
  )
  .jsSettings(
    scalaJSUseMainModuleInitializer := true
  )

lazy val shareOneModelJVM = shareOneModel.jvm
lazy val shareOneModelJS  = shareOneModel.js
