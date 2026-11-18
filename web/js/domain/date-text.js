const months = ['января','февраля','марта','апреля','мая','июня','июля','августа','сентября','октября','ноября','декабря'];
const human = (d, m, y) => `${Number(d)} ${months[Number(m) - 1]}${y ? ` ${y}` : ''}`;
const sameMonth = (a, b) => a.m === b.m && a.y === b.y;

// Приводит технические формы дат из каталога (ISO, дд.мм.гггг, голый год) к читаемому русскому тексту.
export function readableDateText(text) {
  const value = String(text ?? '').trim();
  if (!value) return '';
  const iso = value.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (iso) return human(iso[3], iso[2], iso[1]);
  const range = value.match(/^(\d{2})\.(\d{2})\.(\d{4})\s*[-–—]\s*(\d{2})\.(\d{2})\.(\d{4})$/);
  if (range) {
    const [, d1, m1, y1, d2, m2, y2] = range;
    if (sameMonth({ m:m1, y:y1 }, { m:m2, y:y2 })) return `${Number(d1)}–${human(d2, m2, y2)}`;
    return y1 === y2 ? `${human(d1, m1)} – ${human(d2, m2, y2)}` : `${human(d1, m1, y1)} – ${human(d2, m2, y2)}`;
  }
  if (/^\d{4}$/.test(value)) return `Старт в ${value} году`;
  return value.charAt(0).toLocaleUpperCase('ru') + value.slice(1);
}

export const isRollingText = text => /круглогодичн|круглый год|в любое время|в течение года/i.test(String(text ?? ''));
export const capitalize = text => { const value = String(text ?? ''); return value.charAt(0).toLocaleUpperCase('ru') + value.slice(1); };
