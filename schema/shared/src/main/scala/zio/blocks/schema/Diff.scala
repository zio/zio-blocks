package zio.blocks.schema

object Diff {

  def diff[A](source: A, target: A)(implicit schema: Schema[A]): Patch[A] = schema.reflect match {
    case x @ Reflect.Record(fields, _, _, _, _) =>
      fields.foldLeft(Patch(Vector.empty, schema)) { (acc, field) =>
        val lens        = Lens.apply(x, field.asInstanceOf[Term.Bound[A, Any]])
        val targetValue = lens.get(target)
        if (lens.get(source) != targetValue) {
          acc ++ Patch.replace(lens, targetValue)
        } else acc
      }
    // resolve other cases
    case _ => Patch(Vector.empty, schema)
  }

}
