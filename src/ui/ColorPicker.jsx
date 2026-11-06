import { useState, useRef, useEffect } from 'react';
import { hsvToRgb, rgbToHex, hexToHsv } from '../core/colorUtils.js';

export default function ColorPicker({ color, onChange, label }) {
  const [open, setOpen] = useState(false);
  const [hsv, setHsv] = useState(hexToHsv(color));
  const wheelRef = useRef(null);

  useEffect(() => { setHsv(hexToHsv(color)); }, [color]);

  const handlePointer = (e) => {
    if (!wheelRef.current) return;
    const rect = wheelRef.current.getBoundingClientRect();
    const x = e.clientX - rect.left - rect.width / 2;
    const y = e.clientY - rect.top - rect.height / 2;
    const r = Math.min(rect.width, rect.height) / 2;
    const dist = Math.min(r, Math.hypot(x, y));
    let ang = Math.atan2(y, x) * 180 / Math.PI + 90;
    if (ang < 0) ang += 360;
    const s = dist / r;
    const newHsv = { h: ang, s, v: 1 };
    setHsv(newHsv);
    onChange(rgbToHex(hsvToRgb(newHsv.h, newHsv.s, newHsv.v)));
  };

  const dotPos = () => {
    const r = 80;
    const rad = (hsv.h - 90) * Math.PI / 180;
    const x = Math.cos(rad) * hsv.s * r + 80;
    const y = Math.sin(rad) * hsv.s * r + 80;
    return { left: `${x}px`, top: `${y}px` };
  };

  return (
    <div className="cr" style={{ position: 'relative' }}>
      <span>{label}</span>
      <div className="color-preview" style={{ background: color }} onClick={() => setOpen(!open)} />
      <input className="hx" value={color} onChange={(e) => { if (/^#[0-9a-f]{6}$/i.test(e.target.value)) onChange(e.target.value); }} maxLength={7} />
      {open && (
        <div className="color-wheel-popup">
          <div ref={wheelRef} className="color-wheel" onPointerDown={handlePointer} onPointerMove={(e) => { if (e.buttons === 1) handlePointer(e); }}>
            <div className="color-wheel-inner" />
            <div className="color-dot" style={dotPos()} />
          </div>
          <input type="range" min="0" max="1" step="0.01" value={hsv.s} onChange={(e) => {
            const newS = parseFloat(e.target.value);
            setHsv({ ...hsv, s: newS });
            onChange(rgbToHex(hsvToRgb(hsv.h, newS, hsv.v)));
          }} style={{ width: '100%', marginBottom: '8px' }} />
          <button onClick={() => setOpen(false)} style={{ width: '100%', padding: '4px' }}>Done</button>
        </div>
      )}
    </div>
  );
}