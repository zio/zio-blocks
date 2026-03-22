package golem.wasi

import zio.test._

object BlobstoreCompileSpec extends ZIOSpecDefault {
  import Blobstore._

  private val containerMeta = ContainerMetadata("test-container", BigInt(1700000000L))
  private val objectMeta    = ObjectMetadata("file.txt", "test-container", BigInt(1700000000L), 1024L)
  private val objectId1     = ObjectId("container1", "object1")
  private val objectId2     = ObjectId("container2", "object2")

  def spec = suite("BlobstoreCompileSpec")(
    test("ContainerMetadata construction and field access") {
      assertTrue(
        containerMeta.name == "test-container",
        containerMeta.createdAt == BigInt(1700000000L)
      )
    },
    test("ObjectMetadata construction and field access") {
      assertTrue(
        objectMeta.name == "file.txt",
        objectMeta.container == "test-container",
        objectMeta.createdAt == BigInt(1700000000L),
        objectMeta.size == 1024L
      )
    },
    test("ObjectId construction and field access") {
      assertTrue(
        objectId1.container == "container1",
        objectId1.name == "object1",
        objectId2.container == "container2"
      )
    }
  )
}
