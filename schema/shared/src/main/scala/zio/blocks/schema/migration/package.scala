package zio.blocks.schema

/**
 * Package object for the migration system.
 *
 * Extends `MigrationSelectorSyntax` to make selector extension methods
 * available to all code that imports from the migration package.
 *
 * This allows users to write selector expressions like:
 *   - `_.field.each`
 *   - `_.variant.when[Type]`
 *   - `_.seq.at(0)`
 *
 * Without explicitly importing the syntax.
 */
package object migration extends MigrationSelectorSyntax
