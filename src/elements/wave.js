import { getAnalyser } from '../core/audioEngine.js';
import { fillFor } from './bars.js';
import { TAU } from '../core/constants.js';

function getTimeDomain() {
  const an = getAnalyser();
  if (!an) return new Uint8Array(1024).fill(128);
  const td = new Uint8Array(an.fftSize);
  an.getByteTimeDomainData(td);
  return td;
}

export function renderWave(ctx, W, H, t, e) { drawTrace(ctx, W, H, t, e, false); }
export function renderWaveCircular(ctx, W, H, t, e) { drawTrace(ctx, W, H, t, e, true); }

export function renderScope(ctx, W, H, t, e) {
  ctx.save();
  ctx.globalAlpha *= 0.2;
  ctx.lineWidth = 1;
  ctx.strokeStyle = e.color;
  const w = W * 0.8;
  for (let i = 0; i <= 8; i++) {
    ctx.beginPath();
    ctx.moveTo(-w / 2 + (w * i) / 8, -H * 0.25);
    ctx.lineTo(-w / 2 + (w * i) / 8, H * 0.25);
    ctx.stroke();
  }
  for (let i = 0; i <= 4; i++) {
    ctx.beginPath();
    ctx.moveTo(-w / 2, -H * 0.25 + (H * 0.5 * i) / 4);
    ctx.lineTo(w / 2, -H * 0.25 + (H * 0.5 * i) / 4);
    ctx.stroke();
  }
  ctx.restore();
  drawTrace(ctx, W, H, t, e, false);
}

function drawTrace(ctx, W, H, t, e, circle) {
  const td = getTimeDomain();
  const R = Math.min(W, H) * 0.22;
  const w = W * 0.8;
  const p = Math.max(16, Math.min(td.length, e.n * 4));
  const A = (circle ? R * 0.4 : H * 0.2) * (e.sens / 100) * (e.maxH / 100);

  ctx.lineJoin = 'round';
  ctx.lineCap = 'round';
  ctx.lineWidth = (e.thick / 10) + 1;
  ctx.strokeStyle = fillFor(ctx, e, -w / 2, -H * 0.2, w, H * 0.4, t);
  ctx.fillStyle = ctx.strokeStyle;

  const pt = (i, m) => {
    const k = Math.floor((i / p) * (td.length - 1));
    const v = ((td[k] - 128) / 128) * A * m;
    if (circle) {
      const a = (i / p) * TAU - Math.PI / 2;
      return [Math.cos(a) * (R * e.r0 / 100 + v), Math.sin(a) * (R * e.r0 / 100 + v)];
    }
    return [-w / 2 + (i / p) * w, v];
  };

  for (const m of (e.mir !== 'none' && !circle) ? [1, -1] : [1]) {
    ctx.beginPath();
    for (let i = 0; i <= p; i++) {
      const [x, y] = pt(i, m);
      if (e.ws === 'spiky' && !circle) {
        ctx.moveTo(x, 0);
        ctx.lineTo(x, y);
      } else if (i) ctx.lineTo(x, y);
      else ctx.moveTo(x, y);
    }
    if (circle) ctx.closePath();
    if (e.wfill && !circle) {
      const a0 = ctx.globalAlpha;
      ctx.lineTo(w / 2, 0);
      ctx.lineTo(-w / 2, 0);
      ctx.globalAlpha = a0 * 0.35;
      ctx.fill();
      ctx.globalAlpha = a0;
    }
    ctx.stroke();
  }
}