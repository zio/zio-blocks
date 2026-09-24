#!/usr/bin/env python3
"""Construct clean baseline worktrees and apply benchmark-only overlays."""
import argparse,hashlib,json,shutil,subprocess
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
BASELINES={"sync":"2b3896cc1e740a7929056abbc66fbb6119655492","async":"79dbe2b0c7f76bc35bd1899948e193f77e84e606"}
def tree_hash(root):
 return hashlib.sha256("".join(f"{p.relative_to(root)}:{hashlib.sha256(p.read_bytes()).hexdigest()}\n" for p in sorted(root.rglob("*")) if p.is_file()).encode()).hexdigest()
def files_hash(root,files):
 return hashlib.sha256("".join(f"{name}:{hashlib.sha256((root/name).read_bytes()).hexdigest()}\n" for name in files).encode()).hexdigest()
def main():
 p=argparse.ArgumentParser();p.add_argument("kind",choices=BASELINES);p.add_argument("destination");p.add_argument("--candidate",action="store_true");a=p.parse_args()
 dest=Path(a.destination).resolve()
 if dest.exists(): raise SystemExit("destination must not exist")
 revision="HEAD" if a.candidate else BASELINES[a.kind]
 subprocess.run(["git","worktree","add","--detach",str(dest),revision],cwd=ROOT,check=True)
 if a.candidate:
  patch=subprocess.run(["git","diff","--binary","HEAD"],cwd=ROOT,check=True,stdout=subprocess.PIPE).stdout
  if patch: subprocess.run(["git","apply","--binary","-"],cwd=dest,input=patch,check=True)
 production_before=tree_hash(dest/"streams")
 sync_names=["StreamEvalBench.scala","StreamPipelineBench.scala","StreamSetupBench.scala","StreamConcurrentBench.scala","StreamSyncMicroBench.scala"]
 async_names=["StreamAsyncParityBench.scala","StreamAsyncConcurrentParityBench.scala","StreamAsyncMicroBench.scala","StreamAsyncBenchmarkCorrectness.scala"]
 names=sync_names+(async_names if a.kind=="async" else [])
 source=ROOT/"streams-benchmark/src/main/scala/zio/blocks/streams/bench"; target=dest/source.relative_to(ROOT);target.mkdir(parents=True,exist_ok=True)
 for name in names: shutil.copy2(source/name,target/name)
 if tree_hash(dest/"streams")!=production_before: raise SystemExit("production tree changed by overlay")
 record={"kind":a.kind,"revision":revision,"productionHash":production_before,"overlayFiles":names,
         "syncOverlayHash":files_hash(target,sync_names),
         "asyncOverlayHash":files_hash(target,async_names) if a.kind=="async" else None,
         "overlayHash":files_hash(target,names)}
 (dest/"streams-benchmark-overlay.json").write_text(json.dumps(record,indent=2,sort_keys=True)+"\n")
 print(json.dumps(record,indent=2,sort_keys=True))
if __name__=="__main__":main()
