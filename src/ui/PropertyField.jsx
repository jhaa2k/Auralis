import { FONTS, SHAPES, BLEND_MODES, SRC_LIST, BEAT_TRIGGERS } from '../core/constants.js';

const SELECTS = {
  font: FONTS,
  al: ['left', 'center', 'right'],
  blend: BLEND_MODES,
  dir: ['up', 'down', 'both'],
  odir: ['out', 'in', 'both'],
  mir: ['none', 'mirror', 'sym'],
  ws: ['line', 'spiky'],
  shape: SHAPES,
  imgMask: ['none', 'circle'],
  band: ['custom', 'all', 'sub', 'bass', 'lowmid', 'mid', 'upmid', 'presence', 'brill'],
  fsc: ['log', 'linear', 'mel'],
  grad: ['solid', 'linear', 'radial', 'conic'],
  anim: ['none', 'pulse', 'bounce', 'sway', 'fade'],
  rmode: ['continuous', 'pendulum', 'steps', 'random'],
  rease: ['linear', 'smooth', 'ease_in', 'ease_out'],
  rsrc: SRC_LIST,
  bsel: BEAT_TRIGGERS,
  mm: ['none', 'drift', 'orbit', 'figure8', 'bounce', 'shake'],
  barsShapePath: ['line', 'curve', 'step', 'diamond'],
  measureSpeed: ['none', 'bass', 'mid', 'treble', 'volume', 'beat'],
  motionSource: ['transform', 'measure', 'target'],
  relativeMotion: ['absolute', 'relative', 'target-relative']
};

export default function PropertyField({ def, value, onChange }) {
  const [key, label, kind, min, max, step] = def;
  const id = `f-${key}`;

  if (kind === 'r') {
    return (
      <div className="rg">
        <span>{label}</span>
        <input type="range" min={min} max={max} step={step} value={value ?? 0}
          onChange={(e) => onChange(key, +e.target.value)} />
        <input type="number" className="nm" min={min} max={max} step={step} value={value ?? 0}
          onChange={(e) => onChange(key, +e.target.value)} />
      </div>
    );
  }
  if (kind === 'k') {
    return (
      <label className="f" htmlFor={id}>
        <span>{label}</span>
        <input id={id} type="checkbox" checked={!!value}
          onChange={(e) => onChange(key, e.target.checked ? 1 : 0)} />
      </label>
    );
  }
  if (kind === 's') {
    return (
      <label className="f" htmlFor={id}>
        <span>{label}</span>
        <select id={id} value={value ?? ''} onChange={(e) => onChange(key, e.target.value)}>
          {(SELECTS[key] || []).map((o) => <option key={o} value={o}>{o}</option>)}
        </select>
      </label>
    );
  }
  if (kind === 'c') {
    return (
      <div className="cr">
        <span>{label}</span>
        <input type="color" value={value || '#000000'}
          onChange={(e) => onChange(key, e.target.value)} />
        <input className="hx" value={value || '#000000'} maxLength={7}
          onChange={(e) => { if (/^#[0-9a-f]{6}$/i.test(e.target.value)) onChange(key, e.target.value); }} />
      </div>
    );
  }
  if (kind === 't') {
    return (
      <label className="f" htmlFor={id}>
        <span>{label}</span>
        <input id={id} type="text" value={value ?? ''}
          onChange={(e) => onChange(key, e.target.value)} />
      </label>
    );
  }
  return null;
}