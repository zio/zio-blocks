package zio.blocks.schema

object IntoVersionSpecific {
  inline def derived[A, B]: Into[A, B] = ${ IntoAsVersionSpecificImpl.derivedIntoImpl[A, B] }
}
