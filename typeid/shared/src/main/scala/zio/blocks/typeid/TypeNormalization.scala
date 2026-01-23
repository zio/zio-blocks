package zio.blocks.typeid

private[typeid] object TypeNormalization {
  def dealias(tpe: TypeRepr): TypeRepr = tpe match {
    case TypeRepr.Ref(id, args) =>
      id.kind match {
        case TypeDefKind.TypeAlias(alias) =>
          if (args.size == id.typeParams.size) {
            substitute(alias, id.typeParams, args).dealias
          } else {
            tpe
          }
        case _ => tpe
      }

    case TypeRepr.AppliedType(t, args) =>
      TypeRepr.AppliedType(dealias(t), args.map(dealias)) // Shallow or deep?

    case _ => tpe
  }

  def substitute(tpe: TypeRepr, params: List[TypeParam], args: List[TypeRepr]): TypeRepr =
    // Simple substitution logic
    // Map params(i) -> args(i)
    if (params.isEmpty || args.isEmpty) tpe
    else {
      // Create a map for fast lookup? Or just by index if usage matches
      // By name often safer if shadowing, but index is robust
      val mapping = params.zip(args).map { case (p, a) => p.name -> a }.toMap

      def loop(t: TypeRepr): TypeRepr = t match {
        case TypeRepr.TypeParamRef(name, _) if mapping.contains(name) => mapping(name)
        case TypeRepr.Ref(id, targs)                                  => TypeRepr.Ref(id, targs.map(loop))
        case TypeRepr.AppliedType(bt, targs)                          => TypeRepr.AppliedType(loop(bt), targs.map(loop))
        case TypeRepr.Union(ts)                                       => TypeRepr.Union(ts.map(loop))
        case TypeRepr.Intersection(ts)                                => TypeRepr.Intersection(ts.map(loop))
        case TypeRepr.Function(ps, res)                               => TypeRepr.Function(ps.map(loop), loop(res))
        case TypeRepr.Tuple(es)                                       => TypeRepr.Tuple(es.map(loop))
        // ... implementations for others
        case other => other
      }
      loop(tpe)
    }
}
