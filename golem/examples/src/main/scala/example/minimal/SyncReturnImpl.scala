package example.minimal

import golem.runtime.annotations.agentImplementation

@agentImplementation()
final class SyncReturnImpl() extends SyncReturnAgent {
  private var last: String = ""

  override def greet(name: String): String = {
    val msg = s"hello, $name"
    last = s"greet:$name"
    msg
  }

  override def add(a: Int, b: Int): Int = a + b

  override def touch(tag: String): Unit =
    last = tag

  override def lastTag(): String = last
}
