import test from 'node:test';
import assert from 'node:assert/strict';
import { WORDS, MYTHS } from '../js/games/data.js';
import { scoreGuess, keyStates, wordForDay, loadGame, submitGuess, MAX_TRIES } from '../js/games/wordle.js';
import { cardsForDay, loadMyths, answerCard, nextCard, scoreOf, PER_DAY } from '../js/games/myths.js';
import { currentStreak, loadStats } from '../js/games/store.js';
import { renderGames } from '../js/ui/games.js';
import { RESUMES } from '../js/games/resume-data.js';
import { resumeForDay, linesOf, weakCount, loadResume, togglePick, checkResume, scoreResume } from '../js/games/resume.js';

function withStorage(run) {
  const data = new Map();
  const before = globalThis.localStorage;
  globalThis.localStorage = { getItem:key => data.has(key) ? data.get(key) : null, setItem:(key, value) => data.set(key, String(value)) };
  try { run(); } finally { globalThis.localStorage = before; }
}

test('слова дня: 4–5 русских букв, без повторов, у каждого есть тема и пояснение', () => {
  assert.equal(new Set(WORDS.map(word => word.w)).size, WORDS.length);
  for (const word of WORDS) {
    assert.match(word.w, /^[а-я]{4,5}$/, word.w);
    assert.ok(word.t && word.h, word.w);
  }
  assert.ok(wordForDay('2026-11-18').w.length <= 5);
  assert.equal(wordForDay('2026-11-18').w, wordForDay('2026-11-18').w);
});

test('метки Wordle учитывают повторяющиеся буквы', () => {
  assert.deepEqual(scoreGuess('налог', 'грант'), ['present', 'present', 'absent', 'absent', 'present']);
  assert.deepEqual(scoreGuess('аббат', 'табак'), ['present', 'absent', 'correct', 'correct', 'present']);
  assert.deepEqual(scoreGuess('цель', 'цель'), ['correct', 'correct', 'correct', 'correct']);
  assert.equal(keyStates(['тест'], 'цель').е, 'correct');
});

test('игра «Слово дня» сохраняется, победа и проигрыш пишут статистику и серию', () => {
  withStorage(() => {
    const date = '2026-11-18', answer = wordForDay(date).w;
    let game = loadGame(date);
    assert.deepEqual(game, { date, guesses:[], status:'play' });
    game = submitGuess(game, 'я'.repeat(answer.length));
    assert.equal(game.status, 'play');
    assert.deepEqual(loadGame(date).guesses, ['я'.repeat(answer.length)]);
    game = submitGuess(game, answer);
    assert.equal(game.status, 'won');
    assert.equal(currentStreak('wordle', date), 1);
    assert.equal(currentStreak('wordle', '2026-11-19'), 1);
    assert.equal(currentStreak('wordle', '2026-11-20'), 0);
    // следующий день подряд продлевает серию, проигрыш — обнуляет
    let next = loadGame('2026-11-19');
    for (let i = 0; i < MAX_TRIES; i++) next = submitGuess(next, 'я'.repeat(wordForDay('2026-11-19').w.length));
    assert.equal(next.status, 'lost');
    assert.equal(loadStats('wordle').streak, 0);
    assert.equal(loadStats('wordle').played, 2);
  });
});

test('«Миф или правда»: пять утверждений в день, итог и серия', () => {
  withStorage(() => {
    const date = '2026-11-18', cards = cardsForDay(date);
    assert.equal(cards.length, PER_DAY);
    assert.equal(new Set(cards.map(card => card.id)).size, PER_DAY);
    assert.ok(cards.some(card => card.t) && cards.some(card => !card.t));
    assert.ok(MYTHS.every(card => card.s && card.why && typeof card.t === 'boolean'));
    let game = loadMyths(date);
    assert.equal(nextCard(game).index, 0);
    for (const card of cards) { game = answerCard(game, card.t); game = nextCard(game); }
    assert.equal(game.index, PER_DAY);
    assert.equal(scoreOf(game, cards), PER_DAY);
    assert.equal(currentStreak('myths', date), 1);
    assert.deepEqual(loadMyths(date).answers, game.answers);
  });
});

test('экраны игр показывают хаб, слово и утверждение', () => {
  withStorage(() => {
    const date = '2026-11-18';
    const hub = renderGames('#games', date);
    assert.match(hub, /data-route="#games\/wordle"/);
    assert.match(hub, /data-route="#games\/myths"/);
    assert.match(hub, /0 из 3/);
    const wordle = renderGames('#games/wordle', date);
    assert.match(wordle, /class="wd-kb"/);
    assert.equal((wordle.match(/class="wd-row/g) || []).length, MAX_TRIES);
    const myth = renderGames('#games/myths', date);
    assert.match(myth, /data-g="say:true"/);
    assert.match(myth, new RegExp(`Утверждение 1 из ${PER_DAY}`));
  });
});

test('база слов и утверждений: полный круг без повторов, новый круг перемешан', () => {
  const day = date => Math.floor(Date.parse(`${date}T00:00:00Z`) / 86400000);
  const dateOf = n => new Date(n * 86400000).toISOString().slice(0, 10);
  const size = WORDS.length, start = Math.ceil(day('2026-11-18') / size) * size; // начало круга
  const lap = Array.from({ length:size }, (_, i) => wordForDay(dateOf(start + i)).w);
  assert.equal(new Set(lap).size, size);
  const next = Array.from({ length:size }, (_, i) => wordForDay(dateOf(start + size + i)).w);
  assert.equal(new Set(next).size, size);
  assert.notDeepEqual(lap, next);
  assert.ok(WORDS.length >= 100 && MYTHS.length >= 70);
});

test('«Резюме-детектор»: данные, отметки, проверка, серия', () => {
  for (const resume of RESUMES) {
    assert.ok(weakCount(resume) >= 3 && weakCount(resume) < linesOf(resume).length, resume.id);
    for (const line of linesOf(resume).filter(line => line.bad)) assert.ok(line.why && line.fix, resume.id);
  }
  withStorage(() => {
    const date = '2026-11-18', resume = resumeForDay(date), lines = linesOf(resume);
    let game = loadResume(date);
    assert.equal(checkResume(game).checked, false); // без отметок проверять нечего
    const weak = lines.map((line, i) => line.bad ? i : -1).filter(i => i >= 0), good = lines.findIndex(line => !line.bad);
    for (const i of weak) game = togglePick(game, i);
    game = togglePick(game, good);
    game = checkResume(game);
    assert.equal(game.checked, true);
    assert.deepEqual(scoreResume(game, resume), { hits:weak.length, falseAlarms:1, total:weak.length, misses:0, perfect:false });
    assert.equal(togglePick(game, 0), game); // после проверки отметки заморожены
    assert.equal(currentStreak('resume', date), 1);
    const html = renderGames('#games/resume', date);
    assert.match(html, /rs-why/);
    assert.match(html, /Лишних отметок: 1/);
  });
});

test('у каждой игры есть «Как играть», при первом заходе он раскрыт, потом свёрнут', () => {
  withStorage(() => {
    for (const route of ['#games/wordle', '#games/myths', '#games/resume']) {
      const first = renderGames(route, '2026-11-18');
      assert.match(first, /class="game-how is-open"/, route);
      assert.match(first, /data-g="how"/, route);
      renderGames('#games', '2026-11-18');
      assert.doesNotMatch(renderGames(route, '2026-11-18'), /class="game-how is-open"/, route);
    }
  });
});
