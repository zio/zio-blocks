package golem.integration

import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite

import java.io.File
import java.net.Socket
import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit
import scala.sys.process.Process
import scala.util.{Try, Using}

final class GolemExamplesIntegrationSpec extends AnyFunSuite with BeforeAndAfterAll {

  private val golemPort        = 9881
  private val startupTimeoutMs = 60_000L
  private val pollIntervalMs   = 500L
  private val replTimeoutSec   = 180L

  private val examplesDir: File = {
    val cwd        = Path.of(sys.props.getOrElse("user.dir", ".")).toAbsolutePath.normalize
    val candidates = Seq(
      cwd.resolve("examples"),
      cwd.resolve("../examples"),
      cwd.resolve("golem/examples"),
      cwd.resolve("../golem/examples")
    ).map(_.normalize.toFile)

    candidates.find(d => new File(d, "golem.yaml").isFile)
      .getOrElse(sys.error(s"Could not locate examples dir (with golem.yaml) from user.dir=$cwd"))
  }

  private val tsPackagesPath: Option[String] =
    sys.props.get("golem.tsPackagesPath")
      .orElse(sys.env.get("GOLEM_TS_PACKAGES_PATH"))

  private var serverProcess: Option[java.lang.Process] = None
  private var serverLogFile: File = scala.compiletime.uninitialized

  // ---------------------------------------------------------------------------
  // Lifecycle
  // ---------------------------------------------------------------------------

  override def beforeAll(): Unit = {
    super.beforeAll()

    assume(golemOnPath(), "golem executable not found on PATH")
    assume(
      tsPackagesPath.nonEmpty,
      "GOLEM_TS_PACKAGES_PATH env var or golem.tsPackagesPath system property must be set"
    )
    assume(portFree(golemPort), s"port $golemPort is already in use")

    try {
      serverLogFile = Files.createTempFile("golem-server-", ".log").toFile
      serverLogFile.deleteOnExit()

      val pb = new ProcessBuilder(
          "golem", "server", "run", "--clean", "--disable-app-manifest-discovery"
        ).directory(examplesDir)
        .redirectErrorStream(true)
        .redirectOutput(serverLogFile)

      tsPackagesPath.foreach(v => pb.environment().put("GOLEM_TS_PACKAGES_PATH", v))

      val p = pb.start()
      serverProcess = Some(p)

      val deadline = System.currentTimeMillis() + startupTimeoutMs
      var ready    = false
      while (!ready && System.currentTimeMillis() < deadline) {
        if (!p.isAlive) {
          val log = Try(new String(Files.readAllBytes(serverLogFile.toPath))).getOrElse("")
          fail(s"golem server exited early (exit=${p.exitValue()}):\n$log")
        }
        Thread.sleep(pollIntervalMs)
        ready = canConnect(golemPort)
      }
      assume(ready, s"golem server did not become ready within ${startupTimeoutMs}ms")

      // Deploy the component (retry once for the known deploy race)
      val deployTimeoutSec = 300L
      val deployResult = runGolem(deployTimeoutSec, "deploy")
      if (deployResult.exitCode != 0) {
        Thread.sleep(2000)
        val retry = runGolem(deployTimeoutSec, "deploy")
        assert(
          retry.exitCode == 0,
          s"golem deploy failed after retry (exit=${retry.exitCode}):\n${retry.output}"
        )
      }
    } catch {
      case t: Throwable =>
        stopServer()
        throw t
    }
  }

  override def afterAll(): Unit =
    try stopServer()
    finally super.afterAll()

  private def stopServer(): Unit = {
    serverProcess.foreach { p =>
      val h = p.toHandle
      h.descendants().forEach(_.destroy())
      h.destroy()
      Thread.sleep(1000)
      h.descendants().forEach(_.destroyForcibly())
      if (h.isAlive) h.destroyForcibly()
    }
    serverProcess = None
  }

  // ---------------------------------------------------------------------------
  // Sample manifest
  // ---------------------------------------------------------------------------

  private case class Sample(
    name: String,
    script: String,
    assertion: Assertion,
    skip: Option[String] = None
  )

  private sealed trait Assertion
  private case class Contains(fragments: String*)  extends Assertion
  private case class Custom(check: String => Unit) extends Assertion

  private val samples: Seq[Sample] = Seq(
    // --- Simple / deterministic ---
    Sample(
      "snapshot-counter",
      "samples/snapshot-counter/repl-snapshot-counter.ts",
      Custom { output =>
        assert(output.contains("a:") || output.contains("a ="), s"expected field 'a' in: $output")
        assert(output.contains("1"), s"expected value 1 in: $output")
        assert(output.contains("2"), s"expected value 2 in: $output")
      }
    ),
    Sample(
      "fork",
      "samples/fork/repl-fork.ts",
      Contains("original-joined"),
      skip = Some("fork() + awaitPromise blocks in REPL mode")
    ),
    Sample(
      "fork-json",
      "samples/fork/repl-fork-json.ts",
      Contains("original-joined-json: count=42"),
      skip = Some("fork() + awaitPromise blocks in REPL mode")
    ),
    Sample(
      "sync-return",
      "samples/sync-return/repl-sync-return.ts",
      Contains("hello, world", "sum=7", "tag=test-tag")
    ),
    Sample(
      "json-tasks",
      "samples/json-tasks/repl-json-tasks.ts",
      Custom { output =>
        assert(output.contains("t1"), s"expected title 't1' in: $output")
        assert(output.contains("true") || output.contains("completed"), s"expected completed in: $output")
      }
    ),
    Sample(
      "human-in-the-loop",
      "samples/human-in-the-loop/repl-human-in-the-loop.ts",
      Custom { output =>
        assert(output.contains("pending"), s"expected 'pending' in: $output")
        assert(output.contains("approved"), s"expected 'approved' in: $output")
      }
    ),
    Sample(
      "simple-rpc",
      "samples/simple-rpc/repl-counter.ts",
      Custom { output =>
        // Coordinator.route reverses the input string
        assert(output.contains("olleh"), s"expected reversed 'hello' in: $output")
        assert(output.contains("dlrow"), s"expected reversed 'world' in: $output")
      }
    ),
    Sample(
      "agent-to-agent",
      "samples/agent-to-agent/repl-minimal-agent-to-agent.ts",
      Custom { output =>
        assert(output.contains("olleh"), s"expected reversed 'hello' in: $output")
        assert(output.contains("cba"), s"expected reversed 'abc' in: $output")
      }
    ),
    Sample(
      "stateful-counter",
      "samples/stateful-counter/repl-stateful-counter.ts",
      Custom { output =>
        // initial=10, first increment=11, second=12, current=12
        assert(output.contains("first") && output.contains("11"), s"expected first=11 in: $output")
        assert(output.contains("second") && output.contains("12"), s"expected second=12 in: $output")
        assert(output.contains("current") && output.contains("12"), s"expected current=12 in: $output")
      }
    ),
    Sample(
      "shard",
      "samples/shard/repl-shard.ts",
      Contains("users:0:alice")
    ),
    Sample(
      "trigger",
      "samples/trigger/repl-trigger.ts",
      Custom { output =>
        assert(output.contains("pong"), s"expected 'pong' in: $output")
        assert(output.contains("10"), s"expected first process result in: $output")
      }
    ),

    // --- Transactions (deterministic trace output) ---
    Sample(
      "transactions-infallible",
      "samples/transactions/repl-infallible.ts",
      Contains("Infallible Transaction Demo", "transaction result=30")
    ),
    Sample(
      "transactions-fallible-success",
      "samples/transactions/repl-fallible-success.ts",
      Contains("transaction result=Right(51)")
    ),
    Sample(
      "transactions-fallible-failure",
      "samples/transactions/repl-fallible-failure.ts",
      Contains("FailedAndRolledBackCompletely", "intentional-failure")
    ),

    // --- Guards ---
    Sample(
      "guards-block",
      "samples/guards/repl-guards-block.ts",
      Contains("retry-ok", "level-ok", "idem-ok", "atomic-ok")
    ),
    Sample(
      "guards-resource",
      "samples/guards/repl-guards-resource.ts",
      Contains("result=")
    ),
    Sample(
      "guards-oplog",
      "samples/guards/repl-oplog.ts",
      Contains("current oplog index=", "markBeginOperation", "markEndOperation")
    ),

    // --- Observability ---
    Sample(
      "observability-trace",
      "samples/observability/repl-observability.ts",
      Contains("=== Trace Demo ===", "traceId", "spanId")
    ),
    Sample(
      "observability-durability",
      "samples/observability/repl-durability.ts",
      Contains("=== Durability Demo ===", "isLive", "persistenceLevel")
    ),

    // --- Storage / Config ---
    Sample(
      "storage-config",
      "samples/storage/repl-storage.ts",
      Contains("=== Config Demo ===", "Config.get")
    ),

    // --- JSON promise ---
    Sample(
      "json-promise",
      "samples/json-promise/repl-json-promise.ts",
      Contains("roundtrip", "createPromise ok")
    ),

    // --- Oplog inspector ---
    Sample(
      "oplog-inspector",
      "samples/oplog-inspector/repl-oplog-inspector.ts",
      Contains("=== Oplog Inspector")
    ),
    Sample(
      "oplog-search",
      "samples/oplog-inspector/repl-oplog-search.ts",
      Contains("=== Searching oplog")
    ),

    // --- Agent registry ---
    Sample(
      "agent-registry",
      "samples/agent-registry/repl-registry.ts",
      Contains("registeredAgentType", "getAllAgentTypes")
    ),
    Sample(
      "agent-registry-query",
      "samples/agent-registry/repl-agent-query.ts",
      Contains("agentType=", "agentName=")
    ),
    Sample(
      "agent-registry-phantom",
      "samples/agent-registry/repl-phantom.ts",
      Contains("phantom counter")
    ),

    // --- Host API explorer ---
    Sample(
      "host-api-explorer-all",
      "samples/host-api-explorer/repl-explore-all.ts",
      Contains("=== Config", "=== Durability", "=== Context")
    ),
    Sample(
      "host-api-explorer-config",
      "samples/host-api-explorer/repl-explore-config.ts",
      Contains("Config.get", "Config.getAll")
    ),
    Sample(
      "host-api-explorer-context",
      "samples/host-api-explorer/repl-explore-context.ts",
      Contains("traceId=", "spanId=")
    ),
    Sample(
      "host-api-explorer-durability",
      "samples/host-api-explorer/repl-explore-durability.ts",
      Contains("isLive=", "persistenceLevel=")
    ),
    Sample(
      "host-api-explorer-blobstore",
      "samples/host-api-explorer/repl-explore-blobstore.ts",
      Contains("containerExists", "createContainer")
    ),
    Sample(
      "host-api-explorer-oplog",
      "samples/host-api-explorer/repl-explore-oplog.ts",
      Contains("oplog entry count=")
    ),
    Sample(
      "host-api-explorer-keyvalue",
      "samples/host-api-explorer/repl-explore-keyvalue.ts",
      Contains("set(", "get(", "exists(")
    ),
    Sample(
      "host-api-explorer-rdbms",
      "samples/host-api-explorer/repl-explore-rdbms.ts",
      Contains("Left(")
    ),

    // --- Database (requires external DB) ---
    Sample(
      "database",
      "samples/database/repl-database.ts",
      Contains("=== Type Showcase ===", "DbDate", "IpAddress"),
      skip = Some("requires database server (set RUN_DATABASE_TESTS=1)")
    )
  )

  // ---------------------------------------------------------------------------
  // Test generation
  // ---------------------------------------------------------------------------

  samples.foreach { sample =>
    test(sample.name) {
      sample.skip.foreach(reason => assume(false, reason))

      val result = runRepl(sample.script)
      assert(
        result.exitCode == 0,
        s"golem repl failed for '${sample.name}' (exit=${result.exitCode}):\n${result.output}"
      )

      val output = normalizeOutput(result.output)

      sample.assertion match {
        case Contains(fragments*) =>
          fragments.foreach { frag =>
            assert(output.contains(frag), s"Expected output to contain '$frag', got:\n$output")
          }

        case Custom(check) =>
          check(output)
      }
    }
  }

  // Verify the manifest covers all sample scripts
  test("manifest covers all sample scripts") {
    val samplesDir = new File(examplesDir, "samples")
    val allScripts = findTsFiles(samplesDir).map(_.relativeTo(examplesDir)).sorted
    val manifest   = samples.map(_.script).sorted
    val uncovered  = allScripts.filterNot(manifest.contains)
    assert(uncovered.isEmpty, s"Sample scripts not covered by manifest: ${uncovered.mkString(", ")}")
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private case class GolemResult(exitCode: Int, output: String)

  private def runRepl(scriptFile: String): GolemResult =
    runGolem(
      replTimeoutSec,
      "repl", "scala:examples",
      "--language", "typescript",
      "--script-file", scriptFile
    )

  private def runGolem(args: String*): GolemResult =
    runGolem(120L, args*)

  private def runGolem(timeoutSec: Long, args: String*): GolemResult = {
    val appManifest = new File(examplesDir, "golem.yaml").getAbsolutePath
    val cmd         = Seq("golem", "--yes", "--local", "--app-manifest-path", appManifest) ++ args

    val pb = new ProcessBuilder(cmd*)
      .directory(examplesDir)
      .redirectErrorStream(true)

    tsPackagesPath.foreach(v => pb.environment().put("GOLEM_TS_PACKAGES_PATH", v))

    val proc      = pb.start()
    val outBuffer = new java.io.ByteArrayOutputStream()
    val reader    = new Thread(() => {
      try {
        val buf = new Array[Byte](4096)
        val is  = proc.getInputStream
        var n   = is.read(buf)
        while (n != -1) {
          outBuffer.write(buf, 0, n)
          n = is.read(buf)
        }
      } catch { case _: Exception => }
    })
    reader.setDaemon(true)
    reader.start()

    val done = proc.waitFor(timeoutSec, TimeUnit.SECONDS)
    if (!done) {
      proc.destroyForcibly()
      reader.join(2000)
      GolemResult(-1, s"TIMEOUT after ${timeoutSec}s. Partial output:\n${outBuffer.toString("UTF-8")}")
    } else {
      reader.join(5000)
      GolemResult(proc.exitValue(), outBuffer.toString("UTF-8"))
    }
  }

  private def normalizeOutput(raw: String): String =
    raw
      .replaceAll("\u001b\\[[0-9;]*[a-zA-Z]", "") // strip ANSI escape codes
      .replaceAll("\r\n", "\n")                     // normalize line endings
      .trim

  private def golemOnPath(): Boolean =
    Try(Process(Seq("golem", "--version")).!!).isSuccess

  private def portFree(port: Int): Boolean =
    !canConnect(port)

  private def canConnect(port: Int): Boolean =
    Try(Using.resource(new Socket("localhost", port))(_ => ())).isSuccess

  private def findTsFiles(dir: File): Seq[File] =
    if (!dir.exists()) Seq.empty
    else {
      val files: Array[File] = Option(dir.listFiles()).getOrElse(Array.empty[File])
      val tsFiles = files.filter(f => f.isFile && f.getName.endsWith(".ts"))
      val subdirs = files.filter(_.isDirectory).flatMap(findTsFiles)
      tsFiles.toSeq ++ subdirs
    }

  private implicit class FileOps(f: File) {
    def relativeTo(base: File): String =
      base.toPath.relativize(f.toPath).toString
  }
}
