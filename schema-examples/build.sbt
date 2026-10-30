name         := "schema-examples"
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
  zioBlocks("schema").value,
  zioBlocks("markdown").value,
  zioBlocks("streams").value,
  zioBlocks("chunk").value,
  zioBlocks("context").value,
  zioBlocks("schema-yaml").value,
  zioBlocks("schema-csv").value,
  zioBlocks("schema-xml").value,
  zioBlocks("schema-toon").value,
  zioBlocks("schema-messagepack").value,
  zioBlocks("schema-avro").value,
  zioBlocks("schema-thrift").value,
  zioBlocks("schema-bson").value,
  "com.lihaoyi" %% "sourcecode"     % "0.4.4",
  "dev.zio"     %% "zio-sbt-source" % "0.8.6",
  // MongoDB Java driver for the store-case-classes-in-mongodb guide; 5.13.0 matches the bson 5.13.0 that schema-bson uses
  "org.mongodb" % "mongodb-driver-sync" % "5.13.0"
)
