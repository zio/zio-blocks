package zio.blocks.schema

import zio.blocks.schema.json.{Json, JsonEncoder, JsonFormat}
import zio.blocks.schema.patch.{Patch, PatchMode}

trait SyntaxVersionSpecific {
  implicit final class SchemaValueOps[A](self: A) {
    def applyPatch(patch: Patch[A]): A = patch(self)

    def applyPatchStrict(patch: Patch[A]): Either[SchemaError, A] = patch(self, PatchMode.Strict)

    def diff(that: A)(implicit schema: Schema[A]): Patch[A] = schema.diff(self, that)

    def show(implicit schema: Schema[A]): String = schema.toDynamicValue(self).toString

    def toJson(implicit jsonEncoder: JsonEncoder[A]): Json = jsonEncoder.encode(self)

    def toJsonString(implicit schema: Schema[A]): String = schema.getInstance(JsonFormat).encodeToString(self)

    def toJsonBytes(implicit schema: Schema[A]): Array[Byte] = schema.getInstance(JsonFormat).encode(self)
  }

  implicit final class StringSchemaOps(self: String) {
    def fromJson[A](implicit schema: Schema[A]): Either[SchemaError, A] = schema.getInstance(JsonFormat).decode(self)
  }

  implicit final class ByteArraySchemaOps(self: Array[Byte]) {
    def fromJson[A](implicit schema: Schema[A]): Either[SchemaError, A] = schema.getInstance(JsonFormat).decode(self)
  }
}
