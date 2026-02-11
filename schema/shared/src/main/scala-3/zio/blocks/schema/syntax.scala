package zio.blocks.schema

import zio.blocks.schema.json.{Json, JsonEncoder, JsonFormat}
import zio.blocks.schema.patch.{Patch, PatchMode}

trait SyntaxVersionSpecific {
  extension [A](self: A) {
    def diff(that: A)(using schema: Schema[A]): Patch[A] = schema.diff(self, that)

    def show(using schema: Schema[A]): String = schema.toDynamicValue(self).toString

    def toJson(using jsonEncoder: JsonEncoder[A]): Json = jsonEncoder.encode(self)

    def toJsonString(using schema: Schema[A]): String = toJson.print

    def toJsonBytes(using schema: Schema[A]): Array[Byte] = schema.getInstance(JsonFormat).encode(self)

    def applyPatch(patch: Patch[A]): A = patch(self)

    def applyPatchStrict(patch: Patch[A]): Either[SchemaError, A] = patch(self, PatchMode.Strict)
  }

  extension (self: String) {
    def fromJson[A](using schema: Schema[A]): Either[SchemaError, A] = schema.getInstance(JsonFormat).decode(self)
  }

  extension (self: Array[Byte]) {
    def fromJson[A](using schema: Schema[A]): Either[SchemaError, A] = schema.getInstance(JsonFormat).decode(self)
  }
}
