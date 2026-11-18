import { escapeHtml } from '../escape.js';
import { brandHeader } from './brand.js';
import { renderMainNav } from './nav.js';
import { pathProfileData } from '../domain/path.js';
import { sphereName } from '../domain/sphere-name.js';

const steps = [['Профтест','Пройдите профтест'],['Навыки','Попробуйте короткий проект'],['Олимпиады и курсы','Выберите возможность'],['Стажировка','Получите первый опыт'],['Работа','Двигайтесь к своей цели']];
const gamesBanner = '<button class="path-games" data-route="#games"><span class="pg-art" aria-hidden="true"><i class="ok"></i><i class="mid"></i><i class="off"></i><i class="mid"></i><i class="ok"></i><i class="ok"></i></span><span class="pg-copy"><strong>Игры дня</strong><small>Слово дня и Миф или правда</small></span><span class="pg-go" aria-hidden="true"><svg viewBox="0 0 24 24"><path d="M4 12h16m-7-7 7 7-7 7"/></svg></span></button>';
const arrow = '<svg aria-hidden="true" viewBox="0 0 24 24"><path d="M3 12h18m-8-8 8 8-8 8"/></svg>';

function renderStart(hasDraft) {
  const label = hasDraft ? 'Продолжить тест' : 'Начать профтест';
  return `<section class="path-home" data-start>${brandHeader()}<figure class="path-photo"><img src="img/start-photo.jpg" alt=""><i class="c1"></i><i class="c2"></i><i class="c3"></i><i class="c4"></i><figcaption class="path-tag">Идеи → дело</figcaption></figure><p class="path-vertical" aria-hidden="true">Профтест · Путь · Возможности</p><h1 class="path-hero">Идеи обретают форму</h1><p class="path-subtitle">Пройдите профтест, соберите свой путь и находите курсы, олимпиады и стажировки под себя.</p><div class="path-actions p-cta"><button class="primary" data-action="continue">${label}${arrow}</button></div><button class="path-link" data-route="#opportunities">Сначала посмотреть возможности</button>${gamesBanner}${renderMainNav('path')}</section>`;
}

export function renderPath({ hasDraft = false, hasResult = false, result, config = {} } = {}) {
  if (!hasResult) return renderStart(hasDraft);
  const id = result?.topSpheres?.[0];
  const data = pathProfileData(result, null, config);
  const timeline = steps.map(([title, description], index) => {
    const complete = index === 0;
    const current = index === 1;
    const detail = index === 0 ? sphereName(id) : index === 1 ? data.experiment || description : description;
    const rail = '<span class="path-rail" aria-hidden="true"><span class="path-node"></span></span>';
    const body = index < 2
      ? `<div class="path-step-heading"><span class="path-step-number">0${index + 1}</span>${current ? '<span class="path-now">СЕЙЧАС</span>' : ''}</div><div class="path-step-title">${escapeHtml(title)}${complete ? '<span class="path-passed">✓ пройден</span>' : ''}</div><p>${escapeHtml(detail)}</p>`
      : `<span class="path-step-number">0${index + 1}</span><div><div class="path-step-title">${escapeHtml(title)}</div><p>${escapeHtml(detail)}</p></div>`;
    return `<article class="path-step${current ? ' is-current' : ''}${complete ? ' is-complete' : ''}" data-status="${complete ? 'complete' : current ? 'current' : 'pending'}">${rail}<div class="path-step-content">${body}</div></article>`;
  }).join('');
  const lines = '<svg class="path-lines" aria-hidden="true" viewBox="0 0 100 448" width="100" height="448"><path class="done" d="M36 24V70H64V112"/><path class="todo" d="M64 142V190H36V396"/></svg>';
  const links = `<button class="path-secondary" data-route="#result">Открыть результат</button>${hasDraft ? '<button class="path-secondary" data-action="continue">Продолжить тест</button>' : ''}${gamesBanner}`;
  return `<section class="path-home">${brandHeader()}<span class="path-step-count">Шаг 2 из 5</span><h1>Ваш следующий шаг</h1><p class="path-subtitle">Небольшое действие сегодня</p><div class="path-timeline" tabindex="0" role="region" aria-label="Этапы пути"><span class="path-dotfield" aria-hidden="true"></span>${lines}<div class="path-steps">${timeline}</div></div><div class="path-actions p-cta"><button class="primary" data-route="#opportunities?recommended">Открыть подборку${arrow}</button></div><div class="path-links">${links}</div>${renderMainNav('path')}</section>`;
}
