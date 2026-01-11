package zio.blocks.schema

sealed trait PatchMode
object PatchMode {
  case object Strict  extends PatchMode
  case object Lenient extends PatchMode
}

final case class Patch[A](
  dynamicPatch: DynamicPatch,
  schema: Schema[A],
  mode: PatchMode = PatchMode.Strict
) {
  
  def apply(value: A, applyMode: PatchMode): Either[SchemaError, A] = {
    val dyn = schema.toDynamicValue(value)
    dynamicPatch.apply(dyn, applyMode).flatMap { resDyn =>
      schema.fromDynamicValue(resDyn)
    }
  }

  def apply(value: A): A = apply(value, mode) match {
    case Right(v) => v
    case Left(e) => throw new RuntimeException(s"Patch application failed in strict mode: ${e.message}")
  }

  def ++(that: Patch[A]): Patch[A] = Patch(dynamicPatch ++ that.dynamicPatch, schema, mode)

  def lenient: Patch[A] = copy(mode = PatchMode.Lenient)
  def strict: Patch[A]  = copy(mode = PatchMode.Strict)

  def applyLenient(value: A): A = apply(value, PatchMode.Lenient) match {
    case Right(v) => v
    case Left(_) => value
  }
  
  def applyOption(value: A): Option[A] = apply(value, mode).toOption
  def applyOrFail(value: A): Either[SchemaError, A] = apply(value, mode)
}

object Patch {
  def empty[A](implicit schema: Schema[A]): Patch[A] = Patch(DynamicPatch(Vector.empty), schema)

  def replace[S, A](optic: Optic[S, A], value: A)(implicit schema: Schema[S]): Patch[S] = {
    val dynOptic = optic.toDynamic
    // Use optic.focus directly
    val focus = optic.focus
    val dynValue = focus.toDynamicValue(value)(zio.blocks.schema.binding.Binding.bindingHasBinding)
    
    Patch(DynamicPatch(Vector(DynamicPatchOp(dynOptic, Operation.Set(dynValue)))), schema)
  }

  def insert[S, A](optic: Optic[S, A], value: A)(implicit schema: Schema[S]): Patch[S] = {
     val dynOptic = optic.toDynamic
     val nodes = dynOptic.nodes
     
     if (nodes.nonEmpty && nodes.last.isInstanceOf[DynamicOptic.Node.AtIndex]) {
       val idx = nodes.last.asInstanceOf[DynamicOptic.Node.AtIndex].index
       val parentOptic = DynamicOptic(nodes.init)
       
       val focus = optic.focus
       val dynValue = focus.toDynamicValue(value)(zio.blocks.schema.binding.Binding.bindingHasBinding)
       
       val seqOp = SeqOp.Insert(idx, Vector(dynValue))
       val op = Operation.SequenceEdit(Vector(seqOp))
       
       Patch(DynamicPatch(Vector(DynamicPatchOp(parentOptic, op))), schema)
     } else if (nodes.nonEmpty && nodes.last.isInstanceOf[DynamicOptic.Node.AtMapKey]) {
       val keyVal = nodes.last.asInstanceOf[DynamicOptic.Node.AtMapKey].key
       val parentOptic = DynamicOptic(nodes.init)
       
       val focus = optic.focus
       val dynValue = focus.toDynamicValue(value)(zio.blocks.schema.binding.Binding.bindingHasBinding)
       
       val mapOp = MapOp.Add(keyVal, dynValue)
       val op = Operation.MapEdit(Vector(mapOp))
       Patch(DynamicPatch(Vector(DynamicPatchOp(parentOptic, op))), schema)
     } else {
       throw new IllegalArgumentException("Patch.insert requires an optic pointing to a sequence index or map key")
     }
  }

  def remove[S, A](optic: Optic[S, A])(implicit schema: Schema[S]): Patch[S] = {
     val dynOptic = optic.toDynamic
     val nodes = dynOptic.nodes
     
     if (nodes.nonEmpty && nodes.last.isInstanceOf[DynamicOptic.Node.AtIndex]) {
       val idx = nodes.last.asInstanceOf[DynamicOptic.Node.AtIndex].index
       val parentOptic = DynamicOptic(nodes.init)
       val seqOp = SeqOp.Delete(idx, 1)
       val op = Operation.SequenceEdit(Vector(seqOp))
       Patch(DynamicPatch(Vector(DynamicPatchOp(parentOptic, op))), schema)
     } else if (nodes.nonEmpty && nodes.last.isInstanceOf[DynamicOptic.Node.AtMapKey]) {
       val keyVal = nodes.last.asInstanceOf[DynamicOptic.Node.AtMapKey].key
       val parentOptic = DynamicOptic(nodes.init)
       val mapOp = MapOp.Remove(keyVal)
       val op = Operation.MapEdit(Vector(mapOp))
       Patch(DynamicPatch(Vector(DynamicPatchOp(parentOptic, op))), schema)
     } else {
        throw new IllegalArgumentException("Patch.remove requires an optic pointing to a sequence index or map key")
     }
  }

  def diff[A](oldValue: A, newValue: A)(implicit schema: Schema[A]): Patch[A] = {
    val oldDyn = schema.toDynamicValue(oldValue)
    val newDyn = schema.toDynamicValue(newValue)
    val dynPatch = DynamicPatch.diff(oldDyn, newDyn)
    Patch(dynPatch, schema)
  }
}
