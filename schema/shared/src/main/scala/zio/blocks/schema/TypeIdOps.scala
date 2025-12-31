package zio.blocks.schema

import zio.blocks.typeid.TypeId
import zio.blocks.schema.binding.Binding

/**
 * Extension methods for TypeId to support Schema operations.
 * 
 * This keeps TypeId in the typeid module dependency-free (pure data),
 * while providing Schema-specific functionality in the schema module.
 * 
 * During the migration period, TypeId is converted to TypeName for use
 * with Reflect.Wrapper. After full migration, this conversion will be removed.
 */
object TypeIdOps {
  
  implicit class TypeIdSchemaOps[A](private val typeId: TypeId[A]) extends AnyVal {
    
    /**
     * Convert TypeId to TypeName for compatibility during migration.
     */
    def toTypeName: TypeName[A] = {
      val packages = typeId.owner.segments.collect { 
        case zio.blocks.typeid.Owner.Package(name) => name 
      }
      val values = typeId.owner.segments.collect { 
        case zio.blocks.typeid.Owner.Term(name) => name
        case zio.blocks.typeid.Owner.Type(name) => name
      }
      new TypeName[A](new Namespace(packages, values), typeId.name, Nil)
    }
    
    /**
     * Create a Schema for a wrapped type where the wrapper may fail.
     * 
     * @param wrapFn Function to convert from underlying type to wrapped type (may fail)
     * @param unwrapFn Function to convert from wrapped type to underlying type
     * @tparam B The underlying type (must have a Schema)
     */
    def wrap[B](wrapFn: B => Either[String, A], unwrapFn: A => B)(implicit schemaB: Schema[B]): Schema[A] = {
      val typeName = toTypeName
      val primitiveTypeOpt = Reflect.unwrapToPrimitiveTypeOption(schemaB.reflect).asInstanceOf[Option[PrimitiveType[A]]]
      new Schema(
        new Reflect.Wrapper[Binding, A, B](
          schemaB.reflect,
          typeName,
          primitiveTypeOpt,
          new Binding.Wrapper[A, B](wrapFn, unwrapFn)
        )
      )
    }
    
    /**
     * Create a Schema for a wrapped type where the wrapper always succeeds.
     * 
     * @param wrapFn Function to convert from underlying type to wrapped type
     * @param unwrapFn Function to convert from wrapped type to underlying type
     * @tparam B The underlying type (must have a Schema)
     */
    def wrapTotal[B](wrapFn: B => A, unwrapFn: A => B)(implicit schemaB: Schema[B]): Schema[A] = {
      val typeName = toTypeName
      val primitiveTypeOpt = Reflect.unwrapToPrimitiveTypeOption(schemaB.reflect).asInstanceOf[Option[PrimitiveType[A]]]
      new Schema(
        new Reflect.Wrapper[Binding, A, B](
          schemaB.reflect,
          typeName,
          primitiveTypeOpt,
          new Binding.Wrapper[A, B]((x: B) => Right(wrapFn(x)), unwrapFn)
        )
      )
    }
  }
}
