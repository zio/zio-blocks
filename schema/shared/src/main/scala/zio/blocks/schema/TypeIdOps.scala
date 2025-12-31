package zio.blocks.schema

import zio.blocks.typeid.TypeId
import zio.blocks.schema.binding.Binding

/**
 * Extension methods for TypeId to support Schema operations.
 * 
 * This keeps TypeId in the typeid module dependency-free (pure data),
 * while providing Schema-specific functionality in the schema module.
 */
object TypeIdOps {
  
  implicit class TypeIdSchemaOps[A](private val typeId: TypeId[A]) extends AnyVal {
    
    /**
     * Create a Schema for a wrapped type where the wrapper may fail.
     * 
     * @param wrap Function to convert from underlying type to wrapped type (may fail)
     * @param unwrap Function to convert from wrapped type to underlying type
     * @tparam B The underlying type (must have a Schema)
     */
    def wrap[B](wrap: B => Either[String, A], unwrap: A => B)(implicit schemaB: Schema[B]): Schema[A] =
      new Schema(
        new Reflect.Wrapper[Binding, A, B](
          schemaB.reflect,
          typeId,
          new Binding.Wrapper(wrap, unwrap)
        )
      )
    
    /**
     * Create a Schema for a wrapped type where the wrapper always succeeds.
     * 
     * @param wrap Function to convert from underlying type to wrapped type
     * @param unwrap Function to convert from wrapped type to underlying type
     * @tparam B The underlying type (must have a Schema)
     */
    def wrapTotal[B](wrap: B => A, unwrap: A => B)(implicit schemaB: Schema[B]): Schema[A] =
      new Schema(
        new Reflect.Wrapper[Binding, A, B](
          schemaB.reflect,
          typeId,
          new Binding.Wrapper(x => new Right(wrap(x)), unwrap)
        )
      )
    
    /**
     * Lookup the PrimitiveType for this TypeId, if it represents a primitive type.
     */
    def primitiveType: Option[PrimitiveType[A]] = {
      val typeName = typeIdToTypeName(typeId)
      typeName.primitiveType
    }
    
    /**
     * Convert TypeId back to TypeName for compatibility during migration.
     * This will be removed once migration is complete.
     */
    private def typeIdToTypeName(id: TypeId[A]): TypeName[A] = {
      val namespace = Namespace(
        id.owner.segments.collect { case zio.blocks.typeid.Owner.Package(name) => name }
      )
      new TypeName(namespace, id.name, Nil)
    }
  }
}
