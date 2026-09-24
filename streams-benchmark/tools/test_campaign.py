#!/usr/bin/env python3
import json,os,subprocess,sys,tempfile
from pathlib import Path
import campaign

assert campaign.ratio_verdict([100]*6,[100]*6)["verdict"]=="pass"
assert campaign.ratio_verdict([100]*6,[90]*6)["verdict"]=="regression"
assert campaign.ratio_verdict([100]*6,[90,110]*3)["verdict"]=="inconclusive"
assert campaign.ratio_verdict([100]*6,[95]*6)["lower"]==.95 # zero variance
assert campaign.allocation_verdict([0]*6,[8]*6)["verdict"]=="pass"
assert campaign.allocation_verdict([100]*6,[106]*6)["verdict"]=="regression"
assert campaign.slope({0:10,1:12,1000:2010})=={"fixed":10.0,"bytesPerElement":2.0}
slope_samples={"baseline":{0:[10]*6,1:[12]*6,1000:[2010]*6},"candidate":{0:[11]*6,1:[13]*6,1000:[2011]*6}}
assert campaign.slope_verdict(slope_samples,{"fixedBytes":11,"bytesPerElement":2})["verdict"]=="pass"
assert campaign.slope_verdict(slope_samples,{"fixedBytes":10,"bytesPerElement":2})["verdict"]=="regression"
assert campaign.final_verdict({"verdict":"inconclusive"})["verdict"]=="fail"
assert campaign.final_verdict({"verdict":"inconclusive"},{"verdict":"inconclusive"},{"verdict":"pass"})["verdict"]=="pass"
assert campaign.final_verdict({"verdict":"pass"},{"verdict":"regression"})["verdict"]=="pass"
pair={k:k for k in campaign.PAIR_KEYS}; campaign.validate_pair(pair,dict(pair))
try: campaign.validate_pair(pair,dict(pair,checksum="bad")); raise AssertionError
except ValueError: pass
cfg={"comparison":"sync-regression","kind":"throughput","pairs":6,"measurementSeconds":1,
 "warmupIterations":10,"measurementIterations":10,"cells":[{"id":"x","baselineMetadata":pair,"candidateMetadata":dict(pair)}]}
campaign.validate_config(cfg)
try: campaign.validate_config(dict(cfg,pairs=7)); raise AssertionError
except ValueError: pass
assert campaign.order(4,6)==campaign.order(4,6) and len(campaign.order(4,6))==6
assert campaign.exact_benchmark_pattern("Bench.filterMap")==r"^Bench\.filterMap$"
try: campaign.validate_host({"machine":"a"},{"machine":"b"}); raise AssertionError
except RuntimeError: pass
try: campaign.validate_controlled_host(campaign.host_identity()); raise AssertionError
except RuntimeError: pass
campaign.validate_controlled_host({"affinity":"0-3","affinityActual":"0,1,2,3","powerMode":"performance","turbo":"off",
 "thermalCeiling":"80C","backgroundLoadCeiling":"1000"})
try:
 campaign.validate_controlled_host({"affinity":"0-3","affinityActual":"0,1","powerMode":"performance","turbo":"off",
  "thermalCeiling":"80C","backgroundLoadCeiling":"1000"}); raise AssertionError
except RuntimeError: pass
with tempfile.TemporaryDirectory() as d:
 p=Path(d); (p/"a").write_text("a"); campaign.checksum_tree(p)
 assert "SHA256SUMS" not in (p/"SHA256SUMS").read_text(); campaign.checksum_tree(p,True)
 (p/"a").write_text("b")
 try: campaign.checksum_tree(p,True); raise AssertionError
 except ValueError: pass
with tempfile.TemporaryDirectory() as d:
 p=Path(d)/"j.json"; p.write_text(json.dumps([{"primaryMetric":{"score":7},"secondaryMetrics":{"gc.alloc.rate.norm":{"score":3}}}]))
 assert campaign.jmh_value(p)==7 and campaign.jmh_value(p,True)==3
with tempfile.TemporaryDirectory() as d:
 p=Path(d); raw=p/"raw/01.json"; failed=p/"failed/01.json"
 raw.parent.mkdir(); raw.write_text("[]\n")
 assert campaign.quarantine_invalid_raw(raw,failed)==failed
 assert not raw.exists() and failed.read_text()=="[]\n"
 raw.write_text("not json\n")
 retry=campaign.quarantine_invalid_raw(raw,failed)
 assert retry.name=="01.attempt-002.json" and retry.read_text()=="not json\n"
 raw.write_text("{}\n")
 retry=campaign.quarantine_invalid_raw(raw,failed)
 assert retry.name=="01.attempt-003.json" and retry.read_text()=="{}\n"
with tempfile.TemporaryDirectory() as d:
 p=Path(d); base=p/"base"; candidate=p/"candidate"; allow=p/"allow.json"
 base.write_text("0: ireturn\n"); candidate.write_text(base.read_text()); allow.write_text('{"oldToNew":[],"allowedPatterns":[]}')
 gate=Path(__file__).with_name("bytecode_gate.py")
 assert subprocess.run(["python3",str(gate),str(base),str(candidate),"--allowlist",str(allow)],capture_output=True).returncode==0
 candidate.write_text("0: iconst_1\n1: ireturn\n")
 assert subprocess.run(["python3",str(gate),str(base),str(candidate),"--allowlist",str(allow)],capture_output=True).returncode==0
 candidate.write_text("0: new java/lang/Object\n")
 assert subprocess.run(["python3",str(gate),str(base),str(candidate),"--allowlist",str(allow)],capture_output=True).returncode!=0
 candidate.write_text("""  private int foldInt();
    Code:
       0: invokevirtual #1
       3: goto          12
       6: invokevirtual #2
       9: athrow
      12: ireturn
    Exception table:
       from    to  target type
           0     3     6   Class java/lang/Exception
""")
 base.write_text("""  private int foldInt();
    Code:
       0: invokevirtual #1
       3: ireturn
""")
 assert subprocess.run(["python3",str(gate),str(base),str(candidate),"--allowlist",str(allow),"--method","foldInt("],capture_output=True).returncode==0
with tempfile.TemporaryDirectory() as d:
 p=Path(d); baseline=p/"baseline"; candidate=p/"candidate"; baseline.mkdir(); candidate.mkdir()
 controlled={"BENCH_CPU_AFFINITY":"0","BENCH_POWER_MODE":"performance","BENCH_TURBO":"fixed",
             "BENCH_THERMAL_CEILING":"80C","BENCH_LOAD_CEILING":"0.25"}
 old={key:os.environ.get(key) for key in controlled}; os.environ.update(controlled)
 try:
  metadata={key:key for key in campaign.PAIR_KEYS}
  metadata["params"]={}
  host=dict(campaign.host_identity(),affinity="0",affinityActual="0",backgroundLoadCeiling="1000")
  identity=campaign.host_identity
  campaign.host_identity=lambda: dict(host)
  config={"comparison":"sync-regression","kind":"throughput","baselineCommit":"b","candidateCommit":"c",
   "baselineWorktree":str(baseline),"candidateWorktree":str(candidate),"harnessHash":"h","seed":7,"pairs":6,
   "warmupSeconds":1,"measurementSeconds":1,"warmupIterations":10,"measurementIterations":10,
   "host":host,
   "command":[sys.executable,"-c","import json,sys;json.dump([{{'primaryMetric':{{'score':100}},'secondaryMetrics':{{'gc.alloc.rate.norm':{{'score':1}}}}}}],open(sys.argv[1],'w'))","{output}"],
   "cells":[{"id":"cell","baseline":"b","candidate":"c","baselineMetadata":metadata,"candidateMetadata":dict(metadata)}]}
  config_path=p/"config.json"; config_path.write_text(json.dumps(config)); campaign.run_protocol(config_path,p/"protocol")
  assert json.loads((p/"protocol/final.json").read_text())["cell"]["verdict"]=="pass"
  campaign.checksum_tree(p/"protocol",True)
  initial=p/"resume"; campaign.run_campaign(config_path,initial)
  before={path:path.stat().st_mtime_ns for path in (initial/"raw").rglob("*.json")}
  campaign.run_campaign(config_path,initial)
  assert before=={path:path.stat().st_mtime_ns for path in (initial/"raw").rglob("*.json")}
  interrupted=initial/"raw/cell/baseline/01.json"
  failed=initial/"failed-raw/cell/baseline/01.json"; failed.parent.mkdir(parents=True)
  failed.write_text("[]\n"); interrupted.write_text("[]\n")
  campaign.run_campaign(config_path,initial)
  assert campaign.jmh_value(interrupted)==100
  assert failed.read_text()=="[]\n"
  assert (failed.parent/"01.attempt-002.json").read_text()=="[]\n"
 finally:
  campaign.host_identity=identity
  for key,value in old.items():
   if value is None: os.environ.pop(key,None)
   else: os.environ[key]=value
print("campaign self-tests: PASS")
