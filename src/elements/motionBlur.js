import { layerRenderers } from './registry.js';

export function renderMotionBlur(ctx, W, H, t, e, layer) {
  const target = layer._target;
  if (!target) return;
  const amt = e.blurAmount || 1;
  const samples = 8;

  if (e.showUnblurredUnder) {
    ctx.save();
    ctx.globalAlpha *= 0.5;
    drawTarget(ctx, target, W, H, t);
    ctx.restore();
  }

  for (let i = 0; i < samples; i++) {
    const f = i / samples;
    ctx.save();
    ctx.globalAlpha *= (1 - f) * 0.6;
    const vx = (target._vx || 0) * f * amt;
    const vy = (target._vy || 0) * f * amt;
    ctx.translate(-vx, -vy);
    drawTarget(ctx, target, W, H, t);
    ctx.restore();
  }

  if (e.showUnblurred) drawTarget(ctx, target, W, H, t);
}

function drawTarget(ctx, target, W, H, t) {
  const r = layerRenderers[target.type];
  if (r) r(ctx, W, H, t, target, target);
}