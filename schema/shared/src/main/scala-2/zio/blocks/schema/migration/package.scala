package zio.blocks.schema

package object migration extends MigrationSelectorSyntax {
  type &[+A, +B] = A with B
}
