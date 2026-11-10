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

val zioBlocksVersion = "0.0.56"

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
  "dev.zio"     %% "zio-sbt-source" % "0.8.8",
  // MongoDB Java driver for the store-case-classes-in-mongodb guide; 5.13.0 matches the bson 5.13.0 that schema-bson uses
  "org.mongodb" % "mongodb-driver-sync" % "5.13.0"
)

// Examples written in Scala 3-only syntax (`@main`, `derives`, `enum`, `given`/`using`, context functions `?=>`,
// `extension`, `opaque`/`inline`, top-level definitions, `zio.sbt` source macros (their expansion calls a `private[sbt]` method 2.13 rejects), wildcard `import x.*`) are skipped on Scala 2.13, so the rest of the build compiles there.
val scala3OnlySyntax =
  """(?m)^\s*@main\b|^(?!\s*(\*|//)).*\bderives\s+[A-Z]|^\s*(final\s+)?enum\s|\?=>|^\s*extension\s|^\s*given\s|\(using\b|\bsummon\[|^import\s.*\*|^\s*(inline|opaque|transparent)\s|^(def|val|var|type)\s|^\s*(\+\+|\+)\s|^import\s+zio\.sbt\.""".r

// Whole example packages that are Scala 3-only: `namedtuples` uses Scala 3 named tuples.
val scala3OnlyPackages = Set("namedtuples")

// Single examples that hit Scala 2.13 limits in the 0.0.56 artifacts: `json"..."`/`yaml"..."` interpolators and the
// migration builder expand to calls into `private[...]` members, and a few examples rely on Scala 3 type inference.
val scala3OnlyExamples = Set(
  "avrorecords/Step5TypeMappingExample.scala",
  "comptime/RdbmsExample.scala",
  "evolveyourdata/CompleteExample.scala",
  "evolveyourdata/Concept4MigrationExample.scala",
  "evolveyourdata/Concept5ReverseMigrationExample.scala",
  "jsonwithoutcaseclasses/Step6BuildJsonExample.scala",
  "nesteddatawithoptics/CompleteExample.scala",
  "nesteddatawithoptics/Concept4EachExample.scala",
  "readandwriteyaml/Step5BuildYamlInCodeExample.scala"
)

Compile / unmanagedSources := {
  val sources = (Compile / unmanagedSources).value
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, _)) =>
      sources.filterNot(f =>
        scala3OnlyPackages(f.getParentFile.getName) || scala3OnlyExamples(
          f.getParentFile.getName + "/" + f.getName
        ) || scala3OnlySyntax.findFirstIn(IO.read(f)).isDefined
      )
    case _ => sources
  }
}
