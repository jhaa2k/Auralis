import { layerRenderers } from './registry.js';

let offscreen = null;

export function renderBlurEffect(ctx, W, H, t, e, layer) {
  const target = layer._target;
  if (!target) return;

  if (!offscreen) offscreen = document.createElement('canvas');
  if (offscreen.width !== W || offscreen.height !== H) {
    offscreen.width = W;
    offscreen.height = H;
  }
  const ox = offscreen.getContext('2d');

  ox.clearRect(0, 0, W, H);
  ox.save();
  ox.translate(W / 2, H / 2);
  const r = layerRenderers[target.type];
  if (r) r(ox, W, H, t, target, target);
  ox.restore();

  const passes = [
    { scale: e.layer1Scale, color: e.layer1Color },
    { scale: e.layer2Scale, color: e.layer2Color },
    { scale: e.layer3Scale, color: e.layer3Color }
  ];

  if (e.showUnblurredUnder) {
    ctx.save();
    ctx.globalAlpha *= 0.5;
    ctx.drawImage(offscreen, -W / 2, -H / 2, W, H);
    ctx.restore();
  }

  for (const p of passes) {
    ctx.save();
    ctx.globalCompositeOperation = 'lighter';
    ctx.globalAlpha *= 0.4;
    ctx.filter = `blur(${e.blurRadius * p.scale * e.blurMultiplier}px)`;
    ctx.drawImage(offscreen, -W / 2, -H / 2, W, H);
    ctx.restore();
  }

  if (e.showUnblurred) ctx.drawImage(offscreen, -W / 2, -H / 2, W, H);
}