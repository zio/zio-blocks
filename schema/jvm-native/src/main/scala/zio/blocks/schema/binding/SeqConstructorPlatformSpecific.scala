package zio.blocks.schema.binding

trait SeqConstructorPlatformSpecific {
  def classForName(fqcn: String): Class[_] = Class.forName(fqcn)
}
