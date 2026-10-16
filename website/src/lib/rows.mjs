/**
 * Groups categories into rows for the catalog. On wide screens the tile grid has `columns` columns; consecutive
 * categories share a row while their tiles together fit in those columns (for example 1 + 1, or 1 + 2), and every
 * other category has its own row.
 */
export function packRows(categories, columns = 3) {
  const rows = [];
  for (const category of categories) {
    const last = rows[rows.length - 1];
    const used = last ? last.reduce((n, c) => n + c.blocks.length, 0) : 0;
    if (last && used + category.blocks.length <= columns) last.push(category);
    else rows.push([category]);
  }
  return rows;
}
