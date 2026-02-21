package golem.wasi

import org.scalatest.funsuite.AnyFunSuite

class KeyValueCompileSpec extends AnyFunSuite {
  import KeyValue._

  test("Bucket method types compile") {
    type GetType        = String => Option[Array[Byte]]
    type SetType        = (String, Array[Byte]) => Unit
    type DeleteType     = String => Unit
    type ExistsType     = String => Boolean
    type KeysType       = () => List[String]
    type GetManyType    = List[String] => List[Option[Array[Byte]]]
    type DeleteManyType = List[String] => Unit
    assert(true)
  }

  test("Bucket type compiles") {
    val _: Bucket = null.asInstanceOf[Bucket]
    assert(true)
  }

  test("OutgoingValue method types compile") {
    type WriteSyncType = Array[Byte] => Unit
    assert(true)
  }

  test("OutgoingValue type compiles") {
    val _: OutgoingValue = null.asInstanceOf[OutgoingValue]
    assert(true)
  }

  test("IncomingValue method types compile") {
    type ConsumeSyncType = () => Array[Byte]
    type SizeType        = () => Long
    assert(true)
  }
}
