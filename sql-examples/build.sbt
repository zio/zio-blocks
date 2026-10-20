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

// Scala 3.7+ artifacts are published as `zio-blocks-next-*`; older Scala versions use `zio-blocks-*`.
def zioBlocks(module: String) = Def.setting {
  val prefix = CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((3, minor)) if minor >= 7 => "zio-blocks-next"
    case _                              => "zio-blocks"
  }
  "dev.zio" %% s"$prefix-$module" % zioBlocksVersion
}

libraryDependencies ++= Seq(
  zioBlocks("sql").value,
  zioBlocks("streams").value,
  "org.xerial"      % "sqlite-jdbc"  % "3.53.4.0",
  "org.postgresql"  % "postgresql"   % "42.7.14",
  "com.zaxxer"      % "HikariCP"     % "7.1.0",
  "dev.zio"        %% "zio-test"     % "2.1.27" % Test,
  "dev.zio"        %% "zio-test-sbt" % "2.1.27" % Test
)
