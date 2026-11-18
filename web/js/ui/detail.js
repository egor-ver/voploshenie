import { escapeHtml } from '../escape.js';
import { formatName, sphereName, typeName } from './opportunities.js';
import { renderMainNav } from './nav.js';
import { opportunityClosed } from '../domain/catalog.js';
import { primaryAction, stateScreen } from './states.js';
import { brandHeader } from './brand.js';
import { readableDateText, capitalize } from '../domain/date-text.js';
import { localDate } from '../domain/local-date.js';

const dateLabels = { apply_deadline:'Приём заявок', start:'Начало', end:'Окончание' };
const months = ['января','февраля','марта','апреля','мая','июня','июля','августа','сентября','октября','ноября','декабря'];
const monthNames = ['январь','февраль','март','апрель','май','июнь','июль','август','сентябрь','октябрь','ноябрь','декабрь'];

function humanDate(date) {
  if (date.precision === 'day') { const [year, month, day] = date.date.split('-').map(Number); return `${day} ${months[month - 1]} ${year}`; }
  if (date.precision === 'month') { const [year, month] = date.date.split('-').map(Number); return `${monthNames[month - 1]} ${year}`; }
  return date.date;
}

const shortMonths = ['янв','фев','мар','апр','мая','июн','июл','авг','сен','окт','ноя','дек'];
function shortDate(date) {
  const [year, month, day] = date.date.split('-').map(Number);
  if (date.precision === 'day') return { big:String(day), small:shortMonths[month - 1] };
  if (date.precision === 'month') return { big:shortMonths[month - 1], small:String(year) };
  return { big:date.date, small:'' };
}
const stampText = date => { const s = shortDate(date); return `${s.big} ${s.small}`; };
const arrow = '<span aria-hidden="true">→</span>';
const saveButton = (id, saved, cls = 'detail-save') => `<button class="${cls}${saved ? ' is-saved' : ''}" data-save="${escapeHtml(id)}">${saved ? '♥ В избранном' : '♡ Сохранить'}</button>`;

function lastDay(item) {
  const days = (item.dates || []).filter(date => date.precision === 'day').map(date => date.date).sort();
  if (!days.length) return '';
  const [, month, day] = days[days.length - 1].split('-');
  return `${day}.${month}`;
}

function stageRuler(dates, fallback = '') {
  const steps = dates.filter(date => dateLabels[date.kind]);
  if (steps.length < (fallback ? 1 : 2)) return fallback ? `<ol class="detail-stages" style="--steps:1" aria-label="Этапы"><li class="detail-stage is-first"><i></i><b>${escapeHtml(readableDateText(fallback))}</b></li></ol>` : '';
  const cells = steps.map((date, index) => { const s = shortDate(date); return `<li class="detail-stage${index === 0 ? ' is-first' : ''}"><i></i><b>${escapeHtml(`${s.big} ${s.small}`.trim())}</b><span>${dateLabels[date.kind]} · ${escapeHtml(humanDate(date))}</span></li>`; }).join('');
  return `<ol class="detail-stages" style="--steps:${steps.length}" aria-label="Этапы">${cells}</ol>`;
}

function factsGrid(item) {
  const hasDates = (item.dates || []).some(date => dateLabels[date.kind]);
  const cells = [['Формат', formatName(item.format)], ['Для кого', capitalize(item.audience_text)], ['Сроки', hasDates ? '' : readableDateText(item.date_text)], ['Сфера', (item.spheres || []).map(sphereName).join(', ')]].filter(([, value]) => value);
  return cells.map(([label, value]) => `<div class="detail-cell"><span>${label}</span><b>${escapeHtml(value)}</b></div>`).join('');
}

const gap = '<span class="detail-gap"></span>';
const staircase = '<svg class="detail-stairs" viewBox="0 0 390 230" aria-hidden="true"><path d="M40 190 V150 H120 V110 H200 V70" fill="none" stroke="var(--p-primary)" stroke-width="2.5"/><path d="M200 70 H280 V30 H350" fill="none" stroke="var(--p-outline)" stroke-width="1.5" stroke-dasharray="4 4"/></svg>';

function renderClosed({ item, saved, backRoute, backLabel, matchLabel }) {
  const lastDate = (item.dates || []).filter(date => date.precision === 'day').sort((a, b) => a.date.localeCompare(b.date)).pop();
  const stampDay = lastDate ? lastDate.date.split('-').reverse().slice(0, 2).join('.') : '';
  const stamp = `<div class="detail-closed-art" aria-hidden="true">${staircase}<span class="detail-closed-stamp"><span>Приём</span><b>закрыт</b>${stampDay ? `<span>${stampDay}</span>` : ''}</span></div>`;
  const savedRow = `<button class="detail-saved-row${saved ? ' is-saved' : ''}" data-save="${escapeHtml(item.id)}">${saved ? '♥ Сохранено на следующий набор' : '♡ Сохранить на следующий набор'}</button>`;
  const body = `${matchLabel ? `${matchLabel}. ` : ''}${lastDate ? `Сроки закончились ${humanDate(lastDate)}. ` : 'Сроки закончились. '}Можно найти похожую возможность или сохранить её на следующий набор.`;
  return `<section class="detail-screen detail-closed">${brandHeader()}${stamp}<h1 class="p-title">Регистрация<br>уже закрыта</h1><p class="detail-closed-body">${escapeHtml(body)}</p>${savedRow}<button class="detail-back" data-route="${escapeHtml(backRoute)}">← ${escapeHtml(backLabel)}</button>${gap}<button class="detail-similar" data-route="#similar/${escapeHtml(encodeURIComponent(item.id))}">Найти похожие ${arrow}</button>${renderMainNav('opportunities')}</section>`;
}

function renderOlympiad({ item, saved, backRoute, backLabel, matchLabel, cta }) {
  const sub = [item.direction, capitalize(item.audience_text)].filter(Boolean).join(' · ');
  const photo = `<div class="detail-photo"><img src="img/start-photo.jpg" alt=""><i></i><i></i><i></i><i></i><span>Олимпиада${formatName(item.format) ? ` · ${escapeHtml(formatName(item.format))}` : ''}</span></div>`;
  return `<section class="detail-screen detail-olympiad"><div class="detail-toolbar"><button class="detail-back" data-route="${escapeHtml(backRoute)}">← ${escapeHtml(backLabel)}</button>${saveButton(item.id, saved)}</div>${photo}<h1 class="p-title p-title--bare">${escapeHtml(item.title)}</h1>${sub ? `<p class="detail-sub">${escapeHtml(sub)}</p>` : ''}${stageRuler(item.dates || [], item.date_text || '')}${matchLabel ? `<p class="detail-match">${escapeHtml(matchLabel)}</p>` : ''}${cta ? gap + cta : ''}${renderMainNav('opportunities')}</section>`;
}

export function renderDetail({ item, favorites = [], today = localDate(), matchPercent = null, backRoute = '#opportunities' } = {}) {
  if (!item) return `<section class="detail-screen">${brandHeader()}<p>Возможность не найдена</p>${renderMainNav('opportunities')}</section>`;
  const saved = favorites.includes(item.id);
  const backLabel = backRoute === '#saved' ? 'Избранное' : backRoute === '#deadlines' ? 'Дедлайны' : 'Возможности';
  const matchLabel = Number.isFinite(matchPercent) ? `Совпадение с вашим профилем: ${matchPercent}% (по результатам профтеста)` : '';
  if (opportunityClosed(item, today)) return renderClosed({ item, saved, backRoute, backLabel, matchLabel });
  const dates = item.dates || [];
  const url = /^https?:\/\//i.test(item.url || '') ? item.url : '';
  const cta = url ? `<div class="detail-cta"><a class="detail-primary" href="${escapeHtml(url)}" target="_blank" rel="noopener noreferrer">${item.url_kind === 'program' ? (item.type === 'olympiad' ? 'Зарегистрироваться' : 'Подать заявку') : 'Открыть сайт организатора'}${arrow}</a></div>` : '';
  if (item.type === 'olympiad') return renderOlympiad({ item, saved, backRoute, backLabel, matchLabel, cta });
  const deadline = dates.find(date => date.kind === 'apply_deadline' && date.precision === 'day');
  const stamp = deadline ? `<span class="detail-stamp"><span>Подать до</span><b>${escapeHtml(stampText(deadline))}</b></span>` : '';
  const ruler = stageRuler(dates);
  const lines = ruler ? '' : dates.map(date => dateLabels[date.kind] ? `<p>${dateLabels[date.kind]} · ${escapeHtml(humanDate(date))}</p>` : '').join('');
  return `<section class="detail-screen"><div class="detail-toolbar"><button class="detail-back" data-route="${escapeHtml(backRoute)}">← ${escapeHtml(backLabel)}</button>${saveButton(item.id, saved)}</div><p class="detail-kicker">${escapeHtml(typeName(item.type))}${item.spheres?.length ? ` · ${escapeHtml(item.spheres.map(sphereName).join(' · '))}` : ''}</p><div class="detail-head${stamp ? ' has-stamp' : ''}"><h1 class="p-title p-title--bare">${escapeHtml(item.title)}</h1>${stamp}</div>${item.organizer ? `<p class="detail-organizer">${escapeHtml(item.organizer)}</p>` : ''}${ruler}<div class="detail-facts"><div class="detail-grid">${factsGrid(item)}</div>${lines}</div>${item.direction ? `<h2>Что вы будете делать</h2><p class="detail-direction">${escapeHtml(item.direction)}</p>` : ''}${matchLabel ? `<p class="detail-match">${escapeHtml(matchLabel)}</p>` : ''}${cta ? gap + cta : ''}${renderMainNav('opportunities')}</section>`;
}
