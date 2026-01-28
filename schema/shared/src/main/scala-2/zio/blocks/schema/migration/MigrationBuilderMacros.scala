package zio.blocks.schema.migration
import scala.reflect.macros.whitebox
object MigrationBuilderMacros {
  def addFieldImpl[A, B, T](c: whitebox.Context)(name: c.Tree, default: c.Tree): c.Tree = {
    import c.universe._; q"${c.prefix}.addFieldInternal($name, $default)"
  }
  def dropFieldImpl[A, B, T](c: whitebox.Context)(name: c.Tree): c.Tree = {
    import c.universe._; q"${c.prefix}.dropFieldInternal($name)"
  }
  def dropFieldWithDefaultImpl[A, B, T](c: whitebox.Context)(name: c.Tree, defaultForReverse: c.Tree): c.Tree = {
    import c.universe._; q"${c.prefix}.dropFieldInternal($name, $defaultForReverse)"
  }
  def renameFieldImpl[A, B, T1, T2](c: whitebox.Context)(from: c.Tree, to: c.Tree): c.Tree = {
    import c.universe._; q"${c.prefix}.renameFieldInternal($from, $to)"
  }
  def transformFieldImpl[A, B, T](c: whitebox.Context)(path: c.Tree, transform: c.Tree): c.Tree = {
    import c.universe._; q"${c.prefix}.transformFieldInternal($path, $transform)"
  }
  def mandateFieldImpl[A, B, T](c: whitebox.Context)(path: c.Tree, default: c.Tree): c.Tree = {
    import c.universe._; q"${c.prefix}.mandateFieldInternal($path, $default)"
  }
  def optionalizeFieldImpl[A, B, T](c: whitebox.Context)(path: c.Tree): c.Tree = {
    import c.universe._; q"${c.prefix}.optionalizeFieldInternal($path)"
  }
  def changeFieldTypeImpl[A, B, T1, T2](c: whitebox.Context)(path: c.Tree, converter: c.Tree): c.Tree = {
    import c.universe._; q"${c.prefix}.changeFieldTypeInternal($path, $converter)"
  }
  def transformElementsImpl[A, B, T](c: whitebox.Context)(path: c.Tree, transform: c.Tree): c.Tree = {
    import c.universe._; q"${c.prefix}.transformElementsInternal($path, $transform)"
  }
  def transformKeysImpl[A, B, K, V](c: whitebox.Context)(path: c.Tree, transform: c.Tree): c.Tree = {
    import c.universe._; q"${c.prefix}.transformKeysInternal($path, $transform)"
  }
  def transformValuesImpl[A, B, K, V](c: whitebox.Context)(path: c.Tree, transform: c.Tree): c.Tree = {
    import c.universe._; q"${c.prefix}.transformValuesInternal($path, $transform)"
  }
  def buildImpl[A, B](c: whitebox.Context): c.Tree        = { import c.universe._; q"${c.prefix}.buildInternal" }
  def buildPartialImpl[A, B](c: whitebox.Context): c.Tree = { import c.universe._; q"${c.prefix}.buildPartialInternal" }
}
