package zio.blocks.schema

import zio.blocks.typeid._
import scala.collection.mutable

/**
 * Compatibility layer between TypeId and TypeName during migration.
 * This allows gradual refactoring while keeping tests working.
 */
object TypeIdCompat {

  // Bidirectional cache: TypeName <-> TypeId
  // Using TypeName as key avoids collisions (Vector[Int] vs Vector[BigInt])
  private val typeNameToTypeId = new mutable.HashMap[TypeName[_], TypeId[_]]()
  // Use java.util.IdentityHashMap for reference-based equality on TypeId
  // This allows multiple structurally identical TypeIds (e.g., IArray[Int] and IArray[StructureId])
  // to map to different TypeNames based on object identity (reference equality, not structural equality)
  private val typeIdToTypeNameIdentity = new java.util.IdentityHashMap[TypeId[_], TypeName[_]]()

  /**
   * Convert a TypeId to a TypeName for backwards compatibility.
   */
  def toTypeName[A](typeId: TypeId[A]): TypeName[A] = {
    // Check identity-based cache first (uses reference equality, not structural equality)
    typeIdToTypeNameIdentity.synchronized {
      val cached = typeIdToTypeNameIdentity.get(typeId)
      if (cached != null) {
        return cached.asInstanceOf[TypeName[A]]
      }
    }

    // If not in cache, create a new TypeName with placeholder params
    // This should rarely happen since fromTypeName populates the cache
    val namespace = ownerToNamespace(typeId.owner)
    val params = typeId.typeParams.map(tp => TypeName.string) // Placeholder
    TypeName(namespace, typeId.name, params)
  }

  /**
   * Convert Owner to Namespace.
   */
  private def ownerToNamespace(owner: Owner): Namespace = {
    val (packages, values) = owner.segments.foldLeft((List.empty[String], List.empty[String])) {
      case ((pkgs, vals), seg) => seg match {
        case Owner.Segment.Package(name) => (pkgs :+ name, vals)
        case Owner.Segment.Term(name) => (pkgs, vals :+ name)
        case Owner.Segment.Type(name) => (pkgs, vals :+ name)
      }
    }
    Namespace(packages, values)
  }

  /**
   * Create a TypeId from a TypeName (for testing compatibility).
   * Recursively processes nested TypeName parameters to preserve full type information.
   */
  def fromTypeName[A](typeName: TypeName[A]): TypeId[A] = {
    // Check if we've already converted this TypeName
    typeNameToTypeId.synchronized {
      typeNameToTypeId.get(typeName) match {
        case Some(cached) =>
          return cached.asInstanceOf[TypeId[A]]
        case None =>
          // Continue with conversion
      }
    }

    val owner = namespaceToOwner(typeName.namespace)

    // Recursively convert nested TypeName parameters to TypeIds
    // This ensures the cache is populated for all nested types
    typeName.params.foreach { param =>
      fromTypeName(param)
    }

    val typeParams = typeName.params.zipWithIndex.map { case (_, idx) =>
      TypeParam(s"_$idx", idx)
    }.toList
    val typeId: TypeId[A] = TypeId.nominal[A](typeName.name, owner, typeParams)

    // Cache bidirectionally so we can reconstruct later
    // Store TypeName -> TypeId mapping (structural equality)
    typeNameToTypeId.synchronized {
      typeNameToTypeId.put(typeName, typeId)
    }
    // Store TypeId -> TypeName mapping using IDENTITY (reference equality)
    // This allows multiple structurally identical TypeIds to map to different TypeNames
    typeIdToTypeNameIdentity.synchronized {
      typeIdToTypeNameIdentity.put(typeId, typeName)
    }
    typeId
  }

  /**
   * Convert Namespace to Owner.
   */
  private def namespaceToOwner(namespace: Namespace): Owner = {
    val packageSegments = namespace.packages.map(Owner.Segment.Package(_))
    val valueSegments = namespace.values.map(Owner.Segment.Term(_))
    Owner((packageSegments ++ valueSegments).toList)
  }
}
