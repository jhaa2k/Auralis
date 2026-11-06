import { useStore } from '../core/projectStore.js';
import { RULE_DEFAULTS, CURVES } from '../core/measurements.js';
import { SRC_LIST } from '../core/constants.js';

const PROPS = ['x', 'y', 'sx', 'sy', 'rot', 'op', 'thick', 'maxH', 'n', 'glow', 'blur', 'hue', 'bright', 'contr', 'sat', 'sens', 'r0', 'fz'];

export default function MeasurementEditor({ layer }) {
  const updateLayer = useStore((s) => s.updateLayer);
  if (!layer) return null;
  const rules = layer.rules || [];

  const add = () => {
    updateLayer(layer._id, { rules: [...rules, { ...RULE_DEFAULTS, o0: layer.sy, o1: layer.sy + 30 }] });
  };
  const update = (i, patch) => {
    const next = rules.map((r, j) => (j === i ? { ...r, ...patch } : r));
    updateLayer(layer._id, { rules: next });
  };
  const remove = (i) => updateLayer(layer._id, { rules: rules.filter((_, j) => j !== i) });

  return (
    <div>
      <button onClick={add} style={{ marginBottom: 8 }}>+ Add rule</button>
      {rules.map((r, i) => (
        <div key={i} className="rl">
          <div className="row">
            <label className="dm">
              <input type="checkbox" checked={!!r.on}
                onChange={(e) => update(i, { on: e.target.checked ? 1 : 0 })} /> On
            </label>
            <select value={r.src} onChange={(e) => update(i, { src: e.target.value })}>
              {SRC_LIST.map((s) => <option key={s}>{s}</option>)}
            </select>
            <select value={r.prop} onChange={(e) => update(i, { prop: e.target.value })}>
              {PROPS.map((p) => <option key={p}>{p}</option>)}
            </select>
            <button className="dg" onClick={() => remove(i)}>
              <svg className="i sm"><use href="#i-trash" /></svg>
            </button>
          </div>
          <div className="row">
            <label className="dm">In min
              <input type="number" className="nm" value={r.i0} step="0.05"
                onChange={(e) => update(i, { i0: +e.target.value })} />
            </label>
            <label className="dm">In max
              <input type="number" className="nm" value={r.i1} step="0.05"
                onChange={(e) => update(i, { i1: +e.target.value })} />
            </label>
            <label className="dm">Out min
              <input type="number" className="nm" value={r.o0} step="1"
                onChange={(e) => update(i, { o0: +e.target.value })} />
            </label>
            <label className="dm">Out max
              <input type="number" className="nm" value={r.o1} step="1"
                onChange={(e) => update(i, { o1: +e.target.value })} />
            </label>
          </div>
          <div className="row">
            <select value={r.cv} onChange={(e) => update(i, { cv: e.target.value })}>
              {Object.keys(CURVES).map((c) => <option key={c}>{c}</option>)}
            </select>
            <label className="dm">
              <input type="checkbox" checked={!!r.inv}
                onChange={(e) => update(i, { inv: e.target.checked ? 1 : 0 })} /> Invert
            </label>
            <label className="dm">Attack
              <input type="number" className="nm" value={r.att}
                onChange={(e) => update(i, { att: +e.target.value })} />
            </label>
            <label className="dm">Release
              <input type="number" className="nm" value={r.rel}
                onChange={(e) => update(i, { rel: +e.target.value })} />
            </label>
          </div>
        </div>
      ))}
    </div>
  );
}