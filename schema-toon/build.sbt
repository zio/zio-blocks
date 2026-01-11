import BuildHelper._

enablePlugins(BuildInfoPlugin)
enablePlugins(JmhPlugin)

name := "zio-blocks-schema-toon"

libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-schema" % version.value
)

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)
buildInfoPackage := "zio.blocks.schema.toon"

