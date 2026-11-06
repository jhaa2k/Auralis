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

  useEffect(() => {
    let raf;
    const tick = () => {
      setTime(getCurrentTime());
      setDur(getDuration());
      setPlaying(!isPaused());
      raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, []);

  const togglePlay = () => {
    if (isPaused()) { play(); setAudioState({ playing: true }); }
    else { pause(); setAudioState({ playing: false }); }
  };

  const onSeek = (e) => {
    const r = wfRef.current.getBoundingClientRect();
    seek(((e.clientX - r.left) / r.width) * dur);
  };

  return (
    <div className="tp">
      <div className="tr1">
        <span>No track loaded</span>
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
      <div className="wfw" ref={wfRef} onClick={onSeek}>
        <canvas id="wf" />
        <div id="ph" style={{ transform: `translateX(${dur ? (time / dur) * 100 : 0}%)` }} />
      </div>
    </div>
  );
}