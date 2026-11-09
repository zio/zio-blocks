name         := "sql-examples"
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

val zioBlocksVersion = "0.0.56"

// Scala 3.9+ uses the `zio-blocks-next-*` artifacts (built with Scala 3.9.0, unreadable by older Scala 3 compilers); older Scala versions use `zio-blocks-*`.
def zioBlocks(module: String) = Def.setting {
  val prefix = CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((3, minor)) if minor >= 9 => "zio-blocks-next"
    case _                              => "zio-blocks"
  }
  "dev.zio" %% s"$prefix-$module" % zioBlocksVersion
}

// The zio-blocks streams jar is class file 65 (Java 21). Its main sources compile on JDK 17, but the
// tests load those classes at run time, so on JDK 17 the test sources are skipped with a warning.
val javaMajor = System.getProperty("java.specification.version").split('.').last.toInt

def skipBelowJdk21(sources: Seq[File], log: Logger): Seq[File] =
  if (javaMajor >= 21) sources
  else {
    log.warn(s"sql-examples tests need JDK 21 or newer (running on JDK $javaMajor); skipping test compilation")
    Nil
  }

Test / unmanagedSources := skipBelowJdk21((Test / unmanagedSources).value, streams.value.log)

libraryDependencies ++= Seq(
  zioBlocks("sql").value,
  zioBlocks("streams").value,
  zioBlocks("sql-zio").value,
  zioBlocks("data-migration").value,
  "org.xerial"      % "sqlite-jdbc"  % "3.53.4.0",
  "org.postgresql"  % "postgresql"   % "42.7.14",
  "com.zaxxer"      % "HikariCP"     % "7.1.0",
  "dev.zio"        %% "zio-test"     % "2.1.27" % Test,
  "dev.zio"        %% "zio-test-sbt" % "2.1.27" % Test
)
