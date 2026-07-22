import React, { useState, useCallback, useRef } from "react";

const CAP  = 4;
const MASK = CAP - 1;

const COLOR = {
  write:   "#1D9E75",
  read:    "#378ADD",
  fail:    "#E24B4A",
  warn:    "#BA7517",
  neutral: "#888780",
};

function initState() {
  return {
    buf:  Array(CAP).fill(null),
    seq:  Array.from({ length: CAP }, (_, i) => i),
    pIdx: 0,
    cIdx: 0,
  };
}

// ── Prose step summary ────────────────────────────────────────────────────────

function stepDescription(calc) {
  if (!calc) return null;

  if (calc.op === "offer") {
    if (calc.diff === 0) {
      return {
        color: COLOR.write,
        paragraphs: [
          `The producer reads pIdx = ${calc.pIdx} and computes the target slot as ` +
          `pIdx & mask = ${calc.pIdx} & ${MASK} = ${calc.slot}. ` +
          `It reads the sequence stamp seqBuf[${calc.slot}] = ${calc.seq}. ` +
          `The difference seq − pIdx = ${calc.seq} − ${calc.pIdx} = 0 means the stamp ` +
          `exactly matches the producer counter — this slot has been fully consumed and ` +
          `recycled, and is now free to be written again.`,

          `The producer performs a CAS to advance pIdx from ${calc.pIdx} to ${calc.pIdx + 1}, ` +
          `claiming slot ${calc.slot} exclusively against any competing producer. ` +
          `It writes the element into buf[${calc.slot}], then advances the stamp to ` +
          `seqBuf[${calc.slot}] = ${calc.pIdx + 1} with a release store. ` +
          `This new stamp is exactly cIdx + 1 for the consumer currently pointing at this slot, ` +
          `which is the signal consumers check to know a fully written element is waiting.`,
        ],
      };
    } else if (calc.diff < 0) {
      return {
        color: COLOR.fail,
        paragraphs: [
          `The producer reads pIdx = ${calc.pIdx} and computes the target slot as ` +
          `pIdx & mask = ${calc.pIdx} & ${MASK} = ${calc.slot}. ` +
          `It reads the sequence stamp seqBuf[${calc.slot}] = ${calc.seq}. ` +
          `The difference seq − pIdx = ${calc.seq} − ${calc.pIdx} = ${calc.diff} is negative.`,

          `A negative diff means the slot's stamp has fallen behind the producer counter. ` +
          `The slot was written in a previous lap and its consumer has not yet read and ` +
          `recycled it — the buffer is full. ` +
          `offer() returns false immediately and no state is changed.`,
        ],
      };
    }
  } else {
    if (calc.diff === 0) {
      return {
        color: COLOR.read,
        paragraphs: [
          `The consumer reads cIdx = ${calc.cIdx} and computes the source slot as ` +
          `cIdx & mask = ${calc.cIdx} & ${MASK} = ${calc.slot}. ` +
          `It reads the sequence stamp seqBuf[${calc.slot}] = ${calc.seq}. ` +
          `The expected stamp is cIdx + 1 = ${calc.expected}. ` +
          `The difference seq − expected = ${calc.seq} − ${calc.expected} = 0 means the stamp ` +
          `matches exactly — a producer has written to this slot and advanced the stamp to ` +
          `signal that a complete element is ready.`,

          `The consumer performs a CAS to advance cIdx from ${calc.cIdx} to ${calc.cIdx + 1}, ` +
          `claiming the right to read slot ${calc.slot} against any competing consumer. ` +
          `It reads the element from buf[${calc.slot}] and clears the slot to null. ` +
          `It then advances the stamp to seqBuf[${calc.slot}] = ${calc.cIdx + CAP} with a release store. ` +
          `This new stamp is exactly pIdx for the producer that will next wrap around to this slot, ` +
          `which is the signal producers check to know the slot is free again.`,
        ],
      };
    } else if (calc.diff < 0) {
      return {
        color: COLOR.fail,
        paragraphs: [
          `The consumer reads cIdx = ${calc.cIdx} and computes the source slot as ` +
          `cIdx & mask = ${calc.cIdx} & ${MASK} = ${calc.slot}. ` +
          `It reads the sequence stamp seqBuf[${calc.slot}] = ${calc.seq}. ` +
          `The expected stamp is cIdx + 1 = ${calc.expected}. ` +
          `The difference seq − expected = ${calc.seq} − ${calc.expected} = ${calc.diff} is negative.`,

          `A negative diff means no producer has yet written to this slot in the current lap — ` +
          `the stamp is still behind the value a completed write would have set. ` +
          `The buffer is empty. take() returns null and cIdx stays at ${calc.cIdx}.`,
        ],
      };
    }
  }

  return null;
}

function StepDescription({ calc }) {
  const desc = stepDescription(calc);

  if (!desc) return (
    <div style={{
      margin: "6px 0 4px", borderRadius: 8,
      border: "1px solid #e8e6df", background: "#f5f4f0",
      padding: "10px 14px", fontSize: 12,
      color: "#ccc", textAlign: "center", fontStyle: "italic",
    }}>
      step summary will appear here after each operation
    </div>
  );

  return (
    <div style={{
      margin: "6px 0 4px", borderRadius: 8,
      border: `1px solid ${desc.color}22`,
      background: `${desc.color}08`,
      padding: "10px 14px",
    }}>
      {desc.paragraphs.map((p, i) => (
        <p key={i} style={{
          margin: i === 0 ? "0 0 6px" : "0",
          fontSize: 12.5, lineHeight: 1.65,
          color: "#444",
        }}>
          {p}
        </p>
      ))}
    </div>
  );
}

// ── Intermediate calculation trace panel ─────────────────────────────────────

function CalcPanel({ calc }) {
  if (!calc) return (
    <div style={{
      margin: "6px 0 4px", borderRadius: 8,
      border: "1px solid #e8e6df",
      background: "#f5f4f0",
      padding: "10px 14px",
      fontSize: 11, fontFamily: "monospace",
      color: "#ccc", textAlign: "center",
    }}>
      algorithm trace will appear here after each operation
    </div>
  );

  const isOffer   = calc.op === "offer";
  const opColor   = isOffer ? COLOR.write : COLOR.read;
  const diffZero  = calc.diff === 0;
  const diffNeg   = calc.diff < 0;

  const decisionColor = diffZero ? opColor : diffNeg ? COLOR.fail : COLOR.warn;
  const decisionText  = diffZero
    ? (isOffer
        ? `✓  diff == 0  →  slot is free  →  CAS pIdx ${calc.pIdx}→${calc.pIdx + 1}, write, stamp seqBuf[${calc.slot}] = ${calc.pIdx + 1}`
        : `✓  diff == 0  →  data is ready  →  CAS cIdx ${calc.cIdx}→${calc.cIdx + 1}, read, stamp seqBuf[${calc.slot}] = ${calc.cIdx + CAP}`)
    : diffNeg
    ? (isOffer
        ? `✗  diff < 0  →  buffer FULL  →  return false`
        : `✗  diff < 0  →  buffer EMPTY  →  return null`)
    : `↻  diff > 0  →  another thread advanced past this slot  →  retry`;

  const rows = isOffer ? [
    {
      label: "pIdx",
      expr:  `pIdx`,
      val:   String(calc.pIdx),
      note:  "monotonic producer counter (never resets)",
      hi:    false,
    },
    {
      label: "slot",
      expr:  `pIdx & mask`,
      val:   String(calc.slot),
      note:  `${calc.pIdx} & ${MASK}  =  ${calc.slot}   (bitmask replaces mod)`,
      hi:    false,
    },
    {
      label: "seq",
      expr:  `seqBuf[slot]`,
      val:   String(calc.seq),
      note:  `seqBuf[${calc.slot}] = ${calc.seq}   (the slot's current stamp)`,
      hi:    false,
    },
    {
      label: "diff",
      expr:  `seq − pIdx`,
      val:   String(calc.diff),
      note:  `${calc.seq} − ${calc.pIdx} = ${calc.diff}`,
      hi:    true,
    },
  ] : [
    {
      label: "cIdx",
      expr:  `cIdx`,
      val:   String(calc.cIdx),
      note:  "monotonic consumer counter (never resets)",
      hi:    false,
    },
    {
      label: "slot",
      expr:  `cIdx & mask`,
      val:   String(calc.slot),
      note:  `${calc.cIdx} & ${MASK}  =  ${calc.slot}   (bitmask replaces mod)`,
      hi:    false,
    },
    {
      label: "seq",
      expr:  `seqBuf[slot]`,
      val:   String(calc.seq),
      note:  `seqBuf[${calc.slot}] = ${calc.seq}   (the slot's current stamp)`,
      hi:    false,
    },
    {
      label: "expected",
      expr:  `cIdx + 1`,
      val:   String(calc.expected),
      note:  `${calc.cIdx} + 1 = ${calc.expected}   (producer stamps this after writing)`,
      hi:    false,
    },
    {
      label: "diff",
      expr:  `seq − expected`,
      val:   String(calc.diff),
      note:  `${calc.seq} − ${calc.expected} = ${calc.diff}`,
      hi:    true,
    },
  ];

  return (
    <div style={{
      margin: "6px 0 4px", borderRadius: 8,
      border: `1px solid ${opColor}30`,
      overflow: "hidden", fontSize: 12,
    }}>
      {/* Header */}
      <div style={{
        background: `${opColor}14`,
        padding: "5px 12px",
        display: "flex", alignItems: "center", gap: 8,
        borderBottom: `1px solid ${opColor}20`,
      }}>
        <span style={{
          color: opColor, fontFamily: "monospace",
          fontWeight: 700, fontSize: 12,
        }}>
          {isOffer ? "offer()" : "take()"}
        </span>
        <span style={{ color: "#bbb", fontSize: 11 }}>
          — algorithm trace
        </span>
      </div>

      {/* Variable rows */}
      {rows.map((r, i) => (
        <div key={i} style={{
          display: "grid",
          gridTemplateColumns: "62px 140px 36px 1fr",
          alignItems: "center",
          gap: 0,
          padding: "4px 12px",
          borderBottom: i < rows.length - 1 ? "1px solid #f0ede6" : "none",
          background: r.hi ? `${opColor}0c` : "transparent",
        }}>
          {/* variable name */}
          <span style={{
            fontFamily: "monospace", fontSize: 11, fontWeight: r.hi ? 700 : 400,
            color: r.hi ? opColor : "#aaa",
          }}>
            {r.label}
          </span>
          {/* formula */}
          <span style={{
            fontFamily: "monospace", fontSize: 11, color: "#ccc",
          }}>
            = {r.expr}
          </span>
          {/* computed value — big & bold */}
          <span style={{
            fontFamily: "monospace", fontSize: 14, fontWeight: 700,
            color: r.hi ? decisionColor : "#555",
            textAlign: "right", paddingRight: 10,
          }}>
            {r.val}
          </span>
          {/* arithmetic note */}
          <span style={{
            fontFamily: "monospace", fontSize: 10,
            color: r.hi ? decisionColor + "aa" : "#d8d5cc",
          }}>
            {r.note}
          </span>
        </div>
      ))}

      {/* Decision bar */}
      <div style={{
        padding: "6px 12px",
        background: `${decisionColor}10`,
        borderTop: `1px solid ${decisionColor}28`,
        fontFamily: "monospace", fontSize: 11,
        fontWeight: 600, color: decisionColor,
      }}>
        {decisionText}
      </div>
    </div>
  );
}

// ── SVG ring buffer diagram ──────────────────────────────────────────────────

function RingDiagram({ buf, seq, pIdx, cIdx, hiSlots, hiColor }) {
  const size  = pIdx - cIdx;
  const pSlot = pIdx & MASK;
  const cSlot = cIdx & MASK;
  const same  = pSlot === cSlot;

  const SX = [56, 186, 316, 446];
  const SW = 106, SH = 80, SY = 36;
  const bot = SY + SH;

  const pCX = SX[pSlot] + SW / 2;
  const cCX = SX[cSlot] + SW / 2;
  const pX  = same ? pCX - 30 : pCX;
  const cX  = same ? cCX + 30 : cCX;

  const szColor = size === CAP ? COLOR.warn : COLOR.neutral;

  const statItems = [
    { label: "size",         val: `${size} / ${CAP}`, col: szColor      },
    { label: "producer lap", val: `${Math.floor(pIdx / CAP)}`, col: COLOR.write },
    { label: "consumer lap", val: `${Math.floor(cIdx / CAP)}`, col: COLOR.read  },
  ];

  return (
    <svg viewBox="0 0 608 208" style={{ width: "100%", display: "block" }}>
      <defs>
        <marker id="arr" viewBox="0 0 10 10" refX="8" refY="5"
          markerWidth="5" markerHeight="5" orient="auto-start-reverse">
          <path d="M2 1L8 5L2 9" fill="none" stroke="context-stroke"
            strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
        </marker>
      </defs>

      {SX.map((x, i) => {
        const hi  = hiSlots?.includes(i);
        const val = buf[i];
        const col = hi ? hiColor : undefined;
        return (
          <g key={i}>
            <rect x={x} y={SY} width={SW} height={SH} rx="8"
              fill={hi ? col + "18" : "var(--slot-bg, #f5f5f3)"}
              stroke={hi ? col : val ? "#aaa" : "#d4d2c8"}
              strokeWidth={hi ? 1.5 : 0.5} />
            <text x={x + SW / 2} y={SY + 12} textAnchor="middle"
              dominantBaseline="central"
              fontSize="10" fill="#888" fontFamily="monospace">
              slot {i}
            </text>
            <text x={x + SW / 2} y={SY + SH / 2 + 2} textAnchor="middle"
              dominantBaseline="central"
              fontSize={val ? 20 : 14} fontWeight={val ? "600" : "400"}
              fontFamily={val ? "monospace" : "sans-serif"}
              fill={val ? COLOR.write : "#ccc"}>
              {val ?? "—"}
            </text>
            <text x={x + SW / 2} y={SY + SH - 10} textAnchor="middle"
              dominantBaseline="central"
              fontSize="11" fontFamily="monospace"
              fill={hi ? col : "#999"}>
              seq={seq[i]}
            </text>
          </g>
        );
      })}

      {/* pIdx arrow */}
      <line x1={pX} y1={bot + 2} x2={pX} y2={bot + 13}
        stroke={COLOR.write} strokeWidth="1.5" markerEnd="url(#arr)" />
      <text x={pX} y={bot + 26} textAnchor="middle" dominantBaseline="central"
        fontSize="11" fontFamily="monospace" fill={COLOR.write}>
        pIdx={pIdx}
      </text>

      {/* cIdx arrow */}
      <line x1={cX} y1={bot + 2} x2={cX} y2={bot + 13}
        stroke={COLOR.read} strokeWidth="1.5" markerEnd="url(#arr)" />
      <text x={cX} y={bot + 26} textAnchor="middle" dominantBaseline="central"
        fontSize="11" fontFamily="monospace" fill={COLOR.read}>
        cIdx={cIdx}
      </text>

      {/* Stats row */}
      {statItems.map((s, n) => {
        const sx = 100 + n * 200;
        return (
          <g key={n}>
            <text x={sx} y={164} textAnchor="middle" dominantBaseline="central"
              fontSize="9" fill="#aaa" fontFamily="sans-serif">
              {s.label}
            </text>
            <text x={sx} y={180} textAnchor="middle" dominantBaseline="central"
              fontSize="14" fontWeight="500" fontFamily="monospace" fill={s.col}>
              {s.val}
            </text>
          </g>
        );
      })}

      {/* Legend */}
      {[[COLOR.write, "pIdx — producer"], [COLOR.read, "cIdx — consumer"]].map(
        ([c, label], n) => (
          <g key={n}>
            <circle cx={168 + n * 200} cy={200} r="4" fill={c} />
            <text x={178 + n * 200} y={200} dominantBaseline="central"
              fontSize="11" fill="#999" fontFamily="sans-serif">
              {label}
            </text>
          </g>
        )
      )}
    </svg>
  );
}

// ── History log ──────────────────────────────────────────────────────────────

function HistoryLog({ entries, currentIndex }) {
  const bottomRef = useRef(null);

  return (
    <div style={{
      maxHeight: 110, overflowY: "auto",
      border: "1px solid #e0ded6", borderRadius: 8,
      fontSize: 11, fontFamily: "monospace",
    }}>
      {entries.length === 0 ? (
        <div style={{ padding: "6px 12px", color: "#aaa", fontFamily: "sans-serif" }}>
          No operations yet — try offering a value above.
        </div>
      ) : (
        entries.map((e, i) => {
          const border = {
            write: COLOR.write, read: COLOR.read,
            fail:  COLOR.fail,  warn: COLOR.warn,
          }[e.kind] ?? "#ccc";
          const isSelected = currentIndex === i;
          return (
            <div key={i} style={{
              display: "flex", gap: 10, padding: "4px 10px",
              borderLeft: `3px solid ${border}`,
              borderBottom: i < entries.length - 1 ? "1px solid #f0ede6" : "none",
              color: border,
              background: isSelected ? `${border}0a` : "transparent",
              fontWeight: isSelected ? 600 : 400,
            }}>
              <span style={{ color: "#bbb", minWidth: 32, fontSize: 10 }}>
                #{i + 1}
              </span>
              <span style={{ flex: 1 }}>{e.msg}</span>
            </div>
          );
        })
      )}
      <div ref={bottomRef} />
    </div>
  );
}

// ── Main component ───────────────────────────────────────────────────────────

export default function MpmcRingBuffer() {
  const [state,   setState]   = useState(initState);
  const [input,   setInput]   = useState("A");
  const [history, setHistory] = useState([{ msg: "initial state", kind: "neutral", stateSnapshot: initState(), calcSnapshot: null }]);
  const [hi,      setHi]      = useState({ slots: null, color: null });
  const [calc,    setCalc]    = useState(null);
  const [historyIndex, setHistoryIndex] = useState(-1);

  function addLog(msg, kind, stateSnapshot, calcSnapshot) {
    setHistory(h => [...h, { msg, kind, stateSnapshot, calcSnapshot }]);
  }

  const displayedState = historyIndex >= 0 && historyIndex < history.length
    ? history[historyIndex].stateSnapshot
    : state;

  const goBack = useCallback(() => {
    setHistoryIndex(i => {
      let nextIndex;
      if (i === -1) nextIndex = history.length - 1;  // Go to last operation
      else nextIndex = Math.max(0, i - 1);

      if (nextIndex >= 0 && nextIndex < history.length) {
        setCalc(history[nextIndex].calcSnapshot);
      }
      return nextIndex;
    });
  }, [history]);

  const goForward = useCallback(() => {
    setHistoryIndex(i => {
      let nextIndex;
      if (i === history.length - 1) nextIndex = -1;  // Go to current state
      else nextIndex = Math.min(history.length - 1, i + 1);

      if (nextIndex >= 0 && nextIndex < history.length) {
        setCalc(history[nextIndex].calcSnapshot);
      } else if (nextIndex === -1) {
        setCalc(null);
      }
      return nextIndex;
    });
  }, [history]);

  const doOffer = useCallback(() => {
    const val = input.trim();
    if (!val) return;

    setState(prev => {
      const { buf, seq, pIdx, cIdx } = prev;
      const slot = pIdx & MASK;
      const s    = seq[slot];
      const diff = s - pIdx;

      // capture trace before mutation
      setCalc({ op: "offer", pIdx, slot, seq: s, diff });

      if (diff === 0) {
        const newBuf = [...buf]; newBuf[slot] = val;
        const newSeq = [...seq]; newSeq[slot] = pIdx + 1;
        const newState = { buf: newBuf, seq: newSeq, pIdx: pIdx + 1, cIdx };
        const calcSnapshot = { op: "offer", pIdx, slot, seq: s, diff };
        const msg = `offer("${val}") → true   [slot ${slot}  seq: ${s}→${newSeq[slot]}  pIdx: ${pIdx}→${pIdx + 1}]`;
        addLog(msg, "write", newState, calcSnapshot);
        setHi({ slots: [slot], color: COLOR.write });
        if (val.length === 1 && val >= "A" && val < "Z")
          setInput(String.fromCharCode(val.charCodeAt(0) + 1));
        setHistoryIndex(-1);
        return newState;
      } else if (diff < 0) {
        const calcSnapshot = { op: "offer", pIdx, slot, seq: s, diff };
        const msg = `offer("${val}") → false  [slot ${slot}  seq=${s}  pIdx=${pIdx}  diff=${diff} < 0 → FULL]`;
        addLog(msg, "fail", prev, calcSnapshot);
        setHi({ slots: [slot], color: COLOR.fail });
        setHistoryIndex(-1);
        return prev;
      }
      return prev;
    });
  }, [input]);

  const doTake = useCallback(() => {
    setState(prev => {
      const { buf, seq, pIdx, cIdx } = prev;
      const slot     = cIdx & MASK;
      const s        = seq[slot];
      const expected = cIdx + 1;
      const diff     = s - expected;

      setCalc({ op: "take", cIdx, slot, seq: s, expected, diff });

      if (diff === 0) {
        const val    = buf[slot];
        const newBuf = [...buf]; newBuf[slot] = null;
        const newSeq = [...seq]; newSeq[slot] = cIdx + CAP;
        const newState = { buf: newBuf, seq: newSeq, pIdx, cIdx: cIdx + 1 };
        const calcSnapshot = { op: "take", cIdx, slot, seq: s, expected, diff };
        const msg = `take() → "${val}"   [slot ${slot}  seq: ${s}→${newSeq[slot]}  cIdx: ${cIdx}→${cIdx + 1}]`;
        addLog(msg, "read", newState, calcSnapshot);
        setHi({ slots: [slot], color: COLOR.read });
        setHistoryIndex(-1);
        return newState;
      } else if (diff < 0) {
        const calcSnapshot = { op: "take", cIdx, slot, seq: s, expected, diff };
        const msg = `take() → null   [slot ${slot}  seq=${s}  cIdx+1=${expected}  diff=${diff} < 0 → EMPTY]`;
        addLog(msg, "fail", prev, calcSnapshot);
        setHi({ slots: [slot], color: COLOR.fail });
        setHistoryIndex(-1);
        return prev;
      }
      return prev;
    });
  }, []);

  const doReset = useCallback(() => {
    setState(initState());
    setInput("A");
    setHistory([{ msg: "initial state", kind: "neutral", stateSnapshot: initState(), calcSnapshot: null }]);
    setHi({ slots: null, color: null });
    setCalc(null);
    setHistoryIndex(-1);
  }, []);

  const onKey = useCallback((e) => {
    if (e.key === "Enter") doOffer();
  }, [doOffer]);

  const s = {
    wrap: {
      fontFamily: "sans-serif",
      border: "1px solid #e0ded6", borderRadius: 12,
      padding: "16px 16px 12px", background: "#fafaf8",
      maxWidth: 680, margin: "1.5rem auto",
    },
    heading: {
      fontSize: 13, fontWeight: 600, letterSpacing: "0.04em",
      textTransform: "uppercase", color: "#888",
      marginBottom: 12, textAlign: "center",
    },
    controls: {
      display: "flex", alignItems: "center", gap: 8,
      justifyContent: "center", flexWrap: "wrap", marginBottom: 6,
    },
    input: {
      padding: "6px 10px", borderRadius: 8,
      border: "1px solid #d4d2c8", background: "#fff",
      fontSize: 13, fontFamily: "monospace", width: 110,
      outline: "none", color: "#333",
    },
    btnOffer: {
      padding: "6px 18px", borderRadius: 8, border: "none",
      background: COLOR.write, color: "#fff",
      fontSize: 13, fontWeight: 600, cursor: "pointer",
    },
    btnTake: {
      padding: "6px 18px", borderRadius: 8, border: "none",
      background: COLOR.read, color: "#fff",
      fontSize: 13, fontWeight: 600, cursor: "pointer",
    },
    btnReset: {
      padding: "6px 14px", borderRadius: 8,
      border: "1px solid #d4d2c8", background: "transparent",
      color: "#888", fontSize: 13, cursor: "pointer",
    },
    btnNav: (disabled) => ({
      padding: "6px 12px", borderRadius: 8,
      border: `1px solid ${disabled ? "#e8e6df" : "#d4d2c8"}`, background: "transparent",
      color: disabled ? "#ccc" : "#888", fontSize: 13, cursor: disabled ? "default" : "pointer",
      opacity: disabled ? 0.5 : 1,
    }),
    hint: {
      fontSize: 11, textAlign: "center", color: "#aaa",
      marginTop: 8, lineHeight: 1.6,
    },
    sectionLabel: {
      fontSize: 11, color: "#bbb", marginBottom: 4, paddingLeft: 4,
    },
  };

  return (
    <div style={s.wrap}>
      <div style={s.heading}>MPMC Ring Buffer · Capacity 4</div>

      <div style={s.controls}>
        <input
          style={s.input}
          value={input}
          maxLength={6}
          placeholder='e.g. "A"'
          onChange={e => setInput(e.target.value)}
          onKeyDown={onKey}
        />
        <button style={s.btnOffer} onClick={doOffer}>Offer →</button>
        <button style={s.btnTake}  onClick={doTake}>← Take</button>
        <button style={s.btnReset} onClick={doReset}>Reset</button>
        <button style={s.btnNav(historyIndex === 0)} onClick={goBack} disabled={historyIndex === 0}>
          ← Back
        </button>
        <button style={s.btnNav(historyIndex === -1)} onClick={goForward} disabled={historyIndex === -1}>
          Forward →
        </button>
      </div>

      <RingDiagram
        buf={displayedState.buf}
        seq={displayedState.seq}
        pIdx={displayedState.pIdx}
        cIdx={displayedState.cIdx}
        hiSlots={hi.slots}
        hiColor={hi.color}
      />

      <div style={{ marginTop: 8 }}>
        <div style={s.sectionLabel}>Step summary</div>
        <StepDescription calc={calc} />
      </div>

      <div style={{ marginTop: 8 }}>
        <div style={s.sectionLabel}>Algorithm trace</div>
        <CalcPanel calc={calc} />
      </div>

      <div style={{ marginTop: 8 }}>
        <div style={s.sectionLabel}>Operation history</div>
        <HistoryLog entries={history} currentIndex={historyIndex} />
      </div>

      <div style={s.hint}>
        Type any label ·{" "}
        <strong style={{ color: COLOR.write }}>Offer</strong> to enqueue ·{" "}
        <strong style={{ color: COLOR.read }}>Take</strong> to dequeue ·
        the trace panel shows every variable the algorithm computes before deciding ·
        use Back / Forward to replay any step
      </div>
    </div>
  );
}
