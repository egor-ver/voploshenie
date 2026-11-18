import { escapeHtml } from '../escape.js';
import { groupDeadlines } from '../domain/deadlines.js';
import { renderMainNav } from './nav.js';
import { brandHeader } from './brand.js';
import { readableDateText } from '../domain/date-text.js';
import { localDate } from '../domain/local-date.js';

const monthNames = ['Январь','Февраль','Март','Апрель','Май','Июнь','Июль','Август','Сентябрь','Октябрь','Ноябрь','Декабрь'];
const kinds = { apply_deadline:'Приём заявок', start:'Начало', end:'Окончание' };
const types = { olympiad:'Олимпиада', internship:'Стажировка', course:'Курс', event:'Событие' };
const weekdays = ['ВС','ПН','ВТ','СР','ЧТ','ПТ','СБ'];
const monthShort = ['ЯНВ','ФЕВ','МАР','АПР','МАЙ','ИЮН','ИЮЛ','АВГ','СЕН','ОКТ','НОЯ','ДЕК'];

export function renderDeadlines({ items = [], favorites = [], today = localDate() } = {}) {
  const groups = groupDeadlines(items.filter(item => favorites.includes(item.id)), today).map(group => ({ ...group, entries:group.entries.map((entry, index) => ({ entry, index })).sort((a, b) => (a.entry.date ? 0 : 1) - (b.entry.date ? 0 : 1) || (a.entry.date?.date || '').localeCompare(b.entry.date?.date || '') || a.index - b.index).map(({ entry }) => entry) }));
  const nearest = groups.flatMap(group => group.entries).find(({ date }) => date?.precision === 'day' && daysBetween(today, date.date) >= 0)?.item.id;
  const soon = groups.flatMap(group => group.entries).filter(({ date }) => date?.precision === 'day' && daysBetween(today, date.date) <= 30).length;
  const days = groups.flatMap(group => group.entries).filter(({ date }) => date?.precision === 'day').map(({ date }) => daysBetween(today, date.date));
  const nearestDays = days.length ? Math.min(...days) : null;
  const subtitle = soon ? `${soon} ${plural(soon, 'событие', 'события', 'событий')} в ближайший месяц` : (nearestDays => nearestDays === null ? 'Сроки сохранённых возможностей' : nearestDays === 0 ? 'Ближайший дедлайн — сегодня' : `Ближайший дедлайн — через ${nearestDays} ${plural(nearestDays, 'день', 'дня', 'дней')}`)(nearestDays);
  const body = groups.length ? groups.map((group, index) => `<section class="deadline-group${index ? '' : ' is-first'}"><h2 class="deadline-month">${escapeHtml(group.month ? monthNames[Number(group.month.slice(5)) - 1] : group.year ? String(group.year) : 'Без даты')}${group.month ? ` <span class="deadline-year">${escapeHtml(group.month.slice(0,4))}</span>` : group.year ? ` <span class="deadline-year">В течение года</span>` : ''}</h2><div class="deadline-list">${group.entries.map(({ item, date }) => entry(item, date, item.id === nearest, today)).join('')}</div></section>`).join('') : '<div class="deadline-empty"><div class="deadline-empty__art" aria-hidden="true"><span class="deadline-empty__sheet"><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i class="is-on"></i><i></i><i></i><i></i><i></i><i></i><i></i></span></div><span class="p-kicker">Пока пусто</span><p>Сохраните возможность в разделе «Возможности» — здесь появятся даты приёма заявок и старта</p><button class="p-btn p-btn--primary" data-route="#opportunities"><span>Открыть возможности</span><svg aria-hidden="true" viewBox="0 0 24 24"><path d="M4 12h16m-7-7 7 7-7 7"/></svg></button></div>';
  const internship = groups.flatMap(group => group.entries).find(({ item }) => item.type === 'internship');
  const cta = internship ? `<div class="deadline-cta"><span class="p-cta"><button class="p-btn" data-route="#detail/${escapeHtml(encodeURIComponent(internship.item.id))}">Посмотреть стажировку <span aria-hidden="true">→</span></button></span></div>` : '';
  return `<section class="screen-deadlines">${brandHeader()}<h1 class="p-title">Дедлайны</h1><p class="deadline-sub">${escapeHtml(subtitle)}</p><div class="deadline-content">${body}</div>${cta}${renderMainNav('deadlines')}</section>`;
}

function entry(item, date, isNearest, today) {
  const day = date?.precision === 'day';
  const left = daysBetween(today, date?.date);
  const stub = day ? `<b>${escapeHtml(date.date.slice(8))}</b><span>${weekdays[new Date(`${date.date}T00:00:00Z`).getUTCDay()]}</span>`
    : date?.precision === 'month' ? `<b>—</b><span>${monthShort[Number(date.date.slice(5)) - 1]}</span>`
    : date ? '<b>—</b><span>год</span>' : '<b>—</b><span></span>';
  const mark = day && isNearest ? `<span class="deadline-kicker">${left === 0 ? 'Сегодня' : `Через ${left} ${plural(left, 'день', 'дня', 'дней')}`}</span>` : '';
  const tail = day && !isNearest && left <= 7 ? `<span class="deadline-chip">через ${left} ${plural(left, 'день', 'дня', 'дней')}</span>` : '<span class="deadline-arrow" aria-hidden="true">→</span>';
  const meta = [types[item.type], deadlineLabel(date, item.date_text)].filter(Boolean).join(' · ');
  return `<button class="deadline-entry${isNearest ? ' is-nearest' : ''}" data-item="${escapeHtml(item.id)}"><span class="deadline-stub">${stub}</span><span class="deadline-text">${mark}<strong>${escapeHtml(item.title)}</strong><span class="deadline-meta">${escapeHtml(meta)}</span></span>${tail}</button>`;
}

function daysBetween(from, to) {
  if (!to || !/^\d{4}-\d{2}-\d{2}$/.test(to)) return Infinity;
  return Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86400000);
}

function plural(n, one, few, many) {
  const m10 = n % 10, m100 = n % 100;
  return m10 === 1 && m100 !== 11 ? one : m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14) ? few : many;
}

function deadlineLabel(date, fallback) {
  if (!date) return readableDateText(fallback) || 'Дата уточняется';
  const text = date.precision === 'day' ? dayDate(date.date) : date.precision === 'month' ? `${monthNames[Number(date.date.slice(5)) - 1]} ${date.date.slice(0,4)}` : date.date;
  return [kinds[date.kind], text].filter(Boolean).join(' · ');
}

function dayDate(value) {
  const parts = new Intl.DateTimeFormat('ru-RU', { day:'numeric', month:'long', year:'numeric', timeZone:'UTC' }).formatToParts(new Date(`${value}T00:00:00Z`));
  return `${parts.find(part => part.type === 'day').value} ${parts.find(part => part.type === 'month').value} ${parts.find(part => part.type === 'year').value}`;
}
