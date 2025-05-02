package zio.blocks.schema.binding

import zio.blocks.schema.binding.RegisterOffset.RegisterOffset

final class RegisterPool private () {
  private var registers: Array[Registers] = Array.fill[Registers](8)(Registers(RegisterOffset.Zero))
  private var used: Int                   = 0

  def size: Int = used

  def allocate(): Registers = {
    ensureCapacity(1)

    if (registers(used) == null) {
      registers(used) = Registers(RegisterOffset.Zero)
    }

    val register = registers(used)
    used += 1
    register
  }

  def releaseLast(): Unit =
    used -= 1

  private def ensureCapacity(requested: Int): Unit =
    if (used + requested > registers.length) {
      val newRegisters = Array.ofDim[Registers](registers.length * 2)
      System.arraycopy(registers, 0, newRegisters, 0, used)
      registers = newRegisters
    }
}
object RegisterPool {
  private val _threadLocal = new ThreadLocal[RegisterPool] {
    override def initialValue(): RegisterPool = new RegisterPool()
  }

  def get(): RegisterPool = _threadLocal.get()
}
