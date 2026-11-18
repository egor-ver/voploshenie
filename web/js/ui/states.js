import { escapeHtml } from '../escape.js';
import { brandHeader } from './brand.js';

export const arrowIcon = '<svg class="arrow" viewBox="0 0 18 18" aria-hidden="true"><path d="M1 9H16M10 15L16 9L10 3" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>';

export const primaryAction = (label, attributes) => `<button class="primary" ${attributes}>${escapeHtml(label)}${arrowIcon}</button>`;

export function stateContent({ title, body, actions = '', level = 1, art = '', variant = '' }) {
  return `<div class="state-grid${variant ? ` state-grid--${variant}` : ''}" aria-hidden="true">${art}</div><h${level}>${escapeHtml(title)}</h${level}><p class="state-body">${escapeHtml(body)}</p><div class="state-actions">${actions}</div>`;
}

export const stateScreen = ({ nav = '', ...content }) => `<section class="state-screen">${brandHeader()}${stateContent(content)}${nav}</section>`;

export function renderLoading() {
  const card = (kicker, lines, meta) => `<div class="skeleton-card"><div class="skeleton-main"><i style="width:${kicker}px"></i>${lines.map(w => `<i style="width:${w}px"></i>`).join('')}<i class="skeleton-meta" style="width:${meta}px"></i></div><div class="skeleton-stub"><i></i><i></i></div></div>`;
  return `<section class="state-screen state-screen--loading" role="status" aria-busy="true">${brandHeader()}<h1 class="p-load-title">Подбираем возможности</h1><p class="screen-subtitle">Это займёт немного времени</p><div class="p-loading-dots" aria-hidden="true"><i></i><i></i></div><div class="skeleton-list">${card(110, [170], 130)}${card(80, [200, 120], 150)}</div></section>`;
}

export function renderLoadError() {
  return stateScreen({ title:'Не получилось загрузить', body:'Проверьте подключение к интернету и попробуйте ещё раз. Сохранённое никуда не пропадёт.', actions:'<button class="primary" data-action="retry">Повторить<span aria-hidden="true">↻</span></button><button class="link-button p-link" data-route="#saved">Открыть избранное</button>', variant:'error', art:'<b>!</b>' });
}
