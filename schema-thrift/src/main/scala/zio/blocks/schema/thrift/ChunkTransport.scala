package zio.blocks.schema.thrift

import org.apache.thrift.TConfiguration
import org.apache.thrift.transport.TTransport
import org.apache.thrift.transport.TTransportException

final class ChunkTransport(
  private[this] var readBuffer: Array[Byte] = null,
  private[this] var readPos: Int = 0,
  private[this] var readLimit: Int = 0
) extends TTransport {

  private[this] var writeBuffer: Array[Byte] = new Array[Byte](64)
  private[this] var writePos: Int            = 0

  override def isOpen: Boolean = true

  override def open(): Unit = ()

  override def close(): Unit = ()

  override def read(buf: Array[Byte], off: Int, len: Int): Int = {
    if (readBuffer eq null) throw new TTransportException("No read buffer")
    val available = readLimit - readPos
    if (available <= 0) throw new TTransportException("Unexpected end of input")
    val bytesToRead = Math.min(len, available)
    System.arraycopy(readBuffer, readPos, buf, off, bytesToRead)
    readPos += bytesToRead
    bytesToRead
  }

  override def write(buf: Array[Byte], off: Int, len: Int): Unit = {
    ensureCapacity(len)
    System.arraycopy(buf, off, writeBuffer, writePos, len)
    writePos += len
  }

  override def getBuffer: Array[Byte] = writeBuffer

  override def getBufferPosition: Int = writePos

  override def getConfiguration: TConfiguration = ChunkTransport.defaultConfig

  override def updateKnownMessageSize(size: Long): Unit = ()

  override def checkReadBytesAvailable(numBytes: Long): Unit = ()

  def toByteArray: Array[Byte] = java.util.Arrays.copyOf(writeBuffer, writePos)

  def reset(): Unit = {
    writePos = 0
    readPos = 0
  }

  def setReadBuffer(buffer: Array[Byte], offset: Int, length: Int): Unit = {
    readBuffer = buffer
    readPos = offset
    readLimit = offset + length
  }

  private[this] def ensureCapacity(additional: Int): Unit = {
    val newLen = writePos + additional
    if (newLen > writeBuffer.length) {
      val newSize = Math.max(writeBuffer.length << 1, newLen)
      writeBuffer = java.util.Arrays.copyOf(writeBuffer, newSize)
    }
  }
}

object ChunkTransport {
  private val defaultConfig: TConfiguration = new TConfiguration()
}
