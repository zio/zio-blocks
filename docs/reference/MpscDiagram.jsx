import React, { useState, useCallback } from 'react';

// ── Module-level constants ─────────────────────────────
const CAP  = 4;
const MASK = CAP - 1;

const COLOR = {
  write:   "#1D9E75",  // green  — successful offer
  read:    "#378ADD",  // blue   — successful take
  fail:    "#E24B4A",  // red    — buffer full / empty
  neutral: "#888780",  // grey   — neutral / initial state
};

const initState = () => ({
  buf:    [null, null, null, null],
  pIdx:   0,
  cIdx:   0,
  pLimit: CAP,
});

// ── Intermediate calculation trace panel ─────────────────────────────
const CalcPanel = ({ calc }) => {
  if (!calc) {
    return (
      <div style={{ padding: '12px', fontSize: '14px', color: '#888' }}>
        algorithm trace will appear here after each operation
      </div>
    );
  }

  const { op, color } = calc;

  if (op === 'offer') {
    const { pIdx, pLimit, pLimitRefreshed, cIdx, slot, outcome } = calc;
    const isFull = outcome === 'full';

    return (
      <div style={{ border: '1px solid #ddd', borderRadius: '4px', overflow: 'hidden', fontSize: '13px' }}>
        <div style={{ backgroundColor: color, color: 'white', padding: '8px 12px', fontWeight: 'bold' }}>
          offer() — algorithm trace
        </div>

        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px' }}>
          <tbody>
            <tr>
              <td style={{ padding: '8px 12px', borderBottom: '1px solid #eee', fontWeight: '600', width: '80px' }}>pIdx</td>
              <td style={{ padding: '8px 12px', borderBottom: '1px solid #eee', fontFamily: 'monospace' }}>pIdx</td>
              <td style={{ padding: '8px 12px', borderBottom: '1px solid #eee', fontFamily: 'monospace', fontWeight: '500' }}>{pIdx}</td>
              <td style={{ padding: '8px 12px', borderBottom: '1px solid #eee', fontSize: '12px', color: '#666' }}>monotonic producer counter (claimed via CAS)</td>
            </tr>
            <tr>
              <td style={{ padding: '8px 12px', borderBottom: '1px solid #eee', fontWeight: '600' }}>pLimit</td>
              <td style={{ padding: '8px 12px', borderBottom: '1px solid #eee', fontFamily: 'monospace' }}>pLimit</td>
              <td style={{ padding: '8px 12px', borderBottom: '1px solid #eee', fontFamily: 'monospace', fontWeight: '500' }}>{pLimit}</td>
              <td style={{ padding: '8px 12px', borderBottom: '1px solid #eee', fontSize: '12px', color: '#666' }}>cached: last known cIdx + capacity</td>
            </tr>
            {isFull && (
              <tr>
                <td style={{ padding: '8px 12px', borderBottom: '1px solid #eee', fontWeight: '600' }}>cIdx</td>
                <td style={{ padding: '8px 12px', borderBottom: '1px solid #eee', fontFamily: 'monospace' }}>consumerIndex</td>
                <td style={{ padding: '8px 12px', borderBottom: '1px solid #eee', fontFamily: 'monospace', fontWeight: '500' }}>{cIdx}</td>
                <td style={{ padding: '8px 12px', borderBottom: '1px solid #eee', fontSize: '12px', color: '#666' }}>refreshed: stale limit exceeded</td>
              </tr>
            )}
            {isFull && (
              <tr>
                <td style={{ padding: '8px 12px', borderBottom: '1px solid #eee', fontWeight: '600' }}>newPLimit</td>
                <td style={{ padding: '8px 12px', borderBottom: '1px solid #eee', fontFamily: 'monospace' }}>cIdx + capacity</td>
                <td style={{ padding: '8px 12px', borderBottom: '1px solid #eee', fontFamily: 'monospace', fontWeight: '500' }}>{pLimitRefreshed}</td>
                <td style={{ padding: '8px 12px', borderBottom: '1px solid #eee', fontSize: '12px', color: '#666' }}></td>
              </tr>
            )}
            <tr>
              <td style={{ padding: '8px 12px', borderBottom: '1px solid #eee', fontWeight: '600' }}>slot</td>
              <td style={{ padding: '8px 12px', borderBottom: '1px solid #eee', fontFamily: 'monospace' }}>pIdx & mask</td>
              <td style={{ padding: '8px 12px', borderBottom: '1px solid #eee', fontFamily: 'monospace', fontWeight: '500' }}>{slot}</td>
              <td style={{ padding: '8px 12px', borderBottom: '1px solid #eee', fontSize: '12px', color: '#666' }}>bitmask arithmetic</td>
            </tr>

            <tr style={{ backgroundColor: color + '20' }}>
              <td style={{ padding: '8px 12px', fontWeight: '600', fontWeight: 'bold' }}>
                {isFull ? 'pIdx >= newPLimit' : 'pIdx < pLimit'}
              </td>
              <td colSpan="2" style={{ padding: '8px 12px', fontFamily: 'monospace', fontWeight: 'bold' }}>
                {isFull ? 'true' : 'true'}
              </td>
              <td style={{ padding: '8px 12px', fontSize: '12px', color: '#666' }}></td>
            </tr>
          </tbody>
        </table>

        <div style={{ backgroundColor: color + '30', padding: '8px 12px', fontSize: '12px', fontWeight: '500', color: '#333' }}>
          {isFull ? '✗ Buffer FULL → return false' : '✓ Slot free → write to buf[slot], stamp pIdx++'}
        </div>
      </div>
    );
  }

  if (op === 'take') {
    const { cIdx, slot, val, outcome } = calc;
    const isEmpty = outcome === 'empty';

    return (
      <div style={{ border: '1px solid #ddd', borderRadius: '4px', overflow: 'hidden', fontSize: '13px' }}>
        <div style={{ backgroundColor: color, color: 'white', padding: '8px 12px', fontWeight: 'bold' }}>
          take() — algorithm trace
        </div>

        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px' }}>
          <tbody>
            <tr>
              <td style={{ padding: '8px 12px', borderBottom: '1px solid #eee', fontWeight: '600', width: '80px' }}>cIdx</td>
              <td style={{ padding: '8px 12px', borderBottom: '1px solid #eee', fontFamily: 'monospace' }}>cIdx</td>
              <td style={{ padding: '8px 12px', borderBottom: '1px solid #eee', fontFamily: 'monospace', fontWeight: '500' }}>{cIdx}</td>
              <td style={{ padding: '8px 12px', borderBottom: '1px solid #eee', fontSize: '12px', color: '#666' }}>monotonic consumer counter</td>
            </tr>
            <tr>
              <td style={{ padding: '8px 12px', borderBottom: '1px solid #eee', fontWeight: '600' }}>slot</td>
              <td style={{ padding: '8px 12px', borderBottom: '1px solid #eee', fontFamily: 'monospace' }}>cIdx & mask</td>
              <td style={{ padding: '8px 12px', borderBottom: '1px solid #eee', fontFamily: 'monospace', fontWeight: '500' }}>{slot}</td>
              <td style={{ padding: '8px 12px', borderBottom: '1px solid #eee', fontSize: '12px', color: '#666' }}>bitmask arithmetic</td>
            </tr>

            <tr style={{ backgroundColor: color + '20' }}>
              <td style={{ padding: '8px 12px', fontWeight: '600', fontWeight: 'bold' }}>buf[slot]</td>
              <td style={{ padding: '8px 12px', fontFamily: 'monospace', fontWeight: 'bold' }}></td>
              <td style={{ padding: '8px 12px', fontFamily: 'monospace', fontWeight: 'bold' }}>{val === null ? 'null' : JSON.stringify(val)}</td>
              <td style={{ padding: '8px 12px', fontSize: '12px', color: '#666' }}></td>
            </tr>
          </tbody>
        </table>

        <div style={{ backgroundColor: color + '30', padding: '8px 12px', fontSize: '12px', fontWeight: '500', color: '#333' }}>
          {isEmpty ? '✗ Buffer EMPTY (or mid-write) → return null' : '✓ Data ready → read, clear, advance cIdx'}
        </div>
      </div>
    );
  }

  return null;
};

// ── SVG ring buffer diagram ─────────────────────────────
const RingDiagram = ({ state, highlighted }) => {
  const { buf, pIdx, cIdx, pLimit } = state;

  const slotWidth = 120;
  const slotHeight = 80;
  const slotGap = 40;
  const totalWidth = 4 * slotWidth + 3 * slotGap + 80;
  const totalHeight = 280;

  return (
    <svg viewBox={`0 0 ${totalWidth} ${totalHeight}`} style={{ border: '1px solid #ddd', borderRadius: '4px', backgroundColor: '#fafafa' }}>
      {/* Title */}
      <text x="20" y="28" fontSize="16" fontWeight="bold" fill="#333">
        Ring Buffer (capacity = {CAP})
      </text>

      {/* Four slot boxes */}
      {[0, 1, 2, 3].map((i) => {
        const x = 40 + i * (slotWidth + slotGap);
        const y = 50;
        const isHighlighted = highlighted && highlighted.slots && highlighted.slots.includes(i);

        return (
          <g key={i}>
            <rect
              x={x}
              y={y}
              width={slotWidth}
              height={slotHeight}
              fill={isHighlighted ? (highlighted.color + '30') : '#fff'}
              stroke={isHighlighted ? highlighted.color : '#ccc'}
              strokeWidth={isHighlighted ? 2 : 1}
              rx="4"
            />
            <text x={x + slotWidth / 2} y={y + 24} fontSize="13" fontWeight="bold" textAnchor="middle" fill="#333">
              slot {i}
            </text>
            <text x={x + slotWidth / 2} y={y + 50} fontSize="14" fontWeight="500" textAnchor="middle" fill="#666">
              {buf[i] === null ? '—' : buf[i]}
            </text>
          </g>
        );
      })}

      {/* Producer index arrow (pIdx) */}
      <g>
        <line x1="40" y1="150" x2="40" y2="190" stroke={COLOR.write} strokeWidth="2" markerEnd="url(#arrowGreen)" />
        <text x="40" y="210" fontSize="12" fontWeight="bold" textAnchor="middle" fill={COLOR.write}>
          pIdx={pIdx % CAP}
        </text>
        <text x="40" y="228" fontSize="11" textAnchor="middle" fill="#888">
          ({pIdx})
        </text>
      </g>

      {/* Consumer index arrow (cIdx) */}
      <g>
        <line x1="580" y1="150" x2="580" y2="190" stroke={COLOR.read} strokeWidth="2" />
        <polygon points="580,190 575,180 585,180" fill={COLOR.read} />
        <text x="580" y="210" fontSize="12" fontWeight="bold" textAnchor="middle" fill={COLOR.read}>
          cIdx={cIdx % CAP}
        </text>
        <text x="580" y="228" fontSize="11" textAnchor="middle" fill="#888">
          ({cIdx})
        </text>
      </g>

      {/* Stats row */}
      <text x="40" y="255" fontSize="12" fill="#666">
        size: {Math.max(0, pIdx - cIdx)} / {CAP}
      </text>
      <text x="40" y="275" fontSize="12" fill="#666">
        pIdx: {pIdx}, pLimit: {pLimit}, cIdx: {cIdx}
      </text>
    </svg>
  );
};

// ── History log ─────────────────────────────
const HistoryLog = ({ history, currentIndex }) => {
  return (
    <div
      style={{
        border: '1px solid #ddd',
        borderRadius: '4px',
        maxHeight: '110px',
        overflowY: 'auto',
        backgroundColor: '#f9f9f9',
        fontSize: '12px',
        fontFamily: 'monospace',
      }}
    >
      {history.length === 0 ? (
        <div style={{ padding: '12px', color: '#888' }}>history empty</div>
      ) : (
        history.map((entry, idx) => {
          const isSelected = idx === currentIndex;
          const borderColor = COLOR[entry.kind] || COLOR.neutral;

          return (
            <div
              key={idx}
              style={{
                padding: '8px 12px',
                borderLeft: `4px solid ${borderColor}`,
                backgroundColor: isSelected ? borderColor + '20' : 'transparent',
                fontWeight: isSelected ? 'bold' : 'normal',
                display: 'flex',
                gap: '8px',
              }}
            >
              <span style={{ color: '#aaa', minWidth: '30px' }}>#{idx}</span>
              <span>{entry.msg}</span>
            </div>
          );
        })
      )}
    </div>
  );
};

// ── Main component ─────────────────────────────
export default function MpscRingBuffer() {
  const [state, setState] = useState(initState());
  const [input, setInput] = useState('A');
  const [history, setHistory] = useState([
    { msg: 'initial state', kind: 'neutral', stateSnapshot: initState(), calcSnapshot: null },
  ]);
  const [hi, setHi] = useState({ slots: null, color: null });
  const [calc, setCalc] = useState(null);
  const [historyIndex, setHistoryIndex] = useState(0);

  const displayedState = historyIndex >= 0 ? history[historyIndex].stateSnapshot : state;

  const doOffer = useCallback(() => {
    if (input === null || input.trim() === '') return;

    const { buf, pIdx, cIdx, pLimit } = state;
    let newPLimit = pLimit;
    let newCIdx = cIdx;
    let outcome = 'write';

    // Check if we need to refresh pLimit
    if (pIdx >= pLimit) {
      // Slow path: refresh from consumer index
      newCIdx = cIdx;
      const newLimit = newCIdx + CAP;

      if (pIdx >= newLimit) {
        // Buffer is FULL
        outcome = 'full';
        const calcTrace = {
          op: 'offer',
          pIdx,
          pLimit,
          pLimitRefreshed: newLimit,
          cIdx: newCIdx,
          slot: pIdx & MASK,
          outcome,
          color: COLOR.fail,
        };
        setCalc(calcTrace);
        setHi({ slots: [pIdx & MASK], color: COLOR.fail });

        const msg = `offer("${input}") → false  [slot ${pIdx & MASK}  pIdx=${pIdx} >= pLimit=${newLimit} (refreshed) → FULL]`;
        setHistory((h) => [...h, { msg, kind: 'fail', stateSnapshot: state, calcSnapshot: calcTrace }]);
        setHistoryIndex(-1);
        return;
      }
      newPLimit = newLimit;
    }

    // Write to buffer
    const newBuf = [...buf];
    const slot = pIdx & MASK;
    newBuf[slot] = input;

    const newState = {
      buf: newBuf,
      pIdx: pIdx + 1,
      cIdx,
      pLimit: newPLimit,
    };

    const calcTrace = {
      op: 'offer',
      pIdx,
      pLimit,
      pLimitRefreshed: newPLimit,
      cIdx,
      slot,
      outcome,
      color: COLOR.write,
    };

    setState(newState);
    setCalc(calcTrace);
    setHi({ slots: [slot], color: COLOR.write });

    const nextLetter = String.fromCharCode(input.charCodeAt(0) + 1);
    setInput(nextLetter);

    const msg = `offer("${input}") → true   [slot ${slot}  buf[${slot}]: null→"${input}"  pIdx: ${pIdx}→${pIdx + 1}]`;
    setHistory((h) => [...h, { msg, kind: 'write', stateSnapshot: newState, calcSnapshot: calcTrace }]);
    setHistoryIndex(-1);
  }, [state, input]);

  const doTake = useCallback(() => {
    const { buf, pIdx, cIdx } = state;
    const slot = cIdx & MASK;
    const val = buf[slot];

    let outcome = 'empty';
    let color = COLOR.fail;
    let msg = '';

    if (val === null) {
      // Empty
      const calcTrace = {
        op: 'take',
        cIdx,
        slot,
        val: null,
        outcome,
        color,
      };

      setCalc(calcTrace);
      setHi({ slots: [slot], color });
      msg = `take() → null   [slot ${slot}  buf[${slot}]=null → EMPTY]`;
      setHistory((h) => [...h, { msg, kind: 'fail', stateSnapshot: state, calcSnapshot: calcTrace }]);
      setHistoryIndex(-1);
    } else {
      // Read
      outcome = 'read';
      color = COLOR.read;

      const newBuf = [...buf];
      newBuf[slot] = null;

      const newState = {
        buf: newBuf,
        pIdx,
        cIdx: cIdx + 1,
        pLimit: state.pLimit,
      };

      const calcTrace = {
        op: 'take',
        cIdx,
        slot,
        val,
        outcome,
        color,
      };

      setState(newState);
      setCalc(calcTrace);
      setHi({ slots: [slot], color });
      msg = `take() → "${val}"   [slot ${slot}  buf[${slot}]: "${val}"→null  cIdx: ${cIdx}→${cIdx + 1}]`;
      setHistory((h) => [...h, { msg, kind: 'read', stateSnapshot: newState, calcSnapshot: calcTrace }]);
      setHistoryIndex(-1);
    }
  }, [state]);

  const doReset = useCallback(() => {
    const resetState = initState();
    setState(resetState);
    setInput('A');
    setHistory([
      { msg: 'initial state', kind: 'neutral', stateSnapshot: resetState, calcSnapshot: null },
    ]);
    setHi({ slots: null, color: null });
    setCalc(null);
    setHistoryIndex(0);
  }, []);

  const goBack = useCallback(() => {
    if (historyIndex > 0) {
      const nextIdx = historyIndex - 1;
      setHistoryIndex(nextIdx);
      const entry = history[nextIdx];
      setCalc(entry.calcSnapshot);
    }
  }, [historyIndex, history]);

  const goForward = useCallback(() => {
    if (historyIndex < history.length - 1) {
      const nextIdx = historyIndex + 1;
      setHistoryIndex(nextIdx);
      const entry = history[nextIdx];
      setCalc(entry.calcSnapshot);
    } else if (historyIndex === history.length - 1) {
      setHistoryIndex(-1);
      setCalc(null);
    }
  }, [historyIndex, history]);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '20px', padding: '16px', backgroundColor: '#fff', borderRadius: '4px' }}>
      {/* Controls */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px', justifyContent: 'center', flexWrap: 'wrap' }}>
        <input
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value.slice(0, 6))}
          onKeyDown={(e) => e.key === 'Enter' && doOffer()}
          maxLength="6"
          style={{
            padding: '6px 10px',
            border: '1px solid #ccc',
            borderRadius: '4px',
            fontSize: '14px',
            fontWeight: '500',
          }}
          placeholder="A"
        />
        <button
          onClick={doOffer}
          style={{
            padding: '6px 12px',
            backgroundColor: COLOR.write,
            color: 'white',
            border: 'none',
            borderRadius: '4px',
            fontWeight: 'bold',
            cursor: 'pointer',
          }}
        >
          Offer →
        </button>
        <button
          onClick={doTake}
          style={{
            padding: '6px 12px',
            backgroundColor: COLOR.read,
            color: 'white',
            border: 'none',
            borderRadius: '4px',
            fontWeight: 'bold',
            cursor: 'pointer',
          }}
        >
          ← Take
        </button>
        <button
          onClick={doReset}
          style={{
            padding: '6px 12px',
            backgroundColor: '#f0f0f0',
            color: '#333',
            border: '1px solid #ccc',
            borderRadius: '4px',
            fontWeight: 'bold',
            cursor: 'pointer',
          }}
        >
          Reset
        </button>
        <button
          onClick={goBack}
          disabled={historyIndex === 0}
          style={{
            padding: '6px 12px',
            backgroundColor: historyIndex === 0 ? '#e0e0e0' : '#378ADD',
            color: historyIndex === 0 ? '#999' : 'white',
            border: 'none',
            borderRadius: '4px',
            fontWeight: 'bold',
            cursor: historyIndex === 0 ? 'not-allowed' : 'pointer',
          }}
        >
          ← Back
        </button>
        <button
          onClick={goForward}
          disabled={historyIndex === -1}
          style={{
            padding: '6px 12px',
            backgroundColor: historyIndex === -1 ? '#e0e0e0' : '#1D9E75',
            color: historyIndex === -1 ? '#999' : 'white',
            border: 'none',
            borderRadius: '4px',
            fontWeight: 'bold',
            cursor: historyIndex === -1 ? 'not-allowed' : 'pointer',
          }}
        >
          Forward →
        </button>
      </div>

      {/* SVG Diagram */}
      <RingDiagram state={displayedState} highlighted={hi} />

      {/* Calc panel */}
      <CalcPanel calc={calc} />

      {/* History log */}
      <div>
        <div style={{ fontSize: '13px', fontWeight: '600', marginBottom: '8px' }}>Operation history:</div>
        <HistoryLog history={history} currentIndex={historyIndex} />
      </div>

      {/* Hint */}
      <div style={{ fontSize: '12px', color: '#999', textAlign: 'center' }}>
        Type a label, press Enter or click Offer/Take. Use Back/Forward to navigate through operations.
      </div>
    </div>
  );
}
