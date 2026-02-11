package zio.blocks.schema

import zio.blocks.schema.json.{Json, JsonEncoder, JsonFormat}
import zio.blocks.schema.patch.{Patch, PatchMode}

trait SyntaxVersionSpecific {
  implicit final class SchemaValueOps[A](private val self: A) {
    def diff(that: A)(implicit schema: Schema[A]): Patch[A] = schema.diff(self, that)

    def show(implicit schema: Schema[A]): String = schema.toDynamicValue(self).toString

    def toJson(implicit jsonEncoder: JsonEncoder[A]): Json = jsonEncoder.encode(self)

    def toJsonString(implicit schema: Schema[A]): String = toJson.print

    def toJsonBytes(implicit schema: Schema[A]): Array[Byte] = schema.getInstance(JsonFormat).encode(self)

    def applyPatch(patch: Patch[A]): A = patch(self)

    def applyPatchStrict(patch: Patch[A]): Either[SchemaError, A] = patch(self, PatchMode.Strict)
  }

  implicit final class StringSchemaOps(private val self: String) {
    def fromJson[A](implicit schema: Schema[A]): Either[SchemaError, A] = schema.getInstance(JsonFormat).decode(self)
  }

  implicit final class ByteArraySchemaOps(private val self: Array[Byte]) {
    def fromJson[A](implicit schema: Schema[A]): Either[SchemaError, A] = schema.getInstance(JsonFormat).decode(self)
  }
}
