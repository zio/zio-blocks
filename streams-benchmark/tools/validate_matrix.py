#!/usr/bin/env python3
import json, re
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
m=json.loads((ROOT/'streams-benchmark/benchmark-manifest.json').read_text())
ranking_expected={
 'StreamEvalBench','StreamPipelineBench','StreamSetupBench','StreamConcurrentBench',
 'StreamAsyncEvalBench','StreamAsyncPipelineBench','StreamAsyncSetupBench','StreamAsyncConcurrentBench',
}
ranking_allowlist=m.get('providerRankingAllowlist',[])
assert len(ranking_allowlist)==len(set(ranking_allowlist)), 'duplicate provider ranking class'
assert set(ranking_allowlist)==ranking_expected, 'provider ranking allowlist mismatch'
assert m.get('providerVersions')=={'fs2':'3.14.0','pekko':'1.7.0','kyo':'1.0.0-RC6','ox':'1.0.6'}, 'provider versions mismatch'
assert m.get('providerPolicies')=={
 'kyoStreamBufferSize':16,
 'kyoFanInPolicy':'N/A: public collectAll and binary merge do not expose bounded dynamic fan-in',
 'oxBufferPolicy':'BufferCapacity.default; no explicit source buffer',
}, 'provider policy mismatch'
cells=sum(len(m['parity'][x]) for x in ('eval','pipeline','setup'))
c=m['parity']['concurrency']; cells += len(c['operators'])*len(c['parallelism'])*len(c['workload'])
assert cells == 52, f"expected 52 parity cells, got {cells}"
ids=[x['id'] for x in m['micro']]
assert len(ids)==len(set(ids)), 'duplicate micro row'
required=['state.','suspension.','dynamic.','primitive.','terminal.','resource.','concurrency.']
assert all(any(i.startswith(prefix) for i in ids) for prefix in required)
for row in m['micro']:
    assert row.get('clause'), f"{row['id']} has no acceptance clause"
    assert row.get('category'), f"{row['id']} has no comparison category"
sources='\n'.join(p.read_text() for p in (ROOT/'streams-benchmark/src/main/scala').rglob('*.scala'))
declared=set(re.findall(r'@Benchmark\s+def\s+([A-Za-z0-9_]+)',sources))
parity_methods={f'async_{x}' for family in ('eval','pipeline','setup') for x in m['parity'][family]}
parity_methods |= {f'async_{x}' for x in m['parity']['concurrency']['operators']}
missing=parity_methods-declared
assert not missing, f"missing parity declarations: {sorted(missing)}"

# The inventory is derived from declarations, rather than trusting the manifest's
# claimed count. Parameterized concurrency declarations expand to eighteen cells.
bench_dir=ROOT/'streams-benchmark/src/main/scala/zio/blocks/streams/bench'
native_concurrent=(bench_dir/'StreamConcurrentBench.scala').read_text()
ready_concurrent=(bench_dir/'StreamAsyncConcurrentBench.scala').read_text()
assert '@Benchmark\n  def kyo_mapPar' in native_concurrent, 'native Kyo mapPar is missing'
assert '"kyo:mapPar"' in ready_concurrent, 'ready-effect Kyo mapPar is missing'
for unsupported in ('kyo_mergeAll', 'kyo_flatMapPar', '"kyo:mergeAll"', '"kyo:flatMapPar"'):
 assert unsupported not in native_concurrent + ready_concurrent, f'unsupported Kyo dynamic fan-in cell present: {unsupported}'
sync_expected={
 'StreamEvalBench.scala':{f'zb_{x}' for x in m['parity']['eval']},
 'StreamPipelineBench.scala':{f'zb_{x}' for x in m['parity']['pipeline']},
 'StreamSetupBench.scala':{f'zb_{x}' for x in m['parity']['setup']},
 'StreamConcurrentBench.scala':{f'zb_{x}' for x in c['operators']},
}
for name,expected in sync_expected.items():
 actual=set(re.findall(r'@Benchmark\s+(?:\n\s*)?def\s+(zb_[A-Za-z0-9_]+)',(bench_dir/name).read_text()))
 assert actual==expected, f'{name} inventory mismatch: missing={sorted(expected-actual)}, extra={sorted(actual-expected)}'

async_expected={
 'StreamAsyncEvalParityBench':{f'async_{x}' for x in m['parity']['eval']},
 'StreamAsyncPipelineParityBench':{f'async_{x}' for x in m['parity']['pipeline']},
 'StreamAsyncSetupParityBench':{f'async_{x}' for x in m['parity']['setup']},
 'StreamAsyncConcurrentParityBench':{f'async_{x}' for x in c['operators']},
}
for cls,expected in async_expected.items():
 block=re.search(rf'class {cls}\b(.*?)(?=\n(?:class|@BenchmarkMode)|\Z)',sources,re.S)
 assert block, f'missing class {cls}'
 actual=set(re.findall(r'@Benchmark\s+def\s+(async_[A-Za-z0-9_]+)',block.group(1)))
 assert actual==expected, f'{cls} inventory mismatch: missing={sorted(expected-actual)}, extra={sorted(actual-expected)}'

legacy_expected={
 'syncSource_asyncTerminal','liftedReader_readyRead','nativeReader_readyRead','readyCallback_map',
 'readyCallback_filter','suspendedCallback_map','suspendedReader_read','liftedSync_afterAsync',
 'asyncReader_readyCallback','dynamic_flatMapAsync','asyncAcquire_finalize','directByte_bulk',
 'primitive_boolean','primitive_byte','primitive_char','primitive_short','primitive_int','primitive_long',
 'primitive_float','primitive_double','bulk_collect','sink_drain','bounded_mapParAsync','bounded_flatMapParAsync'
}
legacy=m.get('legacyStreamAsyncBench',{})
assert set(legacy)==legacy_expected, f'legacy semantic map mismatch: {sorted(legacy_expected ^ set(legacy))}'
assert all(target in ids for target in legacy.values()), 'legacy semantic map targets an unknown micro row'
benchmarks={row['benchmark'] for row in m['micro']}
for row in m['micro']:
 method=row['benchmark'].rsplit('.',1)[1]
 assert method in declared, f"{row['id']} points to missing @Benchmark {row['benchmark']}"
 assert set(row)>= {'id','benchmark','category','params','expectedChecksum','setupBoundary','budget','baselineOwner','clause'}
 assert set(row['budget']) >= {'fixedBytes','bytesPerElement'}, f"{row['id']} has incomplete allocation budget"
 if row['params'].get('N') == [0,1,1000]:
  assert isinstance(row['budget']['fixedBytes'],(int,float)) and isinstance(row['budget']['bytesPerElement'],(int,float))

# Package J is a parameterized matrix rather than 64 copy-pasted methods. Keep
# every dimension closed so removing a primitive, route, execution, or slope
# point fails validation.
specialization=m.get('primitiveSpecialization',{})
primitive_expected=['boolean','byte','char','short','int','long','float','double']
route_expected=['stream','pipeline-stream','pipeline-sink','widened']
assert specialization.get('primitives')==primitive_expected, 'primitive-specialization lane matrix mismatch'
assert specialization.get('routes')==route_expected, 'primitive-specialization route matrix mismatch'
assert specialization.get('N')==[0,1,1000], 'primitive-specialization slope matrix mismatch'
assert specialization.get('stageShape')==['source','preserving','transforming','sink'], 'primitive stage shape mismatch'
execution_expected={
 'sync':'StreamPrimitiveSpecializationBench.syncPrimitiveRoute',
 'async':'StreamPrimitiveSpecializationBench.asyncPrimitiveRoute',
}
assert specialization.get('executions')==execution_expected, 'primitive-specialization execution matrix mismatch'
assert all(target.rsplit('.',1)[1] in declared for target in execution_expected.values()), 'missing primitive matrix benchmark'
specialization_rows={row['benchmark']:row for row in m['micro'] if row['id'].startswith('primitive-specialization.')}
assert set(specialization_rows)==set(execution_expected.values()), 'primitive specialization campaign rows mismatch'
for benchmark,row in specialization_rows.items():
 assert row['params']=={'primitive':primitive_expected,'route':route_expected,'N':[0,1,1000]}, f'{benchmark} parameter matrix mismatch'
primitive_source=(bench_dir/'StreamPrimitiveSpecializationBench.scala').read_text()
for lane in primitive_expected:
 assert len(re.findall(rf'case "{lane}"',primitive_source))==2, f'missing sync/async dispatch for {lane}'
for route in route_expected:
 assert len(re.findall(rf'case "{route}"',primitive_source))==2, f'missing sync/async route for {route}'
specialization_cells=len(primitive_expected)*len(route_expected)*len(execution_expected)*len(specialization['N'])
assert specialization_cells==192, f'expected 192 primitive specialization parameter cells, got {specialization_cells}'
assert 'new Thread' not in sources, 'thread-per-element fixture is prohibited'
print(f"matrix: PASS (52 sync + 52 ready cells; {specialization_cells} primitive specialization cells; {len(ids)} unique micro rows; 24 legacy semantics mapped)")
