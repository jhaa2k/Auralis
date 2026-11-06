import { useState, useRef } from 'react';
import { useStore } from '../core/projectStore.js';
import { play, pause, isPaused } from '../core/audioEngine.js';
import { startRecord, listFormats } from '../core/exportEngine.js';

export default function ExportPanel() {
  const settings = useStore((s) => s.settings);
  const updateSettings = useStore((s) => s.updateSettings);
  const pushToast = useStore((s) => s.pushToast);
  const [recording, setRecording] = useState(false);
  const [progress, setProgress] = useState(0);
  const [outputUrl, setOutputUrl] = useState(null);
  const recRef = useRef(null);

  const formats = listFormats();

  const handleExport = async () => {
    const canvas = document.getElementById('cv');
    if (!canvas) return;
    
    // File System Access API
    let dirHandle = null;
    try {
      if (!settings.exportDir && window.showDirectoryPicker) {
        dirHandle = await window.showDirectoryPicker({ mode: 'readwrite', startIn: 'downloads' });
        pushToast('Folder access granted', 'ok');
        // We can't store the handle in localStorage directly, but we can keep it in memory/IndexedDB if needed. 
        // For simplicity, we'll just use it for this session.
        window._exportDirHandle = dirHandle;
      } else {
        dirHandle = window._exportDirHandle;
      }
    } catch (err) {
      pushToast('Export cancelled or permission denied', 'err');
      return;
    }

    if (!isPaused()) pause();
    play();
    
    setProgress(0);
    const rec = startRecord(
      canvas,
      settings,
      async (blob, name) => {
        setRecording(false);
        setProgress(100);
        
        if (dirHandle) {
          try {
            const fileHandle = await dirHandle.getFileHandle(name, { create: true });
            const writable = await fileHandle.createWritable();
            await writable.write(blob);
            await writable.close();
            pushToast(`Saved to folder successfully`, 'ok');
            return;
          } catch (err) {
            pushToast('Failed to save to folder. Downloading instead.', 'err');
          }
        }
        
        // Fallback
        setOutputUrl(URL.createObjectURL(blob));
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = name;
        a.click();
        pushToast('Export downloaded', 'ok');
      },
      (err) => { pushToast(err, 'err'); setRecording(false); },
      (p) => setProgress(p) // Progress callback
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
      </div>
      
      {recording && (
        <div style={{ marginBottom: 8 }}>
          <progress value={progress} max="100" />
          <div className="dm" style={{ textAlign: 'center' }}>{progress.toFixed(0)}%</div>
        </div>
      )}

      <div className="row" style={{ marginTop: 8 }}>
        {!recording ? (
          <button className="pri" onClick={handleExport}>Export Video</button>
        ) : (
          <button className="dg" onClick={stopRec}>Stop Recording</button>
        )}
      </div>
      
      {outputUrl && (
        <div>
          <video controls src={outputUrl} style={{ width: '100%', borderRadius: 'var(--r)', marginTop: 8 }} />
          <a className="btn" href={outputUrl} download="audiovis-export.webm" style={{ marginTop: 8 }}>Download manually</a>
        </div>
      )}
      <div className="dm" style={{ marginTop: 8 }}>Export runs in real-time. For the best quality, use a desktop browser.</div>
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