import { getAnalyser } from '../core/audioEngine.js';
import { fpos, getMeasurement, getBeatTriggered } from '../core/analyser.js';
import { clamp, TAU } from '../core/constants.js';

export function bv(eff, i, n) {
  const an = getAnalyser();
  if (!an) return 0;
  const fd = new Uint8Array(an.frequencyBinCount);
  an.getByteFrequencyData(fd);
  const a = fpos(eff, i / n);
  const b = fpos(eff, (i + 1) / n);
  const sr = an.context.sampleRate;
  const hz = sr / 2 / fd.length;
  let i0 = Math.max(0, Math.floor(a / hz));
  let j0 = Math.max(i0 + 1, Math.min(fd.length, Math.ceil(b / hz)));
  let s = 0;
  for (let k = i0; k < j0; k++) s += fd[k];
  let v = s / (j0 - i0) / 255;
  const fl = 0.08;
  v = clamp((v - fl) / (1 - fl), 0, 1);
  eff._s = eff._s || [];
  const prev = eff._s[i] || 0;
  const resp = v > prev ? (eff.att || 60) / 100 : (eff.rel || 15) / 100;
  const smoothed = prev + (v - prev) * resp;
  eff._s[i] = smoothed;
  return clamp(smoothed * (eff.sens / 100), 0, 1.5);
}

export function fillFor(ctx, eff, bx, by, bw, bh, t) {
  if (eff.grad === 'solid') return eff.color;
  const cx = bx + bw / 2;
  const cy = by + bh / 2;
  const a = ((eff.gAng + (eff.gAnim || 0) * t) * Math.PI) / 180;
  const d = Math.hypot(bw, bh) / 2;
  let g;
  if (eff.grad === 'radial') g = ctx.createRadialGradient(cx, cy, 0, cx, cy, d);
  else if (eff.grad === 'conic' && ctx.createConicGradient) g = ctx.createConicGradient(a, cx, cy);
  else {
    g = ctx.createLinearGradient(
      cx - Math.cos(a) * d, cy - Math.sin(a) * d,
      cx + Math.cos(a) * d, cy + Math.sin(a) * d
    );
  }
  const cs = eff.three ? [eff.color, eff.color2, eff.color3] : [eff.color, eff.color2];
  cs.forEach((c, i) => g.addColorStop(i / (cs.length - 1), c));
  return g;
}

function roundRectPath(ctx, x, y, w, h, r) {
  if (r > 0 && ctx.roundRect) {
    ctx.roundRect(x, y, w, h, Math.min(r, w / 2, h / 2));
  } else {
    ctx.rect(x, y, w, h);
  }
}

export function renderBars(ctx, W, H, t, e, layer, horizontal = false) {
  const span = (horizontal ? H : W) * 0.8;
  const len = (horizontal ? W : H) * 0.4 * (e.maxH / 100) * (e.heightScale / 100);
  const n = e.n;
  const sl = span / n;
  const bw = Math.max(1, sl * (e.thick / 100));
  if (horizontal) ctx.rotate(Math.PI / 2);
  const both = e.dir === 'both';
  const dn = e.dir === 'down';
  const mirr = e.mir === 'mirror';

  ctx.fillStyle = fillFor(ctx, e, -span / 2, dn ? 0 : -len, span, both ? len * 2 : len, t);

  ctx.beginPath();
  const nh = Math.ceil(n / 2);
  const a0 = ctx.globalAlpha;
  const mp = mirr ? new Path2D() : null;

  for (let i = 0; i < n; i++) {
    let v;
    if (e.mir === 'sym') v = bv(e, Math.min(nh - 1, Math.floor(Math.abs(i - (n - 1) / 2))), nh);
    else v = bv(e, e.flipInput ? n - 1 - i : i, n);
    let h = Math.max(1, v * len * (1 - (e.rvar / 100) * (e._rv?.[i] ?? (e._rv || (e._rv = {}))[i] ?? (e._rv[i] = Math.random()))));
    h = clamp(h, (len * e.minHeightScale) / 100, (len * e.maxHeightScale) / 100);
    const x = -span / 2 + i * sl + (sl - bw) / 2;
    const r = (e.round * bw) / 100;
    if (!dn) roundRectPath(ctx, x, -h, bw, h, r);
    if (dn || both) roundRectPath(ctx, x, 0, bw, h, r);
    if (mirr) roundRectPath(mp, x, 0, bw, h * 0.6, r);
  }
  ctx.fill();
  if (mirr) {
    ctx.globalAlpha = a0 * 0.35;
    ctx.fill(mp);
    ctx.globalAlpha = a0;
  }
}

export function renderCircle(ctx, W, H, t, e) { drawCircleBars(ctx, W, H, t, e, 0, 1); }
export function renderMultiRing(ctx, W, H, t, e) {
  for (let k = 0; k < e.rings; k++) drawCircleBars(ctx, W, H, t, e, k, e.rings);
}

function drawCircleBars(ctx, W, H, t, e, k, K) {
  const R = Math.min(W, H) * 0.22;
  const n = e.n;
  const r0 = (R * e.r0) / 100 * (1 + k * 0.55);
  const len = (R * e.maxH) / 100 * (K > 1 ? 0.5 : 1);
  const lw = Math.max(1, ((TAU * Math.max(r0, 10)) / n) * (e.thick / 100));
  const nh = Math.ceil(n / 2);
  ctx.lineWidth = lw;
  ctx.strokeStyle = fillFor(ctx, e, -R * 2, -R * 2, R * 4, R * 4, t);
  ctx.beginPath();
  for (let i = 0; i < n; i++) {
    const v = e.mir !== 'none' ? bv(e, i < nh ? i : n - 1 - i, nh) : bv(e, i + k * n, n * K);
    const a = ((e.a0 + (e.a1 - e.a0) * i / n) * Math.PI) / 180;
    const c = Math.cos(a);
    const s = Math.sin(a);
    const l = v * len;
    let p = r0, q = r0 + l;
    if (e.odir === 'in') q = Math.max(0, r0 - l);
    else if (e.odir === 'both') { p = Math.max(0, r0 - l / 2); q = r0 + l / 2; }
    ctx.moveTo(c * p, s * p);
    ctx.lineTo(c * q, s * q);
  }
  ctx.stroke();
}

export function renderConcentric(ctx, W, H, t, e) {
  const R = Math.min(W, H) * 0.22;
  ctx.strokeStyle = fillFor(ctx, e, -R * 2, -R * 2, R * 4, R * 4, t);
  for (let k = 0; k < e.rings; k++) {
    const v = bv(e, k, e.rings);
    ctx.lineWidth = Math.max(1, (H / 720) * (e.thick / 10) * (1 + v * 2));
    ctx.beginPath();
    ctx.arc(0, 0, R * (0.25 + 0.75 * ((k + 1) / e.rings)) * (1 + v * 0.15 * (e.maxH / 100)), 0, TAU);
    ctx.stroke();
  }
}

export function renderRing(ctx, W, H, t, e) {
  const R = Math.min(W, H) * 0.22;
  const an = getAnalyser();
  if (!an) return;
  const td = new Uint8Array(an.fftSize);
  an.getByteTimeDomainData(td);
  const v = bv(e, 0, 1);
  const p = 180;
  ctx.lineWidth = (H / 720) * (e.thick / 6) * (1 + v);
  ctx.strokeStyle = fillFor(ctx, e, -R * 2, -R * 2, R * 4, R * 4, t);
  ctx.beginPath();
  for (let i = 0; i <= p; i++) {
    const k = Math.floor((i / p) * (td.length - 1));
    const r = R * (e.r0 / 100 + v * 0.25) + ((td[k] - 128) / 128) * R * 0.3 * (e.sens / 100);
    const a = (i / p) * TAU;
    if (i) ctx.lineTo(Math.cos(a) * r, Math.sin(a) * r);
    else ctx.moveTo(Math.cos(a) * r, Math.sin(a) * r);
  }
  ctx.closePath();
  ctx.stroke();
}

export function renderPulse(ctx, W, H, t, e) {
  const R = Math.min(W, H) * 0.22;
  const P = e._p = e._p || [];
  if (getBeatTriggered(e.bsel)) P.push({ r: (R * e.r0) / 100, a: 1 });
  ctx.strokeStyle = fillFor(ctx, e, -R * 3, -R * 3, R * 6, R * 6, t);
  ctx.lineWidth = Math.max(1, (H / 720) * (e.thick / 8));
  for (let i = P.length - 1; i >= 0; i--) {
    const p = P[i];
    p.r += R * 0.025 * (1 + getMeasurement('bass') * 2);
    p.a -= 0.018;
    if (p.a <= 0) { P.splice(i, 1); continue; }
    ctx.globalAlpha = (e.op / 100) * p.a;
    ctx.beginPath();
    ctx.arc(0, 0, p.r, 0, TAU);
    ctx.stroke();
  }
}

export function renderDots(ctx, W, H, t, e) {
  const span = W * 0.8;
  const len = H * 0.35 * (e.maxH / 100);
  const n = e.n;
  const sl = span / n;
  ctx.fillStyle = fillFor(ctx, e, -span / 2, -len, span, len, t);
  for (let i = 0; i < n; i++) {
    const v = bv(e, i, n);
    const x = -span / 2 + i * sl + sl / 2;
    ctx.beginPath();
    ctx.arc(x, -v * len, Math.max(2, sl * 0.35 * (e.thick / 70)), 0, TAU);
    ctx.fill();
  }
}

export function renderLines(ctx, W, H, t, e) {
  const span = W * 0.8;
  const len = H * 0.35 * (e.maxH / 100);
  const n = e.n;
  const sl = span / n;
  ctx.strokeStyle = fillFor(ctx, e, -span / 2, -len, span, len, t);
  ctx.fillStyle = ctx.strokeStyle;
  ctx.lineWidth = Math.max(1, (sl * e.thick) / 100 * 0.4);
  for (let i = 0; i < n; i++) {
    const v = bv(e, i, n);
    const x = -span / 2 + i * sl + sl / 2;
    ctx.beginPath();
    ctx.moveTo(x, 0);
    ctx.lineTo(x, -v * len);
    ctx.stroke();
    ctx.beginPath();
    ctx.arc(x, -v * len, ctx.lineWidth, 0, TAU);
    ctx.fill();
  }
}

export function renderBlocks(ctx, W, H, t, e) {
  const span = W * 0.8;
  const len = H * 0.35 * (e.maxH / 100);
  const n = e.n;
  const sl = span / n;
  ctx.fillStyle = fillFor(ctx, e, -span / 2, -len, span, len, t);
  for (let i = 0; i < n; i++) {
    const v = bv(e, i, n);
    const x = -span / 2 + i * sl + sl / 2;
    const bh = len / e.seg;
    const lit = Math.floor(v * e.seg);
    for (let b = 0; b < lit; b++) {
      ctx.fillRect(x - (sl * e.thick) / 200, -(b + 1) * bh, (sl * e.thick) / 100, bh * 0.8);
    }
  }
}

export function renderGrid(ctx, W, H, t, e) {
  const n = e.n;
  const span = W * 0.8;
  const cw = span / n;
  const ch = (H * 0.5) / e.seg;
  e._h = e._h || [];
  const row = [];
  for (let i = 0; i < n; i++) row.push(bv(e, i, n));
  e._h.unshift(row);
  if (e._h.length > e.seg) e._h.length = e.seg;
  ctx.fillStyle = fillFor(ctx, e, -span / 2, -H * 0.25, span, H * 0.5, t);
  const a0 = ctx.globalAlpha;
  e._h.forEach((r, y) => {
    r.forEach((v, x) => {
      ctx.globalAlpha = a0 * clamp(v, 0, 1);
      ctx.fillRect(-span / 2 + x * cw, -H * 0.25 + y * ch, cw * 0.9, ch * 0.9);
    });
  });
  ctx.globalAlpha = a0;
}