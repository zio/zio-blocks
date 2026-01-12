package zio.blocks.schema.migration

object DynamicMigrationLawsSpec {

  def testIdentity(): Unit = {
    val p = DynamicMigration.id
    assert(p.actions.isEmpty)
    assert((p ++ p).actions.isEmpty)
  }

  def testAssociativity(): Unit = {
    val a = DynamicMigration(MigrationAction.RenameField(zio.blocks.schema.DynamicOptic.root, "x", "y"))
    val b = DynamicMigration(MigrationAction.DropField(zio.blocks.schema.DynamicOptic.root, "z", DefaultValueExpr))
    val c = DynamicMigration(MigrationAction.AddField(zio.blocks.schema.DynamicOptic.root, "k", DefaultValueExpr))

    val left  = (a ++ b) ++ c
    val right = a ++ (b ++ c)
    assert(left.actions == right.actions)
  }

  def testReverseInvolution(): Unit = {
    val p = DynamicMigration(
      MigrationAction.RenameField(zio.blocks.schema.DynamicOptic.root, "a", "b"),
      MigrationAction.DropField(zio.blocks.schema.DynamicOptic.root, "x", DefaultValueExpr)
    )
    assert(p.reverse.reverse.actions == p.actions)
  }
}
