package golem.wasi

import org.scalatest.funsuite.AnyFunSuite

class BlobstoreCompileSpec extends AnyFunSuite {
  import Blobstore._

  private val containerMeta = ContainerMetadata("test-container", BigInt(1700000000L))
  private val objectMeta    = ObjectMetadata("file.txt", "test-container", BigInt(1700000000L), 1024L)
  private val objectId1     = ObjectId("container1", "object1")
  private val objectId2     = ObjectId("container2", "object2")

  test("ContainerMetadata construction and field access") {
    assert(containerMeta.name == "test-container")
    assert(containerMeta.createdAt == BigInt(1700000000L))
  }

  test("ObjectMetadata construction and field access") {
    assert(objectMeta.name == "file.txt")
    assert(objectMeta.container == "test-container")
    assert(objectMeta.createdAt == BigInt(1700000000L))
    assert(objectMeta.size == 1024L)
  }

  test("ObjectId construction and field access") {
    assert(objectId1.container == "container1")
    assert(objectId1.name == "object1")
    assert(objectId2.container == "container2")
  }

  test("Container method types compile") {
    type NameType          = () => String
    type InfoType          = () => ContainerMetadata
    type GetDataType       = (String, Long, Long) => Array[Byte]
    type WriteDataType     = (String, Array[Byte]) => Unit
    type ListObjectsType   = () => List[String]
    type DeleteObjectType  = String => Unit
    type DeleteObjectsType = List[String] => Unit
    type HasObjectType     = String => Boolean
    type ObjectInfoType    = String => ObjectMetadata
    type ClearType         = () => Unit
    assert(true)
  }

  test("top-level function return types compile") {
    val _: Container = null.asInstanceOf[Container]
    val _: Boolean   = true
    assert(true)
  }
}
