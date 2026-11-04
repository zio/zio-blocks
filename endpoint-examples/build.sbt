name         := "endpoint-examples"
version      := "0.1.0"
scalaVersion := "3.9.0"
crossScalaVersions := Seq("3.9.0", "3.10.0")

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

val zioBlocksVersion = "0.0.56"

// For Scala 3.10+, artifacts are published as `zio-blocks-next-*`; older Scala versions use `zio-blocks-*`.
def zioBlocks(module: String) = Def.setting {
  val prefix = CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((3, minor)) if minor >= 10 => "zio-blocks-next"
    case _                              => "zio-blocks"
  }
  "dev.zio" %% s"$prefix-$module" % zioBlocksVersion
}

libraryDependencies ++= Seq(
  zioBlocks("endpoint").value
)

Compile / unmanagedSourceDirectories ++= {
  CrossVersion.partialVersion(scalaVersion.value) match {
    // BulkEndpointsExample needs the `zio-blocks-next-*` artifacts, so the
    // `scala-3.7` directory is only included where next is published.
    case Some((3, n)) if n >= 10 => Seq(baseDirectory.value / "src" / "main" / "scala-3.7")
    case _                       => Seq.empty
  }
}

// Examples written in Scala 3-only syntax (`@main`, `derives`, `enum`, `given`/`using`, context functions `?=>`,
// `extension`, `opaque`/`inline`, top-level definitions, wildcard `import x.*`) are skipped on Scala 2.13, so the rest of the build compiles there.
val scala3OnlySyntax =
  """(?m)^\s*@main\b|^(?!\s*(\*|//)).*\bderives\s+[A-Z]|^\s*(final\s+)?enum\s|\?=>|^\s*extension\s|^\s*given\s|\(using\b|\bsummon\[|^import\s.*\*|^\s*(inline|opaque|transparent)\s|^(def|val|var|type)\s|^\s*(\+\+|\+)\s""".r

Compile / unmanagedSources := {
  val sources = (Compile / unmanagedSources).value
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, _)) => sources.filterNot(f => scala3OnlySyntax.findFirstIn(IO.read(f)).isDefined)
    case _            => sources
  }
}
