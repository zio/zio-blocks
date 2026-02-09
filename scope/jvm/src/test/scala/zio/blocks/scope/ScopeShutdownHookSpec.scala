package zio.blocks.scope

import zio.test._
import scala.sys.process._
import scala.collection.mutable.ArrayBuffer
import java.io.File
import java.net.{URL, URLClassLoader}

object ScopeShutdownHookSpec extends ZIOSpecDefault {
  private def javaPath: String = {
    val javaHome  = System.getProperty("java.home")
    val separator = File.separator
    s"$javaHome${separator}bin${separator}java"
  }

  private def buildClasspath: String = {
    val urls = ArrayBuffer.empty[URL]

    def collectUrls(cl: ClassLoader): Unit = cl match {
      case null                => ()
      case ucl: URLClassLoader =>
        urls ++= ucl.getURLs
        collectUrls(cl.getParent)
      case other =>
        collectUrls(other.getParent)
    }

    collectUrls(this.getClass.getClassLoader)
    urls.map(u => new File(u.toURI).getAbsolutePath).mkString(File.pathSeparator)
  }

  def spec = suite("Scope shutdown hook")(
    test("shutdown hook runs on JVM exit") {
      val classpath = buildClasspath
      val stdout    = ArrayBuffer.empty[String]
      val stderr    = ArrayBuffer.empty[String]
      val logger    = ProcessLogger(line => stdout += line, line => stderr += line)

      val exitCode = Process(Seq(javaPath, "-cp", classpath, "zio.blocks.scope.ShutdownHookTestMain")).!(logger)

      val output = stdout.mkString("\n")
      val errors = stderr.mkString("\n")

      if (exitCode != 0) {
        println(s"STDERR: $errors")
        println(s"STDOUT: $output")
      }

      assertTrue(exitCode == 0) &&
      assertTrue(output.contains("MAIN_FINISHED")) &&
      assertTrue(output.contains("SHUTDOWN_HOOK_RAN"))
    }
  )
}
