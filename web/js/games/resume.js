import { RESUMES } from './resume-data.js';
import { dayNumber, hash, read, write, recordPlay } from './store.js';

// По одному резюме в день; круг проходит все резюме без повторов, на новом круге порядок другой
const laps = new Map();
const orderFor = lap => {
  if (!laps.has(lap)) laps.set(lap, [...RESUMES].sort((a, b) => hash(`${lap}:${a.id}`) - hash(`${lap}:${b.id}`)));
  return laps.get(lap);
};
export function resumeForDay(date) {
  const day = dayNumber(date), size = RESUMES.length;
  return orderFor(Math.floor(day / size))[((day % size) + size) % size];
}

// Нумеруем только кликабельные строки (без заголовков разделов)
export const linesOf = resume => resume.items.filter(item => !item.h);
export const weakCount = resume => linesOf(resume).filter(line => line.bad).length;

const fresh = date => ({ date, picked:[], checked:false });
export function loadResume(date) {
  const saved = read('resume.game', null);
  const lines = linesOf(resumeForDay(date)).length;
  const ok = saved && saved.date === date && typeof saved.checked === 'boolean' && Array.isArray(saved.picked)
    && saved.picked.every(value => Number.isInteger(value) && value >= 0 && value < lines);
  return ok ? { date, picked:[...new Set(saved.picked)], checked:Boolean(saved.checked) } : fresh(date);
}

export function togglePick(game, index) {
  if (game.checked) return game;
  const picked = game.picked.includes(index) ? game.picked.filter(value => value !== index) : [...game.picked, index];
  const next = { ...game, picked };
  write('resume.game', next);
  return next;
}

export function scoreResume(game, resume) {
  const lines = linesOf(resume);
  const hits = game.picked.filter(i => lines[i]?.bad).length;
  const falseAlarms = game.picked.filter(i => !lines[i]?.bad).length;
  const total = weakCount(resume);
  return { hits, falseAlarms, total, misses:total - hits, perfect:hits === total && falseAlarms === 0 };
}

// Проверка: день засчитывается в серию независимо от результата (серия — про привычку)
export function checkResume(game) {
  if (game.checked || !game.picked.length) return game;
  const next = { ...game, checked:true };
  write('resume.game', next);
  recordPlay('resume', game.date, true, true);
  return next;
}
