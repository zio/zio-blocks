import sbt.Keys.*
import sbt.{Def, *}
import sbtbuildinfo.*
import sbtbuildinfo.BuildInfoKeys.*
import sbtcrossproject.CrossPlugin.autoImport.*
import scoverage.ScoverageKeys._

object BuildHelper {
  val Scala213: String = "2.13.18"
  val Scala33: String  = "3.3.7" // LTS
  val Scala3: String   = "3.7.4"

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

  /**
   * Find all applicable Scala 3.x minor version directories.
   *
   * Supports three directory naming conventions:
   *   - `scala-3.X` (without suffix): for version X up to but not including the
   *     next version-specific directory. Only the best match (highest X <=
   *     target) is included.
   *   - `scala-3.X+` (with +): for version X and ALL later versions. All
   *     matching directories are included.
   *   - `scala-3.X-` (with -): for versions BEFORE X (not including X). All
   *     matching directories are included.
   *
   * Examples with scala-3, scala-3.5+, scala-3.7:
   *   - Scala 3.7 uses: scala-3, scala-3.5+, scala-3.7
   *   - Scala 3.6 uses: scala-3, scala-3.5+
   *   - Scala 3.4 uses: scala-3
   *
   * Examples with scala-3, scala-3.5, scala-3.7:
   *   - Scala 3.7 uses: scala-3, scala-3.7 (scala-3.5 superseded)
   *   - Scala 3.6 uses: scala-3, scala-3.5
   *   - Scala 3.4 uses: scala-3
   *
   * Examples with scala-3.5-, scala-3.5+:
   *   - Scala 3.7 uses: scala-3.5+ (mutually exclusive)
   *   - Scala 3.5 uses: scala-3.5+ (mutually exclusive)
   *   - Scala 3.4 uses: scala-3.5- (mutually exclusive)
   *   - Scala 3.3 uses: scala-3.5- (mutually exclusive)
   *
   * @param targetMinor
   *   the minor version of the Scala 3 compiler being used
   * @param platforms
   *   the platforms to search (e.g., Seq("shared", "jvm"))
   * @param conf
   *   "main" or "test"
   * @param baseDir
   *   the base directory of the project
   * @return
   *   all applicable version strings (e.g., Seq("3.5+", "3.7")), sorted
   *   ascending by minor version
   */
  def findApplicableScala3MinorDirs(
    targetMinor: Int,
    platforms: Seq[String],
    conf: String,
    baseDir: File
  ): Seq[String] = {
    val Scala3PlusPattern  = """scala-3\.(\d+)\+""".r
    val Scala3MinusPattern = """scala-3\.(\d+)-""".r
    val Scala3ExactPattern = """scala-3\.(\d+)""".r

    val allDirs = for {
      platform <- platforms
      dir       = baseDir.getParentFile / platform.toLowerCase / "src" / conf
      if dir.exists
      child <- dir.listFiles().toSeq
      if child.isDirectory
    } yield child.getName

    // scala-3.X+ directories: include if X <= target (i.e., target >= X)
    val plusMinors = allDirs.collect { case Scala3PlusPattern(m) =>
      scala.util.Try(m.toInt).toOption
    }.flatten.distinct.filter(_ <= targetMinor)

    // scala-3.X- directories: include if target < X (i.e., target is before X)
    val minusMinors = allDirs.collect { case Scala3MinusPattern(m) =>
      scala.util.Try(m.toInt).toOption
    }.flatten.distinct.filter(targetMinor < _)

    // scala-3.X directories: include only the best match (highest X <= target)
    val exactMinors = allDirs.collect { case Scala3ExactPattern(m) =>
      scala.util.Try(m.toInt).toOption
    }.flatten.distinct.filter(_ <= targetMinor)

    val bestExact = exactMinors.sorted.lastOption

    val result = plusMinors.map(m => s"3.$m+") ++ minusMinors.map(m => s"3.$m-") ++ bestExact.map(m => s"3.$m").toSeq
    result.sortBy(s => s.stripSuffix("+").stripSuffix("-").stripPrefix("3.").toInt)
  }

  def crossPlatformSources(scalaVer: String, platform: String, conf: String, baseDir: File): Seq[File] = {
    val platforms = Seq("shared", platform)
    val versions  = CrossVersion.partialVersion(scalaVer) match {
      case Some((2, 12))    => Seq("2.12-2.13")
      case Some((2, 13))    => Seq("2.13+", "2.12-2.13")
      case Some((3, minor)) =>
        val base          = Seq("2.13+", "3")
        val minorSpecific = findApplicableScala3MinorDirs(minor.toInt, platforms, conf, baseDir)
        base ++ minorSpecific
      case _ => Seq()
    }
    platformSpecificSources(platform, conf, baseDir)(versions*)
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

  def stdSettings(prjName: String, scalaVersions: Seq[String] = Seq(Scala3, Scala33, Scala213)): Seq[Def.Setting[?]] =
    Seq(
      name := prjName,
      // For Scala 3.7+, publish this module/artifact as "zio-blocks-next-*" (project name remains prjName)
      moduleName := {
        CrossVersion.partialVersion(scalaVersion.value) match {
          case Some((3, minor)) if minor >= 7 => prjName.replace("zio-blocks-", "zio-blocks-next-")
          case _                              => prjName
        }
      },
      crossScalaVersions       := scalaVersions,
      scalaVersion             := scalaVersions.head,
      ThisBuild / scalaVersion := scalaVersions.head,
      ThisBuild / publishTo    := {
        val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
        if (isSnapshot.value) Some("central-snapshots" at centralSnapshots)
        else localStaging.value
      },
      scalacOptions ++= Seq(
        "-deprecation",
        "-encoding",
        "UTF-8",
        "-feature",
        "-unchecked"
      ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((3, minor)) =>
          Seq(
            "-release",
            if (minor < 8) "11" else "17",
            "-rewrite",
            "-no-indent",
            "-explain",
            "-explain-cyclic",
            "-Wunused:all",
            "-Wconf:msg=unused.*&src=.*/test/.*:s",                          // suppress unused warnings in test sources
            "-Wconf:msg=nowarn annotation does not suppress any warnings:s", // nowarn difference between Scala 3.3 and 3.5
            "-Wconf:msg=with as a type operator has been deprecated:s",      // `with` works in both Scala 2 and 3, & only in Scala 3
            "-Wconf:msg=`_` is deprecated for wildcard arguments:s",         // cross-build with Scala 2 requires [_] syntax
            "-Wconf:msg=(is deprecated)&src=zio/blocks/schema/.*:silent",    // workaround for `@deprecated("reasons") case class C() derives Schema`
            "-Wconf:msg=Ignoring .*this.* qualifier:s",
            "-Wconf:msg=Implicit parameters should be provided with a `using` clause:s",
            "-Wconf:msg=The syntax `.*` is no longer supported for vararg splices; use `.*` instead:s",
            "-Wconf:id=E029:s",                                                      // suppress non-exhaustive pattern match warnings in macro code
            "-Wconf:id=E030:s",                                                      // suppress unreachable case warnings in type pattern matching
            "-Wconf:msg=package scala contains object and package with same name:s", // Scala.js classpath artifact
            "-Werror"
          ) ++ (if (minor >= 5) Seq("-experimental") else Seq.empty)
        case _ =>
          Seq(
            "-release",
            "11",
            "-language:existentials",
            "-opt:l:method",
            "-Ywarn-unused",
            "-Xfatal-warnings"
          )
      }),
      versionScheme := Some("early-semver"),
      testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
      Test / parallelExecution := true,
      Compile / fork           := false,
      Test / fork              := false, // set fork to `true` to improve log readability
      // For compatibility with Java 9+ module system;
      // without Automatic-Module-Name, the module name is derived from the jar file which is invalid because of the scalaVersion suffix.
      Compile / packageBin / packageOptions +=
        Package.ManifestAttributes(
          "Automatic-Module-Name" -> s"${organization.value}.${moduleName.value}".replaceAll("-", ".")
        ),
      coverageFailOnMinimum      := true,
      coverageMinimumStmtTotal   := 95,
      coverageMinimumBranchTotal := 90,
      coverageExcludedFiles      := ".*BuildInfo.*"
    )

  def jsSettings: Seq[Def.Setting[?]] = Seq(
    coverageEnabled          := false,
    Test / parallelExecution := false,
    Test / fork              := false
  )
}
