package golem.host

import golem.HostApi
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.scalajs.js

class OplogEntryRoundtripSpec extends AnyFunSuite with Matchers {
  import OplogApi._

  private def ts(seconds: Int = 1700000000, nanos: Int = 500000000): js.Dynamic =
    js.Dynamic.literal(
      seconds = js.BigInt(seconds.toString),
      nanoseconds = nanos
    )

  private def wrapEntry(tag: String, v: js.Dynamic): js.Dynamic =
    js.Dynamic.literal(tag = tag, `val` = v)

  private def simpleTimestampEntry(v: js.Dynamic): js.Dynamic =
    js.Dynamic.literal(timestamp = v)

  // --- Simple timestamp-only entries ---

  test("Suspend from dynamic") {
    val raw    = wrapEntry("suspend", js.Dynamic.literal(timestamp = ts()))
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.Suspend]
    parsed.timestamp.seconds shouldBe BigInt(1700000000)
  }

  test("NoOp from dynamic") {
    val raw    = wrapEntry("no-op", js.Dynamic.literal(timestamp = ts()))
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.NoOp]
  }

  test("Interrupted from dynamic") {
    val raw    = wrapEntry("interrupted", js.Dynamic.literal(timestamp = ts()))
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.Interrupted]
  }

  test("Exited from dynamic") {
    val raw    = wrapEntry("exited", js.Dynamic.literal(timestamp = ts()))
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.Exited]
  }

  test("BeginAtomicRegion from dynamic") {
    val raw    = wrapEntry("begin-atomic-region", js.Dynamic.literal(timestamp = ts()))
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.BeginAtomicRegion]
  }

  test("BeginRemoteWrite from dynamic") {
    val raw    = wrapEntry("begin-remote-write", js.Dynamic.literal(timestamp = ts()))
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.BeginRemoteWrite]
  }

  test("Restart from dynamic") {
    val raw    = wrapEntry("restart", js.Dynamic.literal(timestamp = ts()))
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.Restart]
  }

  // --- Single-field parameter entries ---

  test("Error from dynamic") {
    val raw = wrapEntry(
      "error",
      js.Dynamic.literal(
        timestamp = ts(),
        error = "something failed",
        retryFrom = js.BigInt("5")
      )
    )
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.Error]
    val e = parsed.asInstanceOf[OplogEntry.Error]
    e.params.error shouldBe "something failed"
    e.params.retryFrom shouldBe BigInt(5)
  }

  test("EndAtomicRegion from dynamic") {
    val raw = wrapEntry(
      "end-atomic-region",
      js.Dynamic.literal(
        timestamp = ts(),
        beginIndex = js.BigInt("10")
      )
    )
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.EndAtomicRegion]
    parsed.asInstanceOf[OplogEntry.EndAtomicRegion].params.beginIndex shouldBe BigInt(10)
  }

  test("EndRemoteWrite from dynamic") {
    val raw = wrapEntry(
      "end-remote-write",
      js.Dynamic.literal(
        timestamp = ts(),
        beginIndex = js.BigInt("20")
      )
    )
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.EndRemoteWrite]
    parsed.asInstanceOf[OplogEntry.EndRemoteWrite].params.beginIndex shouldBe BigInt(20)
  }

  test("GrowMemory from dynamic") {
    val raw = wrapEntry(
      "grow-memory",
      js.Dynamic.literal(
        timestamp = ts(),
        delta = js.BigInt("65536")
      )
    )
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.GrowMemory]
    parsed.asInstanceOf[OplogEntry.GrowMemory].params.delta shouldBe BigInt(65536)
  }

  test("CancelInvocation from dynamic") {
    val raw = wrapEntry(
      "cancel-invocation",
      js.Dynamic.literal(
        timestamp = ts(),
        idempotencyKey = "idem-123"
      )
    )
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.CancelInvocation]
    parsed.asInstanceOf[OplogEntry.CancelInvocation].params.idempotencyKey shouldBe "idem-123"
  }

  test("FinishSpan from dynamic") {
    val raw = wrapEntry(
      "finish-span",
      js.Dynamic.literal(
        timestamp = ts(),
        spanId = "span-42"
      )
    )
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.FinishSpan]
    parsed.asInstanceOf[OplogEntry.FinishSpan].params.spanId shouldBe "span-42"
  }

  test("ChangePersistenceLevel from dynamic") {
    val raw = wrapEntry(
      "change-persistence-level",
      js.Dynamic.literal(
        timestamp = ts(),
        persistenceLevel = js.Dynamic.literal(tag = "smart")
      )
    )
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.ChangePersistenceLevel]
    parsed
      .asInstanceOf[OplogEntry.ChangePersistenceLevel]
      .params
      .persistenceLevel shouldBe HostApi.PersistenceLevel.Smart
  }

  test("BeginRemoteTransaction from dynamic") {
    val raw = wrapEntry(
      "begin-remote-transaction",
      js.Dynamic.literal(
        timestamp = ts(),
        transactionId = "tx-1"
      )
    )
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.BeginRemoteTransaction]
    parsed.asInstanceOf[OplogEntry.BeginRemoteTransaction].params.transactionId shouldBe "tx-1"
  }

  test("PreCommitRemoteTransaction from dynamic") {
    val raw = wrapEntry(
      "pre-commit-remote-transaction",
      js.Dynamic.literal(
        timestamp = ts(),
        beginIndex = js.BigInt("30")
      )
    )
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.PreCommitRemoteTransaction]
    parsed.asInstanceOf[OplogEntry.PreCommitRemoteTransaction].params.beginIndex shouldBe BigInt(30)
  }

  test("PreRollbackRemoteTransaction from dynamic") {
    val raw = wrapEntry(
      "pre-rollback-remote-transaction",
      js.Dynamic.literal(
        timestamp = ts(),
        beginIndex = js.BigInt("31")
      )
    )
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.PreRollbackRemoteTransaction]
  }

  test("CommittedRemoteTransaction from dynamic") {
    val raw = wrapEntry(
      "committed-remote-transaction",
      js.Dynamic.literal(
        timestamp = ts(),
        beginIndex = js.BigInt("32")
      )
    )
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.CommittedRemoteTransaction]
  }

  test("RolledBackRemoteTransaction from dynamic") {
    val raw = wrapEntry(
      "rolled-back-remote-transaction",
      js.Dynamic.literal(
        timestamp = ts(),
        beginIndex = js.BigInt("33")
      )
    )
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.RolledBackRemoteTransaction]
  }

  // --- Complex entries ---

  test("Jump from dynamic") {
    val raw = wrapEntry(
      "jump",
      js.Dynamic.literal(
        timestamp = ts(),
        jump = js.Dynamic.literal(start = js.BigInt("0"), end = js.BigInt("10"))
      )
    )
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.Jump]
    val j = parsed.asInstanceOf[OplogEntry.Jump]
    j.params.jump.start shouldBe BigInt(0)
    j.params.jump.end shouldBe BigInt(10)
  }

  test("ChangeRetryPolicy from dynamic") {
    val raw = wrapEntry(
      "change-retry-policy",
      js.Dynamic.literal(
        timestamp = ts(),
        newPolicy = js.Dynamic.literal(
          maxAttempts = 5,
          minDelay = js.BigInt("1000000"),
          maxDelay = js.BigInt("60000000000"),
          multiplier = 2.0,
          maxJitterFactor = 0.1
        )
      )
    )
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.ChangeRetryPolicy]
    val p = parsed.asInstanceOf[OplogEntry.ChangeRetryPolicy].params.newPolicy
    p.maxAttempts shouldBe 5
    p.multiplier shouldBe 2.0
    p.maxJitterFactor shouldBe Some(0.1)
  }

  test("Log from dynamic") {
    val raw = wrapEntry(
      "log",
      js.Dynamic.literal(
        timestamp = ts(),
        level = js.Dynamic.literal(tag = "info"),
        context = "main",
        message = "Agent started"
      )
    )
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.Log]
    val l = parsed.asInstanceOf[OplogEntry.Log]
    l.params.level shouldBe LogLevel.Info
    l.params.context shouldBe "main"
    l.params.message shouldBe "Agent started"
  }

  test("ExportedFunctionCompleted with response from dynamic") {
    val vatDyn = js.Dynamic.literal(
      value = js.Dynamic.literal(nodes =
        js.Array(
          js.Dynamic.literal(tag = "prim-string", `val` = "result")
        )
      ),
      typ = js.Dynamic.literal(nodes =
        js.Array(
          js.Dynamic.literal(
            name = "r",
            owner = js.undefined,
            `type` = js.Dynamic.literal(tag = "prim-string-type")
          )
        )
      )
    )
    val raw = wrapEntry(
      "exported-function-completed",
      js.Dynamic.literal(
        timestamp = ts(),
        response = vatDyn,
        consumedFuel = 1000.0
      )
    )
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.ExportedFunctionCompleted]
    val c = parsed.asInstanceOf[OplogEntry.ExportedFunctionCompleted]
    c.params.response.isDefined shouldBe true
    c.params.consumedFuel shouldBe 1000L
    c.params.response.get.value.nodes.head shouldBe WitValueTypes.WitNode.PrimString("result")
  }

  test("ExportedFunctionCompleted without response from dynamic") {
    val raw = wrapEntry(
      "exported-function-completed",
      js.Dynamic.literal(
        timestamp = ts(),
        response = js.undefined,
        consumedFuel = 0.0
      )
    )
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.ExportedFunctionCompleted]
    parsed.asInstanceOf[OplogEntry.ExportedFunctionCompleted].params.response shouldBe None
  }

  test("ImportedFunctionInvoked from dynamic") {
    val vatDyn = js.Dynamic.literal(
      value = js.Dynamic.literal(nodes = js.Array(js.Dynamic.literal(tag = "prim-s32", `val` = 42))),
      typ = js.Dynamic.literal(nodes =
        js.Array(
          js.Dynamic
            .literal(name = js.undefined, owner = js.undefined, `type` = js.Dynamic.literal(tag = "prim-s32-type"))
        )
      )
    )
    val raw = wrapEntry(
      "imported-function-invoked",
      js.Dynamic.literal(
        timestamp = ts(),
        functionName = "wasi:io/read",
        request = vatDyn,
        response = vatDyn,
        wrappedFunctionType = js.Dynamic.literal(tag = "read-remote")
      )
    )
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.ImportedFunctionInvoked]
    val i = parsed.asInstanceOf[OplogEntry.ImportedFunctionInvoked]
    i.params.functionName shouldBe "wasi:io/read"
    i.params.wrappedFunctionType shouldBe DurabilityApi.DurableFunctionType.ReadRemote
  }

  test("CreateResource from dynamic") {
    val raw = wrapEntry(
      "create-resource",
      js.Dynamic.literal(
        timestamp = ts(),
        resourceId = js.BigInt("1"),
        name = "handle",
        owner = "golem:api"
      )
    )
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.CreateResource]
    val cr = parsed.asInstanceOf[OplogEntry.CreateResource]
    cr.params.resourceId shouldBe BigInt(1)
    cr.params.name shouldBe "handle"
    cr.params.owner shouldBe "golem:api"
  }

  test("DropResource from dynamic") {
    val raw = wrapEntry(
      "drop-resource",
      js.Dynamic.literal(
        timestamp = ts(),
        resourceId = js.BigInt("1"),
        name = "handle",
        owner = "golem:api"
      )
    )
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.DropResource]
  }

  test("ActivatePlugin from dynamic") {
    val raw = wrapEntry(
      "activate-plugin",
      js.Dynamic.literal(
        timestamp = ts(),
        plugin = js.Dynamic.literal(
          name = "my-plugin",
          version = "1.0",
          parameters = js.Array(js.Tuple2("key", "val"))
        )
      )
    )
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.ActivatePlugin]
    val p = parsed.asInstanceOf[OplogEntry.ActivatePlugin].params.plugin
    p.name shouldBe "my-plugin"
    p.parameters shouldBe Map("key" -> "val")
  }

  test("DeactivatePlugin from dynamic") {
    val raw = wrapEntry(
      "deactivate-plugin",
      js.Dynamic.literal(
        timestamp = ts(),
        plugin = js.Dynamic.literal(
          name = "my-plugin",
          version = "2.0",
          parameters = js.Array[js.Any]()
        )
      )
    )
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.DeactivatePlugin]
  }

  test("Revert from dynamic") {
    val raw = wrapEntry(
      "revert",
      js.Dynamic.literal(
        timestamp = ts(),
        start = js.BigInt("0"),
        end = js.BigInt("10")
      )
    )
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.Revert]
    val r = parsed.asInstanceOf[OplogEntry.Revert]
    r.params.start shouldBe BigInt(0)
    r.params.end shouldBe BigInt(10)
  }

  test("StartSpan from dynamic with attributes") {
    val raw = wrapEntry(
      "start-span",
      js.Dynamic.literal(
        timestamp = ts(),
        spanId = "span-1",
        parent = "parent-span",
        linkedContext = "linked",
        attributes = js.Array(
          js.Dynamic.literal(
            key = "env",
            value = js.Dynamic.literal(tag = "string", `val` = "prod")
          )
        )
      )
    )
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.StartSpan]
    val s = parsed.asInstanceOf[OplogEntry.StartSpan]
    s.params.spanId shouldBe "span-1"
    s.params.parent shouldBe Some("parent-span")
    s.params.linkedContext shouldBe Some("linked")
    s.params.attributes.size shouldBe 1
    s.params.attributes.head.key shouldBe "env"
  }

  test("StartSpan from dynamic without optional fields") {
    val raw = wrapEntry(
      "start-span",
      js.Dynamic.literal(
        timestamp = ts(),
        spanId = "span-2",
        parent = js.undefined,
        linkedContext = js.undefined,
        attributes = js.undefined
      )
    )
    val parsed = OplogEntry.fromDynamic(raw)
    val s      = parsed.asInstanceOf[OplogEntry.StartSpan]
    s.params.parent shouldBe None
    s.params.linkedContext shouldBe None
    s.params.attributes shouldBe empty
  }

  test("SetSpanAttribute from dynamic") {
    val raw = wrapEntry(
      "set-span-attribute",
      js.Dynamic.literal(
        timestamp = ts(),
        spanId = "span-1",
        key = "priority",
        value = js.Dynamic.literal(tag = "string", `val` = "high")
      )
    )
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.SetSpanAttribute]
    val sa = parsed.asInstanceOf[OplogEntry.SetSpanAttribute]
    sa.params.key shouldBe "priority"
    sa.params.value shouldBe ContextApi.AttributeValue.StringValue("high")
  }

  test("FailedUpdate with details from dynamic") {
    val raw = wrapEntry(
      "failed-update",
      js.Dynamic.literal(
        timestamp = ts(),
        targetRevision = js.BigInt("3"),
        details = "compile error"
      )
    )
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.FailedUpdate]
    parsed.asInstanceOf[OplogEntry.FailedUpdate].params.details shouldBe Some("compile error")
  }

  test("FailedUpdate without details from dynamic") {
    val raw = wrapEntry(
      "failed-update",
      js.Dynamic.literal(
        timestamp = ts(),
        targetRevision = js.BigInt("3"),
        details = js.undefined
      )
    )
    val parsed = OplogEntry.fromDynamic(raw)
    parsed.asInstanceOf[OplogEntry.FailedUpdate].params.details shouldBe None
  }

  test("PendingUpdate auto-update from dynamic") {
    val raw = wrapEntry(
      "pending-update",
      js.Dynamic.literal(
        timestamp = ts(),
        targetRevision = js.BigInt("5"),
        updateDescription = js.Dynamic.literal(tag = "auto-update")
      )
    )
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.PendingUpdate]
    parsed.asInstanceOf[OplogEntry.PendingUpdate].params.updateDescription shouldBe UpdateDescription.AutoUpdate
  }

  test("PendingUpdate snapshot-based from dynamic") {
    val raw = wrapEntry(
      "pending-update",
      js.Dynamic.literal(
        timestamp = ts(),
        targetRevision = js.BigInt("5"),
        updateDescription = js.Dynamic.literal(tag = "snapshot-based", `val` = js.Array(1, 2, 3))
      )
    )
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.PendingUpdate]
    val ud = parsed.asInstanceOf[OplogEntry.PendingUpdate].params.updateDescription
    ud shouldBe a[UpdateDescription.SnapshotBased]
    ud.asInstanceOf[UpdateDescription.SnapshotBased].data.toList shouldBe List[Byte](1, 2, 3)
  }

  test("SuccessfulUpdate from dynamic") {
    val raw = wrapEntry(
      "successful-update",
      js.Dynamic.literal(
        timestamp = ts(),
        targetRevision = js.BigInt("3"),
        newComponentSize = js.BigInt("2048"),
        newActivePlugins = js.Array(
          js.Dynamic.literal(name = "p1", version = "1.0", parameters = js.Array[js.Any]())
        )
      )
    )
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.SuccessfulUpdate]
    val su = parsed.asInstanceOf[OplogEntry.SuccessfulUpdate]
    su.params.newComponentSize shouldBe BigInt(2048)
    su.params.newActivePlugins.size shouldBe 1
    su.params.newActivePlugins.head.name shouldBe "p1"
  }

  test("PendingAgentInvocation with exported-function from dynamic") {
    val raw = wrapEntry(
      "pending-agent-invocation",
      js.Dynamic.literal(
        timestamp = ts(),
        invocation = js.Dynamic.literal(
          tag = "exported-function",
          `val` = js.Dynamic.literal(
            idempotencyKey = "idem-1",
            functionName = "increment",
            input = js.undefined,
            traceId = "trace-1",
            traceStates = js.Array[String](),
            invocationContext = js.undefined
          )
        )
      )
    )
    val parsed = OplogEntry.fromDynamic(raw)
    parsed shouldBe a[OplogEntry.PendingAgentInvocation]
    val inv = parsed.asInstanceOf[OplogEntry.PendingAgentInvocation].params.invocation
    inv shouldBe a[AgentInvocation.ExportedFunction]
    inv.asInstanceOf[AgentInvocation.ExportedFunction].params.functionName shouldBe "increment"
  }

  test("PendingAgentInvocation with manual-update from dynamic") {
    val raw = wrapEntry(
      "pending-agent-invocation",
      js.Dynamic.literal(
        timestamp = ts(),
        invocation = js.Dynamic.literal(
          tag = "manual-update",
          `val` = js.BigInt("7")
        )
      )
    )
    val parsed = OplogEntry.fromDynamic(raw)
    val inv    = parsed.asInstanceOf[OplogEntry.PendingAgentInvocation].params.invocation
    inv shouldBe a[AgentInvocation.ManualUpdate]
    inv.asInstanceOf[AgentInvocation.ManualUpdate].componentRevision shouldBe BigInt(7)
  }

  // --- Edge cases ---

  test("unknown oplog entry tag throws") {
    val raw = js.Dynamic.literal(tag = "unknown-entry", `val` = js.Dynamic.literal())
    an[IllegalArgumentException] should be thrownBy OplogEntry.fromDynamic(raw)
  }

  test("LogLevel.fromString covers all variants") {
    LogLevel.fromString("stdout") shouldBe LogLevel.Stdout
    LogLevel.fromString("stderr") shouldBe LogLevel.Stderr
    LogLevel.fromString("trace") shouldBe LogLevel.Trace
    LogLevel.fromString("debug") shouldBe LogLevel.Debug
    LogLevel.fromString("info") shouldBe LogLevel.Info
    LogLevel.fromString("warn") shouldBe LogLevel.Warn
    LogLevel.fromString("error") shouldBe LogLevel.Error
    LogLevel.fromString("critical") shouldBe LogLevel.Critical
    LogLevel.fromString("unknown") shouldBe LogLevel.Info
  }
}
