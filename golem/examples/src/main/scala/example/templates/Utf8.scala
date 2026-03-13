package example.templates

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import scala.scalajs.js.typedarray.Uint8Array
import scala.annotation.unused

private[templates] object Utf8 {
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

  def encodeBytes(input: String): Array[Byte] = {
    val u8  = encode(input)
    val out = new Array[Byte](u8.length)
    var i   = 0
    while (i < u8.length) {
      out(i) = u8(i).toByte
      i += 1
    }
    out
  }

  def decodeBytes(bytes: Array[Byte]): String = {
    val u8 = new Uint8Array(bytes.length)
    var i  = 0
    while (i < bytes.length) {
      u8(i) = (bytes(i) & 0xff).toShort
      i += 1
    }
    decode(u8)
  }
}
