import { WORDS } from './data.js';
import { dayNumber, hash, read, write, recordPlay } from './store.js';

export const MAX_TRIES = 6;
// Слова идут по кругу без повторов; на каждом новом круге порядок перемешивается заново (по номеру круга)
const laps = new Map();
const orderFor = lap => {
  if (!laps.has(lap)) laps.set(lap, [...WORDS].sort((a, b) => hash(`${lap}:${a.w}`) - hash(`${lap}:${b.w}`) || a.w.localeCompare(b.w)));
  return laps.get(lap);
};

export const normalize = text => String(text).toLowerCase().replace(/ё/g, 'е');
export function wordForDay(date) {
  const day = dayNumber(date), size = WORDS.length;
  return orderFor(Math.floor(day / size))[((day % size) + size) % size];
}

// Честные метки Wordle: повторяющиеся буквы не «горят» лишний раз
export function scoreGuess(guess, answer) {
  const marks = Array(answer.length).fill('absent');
  const left = {};
  for (let i = 0; i < answer.length; i++) {
    if (guess[i] === answer[i]) marks[i] = 'correct';
    else left[answer[i]] = (left[answer[i]] || 0) + 1;
  }
  for (let i = 0; i < answer.length; i++) {
    if (marks[i] === 'correct' || !left[guess[i]]) continue;
    marks[i] = 'present';
    left[guess[i]] -= 1;
  }
  return marks;
}

const rank = { absent:1, present:2, correct:3 };
export function keyStates(guesses, answer) {
  const states = {};
  for (const guess of guesses) scoreGuess(guess, answer).forEach((mark, i) => { if (!states[guess[i]] || rank[mark] > rank[states[guess[i]]]) states[guess[i]] = mark; });
  return states;
}

const fresh = date => ({ date, guesses:[], status:'play' });
export function loadGame(date) {
  const saved = read('wordle.game', null);
  const word = wordForDay(date).w;
  const ok = saved && saved.date === date && Array.isArray(saved.guesses) && saved.guesses.length <= MAX_TRIES
    && saved.guesses.every(guess => typeof guess === 'string' && guess.length === word.length)
    && ['play', 'won', 'lost'].includes(saved.status);
  return ok ? saved : fresh(date);
}

// Принять слово: возвращает обновлённую игру (и записывает итог в серию, когда игра закончилась)
export function submitGuess(game, guess) {
  const answer = wordForDay(game.date).w;
  const guesses = [...game.guesses, guess];
  const status = guess === answer ? 'won' : guesses.length >= MAX_TRIES ? 'lost' : 'play';
  const next = { date:game.date, guesses, status };
  write('wordle.game', next);
  if (status !== 'play') recordPlay('wordle', game.date, status === 'won');
  return next;
}

export function shareText(game) {
  const answer = wordForDay(game.date).w;
  const emoji = { correct:'🟩', present:'🟪', absent:'⬜' };
  const [, month, day] = game.date.split('-');
  const rows = game.guesses.map(guess => scoreGuess(guess, answer).map(mark => emoji[mark]).join('')).join('\n');
  return `Воплощение · Слово дня ${day}.${month}\n${game.status === 'won' ? game.guesses.length : 'X'}/${MAX_TRIES}\n${rows}`;
}
