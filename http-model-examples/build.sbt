name         := "http-model-examples"
version      := "0.1.0"
scalaVersion := "3.9.0"

publish / skip := true
run / fork     := true

scalacOptions ++= Seq("-deprecation", "-encoding", "UTF-8", "-feature", "-unchecked")

// The zio-blocks-next-* artifacts (Scala 3.9+) are compiled with `-experimental`, which consumers must opt into.
scalacOptions ++= {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((3, minor)) if minor >= 9 => Seq("-experimental")
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
  zioBlocks("http-model").value,
  zioBlocks("http-model-schema").value,
  zioBlocks("html").value,
  zioBlocks("schema").value
)

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
