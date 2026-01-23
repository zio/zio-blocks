package zio.blocks.typeid

import scala.language.experimental.macros

trait TypeIdPlatformSpecific {
  def from[A]: TypeId[A] = macro TypeIdMacros.fromImpl[A]
  def of[A]: TypeId[A] = macro TypeIdMacros.fromImpl[A]

  implicit def derived[A]: TypeId[A] = macro TypeIdMacros.fromImpl[A]
}
