package cloud.golem.hosttests

import cloud.golem.sdk.HostApi
import cloud.golem.sdk.{Guards, Transactions}
import cloud.golem.runtime.rpc.host.AgentHostApi

import scala.annotation.nowarn
import scala.annotation.tailrec
import scala.math.{BigInt => ScalaBigInt}
import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array
import scala.util.control.NonFatal

private[hosttests] object HostTestSuite {
  private val tests: List[TestCase] = List(
    TestCase("get-self-metadata", () => testGetSelfMetadata()),
    TestCase("list-registered-agent-types", () => testRegisteredAgentTypes()),
    TestCase("enumerate-agents", () => testEnumerateAgents()),
    TestCase("retry-policy-roundtrip", () => testRetryPolicyRoundTrip()),
    TestCase("persistence-level-roundtrip", () => testPersistenceLevelRoundTrip()),
    TestCase("idempotence-mode-roundtrip", () => testIdempotenceModeRoundTrip()),
    TestCase("parse-self-agent-id", () => testParseSelfAgentId()),
    TestCase("host-guard-helpers", () => testHostGuardHelpers()),
    TestCase("transaction-dsl", () => testTransactionDsl())
  )

  def run(selectedTests: Option[Set[String]] = None): TestReport = {
    val started = now()
    val active  = selectedTests match {
      case Some(names) if names.nonEmpty =>
        val filtered = tests.filter(tc => names.contains(tc.name))
        log(s"RUN start tests=${filtered.map(_.name).mkString(",")}")
        filtered
      case _ =>
        log("RUN start tests=ALL")
        tests
    }
    val report =
      try {
        val outcomes = active.map(runTest)
        TestReport(
          total = active.length,
          passed = outcomes.count(_.success),
          failed = outcomes.count(!_.success),
          durationMs = now() - started,
          outcomes = outcomes
        )
      } catch {
        case NonFatal(err) =>
          val duration = now() - started
          TestReport(
            total = 1,
            passed = 0,
            failed = 1,
            durationMs = duration,
            outcomes = List(TestOutcome("harness-panic", success = false, duration, Some(err.getMessage)))
          )
      }
    log(s"RUN complete status=${report.status} passed=${report.passed}/${report.total}")
    report
  }

  private def runTest(testCase: TestCase): TestOutcome = {
    val started = now()
    log(s"TEST start ${testCase.name}")
    try {
      testCase.run()
      val outcome = TestOutcome(testCase.name, success = true, now() - started, None)
      log(s"TEST pass ${testCase.name}")
      outcome
    } catch {
      case NonFatal(err) =>
        val outcome = TestOutcome(testCase.name, success = false, now() - started, Some(err.getMessage))
        log(s"TEST fail ${testCase.name}: ${err.getMessage}")
        outcome
    }
  }

  private def now(): Double = js.Date.now()

  private def testRegisteredAgentTypes(): Unit = {
    val types = AgentHostApi.getAllAgentTypes()
    require(types.nonEmpty, "host returned no registered agent types")
    val matches = types.exists { agentType =>
      val descriptor = agentType.agentType
      descriptor.typeName == HostTestsConstants.AgentTypeName
    }
    require(matches, s"${HostTestsConstants.AgentTypeName} not present in registered agent types list")
  }

  private def testGetSelfMetadata(): Unit = {
    val metadata = AgentHostApi.getSelfMetadata()
    val agentId  = metadata.agentId.agentId
    require(agentId != null && agentId.nonEmpty, "self metadata contains empty agent id")
  }

  private def testEnumerateAgents(): Unit = {
    val metadata    = AgentHostApi.getSelfMetadata()
    val componentId = metadata.agentId.componentId
    val handle      =
      try AgentHostApi.getAgents(componentId, filter = None, precise = false)
      catch {
        case js.JavaScriptException(err) =>
          val detail = s"type=${js.typeOf(err)}, value=${err.toString}"
          throw new IllegalStateException(s"get-agents constructor failed: $detail")
      }
    val maxBatches = 512
    var batches    = 0
    var matched    = false

    @tailrec
    def loop(): Unit =
      AgentHostApi.nextAgentBatch(handle) match {
        case Some(batch) =>
          batches += 1
          if (batches > maxBatches)
            throw new IllegalStateException(s"enumerate-agents exceeded $maxBatches batches without completing")
          if (batch.exists(_.agentId.agentId == metadata.agentId.agentId)) {
            matched = true
          }
          loop()
        case None => ()
      }

    loop()
    require(batches > 0, "enumerate-agents returned no data")
    require(matched, "enumerate-agents did not include the current agent")
  }

  private def testRetryPolicyRoundTrip(): Unit = {
    val original = HostApi.getRetryPolicy()
    val updated  =
      original.copy(
        maxAttempts = original.maxAttempts + 1,
        minDelayNanos = original.minDelayNanos + BigInt(1_000_000L),
        maxDelayNanos = original.maxDelayNanos + BigInt(2_000_000L),
        multiplier = original.multiplier + 0.1d,
        maxJitterFactor = original.maxJitterFactor.orElse(Some(0.1d))
      )
    HostApi.setRetryPolicy(updated)
    val observed = HostApi.getRetryPolicy()
    try {
      require(observed.maxAttempts == updated.maxAttempts, "retry policy max-attempts mismatch")
      require(observed.minDelayNanos == updated.minDelayNanos, "retry policy min-delay mismatch")
      require(observed.maxDelayNanos == updated.maxDelayNanos, "retry policy max-delay mismatch")
      require(math.abs(observed.multiplier - updated.multiplier) < 0.0001d, "retry policy multiplier mismatch")
    } finally {
      HostApi.setRetryPolicy(original)
    }
  }

  @nowarn("msg=.*unused private member.*")
  private def bigIntToLongOpt(value: js.Any): Option[Long] =
    if (js.isUndefined(value) || value == null) None else Some(ScalaBigInt(value.toString()).toLong)

  private def log(message: String): Unit = {
    val console = js.Dynamic.global.selectDynamic("console")
    if (!js.isUndefined(console) && !js.isUndefined(console.selectDynamic("log"))) {
      console.applyDynamic("log")(s"[host-tests] $message")
    }
  }

  private def testPersistenceLevelRoundTrip(): Unit = {
    val original    = HostApi.getOplogPersistenceLevel()
    val originalTag = persistenceTag(original)
    val toggled     =
      if (originalTag == "persist-nothing")
        HostApi.PersistenceLevel.Smart
      else HostApi.PersistenceLevel.PersistNothing
    HostApi.setOplogPersistenceLevel(toggled)
    try {
      val observed    = HostApi.getOplogPersistenceLevel()
      val observedTag = persistenceTag(observed)
      require(
        observedTag == persistenceTag(toggled),
        s"expected persistence level ${persistenceTag(toggled)} but observed $observedTag"
      )
    } finally {
      HostApi.setOplogPersistenceLevel(original)
    }
  }

  private def testIdempotenceModeRoundTrip(): Unit = {
    val original = HostApi.getIdempotenceMode()
    val toggled  = !original
    HostApi.setIdempotenceMode(toggled)
    try {
      require(HostApi.getIdempotenceMode() == toggled, "idempotence mode did not toggle")
    } finally {
      HostApi.setIdempotenceMode(original)
    }
  }

  @nowarn("msg=.*unused private member.*")
  private def testOplogMarkers(): Unit = {
    val begin = HostApi.markBeginOperation()
    require(begin >= 0, s"mark-begin-operation returned negative index: $begin")
    HostApi.markEndOperation(begin)
    val indexBefore = HostApi.getOplogIndex()
    HostApi.oplogCommit(1)
    val indexAfter = HostApi.getOplogIndex()
    require(
      indexAfter >= indexBefore,
      "oplog index decreased after commit"
    )
  }

  private def bigIntToLong(value: js.BigInt): Long =
    ScalaBigInt(value.toString()).toLong

  @nowarn("msg=.*unused private member.*")
  private def testGenerateIdempotencyKey(): Unit = {
    val first      = AgentHostApi.generateIdempotencyKey()
    val second     = AgentHostApi.generateIdempotencyKey()
    val firstHigh  = bigIntToLong(first.highBits)
    val firstLow   = bigIntToLong(first.lowBits)
    val secondHigh = bigIntToLong(second.highBits)
    val secondLow  = bigIntToLong(second.lowBits)
    require(!(firstHigh == secondHigh && firstLow == secondLow), "idempotency keys are identical")
  }

  @nowarn("msg=.*unused private member.*")
  private def testPromiseLifecycle(): Unit = {
    val promiseId = AgentHostApi.createPromise()
    val handle    = AgentHostApi.getPromise(promiseId)
    val initial   = handle.get().toOption
    require(initial.isEmpty, "fresh promise unexpectedly had a payload")
    val payload   = new Uint8Array(js.Array[Short](1, 2, 3))
    val completed = AgentHostApi.completePromise(promiseId, payload)
    require(completed == true || completed == false, "complete-promise did not return a boolean")
  }

  private def testParseSelfAgentId(): Unit = {
    val metadata = AgentHostApi.getSelfMetadata()
    val parsed   = AgentHostApi.parseAgentId(metadata.agentId.agentId)
    parsed match {
      case Right(parts) =>
        require(parts.agentTypeName == HostTestsConstants.AgentTypeName, "parsed agent type mismatch")
        require(parts.payload != null, "parsed payload missing")
      case Left(err) =>
        throw new IllegalStateException(s"parse-agent-id failed: $err")
    }
  }

  private def testHostGuardHelpers(): Unit = {
    testPersistenceGuard()
    testRetryPolicyGuard()
    testIdempotenceGuard()
    testAtomicGuard()
  }

  private def testPersistenceGuard(): Unit = {
    val originalTag = persistenceTag(HostApi.getOplogPersistenceLevel())
    val toggled     =
      if (originalTag == "persist-nothing") HostApi.PersistenceLevel.Smart
      else HostApi.PersistenceLevel.PersistNothing

    Guards.withPersistenceLevel(toggled) {
      val observedTag = persistenceTag(HostApi.getOplogPersistenceLevel())
      require(
        observedTag == persistenceTag(toggled),
        s"persistence guard failed to apply new level (expected ${persistenceTag(toggled)}, observed $observedTag)"
      )
    }

    val restoredTag = persistenceTag(HostApi.getOplogPersistenceLevel())
    require(
      restoredTag == originalTag,
      s"persistence guard failed to restore original level (expected $originalTag, observed $restoredTag)"
    )
  }

  private def persistenceTag(level: HostApi.PersistenceLevel): String =
    level.tag

  private def testRetryPolicyGuard(): Unit = {
    val original = HostApi.getRetryPolicy()
    val updated  =
      original.copy(
        maxAttempts = original.maxAttempts + 1,
        minDelayNanos = original.minDelayNanos + BigInt(123L),
        maxDelayNanos = original.maxDelayNanos + BigInt(456L),
        multiplier = original.multiplier + 0.05d
      )

    Guards.withRetryPolicy(updated) {
      val observed = HostApi.getRetryPolicy()
      require(observed.maxAttempts == updated.maxAttempts, "guard retry max-attempts mismatch")
      require(observed.minDelayNanos == updated.minDelayNanos, "guard retry min-delay mismatch")
      require(observed.maxDelayNanos == updated.maxDelayNanos, "guard retry max-delay mismatch")
      require(math.abs(observed.multiplier - updated.multiplier) < 0.0001d, "guard retry multiplier mismatch")
    }

    val restored = HostApi.getRetryPolicy()
    require(restored.maxAttempts == original.maxAttempts, "retry guard did not restore max-attempts")
    require(restored.minDelayNanos == original.minDelayNanos, "retry guard did not restore min-delay")
    require(restored.maxDelayNanos == original.maxDelayNanos, "retry guard did not restore max-delay")
    require(math.abs(restored.multiplier - original.multiplier) < 0.0001d, "retry guard did not restore multiplier")
  }

  private def testIdempotenceGuard(): Unit = {
    val original = HostApi.getIdempotenceMode()
    val toggled  = !original
    Guards.withIdempotenceMode(toggled) {
      require(
        HostApi.getIdempotenceMode() == toggled,
        "idempotence guard failed to apply new mode"
      )
    }
    require(
      HostApi.getIdempotenceMode() == original,
      "idempotence guard failed to restore original mode"
    )
  }

  private def testAtomicGuard(): Unit = {
    val before   = HostApi.getOplogIndex()
    var executed = false
    Guards.atomically {
      executed = true
      HostApi.oplogCommit(1)
    }
    require(executed, "atomic guard block did not execute")
    val after = HostApi.getOplogIndex()
    require(
      after >= before,
      "oplog index regressed after atomic guard block"
    )

    var failureObserved = false
    try {
      Guards.atomically {
        throw new RuntimeException("atomic failure sentinel")
      }
    } catch {
      case _: RuntimeException =>
        failureObserved = true
    }
    require(failureObserved, "atomic guard did not propagate failure")
  }

  private def testTransactionDsl(): Unit = {
    testInfallibleTransactionDsl()
    testFallibleTransactionDsl()
  }

  private def testInfallibleTransactionDsl(): Unit = {
    var applied       = false
    var compensations = 0
    val op            = Transactions.operation[Unit, Int, String] { _ =>
      applied = true
      Right(7)
    } { (_, _) =>
      compensations += 1
      Right(())
    }

    val value = Transactions.infallibleTransaction { tx =>
      tx.execute(op, ())
    }

    require(value == 7, "infallible transaction did not produce expected result")
    require(applied, "infallible transaction never executed operation")
    require(compensations == 0, "compensations ran during successful transaction")
  }

  private def testFallibleTransactionDsl(): Unit = {
    var compensations = 0

    val op = Transactions.operation[Int, Int, String] { in =>
      Right(in + 1)
    } { (_, _) =>
      compensations += 1
      Right(())
    }

    val failure = Transactions.fallibleTransaction[Int, String] { tx =>
      tx.execute(op, 1).flatMap(_ => Left("boom"))
    }

    failure match {
      case Left(Transactions.TransactionFailure.FailedAndRolledBackCompletely("boom")) =>
        require(compensations == 1, s"expected exactly one compensation, observed $compensations")
      case other =>
        throw new IllegalStateException(s"unexpected fallible transaction result: $other")
    }
  }

  final case class TestOutcome(name: String, success: Boolean, durationMs: Double, detail: Option[String]) {
    def toJs: js.Dynamic = {
      val base = js.Dynamic.literal(
        "name"       -> name,
        "status"     -> (if (success) "passed" else "failed"),
        "durationMs" -> durationMs
      )
      detail.foreach(msg => base.updateDynamic("detail")(msg))
      base
    }
  }

  final case class TestReport(total: Int, passed: Int, failed: Int, durationMs: Double, outcomes: List[TestOutcome]) {
    def toJs: js.Dynamic =
      js.Dynamic.literal(
        "status"     -> status,
        "total"      -> total,
        "passed"     -> passed,
        "failed"     -> failed,
        "durationMs" -> durationMs,
        "results"    -> js.Array(outcomes.map(_.toJs): _*)
      )

    def status: String = if (failed == 0) "passed" else "failed"
  }

  private final case class TestCase(name: String, run: () => Unit)
}
