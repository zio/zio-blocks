package zio.blocks.schema.binding

final class RegisterPool private () {
  private[this] var registers: Array[Registers] = Array.fill[Registers](8)(Registers(RegisterOffset.Zero))
  private[this] var used: Int                   = 0

  def size: Int = used

  def allocate(): Registers = {
    val idx = this.used
    if (idx + 1 > registers.length) registers = java.util.Arrays.copyOf(registers, registers.length << 1)
    var register = registers(idx)
    if (register eq null) {
      register = Registers(RegisterOffset.Zero)
      registers(idx) = register
    }
    this.used = idx + 1
    register
  }

  def releaseLast(): Unit = used -= 1
}

object RegisterPool {
  private[this] val pools = new ThreadLocal[RegisterPool] {
    override def initialValue(): RegisterPool = new RegisterPool()
  }

  def get(): RegisterPool = pools.get()
}
