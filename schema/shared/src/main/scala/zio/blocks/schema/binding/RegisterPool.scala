package zio.blocks.schema.binding

final class RegisterPool private () {
  private[this] var used: Int = 0
  /*
  private[this] var registers: Array[Registers] = Array.fill[Registers](8)(allocate())

  def acquire(): Registers = {
    val idx = this.used
    if (idx == registers.length) registers = java.util.Arrays.copyOf(registers, idx << 1)
    var register = registers(idx)
    if (register eq null) {
      register = allocate()
      registers(idx) = register
    }
    this.used = idx + 1
    register
  }

  def release(): Unit = {
    val idx = this.used - 1
    registers(idx).resetObjects()
    used = idx
  }
   */
  private[this] var weakRef: java.lang.ref.WeakReference[Array[Registers]] =
    new java.lang.ref.WeakReference(Array(allocate()))

  def acquire(): Registers = {
    var idx       = this.used
    var registers = weakRef.get
    if (registers eq null) { // the reference was collected by GC
      idx = 0
      registers = Array(allocate())
      weakRef = new java.lang.ref.WeakReference(registers)
    } else if (idx == registers.length) {
      registers = java.util.Arrays.copyOf(registers, idx << 1)
      registers(idx) = allocate()
      weakRef = new java.lang.ref.WeakReference(registers)
    }
    this.used = idx + 1
    registers(idx)
  }

  def release(): Unit = if (used > 0) used -= 1

  private[this] def allocate(): Registers = Registers(RegisterOffset(bytes = 8, objects = 8))
}

object RegisterPool {
  private[this] val pools = new ThreadLocal[RegisterPool] {
    override def initialValue(): RegisterPool = new RegisterPool()
  }

  def get(): RegisterPool = pools.get()
}
