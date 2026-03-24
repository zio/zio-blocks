package golem.host

import scala.scalajs.js

package object js {
  // CoreTypes
  type JsNodeIndex    = Int
  type JsResourceId   = js.BigInt
  type JsResourceMode = String // "owned" | "borrowed"

  // golem:agent/common@1.5.0
  type JsAgentMode         = String // "durable" | "ephemeral"
  type JsAgentConfigSource = String // "local" | "secret"
  type JsSystemVariable    = String // "agent-type" | "agent-version"

  // golem:agent/agent-host@1.5.0
  type JsUpdateMode             = String // "automatic" | "snapshot-based"
  type JsAgentStatus            = String // "running" | "idle" | "suspended" | "interrupted" | "retrying" | "failed" | "exited"
  type JsFilterComparator       = String // "equal" | "not-equal" | "greater-equal" | "greater" | "less-equal" | "less"
  type JsStringFilterComparator = String // "equal" | "not-equal" | "like" | "not-like" | "starts-with"

  // DurabilityTypes
  type JsDurableFunctionType = JsWrappedFunctionType
  type JsOplogEntryVersion   = String // "v1" | "v2"
}
