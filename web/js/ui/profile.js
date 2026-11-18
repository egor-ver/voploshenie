import { escapeHtml } from '../escape.js';
import { renderMainNav } from './nav.js';
import { brandHeader } from './brand.js';
import { sphereName } from '../domain/sphere-name.js';

const clamp = value => Math.max(0, Math.min(100, Math.round(Number(value) || 0)));
const arrow = '<span aria-hidden="true">→</span>';

/* Пустой профиль: «карточка профиля» в чертёжном стиле и список того, что появится после теста */
const profileArt = '<div class="profile-art" aria-hidden="true"><svg viewBox="0 0 320 188"><defs><pattern id="pa-dots" width="10" height="10" patternUnits="userSpaceOnUse"><circle cx="2" cy="2" r="1.6" fill="currentColor"/></pattern><linearGradient id="pa-fade" x1="1" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff"/><stop offset=".8" stop-color="#000"/></linearGradient><mask id="pa-mask"><rect width="150" height="120" fill="url(#pa-fade)"/></mask></defs><g class="pa-dots" mask="url(#pa-mask)" transform="translate(170 0)"><rect width="150" height="120" fill="url(#pa-dots)"/></g><path class="pa-back" d="M30 34h244v132H30z"/><path class="pa-leaf" d="M18 22h226a24 24 0 0 1 24 24v108H18z"/><circle class="pa-avatar-bg" cx="68" cy="76" r="26"/><circle class="pa-avatar" cx="68" cy="68" r="10"/><path class="pa-avatar" d="M50 98a18 16 0 0 1 36 0"/><rect class="pa-line" x="112" y="52" width="92" height="8"/><rect class="pa-line is-soft" x="112" y="68" width="62" height="6"/><g class="pa-bars"><rect class="b1" x="40" y="116" width="188" height="9"/><rect class="b2" x="40" y="132" width="132" height="9"/><rect class="b3" x="40" y="148" width="84" height="9"/></g><rect class="pa-stamp-box" x="206" y="118" width="46" height="38" transform="rotate(-8 229 137)"/><path class="pa-check" d="m216 138 8 8 14-16" transform="rotate(-8 229 137)"/></svg></div>';
const profilePreview = '<ol class="profile-preview"><li><b>01</b><span><strong>Ваши сферы</strong>Процент совпадения с каждым направлением</span></li><li><b>02</b><span><strong>Ценности и потребности</strong>Что для вас важно в работе</span></li><li><b>03</b><span><strong>Подборка возможностей</strong>Курсы, олимпиады и стажировки под вас</span></li></ol>';

export function renderProfile({ result, answers = {}, hasDraft = false } = {}) {
  if (!result) return `<section class="screen-profile">${brandHeader()}<div class="profile-empty"><h1>Мой профиль</h1><p>${hasDraft?'Продолжите профтест с того места, где остановились':'Пройдите профтест, чтобы увидеть свой профиль'}</p>${profileArt}${profilePreview}<div class="p-cta profile-cta"><button class="primary p-btn" data-action="continue">${hasDraft?'Продолжить тест':'Пройти тест'}</button></div></div>${renderMainNav('profile')}</section>`;
  const name = (typeof answers.profile?.PRF01==='string'?answers.profile.PRF01.trim():'') || 'Без имени';
  const words = name.split(/\s+/).filter(Boolean);
  const longest = Math.max(0, ...name.split(/[\s-]+/).map(word => word.length));
  const nameSize = longest >= 24 ? 'xxl' : longest >= 19 ? 'xl' : longest >= 13 ? 'l' : 'm';
  const initials = words.slice(0, 2).map(word => word[0].toLocaleUpperCase('ru')).join('');
  const top = (result.topSpheres || []).slice(0, 3);
  const leadId = top[0];
  const percentOf = id => clamp(result.spheres?.find(sphere => sphere.id === id)?.percent);
  const percent = percentOf(leadId);
  const summary = top.map(id => `<div class="profile-stat"><b>${percentOf(id)}<small>%</small></b><span>${escapeHtml(sphereName(id))}</span></div>`).join('');
  const interests = top.map(sphereName).join(' · ');
  return `<section class="screen-profile">${brandHeader()}<div class="profile-identity"><span class="profile-avatar" aria-hidden="true"><span>${escapeHtml(initials)}</span></span><div class="profile-identity-copy"><strong class="profile-name profile-name--${nameSize}">${escapeHtml(words.join(" "))}</strong><span>${escapeHtml(result.label || '')}</span></div></div><section class="profile-direction"><div class="profile-direction-head"><h2 class="p-kicker profile-kicker">Моё направление</h2><button class="profile-retake" data-action="reset-test">Пересдать →</button></div><strong>${escapeHtml(sphereName(leadId))}</strong><div class="profile-score" role="progressbar" aria-label="Результат по ведущей сфере" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${percent}"><div class="profile-score-bar" style="--w:${percent}%"></div><span class="profile-score-value">${percent}<small>%</small></span></div></section><div class="profile-summary" style="--cols:${top.length}">${summary}</div><div class="profile-list"><button class="profile-row" data-route="#result">Открыть результат${arrow}</button><button class="profile-row" data-route="#opportunities?recommended">Открыть подборку${arrow}</button><div class="profile-row profile-row--info"><span>Мне интересно</span><span class="profile-value">${escapeHtml(interests)}</span></div><button class="profile-row" data-route="#games">Игры дня${arrow}</button><button class="profile-row" data-action="reset-test">Пройти тест повторно${arrow}</button></div>${renderMainNav('profile')}</section>`;
}
