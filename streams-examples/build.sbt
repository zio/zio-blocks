name         := "streams-examples"
version      := "0.1.0"
scalaVersion := "3.9.0"

publish / skip := true
run / fork     := true

scalacOptions ++= Seq("-deprecation", "-encoding", "UTF-8", "-feature", "-unchecked")

// Scala 3.8+ artifacts are published with `@experimental` APIs, which consumers must opt into.
scalacOptions ++= {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((3, minor)) if minor >= 8 => Seq("-experimental")
    case _                              => Seq.empty
  }
}

// Scala 3-only examples are skipped on Scala 2.13: `zio.sbt.ExprEval.show` (zio-sbt-source) expands to a
// `private[sbt]` call that 2.13 rejects, and `MixedFeedAuditWalkthrough` is built on union types (`Int | String`).
val scala3OnlyExamples = Set("MixedFeedAuditWalkthrough.scala")

Compile / unmanagedSources := {
  val sources = (Compile / unmanagedSources).value
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, _)) =>
      sources.filterNot(f => scala3OnlyExamples(f.getName) || IO.read(f).contains("import zio.sbt."))
    case _ => sources
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
  zioBlocks("streams").value,
  zioBlocks("chunk").value,
  "com.lihaoyi" %% "sourcecode"     % "0.4.4",
  "dev.zio"     %% "zio-sbt-source" % "0.8.6"
)
