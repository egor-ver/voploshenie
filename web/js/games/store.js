import { localDate } from '../domain/local-date.js';

const PREFIX = 'voplo.games.';

export function read(key, fallback = null) {
  try { const raw = globalThis.localStorage?.getItem(PREFIX + key); return raw ? JSON.parse(raw) : fallback; }
  catch { return fallback; }
}
export function write(key, value) {
  try { globalThis.localStorage?.setItem(PREFIX + key, JSON.stringify(value)); }
  catch { /* без хранилища играем в рамках вкладки */ }
}

export const today = () => localDate();
export const dayNumber = date => Math.floor(Date.parse(`${date}T00:00:00Z`) / 86400000);
const dateOf = number => new Date(number * 86400000).toISOString().slice(0, 10);

// Стабильное «перемешивание»: порядок один и тот же у всех, день за днём идёт по кругу
export function hash(text) {
  let value = 2166136261;
  for (const char of String(text)) { value ^= char.codePointAt(0); value = Math.imul(value, 16777619) >>> 0; }
  return value;
}

export const emptyStats = () => ({ streak:0, best:0, last:'', played:0, wins:0 });

export function loadStats(game) {
  const value = read(`${game}.stats`, null);
  const number = key => Number.isInteger(value?.[key]) && value[key] >= 0 ? value[key] : 0;
  return value && typeof value === 'object' ? { streak:number('streak'), best:number('best'), last:typeof value.last === 'string' ? value.last : '', played:number('played'), wins:number('wins') } : emptyStats();
}

// Серия живёт, пока вы играете каждый день; проигрыш в «Слове дня» её обнуляет
export function recordPlay(game, date, won, keepStreakOnLoss = false) {
  const stats = loadStats(game);
  if (stats.last === date) return stats;
  const continued = stats.last === dateOf(dayNumber(date) - 1);
  stats.streak = won || keepStreakOnLoss ? (continued ? stats.streak : 0) + 1 : 0;
  stats.best = Math.max(stats.best, stats.streak);
  stats.played += 1;
  if (won) stats.wins += 1;
  stats.last = date;
  write(`${game}.stats`, stats);
  return stats;
}

// Сколько дней подряд игра сыграна: если вчера пропущен, серия уже сгорела
export function currentStreak(game, date = today()) {
  const stats = loadStats(game);
  return stats.last === date || stats.last === dateOf(dayNumber(date) - 1) ? stats.streak : 0;
}
