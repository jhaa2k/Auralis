import { FONTS, SHAPES, BLEND_MODES, SRC_LIST, BEAT_TRIGGERS, COLOR_BEATS } from '../core/constants.js';
import ColorPicker from './ColorPicker.jsx';

const SELECTS = {
  font: FONTS, al: ['left', 'center', 'right'], blend: BLEND_MODES,
  dir: ['up', 'down', 'both'], odir: ['out', 'in', 'both'], mir: ['none', 'mirror', 'sym'],
  ws: ['line', 'spiky'], shape: SHAPES, imgMask: ['none', 'circle'],
  band: ['custom', 'all', 'sub', 'bass', 'lowmid', 'mid', 'upmid', 'presence', 'brill'],
  fsc: ['log', 'linear', 'mel'], grad: ['solid', 'linear', 'radial', 'conic'],
  anim: ['none', 'pulse', 'bounce', 'sway', 'fade'], rmode: ['continuous', 'pendulum', 'steps', 'random'],
  rease: ['linear', 'smooth', 'ease_in', 'ease_out'], rsrc: SRC_LIST, bsel: BEAT_TRIGGERS,
  mm: ['none', 'drift', 'orbit', 'figure8', 'bounce', 'shake'],
  barsShapePath: ['line', 'curve', 'step', 'diamond'], measureSpeed: ['none', 'bass', 'mid', 'treble', 'volume', 'beat'],
  motionSource: ['transform', 'measure', 'target'], relativeMotion: ['absolute', 'relative', 'target-relative'],
  beatColor: COLOR_BEATS
};

export default function PropertyField({ def, value, onChange }) {
  const [key, label, kind, min, max, step] = def;
  const id = `f-${key}`;

  if (kind === 'r') {
    return (
      <div className="rg">
        <span>{label}</span>
        <input type="range" min={min} max={max} step={step} value={value ?? 0} onChange={(e) => onChange(key, +e.target.value)} />
        <input type="number" className="nm" min={min} max={max} step={step} value={value ?? 0} onChange={(e) => onChange(key, +e.target.value)} />
      </div>
    );
  }
  if (kind === 'k') {
    return (
      <label className="f" htmlFor={id}>
        <span>{label}</span>
        <input id={id} type="checkbox" checked={!!value} onChange={(e) => onChange(key, e.target.checked ? 1 : 0)} />
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
    return <ColorPicker color={value || '#000000'} onChange={(v) => onChange(key, v)} label={label} />;
  }
  if (kind === 't') {
    return (
      <label className="f" htmlFor={id}>
        <span>{label}</span>
        <input id={id} type="text" value={value ?? ''} onChange={(e) => onChange(key, e.target.value)} />
      </label>
    );
  }
  if (kind === 'img') {
    return (
      <label className="btn" style={{ marginTop: 8 }}>
        Upload Image
        <input type="file" accept="image/*" className="hid" onChange={(e) => {
          const file = e.target.files[0];
          if (file) {
            const reader = new FileReader();
            reader.onload = (ev) => {
              const img = new Image();
              img.onload = () => onChange('_img', img);
              img.src = ev.target.result;
            };
            reader.readAsDataURL(file);
          }
        }} />
      </label>
    );
  }
  return null;
}