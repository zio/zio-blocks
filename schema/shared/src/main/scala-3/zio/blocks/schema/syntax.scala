package zio.blocks.schema

import zio.blocks.schema.json.{Json, JsonBinaryCodecDeriver, JsonEncoder}
import zio.blocks.schema.patch.{Patch, PatchMode}

trait SyntaxVersionSpecific {

  extension [A](self: A) {

    def diff(that: A)(using schema: Schema[A]): Patch[A] =
      schema.diff(self, that)

    def show(using schema: Schema[A]): String =
      schema.toDynamicValue(self).toString

    def toJson(using schema: Schema[A]): Json =
      JsonEncoder.fromSchema[A].encode(self)

    def toJsonString(using schema: Schema[A]): String =
      toJson.print

    def toJsonBytes(using schema: Schema[A]): Array[Byte] =
      schema.derive(JsonBinaryCodecDeriver).encode(self)

    def applyPatch(patch: Patch[A]): A =
      patch(self)

    def applyPatchStrict(patch: Patch[A]): Either[SchemaError, A] =
      patch(self, PatchMode.Strict)
  }

  extension (self: String) {

    def fromJson[A](using schema: Schema[A]): Either[SchemaError, A] = {
      val codec = schema.derive(JsonBinaryCodecDeriver)
      codec.decode(self.getBytes(java.nio.charset.StandardCharsets.UTF_8))
    }
  }

  extension (self: Array[Byte]) {

    def fromJson[A](using schema: Schema[A]): Either[SchemaError, A] = {
      val codec = schema.derive(JsonBinaryCodecDeriver)
      codec.decode(self)
    }
  }
}
