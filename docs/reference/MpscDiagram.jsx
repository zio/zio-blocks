import React, { useState, useCallback, useRef } from "react";

const CAP  = 4;
const MASK = CAP - 1;

const COLOR = {
  write:   "#1D9E75",
  read:    "#378ADD",
  fail:    "#E24B4A",
  neutral: "#888780",
};

function initState() {
  return {
    buf:  Array(CAP).fill(null),
    pIdx: 0,
    cIdx: 0,
  };
}

// ── Prose description ─────────────────────────────────────────────────────────

function stepDescription(calc) {
  if (!calc) return null;

  if (calc.op === "offer") {
    const full = calc.size >= CAP;

    if (full) {
      return {
        color: COLOR.fail,
        paragraphs: [
          `The producer reads pIdx = ${calc.pIdx} and cIdx = ${calc.cIdx}. ` +
          `The difference pIdx − cIdx = ${calc.size} equals the capacity (${CAP}), ` +
          `which means every slot in the buffer is currently occupied.`,

          `Because the buffer is full, the producer cannot claim a slot. ` +
          `offer() returns false immediately and the buffer state is unchanged.`,
        ],
      };
    } else {
      return {
        color: COLOR.write,
        paragraphs: [
          `The producer reads pIdx = ${calc.pIdx} and cIdx = ${calc.cIdx}. ` +
          `The difference pIdx − cIdx = ${calc.size}, which is less than the capacity (${CAP}), ` +
          `so there is at least one free slot.`,

          `The target slot is computed as pIdx & mask = ${calc.pIdx} & ${MASK} = ${calc.slot}. ` +
          `The producer performs a CAS to advance pIdx from ${calc.pIdx} to ${calc.pIdx + 1}, ` +
          `claiming that slot exclusively. It then writes the element into buf[${calc.slot}] ` +
          `with release semantics so the consumer can observe it. offer() returns true.`,
        ],
      };
    }
  } else {
    const empty = calc.value === null;

    if (empty) {
      return {
        color: COLOR.fail,
        paragraphs: [
          `The consumer reads cIdx = ${calc.cIdx} with a plain load — no CAS is needed ` +
          `because only one thread ever consumes. ` +
          `The source slot is computed as cIdx & mask = ${calc.cIdx} & ${MASK} = ${calc.slot}.`,

          `An acquire-read of buf[${calc.slot}] returns null. ` +
          `A null slot means either the buffer is empty, or a producer has already ` +
          `claimed the slot via CAS but has not yet written the element (mid-write). ` +
          `In both cases the consumer cannot proceed — take() returns null and cIdx stays at ${calc.cIdx}.`,
        ],
      };
    } else {
      return {
        color: COLOR.read,
        paragraphs: [
          `The consumer reads cIdx = ${calc.cIdx} with a plain load — no CAS is needed ` +
          `because only one thread ever consumes. ` +
          `The source slot is computed as cIdx & mask = ${calc.cIdx} & ${MASK} = ${calc.slot}.`,

          `An acquire-read of buf[${calc.slot}] returns "${calc.value}", confirming the slot ` +
          `holds a fully written element. The consumer clears buf[${calc.slot}] to null ` +
          `(releasing the slot for future producers) and advances cIdx from ${calc.cIdx} to ${calc.cIdx + 1} ` +
          `with release semantics. take() returns "${calc.value}".`,
        ],
      };
    }
  }
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

// ── Algorithm trace panel ─────────────────────────────────────────────────────

function CalcPanel({ calc }) {
  if (!calc) return (
    <div style={{
      margin: "6px 0 4px", borderRadius: 8,
      border: "1px solid #e8e6df", background: "#f5f4f0",
      padding: "10px 14px", fontSize: 11,
      fontFamily: "monospace", color: "#ccc", textAlign: "center",
    }}>
      algorithm trace will appear here after each operation
    </div>
  );

  const isOffer   = calc.op === "offer";
  const opColor   = isOffer ? COLOR.write : COLOR.read;

  let rows;
  let decisionColor;
  let decisionText;

  if (isOffer) {
    const full = calc.size >= CAP;
    rows = [
      {
        label: "pIdx",
        expr:  "pIdx",
        val:   String(calc.pIdx),
        note:  "monotonic producer counter (never resets)",
        hi:    false,
      },
      {
        label: "cIdx",
        expr:  "cIdx",
        val:   String(calc.cIdx),
        note:  "monotonic consumer counter",
        hi:    false,
      },
      {
        label: "size",
        expr:  "pIdx − cIdx",
        val:   String(calc.size),
        note:  `${calc.pIdx} − ${calc.cIdx} = ${calc.size}   (current occupancy)`,
        hi:    true,
      },
      {
        label: "slot",
        expr:  "pIdx & mask",
        val:   String(calc.slot),
        note:  `${calc.pIdx} & ${MASK} = ${calc.slot}   (bitmask replaces mod)`,
        hi:    false,
      },
    ];
    decisionColor = full ? COLOR.fail : COLOR.write;
    decisionText  = full
      ? `✗  size == cap  →  buffer FULL  →  return false`
      : `✓  size < cap  →  CAS pIdx ${calc.pIdx}→${calc.pIdx + 1}, write buf[${calc.slot}]  →  return true`;
  } else {
    const empty = calc.value === null;
    rows = [
      {
        label: "cIdx",
        expr:  "cIdx",
        val:   String(calc.cIdx),
        note:  "plain read — only one consumer thread",
        hi:    false,
      },
      {
        label: "slot",
        expr:  "cIdx & mask",
        val:   String(calc.slot),
        note:  `${calc.cIdx} & ${MASK} = ${calc.slot}   (bitmask replaces mod)`,
        hi:    false,
      },
      {
        label: "value",
        expr:  "buf[slot]",
        val:   calc.value !== null ? `"${calc.value}"` : "null",
        note:  calc.value !== null
          ? `buf[${calc.slot}] = "${calc.value}"   (acquire-read — element is ready)`
          : `buf[${calc.slot}] = null   (empty or producer mid-write)`,
        hi:    true,
      },
    ];
    decisionColor = empty ? COLOR.fail : COLOR.read;
    decisionText  = empty
      ? `✗  value == null  →  buffer EMPTY  →  return null`
      : `✓  value ≠ null  →  clear buf[${calc.slot}], cIdx ${calc.cIdx}→${calc.cIdx + 1}  →  return "${calc.value}"`;
  }

  return (
    <div style={{
      margin: "6px 0 0", borderRadius: 8,
      border: `1px solid ${opColor}30`,
      overflow: "hidden", fontSize: 12,
    }}>
      {/* Header */}
      <div style={{
        background: `${opColor}14`, padding: "5px 12px",
        display: "flex", alignItems: "center", gap: 8,
        borderBottom: `1px solid ${opColor}20`,
      }}>
        <span style={{ color: opColor, fontFamily: "monospace", fontWeight: 700, fontSize: 12 }}>
          {isOffer ? "offer()" : "take()"}
        </span>
        <span style={{ color: "#bbb", fontSize: 11 }}>— algorithm trace</span>
      </div>

      {/* Variable rows */}
      {rows.map((r, i) => (
        <div key={i} style={{
          display: "grid",
          gridTemplateColumns: "52px 130px 44px 1fr",
          alignItems: "center",
          padding: "4px 12px",
          borderBottom: i < rows.length - 1 ? "1px solid #f0ede6" : "none",
          background: r.hi ? `${opColor}0c` : "transparent",
        }}>
          <span style={{
            fontFamily: "monospace", fontSize: 11,
            fontWeight: r.hi ? 700 : 400,
            color: r.hi ? opColor : "#aaa",
          }}>
            {r.label}
          </span>
          <span style={{ fontFamily: "monospace", fontSize: 11, color: "#ccc" }}>
            = {r.expr}
          </span>
          <span style={{
            fontFamily: "monospace", fontSize: 14, fontWeight: 700,
            color: r.hi ? decisionColor : "#555",
            textAlign: "right", paddingRight: 10,
          }}>
            {r.val}
          </span>
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

// ── SVG ring diagram ──────────────────────────────────────────────────────────

function RingDiagram({ buf, pIdx, cIdx, hiSlots, hiColor }) {
  const size  = pIdx - cIdx;
  const pSlot = pIdx & MASK;
  const cSlot = cIdx & MASK;
  const same  = pSlot === cSlot;

  const SX = [56, 186, 316, 446];
  const SW = 106, SH = 70, SY = 30;
  const bot = SY + SH;

  const pCX = SX[pSlot] + SW / 2;
  const cCX = SX[cSlot] + SW / 2;
  const pX  = same ? pCX - 28 : pCX;
  const cX  = same ? cCX + 28 : cCX;

  const szColor = size === CAP ? COLOR.fail : COLOR.neutral;

  const statItems = [
    { label: "size", val: `${size} / ${CAP}`, col: szColor     },
    { label: "pIdx", val: `${pIdx}`,           col: COLOR.write },
    { label: "cIdx", val: `${cIdx}`,           col: COLOR.read  },
  ];

  return (
    <svg viewBox="0 0 608 192" style={{ width: "100%", display: "block" }}>
      <defs>
        <marker id="arr-mpsc" viewBox="0 0 10 10" refX="8" refY="5"
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
            <text x={x + SW / 2} y={SY + 11} textAnchor="middle"
              dominantBaseline="central"
              fontSize="10" fill="#888" fontFamily="monospace">
              slot {i}
            </text>
            <text x={x + SW / 2} y={SY + SH / 2 + 4} textAnchor="middle"
              dominantBaseline="central"
              fontSize={val ? 22 : 13} fontWeight={val ? "600" : "400"}
              fontFamily={val ? "monospace" : "sans-serif"}
              fill={val ? COLOR.write : "#ccc"}>
              {val ?? "null"}
            </text>
          </g>
        );
      })}

      <line x1={pX} y1={bot + 2} x2={pX} y2={bot + 12}
        stroke={COLOR.write} strokeWidth="1.5" markerEnd="url(#arr-mpsc)" />
      <text x={pX} y={bot + 24} textAnchor="middle" dominantBaseline="central"
        fontSize="11" fontFamily="monospace" fill={COLOR.write}>
        pIdx={pIdx}
      </text>

      <line x1={cX} y1={bot + 2} x2={cX} y2={bot + 12}
        stroke={COLOR.read} strokeWidth="1.5" markerEnd="url(#arr-mpsc)" />
      <text x={cX} y={bot + 24} textAnchor="middle" dominantBaseline="central"
        fontSize="11" fontFamily="monospace" fill={COLOR.read}>
        cIdx={cIdx}
      </text>

      {statItems.map((s, n) => {
        const sx = 100 + n * 200;
        return (
          <g key={n}>
            <text x={sx} y={156} textAnchor="middle" dominantBaseline="central"
              fontSize="9" fill="#aaa" fontFamily="sans-serif">
              {s.label}
            </text>
            <text x={sx} y={172} textAnchor="middle" dominantBaseline="central"
              fontSize="14" fontWeight="500" fontFamily="monospace" fill={s.col}>
              {s.val}
            </text>
          </g>
        );
      })}

      {[[COLOR.write, "pIdx — producers (CAS)"], [COLOR.read, "cIdx — single consumer"]].map(
        ([c, label], n) => (
          <g key={n}>
            <circle cx={168 + n * 200} cy={188} r="4" fill={c} />
            <text x={178 + n * 200} y={188} dominantBaseline="central"
              fontSize="11" fill="#999" fontFamily="sans-serif">
              {label}
            </text>
          </g>
        )
      )}
    </svg>
  );
}

// ── History log ───────────────────────────────────────────────────────────────

function HistoryLog({ entries, currentIndex }) {
  const bottomRef = useRef(null);

  return (
    <div style={{
      maxHeight: 110, overflowY: "auto",
      border: "1px solid #e0ded6", borderRadius: 8,
      fontSize: 11, fontFamily: "monospace",
    }}>
      {entries.map((e, i) => {
        const border = { write: COLOR.write, read: COLOR.read, fail: COLOR.fail }[e.kind] ?? "#ccc";
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
            <span style={{ color: "#bbb", minWidth: 32, fontSize: 10 }}>#{i + 1}</span>
            <span style={{ flex: 1 }}>{e.msg}</span>
          </div>
        );
      })}
      <div ref={bottomRef} />
    </div>
  );
}

// ── Main component ────────────────────────────────────────────────────────────

export default function MpscRingBuffer() {
  const [state,        setState]        = useState(initState);
  const [input,        setInput]        = useState("A");
  const [history,      setHistory]      = useState([{
    msg: "initial state", kind: "neutral",
    stateSnapshot: initState(), calcSnapshot: null,
  }]);
  const [hi,           setHi]           = useState({ slots: null, color: null });
  const [calc,         setCalc]         = useState(null);
  const [historyIndex, setHistoryIndex] = useState(-1);

  function addLog(msg, kind, stateSnapshot, calcSnapshot) {
    setHistory(h => [...h, { msg, kind, stateSnapshot, calcSnapshot }]);
  }

  const displayedState = historyIndex >= 0 ? history[historyIndex].stateSnapshot : state;

  const goBack = useCallback(() => {
    setHistoryIndex(i => {
      const next = i === -1 ? history.length - 1 : Math.max(0, i - 1);
      setCalc(history[next].calcSnapshot);
      return next;
    });
  }, [history]);

  const goForward = useCallback(() => {
    setHistoryIndex(i => {
      const next = i === history.length - 1 ? -1 : Math.min(history.length - 1, i + 1);
      if (next >= 0) setCalc(history[next].calcSnapshot);
      else           setCalc(null);
      return next;
    });
  }, [history]);

  const doOffer = useCallback(() => {
    const val = input.trim();
    if (!val) return;

    setState(prev => {
      const { buf, pIdx, cIdx } = prev;
      const size = pIdx - cIdx;
      const slot = pIdx & MASK;
      const snap = { op: "offer", pIdx, cIdx, size, slot };
      setCalc(snap);

      if (size >= CAP) {
        addLog(`offer("${val}") → false  [size=${size}  FULL]`, "fail", prev, snap);
        setHi({ slots: [slot], color: COLOR.fail });
        setHistoryIndex(-1);
        return prev;
      }

      const newBuf = [...buf]; newBuf[slot] = val;
      const newState = { buf: newBuf, pIdx: pIdx + 1, cIdx };
      addLog(`offer("${val}") → true   [slot ${slot}  pIdx: ${pIdx}→${pIdx + 1}]`, "write", newState, snap);
      setHi({ slots: [slot], color: COLOR.write });
      if (val.length === 1 && val >= "A" && val < "Z")
        setInput(String.fromCharCode(val.charCodeAt(0) + 1));
      setHistoryIndex(-1);
      return newState;
    });
  }, [input]);

  const doTake = useCallback(() => {
    setState(prev => {
      const { buf, pIdx, cIdx } = prev;
      const slot  = cIdx & MASK;
      const value = buf[slot];
      const snap  = { op: "take", cIdx, slot, value };
      setCalc(snap);

      if (value === null) {
        addLog(`take() → null   [slot ${slot}  buf[${slot}]=null  EMPTY]`, "fail", prev, snap);
        setHi({ slots: [slot], color: COLOR.fail });
        setHistoryIndex(-1);
        return prev;
      }

      const newBuf = [...buf]; newBuf[slot] = null;
      const newState = { buf: newBuf, pIdx, cIdx: cIdx + 1 };
      addLog(`take() → "${value}"   [slot ${slot}  cIdx: ${cIdx}→${cIdx + 1}]`, "read", newState, snap);
      setHi({ slots: [slot], color: COLOR.read });
      setHistoryIndex(-1);
      return newState;
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
      color: disabled ? "#ccc" : "#888", fontSize: 13,
      cursor: disabled ? "default" : "pointer", opacity: disabled ? 0.5 : 1,
    }),
    hint: {
      fontSize: 11, textAlign: "center", color: "#aaa",
      marginTop: 8, lineHeight: 1.6,
    },
    sectionLabel: { fontSize: 11, color: "#bbb", marginBottom: 4, paddingLeft: 4 },
  };

  return (
    <div style={s.wrap}>
      <div style={s.heading}>MPSC Ring Buffer · Capacity 4</div>

      <div style={s.controls}>
        <input
          style={s.input} value={input} maxLength={6} placeholder='e.g. "A"'
          onChange={e => setInput(e.target.value)}
          onKeyDown={e => e.key === "Enter" && doOffer()}
        />
        <button style={s.btnOffer} onClick={doOffer}>Offer →</button>
        <button style={s.btnTake}  onClick={doTake}>← Take</button>
        <button style={s.btnReset} onClick={doReset}>Reset</button>
        <button style={s.btnNav(historyIndex === 0)}  onClick={goBack}    disabled={historyIndex === 0}>← Back</button>
        <button style={s.btnNav(historyIndex === -1)} onClick={goForward} disabled={historyIndex === -1}>Forward →</button>
      </div>

      <RingDiagram
        buf={displayedState.buf}
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
        use Back / Forward to replay any step
      </div>
    </div>
  );
}
