import sbt._

import zio.Chunk
import zio.json.ast.Json
import zio.sbt.ZioSbtCiPlugin._
import zio.sbt.githubactions.Step.SingleStep
import zio.sbt.githubactions._

/**
 * The CI workflow, as data.
 *
 * `sbt ciGenerateGithubWorkflow` renders this to .github/workflows/ci.yml,
 * which is committed and must not be edited by hand. The lint job runs
 * `sbt ciCheckGithubWorkflow`, which regenerates the workflows and fails if the
 * result differs from what is committed, so the two cannot drift.
 *
 * The jobs below are a direct port of the workflow that was previously
 * maintained by hand. The toolchain is deliberately unchanged -
 * coursier/setup-action for the test and release jobs, with a pinned jvm-index
 * on the test jobs so `temurin:<version>` resolves JDKs newer than the action's
 * bundled index, and actions/setup-java for the docs jobs - so that a CI
 * regression after this migration can only be a porting mistake, not a change
 * of runner image or cache key.
 *
 * Workflows NOT generated from here, because their triggers cannot be expressed
 * by the plugin: deploy-preview.yml (workflow_run), scala-steward.yml
 * (schedule) and release-drafter.yml. deploy-preview.yml consumes the
 * `website-artifact` and `pr-metadata` artifacts uploaded by buildDocs, and
 * matches this workflow by its name, "CI"; both are load-bearing.
 */
object CiWorkflow {

  private val Scalas = List("2.13.x", "3.3.x", "3.8.x")

  // Locale data must be installed before sbt starts, or tests that format dates disagree with the
  // expectations recorded on developer machines.
  private val InstallLocaleData = SingleStep(
    name = "Install locale data",
    run = Some(
      """|sudo apt-get update
         |sudo apt-get install -y locales
         |sudo locale-gen en_US.UTF-8
         |""".stripMargin
    )
  )

  private val CheckoutCurrentBranch = SingleStep(
    name = "Checkout current branch",
    uses = Some(ActionRef("actions/checkout@v7")),
    parameters = Map("fetch-depth" -> Json.Num(0))
  )

  /**
   * The pinned jvm-index is what allows `temurin:<version>` to resolve JDK
   * builds newer than the index bundled with the action.
   */
  private def SetupCoursier(java: String, pinJvmIndex: Boolean = true) = SingleStep(
    name = "Setup Action",
    uses = Some(ActionRef("coursier/setup-action@v3")),
    parameters = Map("jvm" -> Json.Str(s"temurin:$java")) ++
      (if (pinJvmIndex)
         Map(
           "jvm-index" -> Json.Str(
             "https://raw.githubusercontent.com/coursier/jvm-index/refs/heads/master/index.json"
           )
         )
       else Map.empty[String, Json]) ++
      Map("apps" -> Json.Str("sbt"))
  )

  private val CacheScalaDependencies = SingleStep(
    name = "Cache scala dependencies",
    uses = Some(ActionRef("coursier/cache-action@v8"))
  )

  private def expr(e: String): Condition = Condition.Expression(e)

  private val IsPullRequest = expr("github.event_name == 'pull_request'")
  private val HasWebsite    = expr("hashFiles('website/yarn.lock') != ''")

  // ---------------------------------------------------------------------------------------------
  // Build
  // ---------------------------------------------------------------------------------------------

  /**
   * Builds the docusaurus site and uploads it for deploy-preview.yml to publish
   * to Netlify.
   *
   * The install, mdoc and build steps are guarded on the yarn lockfile
   * existing. The closing `find` is not, matching the workflow this replaces -
   * it would fail in a checkout with no website. Left as-is to keep the port
   * faithful; worth guarding separately.
   *
   * `SetupLibuv`, which zio-sbt-ci puts in every job template, is deliberately
   * omitted: libuv1-dev is only needed to link Scala Native binaries and this
   * build cross-compiles to JVM and JS only. It made the job depend on an apt
   * mirror, which stalled for eight minutes and consumed the whole timeout in
   * run 32234167683.
   */
  lazy val buildDocs: Def.Initialize[Job] = Def.setting(
    Job(
      id = "buildDocs",
      name = "Build Docs",
      jobTimeout = Some(20),
      steps = Seq(
        Checkout.value,
        SetupJava("25"),
        SetupSBT,
        CacheDependencies,
        SingleStep(
          name = "Setup Node.js",
          uses = Some(ActionRef("actions/setup-node@v7")),
          parameters = Map("node-version" -> Json.Str("24.12.0"))
        ),
        SingleStep(name = "Install yarn", run = Some("npm install -g yarn")),
        SingleStep(
          name = "Install website dependencies",
          condition = Some(HasWebsite),
          run = Some("yarn install --cwd website")
        ),
        SingleStep(
          name = "Generate documentation with mdoc",
          condition = Some(HasWebsite),
          run = Some("sbt docs/mdoc")
        ),
        SingleStep(
          name = "Check website build process",
          condition = Some(HasWebsite),
          run = Some("yarn --cwd website build")
        ),
        SingleStep(
          name = "Upload website build artifact",
          uses = Some(ActionRef("actions/upload-artifact@v7")),
          condition = Some(HasWebsite && IsPullRequest),
          parameters = Map(
            "name"           -> Json.Str("website-artifact"),
            "path"           -> Json.Str("./website/build"),
            "overwrite"      -> Json.Bool(true),
            "retention-days" -> Json.Num(30)
          )
        ),
        SingleStep(
          name = "Upload PR Metadata",
          condition = Some(IsPullRequest),
          run = Some("""echo "${{ github.event.pull_request.number }}" > pr-number.txt""")
        ),
        SingleStep(
          name = "Upload PR Metadata",
          uses = Some(ActionRef("actions/upload-artifact@v7")),
          condition = Some(IsPullRequest),
          parameters = Map(
            "name"      -> Json.Str("pr-metadata"),
            "path"      -> Json.Str("pr-number.txt"),
            "overwrite" -> Json.Bool(true)
          )
        ),
        SingleStep(name = "Print All Generated Files", run = Some("find ./website/build -print"))
      )
    )
  )

  // ---------------------------------------------------------------------------------------------
  // Lint
  // ---------------------------------------------------------------------------------------------

  /**
   * Ten minutes, as the previous workflow set, is not enough on a cold coursier
   * cache: observed 10m03s twice against 3m13s warm, the cold runs timing out
   * inside the cache restore before `Lint code` even started.
   *
   * Cold restores are not rare here. coursier/cache-action hashes the Scala
   * sources under `project` along with the sbt files, and that set cannot be
   * narrowed, so editing this very file invalidates the cache for every job.
   */
  lazy val lint: Def.Initialize[Job] = Def.setting(
    Job(
      id = "lint",
      name = "lint",
      jobTimeout = Some(20),
      steps = Seq(
        InstallLocaleData,
        CheckoutCurrentBranch,
        SetupCoursier("17", pinJvmIndex = false),
        CacheScalaDependencies,
        // Regenerates the workflows and fails if the result differs from what is committed, so the
        // generated YAML cannot be hand-edited or left behind when this file changes.
        SingleStep(
          name = "Check if the workflows are up to date",
          run = Some("sbt ciCheckGithubWorkflow")
        ),
        SingleStep(
          name = "Lint code",
          run = Some("""sbt "++2.13; check; headerCheckAll; ++3.8; check; headerCheckAll"""")
        )
      )
    )
  )

  // ---------------------------------------------------------------------------------------------
  // Tests
  // ---------------------------------------------------------------------------------------------

  /**
   * JVM tests, one lane per Scala version and JDK.
   *
   * Coverage runs only on the newest JDK, and scaladoc is skipped there because
   * scala3#24183 makes it fail; docs are compiled on JDK 17 instead. Postgres
   * backs the sql modules and carries a health check so steps cannot start
   * before it accepts connections.
   *
   * The timeout is sized for the JDK 25 / 3.8.x cell, the only one that runs
   * instrumented tests, the Scala Next suite and the example compile in
   * sequence. The coverage step alone has measured anywhere from 10 to 20
   * minutes on unchanged sources, because only dependencies are cached and
   * every run recompiles from scratch on whatever runner it draws, so the cap
   * has to clear the slow end of that spread rather than its median.
   */
  lazy val testJVM: Def.Initialize[Job] = Def.setting(
    Job(
      id = "testJVM",
      name = "testJVM",
      jobTimeout = Some(40),
      strategy = Some(
        Strategy(
          matrix = Map("java" -> List("17", "25"), "platform" -> List("JVM"), "scala" -> Scalas),
          failFast = false
        )
      ),
      services = Seq(
        Service(
          name = "postgres",
          image = ImageRef("postgres:16"),
          env = Map(
            "POSTGRES_USER"     -> "postgres",
            "POSTGRES_PASSWORD" -> "postgres",
            "POSTGRES_DB"       -> "postgres"
          ),
          ports = Chunk(ServicePort(32886, 5432)),
          options = Some(
            "--health-cmd pg_isready --health-interval 10s --health-timeout 5s --health-retries 5"
          )
        )
      ),
      steps = Seq(
        CheckoutCurrentBranch,
        SingleStep(
          name = "Set JVM options for JDK 25+",
          condition = Some(expr("matrix.java >= 25")),
          run = Some("""echo "JAVA_TOOL_OPTIONS=-XX:+UseCompactObjectHeaders" >> $GITHUB_ENV""")
        ),
        SetupCoursier("${{ matrix.java }}"),
        CacheScalaDependencies,
        SingleStep(
          name = "Run Scala 2 tests and compile Scala 2 docs",
          condition = Some(expr("startsWith(matrix.scala, '2.')")),
          run = Some("sbt ++${{ matrix.scala }} test${{ matrix.platform }} doc${{ matrix.platform }}")
        ),
        SingleStep(
          name = "Run Scala 3 tests and docs (JDK 17)",
          condition = Some(expr("startsWith(matrix.scala, '3.')") && expr("matrix.java == 17")),
          run = Some("sbt ++${{ matrix.scala }} test${{ matrix.platform }} doc${{ matrix.platform }}")
        ),
        SingleStep(
          name = "Run Scala 3 tests with test coverage (JDK 25, skip docs due to scaladoc bug)",
          condition = Some(expr("startsWith(matrix.scala, '3.')") && expr("matrix.java >= 25")),
          run = Some("sbt ++${{ matrix.scala }} coverage test${{ matrix.platform }} coverageReport")
        ),
        SingleStep(
          name = "Run Scala Next tests",
          condition = Some(expr("matrix.scala == '3.8.x'")),
          run = Some("sbt ++3.8.3 scalaNextTests${{ matrix.platform }}/test benchmarks/test")
        ),
        SingleStep(
          name = "Compile example project",
          condition = Some(expr("matrix.scala == '3.8.x'")),
          run = Some("sbt ++${{ matrix.scala }} schema-examples/compile")
        ),
        // sbt has repeatedly died on the JDK 25 jobs with a bare `exit code 1`, no stderr and no
        // hs_err file, immediately after reporting that every test passed, skipping the remaining
        // commands in the step. The build output cannot distinguish a kernel kill from a
        // self-inflicted exit, so collect what can tell them apart the next time it happens: an
        // OOM kill or a signal is recorded in the kernel log, and a JVM crash leaves an hs_err
        // file behind. Runs on any job failure, ordinary test failures included; it costs seconds.
        SingleStep(
          name = "Collect failure diagnostics",
          condition = Some(Condition.Function("failure()")),
          run = Some(
            """|kernel_log=$(sudo dmesg -T 2>/dev/null) || true
               |echo "::group::Kernel log, OOM and kill lines only"
               |if [ -n "$kernel_log" ]; then
               |  echo "$kernel_log" | grep -iE 'out of memory|oom[-_ ]kill|killed process|segfault' || echo "no OOM or kill lines"
               |else
               |  echo "kernel log unavailable"
               |fi
               |echo "::endgroup::"
               |echo "::group::Kernel log, last 200 lines"
               |if [ -n "$kernel_log" ]; then echo "$kernel_log" | tail -n 200; else echo "kernel log unavailable"; fi
               |echo "::endgroup::"
               |echo "::group::Memory and disk"
               |free -m || true
               |df -h . || true
               |echo "::endgroup::"
               |echo "::group::JVM crash logs"
               |crash_logs=$(find . -maxdepth 3 -name 'hs_err_pid*.log' 2>/dev/null; find . -maxdepth 3 -name 'replay_pid*.log' 2>/dev/null) || true
               |if [ -n "$crash_logs" ]; then
               |  for f in $crash_logs; do echo "--- $f ---"; head -n 100 "$f"; done
               |else
               |  echo "no JVM crash logs"
               |fi
               |echo "::endgroup::"
               |""".stripMargin
          )
        )
      )
    )
  )

  lazy val testJS: Def.Initialize[Job] = Def.setting(
    Job(
      id = "testJS",
      name = "testJS",
      jobTimeout = Some(50),
      strategy = Some(
        Strategy(
          matrix = Map("java" -> List("17"), "platform" -> List("JS"), "scala" -> Scalas),
          failFast = false
        )
      ),
      steps = Seq(
        CheckoutCurrentBranch,
        SetupCoursier("${{ matrix.java }}"),
        CacheScalaDependencies,
        SingleStep(
          name = "Run Scala JS tests and compile docs",
          run = Some("sbt ++${{ matrix.scala }} testJS docJS")
        )
      )
    )
  )

  /**
   * Golem spans four Scala versions, including 2.12 for the sbt plugin, so it
   * gets its own job rather than another axis on testJVM.
   */
  lazy val testGolem: Def.Initialize[Job] = Def.setting(
    Job(
      id = "testGolem",
      name = "Test Golem (Scala 3.8.x / 2.13.x)",
      jobTimeout = Some(25),
      strategy = Some(Strategy(matrix = Map("java" -> List("17")), failFast = false)),
      steps = Seq(
        CheckoutCurrentBranch,
        SetupCoursier("${{ matrix.java }}"),
        CacheScalaDependencies,
        SingleStep(
          name = "Run Golem tests (Scala 3.8)",
          run = Some(
            """sbt "++3.8.2; zioGolemModelJVM/test; zioGolemModelJS/test; zioGolemCoreJS/test; zioGolemMacros/test; zioGolemTestAgents/fastLinkJS""""
          )
        ),
        SingleStep(
          name = "Run Golem tests (Scala 2.13)",
          run = Some(
            """sbt "++2.13.18; zioGolemModelJVM/test; zioGolemModelJS/test; zioGolemCoreJS/test; zioGolemMacros/test; zioGolemTestAgents/fastLinkJS""""
          )
        ),
        SingleStep(
          name = "Test Golem build codegen (Scala 3.3)",
          run = Some("""sbt "++3.3.7; zioGolemBuildCodegen/test"""")
        ),
        SingleStep(
          name = "Compile Golem sbt plugin (Scala 2.12)",
          run = Some("""sbt "++2.12.21!; zioGolemSbt/compile"""")
        )
      )
    )
  )

  // ---------------------------------------------------------------------------------------------
  // Release
  // ---------------------------------------------------------------------------------------------

  /**
   * Publishing is wrapped in a retry: Sonatype staging is intermittently flaky,
   * and a failed release leaves a partially staged repository that has to be
   * dropped by hand.
   *
   * The condition is spelled out rather than
   * `github.event_name != 'pull_request'` because the plugin always adds a
   * workflow_dispatch trigger, and a manual run must not publish.
   */
  lazy val release: Def.Initialize[Job] = Def.setting(
    Job(
      id = "release",
      name = "release",
      jobTimeout = Some(60),
      concurrency = Some(
        Concurrency(group = "release-${{ github.ref }}", cancelInProgress = CancelInProgress.Never)
      ),
      need = Seq("ci"),
      condition = Some(
        expr("github.event_name == 'release' && github.event.action == 'published'") ||
          expr("github.event_name == 'push'")
      ),
      steps = Seq(
        CheckoutCurrentBranch,
        SetupCoursier("25", pinJvmIndex = false),
        CacheDependencies,
        SingleStep(
          name = "Release",
          uses = Some(ActionRef("nick-fields/retry@v4")),
          parameters = Map(
            "timeout_minutes" -> Json.Num(30),
            "max_attempts"    -> Json.Num(3),
            "command"         -> Json.Str("sbt ci-release")
          ),
          env = Map(
            "PGP_PASSPHRASE"    -> "${{ secrets.PGP_PASSPHRASE }}",
            "PGP_SECRET"        -> "${{ secrets.PGP_SECRET }}",
            "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
            "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}"
          )
        )
      )
    )
  )

  lazy val releaseDocs: Def.Initialize[Job] = Def.setting(
    Job(
      id = "release-docs",
      name = "Release Docs",
      jobTimeout = Some(45),
      concurrency = Some(
        Concurrency(
          group = "release-docs-${{ github.ref }}",
          cancelInProgress = CancelInProgress.Never
        )
      ),
      need = Seq("release"),
      condition = Some(
        expr("github.event_name == 'release' && github.event.action == 'published'") ||
          expr("github.event_name == 'workflow_dispatch'")
      ),
      steps = Seq(
        Checkout.value,
        SetupJava("25"),
        SetupSBT,
        CacheDependencies,
        SingleStep(
          name = "Setup NodeJs",
          uses = Some(ActionRef("actions/setup-node@v7")),
          parameters = Map(
            "node-version" -> Json.Str("24.12.0"),
            "registry-url" -> Json.Str("https://registry.npmjs.org")
          )
        ),
        SingleStep(name = "Publish Docs to NPM Registry", run = Some("sbt docs/publishToNpm"))
      )
    )
  )
}
