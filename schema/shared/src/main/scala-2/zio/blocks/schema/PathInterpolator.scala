package zio.blocks.schema

import scala.language.experimental.macros

trait PathInterpolator {
  
  implicit class PathInterpolatorOps(val sc: StringContext) {
    def p(args: Any*): DynamicOptic = macro PathMacros.pImpl
  }
}
