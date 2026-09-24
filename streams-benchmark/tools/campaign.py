#!/usr/bin/env python3
"""Reproducible streams AB/BA campaign runner (stdlib only).

The runner launches one JMH fork per process, preserving its JSON verbatim.  It
never compares unlike rows and never uses JMH output as correctness evidence.
"""
from __future__ import annotations
import argparse, csv, hashlib, json, math, os, platform, random, re, shutil, statistics, subprocess, sys, time
from pathlib import Path

T95={1:6.314,2:2.920,3:2.353,4:2.132,5:2.015,6:1.943,7:1.895,8:1.860,9:1.833}
PAIR_KEYS=("benchmark","params","mode","warmup","measurement","jvmArgs","scala","jdk","harnessHash","work","checksum","boundary")

def bound(xs, lower):
    if len(xs)<2: raise ValueError("at least two paired forks required")
    mean=statistics.mean(xs); sd=statistics.stdev(xs)
    margin=0 if sd==0 else T95[len(xs)-1]*sd/math.sqrt(len(xs))
    return mean-margin if lower else mean+margin

def ratio_verdict(base,cand,threshold=.95):
    if len(base)!=len(cand) or not base: raise ValueError("pair length mismatch")
    if any(x<=0 for x in base+cand): raise ValueError("ratios require positive samples")
    ds=[math.log(c/b) for b,c in zip(base,cand)]
    lo,hi=math.exp(bound(ds,True)),math.exp(bound(ds,False))
    return {"pairs":len(ds),"lower":lo,"upper":hi,"threshold":threshold,
            "verdict":"pass" if lo>=threshold else "regression" if hi<threshold else "inconclusive"}

def allocation_verdict(base,cand):
    if len(base)!=len(cand) or not base: raise ValueError("pair length mismatch")
    bu=bound(base,False)
    if bu<=1:
        upper=bound([c-b for b,c in zip(base,cand)],False)
        return {"nearZero":True,"baselineUpper":bu,"upperIncrease":upper,"limit":8,
                "verdict":"pass" if upper<=8 else "regression"}
    r=ratio_verdict(base,cand,1.05)
    r.update(nearZero=False,baselineUpper=bu)
    # Allocation is an upper-bound gate, opposite throughput direction.
    r["verdict"]="pass" if r["upper"]<=1.05 else "regression" if r["lower"]>1.05 else "inconclusive"
    return r

def final_verdict(initial,extended=None,replacement=None):
    history=[initial]; result=initial
    if result["verdict"]=="inconclusive" and extended is not None: result=extended; history.append(result)
    if result["verdict"]=="inconclusive" and replacement is not None: result=replacement; history.append(result)
    result=dict(result)
    if result["verdict"]=="inconclusive": result["verdict"]="fail"
    result["history"]=history
    return result

def slope(values):
    values={int(k):float(v) for k,v in values.items()}
    if set(values)!={0,1,1000}: raise ValueError("allocation slope requires N=0,1,1000")
    return {"fixed":values[0],"bytesPerElement":(values[1000]-values[1])/999}

def paired_slope(samples):
    """Regress every fork independently, then apply paired confidence bounds."""
    required={0,1,1000}
    normalized={side:{int(n):list(xs) for n,xs in rows.items()} for side,rows in samples.items()}
    if set(normalized)!={"baseline","candidate"} or any(set(rows)!=required for rows in normalized.values()):
        raise ValueError("paired allocation slope requires baseline/candidate N=0,1,1000")
    lengths={len(xs) for rows in normalized.values() for xs in rows.values()}
    if len(lengths)!=1 or next(iter(lengths))<2: raise ValueError("allocation slope fork counts must match")
    per_side={}
    for side,rows in normalized.items():
        per_side[side]=[slope({n:rows[n][i] for n in required}) for i in range(next(iter(lengths)))]
    fixed=[x["fixed"] for x in per_side["candidate"]]
    slopes=[x["bytesPerElement"] for x in per_side["candidate"]]
    fixed_delta=[c["fixed"]-b["fixed"] for b,c in zip(per_side["baseline"],per_side["candidate"])]
    slope_delta=[c["bytesPerElement"]-b["bytesPerElement"] for b,c in zip(per_side["baseline"],per_side["candidate"])]
    return {"perFork":per_side,"candidateFixedUpper":bound(fixed,False),
            "candidateSlopeUpper":bound(slopes,False),"fixedIncreaseUpper":bound(fixed_delta,False),
            "slopeIncreaseUpper":bound(slope_delta,False)}

def slope_verdict(samples,budget):
    result=paired_slope(samples)
    fixed_limit=float(budget["fixedBytes"]); slope_limit=float(budget["bytesPerElement"])
    result.update(fixedLimit=fixed_limit,slopeLimit=slope_limit)
    result["verdict"]="pass" if result["candidateFixedUpper"]<=fixed_limit and result["candidateSlopeUpper"]<=slope_limit else "regression"
    return result

def validate_pair(a,b):
    bad={k:[a.get(k),b.get(k)] for k in PAIR_KEYS if a.get(k)!=b.get(k)}
    if bad: raise ValueError("pair metadata mismatch: "+json.dumps(bad,sort_keys=True))

def validate_config(cfg):
    if cfg.get("comparison") not in ("sync-regression","async-regression","diagnostic-ratio"):
        raise ValueError("invalid comparison domain")
    if cfg.get("kind") not in ("throughput","allocation"): raise ValueError("invalid campaign kind")
    if cfg.get("pairs") not in (6,10): raise ValueError("campaign pairs must be 6 or 10")
    if cfg.get("pairs")==10 and cfg.get("measurementSeconds") not in (1,3): raise ValueError("invalid replacement timing")
    if cfg.get("pairs")==6 and cfg.get("measurementSeconds")!=1: raise ValueError("initial campaign must use one-second measurements")
    if cfg.get("warmupIterations")!=10 or cfg.get("measurementIterations")!=10: raise ValueError("protocol requires ten iterations")
    seen=set()
    for cell in cfg.get("cells",[]):
        if cell["id"] in seen: raise ValueError("duplicate campaign cell: "+cell["id"])
        seen.add(cell["id"])
        validate_pair(cell["baselineMetadata"],cell["candidateMetadata"])

def host_identity():
    def cmd(*a):
        try:return subprocess.check_output(a,text=True,stderr=subprocess.DEVNULL).strip()
        except Exception:return "unavailable"
    java=os.environ.get("JAVA_HOME","")
    java_bin=Path(java)/"bin/java" if java else Path(shutil.which("java") or "")
    affinity_actual=",".join(str(cpu) for cpu in sorted(os.sched_getaffinity(0))) if hasattr(os,"sched_getaffinity") else "unavailable"
    return {"machine":platform.node(),"os":platform.platform(),"kernel":platform.release(),
      "cpu":cmd("sysctl","-n","machdep.cpu.brand_string"),"cores":os.cpu_count(),
      "java":cmd(str(java_bin),"-version") if java_bin else "unavailable",
      "javaSha256":hashlib.sha256(java_bin.read_bytes()).hexdigest() if java_bin.is_file() else "unavailable",
      "affinity":os.environ.get("BENCH_CPU_AFFINITY","unset"),"affinityActual":affinity_actual,
      "powerMode":os.environ.get("BENCH_POWER_MODE","unset"),
      "turbo":os.environ.get("BENCH_TURBO","unset"),"thermalCeiling":os.environ.get("BENCH_THERMAL_CEILING","unset"),
      "backgroundLoadCeiling":os.environ.get("BENCH_LOAD_CEILING","unset")}

def validate_host(frozen,current=None):
    current=current or host_identity(); bad={k:[v,current.get(k)] for k,v in frozen.items() if current.get(k)!=v}
    if bad: raise RuntimeError("host block invalid: "+json.dumps(bad,sort_keys=True))

def validate_controlled_host(host):
    required=("affinity","powerMode","turbo","thermalCeiling","backgroundLoadCeiling")
    missing=[k for k in required if host.get(k) in (None,"","unset","unavailable")]
    if missing:
        raise RuntimeError("controlled-host predicate is not configured: "+", ".join(missing))
    configured={int(part) for group in host["affinity"].split(",") for part in (range(*map(int,group.split("-")[:1]+[str(int(group.split("-")[-1])+1)])) if "-" in group else [int(group)])}
    actual={int(cpu) for cpu in host.get("affinityActual","").split(",") if cpu}
    if configured!=actual:
        raise RuntimeError(f"configured CPU affinity {sorted(configured)} is not enforced; actual={sorted(actual)}")
    ceiling=float(host["backgroundLoadCeiling"])
    load=os.getloadavg()[0]/max(1,len(actual))
    if load>ceiling:
        raise RuntimeError(f"background load per assigned CPU {load:.3f} exceeds ceiling {ceiling:.3f}")

def jmh_value(path,allocation=False):
    data=json.loads(Path(path).read_text())
    if len(data)!=1: raise ValueError(f"expected one JMH row in {path}")
    row=data[0]; metric=row["secondaryMetrics"]["gc.alloc.rate.norm"] if allocation else row["primaryMetric"]
    return float(metric["score"])

def exact_benchmark_pattern(benchmark):
    """Select exactly one JMH benchmark; JMH include arguments are regular expressions."""
    return "^"+re.escape(benchmark)+"$"

def quarantine_invalid_raw(dest,failed):
    """Move an invalid result aside without overwriting an earlier failed attempt."""
    dest=Path(dest); failed=Path(failed); failed.parent.mkdir(parents=True,exist_ok=True)
    target=failed
    attempt=2
    while target.exists():
        target=failed.with_name(f"{failed.stem}.attempt-{attempt:03d}{failed.suffix}")
        attempt+=1
    dest.replace(target)
    return target

def order(seed,pairs):
    rng=random.Random(seed); out=[]
    for i in range(pairs):
        sides=["baseline","candidate"]; rng.shuffle(sides)
        out.append({"block":i+1,"order":sides})
    return out

def checksum_tree(root,verify=False):
    root=Path(root); checksum=root/"SHA256SUMS"
    if verify:
        for line in checksum.read_text().splitlines():
            digest,name=line.split("  ",1)
            if name=="SHA256SUMS" or hashlib.sha256((root/name).read_bytes()).hexdigest()!=digest: raise ValueError("checksum mismatch: "+name)
        return
    files=sorted(p for p in root.rglob("*") if p.is_file() and p!=checksum)
    checksum.write_text("".join(f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.relative_to(root)}\n" for p in files))

def run_campaign(config_path,out):
    cfg=json.loads(Path(config_path).read_text()); validate_config(cfg); out=Path(out)
    frozen=cfg["host"]; validate_controlled_host(frozen); validate_host(frozen)
    schedule=order(cfg["seed"],cfg.get("pairs",6))
    if out.exists():
      manifest_path=out/"manifest.json"
      if not manifest_path.is_file(): raise ValueError(f"cannot resume campaign without manifest: {out}")
      manifest=json.loads(manifest_path.read_text())
      expected=dict(cfg,runOrder=schedule)
      for key,value in expected.items():
        if manifest.get(key)!=value: raise ValueError(f"resume campaign mismatch for {key}")
      (out/"raw").mkdir(exist_ok=True)
    else:
      out.mkdir(parents=True); (out/"raw").mkdir()
      manifest=dict(cfg,runOrder=schedule,startedUtc=time.strftime("%Y-%m-%dT%H:%M:%SZ",time.gmtime()))
      (out/"manifest.json").write_text(json.dumps(manifest,indent=2,sort_keys=True)+"\n")
    run_cells(cfg,out,schedule)
    analyze(out); checksum_tree(out)

def run_cells(cfg,out,schedule):
    out=Path(out); frozen=cfg["host"]
    for cell in cfg["cells"]:
      for block in schedule:
       validate_host(frozen)
       for side in block["order"]:
        dest=out/"raw"/cell["id"]/side/f'{block["block"]:02d}.json'; dest.parent.mkdir(parents=True,exist_ok=True)
        if dest.is_file():
          try:
            jmh_value(dest,cfg["kind"]=="allocation")
            continue
          except (ValueError, KeyError, json.JSONDecodeError):
            failed=out/"failed-raw"/cell["id"]/side/f'{block["block"]:02d}.json'
            quarantine_invalid_raw(dest,failed)
        params=cell[side+"Metadata"].get("params",{})
        if any(isinstance(value,(list,dict)) for value in params.values()):
          raise ValueError("campaign cells require one scalar value per parameter")
        param_args=" ".join(f"-p {key}={value}" for key,value in sorted(params.items()))
        values={"benchmark":exact_benchmark_pattern(cell[side]),"output":str(dest),"fork":"1","params":param_args,
          "warmupTime":str(cfg["warmupSeconds"]),"measurementTime":str(cfg["measurementSeconds"])}
        command=[]
        for part in cfg["command"]:
          rendered=part.format(**values)
          command.extend(rendered.split() if part=="{params}" else [rendered])
        subprocess.run(command,cwd=cfg[side+"Worktree"],check=True)
        try:
          jmh_value(dest,cfg["kind"]=="allocation")
        except (ValueError, KeyError, json.JSONDecodeError) as error:
          failed=out/"failed-raw"/cell["id"]/side/f'{block["block"]:02d}.json'
          archived=quarantine_invalid_raw(dest,failed)
          raise ValueError(f"JMH produced invalid raw result; preserved as {archived}") from error
      validate_host(frozen)

def inconclusive(root):
    analysis=json.loads((Path(root)/"analysis.json").read_text())
    return {cell for cell,result in analysis.items() if result["verdict"]=="inconclusive"}

def extend_campaign(config_path,initial,out):
    """Add exactly four pairs to the initial six-pair sample pool."""
    cfg=json.loads(Path(config_path).read_text()); validate_config(cfg)
    initial=Path(initial); checksum_tree(initial,True)
    unresolved=inconclusive(initial); out=Path(out); out.mkdir(parents=True,exist_ok=False); (out/"raw").mkdir()
    cfg=dict(cfg,pairs=10,cells=[cell for cell in cfg["cells"] if cell["id"] in unresolved])
    validate_config(cfg)
    for cell in cfg["cells"]:
      shutil.copytree(initial/"raw"/cell["id"],out/"raw"/cell["id"])
    additional=order(cfg["seed"],10)[6:]
    manifest=dict(cfg,runOrder=order(cfg["seed"],6)+additional,
      initialArtifact=str(initial.resolve()),initialSha256=hashlib.sha256((initial/"SHA256SUMS").read_bytes()).hexdigest(),
      startedUtc=time.strftime("%Y-%m-%dT%H:%M:%SZ",time.gmtime()))
    (out/"manifest.json").write_text(json.dumps(manifest,indent=2,sort_keys=True)+"\n")
    run_cells(cfg,out,additional)
    analyze(out); checksum_tree(out)

def run_protocol(config_path,out):
    """Execute the immutable 6→10→replacement escalation without pooling forks."""
    out=Path(out)
    if (out/"final.json").is_file():
      checksum_tree(out,True)
      final=json.loads((out/"final.json").read_text())
      if any(result["verdict"]!="pass" for result in final.values()):
        raise SystemExit("completed campaign protocol did not pass every cell")
      return
    out.mkdir(parents=True,exist_ok=True)
    initial=out/"initial"; run_campaign(config_path,initial)
    extended=None; replacement=None
    if inconclusive(initial):
      extended=out/"extended"; extend_campaign(config_path,initial,extended)
    unresolved=inconclusive(extended or initial)
    if unresolved:
      base=json.loads(Path(config_path).read_text())
      replacement_cfg=dict(base,pairs=10,warmupSeconds=3,measurementSeconds=3,
        cells=[cell for cell in base["cells"] if cell["id"] in unresolved],seed=base["seed"]+1)
      config_out=out/"replacement-config.json"
      config_out.write_text(json.dumps(replacement_cfg,indent=2,sort_keys=True)+"\n")
      replacement=out/"replacement"; run_campaign(config_out,replacement)
    final=finalize_campaign(initial,extended,replacement)
    slope_groups=json.loads(Path(config_path).read_text()).get("slopeGroups",{})
    if slope_groups:
      analyses=[json.loads((root/"analysis.json").read_text()) for root in (initial,extended,replacement) if root]
      for group,spec in slope_groups.items():
        cells=spec["cells"]
        samples={side:{} for side in ("baseline","candidate")}
        for n,cell in cells.items():
          selected=None
          for analysis in reversed(analyses):
            if cell in analysis and analysis[cell]["verdict"]!="inconclusive": selected=analysis[cell]; break
          if selected is None: selected=next(analysis[cell] for analysis in reversed(analyses) if cell in analysis)
          for side in samples: samples[side][int(n)]=selected["samples"][side]
        final["slope:"+group]=slope_verdict(samples,spec["budget"])
    (out/"final.json").write_text(json.dumps(final,indent=2,sort_keys=True)+"\n")
    checksum_tree(out)
    if any(result["verdict"]!="pass" for result in final.values()):
      raise SystemExit("campaign protocol did not pass every cell")

def analyze(root):
    root=Path(root); m=json.loads((root/"manifest.json").read_text()); analysis={}
    for cell in m["cells"]:
      vals={s:[jmh_value(p,m["kind"]=="allocation") for p in sorted((root/"raw"/cell["id"]/s).glob("*.json"))] for s in ("baseline","candidate")}
      result=allocation_verdict(vals["baseline"],vals["candidate"]) if m["kind"]=="allocation" else ratio_verdict(vals["baseline"],vals["candidate"])
      analysis[cell["id"]]={"samples":vals,**result}
    (root/"analysis.json").write_text(json.dumps(analysis,indent=2,sort_keys=True)+"\n")
    with (root/"summary.csv").open("w",newline="") as f:
      w=csv.writer(f); w.writerow(["cell","comparison","verdict","lower","upper","pairs"])
      for k,v in analysis.items(): w.writerow([k,m["comparison"],v["verdict"],v.get("lower",""),v.get("upper",v.get("upperIncrease","")),v.get("pairs",len(v["samples"]["baseline"]))])
    lines=["# Campaign summary","",f'Comparison: **{m["comparison"]}** ({"binding regression gate" if m["comparison"] in ("sync-regression","async-regression") else "diagnostic ratio"}).',"","| Cell | Verdict | Lower | Upper |","|---|---:|---:|---:|"]
    lines += [f'| `{k}` | {v["verdict"]} | {v.get("lower","")} | {v.get("upper",v.get("upperIncrease",""))} |' for k,v in analysis.items()]
    (root/"summary.md").write_text("\n".join(lines)+"\n")

def finalize_campaign(initial,extended=None,replacement=None):
    roots=[Path(initial)]+([Path(extended)] if extended else [])+([Path(replacement)] if replacement else [])
    manifests=[json.loads((r/"manifest.json").read_text()) for r in roots]
    signature=lambda m:(m["comparison"],m["kind"],m["baselineCommit"],m["candidateCommit"],m["harnessHash"])
    if any(signature(m)!=signature(manifests[0]) for m in manifests[1:]): raise ValueError("campaign identity mismatch")
    analyses=[json.loads((r/"analysis.json").read_text()) for r in roots]
    final={}
    for cell,first in analyses[0].items():
        more=analyses[1].get(cell) if len(analyses)>1 else None
        repl=analyses[2].get(cell) if len(analyses)>2 else None
        final[cell]=final_verdict(first,more,repl)
    return final

def main():
    p=argparse.ArgumentParser(); sub=p.add_subparsers(dest="cmd",required=True)
    for name in ("hash","verify-hashes","analyze"): q=sub.add_parser(name); q.add_argument("path")
    q=sub.add_parser("finalize"); q.add_argument("initial"); q.add_argument("--extended"); q.add_argument("--replacement"); q.add_argument("--output",required=True)
    q=sub.add_parser("freeze-host"); q.add_argument("path")
    q=sub.add_parser("check-host"); q.add_argument("path")
    q=sub.add_parser("run"); q.add_argument("config"); q.add_argument("output")
    q=sub.add_parser("protocol"); q.add_argument("config"); q.add_argument("output")
    ns=p.parse_args()
    if ns.cmd=="freeze-host": Path(ns.path).write_text(json.dumps(host_identity(),indent=2,sort_keys=True)+"\n")
    elif ns.cmd=="check-host":
        host=json.loads(Path(ns.path).read_text()); validate_controlled_host(host); validate_host(host); print("host: PASS")
    elif ns.cmd=="hash": checksum_tree(ns.path)
    elif ns.cmd=="verify-hashes": checksum_tree(ns.path,True); print("hashes: PASS")
    elif ns.cmd=="analyze": analyze(ns.path)
    elif ns.cmd=="finalize": Path(ns.output).write_text(json.dumps(finalize_campaign(ns.initial,ns.extended,ns.replacement),indent=2,sort_keys=True)+"\n")
    elif ns.cmd=="run": run_campaign(ns.config,ns.output)
    else: run_protocol(ns.config,ns.output)
if __name__=="__main__": main()
