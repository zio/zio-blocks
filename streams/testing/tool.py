#!/usr/bin/env python3
"""Async test evidence accounting and isolated mutation runner."""
import argparse, difflib, hashlib, json, os, platform, re, shutil, subprocess, sys, tempfile, time, xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent

def load(path):
    with open(path, encoding="utf-8") as handle: return json.load(handle)

def load_replay(value):
    path = Path(value)
    return load(path) if path.is_file() else json.loads(value)

def digest_tree(root=ROOT):
    h = hashlib.sha256()
    ignored = {".git", "target", ".bsp", "node_modules"}
    for path in sorted(p for p in root.rglob("*") if p.is_file() and not ignored.intersection(p.parts)):
        rel = path.relative_to(root).as_posix().encode()
        h.update(len(rel).to_bytes(8, "big")); h.update(rel)
        data = path.read_bytes(); h.update(len(data).to_bytes(8, "big")); h.update(data)
    return h.hexdigest()

def validate_census(census, root=ROOT, evidence=None, final=False):
    errors, seen, obligations = [], set(), []
    required = {"public-api","node","reader","sink","pipeline","interpreter-op","resume","lane","platform","writer","foundational-async","pending-leaf","fair-selection"}
    categories = set()
    for entry in census.get("entries", []):
        ident = entry.get("id")
        if not ident or ident in seen: errors.append("duplicate or missing source id: %s" % ident)
        seen.add(ident); categories.add(entry.get("category"))
        if not (root / entry.get("source", "")).is_file(): errors.append("stale source: %s" % entry.get("source"))
        if not entry.get("profiles"): errors.append("missing profiles: %s" % ident)
        if not entry.get("obligations"): errors.append("missing obligations: %s" % ident)
        obligations.extend(entry.get("obligations", []))
        bench = entry.get("benchmark", "")
        if bench == "N/A" or (bench.startswith("N/A:") and not bench[4:].strip()): errors.append("unexplained N/A: %s" % ident)
    errors += ["missing category: " + c for c in sorted(required - categories)]
    operation_tags=[]
    interpreter_spec=(root/"streams/shared/src/test/scala/zio/blocks/streams/AsyncInterpreterSpec.scala").read_text()
    op_tag_source=(root/"streams/shared/src/main/scala/zio/blocks/streams/internal/OpTag.scala").read_text()
    for matrix in census.get("operationMatrices",[]):
        first,last=matrix.get("first",-1),matrix.get("last",-1)
        operation_tags.extend(range(first,last+1))
        if matrix.get("test","") not in interpreter_spec: errors.append("stale operation matrix test: "+str(matrix.get("id")))
    declared_tags=[int(value) for value in re.findall(r"final val [A-Z][A-Z0-9_]* = (\d+)",op_tag_source)]
    expected_tags=list(range(max(declared_tags)+1)) if declared_tags else []
    if operation_tags != expected_tags: errors.append("operation matrices must cover every declared tag exactly once in order")
    reserved = census.get("reservedIds", [])
    if len(reserved) != len(set(reserved)): errors.append("duplicate reserved id")
    errors += ["unreserved obligation: " + x for x in sorted(set(obligations) - set(reserved))]
    errors += ["stale reserved id: " + x for x in sorted(set(reserved) - set(obligations))]
    if final:
        executed = set((evidence or {}).get("executedTestIds", []))
        errors += ["unresolved executed evidence: " + x for x in sorted(set(reserved) - executed)]
    return errors

REPLAY_FIELDS = {"schema","id","seed","sampleOrdinal","shrinkPath","scenario","scala","platform","runtime","lane","kind","terminal","sink","faults","schedule","model","production","traceDiff"}
def validate_replay(value):
    missing = sorted(REPLAY_FIELDS - set(value)); errors = ["missing replay field: " + x for x in missing]
    if value.get("schema") != 1: errors.append("unsupported replay schema")
    if not isinstance(value.get("schedule"), list): errors.append("schedule must be an array")
    return errors

REQUIRED_CELLS = {"jvm-2.13.18", "jvm-3.3.7", "jvm-3.8.3", "js-2.13.18", "js-3.3.7"}
def validate_campaigns(value):
    errors=[]
    floors={
        "pr": {"finiteDepth":4,"graphDepth":8,"samplesPerCell":10000,"actors":4,"events":8,"preemptions":6},
        "nightly": {"graphDepth":12,"samplesPerCell":100000,"scheduleSeeds":1000,"actors":8,"events":20,"preemptions":16,"repeats":100},
        "soak": {"minimumHours":12,"minimumOperations":10000000,"watchdogSeconds":60},
    }
    if set(value.get("cells",())) != REQUIRED_CELLS: errors.append("campaign cells must exactly match supported cells")
    for name, requirements in floors.items():
        campaign=value.get(name,{})
        for field, floor in requirements.items():
            if campaign.get(field, -1) < floor: errors.append("%s.%s below immutable floor" % (name,field))
        if not campaign.get("tags"): errors.append("%s has no assigned tags" % name)
    return errors

def validate_replay_corpus(path=HERE/"replay-corpus"):
    errors=[]; ids=set()
    for bundle in sorted(path.glob("*.json")):
        value=load(bundle); errors += [bundle.name+": "+x for x in validate_replay(value)]
        if value.get("id") in ids: errors.append("duplicate replay id: "+str(value.get("id")))
        ids.add(value.get("id"))
    if not ids: errors.append("replay corpus is empty")
    return errors

def manifest(command, started, status, outputs):
    return {"schema":1,"candidateDigest":digest_tree(),"host":{"platform":platform.platform(),"python":sys.version.split()[0]},"command":command,"discovery":sorted(outputs),"started":started,"ended":time.time(),"exitStatus":status,"outputs":{str(p):hashlib.sha256(Path(p).read_bytes()).hexdigest() for p in outputs if Path(p).is_file()}}

DECL = re.compile(r"^(\s*)(?:(?:override|private(?:\[[^]]+\])?|protected(?:\[[^]]+\])?|final|sealed|abstract|implicit|lazy)\s+)*(def|class|object|trait)\s+([^\s[(=:]+)")
CANDIDATE = re.compile(r"(?i)async")

def scala_declarations(source, text):
    """Extract complete Scala declarations using signatures and balanced bodies."""
    lines=text.splitlines(); starts=[]
    for index,line in enumerate(lines):
        match=DECL.match(line)
        if match: starts.append((index,len(match.group(1)),match.group(2),match.group(3)))
    declarations=[]
    for ordinal,(start,indent,kind,name) in enumerate(starts):
        signature=[]; paren=bracket=0; body_line=None; body_char=None
        in_block=False
        for line_no in range(start,len(lines)):
            raw=lines[line_no]; cleaned=""; i=0; quote=None
            next_declaration=DECL.match(raw)
            if line_no>start and next_declaration and len(next_declaration.group(1))<=indent: break
            if line_no>start and raw.lstrip().startswith("}") and len(raw)-len(raw.lstrip())<indent: break
            while i < len(raw):
                if in_block:
                    end=raw.find("*/",i)
                    if end < 0: i=len(raw); continue
                    in_block=False; i=end+2; continue
                if quote:
                    if raw[i]=="\\": i+=2; continue
                    if raw[i]==quote: quote=None
                    i+=1; continue
                if raw.startswith("//",i): break
                if raw.startswith("/*",i): in_block=True; i+=2; continue
                if raw[i] in "\"'": quote=raw[i]; cleaned+=" "; i+=1; continue
                ch=raw[i]; cleaned+=ch
                if ch=="(": paren+=1
                elif ch==")": paren-=1
                elif ch=="[": bracket+=1
                elif ch=="]": bracket-=1
                elif paren==0 and bracket==0 and ch in "={" and not (ch=="=" and i+1<len(raw) and raw[i+1]==">"):
                    body_line=line_no; body_char=ch
                    if ch=="=":
                        remainder=raw[i+1:].lstrip()
                        if remainder.startswith("{"): body_char="{"
                    break
                i+=1
            signature.append(raw if body_line is None else raw[:i])
            if paren < 0 or bracket < 0: raise ValueError("unbalanced signature at %s:%d"%(source,start+1))
            if body_line is not None: break
            if line_no+1 >= len(lines): break
            next_match=DECL.match(lines[line_no+1])
            if next_match and len(next_match.group(1)) <= indent: break
        if paren or bracket: raise ValueError("unbalanced signature at %s:%d"%(source,start+1))
        if body_line is None:
            end=start
        elif body_char=="{":
            depth=0; began=False; in_block=False; quote=None; end=None
            for line_no in range(body_line,len(lines)):
                raw=lines[line_no]; i=0
                while i < len(raw):
                    if in_block:
                        stop=raw.find("*/",i)
                        if stop<0: break
                        in_block=False; i=stop+2; continue
                    if quote:
                        if raw[i]=="\\": i+=2; continue
                        if raw[i]==quote: quote=None
                        i+=1; continue
                    if raw.startswith("//",i): break
                    if raw.startswith("/*",i): in_block=True; i+=2; continue
                    if raw[i] in "\"'": quote=raw[i]; i+=1; continue
                    if raw[i]=="{": depth+=1; began=True
                    elif raw[i]=="}":
                        depth-=1
                        if depth<0: raise ValueError("unbalanced body at %s:%d"%(source,start+1))
                        if began and depth==0: end=line_no; break
                    i+=1
                if end is not None: break
            if end is None: raise ValueError("unbalanced body at %s:%d"%(source,start+1))
        else:
            # An unbraced RHS ends before the next sibling declaration. Nested
            # declarations cannot begin an unbraced declaration's continuation.
            end=len(lines)-1
            for sibling_start,sibling_indent,_,_ in starts[ordinal+1:]:
                if sibling_indent <= indent: end=sibling_start-1; break
            for line_no in range(body_line+1,end+1):
                stripped=lines[line_no].lstrip()
                if stripped.startswith("}") and len(lines[line_no])-len(stripped) < indent:
                    end=line_no-1; break
            while end>start and not lines[end].strip(): end-=1
        declarations.append({"source":source,"kind":kind,"name":name,"signature":"\n".join(signature),
                             "start":start+1,"end":end+1,"anchor":"\n".join(lines[start:end+1]),"hasBody":body_line is not None})
    return declarations

def selected_declarations(config, root=ROOT):
    selected=[]; classified_anchors={x.get("anchor") for x in config.get("classifications",[])}
    for spec in config.get("sources",[]):
        source=spec["source"]; path=root/source
        if not path.is_file(): raise ValueError("stale region source: "+source)
        declarations=scala_declarations(source,path.read_text(encoding="utf-8"))
        if spec.get("fullFile"):
            lines=path.read_text(encoding="utf-8").splitlines()
            selected.append({"source":source,"kind":"file","name":path.stem,"signature":lines[0] if lines else "",
                             "start":1,"end":len(lines),"anchor":"\n".join(lines)})
            continue
        helpers=set(spec.get("helpers",[])); mode=spec.get("mode")
        for declaration in declarations:
            name=declaration["name"]; signature=declaration["signature"]
            if not declaration["hasBody"] or name in spec.get("declarationOnly",[]) or signature in classified_anchors: continue
            broad=not spec.get("explicitOnly") and (
                CANDIDATE.search(name) or "Reader.AsyncReader" in signature or name in {"materializeAsync","appendAsync","runAsync"}
            )
            if mode=="writer" and not spec.get("explicitOnly"): broad=name=="deferred" or name.endswith("Async")
            if broad or name in helpers: selected.append(declaration)
    # Selecting a complete helper/class makes its nested declarations redundant.
    selected.sort(key=lambda x:(x["source"],x["start"],-x["end"]))
    result=[]
    for declaration in selected:
        if not any(x["source"]==declaration["source"] and x["start"]<=declaration["start"] and x["end"]>=declaration["end"] for x in result):
            result.append(declaration)
    return result

def coverage_regions(config, root=ROOT):
    """Resolve syntax-selected complete declaration bodies to line intervals."""
    if config.get("sources"):
        try: declarations=selected_declarations(config,root)
        except ValueError as error: return [],[str(error)]
        if not declarations: return [],["empty coverage declaration selection"]
        return [{**d,"id":"%s:%d:%s"%(d["source"],d["start"],d["name"]),
                 "rationale":"complete syntax-selected Scala declaration"} for d in declarations],[]
    errors, resolved, ids = [], [], set()
    for region in config.get("regions", []):
        ident, source = region.get("id"), region.get("source", "")
        if not ident or ident in ids: errors.append("duplicate or missing region id: %s" % ident)
        ids.add(ident)
        path = root/source
        if not path.is_file(): errors.append("stale region source: %s" % source); continue
        lines = path.read_text(encoding="utf-8").splitlines()
        def locate(field):
            anchor=region.get(field)
            found=[i+1 for i,line in enumerate(lines) if line == anchor] if anchor else []
            if len(found) != 1: errors.append("%s %s anchor is %s" % (ident,field,"missing" if not found else "ambiguous"))
            return found[0] if len(found)==1 else None
        start=locate("startAnchor")
        end=len(lines) if region.get("toEof") else locate("endAnchor")
        if start and end:
            if region.get("endExclusive"): end-=1
            if end < start: errors.append("empty or reversed region: "+str(ident))
            else: resolved.append({**region,"start":start,"end":end})
        if not region.get("rationale"): errors.append("missing region rationale: "+str(ident))
    by_source={}
    for region in resolved: by_source.setdefault(region["source"],[]).append(region)
    for source, regions in by_source.items():
        ordered=sorted(regions,key=lambda x:x["start"])
        for left,right in zip(ordered,ordered[1:]):
            if right["start"] <= left["end"]: errors.append("overlapping regions: %s and %s" % (left["id"],right["id"]))
    return resolved, errors

def async_candidates(config, root=ROOT):
    candidates=[]
    sources={r["source"] for r in config.get("regions",[])} | {s["source"] for s in config.get("sources",[])}
    for source in sorted(sources):
        path=root/source
        if not path.is_file(): continue
        try: declarations=scala_declarations(source,path.read_text(encoding="utf-8"))
        except ValueError: continue
        for declaration in declarations:
            # Independent census: inspect complete signatures, not selection rules.
            if declaration["hasBody"] and (CANDIDATE.search(declaration["name"]) or
                    "Reader.AsyncReader" in declaration["signature"] or
                    declaration["name"] in {"materializeAsync","appendAsync","runAsync"}):
                candidates.append((source,declaration["start"],declaration["signature"],declaration["name"]))
    return candidates

def check_coverage(report, config, root=ROOT):
    regions, errors=coverage_regions(config,root)
    classifications=config.get("classifications",[]); classified=set(); classified_records=set()
    for item in classifications:
        source=item.get("source",""); path=root/source; anchor=item.get("anchor")
        lines=path.read_text(encoding="utf-8").splitlines() if path.is_file() else []
        found=[i+1 for i,x in enumerate(lines) if x == anchor]
        kind=item.get("classification")
        if kind=="unreachable-scoverage-record":
            record=item.get("record",{}); fields=("class","method","line","start","end","branch")
            if set(record) != set(fields) or not isinstance(record.get("class"),str) or not isinstance(record.get("method"),str) or \
                    any(not isinstance(record.get(field),int) for field in ("line","start","end")) or not isinstance(record.get("branch"),bool):
                errors.append("invalid exact record classification: "+str(item.get("id")))
                continue
            if not isinstance(item.get("proof"),str) or not item["proof"].strip():
                errors.append("exact record classification lacks an invariant proof: "+str(item.get("id")))
            distances=[abs(line-record["line"]) for line in found]
            nearest=min(distances) if distances else None
            if nearest is None or nearest > 32 or distances.count(nearest) != 1:
                errors.append("exact record anchor line mismatch: "+str(item.get("id")))
            key=(source,record["class"],record["method"],record["line"],record["start"],record["end"],record["branch"])
            matches=[statement for statement in report.get("statements",[]) if
                (statement.get("source"),statement.get("class"),statement.get("method"),statement.get("line"),
                 statement.get("start"),statement.get("end"),statement.get("branch")) == key]
            if len(matches)!=1 or (len(matches)==1 and matches[0].get("hit") != 0):
                errors.append("exact coverage record is stale, covered, or ambiguous: "+str(item.get("id")))
            if key in classified_records: errors.append("duplicate exact record classification: "+str(item.get("id")))
            classified_records.add(key)
        elif len(found)!=1:
            errors.append("classification anchor is stale or ambiguous: "+str(item.get("id")))
        elif kind!="non-production-test-hook" or "ForTest" not in (anchor or ""):
            errors.append("invalid broad classification: "+str(item.get("id")))
        elif len(found)==1:
            try: declarations=scala_declarations(source,path.read_text(encoding="utf-8"))
            except ValueError as error: errors.append(str(error)); declarations=[]
            declaration=next((d for d in declarations if d["start"]==found[0] and "ForTest" in d["name"]),None)
            if declaration is None: errors.append("classification is not an exact test-hook declaration: "+str(item.get("id")))
            else: classified.update((source,line) for line in range(declaration["start"],declaration["end"]+1))
    for group in config.get("recordClassifications",[]):
        ident=group.get("id"); source=group.get("source","")
        if not isinstance(group.get("rationale"),str) or not group["rationale"].strip() or \
                not isinstance(group.get("proof"),str) or not group["proof"].strip():
            errors.append("exact record group lacks rationale or proof: "+str(ident))
        records=group.get("records",[])
        if not records: errors.append("empty exact record group: "+str(ident))
        for index,record in enumerate(records):
            if not (isinstance(record,list) and len(record)==6 and isinstance(record[0],str) and
                    isinstance(record[1],str) and all(isinstance(value,int) for value in record[2:5]) and
                    isinstance(record[5],bool)):
                errors.append("invalid grouped exact record: %s[%d]"%(ident,index)); continue
            key=(source,record[0],record[1],record[2],record[3],record[4],record[5])
            matches=[statement for statement in report.get("statements",[]) if
                (statement.get("source"),statement.get("class"),statement.get("method"),statement.get("line"),
                 statement.get("start"),statement.get("end"),statement.get("branch")) == key]
            if len(matches)!=1 or (len(matches)==1 and matches[0].get("hit") != 0):
                errors.append("grouped exact coverage record is stale, covered, or ambiguous: %s[%d]"%(ident,index))
            if key in classified_records: errors.append("duplicate exact record classification: %s[%d]"%(ident,index))
            classified_records.add(key)
    statements=report.get("statements",[])
    statement_minimum=config.get("statementMinimum")
    branch_minimum=config.get("branchMinimum")
    if statement_minimum is not None and statements:
        percentage=100.0*sum(s["hit"] for s in statements)/sum(s["count"] for s in statements)
        if percentage < statement_minimum: errors.append("statement coverage %.2f below minimum %.2f"%(percentage,statement_minimum))
    branches=[s for s in statements if s["branch"]]
    if branch_minimum is not None and branches:
        percentage=100.0*sum(s["hit"] for s in branches)/sum(s["count"] for s in branches)
        if percentage < branch_minimum: errors.append("branch coverage %.2f below minimum %.2f"%(percentage,branch_minimum))
    if config.get("enforceCandidateMapping",True):
        classified_names={(s["source"],name) for s in config.get("sources",[]) for name in s.get("declarationOnly",[])}
        for source,line,text,name in async_candidates(config,root):
            mapped=any(r["source"]==source and r["start"]<=line<=r["end"] for r in regions)
            if not mapped and (source,line) not in classified and (source,name) not in classified_names:
                errors.append("unmapped async candidate: %s:%d: %s" % (source,line,name))
    for region in regions:
        in_region=[s for s in statements if s.get("source")==region["source"] and region["start"]<=s.get("line",0)<=region["end"]]
        if not in_region: errors.append("empty coverage region: "+region["id"]); continue
        selected=[s for s in in_region if
            (s.get("source"),s.get("line")) not in classified and
            (s.get("source"),s.get("class"),s.get("method"),s.get("line"),s.get("start"),s.get("end"),s.get("branch")) not in classified_records]
        for statement in selected:
            if statement["hit"] != statement["count"] or (statement["branch"] and statement["hit"] != statement["count"]):
                kind="branch" if statement["branch"] else "statement"
                errors.append("uncovered %s %s class=%s method=%s line=%s span=%s-%s hit=%s/%s" %
                    (kind,statement["source"],statement["class"],statement["method"],statement["line"],statement["start"],statement["end"],statement["hit"],statement["count"]))
    return errors

def load_coverage(path):
    path=Path(path)
    if path.suffix.lower() != ".xml": return load(path)
    statements=[]
    for statement in ET.parse(path).getroot().findall(".//statement"):
        if statement.get("ignored") == "true": continue
        source=Path(statement.get("source","")).resolve()
        try: relative=source.relative_to(ROOT).as_posix()
        except ValueError: continue
        count=1; hit=int(int(statement.get("invocation-count","0")) > 0)
        statements.append({"source":relative,"class":statement.get("class","") ,"method":statement.get("method",""),
            "line":int(statement.get("line","0")),"start":int(statement.get("start","0")),"end":int(statement.get("end","0")),
            "hit":hit,"count":count,"branch":statement.get("branch")=="true"})
    return {"statements":statements}

def run_mutations(corpus, command, out):
    out=Path(out); artifact_root=out.parent/"mutations"; artifact_root.mkdir(parents=True,exist_ok=True)
    baseline=subprocess.run(command,cwd=ROOT,shell=True,text=True,capture_output=True)
    if baseline.returncode:
        out.write_text(json.dumps({"schema":1,"candidateDigest":digest_tree(),"baseline":{"exitStatus":baseline.returncode},"results":[]},indent=2)+"\n")
        return 1
    results=[]
    for mutant in corpus["mutations"]:
        source=ROOT / mutant["target"]; text=source.read_text()
        if text.count(mutant["anchor"]) != 1: results.append({"id":mutant["id"],"valid":False,"reason":"anchor count"}); continue
        with tempfile.TemporaryDirectory(prefix="async-mutant-") as td:
            work=Path(td)/"worktree"
            shutil.copytree(ROOT,work,ignore=shutil.ignore_patterns(".git","target",".bsp","node_modules","__pycache__"))
            subprocess.run(["git","init","-q"],cwd=work,check=True)
            subprocess.run(["git","add","-A"],cwd=work,check=True)
            subprocess.run(
                ["git","-c","user.name=Async Mutation Runner","-c","user.email=mutation@invalid","commit","-qm","isolated mutation fixture"],
                cwd=work,check=True
            )
            copy=work/mutant["target"]; changed=text.replace(mutant["anchor"],mutant["replacement"],1); copy.write_text(changed)
            artifact=artifact_root/mutant["id"]; artifact.mkdir(exist_ok=True)
            diff="".join(difflib.unified_diff(text.splitlines(True),changed.splitlines(True),fromfile=mutant["target"],tofile=mutant["target"]))
            (artifact/"mutation.diff").write_text(diff)
            mutant_command=mutant.get("command",command)
            proc=subprocess.run(mutant_command, cwd=work, shell=True, text=True, capture_output=True)
            output=proc.stdout+proc.stderr; (artifact/"output.log").write_text(output)
            expected=mutant["killingTests"]
            compiled=("compilation failed" not in output.lower() and "project loading failed" not in output.lower())
            valid=proc.returncode != 0 and compiled and all(test in output for test in expected)
            results.append({"id":mutant["id"],"valid":valid,"compiled":compiled,"exitStatus":proc.returncode,"command":mutant_command,"expected":expected,"diffHash":hashlib.sha256(diff.encode()).hexdigest(),"outputHash":hashlib.sha256(output.encode()).hexdigest()})
    out.write_text(json.dumps({"schema":1,"candidateDigest":digest_tree(),"baseline":{"exitStatus":0},"results":results},indent=2)+"\n")
    return 0 if all(x.get("valid") for x in results) else 1

def main(argv=None):
    parser=argparse.ArgumentParser(); sub=parser.add_subparsers(dest="cmd",required=True)
    p=sub.add_parser("inventory"); p.add_argument("--evidence"); p.add_argument("--final",action="store_true")
    p=sub.add_parser("replay"); p.add_argument("bundle")
    p=sub.add_parser("campaign"); p.add_argument("mode",choices=("pr","nightly","soak"))
    sub.add_parser("check-campaigns")
    sub.add_parser("check-replays")
    p=sub.add_parser("coverage"); p.add_argument("report")
    p=sub.add_parser("mutation"); p.add_argument("--command",required=True); p.add_argument("--out",default="target/async-mutation-evidence.json")
    p=sub.add_parser("evidence"); p.add_argument("--command",required=True); p.add_argument("outputs",nargs="*"); p.add_argument("--out",required=True)
    args=parser.parse_args(argv)
    if args.cmd=="inventory": errors=validate_census(load(HERE/"census.json"), evidence=load(args.evidence) if args.evidence else None, final=args.final)
    elif args.cmd=="replay":
        value=load_replay(args.bundle); errors=validate_replay(value)
        if not errors: print(json.dumps(value,sort_keys=True,separators=(",",":")))
    elif args.cmd=="campaign": print(json.dumps(load(HERE/"campaigns.json")[args.mode],sort_keys=True)); errors=[]
    elif args.cmd=="check-campaigns": errors=validate_campaigns(load(HERE/"campaigns.json"))
    elif args.cmd=="check-replays": errors=validate_replay_corpus()
    elif args.cmd=="coverage": errors=check_coverage(load_coverage(args.report),load(HERE/"critical-coverage.json"))
    elif args.cmd=="mutation": return run_mutations(load(HERE/"mutations.json"),args.command,args.out)
    else:
        started=time.time(); proc=subprocess.run(args.command,cwd=ROOT,shell=True); Path(args.out).write_text(json.dumps(manifest(args.command,started,proc.returncode,args.outputs),indent=2)+"\n"); return proc.returncode
    if errors: print("\n".join(errors),file=sys.stderr); return 1
    return 0
if __name__ == "__main__": raise SystemExit(main())
