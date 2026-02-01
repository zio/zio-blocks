package zio.blocks.schema

/**
 * Schema migration system for type-safe, structural transformations between
 * schema versions.
 *
 * The migration system provides:
 *   - Pure, algebraic migration definitions as immutable data
 *   - Full serializability for offline planning and transport
 *   - Type-safe path selectors via lambda expressions
 *   - SchemaExpr-based value expressions for defaults and transforms
 *   - Enum case transformation with `.when[CaseType]` pattern
 *   - Compile-time path validation with descriptive errors
 *
 * ==Basic Usage==
 * {{{
 * val migration = MigrationBuilder[PersonV1, PersonV2]
 *   .renameField(_.name, _.fullName)
 *   .addField(_.email, SchemaExpr.literal("unknown@example.com"))
 *   .dropField(_.legacyId)
 *   .build
 *
 * val result: Either[String, PersonV2] = migration(v1Person)
 * }}}
 *
 * ==Enum Case Transformations==
 * {{{
 * val migration = MigrationBuilder[EventV1, EventV2]
 *   .renameCase[OldStatus, NewStatus](_.status)
 *   .when[ActiveCase](_.status)(
 *     _.addField(_.timestamp, SchemaExpr.literal(Instant.now))
 *   )
 *   .build
 * }}}
 *
 * ==Field Joins and Splits==
 * {{{
 * // Join multiple fields into one
 * val migration = MigrationBuilder[AddressV1, AddressV2]
 *   .joinFields(
 *     sourcePaths = Chunk(_.street, _.city, _.zip),
 *     targetField = _.fullAddress,
 *     combiner = ...
 *   )
 *   .build
 *
 * // Split one field into multiple
 * val migration = MigrationBuilder[AddressV1, AddressV2]
 *   .splitField(
 *     sourceField = _.fullAddress,
 *     targetPaths = Chunk(_.street, _.city, _.zip),
 *     splitter = ...
 *   )
 *   .build
 * }}}
 *
 * ==Dynamic Migration==
 * {{{
 * // Execute on DynamicValue without type schemas
 * val dynamicMigration: DynamicMigration = migration.dynamicMigration
 * val result: Either[String, DynamicValue] = dynamicMigration(dynamicValue)
 * }}}
 *
 * @see
 *   [[MigrationBuilder]] for the type-safe builder API
 * @see
 *   [[SchemaExpr]] for value expressions
 * @see
 *   [[DynamicMigration]] for untyped execution
 * @see
 *   [[MigrationAction]] for the action algebra
 */
package object migration
