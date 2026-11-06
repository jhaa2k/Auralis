import { useStore } from '../core/projectStore.js';
import { applyAudioSettings, EQ_FREQS } from '../core/audioEngine.js';
import { triggerTap, estimateBPM } from '../core/analyser.js';

export default function AudioPanel() {
  const settings = useStore((s) => s.settings);
  const updateSettings = useStore((s) => s.updateSettings);
  const pushToast = useStore((s) => s.pushToast);

  const set = (patch) => {
    updateSettings(patch);
    applyAudioSettings({ ...settings, ...patch });
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      <details>
        <summary>Playback</summary>
        <div>
          <Range label="Volume %" value={settings.vol} min={0} max={100} step={1} onChange={(v) => set({ vol: v })} />
          <Range label="Preamp dB" value={settings.pre} min={-12} max={12} step={0.5} onChange={(v) => set({ pre: v })} />
          <Range label="Bass boost dB" value={settings.bass} min={0} max={15} step={0.5} onChange={(v) => set({ bass: v })} />
          <Range label="Balance" value={settings.bal} min={-100} max={100} step={1} onChange={(v) => set({ bal: v })} />
          <Range label="Speed %" value={settings.speed} min={50} max={150} step={1} onChange={(v) => set({ speed: v })} />
        </div>
      </details>
      
      <details>
        <summary>Equalizer</summary>
        <div>
          {EQ_FREQS.map((f, i) => (
            <Range key={f} label={f >= 1000 ? `${f / 1000}k` : f} value={settings.eq[i]} min={-12} max={12} step={0.5}
              onChange={(v) => { const eq = [...settings.eq]; eq[i] = v; set({ eq }); }} />
          ))}
        </div>
      </details>

      <details>
        <summary>Effects</summary>
        <div>
          <Range label="Reverb" value={settings.rev} min={0} max={100} step={1} onChange={(v) => set({ rev: v })} />
          <Range label="Delay" value={settings.dly} min={0} max={100} step={1} onChange={(v) => set({ dly: v })} />
          <Range label="Distortion" value={settings.dist} min={0} max={100} step={1} onChange={(v) => set({ dist: v })} />
        </div>
      </details>

      <details>
        <summary>Analysis & Beat</summary>
        <div>
          <label className="f">
            <span>FFT size</span>
            <select value={settings.fft} onChange={(e) => set({ fft: +e.target.value })}>
              {[256, 512, 1024, 2048, 4096, 8192].map((n) => <option key={n} value={n}>{n}</option>)}
            </select>
          </label>
          <Range label="Smoothing" value={settings.smooth} min={0} max={95} step={1} onChange={(v) => set({ smooth: v })} />
          <Range label="Noise floor" value={settings.floor} min={0} max={60} step={1} onChange={(v) => set({ floor: v })} />
          <Range label="Beat level" value={settings.beat} min={110} max={250} step={1} onChange={(v) => set({ beat: v })} />
          
          <label className="f" style={{ marginTop: 8 }}>
            <span>Beat Mode</span>
            <select value={settings.bmode} onChange={(e) => set({ bmode: e.target.value })}>
              <option value="auto">Auto (live)</option>
              <option value="map">Beat map</option>
              <option value="bpm">BPM grid</option>
              <option value="off">Off</option>
            </select>
          </label>
          <Range label="Min level %" value={settings.bmin} min={5} max={90} step={1} onChange={(v) => set({ bmin: v })} />
          <Range label="BPM" value={settings.bpm} min={40} max={220} step={0.5} onChange={(v) => set({ bpm: v })} />
          <div className="row" style={{ marginTop: 8 }}>
            <button onClick={() => { triggerTap(); pushToast('Tap registered', 'ok'); }}>Tap tempo</button>
            <button onClick={() => {
              const bpm = estimateBPM([]);
              if (bpm) { set({ bpm }); pushToast(`Detected ${bpm} BPM`, 'ok'); }
              else pushToast('Not enough beats scanned', 'err');
            }}>Detect BPM</button>
          </div>
        </div>
      </details>

      <details>
        <summary>Modulators (LFO)</summary>
        <div>
          <Range label="LFO 1 Hz" value={settings.lfo1} min={0.05} max={4} step={0.05} onChange={(v) => set({ lfo1: v })} />
          <Range label="LFO 2 Hz" value={settings.lfo2} min={0.05} max={4} step={0.05} onChange={(v) => set({ lfo2: v })} />
        </div>
      </details>
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