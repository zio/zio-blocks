package zio.schema.codec

import zio._
import zio.schema._
import zio.schema.codec.DecodeError._
import zio.stream.ZPipeline
import scala.util.Try
import zio.schema.MutableSchemaBasedValueBuilder.CreateValueFromSchemaError

object ThriftCodec {

  implicit def thriftCodec[A](implicit schema: Schema[A]): BinaryCodec[A] =
    new BinaryCodec[A] {

      override def encode(value: A): Chunk[Byte] =
        new ThriftEncoder().encode(schema, value)

      override def decode(whole: Chunk[Byte]): Either[DecodeError, A] =
        if (whole.isEmpty) Left(EmptyContent("No bytes to decode"))
        else decodeChunk(whole)

      override def streamEncoder: ZPipeline[Any, Nothing, A, Byte] =
        ZPipeline.mapChunks { chunk =>
          val encoder = new ThriftEncoder()
          chunk.flatMap(encoder.encode(schema, _))
        }

      override def streamDecoder: ZPipeline[Any, DecodeError, Byte, A] =
        ZPipeline.mapChunksZIO { bytes =>
          Unsafe.unsafe { implicit u =>
            ZIO.fromEither(decodeChunk(bytes)).map(decoded => Chunk.single(decoded))
          }
        }

      private def decodeChunk(chunk: Chunk[Byte]): Either[DecodeError, A] =
        Try {
          new ThriftDecoder(chunk)
            .create(schema)
            .asInstanceOf[A]
        }.toEither.left.map {
          case error: CreateValueFromSchemaError[_] =>
            error.cause match {
              case de: DecodeError => de
              case ex              => ReadError(Cause.fail(ex), ex.getMessage)
            }
          case de: DecodeError => de
          case err             => ReadError(Cause.fail(err), err.getMessage)
        }
    }
}
