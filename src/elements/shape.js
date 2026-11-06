import { bv, fillFor } from './bars.js';
import { TAU } from '../core/constants.js';

export function renderShape(ctx, W, H, t, e) {
  const R = Math.min(W, H) * 0.22;
  const v = bv(e, 0, 1);
  const r = R * (e.maxH / 100) * (0.6 + v * 0.6);
  ctx.fillStyle = fillFor(ctx, e, -r, -r, r * 2, r * 2, t);
  ctx.strokeStyle = e.sc;
  ctx.lineWidth = e.sw * (H / 720);
  
  ctx.beginPath();
  if (e.shape === 'circle') ctx.arc(0, 0, r, 0, TAU);
  else if (e.shape === 'rect') ctx.rect(-r, -r, r * 2, r * 2);
  else if (e.shape === 'capsule') ctx.ellipse(0, 0, r * 1.6, r * 0.6, 0, 0, TAU);
  else if (e.shape === 'flower') drawFlower(ctx, r);
  else if (e.shape === 'cross') drawCross(ctx, r);
  else if (e.shape === 'hexagon') drawPoly(ctx, 6, r, false);
  else if (e.shape === 'ring') { ctx.arc(0,0,r,0,TAU); ctx.arc(0,0,r*0.6,0,TAU, true); }
  else if (e.shape === 'shield') drawShield(ctx, r);
  else if (e.shape === 'diamond') drawPoly(ctx, 4, r, false, Math.PI/4);
  else drawPoly(ctx, e.shape === 'triangle' ? 3 : e.sides, r, e.shape === 'star');
  
  ctx.fill();
  if (e.sw) ctx.stroke();
}

function drawPoly(ctx, n, r, star, offset = Math.PI/2) {
  const m = star ? n * 2 : n;
  for (let i = 0; i < m; i++) {
    const a = (i / m) * TAU - offset;
    const q = star && i % 2 ? r * 0.45 : r;
    if (i) ctx.lineTo(Math.cos(a) * q, Math.sin(a) * q);
    else ctx.moveTo(Math.cos(a) * q, Math.sin(a) * q);
  }
  ctx.closePath();
}

function drawFlower(ctx, r) {
  for (let i = 0; i < 8; i++) {
    const a = (i / 8) * TAU;
    ctx.arc(Math.cos(a)*r*0.5, Math.sin(a)*r*0.5, r*0.5, 0, TAU);
  }
}

function drawCross(ctx, r) {
  const t = r * 0.35;
  ctx.rect(-t, -r, t*2, r*2);
  ctx.rect(-r, -t, r*2, t*2);
}

function drawShield(ctx, r) {
  ctx.moveTo(0, -r);
  ctx.quadraticCurveTo(r, -r, r, 0);
  ctx.quadraticCurveTo(r, r*0.8, 0, r);
  ctx.quadraticCurveTo(-r, r*0.8, -r, 0);
  ctx.quadraticCurveTo(-r, -r, 0, -r);
  ctx.closePath();
}