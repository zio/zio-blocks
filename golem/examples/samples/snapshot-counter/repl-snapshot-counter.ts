const c = SnapshotCounter.get("demo");
const a = await c.increment();
const b = await c.increment();
console.log({ a, b });
