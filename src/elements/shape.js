import { bv, fillFor } from './bars.js';
import { TAU } from '../core/constants.js';

export function renderShape(ctx, W, H, t, e) {
  const R = Math.min(W, H) * 0.22;
  const v = bv(e, 0, 1);
  const r = R * (e.maxH / 100) * (0.6 + v * 0.6);
  ctx.fillStyle = fillFor(ctx, e, -r, -r, r * 2, r * 2, t);
  ctx.strokeStyle = e.sc;
  ctx.lineWidth = e.sw * (H / 720);
  if (e.shape === 'circle') { ctx.beginPath(); ctx.arc(0, 0, r, 0, TAU); }
  else if (e.shape === 'rect') { ctx.beginPath(); ctx.rect(-r, -r, r * 2, r * 2); }
  else if (e.shape === 'capsule') { ctx.beginPath(); ctx.ellipse(0, 0, r * 1.6, r * 0.6, 0, 0, TAU); }
  else poly(ctx, e.shape === 'triangle' ? 3 : e.sides, r, e.shape === 'star');
  ctx.fill();
  if (e.sw) ctx.stroke();
}

function poly(ctx, n, r, star) {
  ctx.beginPath();
  const m = star ? n * 2 : n;
  for (let i = 0; i < m; i++) {
    const a = (i / m) * TAU - Math.PI / 2;
    const q = star && i % 2 ? r * 0.45 : r;
    if (i) ctx.lineTo(Math.cos(a) * q, Math.sin(a) * q);
    else ctx.moveTo(Math.cos(a) * q, Math.sin(a) * q);
  }
  ctx.closePath();
}