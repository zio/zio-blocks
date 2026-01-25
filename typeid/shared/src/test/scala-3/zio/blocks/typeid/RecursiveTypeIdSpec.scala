package zio.blocks.typeid

import zio.test._

object RecursiveTypeIdSpec extends ZIOSpecDefault {

  type RecursiveAlias = (Int, Option[RecursiveAlias])

  case class RecursiveCase(value: Int, next: Option[RecursiveCase])

  def spec = suite("RecursiveTypeId")(
    test("derives TypeId for recursive type alias") {
      val typeId = TypeId.of[RecursiveAlias]
      assertTrue(
        typeId.fullName.contains("RecursiveAlias"),
        typeId.isAlias
      )
    },
    test("derives TypeId for recursive case class") {
      val typeId = TypeId.of[RecursiveCase]
      assertTrue(
        typeId.fullName.contains("RecursiveCase"),
        typeId.isCaseClass
      )
    },
    test("recursive alias has valid aliasedTo") {
      val typeId    = TypeId.of[RecursiveAlias]
      val aliasedTo = typeId.aliasedTo
      assertTrue(aliasedTo.isDefined)
    },
    test("recursive alias contains self-reference") {
      val typeId    = TypeId.of[RecursiveAlias]
      val aliasedTo = typeId.aliasedTo.get

      def containsRefTo(name: String)(repr: TypeRepr): Boolean = repr match {
        case TypeRepr.Ref(id)                  => id.name == name
        case TypeRepr.Applied(tycon, args)     => containsRefTo(name)(tycon) || args.exists(containsRefTo(name))
        case TypeRepr.Tuple(elems)             => elems.exists(e => containsRefTo(name)(e.tpe))
        case TypeRepr.Union(types)             => types.exists(containsRefTo(name))
        case TypeRepr.Intersection(types)      => types.exists(containsRefTo(name))
        case TypeRepr.Function(params, result) => params.exists(containsRefTo(name)) || containsRefTo(name)(result)
        case _                                 => false
      }

      assertTrue(containsRefTo("RecursiveAlias")(aliasedTo))
    },
    test("recursive alias structure is correct") {
      val typeId    = TypeId.of[RecursiveAlias]
      val aliasedTo = typeId.aliasedTo.get

      aliasedTo match {
        case TypeRepr.Tuple(elems) =>
          assertTrue(
            elems.size == 2,
            elems.head.tpe match {
              case TypeRepr.Ref(id) => id.name == "Int"
              case _                => false
            },
            elems(1).tpe match {
              case TypeRepr.Applied(TypeRepr.Ref(optionId), _) => optionId.name == "Option"
              case _                                           => false
            }
          )
        case _ => assertTrue(false)
      }
    }
  )
}
