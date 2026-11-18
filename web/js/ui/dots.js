// Растровая шкала «Печати»: ряды точек растут и меняют цвет primary → accent.
export function dotsHtml(percent, count = 20) {
  const filled = Math.round(Math.max(0, Math.min(100, percent)) / 100 * count);
  let out = '';
  for (let i = 0; i < count; i++) {
    const t = count > 1 ? i / (count - 1) : 0;
    const size = (4 + t * 6).toFixed(1);
    const color = `color-mix(in srgb, var(--p-accent) ${Math.round(t * 100)}%, var(--p-primary))`;
    out += `<i class="${i < filled ? 'on' : ''}" style="--d:${size}px;--c:${color}"></i>`;
  }
  return `<div class="p-dots" role="img" aria-label="${Math.round(percent)} %">${out}</div>`;
}
export function rulerHtml(done, total) {
  let out = '';
  for (let i = 0; i < total; i++) out += `<i class="${i < done ? 'on' : ''}"></i>`;
  return `<div class="p-ruler" aria-hidden="true">${out}</div>`;
}
