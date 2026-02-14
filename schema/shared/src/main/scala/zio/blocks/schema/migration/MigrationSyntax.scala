package zio.blocks.schema.migration

object MigrationSyntax {

  implicit class CollectionOps[A](private val self: Iterable[A]) extends AnyVal {

    /**
     * Traverses into elements of a collection. Usage: _.items.each.price *
     * NOTE: This method is intercepted by the migration macro at compile time.
     * If you see this error at runtime, it means the macro failed to apply.
     */
    def each: A =
      throw new RuntimeException(
        "Compiler Error: The migration macro failed to intercept '.each'. This code should not exist at runtime."
      )
  }

  implicit class EnumOps[A](private val self: A) extends AnyVal {

    /**
     * Prisms into a specific case of an enum/sealed trait. Usage:
     * _.paymentMethod.when[CreditCard] * NOTE: This method is intercepted by
     * the migration macro at compile time.
     */
    def when[Subtype <: A]: Subtype =
      throw new RuntimeException(
        "Compiler Error: The migration macro failed to intercept '.when'. This code should not exist at runtime."
      )
  }
}
