import { useRef, useEffect, useState } from 'react';
import { useStore } from '../core/projectStore.js';
import { renderFrame } from '../core/renderer.js';
import { analyse } from '../core/analyser.js';
import { useAnimationLoop } from '../hooks/useAnimationLoop.js';

export default function Stage() {
  const canvasRef = useRef(null);
  
  // Subscribe to everything needed so React renders the newest state
  const layers = useStore((s) => s.layers);
  const scene = useStore((s) => s.scene);
  const settings = useStore((s) => s.settings);
  const selectedId = useStore((s) => s.selectedId);
  const updateLayer = useStore((s) => s.updateLayer);
  
  const [drag, setDrag] = useState(null);
  const [res, setRes] = useState('');

  // Keep refs up to date for the animation loop
  const stateRef = useRef({ layers, scene, settings });
  useEffect(() => {
    stateRef.current = { layers, scene, settings };
  }, [layers, scene, settings]);

  useAnimationLoop((now) => {
    const cv = canvasRef.current;
    if (!cv) return;
    const ctx = cv.getContext('2d', { alpha: false, willReadFrequently: false });
    const s = stateRef.current;
    
    analyse(now, s.settings);
    renderFrame(ctx, cv.width, cv.height, now / 1000, { layers: s.layers, scene: s.scene });

    if (s.settings.guides) {
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

  // Update Resolution / Quality based on selected settings
  useEffect(() => {
    const cv = canvasRef.current;
    if (!cv) return;
    
    let baseW = 960;
    let baseH = 540;
    if (settings.ratio === '9:16') { baseW = 540; baseH = 960; }
    else if (settings.ratio === '1:1') { baseW = 720; baseH = 720; }
    
    const scale = settings.pq / 100;
    cv.width = Math.round(baseW * scale);
    cv.height = Math.round(baseH * scale);
    setRes(`${cv.width}×${cv.height} (${settings.pq}%)`);
  }, [settings.pq, settings.ratio]);

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