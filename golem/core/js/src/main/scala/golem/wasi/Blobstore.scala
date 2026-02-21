package golem.wasi

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.typedarray.Uint8Array

/**
 * Scala.js facade for WASI blobstore (`wasi:blobstore`).
 *
 * WIT interfaces:
 * {{{
 *   // wasi:blobstore/types
 *   resource outgoing-value { new-outgoing-value: static func() -> outgoing-value; outgoing-value-write-body: func() -> result<output-stream> }
 *   resource incoming-value { incoming-value-consume-sync: func() -> result<list<u8>, error>; size: func() -> u64 }
 *   record container-metadata { name: container-name, created-at: timestamp }
 *   record object-metadata { name: object-name, container: container-name, created-at: timestamp, size: object-size }
 *   record object-id { container: container-name, object: object-name }
 *
 *   // wasi:blobstore/container
 *   resource container { name, info, get-data, write-data, list-objects, delete-object, has-object, object-info, clear }
 *   resource stream-object-names { read-stream-object-names, skip-stream-object-names }
 *
 *   // wasi:blobstore/blobstore
 *   create-container, get-container, delete-container, container-exists, copy-object, move-object
 * }}}
 */
object Blobstore {

  // --- Native imports ---

  @js.native
  @JSImport("wasi:blobstore/blobstore", JSImport.Namespace)
  private object BlobstoreModule extends js.Object {
    def createContainer(name: String): js.Any       = js.native
    def getContainer(name: String): js.Any          = js.native
    def deleteContainer(name: String): Unit         = js.native
    def containerExists(name: String): Boolean      = js.native
    def copyObject(src: js.Any, dest: js.Any): Unit = js.native
    def moveObject(src: js.Any, dest: js.Any): Unit = js.native
  }

  @js.native
  @JSImport("wasi:blobstore/container", JSImport.Namespace)
  private object ContainerModule extends js.Object

  @js.native
  @JSImport("wasi:blobstore/types", JSImport.Namespace)
  private object TypesModule extends js.Object {
    val OutgoingValue: js.Dynamic = js.native
  }

  // --- Data types ---

  final case class ContainerMetadata(name: String, createdAt: BigInt)
  final case class ObjectMetadata(name: String, container: String, createdAt: BigInt, size: Long)
  final case class ObjectId(container: String, name: String)

  // --- Container resource ---

  final class Container private[Blobstore] (private val underlying: js.Dynamic) {

    def name(): String =
      underlying.name().asInstanceOf[String]

    def info(): ContainerMetadata = {
      val raw = underlying.info().asInstanceOf[js.Dynamic]
      ContainerMetadata(raw.name.asInstanceOf[String], BigInt(raw.createdAt.toString))
    }

    def getData(objectName: String, start: Long, end: Long): Array[Byte] = {
      val iv  = underlying.getData(objectName, start.toDouble, end.toDouble)
      val raw = iv.asInstanceOf[js.Dynamic].incomingValueConsumeSync()
      uint8ArrayToBytes(raw.asInstanceOf[Uint8Array])
    }

    def writeData(objectName: String, data: Array[Byte]): Unit = {
      val ov     = TypesModule.OutgoingValue.newOutgoingValue()
      val stream = ov.asInstanceOf[js.Dynamic].outgoingValueWriteBody()
      val bytes  = bytesToUint8Array(data)
      stream.asInstanceOf[js.Dynamic].blockingWriteAndFlush(bytes)
      underlying.writeData(objectName, ov)
    }

    def listObjects(): List[String] = {
      val stream = underlying.listObjects()
      val result = stream.asInstanceOf[js.Dynamic].readStreamObjectNames(js.BigInt("1000"))
      val arr    = result.asInstanceOf[js.Tuple2[js.Array[String], Boolean]]
      arr._1.toList
    }

    def deleteObject(name: String): Unit =
      underlying.deleteObject(name)

    def deleteObjects(names: List[String]): Unit = {
      val arr = js.Array[String]()
      names.foreach(arr.push(_))
      underlying.deleteObjects(arr)
    }

    def hasObject(name: String): Boolean =
      underlying.hasObject(name).asInstanceOf[Boolean]

    def objectInfo(name: String): ObjectMetadata = {
      val raw = underlying.objectInfo(name).asInstanceOf[js.Dynamic]
      ObjectMetadata(
        name = raw.name.asInstanceOf[String],
        container = raw.container.asInstanceOf[String],
        createdAt = BigInt(raw.createdAt.toString),
        size = raw.size.asInstanceOf[Double].toLong
      )
    }

    def clear(): Unit =
      underlying.clear()
  }

  // --- Top-level functions ---

  def createContainer(name: String): Container =
    new Container(BlobstoreModule.createContainer(name).asInstanceOf[js.Dynamic])

  def getContainer(name: String): Container =
    new Container(BlobstoreModule.getContainer(name).asInstanceOf[js.Dynamic])

  def deleteContainer(name: String): Unit =
    BlobstoreModule.deleteContainer(name)

  def containerExists(name: String): Boolean =
    BlobstoreModule.containerExists(name)

  def copyObject(src: ObjectId, dest: ObjectId): Unit =
    BlobstoreModule.copyObject(objectIdToDynamic(src), objectIdToDynamic(dest))

  def moveObject(src: ObjectId, dest: ObjectId): Unit =
    BlobstoreModule.moveObject(objectIdToDynamic(src), objectIdToDynamic(dest))

  // --- Helpers ---

  private def objectIdToDynamic(oid: ObjectId): js.Dynamic =
    js.Dynamic.literal(container = oid.container, `object` = oid.name)

  private def uint8ArrayToBytes(arr: Uint8Array): Array[Byte] = {
    val bytes = new Array[Byte](arr.length)
    var i     = 0
    while (i < arr.length) { bytes(i) = arr(i).toByte; i += 1 }
    bytes
  }

  private def bytesToUint8Array(data: Array[Byte]): Uint8Array = {
    val jsArr = js.Array[Short]()
    data.foreach(b => jsArr.push((b.toInt & 0xff).toShort))
    new Uint8Array(jsArr.asInstanceOf[js.Iterable[Short]])
  }

  // --- Raw access (for forward compatibility) ---

  def blobstoreRaw: Any = BlobstoreModule
  def containerRaw: Any = ContainerModule
  def typesRaw: Any     = TypesModule
}
