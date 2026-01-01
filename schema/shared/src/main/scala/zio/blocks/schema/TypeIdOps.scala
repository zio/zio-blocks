package zio.blocks.schema

import zio.blocks.typeid.{TypeId, Owner}
import zio.blocks.schema.binding.Binding

/**
 * Extension methods for TypeId and TypeName to support Schema operations.
 * 
 * This keeps TypeId in the typeid module dependency-free (pure data),
 * while providing Schema-specific functionality in the schema module.
 * 
 * During the migration period, TypeId and TypeName can be converted between
 * each other. After full migration, TypeName will be deprecated.
 */
object TypeIdOps {
  
  /**
   * Implicit conversion from TypeName to TypeId.
   * This allows TypeName values to be used where TypeId is expected during migration.
   */
  implicit def typeNameToTypeId[A](typeName: TypeName[A]): TypeId[A] = {
    val packages = typeName.namespace.packages.map(Owner.Package(_)).toList
    val terms = typeName.namespace.values.map(Owner.Term(_)).toList
    val owner = Owner(packages ++ terms)
    val base = TypeId.nominal[A](typeName.name, owner)
    
    if (typeName.params.isEmpty) base
    else {
      val args = typeName.params.map(p => typeNameToTypeId(p)).toList
      TypeId.applied[A](base, args)
    }
  }
  
  /**
   * Extension methods to convert TypeName to TypeId.
   * Used during migration to support macro-generated code that still uses TypeName.
   */
  implicit class TypeNameOps[A](private val typeName: TypeName[A]) extends AnyVal {
    
    /**
     * Convert TypeName to TypeId for migration compatibility.
     */
    def toTypeId: TypeId[A] = typeNameToTypeId(typeName)
  }
  
  
  implicit class TypeIdSchemaOps[A](private val typeId: TypeId[A]) extends AnyVal {
    
    /**
     * Convert TypeId to TypeName for compatibility during migration.
     */
    def toTypeName: TypeName[A] = typeId match {
      case TypeId.Applied(ctor, args) =>
        val base = ctor.toTypeName
        new TypeName[A](base.namespace, base.name, args.map(_.toTypeName))
        
      case _ =>
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
      val primitiveTypeOpt = Reflect.unwrapToPrimitiveTypeOption(schemaB.reflect).asInstanceOf[Option[PrimitiveType[A]]]
      new Schema(
        new Reflect.Wrapper[Binding, A, B](
          schemaB.reflect,
          typeId,
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
      val primitiveTypeOpt = Reflect.unwrapToPrimitiveTypeOption(schemaB.reflect).asInstanceOf[Option[PrimitiveType[A]]]
      new Schema(
        new Reflect.Wrapper[Binding, A, B](
          schemaB.reflect,
          typeId,
          primitiveTypeOpt,
          new Binding.Wrapper[A, B]((x: B) => Right(wrapFn(x)), unwrapFn)
        )
      )
    }
  }
}
