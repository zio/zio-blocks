package zio.blocks.schema

import zio.blocks.schema.json.{Json, JsonBinaryCodecDeriver, JsonEncoder}
import zio.blocks.schema.patch.{Patch, PatchMode}

trait SyntaxVersionSpecific {

  implicit final class SchemaValueOps[A](private val self: A) {

    def diff(that: A)(implicit schema: Schema[A]): Patch[A] =
      schema.diff(self, that)

    def show(implicit schema: Schema[A]): String =
      schema.toDynamicValue(self).toString

    def toJson(implicit schema: Schema[A]): Json =
      JsonEncoder.fromSchema[A].encode(self)

    def toJsonString(implicit schema: Schema[A]): String =
      toJson.print

    def toJsonBytes(implicit schema: Schema[A]): Array[Byte] =
      schema.derive(JsonBinaryCodecDeriver).encode(self)

    def applyPatch(patch: Patch[A]): A =
      patch(self)

    def applyPatchStrict(patch: Patch[A]): Either[SchemaError, A] =
      patch(self, PatchMode.Strict)
  }

  implicit final class StringSchemaOps(private val self: String) {

    def fromJson[A](implicit schema: Schema[A]): Either[SchemaError, A] = {
      val codec = schema.derive(JsonBinaryCodecDeriver)
      codec.decode(self.getBytes(java.nio.charset.StandardCharsets.UTF_8))
    }
  }

  implicit final class ByteArraySchemaOps(private val self: Array[Byte]) {

    def fromJson[A](implicit schema: Schema[A]): Either[SchemaError, A] = {
      val codec = schema.derive(JsonBinaryCodecDeriver)
      codec.decode(self)
    }
  }
}
