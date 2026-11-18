import { MYTHS } from './data.js';
import { dayNumber, hash, read, write, recordPlay } from './store.js';

export const PER_DAY = 5;

// Утверждения идут по кругу без повторов, на каждом новом круге порядок другой.
// Правда и мифы чередуются, чтобы пять утверждений в день не были «все одинаковые».
const decks = new Map();
function deckFor(lap) {
  if (decks.has(lap)) return decks.get(lap);
  const by = (x, y) => hash(`${lap}:${x.id}`) - hash(`${lap}:${y.id}`);
  const truths = MYTHS.filter(item => item.t).sort(by);
  const myths = MYTHS.filter(item => !item.t).sort(by);
  const out = [];
  const pattern = [false, true, false, true, false];
  while (truths.length || myths.length) {
    for (const wantTruth of pattern) {
      const next = (wantTruth ? truths : myths).shift() || (wantTruth ? myths : truths).shift();
      if (next) out.push(next);
    }
  }
  decks.set(lap, out);
  return out;
}

export const cardsForDay = date => Array.from({ length:PER_DAY }, (_, i) => {
  const index = dayNumber(date) * PER_DAY + i, size = MYTHS.length;
  return deckFor(Math.floor(index / size))[index % size];
});

const fresh = date => ({ date, index:0, answers:[] });
export function loadMyths(date) {
  const saved = read('myths.game', null);
  const ok = saved && saved.date === date && Number.isInteger(saved.index) && saved.index >= 0 && saved.index <= PER_DAY
    && Array.isArray(saved.answers) && saved.answers.length <= PER_DAY && saved.answers.every(value => typeof value === 'boolean');
  return ok ? saved : fresh(date);
}

// Ответ на текущую карточку; когда пять карточек пройдены, день засчитывается в серию
export function answerCard(game, guessTrue) {
  if (game.answers.length > game.index || game.index >= PER_DAY) return game;
  const next = { ...game, answers:[...game.answers, guessTrue] };
  write('myths.game', next);
  return next;
}
export function nextCard(game) {
  if (game.answers.length <= game.index) return game;
  const next = { ...game, index:game.index + 1 };
  write('myths.game', next);
  if (next.index >= PER_DAY) recordPlay('myths', game.date, true);
  return next;
}
export const scoreOf = (game, cards) => game.answers.filter((value, i) => value === cards[i]?.t).length;
