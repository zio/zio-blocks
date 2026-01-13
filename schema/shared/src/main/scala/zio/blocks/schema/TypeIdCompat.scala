package zio.blocks.schema

import zio.blocks.typeid._

/**
 * Compatibility layer between TypeId and TypeName during migration.
 * This allows gradual refactoring while keeping tests working.
 */
object TypeIdCompat {

  /**
   * Convert a TypeId to a TypeName for backwards compatibility.
   */
  def toTypeName[A](typeId: TypeId[A]): TypeName[A] = {
    val namespace = ownerToNamespace(typeId.owner)
    val params = typeId.typeParams.map(_ => TypeName.string) // Simplified for now
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
   */
  def fromTypeName[A](typeName: TypeName[A]): TypeId[A] = {
    val owner = namespaceToOwner(typeName.namespace)
    TypeId.nominal(typeName.name, owner, Nil)
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
