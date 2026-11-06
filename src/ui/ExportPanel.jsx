import { useState, useRef } from 'react';
import { useStore } from '../core/projectStore.js';
import { play, pause, isPaused } from '../core/audioEngine.js';
import { startRecord, listFormats } from '../core/exportEngine.js';

export default function ExportPanel() {
  const settings = useStore((s) => s.settings);
  const updateSettings = useStore((s) => s.updateSettings);
  const pushToast = useStore((s) => s.pushToast);
  const [recording, setRecording] = useState(false);
  const [outputUrl, setOutputUrl] = useState(null);
  const recRef = useRef(null);

  const formats = listFormats();

  const startRec = () => {
    const canvas = document.getElementById('cv');
    if (!canvas) return;
    if (!isPaused()) pause();
    play();
    const rec = startRecord(
      canvas,
      settings,
      (blob, name) => {
        setOutputUrl(URL.createObjectURL(blob));
        setRecording(false);
        pushToast('Export finished', 'ok');
      },
      (err) => { pushToast(err, 'err'); setRecording(false); }
    );
    if (rec) { recRef.current = rec; setRecording(true); pushToast('Recording started', 'ok'); }
  };

  const stopRec = () => {
    if (recRef.current && recRef.current.state !== 'inactive') recRef.current.stop();
  };

  return (
    <div>
      <div className="x-grid">
        <label>Aspect
          <select value={settings.ratio} onChange={(e) => updateSettings({ ratio: e.target.value })}>
            {['16:9', '9:16', '1:1', '4:5', '4:3', '21:9', 'custom'].map((o) => <option key={o}>{o}</option>)}
          </select>
        </label>
        <label>Short side
          <select value={settings.res} onChange={(e) => updateSettings({ res: +e.target.value })}>
            {[360, 480, 720, 1080, 1440, 2160].map((o) => <option key={o}>{o}</option>)}
          </select>
        </label>
      </div>
      <div className="x-grid">
        <label>FPS
          <select value={settings.xfps} onChange={(e) => updateSettings({ xfps: +e.target.value })}>
            {[24, 25, 30, 50, 60].map((o) => <option key={o}>{o}</option>)}
          </select>
        </label>
        <label>Format
          <select value={settings.fmt} onChange={(e) => updateSettings({ fmt: +e.target.value })}>
            {formats.map((f, i) => <option key={i} value={i}>{f[1]}</option>)}
          </select>
        </label>
      </div>
      <div style={{ background: 'var(--p2)', borderRadius: 'var(--r)', padding: '8px 14px', marginBottom: 8 }}>
        <Range label="Video Mbps" value={settings.mbps} min={1} max={40} step={1} onChange={(v) => updateSettings({ mbps: v })} />
        <Range label="Audio kbps" value={settings.abr} min={64} max={320} step={32} onChange={(v) => updateSettings({ abr: v })} />
        <Range label="Start s" value={settings.t0} min={0} max={3600} step={1} onChange={(v) => updateSettings({ t0: v })} />
        <Range label="End s (0=end)" value={settings.t1} min={0} max={3600} step={1} onChange={(v) => updateSettings({ t1: v })} />
      </div>
      <div className="row" style={{ marginTop: 8 }}>
        {!recording ? (
          <button className="pri" onClick={startRec}>Export</button>
        ) : (
          <button className="dg" onClick={stopRec}>Stop</button>
        )}
      </div>
      {outputUrl && (
        <div>
          <video controls src={outputUrl} style={{ width: '100%', borderRadius: 'var(--r)', marginTop: 8 }} />
          <a className="btn" href={outputUrl} download="audiovis-export.webm" style={{ marginTop: 8 }}>Download</a>
        </div>
      )}
      <div className="dm" style={{ marginTop: 8 }}>Export runs in real time with the real audio. Keep this tab open.</div>
    </div>
  );
}

function Range({ label, value, min, max, step, onChange }) {
  return (
    <div className="rg">
      <span>{label}</span>
      <input type="range" min={min} max={max} step={step} value={value}
        onChange={(e) => onChange(+e.target.value)} />
      <input type="number" className="nm" min={min} max={max} step={step} value={value}
        onChange={(e) => onChange(+e.target.value)} />
    </div>
  );
}