import { useStore } from '../core/projectStore.js';
import { SCENE_DEFAULTS, SCENE_PRESETS } from '../core/constants.js';

export default function ScenePanel() {
  const scene = useStore((s) => s.scene);
  const setScene = useStore((s) => s.setScene);
  const pushToast = useStore((s) => s.pushToast);

  const set = (k, v) => setScene({ ...scene, [k]: v });

  return (
    <div>
      <label className="f" style={{ marginBottom: 8 }}>
        <span>Scene Preset</span>
        <select onChange={(e) => {
          const preset = SCENE_PRESETS[e.target.value];
          if (preset) { setScene(preset); pushToast(`Applied ${e.target.value}`, 'ok'); }
        }}>
          <option value="">-- Custom --</option>
          {Object.keys(SCENE_PRESETS).map((k) => <option key={k}>{k}</option>)}
        </select>
      </label>

      <div style={{ background: 'var(--p2)', borderRadius: 'var(--r)', padding: '8px 14px', marginBottom: 8 }}>
        <label className="cr">
          <span>Background</span>
          <input type="color" value={scene.bg} onChange={(e) => set('bg', e.target.value)} />
          <span />
        </label>
        <Range label="Camera zoom" value={scene.cam} min={0} max={30} step={0.5} onChange={(v) => set('cam', v)} />
        <Range label="Vignette" value={scene.vig} min={0} max={100} step={1} onChange={(v) => set('vig', v)} />
        <Range label="Scanlines" value={scene.scan} min={0} max={100} step={1} onChange={(v) => set('scan', v)} />
        <Range label="Film grain" value={scene.grain} min={0} max={100} step={1} onChange={(v) => set('grain', v)} />
        <Range label="Beat flash" value={scene.flash} min={0} max={100} step={1} onChange={(v) => set('flash', v)} />
        <Range label="Beat shake" value={scene.shake} min={0} max={40} step={1} onChange={(v) => set('shake', v)} />
        <Range label="Hue jump per beat" value={scene.hueB} min={0} max={90} step={1} onChange={(v) => set('hueB', v)} />
      </div>
      <button onClick={() => { setScene({ ...SCENE_DEFAULTS }); pushToast('Scene reset', 'ok'); }}>Reset scene</button>
    </div>
  );
}

function Range({ label, value, min, max, step, onChange }) {
  return (
    <div className="rg">
      <span>{label}</span>
      <input type="range" min={min} max={max} step={step} value={value} onChange={(e) => onChange(+e.target.value)} />
      <input type="number" className="nm" min={min} max={max} step={step} value={value} onChange={(e) => onChange(+e.target.value)} />
    </div>
  );
}