package zio.blocks.scope

import scala.language.experimental.macros

private[scope] trait WireableVersionSpecific {

  def from[T]: Wireable[T] = macro WireableMacros.fromImpl[T]
}

private[scope] object WireableMacros {
  import scala.reflect.macros.blackbox

  def fromImpl[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[Wireable[T]] = {
    import c.universe._

    val tpe = weakTypeOf[T]
    val sym = tpe.typeSymbol

    if (!sym.isClass) {
      c.abort(c.enclosingPosition, s"${tpe.toString} is not a class")
    }

    val ctor = tpe.decls.collectFirst {
      case m: MethodSymbol if m.isPrimaryConstructor => m
    }.getOrElse(c.abort(c.enclosingPosition, s"${tpe.toString} has no primary constructor"))

    val paramLists = ctor.paramLists
    val (regularParams, implicitParams) = paramLists.partition { params =>
      params.headOption.forall(!_.isImplicit)
    }

    val allRegularParams = regularParams.flatten
    val hasScopeParam = implicitParams.flatten.exists { param =>
      param.typeSignature <:< typeOf[Scope.Any]
    }
    val isAutoCloseable = tpe <:< typeOf[AutoCloseable]
    val depTypes = allRegularParams.map(_.typeSignature)

    depTypes match {
      case Nil =>
        generateWireable0[T](c)(hasScopeParam, isAutoCloseable)
      case List(dep1Tpe) =>
        generateWireable1[T](c)(dep1Tpe, hasScopeParam, isAutoCloseable)
      case List(dep1Tpe, dep2Tpe) =>
        generateWireable2[T](c)(dep1Tpe, dep2Tpe, hasScopeParam, isAutoCloseable)
      case _ =>
        c.abort(c.enclosingPosition, s"Wireable.from supports up to 2 dependencies, got ${depTypes.length}")
    }
  }

  private def generateWireable0[T: c.WeakTypeTag](c: blackbox.Context)(
    hasScopeParam: Boolean,
    isAutoCloseable: Boolean
  ): c.Expr[Wireable[T]] = {
    import c.universe._

    val tpe = weakTypeOf[T]

    val wireBody = if (hasScopeParam) {
      q"""
        _root_.zio.blocks.scope.Wire.Shared.fromFunction[Any, $tpe] { scope =>
          val instance = new $tpe()(scope)
          _root_.zio.blocks.context.Context[$tpe](instance)
        }
      """
    } else if (isAutoCloseable) {
      q"""
        _root_.zio.blocks.scope.Wire.Shared.fromFunction[Any, $tpe] { scope =>
          val instance = new $tpe()
          scope.defer(instance.asInstanceOf[AutoCloseable].close())
          _root_.zio.blocks.context.Context[$tpe](instance)
        }
      """
    } else {
      q"""
        _root_.zio.blocks.scope.Wire.Shared.fromFunction[Any, $tpe] { _ =>
          val instance = new $tpe()
          _root_.zio.blocks.context.Context[$tpe](instance)
        }
      """
    }

    val result = q"""
      new _root_.zio.blocks.scope.Wireable[$tpe] {
        type In = Any
        def wire: _root_.zio.blocks.scope.Wire[Any, $tpe] = $wireBody
      }
    """
    c.Expr[Wireable[T]](result)
  }

  private def generateWireable1[T: c.WeakTypeTag](c: blackbox.Context)(
    dep1Tpe: c.Type,
    hasScopeParam: Boolean,
    isAutoCloseable: Boolean
  ): c.Expr[Wireable[T]] = {
    import c.universe._

    val tpe = weakTypeOf[T]

    val wireBody = if (hasScopeParam) {
      q"""
        _root_.zio.blocks.scope.Wire.Shared.fromFunction[$dep1Tpe, $tpe] { scope =>
          val arg1 = scope.get[$dep1Tpe]
          val instance = new $tpe(arg1)(scope)
          _root_.zio.blocks.context.Context[$tpe](instance)
        }
      """
    } else if (isAutoCloseable) {
      q"""
        _root_.zio.blocks.scope.Wire.Shared.fromFunction[$dep1Tpe, $tpe] { scope =>
          val arg1 = scope.get[$dep1Tpe]
          val instance = new $tpe(arg1)
          scope.defer(instance.asInstanceOf[AutoCloseable].close())
          _root_.zio.blocks.context.Context[$tpe](instance)
        }
      """
    } else {
      q"""
        _root_.zio.blocks.scope.Wire.Shared.fromFunction[$dep1Tpe, $tpe] { scope =>
          val arg1 = scope.get[$dep1Tpe]
          val instance = new $tpe(arg1)
          _root_.zio.blocks.context.Context[$tpe](instance)
        }
      """
    }

    val result = q"""
      new _root_.zio.blocks.scope.Wireable[$tpe] {
        type In = $dep1Tpe
        def wire: _root_.zio.blocks.scope.Wire[$dep1Tpe, $tpe] = $wireBody
      }
    """
    c.Expr[Wireable[T]](result)
  }

  private def generateWireable2[T: c.WeakTypeTag](c: blackbox.Context)(
    dep1Tpe: c.Type,
    dep2Tpe: c.Type,
    hasScopeParam: Boolean,
    isAutoCloseable: Boolean
  ): c.Expr[Wireable[T]] = {
    import c.universe._

    val tpe = weakTypeOf[T]

    val wireBody = if (hasScopeParam) {
      q"""
        _root_.zio.blocks.scope.Wire.Shared.fromFunction[$dep1Tpe with $dep2Tpe, $tpe] { scope =>
          val arg1 = scope.get[$dep1Tpe]
          val arg2 = scope.get[$dep2Tpe]
          val instance = new $tpe(arg1, arg2)(scope)
          _root_.zio.blocks.context.Context[$tpe](instance)
        }
      """
    } else if (isAutoCloseable) {
      q"""
        _root_.zio.blocks.scope.Wire.Shared.fromFunction[$dep1Tpe with $dep2Tpe, $tpe] { scope =>
          val arg1 = scope.get[$dep1Tpe]
          val arg2 = scope.get[$dep2Tpe]
          val instance = new $tpe(arg1, arg2)
          scope.defer(instance.asInstanceOf[AutoCloseable].close())
          _root_.zio.blocks.context.Context[$tpe](instance)
        }
      """
    } else {
      q"""
        _root_.zio.blocks.scope.Wire.Shared.fromFunction[$dep1Tpe with $dep2Tpe, $tpe] { scope =>
          val arg1 = scope.get[$dep1Tpe]
          val arg2 = scope.get[$dep2Tpe]
          val instance = new $tpe(arg1, arg2)
          _root_.zio.blocks.context.Context[$tpe](instance)
        }
      """
    }

    val result = q"""
      new _root_.zio.blocks.scope.Wireable[$tpe] {
        type In = $dep1Tpe with $dep2Tpe
        def wire: _root_.zio.blocks.scope.Wire[$dep1Tpe with $dep2Tpe, $tpe] = $wireBody
      }
    """
    c.Expr[Wireable[T]](result)
  }
}
