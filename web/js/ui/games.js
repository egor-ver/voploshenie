import { escapeHtml } from '../escape.js';
import { brandHeader } from './brand.js';
import { renderMainNav } from './nav.js';
import { today, currentStreak, loadStats, read, write } from '../games/store.js';
import { MAX_TRIES, wordForDay, scoreGuess, keyStates, loadGame, submitGuess, shareText, normalize } from '../games/wordle.js';
import { PER_DAY, cardsForDay, loadMyths, answerCard, nextCard, scoreOf } from '../games/myths.js';
import { resumeForDay, linesOf, weakCount, loadResume, togglePick, scoreResume, checkResume } from '../games/resume.js';

const arrow = '<svg aria-hidden="true" viewBox="0 0 24 24"><path d="M4 12h16m-7-7 7 7-7 7"/></svg>';
const back = '<svg aria-hidden="true" viewBox="0 0 24 24"><path d="M20 12H4m7-7-7 7 7 7"/></svg>';
const chevron = '<svg class="game-chev" aria-hidden="true" viewBox="0 0 24 24"><path d="m9 5 7 7-7 7"/></svg>';
const deleteKey = '<svg aria-hidden="true" viewBox="0 0 24 24"><path d="M21 5H9l-6 7 6 7h12V5Zm-5 4-4 6m0-6 4 6"/></svg>';

const art = {
  resume: '<svg viewBox="0 0 84 84" aria-hidden="true"><rect x="14" y="6" width="50" height="68" class="a-line"/><rect x="22" y="16" width="34" height="5" class="a-off"/><rect x="22" y="28" width="34" height="9" class="a-hl"/><rect x="22" y="44" width="26" height="5" class="a-off"/><rect x="22" y="56" width="30" height="5" class="a-off"/><circle cx="58" cy="58" r="11" class="a-lens"/><path d="m66 66 10 10" class="a-handle"/></svg>',
  wordle: '<svg viewBox="0 0 84 84" aria-hidden="true"><rect x="6" y="6" width="22" height="22" class="a-ok"/><rect x="31" y="6" width="22" height="22" class="a-mid"/><rect x="56" y="6" width="22" height="22" class="a-off"/><rect x="6" y="31" width="22" height="22" class="a-mid"/><rect x="31" y="31" width="22" height="22" class="a-ok"/><rect x="56" y="31" width="22" height="22" class="a-ok"/><rect x="6" y="56" width="22" height="22" class="a-ok"/><rect x="31" y="56" width="22" height="22" class="a-off"/><rect x="56" y="56" width="22" height="22" class="a-line"/></svg>',
  myths: '<svg viewBox="0 0 84 84" aria-hidden="true"><g transform="rotate(-7 28 44)"><rect x="8" y="14" width="40" height="54" class="a-line"/><path d="m19 31 18 18m0-18L19 49" class="a-x"/></g><g transform="rotate(6 58 44)"><rect x="36" y="18" width="40" height="54" class="a-ok"/><path d="m47 46 8 8 14-18" class="a-v"/></g></svg>'
};

// Временное состояние экрана: введённое слово, подсказка, какие анимации проиграть один раз
const ui = { route:'', how:false, input:'', note:'', pop:-1, reveal:-1, shake:false, slide:false, stamp:false };
const flash = () => Object.assign(ui, { note:'', pop:-1, reveal:-1, shake:false, slide:false, stamp:false });
let redrawNow = () => {};

const letters = count => count === 4 ? 'буквы' : 'букв';
const upper = text => String(text).toLocaleUpperCase('ru');
const flame = '<svg class="streak-flame" aria-hidden="true" viewBox="0 0 24 24"><path d="M12 2c.5 3.2 3.8 5 3.8 9.2A3.8 3.8 0 0 1 12 15a3.8 3.8 0 0 1-3.8-3.8c0-1.4.6-2.4 1.3-3.2C9.6 10 11 10.5 11 10.5 10.6 7 11.6 4.2 12 2Zm0 20a6.8 6.8 0 0 1-6.8-6.8c0-2.1.9-3.7 2-4.9.1 2.7 2.1 4.1 4.8 4.1s4.8-1.4 4.8-4.1c1.1 1.200 2 2.800 2 4.900A6.800 6.800 0 0 1 12 22Z"/></svg>';
const days = n => n % 10 === 1 && n % 100 !== 11 ? 'день' : n % 10 >= 2 && n % 10 <= 4 && (n % 100 < 12 || n % 100 > 14) ? 'дня' : 'дней';
const streakBadge = n => n ? `<span class="game-streak is-on" aria-label="Серия: ${n} ${days(n)} подряд">${flame}<b>${n}</b><small>${days(n)} подряд</small></span>` : `<span class="game-streak">${flame}<small>Начните серию</small></span>`;

const HOW = {
  wordle: [
    'Угадайте слово за 6 попыток. Подсказка и тема вверху — это значение слова.',
    'Вводите буквы и нажимайте «Ввод». Подойдёт любое слово нужной длины.',
    'Зелёный — буква на своём месте. Фиолетовый — буква есть в слове, но стоит в другом месте. Серый — такой буквы нет.',
    'Новое слово появляется каждый день.'
  ],
  myths: [
    'Прочитайте утверждение и выберите «Правда» или «Миф».',
    'Сразу после ответа покажем верный вариант и объяснение.',
    'В день — пять карточек. Играйте каждый день, чтобы росла серия.'
  ],
  resume: [
    'Прочитайте резюме или письмо: в нём спрятаны слабые строки.',
    'Нажимайте на строки, которые мешают отклику: шаблонные фразы, общие слова, ошибки и лишние данные.',
    'Нажмите «Проверить»: покажем, что вы нашли, что пропустили и как переписать строку. Лишние отметки снижают результат.'
  ]
};
const caret = '<svg aria-hidden="true" viewBox="0 0 24 24"><path d="m6 9 6 6 6-6"/></svg>';
const howBlock = game => `<section class="game-how${ui.how ? ' is-open' : ''}"><button class="game-how__bar" data-g="how" aria-expanded="${ui.how}"><span class="game-how__icon" aria-hidden="true">?</span><span>Как играть</span>${caret}</button><div class="game-how__body"><div><ol>${HOW[game].map(step => `<li>${escapeHtml(step)}</li>`).join('')}</ol></div></div></section>`;

function top(game, route = '#games') {
  const streak = currentStreak(game);
  return `<div class="game-top"><button class="game-back" data-route="${route}">${back}<span>Игры</span></button>${streakBadge(streak)}</div>`;
}

/* ───────────── Хаб ───────────── */
function renderHub(date) {
  const wordle = loadGame(date), myths = loadMyths(date), rs = loadResume(date), resume = resumeForDay(date);
  const wordleDone = wordle.status !== 'play', mythsDone = myths.index >= PER_DAY, resumeDone = rs.checked;
  const done = Number(wordleDone) + Number(mythsDone) + Number(resumeDone);
  const chip = (cls, text) => `<span class="game-chip ${cls}">${text}</span>`;
  const wordleChip = wordleDone ? chip('is-done', wordle.status === 'won' ? `Угадано за ${wordle.guesses.length}` : 'Слово раскрыто')
    : wordle.guesses.length ? chip('is-live', `Попытка ${wordle.guesses.length + 1} из ${MAX_TRIES}`) : chip('is-new', 'Ждёт вас');
  const mythsChip = mythsDone ? chip('is-done', `Верно ${scoreOf(myths, cardsForDay(date))} из ${PER_DAY}`)
    : myths.index ? chip('is-live', `Вопрос ${myths.index + 1} из ${PER_DAY}`) : chip('is-new', `${PER_DAY} утверждений`);
  const resumeChip = resumeDone ? chip('is-done', `Найдено ${scoreResume(rs, resume).hits} из ${weakCount(resume)}`)
    : rs.picked.length ? chip('is-live', `Отмечено ${rs.picked.length}`) : chip('is-new', 'Ждёт вас');
  const card = (key, route, title, text, chips) => `<button class="game-card g-${key}" data-route="${route}"><span class="game-art">${art[key]}</span><span class="game-copy"><strong>${title}</strong><span>${text}</span><span class="game-chips">${chips}</span></span>${chevron}</button>`;
  const foot = done === 3 ? 'На сегодня всё. Новые задания появятся завтра' : 'Новые задания каждый день';
  return `<section class="games-hub">${brandHeader()}<h1 class="p-title">Игры</h1><p class="games-sub">Короткие практики на каждый день: пара минут, чтобы думать о карьере легче</p><div class="games-today" role="group" aria-label="Игры за сегодня"><span class="p-kicker">Сегодня</span><span class="games-ruler" aria-hidden="true"><i class="${wordleDone ? 'on' : ''}"></i><i class="${mythsDone ? 'on' : ''}"></i><i class="${resumeDone ? 'on' : ''}"></i></span><strong>${done} из 3</strong></div><div class="games-list">${card('wordle', '#games/wordle', 'Слово дня', 'Угадайте термин из мира работы и учёбы за шесть попыток.', wordleChip)}${card('myths', '#games/myths', 'Миф или правда', `${PER_DAY} утверждений о карьере. Узнайте, что правда, а что распространённый миф.`, mythsChip)}${card('resume', '#games/resume', 'Резюме-детектор', 'Найдите слабые строки в резюме и узнайте, как их переписать.', resumeChip)}</div><p class="games-foot">${foot}</p>${renderMainNav('path')}</section>`;
}

/* ───────────── Слово дня ───────────── */
function renderWordle(date) {
  const game = loadGame(date), answer = wordForDay(date), word = answer.w, len = word.length;
  const marks = game.guesses.map(guess => scoreGuess(guess, word));
  const finished = game.status !== 'play';
  const rows = Array.from({ length:MAX_TRIES }, (_, row) => {
    const submitted = row < game.guesses.length;
    const current = row === game.guesses.length && !finished;
    const text = submitted ? game.guesses[row] : current ? ui.input : '';
    const win = submitted && game.status === 'won' && row === game.guesses.length - 1;
    const classes = ['wd-row', current && ui.shake ? 'is-shake' : '', submitted && row === ui.reveal ? 'is-reveal' : '', win ? 'is-win' : ''].filter(Boolean).join(' ');
    const tiles = Array.from({ length:len }, (_, i) => {
      const letter = text[i] ? upper(text[i]) : '';
      const mark = submitted ? marks[row][i] : '';
      const cls = ['wd-tile', mark ? `is-${mark}` : '', letter && !submitted ? 'is-filled' : '', current && i === ui.pop ? 'is-pop' : ''].filter(Boolean).join(' ');
      const label = submitted ? `${letter}: ${mark === 'correct' ? 'на своём месте' : mark === 'present' ? 'есть в слове, но не здесь' : 'нет в слове'}` : letter || 'пусто';
      return `<span class="${cls}" style="--i:${i}" role="gridcell" aria-label="${escapeHtml(label)}">${escapeHtml(letter)}</span>`;
    }).join('');
    return `<div class="${classes}" role="row">${tiles}</div>`;
  }).join('');
  const states = keyStates(game.guesses, word);
  const keyRows = ['йцукенгшщзх', 'фывапролджэ', 'ячсмитьбю'];
  const enterArrow = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 12h16m-7-7 7 7-7 7"/></svg>';
  const key = letter => `<button class="wd-key${states[letter] ? ` is-${states[letter]}` : ''}" data-g="key:${letter}" aria-label="${upper(letter)}">${upper(letter)}</button>`;
  const keyboard = `<div class="wd-kb" role="group" aria-label="Клавиатура">${keyRows.map((row, index) => `<div class="wd-kb-row">${index === 2 ? `<button class="wd-key is-wide is-enter${ui.input.length === len ? ' is-ready' : ''}" data-g="enter">Ввод ${enterArrow}</button>` : ''}${[...row].map(key).join('')}${index === 2 ? `<button class="wd-key is-wide" data-g="back" aria-label="Стереть">${deleteKey}</button>` : ''}</div>`).join('')}</div>`;
  const stats = loadStats('wordle');
  const won = game.status === 'won';
  const delay = ui.reveal >= 0 ? ' is-late' : '';
  const end = finished ? `<div class="wd-end${won ? ' is-won' : ' is-lost'}${delay}"><span class="p-kicker">${won ? `Угадано за ${game.guesses.length} из ${MAX_TRIES}` : 'Не в этот раз'}</span><h2 class="wd-word">${escapeHtml(upper(answer.d || word))}</h2><div class="wd-stats"><div><b>${currentStreak('wordle', date)}</b><span>серия</span></div><div><b>${stats.best}</b><span>лучшая</span></div><div><b>${stats.played}</b><span>игр</span></div></div><p class="game-note" role="status" aria-live="polite">${escapeHtml(ui.note)}</p><span class="p-cta wd-cta"><button class="p-btn" data-g="share"><span>Поделиться</span>${arrow}</button></span><button class="game-link" data-route="#games/myths">Дальше: «Миф или правда» →</button></div>` : '';
  const note = finished ? '' : `<p class="game-note" role="status" aria-live="polite">${escapeHtml(ui.note)}</p>`;
  return `<section class="game-screen game-wordle">${top('wordle')}<h1 class="p-title game-title">Слово дня</h1>${howBlock('wordle')}<div class="wd-clue"><span class="p-kicker">Подсказка · ${escapeHtml(answer.t)}</span><p>${escapeHtml(answer.h)}</p><span class="wd-meta">${len} ${letters(len)} · ${MAX_TRIES} попыток</span></div><ul class="wd-legend" aria-label="Как читать цвета"><li><i class="is-correct"></i>на своём месте</li><li><i class="is-present"></i>есть в слове</li><li><i class="is-absent"></i>нет в слове</li></ul>${note}<div class="wd-board" style="--n:${len}" role="grid" aria-label="Поле игры">${rows}</div>${finished ? end : keyboard}</section>`;
}

/* ───────────── Миф или правда ───────────── */
function renderMyths(date) {
  const game = loadMyths(date), cards = cardsForDay(date);
  const finished = game.index >= PER_DAY;
  const ruler = `<div class="my-ruler" role="img" aria-label="Пройдено ${Math.min(game.index, PER_DAY)} из ${PER_DAY}">${cards.map((card, i) => {
    const answered = i < game.answers.length;
    const cls = answered ? (game.answers[i] === card.t ? 'is-right' : 'is-wrong') : i === game.index ? 'is-now' : '';
    return `<i class="${cls}"></i>`;
  }).join('')}</div>`;
  if (finished) {
    const score = scoreOf(game, cards);
    const verdict = score >= 4 ? 'Вы уверенно отличаете мифы от правды' : score >= 2 ? 'Часть утверждений оказалась неожиданной' : 'Много неожиданного: разберите ответы ниже';
    const review = cards.map((card, i) => `<li class="my-row ${game.answers[i] === card.t ? 'is-right' : 'is-wrong'}" style="--i:${i}"><i aria-hidden="true">${game.answers[i] === card.t ? '✓' : '✕'}</i><span>${escapeHtml(card.s)}</span><b>${card.t ? 'Правда' : 'Миф'}</b></li>`).join('');
    const wordlePending = loadGame(date).status === 'play';
    const next = wordlePending ? '<span class="p-cta my-cta"><button class="p-btn" data-route="#games/wordle"><span>Сыграть в «Слово дня»</span>' + arrow + '</button></span>' : '<span class="p-cta my-cta"><button class="p-btn" data-route="#opportunities"><span>Открыть возможности</span>' + arrow + '</button></span>';
    return `<section class="game-screen game-myths">${top('myths')}<h1 class="p-title game-title">Итоги</h1>${ruler}<div class="my-score"><b>${score}<small>/${PER_DAY}</small></b><p>${verdict}</p></div><ul class="my-review">${review}</ul>${next}<button class="game-link" data-route="#games">К играм →</button></section>`;
  }
  const card = cards[game.index], answered = game.answers.length > game.index, said = game.answers[game.index];
  const right = answered && said === card.t;
  const choice = (value, label, cls) => {
    const picked = answered && said === value, key = answered && card.t === value;
    const state = !answered ? '' : picked ? (right ? ' is-ok' : ' is-bad') : key ? ' is-key' : ' is-dim';
    return `<button class="my-btn ${cls}${state}" data-g="say:${value}"${answered ? ' disabled' : ''}><span>${label}</span></button>`;
  };
  const choices = `<div class="my-choices">${choice(true, 'Правда', 'is-true')}${choice(false, 'Миф', 'is-false')}</div>`;
  const result = answered ? `<div class="my-result${right ? ' is-right' : ' is-wrong'}${ui.stamp ? ' is-stamp' : ''}"><div class="my-verdict"><span class="p-stamp ${right ? 'p-stamp--success' : 'p-stamp--error'}"><span>${right ? 'Верно' : 'Не угадали'}</span></span><strong>${card.t ? 'Это правда' : 'Это миф'}</strong></div><p>${escapeHtml(card.why)}</p></div><button class="p-btn p-btn--primary my-next" data-g="next"><span>${game.index === PER_DAY - 1 ? 'Показать итоги' : 'Дальше'}</span>${arrow}</button>` : '';
  return `<section class="game-screen game-myths">${top('myths')}<h1 class="p-title game-title">Миф или правда</h1>${howBlock('myths')}${ruler}<div class="my-card${ui.slide ? ' is-slide' : ''}${answered ? ' is-answered' : ''}"><span class="my-num" aria-hidden="true">${String(game.index + 1).padStart(2, '0')}</span><span class="p-kicker">Утверждение ${game.index + 1} из ${PER_DAY}</span><p class="my-statement">${escapeHtml(card.s)}</p></div>${choices}${result}</section>`;
}

function renderResume(date) {
  const resume = resumeForDay(date), game = loadResume(date), total = weakCount(resume);
  const score = scoreResume(game, resume);
  let n = -1;
  const body = resume.items.map(item => {
    if (item.h) return `<h2 class="rs-h">${escapeHtml(item.h)}</h2>`;
    n += 1;
    const i = n, picked = game.picked.includes(i);
    let state = picked ? ' is-picked' : '';
    let mark = '';
    if (game.checked) {
      state = item.bad ? (picked ? ' is-hit' : ' is-miss') : (picked ? ' is-false' : '');
      mark = item.bad ? (picked ? '✓' : '✕') : (picked ? '!' : '');
    }
    const why = game.checked && item.bad ? `<p class="rs-why"><b>Почему слабо.</b> ${escapeHtml(item.why)}<span class="rs-fix">Лучше: ${escapeHtml(item.fix)}</span></p>` : '';
    return `<div class="rs-item"><button class="rs-line${state}" data-g="pick:${i}" aria-pressed="${picked}"${game.checked ? ' disabled' : ''}><i class="rs-box" aria-hidden="true">${mark}</i><span>${escapeHtml(item.t)}</span></button>${why}</div>`;
  }).join('');
  const verdict = score.perfect ? 'Идеально: все слабые строки найдены, лишнего нет'
    : score.hits === total ? 'Все слабые строки найдены, но были лишние отметки'
    : score.hits ? 'Часть слабых мест найдена. Остальные разобраны ниже' : 'Слабые места спрятались. Разбор — ниже';
  const finish = `<div class="rs-score${ui.stamp ? ' is-stamp' : ''}"><b>${score.hits}<small>/${total}</small></b><div><p>${verdict}</p>${score.falseAlarms ? `<span class="rs-extra">Лишних отметок: ${score.falseAlarms}</span>` : ''}</div></div><span class="p-cta my-cta"><button class="p-btn" data-route="#opportunities"><span>Открыть возможности</span>${arrow}</button></span><button class="game-link" data-route="#games">К играм →</button>`;
  const controls = `<div class="rs-bar"><span class="rs-count">Отмечено: ${game.picked.length}</span><button class="p-btn p-btn--primary rs-check" data-g="check"${game.picked.length ? '' : ' disabled'}><span>Проверить</span>${arrow}</button></div>`;
  return `<section class="game-screen game-resume">${top('resume')}<h1 class="p-title game-title">Резюме-детектор</h1>${howBlock('resume')}<div class="wd-clue"><span class="p-kicker">Задача · ${escapeHtml(resume.role)}</span><p>Найдите строки, которые слабо работают на отклик</p><span class="wd-meta">Слабых строк: ${total} · нажимайте, чтобы отметить</span></div><article class="rs-sheet"><header class="rs-head"><b>${escapeHtml(resume.name)}</b><span>${escapeHtml(resume.sub)}</span></header>${body}</article>${game.checked ? finish : controls}</section>`;
}

export function renderGames(route, date = today()) {
  if (route !== ui.route) {
    ui.route = route; ui.input = ''; flash(); ui.slide = true;
    const name = route.replace('#games/', '');
    ui.how = Boolean(HOW[name]) && !read(`seen.${name}`, false);
    if (HOW[name]) write(`seen.${name}`, true);
  }
  if (route === '#games/wordle') return renderWordle(date);
  if (route === '#games/myths') return renderMyths(date);
  if (route === '#games/resume') return renderResume(date);
  return renderHub(date);
}

/* ───────────── События ───────────── */
function copy(text) {
  const done = message => { ui.note = message; redrawNow(); };
  try {
    if (globalThis.navigator?.share) return globalThis.navigator.share({ text }).then(() => done('Отправлено'), () => {});
    return globalThis.navigator.clipboard.writeText(text).then(() => done('Результат скопирован'), () => done('Не удалось скопировать'));
  } catch { done('Не удалось скопировать'); }
}

function act(kind, arg) {
  const date = today();
  if (kind === 'how') { ui.how = !ui.how; return 'morph'; }
  if (ui.route === '#games/resume') {
    const game = loadResume(date);
    flash();
    if (kind === 'pick') { const next = togglePick(game, Number(arg)); return next !== game && 'morph'; }
    if (kind === 'check') { const next = checkResume(game); ui.stamp = next !== game; return next !== game && 'morph'; }
    return false;
  }
  if (ui.route === '#games/wordle') {
    const game = loadGame(date);
    const word = wordForDay(date).w;
    flash();
    if (kind === 'share') { if (game.status !== 'play') copy(shareText(game)); return false; }
    if (game.status !== 'play') return false;
    if (kind === 'key') { if (ui.input.length >= word.length) return false; ui.input += arg; ui.pop = ui.input.length - 1; return 'morph'; }
    if (kind === 'back') { if (!ui.input) return false; ui.input = ui.input.slice(0, -1); return 'morph'; }
    if (kind === 'enter') {
      if (ui.input.length < word.length) { ui.note = `Нужно ${word.length} ${letters(word.length)}`; ui.shake = true; return 'morph'; }
      const next = submitGuess(game, ui.input);
      ui.input = ''; ui.reveal = next.guesses.length - 1;
      return 'morph';
    }
    return false;
  }
  if (ui.route === '#games/myths') {
    const game = loadMyths(date);
    flash();
    if (kind === 'say') { const next = answerCard(game, arg === 'true'); ui.stamp = next !== game; return ui.stamp && 'morph'; }
    if (kind === 'next') { const next = nextCard(game); ui.slide = true; return next !== game && 'morph'; }
  }
  return false;
}

const RU = { KeyQ:'й', KeyW:'ц', KeyE:'у', KeyR:'к', KeyT:'е', KeyY:'н', KeyU:'г', KeyI:'ш', KeyO:'щ', KeyP:'з', BracketLeft:'х', BracketRight:'ъ', KeyA:'ф', KeyS:'ы', KeyD:'в', KeyF:'а', KeyG:'п', KeyH:'р', KeyJ:'о', KeyK:'л', KeyL:'д', Semicolon:'ж', Quote:'э', KeyZ:'я', KeyX:'ч', KeyC:'с', KeyV:'м', KeyB:'и', KeyN:'т', KeyM:'ь', Comma:'б', Period:'ю' };

export function bindGames({ root, redraw, morph }) {
  const apply = mode => { if (mode === 'morph' && morph?.()) return; if (mode) redraw(); };
  redrawNow = redraw;
  root.addEventListener('click', event => {
    const control = event.target.closest?.('[data-g]');
    if (!control) return;
    const [kind, arg] = control.dataset.g.split(':');
    apply(act(kind, arg));
  });
  // Физическая клавиатура: русские буквы и английская раскладка по положению клавиш
  globalThis.window?.addEventListener?.('keydown', event => {
    if (ui.route !== '#games/wordle' || globalThis.location?.hash !== '#games/wordle' || event.ctrlKey || event.metaKey || event.altKey) return;
    if (['INPUT', 'TEXTAREA', 'SELECT'].includes(event.target?.tagName)) return;
    let kind = null, arg;
    if (event.key === 'Backspace') kind = 'back';
    else if (event.key === 'Enter') kind = 'enter';
    else { const letter = /^[а-яё]$/i.test(event.key) ? normalize(event.key) : RU[event.code]; if (letter) { kind = 'key'; arg = letter; } }
    if (!kind) return;
    event.preventDefault();
    apply(act(kind, arg));
  });
}
