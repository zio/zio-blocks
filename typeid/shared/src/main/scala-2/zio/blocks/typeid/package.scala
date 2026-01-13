package zio.blocks

package object typeid {
  /**
   * Represents any kind of type, including proper types and type constructors.
   * In Scala 2, we use existential types to represent this.
   */
  type AnyKind = Any
}
