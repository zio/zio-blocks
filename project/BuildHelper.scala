import sbt.Keys.*
import sbt.{Def, *}
import sbtbuildinfo.*
import sbtbuildinfo.BuildInfoKeys.*
import sbtcrossproject.CrossPlugin.autoImport.*
import scoverage.ScoverageKeys.coverageEnabled

object BuildHelper {
  val Scala213: String = "2.13.16"
  val Scala3: String   = "3.7.1"

  val JdkReleaseVersion: String = "11"

  lazy val isRelease: Boolean = {
    val value = sys.env.contains("CI_RELEASE_MODE")
    if (value) println("Detected CI_RELEASE_MODE envvar, enabling optimizations")
    value
  }

  def buildInfoSettings(packageName: String): Seq[Def.Setting[?]] =
    Seq(
      // BuildInfoOption.ConstantValue required to disable assertions in FiberRuntime!
      buildInfoOptions += BuildInfoOption.ConstantValue,
      buildInfoKeys := Seq[BuildInfoKey](
        organization,
        moduleName,
        name,
        version,
        scalaVersion,
        sbtVersion,
        isSnapshot,
        BuildInfoKey("optimizationsEnabled" -> isRelease)
      ),
      buildInfoPackage := packageName
    )

  def platformSpecificSources(platform: String, conf: String, baseDirectory: File)(versions: String*): Seq[File] = for {
    platform <- Seq("shared", platform)
    version  <- "scala" :: versions.toList.map("scala-" + _)
    result    = baseDirectory.getParentFile / platform.toLowerCase / "src" / conf / version
    if result.exists
  } yield result

  def crossPlatformSources(scalaVer: String, platform: String, conf: String, baseDir: File): Seq[File] = {
    val versions = CrossVersion.partialVersion(scalaVer) match {
      case Some((2, 12)) => Seq("2.12-2.13")
      case Some((2, 13)) => Seq("2.13+", "2.12-2.13")
      case Some((3, _))  => Seq("2.13+")
      case _             => Seq()
    }
    platformSpecificSources(platform, conf, baseDir)(versions *)
  }

  def crossProjectSettings: Seq[Def.Setting[?]] = Seq(
    Compile / unmanagedSourceDirectories ++= {
      crossPlatformSources(
        scalaVersion.value,
        crossProjectPlatform.value.identifier,
        "main",
        baseDirectory.value
      )
    },
    Test / unmanagedSourceDirectories ++= {
      crossPlatformSources(
        scalaVersion.value,
        crossProjectPlatform.value.identifier,
        "test",
        baseDirectory.value
      )
    }
  )

  def stdSettings(prjName: String): Seq[Def.Setting[?]] = Seq(
    name                     := prjName,
    crossScalaVersions       := Seq(Scala213, Scala3),
    ThisBuild / scalaVersion := Scala213,
    ThisBuild / publishTo := {
      val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
      if (isSnapshot.value) Some("central-snapshots" at centralSnapshots)
      else localStaging.value
    },
    scalacOptions ++= Seq(
      "-deprecation",
      "-encoding",
      "UTF-8",
      "-feature",
      "-unchecked",
      "-release",
      JdkReleaseVersion
    ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq(
          "-opt:l:method"
        )
      case _ =>
        Seq(
          "-explain",
          "-explain-cyclic",
          "-Wconf:msg=Ignoring .*this.* qualifier:s",
          "-Wconf:msg=Implicit parameters should be provided with a `using` clause:s",
          "-Wconf:msg=The syntax `.*` is no longer supported for vararg splices; use `.*` instead:s"
        )
    }),
    versionScheme := Some("early-semver"),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    Test / parallelExecution := true,
    Compile / fork           := true,
    Test / fork              := true, // set fork to `true` to improve log readability
    // For compatibility with Java 9+ module system;
    // without Automatic-Module-Name, the module name is derived from the jar file which is invalid because of the scalaVersion suffix.
    Compile / packageBin / packageOptions +=
      Package.ManifestAttributes(
        "Automatic-Module-Name" -> s"${organization.value}.$prjName".replaceAll("-", ".")
      )
  )

  def nativeSettings: Seq[Def.Setting[?]] = Seq(
    coverageEnabled := false,
    Test / fork     := false
  )

  def jsSettings: Seq[Def.Setting[?]] = Seq(
    coverageEnabled := false,
    Test / fork     := false
  )
}
