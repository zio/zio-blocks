package zio.blocks.codegen.ir

/**
 * Represents a complete Scala source file in the IR.
 *
 * A Scala file consists of a package declaration, optional imports, and type
 * definitions. This is the top-level IR node for code generation.
 *
 * @param packageDecl
 *   The package declaration for this file
 * @param imports
 *   The import statements (defaults to empty list)
 * @param types
 *   The type definitions in this file (defaults to empty list)
 *
 * @example
 *   {{{
 * val file = ScalaFile(
 *   PackageDecl("com.example"),
 *   imports = List(
 *     Import.SingleImport("scala.collection", "List"),
 *     Import.WildcardImport("zio")
 *   ),
 *   types = List(
 *     CaseClass("Person", List(Field("name", TypeRef.String)))
 *   )
 * )
 *   }}}
 */
final case class ScalaFile(
  packageDecl: PackageDecl,
  imports: List[Import] = Nil,
  types: List[TypeDefinition] = Nil
)
