package zio.blocks.schema.thrift

import org.apache.thrift.TConfiguration
import org.apache.thrift.transport.{TMemoryTransport, TTransport}

/**
 * Memory-based Thrift transport layer for reading and writing byte arrays.
 * Wraps Apache Thrift's TMemoryTransport to work with byte arrays directly.
 */
object ChunkTransport {

  /**
   * Write transport that accumulates written bytes into an array. Uses
   * TMemoryTransport internally for Thrift protocol compatibility.
   */
  class Write extends TTransport {
    private val underlying = new TMemoryTransport(Array.emptyByteArray)

    override def isOpen: Boolean = underlying.isOpen

    override def open(): Unit = underlying.open()

    override def close(): Unit = underlying.close()

    override def read(buf: Array[Byte], off: Int, len: Int): Int =
      throw new UnsupportedOperationException("Cannot read from write transport")

    override def write(buf: Array[Byte], off: Int, len: Int): Unit =
      underlying.write(buf, off, len)

    override def getConfiguration: TConfiguration =
      underlying.getConfiguration

    override def updateKnownMessageSize(size: Long): Unit =
      underlying.updateKnownMessageSize(size)

    override def checkReadBytesAvailable(numBytes: Long): Unit =
      underlying.checkReadBytesAvailable(numBytes)

    /**
     * Returns the accumulated bytes as an array.
     */
    def toByteArray: Array[Byte] =
      underlying.getOutput.toByteArray
  }

  /**
   * Read transport that reads from a byte array. Uses TMemoryTransport
   * internally for Thrift protocol compatibility.
   */
  class Read(input: Array[Byte]) extends TTransport {
    private val underlying = new TMemoryTransport(input)

    override def isOpen: Boolean = underlying.isOpen

    override def open(): Unit = underlying.open()

    override def close(): Unit = underlying.close()

    override def read(buf: Array[Byte], off: Int, len: Int): Int =
      underlying.read(buf, off, len)

    override def write(buf: Array[Byte], off: Int, len: Int): Unit =
      throw new UnsupportedOperationException("Cannot write to read transport")

    override def getConfiguration: TConfiguration =
      underlying.getConfiguration

    override def updateKnownMessageSize(size: Long): Unit =
      underlying.updateKnownMessageSize(size)

    override def checkReadBytesAvailable(numBytes: Long): Unit =
      underlying.checkReadBytesAvailable(numBytes)
  }
}
