import { byNearestDate, opportunityClosed } from '../domain/catalog.js';
import { cardDate, typeName, sphereName, formatName, audienceText, opportunityCard } from './opportunities.js';
import { escapeHtml } from '../escape.js';
import { renderMainNav } from './nav.js';
import { brandHeader } from './brand.js';
import { localDate } from '../domain/local-date.js';

function emptySaved() {
  return `<section class="saved-screen saved-empty">
    ${brandHeader()}
    <h1 class="saved-title">Избранное<span aria-hidden="true">.</span></h1>
    <div class="saved-art" aria-hidden="true"><i class="saved-art-dots"></i><i class="saved-art-cut"></i><i class="saved-art-fill"></i><svg width="120" height="170" viewBox="0 0 120 170"><path d="M1 1 H119 V168 L60 128 L1 168 Z" fill="none" stroke="currentColor" stroke-width="1.5"/></svg></div>
    <p class="saved-empty-title">Здесь пока пусто.</p>
    <p class="saved-empty-text">Сохраняйте возможности ♡, чтобы вернуться к ним позже и не пропустить дедлайн.</p>
    <a class="saved-empty-action" href="#opportunities" data-route="#opportunities">Найти возможности <span aria-hidden="true">→</span></a>
    ${renderMainNav('saved')}
  </section>`;
}

export function savedCount(count) {
  const lastTwo = count % 100, last = count % 10;
  const word = lastTwo >= 11 && lastTwo <= 14 ? 'сохранённых возможностей' : last === 1 ? 'сохранённая возможность' : last >= 2 && last <= 4 ? 'сохранённые возможности' : 'сохранённых возможностей';
  return `${count} ${word}`;
}
const heart = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20.8 8.8c0 5.1-8.8 11-8.8 11s-8.8-5.9-8.8-11a4.8 4.8 0 0 1 8.8-2.6 4.8 4.8 0 0 1 8.8 2.6Z" fill="currentColor" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/></svg>';
const tabLabels = { course:'Курсы', internship:'Стажировки', olympiad:'Олимпиады', event:'События' };

function dueSoon(item, today) {
  const row = item.dates?.find(date => date.kind === 'apply_deadline' && date.precision === 'day');
  if (!row) return false;
  const days = (Date.parse(row.date) - Date.parse(today)) / 864e5;
  return days >= 0 && days <= 14;
}

function savedTicket(item, closed, today) {
  const date = cardDate(item);
  const kicker = [typeName(item.type), closed ? 'приём завершён' : (item.spheres || []).map(sphereName).join(' · ')].filter(Boolean).join(' · ');
  const meta = [formatName(item.format), audienceText(item)].filter(Boolean).join(' · ');
  const id = escapeHtml(item.id);
  const save = `<button class="save-toggle is-saved saved-heart-btn" data-save="${id}" aria-label="Удалить из избранного">${heart}</button>`;
  const main = `<button class="opportunity-main saved-main" data-item="${id}"><span class="saved-kicker">${escapeHtml(kicker)}</span><strong>${escapeHtml(item.title)}</strong>${meta ? `<small>${escapeHtml(meta)}</small>` : ''}</button>`;
  if (closed) return `<div class="opportunity-item saved-item is-closed" data-saved-type="${escapeHtml(item.type)}"><article class="saved-closed">${main}<span class="saved-stamp">Закрыт</span>${save}</article></div>`;
  return `<div class="saved-item" data-saved-type="${escapeHtml(item.type)}">${opportunityCard(item, true, dueSoon(item, today) ? 'Скоро дедлайн' : '', true)}</div>`;
}

export function renderSaved({ items = [], favorites = [] } = {}) {
  const today = localDate();
  const sorted = byNearestDate(items.filter(item => favorites.includes(item.id)), today);
  if (!sorted.length) return emptySaved();
  const closedOf = item => opportunityClosed(item, today);
  const saved = [...sorted.filter(item => !closedOf(item)), ...sorted.filter(closedOf)];
  const types = Object.keys(tabLabels).filter(type => saved.some(item => item.type === type));
  const tabs = types.length > 1 ? `<div class="saved-tabs" role="tablist" aria-label="Тип возможности">${[['', 'Все'], ...types.map(type => [type, tabLabels[type]])].map(([type, label]) => `<button class="saved-tab" role="tab" data-saved-tab="${type}" aria-selected="${type === ''}">${label}</button>`).join('')}</div>` : '';
  const count = String(saved.length).padStart(2, '0');
  return `<section class="saved-screen saved-print catalog-screen">${brandHeader()}<h1 class="saved-title">Избранное<sup class="saved-count" aria-label="${savedCount(saved.length)}">${count}</sup></h1><p class="saved-sub">${savedCount(saved.length)}</p>${tabs}<div class="saved-list">${saved.map(item => savedTicket(item, closedOf(item), today)).join('')}</div><span class="p-cta saved-cta"><a class="p-btn" href="#opportunities" data-route="#opportunities"><span>Найти ещё возможности</span><svg aria-hidden="true" viewBox="0 0 24 24"><path d="M4 12h16m-7-7 7 7-7 7"/></svg></a></span>${renderMainNav('saved')}</section>`;
}

if (typeof document !== 'undefined' && document.addEventListener) document.addEventListener('click', event => {
  const tab = event.target?.closest?.('[data-saved-tab]');
  if (!tab) return;
  const root = tab.closest('.saved-screen');
  const type = tab.dataset.savedTab;
  root.querySelectorAll('[data-saved-tab]').forEach(button => button.setAttribute('aria-selected', String(button === tab)));
  root.querySelectorAll('.saved-item').forEach(row => { row.hidden = Boolean(type) && row.dataset.savedType !== type; });
});
