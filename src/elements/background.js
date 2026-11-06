import { fillFor } from './bars.js';

export function renderBackground(ctx, W, H, t, e) {
  if (e._img && e._img.width) {
    ctx.drawImage(e._img, -W / 2, -H / 2, W, H);
  } else {
    ctx.fillStyle = fillFor(ctx, e, 0, 0, W, H, t);
    ctx.fillRect(-W / 2, -H / 2, W * 2, H * 2);
  }
}