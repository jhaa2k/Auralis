import { useStore } from '../core/projectStore.js';
import { initAudio, loadBuffer, getContext } from '../core/audioEngine.js';
import { scanBeats, setBeatMap } from '../core/analyser.js';

export default function MediaPanel() {
  const audio = useStore((s) => s.audio);
  const setAudioState = useStore((s) => s.setAudioState);
  const settings = useStore((s) => s.settings);
  const pushToast = useStore((s) => s.pushToast);

  const handleFiles = async (e) => {
    const files = [...e.target.files];
    e.target.value = '';
    if (!files.length) return;
    if (!initAudio()) { pushToast('Web Audio is not supported here', 'err'); return; }
    const ac = getContext();
    await ac.resume();

    const newFiles = [];
    for (const f of files) {
      try {
        const ab = await f.arrayBuffer();
        const buf = await ac.decodeAudioData(ab.slice(0));
        newFiles.push({ name: f.name, buffer: buf });
      } catch { pushToast(`Cannot decode ${f.name}`, 'err'); }
    }
    if (!newFiles.length) return;

    const all = [...audio.files, ...newFiles];
    setAudioState({ files: all, cur: audio.cur < 0 ? 0 : audio.cur });

    if (audio.cur < 0) {
      const first = newFiles[0];
      loadBuffer(first.buffer);
      setAudioState({ cur: all.length - newFiles.length, duration: first.buffer.duration, playing: false });
      const beats = scanBeats(first.buffer, settings);
      setBeatMap(beats);
      pushToast(`${beats.length} beats detected`, 'ok');
    }
  };

  return (
    <div>
      <div className="sec">Import</div>
      <label className="btn" htmlFor="fi">Choose audio files</label>
      <input id="fi" className="hid" type="file" accept="audio/*,video/mp4,.mp3,.wav,.flac,.aac,.ogg,.m4a,.aif" multiple onChange={handleFiles} />
      <div className="sec">Queue</div>
      <div id="queue" style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {audio.files.length === 0 && <div className="dm">No tracks yet. Choose audio files above.</div>}
        {audio.files.map((f, i) => (
          <div key={i} className={`it ${i === audio.cur ? 'sl' : ''}`}>
            <button onClick={() => {
              loadBuffer(f.buffer);
              setAudioState({ cur: i, duration: f.buffer.duration });
              const beats = scanBeats(f.buffer, settings);
              setBeatMap(beats);
            }}>{f.name}</button>
          </div>
        ))}
      </div>
      <div className="sec">Sleep timer</div>
      <select><option value="0">Off</option><option value="15">15 min</option><option value="30">30 min</option><option value="45">45 min</option><option value="60">60 min</option></select>
      <div className="dm">Audio is decoded in memory. Files stay on this device.</div>
    </div>
  );
}