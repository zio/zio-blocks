name         := "telemetry-examples"
version      := "0.1.0"
scalaVersion := "3.9.0"

publish / skip := true
run / fork     := true

scalacOptions ++= Seq("-deprecation", "-encoding", "UTF-8", "-feature", "-unchecked")

// Scala 3.9+ artifacts are published with `@experimental` APIs, which consumers must opt into.
scalacOptions ++= {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((3, minor)) if minor >= 9 => Seq("-experimental")
    case _                              => Seq.empty
  }
}

val zioBlocksVersion = "0.0.56"

// Scala 3.9+ artifacts are published as `zio-blocks-next-*`; older Scala versions use `zio-blocks-*`.
def zioBlocks(module: String) = Def.setting {
  val prefix = CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((3, minor)) if minor >= 9 => "zio-blocks-next"
    case _                              => "zio-blocks"
  }
  "dev.zio" %% s"$prefix-$module" % zioBlocksVersion
}

libraryDependencies ++= Seq(
  zioBlocks("telemetry").value,
  zioBlocks("telemetry-otel").value,
  zioBlocks("http-model").value,
  zioBlocks("async").value,
  "dev.zio" %% "zio-test"     % "2.1.27" % Test,
  "dev.zio" %% "zio-test-sbt" % "2.1.27" % Test
)

// The telemetry module is compiled for Java 21, and its `log.*` macros run inside the compiler, so
// even compiling these examples needs JDK 21 or newer. CI also compiles every example build on
// JDK 17; there the sources are skipped with a warning instead of failing the build.
val javaMajor = System.getProperty("java.specification.version").split('.').last.toInt

def skipBelowJdk21(sources: Seq[File], log: Logger): Seq[File] =
  if (javaMajor >= 21) sources
  else {
    log.warn(s"telemetry-examples needs JDK 21 or newer (running on JDK $javaMajor); skipping compilation")
    Nil
  }

Compile / unmanagedSources := skipBelowJdk21((Compile / unmanagedSources).value, streams.value.log)
Test / unmanagedSources    := skipBelowJdk21((Test / unmanagedSources).value, streams.value.log)
