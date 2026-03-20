package zio.blocks.schema

import zio.blocks.schema.json.Json
import zio.blocks.schema.patch.{Patch, PatchMode}

trait SyntaxVersionSpecific {
  implicit final class SchemaValueOps[A](self: A) {
    def applyPatch(patch: Patch[A]): A = patch(self)

    def applyPatchStrict(patch: Patch[A]): Either[SchemaError, A] = patch(self, PatchMode.Strict)

    def diff(that: A)(implicit schema: Schema[A]): Patch[A] = schema.diff(self, that)

    def show(implicit schema: Schema[A]): String = schema.toDynamicValue(self).toString

    def toJson(implicit schema: Schema[A]): Json = schema.jsonCodec.encodeValue(self)

    def toJsonString(implicit schema: Schema[A]): String = schema.jsonCodec.encodeToString(self)

    def toJsonBytes(implicit schema: Schema[A]): Array[Byte] = schema.jsonCodec.encode(self)
  }

  implicit final class StringSchemaOps(self: String) {
    def fromJson[A](implicit schema: Schema[A]): Either[SchemaError, A] = schema.jsonCodec.decode(self)
  }

  implicit final class ByteArraySchemaOps(self: Array[Byte]) {
    def fromJson[A](implicit schema: Schema[A]): Either[SchemaError, A] = schema.jsonCodec.decode(self)
  }
}
