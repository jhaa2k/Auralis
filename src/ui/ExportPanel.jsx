import { useState, useRef } from 'react';
import { useStore } from '../core/projectStore.js';
import { play, pause, isPaused, getContext, getDestinationStream } from '../core/audioEngine.js';

export default function ExportPanel() {
  const settings = useStore((s) => s.settings);
  const updateSettings = useStore((s) => s.updateSettings);
  const pushToast = useStore((s) => s.pushToast);
  
  const [recording, setRecording] = useState(false);
  const [progress, setProgress] = useState(0);
  const [outputUrl, setOutputUrl] = useState(null);
  const recRef = useRef(null);
  const chunksRef = useRef([]);

  const handleExport = async () => {
    const canvas = document.getElementById('cv');
    if (!canvas) return pushToast('Canvas not ready', 'err');

    // 1. Grab Audio Context & Stream
    const ac = getContext();
    if (!ac) return pushToast('Load audio first to initialize engine', 'err');
    if (ac.state === 'suspended') await ac.resume();

    // 2. Request Folder Permission DIRECTLY inside the click event
    let dirHandle = null;
    try {
      if (window.showDirectoryPicker) {
        dirHandle = await window.showDirectoryPicker({ mode: 'readwrite', startIn: 'downloads' });
        // Verify we actually have permission
        if (await dirHandle.queryPermission({ mode: 'readwrite' }) !== 'granted') {
          await dirHandle.requestPermission({ mode: 'readwrite' });
        }
      }
    } catch (err) {
      console.warn("Folder picker cancelled or denied", err);
      pushToast('Folder access denied. Will download normally.', 'err');
      dirHandle = null;
    }

    // 3. Setup Streams
    if (!isPaused()) pause();
    play(); // Start playing so the canvas moves

    const audioStream = getDestinationStream();
    const videoStream = canvas.captureStream(settings.xfps || 30);
    
    // Combine audio and video
    const combinedStream = new MediaStream([
      ...videoStream.getVideoTracks(),
      ...(audioStream ? audioStream.getAudioTracks() : [])
    ]);

    // 4. Determine Format
    const formats = [
      { mime: 'video/mp4;codecs=avc1,mp4a.40.2', ext: 'mp4' },
      { mime: 'video/webm;codecs=vp9,opus', ext: 'webm' },
      { mime: 'video/webm;codecs=vp8,opus', ext: 'webm' },
      { mime: 'video/webm', ext: 'webm' }
    ];
    const fmt = formats[settings.fmt] || formats[1]; // Default to WebM VP9
    const finalMime = MediaRecorder.isTypeSupported(fmt.mime) ? fmt.mime : 'video/webm';

    // 5. Start Recorder
    try {
      const recorder = new MediaRecorder(combinedStream, {
        mimeType: finalMime,
        videoBitsPerSecond: (settings.mbps || 8) * 1000000,
        audioBitsPerSecond: (settings.abr || 192) * 1000
      });

      chunksRef.current = [];
      recorder.ondataavailable = (e) => {
        if (e.data && e.data.size > 0) chunksRef.current.push(e.data);
      };

      recorder.onstop = async () => {
        setRecording(false);
        setProgress(100);
        const blob = new Blob(chunksRef.current, { type: finalMime.split(';')[0] });
        const fileName = `audiovis_export_${Date.now()}.${fmt.ext}`;

        // Save to Folder if permission was granted
        if (dirHandle) {
          try {
            const fileHandle = await dirHandle.getFileHandle(fileName, { create: true });
            const writable = await fileHandle.createWritable();
            await writable.write(blob);
            await writable.close();
            pushToast(`Saved directly to folder!`, 'ok');
            return; // Exit early since it saved
          } catch (err) {
            pushToast('Failed to save to folder, using Download.', 'err');
          }
        }

        // Fallback: Download to default Downloads folder
        setOutputUrl(URL.createObjectURL(blob));
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = fileName;
        a.click();
        pushToast('Video Downloaded!', 'ok');
      };

      recRef.current = recorder;
      recorder.start(500); // Collect chunks every 500ms
      setRecording(true);
      setProgress(0);
      pushToast('Recording started', 'ok');

      // 6. Update Progress
      const start = Date.now();
      const duration = settings.t1 > 0 ? settings.t1 : 180; // Fake max progress if unknown
      const interval = setInterval(() => {
        if (recorder.state === 'inactive') {
          clearInterval(interval);
          return;
        }
        const elapsed = (Date.now() - start) / 1000;
        setProgress(Math.min(99, (elapsed / duration) * 100));
      }, 500);

    } catch (e) {
      pushToast('Failed to start recorder: ' + e.message, 'err');
    }
  };

  const stopRec = () => {
    if (recRef.current && recRef.current.state !== 'inactive') {
      recRef.current.stop();
      pause();
    }
  };

  return (
    <div>
      <div className="x-grid">
        <label>Aspect
          <select value={settings.ratio} onChange={(e) => updateSettings({ ratio: e.target.value })}>
            {['16:9', '9:16', '1:1', '4:5', '4:3', '21:9'].map((o) => <option key={o}>{o}</option>)}
          </select>
        </label>
        <label>Quality
          <select value={settings.res} onChange={(e) => updateSettings({ res: +e.target.value })}>
            {[480, 720, 1080, 2160].map((o) => <option key={o} value={o}>{o}p</option>)}
          </select>
        </label>
      </div>
      
      <div className="x-grid">
        <label>FPS
          <select value={settings.xfps} onChange={(e) => updateSettings({ xfps: +e.target.value })}>
            {[24, 30, 60].map((o) => <option key={o}>{o}</option>)}
          </select>
        </label>
        <label>Format
          <select value={settings.fmt} onChange={(e) => updateSettings({ fmt: +e.target.value })}>
            <option value={0}>MP4 (H.264)</option>
            <option value={1}>WebM (VP9)</option>
          </select>
        </label>
      </div>

      <div style={{ background: 'var(--p2)', borderRadius: 'var(--r)', padding: '8px 14px', marginBottom: 8 }}>
        <Range label="Video Mbps" value={settings.mbps} min={1} max={40} step={1} onChange={(v) => updateSettings({ mbps: v })} />
        <Range label="Audio kbps" value={settings.abr} min={64} max={320} step={32} onChange={(v) => updateSettings({ abr: v })} />
      </div>
      
      {recording && (
        <div style={{ marginBottom: 12 }}>
          <div style={{ width: '100%', height: '3px', background: 'var(--ln)', borderRadius: '2px', overflow: 'hidden' }}>
            <div style={{ width: `${progress}%`, height: '100%', background: 'var(--ac)', transition: 'width 0.2s linear' }} />
          </div>
          <div className="dm" style={{ textAlign: 'center', marginTop: 6 }}>
            Rendering... {progress.toFixed(0)}%
          </div>
        </div>
      )}

      <div className="row" style={{ marginTop: 8 }}>
        {!recording ? (
          <button className="pri" onClick={handleExport} style={{ flex: 1 }}>Export Video</button>
        ) : (
          <button className="dg" onClick={stopRec} style={{ flex: 1 }}>Stop Recording</button>
        )}
      </div>
      
      {outputUrl && (
        <div style={{ marginTop: 12 }}>
          <video controls src={outputUrl} style={{ width: '100%', borderRadius: 'var(--r)' }} />
          <div className="dm" style={{ textAlign: 'center', marginTop: 4 }}>Preview ready. If it didn't auto-download, click below.</div>
          <a className="btn" href={outputUrl} download="audiovis-export.webm" style={{ marginTop: 8 }}>Save manually</a>
        </div>
      )}
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