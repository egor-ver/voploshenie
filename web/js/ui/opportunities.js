import { escapeHtml } from '../escape.js';
import { matchPercent, byCourse, byInterests, byNearestDate, nearestDate, recommendations, similar } from '../domain/catalog.js';
import { renderMainNav } from './nav.js';
import { brandHeader } from './brand.js';
import { primaryAction, stateContent, stateScreen } from './states.js';
import { localDate } from '../domain/local-date.js';
import { sphereName as fullSphereName } from '../domain/sphere-name.js';
import { capitalize, isRollingText } from '../domain/date-text.js';

const types = { olympiad:'Олимпиада', internship:'Стажировка', course:'Курс', event:'Событие' };
const formats = { remote:'Удалённо', offline:'Очно', hybrid:'Очно и удалённо' };
const months = ['ЯНВ','ФЕВ','МАР','АПР','МАЙ','ИЮН','ИЮЛ','АВГ','СЕН','ОКТ','НОЯ','ДЕК'];
const searchIcon = '<svg class="search-icon" viewBox="0 0 14 14" aria-hidden="true"><circle cx="6" cy="6" r="4.6" fill="none" stroke="currentColor" stroke-width="1.8"/><path d="M9.4 9.4 13 13" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>';
const heartIcon='<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20.8 8.8c0 5.1-8.8 11-8.8 11s-8.8-5.9-8.8-11a4.8 4.8 0 0 1 8.8-2.6 4.8 4.8 0 0 1 8.8 2.6Z" fill="none" stroke="currentColor" stroke-width="var(--stroke)" stroke-linecap="round" stroke-linejoin="round"/></svg>';
const typeIcons = {
  olympiad:'<svg viewBox="0 0 24 24"><path d="M8 4h8v5a4 4 0 0 1-8 0V4ZM5 5h3m8 0h3M12 13v4m-3 3h6"/></svg>',
  internship:'<svg viewBox="0 0 24 24"><path d="M4 8h16v11H4V8Zm5 0V5h6v3M4 13h16"/></svg>',
  course:'<svg viewBox="0 0 24 24"><path d="M4 5h6a2 2 0 0 1 2 2v12a2 2 0 0 0-2-2H4V5Zm16 0h-6a2 2 0 0 0-2 2v12a2 2 0 0 1 2-2h6V5Z"/></svg>',
  event:'<svg viewBox="0 0 24 24"><path d="M4 6h16v14H4V6Zm0 4h16M8 3v4m8-4v4"/></svg>'
};
const loopIcon = '<svg viewBox="0 0 24 24"><path d="m17 2 4 4-4 4M3 11V9a3 3 0 0 1 3-3h15M7 22l-4-4 4-4m14-1v2a3 3 0 0 1-3 3H3"/></svg>';
const clockIcon = '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>';
const monthNames = ['Январь','Февраль','Март','Апрель','Май','Июнь','Июль','Август','Сентябрь','Октябрь','Ноябрь','Декабрь'];
const dayDiff = (from, to) => Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86400000);
// Группа в списке «по дедлайну»: порядок групп совпадает с порядком byNearestDate
export function dateGroup(item, today = localDate()) {
  const nearest = nearestDate(item, today);
  if (nearest === '9999-12-31') return item.is_rolling || isRollingText(item.date_text) ? 'Круглогодично' : 'Дата уточняется';
  if (nearest <= today) return 'Идёт сейчас';
  if (dayDiff(today, nearest) <= 30) return 'Ближайший месяц';
  if (item.dates.some(date => date.precision === 'year' && `${date.date}-01-01` === nearest)) return `${nearest.slice(0, 4)} год`;
  return `${monthNames[Number(nearest.slice(5, 7)) - 1]} ${nearest.slice(0, 4)}`;
}
export const sphereName = fullSphereName;
export const typeName = id => types[id] || id;
export const formatName = id => formats[id] || '';
export const audienceText = item => capitalize(item?.audience_text || '');
export function dateRow(item, today = null) {
  const rows = item.dates || [];
  const ahead = row => !today || !['day', 'month'].includes(row.precision) || row.date >= (row.precision === 'month' ? today.slice(0, 7) : today);
  return rows.find(row => row.kind === 'apply_deadline' && ahead(row)) || rows.find(row => row.kind === 'start' && ahead(row)) || (today ? rows.find(row => row.kind === 'end' && ahead(row)) : null) || rows.find(row => row.kind === 'apply_deadline') || rows.find(row => row.kind === 'start');
}
export function cardDate(item, today = null) {
  const date = dateRow(item, today);
  if (date?.precision === 'year' && !item.is_rolling && !isRollingText(item.date_text)) return { day:'', month:date.date };
  if (!date || !['day','month'].includes(date.precision)) return { day:'', month:item.is_rolling || isRollingText(item.date_text) ? 'Круглогодично' : 'Дата уточняется' };
  const [, month, day] = date.date.split('-').map(Number);
  return { day:date.precision === 'day' ? String(day) : '', month:months[month - 1], ...(date.kind === 'end' ? { until:true } : {}) };
}
export function opportunityCard(item, saved, note = '', savedPage = false, { featured = false } = {}) {
  const today = localDate();
  const date = cardDate(item, today);
  const row = dateRow(item, today);
  const left = date.day && row?.precision === 'day' ? dayDiff(today, row.date) : null;
  const chip = left !== null && left >= 0 && left <= 14 ? `<em class="ticket-chip">${left === 0 ? 'сегодня' : date.until ? `ещё ${left} дн.` : `через ${left} дн.`}</em>` : '';
  const month = date.month;
  let stub;
  if (date.day) stub = `<span class="ticket-date"><span>${escapeHtml(date.day)}<small>${date.until ? 'до ' : ''}${escapeHtml(month)}</small>${chip}</span></span>`;
  else if (/^\d{4}$/.test(month)) stub = `<span class="ticket-date is-year"><span>${escapeHtml(month)}<small>В течение года</small></span></span>`;
  else if (months.includes(month)) stub = `<span class="ticket-date is-month"><span>${escapeHtml(month)}<small>${escapeHtml(String(row?.date || '').slice(0, 4))}</small></span></span>`;
  else stub = `<span class="ticket-date is-glyph"><span><i class="ticket-glyph" aria-hidden="true">${month === 'Круглогодично' ? loopIcon : clockIcon}</i><small>${month === 'Круглогодично' ? 'Круглый год' : 'Дата уточняется'}</small></span></span>`;
  return `<div class="opportunity-item t-${escapeHtml(item.type || 'event')}${featured ? ' is-featured' : ''}"><article class="opportunity-card${savedPage?' is-saved-page':''}"><button class="opportunity-main" data-item="${escapeHtml(item.id)}"><span class="kicker"><i class="kicker-icon" aria-hidden="true">${typeIcons[item.type] || typeIcons.event}</i><span>${escapeHtml(typeName(item.type))} · ${escapeHtml((item.spheres || []).map(sphereName).join(' · '))}</span></span><strong>${escapeHtml(item.title)}</strong><small>${escapeHtml([formatName(item.format), audienceText(item)].filter(Boolean).join(' · '))}</small>${note?`<small class="catalog-note">${escapeHtml(note)}</small>`:''}</button>${stub}</article><button class="save-toggle${saved ? ' is-saved' : ''}" data-save="${escapeHtml(item.id)}" aria-label="${saved ? 'Удалить из избранного' : 'Сохранить'}">${heartIcon}<span>${saved?'В избранном':'Сохранить'}</span></button></div>`;
}
export function locationNote(item, city, preference) {
  if (!['a','b'].includes(preference)) return '';
  if (item.format === 'remote') return 'Онлайн';
  const normalize=value=>String(value||'').trim().replace(/\s+/g,' ').toLocaleLowerCase('ru');
  return city && item.city && normalize(city)===normalize(item.city) ? 'Рядом' : '';
}
export function renderOpportunities({ items = [], favorites = [], result = null, query = '', type = '', sort = 'date', recommended = false, course = '', city = '', locationPreference = '' } = {}) {
  const normalizedQuery = query.trim().toLocaleLowerCase('ru');
  let list = recommended && result ? recommendations(items, result) : [...items].map(item=>({item,fromSphere:null}));
  if (type) list = list.filter(row => row.item.type === type);
  if (normalizedQuery) list = list.filter(({item}) => [item.title, item.organizer].filter(Boolean).join(' ').toLocaleLowerCase('ru').includes(normalizedQuery));
  if (sort === 'date') list = byNearestDate(list.map(row=>row.item), localDate()).map(item=>list.find(row=>row.item===item));
  else if (!recommended && result?.spheres) { const ordered=byInterests(list.map(row=>row.item), result); list=ordered.map(item=>list.find(row=>row.item===item)); }
  if(recommended && sort !== 'date')list=byCourse(course,list);
  return `<section class="catalog-screen">${brandHeader()}<h1>${recommended?'Подборка для вас':'Возможности'}</h1>${recommended?'<p class="catalog-recommendation">Подобрано по результату теста</p>':''}<label class="catalog-search"><span class="visually-hidden">Поиск</span>${searchIcon}<input name="catalog-query" value="${escapeHtml(query)}" placeholder="Найти возможность"></label><div class="catalog-filters" aria-label="Тип возможности">${[['','Все'],['olympiad','Олимпиады'],['internship','Стажировки'],['course','Курсы'],['event','События']].map(([key,label]) => `<button data-type="${key}" aria-pressed="${type === key}">${label}</button>`).join('')}</div><p class="catalog-count">Найдено: ${list.length}${result?.spheres?`<button class="catalog-sort" data-route="#sort">${sort === 'date' ? 'Ближайший дедлайн' : 'По вашим интересам'} ↓</button>`:''}</p>${list.length ? `<div class="catalog-list">${(() => {const today=localDate();let lastGroup='';const counts={},order=[];if(sort==='date'){list.forEach(row=>{const g=dateGroup(row.item,today);counts[g]=(counts[g]||0)+1;if(!order.includes(g))order.push(g);});list=[...list].sort((a,b)=>order.indexOf(dateGroup(a.item,today))-order.indexOf(dateGroup(b.item,today)));}return list.map((row,index) => {const match=result?.spheres?`Совпадение ${matchPercent(row.item,result)}%`:'';const note=[match,recommended?locationNote(row.item,city,locationPreference):'',recommended&&row.fromSphere==='other'?'Другие возможности':recommended&&row.fromSphere?`Также из сферы ${sphereName(row.fromSphere)}`:''].filter(Boolean).join(' · ');const group=sort==='date'?dateGroup(row.item,today):'';const head=group&&group!==lastGroup?`<p class="catalog-group"><span>${escapeHtml(group)}</span><b>${counts[group]}</b></p>`:'';lastGroup=group||lastGroup;return head+opportunityCard(row.item,favorites.includes(row.item.id),note,false,{featured:Boolean(head)&&group==='Ближайший месяц'&&cardDate(row.item).day!==''});}).join('');})()}</div>` : stateContent({ title:'Пока ничего не найдено', body:'По этим условиям предложений нет. Попробуйте изменить запрос или выбрать другой тип.', actions:primaryAction('Сбросить', recommended ? 'data-action="reset-recommended"' : 'data-action="reset-filters"'), level:2, art:'<b>0</b><b>0</b><em>Результатов</em>' })}${renderMainNav('opportunities')}</section>`;
}
export function renderSimilar({ item, items = [], favorites = [] } = {}) {
  const list = item ? similar(items, item) : [];
  if (!list.length) return stateScreen({ title:'Пока ничего не найдено', body:'Похожих возможностей нет.', actions:primaryAction('Все возможности', 'data-route="#opportunities"'), nav:renderMainNav('opportunities') });
  return `<section class="catalog-screen">${brandHeader()}<button class="link-button" data-route="#detail/${escapeHtml(encodeURIComponent(item.id))}">← Возможность</button><h1>Найти похожие</h1><div class="catalog-list">${list.map(candidate => opportunityCard(candidate, favorites.includes(candidate.id))).join('')}</div>${renderMainNav('opportunities')}</section>`;
}
