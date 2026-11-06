import { layerRenderers } from './registry.js';

export function renderRgbSplit(ctx, W, H, t, e, layer) {
  const target = layer._target;
  if (!target) return;
  const amt = e.splitAmount * e.splitMultiplier;
  const colors = [e.splitColor0, e.splitColor1, e.splitColor2];
  const offsets = [[-amt, 0], [0, 0], [amt, 0]];

  ctx.save();
  ctx.globalCompositeOperation = e.blend || 'screen';

  if (e.showUnblurredUnder) {
    ctx.save();
    ctx.globalAlpha *= 0.5;
    drawTarget(ctx, target, W, H, t);
    ctx.restore();
  }

  for (let i = 0; i < 3; i++) {
    ctx.save();
    ctx.translate(offsets[i][0], offsets[i][1]);
    ctx.globalAlpha *= 0.9;
    ctx.globalCompositeOperation = 'lighter';
    ctx.shadowColor = colors[i];
    ctx.shadowBlur = 0;
    drawTarget(ctx, target, W, H, t);
    ctx.restore();
  }

  if (e.showUnblurred) drawTarget(ctx, target, W, H, t);
  ctx.restore();
}

function drawTarget(ctx, target, W, H, t) {
  const r = layerRenderers[target.type];
  if (r) r(ctx, W, H, t, target, target);
}