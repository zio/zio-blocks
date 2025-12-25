package zio.blocks.schema

/**
 * A Patch is a sequence of operations that can be applied to a value to produce
 * a new value. Patches are serializable and can be computed via diffing.
 *
 * {{{
 * val patch1 = Patch.set(Person.name, "John")
 * val patch2 = Patch.set(Person.age, 30)
 *
 * val patch3 = patch1 ++ patch2
 *
 * patch3(Person("Jane", 25)) // Person("John", 30)
 * }}}
 *
 * Patches can also be created by diffing two values:
 * {{{
 * val person1 = Person("Jane", 25)
 * val person2 = Person("John", 30)
 * val patch = Schema[Person].diff(person1, person2)
 * patch(person1) // Person("John", 30)
 * }}}
 */
final case class Patch[S](
  dynamicPatch: DynamicPatch,
  source: Schema[S]
) {

  /** Compose patches sequentially (monoid operation) */
  def ++(that: Patch[S]): Patch[S] =
    Patch(this.dynamicPatch ++ that.dynamicPatch, this.source)

  /**
   * Apply patch with default Strict mode.
   * On error, returns the original value unchanged.
   */
  def apply(s: S): S = applyOrFail(s).getOrElse(s)

  /**
   * Apply patch, returning None on failure.
   */
  def applyOption(s: S): Option[S] =
    applyOrFail(s).toOption

  /**
   * Apply patch with Strict mode, returning Either[SchemaError, S].
   */
  def applyOrFail(s: S): Either[SchemaError, S] =
    applyWithMode(s, DynamicPatch.PatchMode.Strict)

  /**
   * Apply this patch with explicit control over the patch mode.
   * Returns Left(SchemaError) on failure, Right(result) on success.
   */
  def applyWithMode(s: S, mode: DynamicPatch.PatchMode): Either[SchemaError, S] = {
    val dv = source.toDynamicValue(s)
    dynamicPatch.apply(dv, mode).flatMap(newDv => source.fromDynamicValue(newDv))
  }

  /**
   * Check if this patch has any operations.
   */
  def isEmpty: Boolean = dynamicPatch.ops.isEmpty

  /**
   * Check if this patch has operations.
   */
  def nonEmpty: Boolean = !isEmpty

  /**
   * Get the underlying dynamic patch.
   */
  def toDynamicPatch: DynamicPatch = dynamicPatch
}

object Patch {
  /**
   * Create a Patch from a DynamicPatch.
   */
  def apply[S](dynamicPatch: DynamicPatch, schema: Schema[S]): Patch[S] =
    new Patch(dynamicPatch, schema)

  /**
   * Create an empty patch that does nothing.
   */
  def empty[S](implicit schema: Schema[S]): Patch[S] =
    new Patch(DynamicPatch.empty, schema)

  // ============================================================
  // Type-safe constructors using Lens
  // ============================================================

  /**
   * Set a field to a value using a Lens.
   * The value is automatically converted to DynamicValue via its Schema.
   */
  def set[S, A](lens: Lens[S, A], value: A)(implicit
    sourceSchema: Schema[S],
    valueSchema: Schema[A]
  ): Patch[S] = {
    val optic = lens.toDynamic
    val dynamicValue = valueSchema.toDynamicValue(value)
    Patch(DynamicPatch.setAt(optic, dynamicValue), sourceSchema)
  }

  /**
   * Set a value through an Optional optic.
   */
  def set[S, A](optional: Optional[S, A], value: A)(implicit
    sourceSchema: Schema[S],
    valueSchema: Schema[A]
  ): Patch[S] = {
    val optic = optional.toDynamic
    val dynamicValue = valueSchema.toDynamicValue(value)
    Patch(DynamicPatch.setAt(optic, dynamicValue), sourceSchema)
  }

  /**
   * Set a value through a Traversal (applies to all targets).
   */
  def set[S, A](traversal: Traversal[S, A], value: A)(implicit
    sourceSchema: Schema[S],
    valueSchema: Schema[A]
  ): Patch[S] = {
    val optic = traversal.toDynamic
    val dynamicValue = valueSchema.toDynamicValue(value)
    Patch(DynamicPatch.setAt(optic, dynamicValue), sourceSchema)
  }

  /**
   * Set a case value through a Prism.
   */
  def set[S, A <: S](prism: Prism[S, A], value: A)(implicit
    sourceSchema: Schema[S],
    valueSchema: Schema[A]
  ): Patch[S] = {
    val optic = prism.toDynamic
    val dynamicValue = valueSchema.toDynamicValue(value)
    Patch(DynamicPatch.setAt(optic, dynamicValue), sourceSchema)
  }

  // ============================================================
  // Delta operations for numeric types
  // ============================================================

  /**
   * Add an integer delta to a field.
   */
  def addInt[S](lens: Lens[S, Int], delta: Int)(implicit schema: Schema[S]): Patch[S] =
    Patch(DynamicPatch.intDeltaAt(lens.toDynamic, delta), schema)

  /**
   * Add a long delta to a field.
   */
  def addLong[S](lens: Lens[S, Long], delta: Long)(implicit schema: Schema[S]): Patch[S] =
    Patch(DynamicPatch.longDeltaAt(lens.toDynamic, delta), schema)

  /**
   * Add a double delta to a field.
   */
  def addDouble[S](lens: Lens[S, Double], delta: Double)(implicit schema: Schema[S]): Patch[S] =
    Patch(DynamicPatch.doubleDeltaAt(lens.toDynamic, delta), schema)

  // ============================================================
  // Deprecated compatibility layer
  // ============================================================

  /**
   * Replace a field value using a Lens.
   * @deprecated Use Patch.set instead
   */
  @deprecated("Use Patch.set instead of Patch.replace", "2.0.0")
  def replace[S, A](lens: Lens[S, A], value: A)(implicit
    sourceSchema: Schema[S],
    valueSchema: Schema[A]
  ): Patch[S] = set(lens, value)

  /**
   * Replace a value through an Optional optic.
   * @deprecated Use Patch.set instead
   */
  @deprecated("Use Patch.set instead of Patch.replace", "2.0.0")
  def replace[S, A](optional: Optional[S, A], value: A)(implicit
    sourceSchema: Schema[S],
    valueSchema: Schema[A]
  ): Patch[S] = set(optional, value)

  /**
   * Replace a value through a Traversal.
   * @deprecated Use Patch.set instead
   */
  @deprecated("Use Patch.set instead of Patch.replace", "2.0.0")
  def replace[S, A](traversal: Traversal[S, A], value: A)(implicit
    sourceSchema: Schema[S],
    valueSchema: Schema[A]
  ): Patch[S] = set(traversal, value)

  /**
   * Replace a case value through a Prism.
   * @deprecated Use Patch.set instead
   */
  @deprecated("Use Patch.set instead of Patch.replace", "2.0.0")
  def replace[S, A <: S](prism: Prism[S, A], value: A)(implicit
    sourceSchema: Schema[S],
    valueSchema: Schema[A]
  ): Patch[S] = set(prism, value)
}
