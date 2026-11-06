import { useRef, useEffect, useState } from 'react';
import { useStore } from '../core/projectStore.js';
import { renderFrame } from '../core/renderer.js';
import { analyse } from '../core/analyser.js';
import { useAnimationLoop } from '../hooks/useAnimationLoop.js';

export default function Stage() {
  const canvasRef = useRef(null);
  const layers = useStore((s) => s.layers);
  const scene = useStore((s) => s.scene);
  const settings = useStore((s) => s.settings);
  const selectedId = useStore((s) => s.selectedId);
  const updateLayer = useStore((s) => s.updateLayer);
  const [drag, setDrag] = useState(null);
  const [res, setRes] = useState('');

  useAnimationLoop((now) => {
    const cv = canvasRef.current;
    if (!cv) return;
    const ctx = cv.getContext('2d');
    analyse(now, settings);
    renderFrame(ctx, cv.width, cv.height, now / 1000, { layers, scene });

    if (settings.guides) {
      const u = cv.height / 720;
      ctx.strokeStyle = 'rgba(255,255,255,.3)';
      ctx.lineWidth = u;
      ctx.beginPath();
      ctx.moveTo(cv.width / 2, 0);
      ctx.lineTo(cv.width / 2, cv.height);
      ctx.moveTo(0, cv.height / 2);
      ctx.lineTo(cv.width, cv.height / 2);
      ctx.stroke();
    }
  });

  useEffect(() => {
    const cv = canvasRef.current;
    if (!cv) return;
    const w = Math.round((cv.clientWidth * settings.pq) / 100);
    const h = Math.round((cv.clientHeight * settings.pq) / 100);
    cv.width = w;
    cv.height = h;
    setRes(`${w}×${h}`);
  }, [settings.pq, settings.res, settings.ratio]);

  const onPointerDown = (e) => {
    const layer = layers.find((l) => l._id === selectedId);
    if (!layer || layer.lock || layer.type === 'bg') return;
    const cv = canvasRef.current;
    const r = cv.getBoundingClientRect();
    setDrag({ sx: e.clientX, sy: e.clientY, x: layer.x, y: layer.y, w: r.width, h: r.height });
    cv.setPointerCapture(e.pointerId);
  };
  const onPointerMove = (e) => {
    if (!drag) return;
    const dx = ((e.clientX - drag.sx) / drag.w) * 100;
    const dy = ((e.clientY - drag.sy) / drag.h) * 100;
    updateLayer(selectedId, {
      x: Math.max(0, Math.min(100, drag.x + dx)),
      y: Math.max(0, Math.min(100, drag.y + dy))
    });
  };
  const onPointerUp = () => setDrag(null);

  return (
    <div className="cvw">
      <canvas
        ref={canvasRef}
        id="cv"
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerUp}
      />
      <div className="hud">
        <span id="bdg">PREVIEW</span>
        <span>{res}</span>
        <span />
      </div>
    </div>
  );
}