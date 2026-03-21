package golem.host.js

import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array

// ---------------------------------------------------------------------------
// wasi:keyvalue  –  JS facade traits
// ---------------------------------------------------------------------------

@js.native
sealed trait JsKvBucket extends js.Object

@js.native
sealed trait JsKvOutgoingValue extends js.Object {
  def outgoingValueWriteBodySync(value: Uint8Array): Unit = js.native
}

@js.native
sealed trait JsKvIncomingValue extends js.Object {
  def incomingValueConsumeSync(): Uint8Array = js.native
  def incomingValueSize(): js.BigInt         = js.native
}
