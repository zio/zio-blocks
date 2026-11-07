#!/usr/bin/env python3
"""Generate every frozen benchmark campaign cell from the canonical manifest."""
import argparse,hashlib,itertools,json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
PREFIX="zio.blocks.streams.bench."
COMMAND=["java","-jar","streams-benchmark/target/scala-3.8.3/streams-benchmark.jar",
  "-wi","10","-i","10","-f","{fork}","-w","{warmupTime}s","-r","{measurementTime}s",
  "-rf","json","-rff","{output}","{params}","{benchmark}"]

def load(path): return json.loads(Path(path).read_text())
def digest(value): return hashlib.sha256(json.dumps(value,sort_keys=True,separators=(",",":")).encode()).hexdigest()
def scalar_params(params):
  keys=sorted(params)
  values=[value if isinstance(value,list) else [value] for value in (params[key] for key in keys)]
  return [dict(zip(keys,row)) for row in itertools.product(*values)]
def metadata(logical,params,harness,host,work,checksum,boundary):
  return {"benchmark":logical,"params":params,"mode":"Throughput","warmup":"10x1s","measurement":"10x1s",
    "jvmArgs":[],"scala":"3.8.3","jdk":host["javaSha256"],"harnessHash":harness,
    "work":work,"checksum":checksum,"boundary":boundary}
def cell(ident,baseline,candidate,params,harness,host,work,checksum,boundary):
  meta=metadata(ident,params,harness,host,work,checksum,boundary)
  return {"id":ident,"baseline":PREFIX+baseline,"candidate":PREFIX+candidate,
    "baselineMetadata":meta,"candidateMetadata":dict(meta)}
def base_config(comparison,kind,baseline_commit,candidate_commit,baseline_worktree,candidate_worktree,harness,host,cells):
  command=COMMAND if kind=="throughput" else [*COMMAND[:3],"-prof","gc",*COMMAND[3:]]
  return {"schema":1,"comparison":comparison,"kind":kind,"baselineCommit":baseline_commit,
    "candidateCommit":candidate_commit,"baselineWorktree":str(Path(baseline_worktree).resolve()),
    "candidateWorktree":str(Path(candidate_worktree).resolve()),"harnessHash":harness,"seed":20260818,
    "pairs":6,"warmupSeconds":1,"measurementSeconds":1,"warmupIterations":10,"measurementIterations":10,
    "host":host,"command":command,"cells":cells,
    "escalation":{"initialPairs":6,"extendedPairs":10,"replacementPairs":10,
      "replacementWarmupSeconds":3,"replacementMeasurementSeconds":3,"pooling":"forbidden"}}
def parity(manifest,async_side,harness,host):
  classes={"eval":("StreamEvalBench","StreamAsyncEvalParityBench"),
    "pipeline":("StreamPipelineBench","StreamAsyncPipelineParityBench"),
    "setup":("StreamSetupBench","StreamAsyncSetupParityBench")}
  rows=[]
  for family,(sync_cls,async_cls) in classes.items():
    for method in manifest["parity"][family]:
      params={"N":10000}; sync=f"{sync_cls}.zb_{method}"; asynchronous=f"{async_cls}.async_{method}"
      baseline=asynchronous if async_side else sync; candidate=baseline
      rows.append(cell(f"{family}.{method}",baseline,candidate,params,harness,host,
        f"canonical {family}.{method} work","verified by StreamAsyncBenchmarkCorrectness",f"canonical {family} boundary"))
  concurrent=manifest["parity"]["concurrency"]
  for method,p,w in itertools.product(concurrent["operators"],concurrent["parallelism"],concurrent["workload"]):
    sync=f"StreamConcurrentBench.zb_{method}"; asynchronous=f"StreamAsyncConcurrentParityBench.async_{method}"
    chosen=asynchronous if async_side else sync; params={"parallelism":p,"workload":w}
    rows.append(cell(f"concurrency.{method}.p{p}.{w}",chosen,chosen,params,harness,host,
      f"one million elements; {method}; {w} work","verified by StreamAsyncBenchmarkCorrectness","prebuilt operator fixture"))
  return rows
def diagnostic(manifest,harness,host):
  rows=[]; classes={"eval":("StreamEvalBench","StreamAsyncEvalParityBench"),
    "pipeline":("StreamPipelineBench","StreamAsyncPipelineParityBench"),"setup":("StreamSetupBench","StreamAsyncSetupParityBench")}
  for family,(sync_cls,async_cls) in classes.items():
    for method in manifest["parity"][family]:
      rows.append(cell(f"diagnostic.{family}.{method}",f"{sync_cls}.zb_{method}",f"{async_cls}.async_{method}",
        {"N":10000},harness,host,f"canonical {family}.{method} work","verified by StreamAsyncBenchmarkCorrectness",f"canonical {family} boundary"))
  concurrent=manifest["parity"]["concurrency"]
  for method,p,w in itertools.product(concurrent["operators"],concurrent["parallelism"],concurrent["workload"]):
    rows.append(cell(f"diagnostic.concurrency.{method}.p{p}.{w}",f"StreamConcurrentBench.zb_{method}",
      f"StreamAsyncConcurrentParityBench.async_{method}",{"parallelism":p,"workload":w},harness,host,
      f"one million elements; {method}; {w} work","verified by StreamAsyncBenchmarkCorrectness","prebuilt operator fixture"))
  return rows
def micro(manifest,harness_by_owner,host):
  by_owner={"syncBaseline":[],"asyncBaseline":[]}; slopes={"syncBaseline":{},"asyncBaseline":{}}
  for row in manifest["micro"]:
    owner=row["baselineOwner"]
    expanded=scalar_params(row.get("params",{}))
    group={}
    for params in expanded:
      suffix="" if not params else "."+".".join(f"{k}-{v}" for k,v in sorted(params.items()))
      ident=row["id"]+suffix; group[str(params.get("N"))]=ident if "N" in params else None
      by_owner[owner].append(cell(ident,row["benchmark"],row["benchmark"],params,harness_by_owner[owner],host,
        row["category"],row["expectedChecksum"],row["setupBoundary"]))
    if set(group)=={"0","1","1000"}:
      slopes[owner][row["id"]]={"cells":group,"budget":row["budget"]}
  return by_owner,slopes
def record(root): return load(Path(root)/"streams-benchmark-overlay.json")
def main():
  p=argparse.ArgumentParser();p.add_argument("--host",required=True);p.add_argument("--candidate-commit",required=True)
  p.add_argument("--sync-baseline",required=True);p.add_argument("--sync-candidate",required=True)
  p.add_argument("--async-baseline",required=True);p.add_argument("--async-candidate",required=True);p.add_argument("--out",required=True);a=p.parse_args()
  manifest=load(ROOT/"streams-benchmark/benchmark-manifest.json");host=load(a.host);out=Path(a.out);out.mkdir(parents=True,exist_ok=False)
  records={name:record(path) for name,path in (("sb",a.sync_baseline),("sc",a.sync_candidate),("ab",a.async_baseline),("ac",a.async_candidate))}
  if records["sb"]["syncOverlayHash"]!=records["sc"]["syncOverlayHash"]: raise SystemExit("sync harness hashes differ")
  if records["ab"]["asyncOverlayHash"]!=records["ac"]["asyncOverlayHash"]: raise SystemExit("async harness hashes differ")
  sync_hash=records["sb"]["syncOverlayHash"];async_hash=records["ab"]["asyncOverlayHash"]
  configs={
    "sync-throughput.json":base_config("sync-regression","throughput",manifest["syncBaseline"],a.candidate_commit,a.sync_baseline,a.sync_candidate,sync_hash,host,parity(manifest,False,sync_hash,host)),
    "async-throughput.json":base_config("async-regression","throughput",manifest["asyncBaseline"],a.candidate_commit,a.async_baseline,a.async_candidate,async_hash,host,parity(manifest,True,async_hash,host)),
    "diagnostic-ratio.json":base_config("diagnostic-ratio","throughput",a.candidate_commit,a.candidate_commit,a.sync_candidate,a.async_candidate,digest(manifest["parity"]),host,diagnostic(manifest,digest(manifest["parity"]),host))}
  micro_rows,slopes=micro(manifest,{"syncBaseline":sync_hash,"asyncBaseline":async_hash},host)
  for owner,prefix,baseline,base_work,candidate_work,harness in (
    ("syncBaseline","sync-micro",manifest["syncBaseline"],a.sync_baseline,a.sync_candidate,sync_hash),
    ("asyncBaseline","async-micro",manifest["asyncBaseline"],a.async_baseline,a.async_candidate,async_hash)):
    for kind in ("throughput","allocation"):
      cfg=base_config("sync-regression" if owner=="syncBaseline" else "async-regression",kind,baseline,a.candidate_commit,base_work,candidate_work,harness,host,micro_rows[owner])
      if kind=="allocation": cfg["slopeGroups"]=slopes[owner]
      configs[f"{prefix}-{kind}.json"]=cfg
  for name,value in configs.items(): (out/name).write_text(json.dumps(value,indent=2,sort_keys=True)+"\n")
  (out/"SHA256SUMS").write_text("".join(f"{hashlib.sha256((out/name).read_bytes()).hexdigest()}  {name}\n" for name in sorted(configs)))
  print(f"generated {len(configs)} campaign configurations with {sum(len(c['cells']) for c in configs.values())} cells")
if __name__=="__main__":main()
