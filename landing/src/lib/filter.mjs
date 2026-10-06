/** Whether a catalog tile passes the active filters. `null` means "any". */
export function matches(tile, f) {
  return (
    (f.category === null || tile.category === f.category) &&
    (f.platform === null || tile.platforms.includes(f.platform)) &&
    (f.scala === null || tile.scala.includes(f.scala))
  );
}
