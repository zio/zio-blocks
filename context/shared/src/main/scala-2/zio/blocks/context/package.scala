package zio.blocks

package object context {
  type &[+A, +B] = A with B
}
