import { useStore } from '../core/projectStore.js';
import { play, pause, isPaused, getCurrentTime, getDuration, seek } from '../core/audioEngine.js';
import { fmtTime } from '../core/constants.js';
import { useEffect, useRef, useState } from 'react';

export default function Transport() {
  const setAudioState = useStore((s) => s.setAudioState);
  const [time, setTime] = useState(0);
  const [dur, setDur] = useState(0);
  const [playing, setPlaying] = useState(false);
  
  const wfRef = useRef(null);
  const phRef = useRef(null);

  useEffect(() => {
    let raf;
    const tick = () => {
      const t = getCurrentTime();
      const d = getDuration();
      setTime(t);
      setDur(d);
      setPlaying(!isPaused());

      // Safely update the playhead
      if (phRef.current && d > 0) {
        const pct = Math.max(0, Math.min(100, (t / d) * 100));
        phRef.current.style.left = `${pct}%`;
      }
      raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, []);

  const togglePlay = () => {
    if (isPaused()) { 
      play(); 
      setAudioState({ playing: true }); 
    } else { 
      pause(); 
      setAudioState({ playing: false }); 
    }
  };

  const onSeek = (e) => {
    if (!wfRef.current || dur === 0) return;
    const r = wfRef.current.getBoundingClientRect();
    const pct = Math.max(0, Math.min(1, (e.clientX - r.left) / r.width));
    seek(pct * dur);
  };

  return (
    <div className="tp">
      <div className="tr1">
        <span>{dur > 0 ? 'Track Loaded' : 'No track loaded'}</span>
        <span>{fmtTime(time)} / {fmtTime(dur)}</span>
      </div>
      <div className="tr2">
        <button><svg className="i"><use href="#i-shuffle" /></svg></button>
        <button><svg className="i"><use href="#i-prev" /></svg></button>
        <button id="pl" className="pri" onClick={togglePlay}>
          <svg className="i"><use href={playing ? '#i-pause' : '#i-play'} /></svg>
        </button>
        <button><svg className="i"><use href="#i-next" /></svg></button>
        <button><svg className="i"><use href="#i-repeat" /></svg></button>
      </div>
      
      {/* Container for waveform */}
      <div className="wfw" ref={wfRef} onClick={onSeek}>
        {/* Only render the canvas and playhead if there is audio loaded */}
        {dur > 0 && <canvas id="wf" />}
        {dur > 0 && <div id="ph" ref={phRef} />}
      </div>
    </div>
  );
}