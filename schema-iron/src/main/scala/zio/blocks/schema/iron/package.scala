package zio.blocks.schema

import io.github.iltotore.iron.*

package object iron {

  inline given ironSchema[A, C](using baseSchema: Schema[A], constraint: Constraint[A, C]): Schema[A :| C] =
    baseSchema.transformOrFail(
      a => a.refineEither[C].left.map(SchemaError.validationFailed),
      refined => refined.asInstanceOf[A]
    )
}

