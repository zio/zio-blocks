package zio.blocks.schema.migration

object TypeLevel {

  sealed trait FieldTree

  sealed trait Empty extends FieldTree

  sealed trait Branch[Name <: String, Op <: Operation, Children <: FieldTree, Siblings <: FieldTree] extends FieldTree

  sealed trait Operation
  sealed trait OpAdd    extends Operation
  sealed trait OpDrop   extends Operation
  sealed trait OpNested extends Operation

}
