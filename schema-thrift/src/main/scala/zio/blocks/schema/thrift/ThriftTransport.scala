package zio.blocks.schema.thrift

import org.apache.thrift.transport.TTransport
import java.nio.ByteBuffer

private[thrift] class ReadByteBufferTransport(buffer: ByteBuffer) extends TTransport {
  override def isOpen: Boolean = true
  override def open(): Unit = {}
  override def close(): Unit = {}

  override def read(buf: Array[Byte], off: Int, len: Int): Int = {
    if (!buffer.hasRemaining) return -1 // EOF
    val remaining = buffer.remaining()
    val toRead = math.min(len, remaining)
    buffer.get(buf, off, toRead)
    toRead
  }

  override def write(buf: Array[Byte], off: Int, len: Int): Unit =
    throw new UnsupportedOperationException("Read-only transport")
  
  override def getConfiguration: org.apache.thrift.TConfiguration = new org.apache.thrift.TConfiguration()
  override def updateKnownMessageSize(size: Long): Unit = {}
  override def checkReadBytesAvailable(numBytes: Long): Unit = 
     if (buffer.remaining() < numBytes) throw new org.apache.thrift.transport.TTransportException("Not enough bytes remaining")
}

private[thrift] class WriteByteBufferTransport(buffer: ByteBuffer) extends TTransport {
  override def isOpen: Boolean = true
  override def open(): Unit = {}
  override def close(): Unit = {}

  override def read(buf: Array[Byte], off: Int, len: Int): Int =
    throw new UnsupportedOperationException("Write-only transport")

  override def write(buf: Array[Byte], off: Int, len: Int): Unit = {
    // Ensure buffer has space? BinaryCodec assumes buffer is large enough or handles it?
    // Codec.scala doesn't specify strategy for resizing.
    // AvroBinaryCodec uses a wrapper OutputStream that writes to ByteBuffer.
    // If we reach limit, ByteBuffer throws BufferOverflowException.
    buffer.put(buf, off, len)
  }
  
  override def getConfiguration: org.apache.thrift.TConfiguration = new org.apache.thrift.TConfiguration()
  override def updateKnownMessageSize(size: Long): Unit = {}
  override def checkReadBytesAvailable(numBytes: Long): Unit = {}
}
