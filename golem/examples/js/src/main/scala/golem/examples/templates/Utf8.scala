package golem.examples.templates

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import scala.scalajs.js.typedarray.Uint8Array
import scala.annotation.unused

private[golem] object Utf8 {
  @js.native
  @JSGlobal("TextEncoder")
  private class TextEncoder() extends js.Object {
    def encode(input: String): Uint8Array = js.native
  }

  @js.native
  @JSGlobal("TextDecoder")
  private class TextDecoder(@unused label: String = "utf-8") extends js.Object {
    def decode(input: Uint8Array): String = js.native
  }

  def encode(input: String): Uint8Array =
    new TextEncoder().encode(input)

  def decode(bytes: Uint8Array): String =
    new TextDecoder("utf-8").decode(bytes)
}
