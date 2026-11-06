import { fillFor } from './bars.js';

export function renderBackground(ctx, W, H, t, e) {
  ctx.fillStyle = fillFor(ctx, e, 0, 0, W, H, t);
  ctx.fillRect(-W / 2, -H / 2, W * 2, H * 2);
}