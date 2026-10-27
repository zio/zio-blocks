//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-mux:0.0.56
//> using file Frame.scala
// Run from this folder:  scala-cli run Step4FrameFormatExample.scala
// Or from mux-examples:  sbt "runMain muxoverasocket.Step4FrameFormatExample"

package muxoverasocket

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream, EOFException}

/**
 * How to Run a Mux over a Real Socket — Define a Frame Format
 *
 * Encodes three frames into bytes, decodes them back, and shows what a
 * connection that breaks in the middle of a frame looks like.
 */
object Step4FrameFormatExample {

  def main(args: Array[String]): Unit = {
    val bytes = new ByteArrayOutputStream()
    val out   = new DataOutputStream(bytes)
    Frame.write(out, Frame.Data(1, "hi"))
    Frame.write(out, Frame.End(1))
    Frame.write(out, Frame.Reset(3, "cancelled"))
    out.flush()
    val wire = bytes.toByteArray
    val in   = new DataInputStream(new ByteArrayInputStream(wire))
    val cut  = new DataInputStream(new ByteArrayInputStream(wire.take(7))) // a frame cut off after 7 bytes

    println(s"first frame on the wire: ${wire.take(11).map(b => f"$b%02x").mkString(" ")}")
    println(Frame.read(in))
    println(Frame.read(in))
    println(Frame.read(in))
    println(Frame.read(in))
    try println(Frame.read(cut))
    catch { case e: EOFException => println(s"cut mid-frame: $e") }
  }
}
