import { primaryAction } from './states.js';

const options = [['date', 'Ближайший дедлайн', 'Сначала ближайшие по срокам'], ['interest', 'По вашим интересам', 'Сначала то, что лучше подходит по результату теста']];

export function renderSort({ choice = 'date', hasResult = false } = {}) {
  const rows = options.map(([value, title, rawHint]) => { const hint = value === 'interest' && !hasResult ? 'Станет доступно после профтеста' : rawHint; return `<label class="sort-option"><span><strong>${title}</strong><small>${hint}</small></span><input type="radio" name="sort" value="${value}"${choice === value ? ' checked' : ''}${value === 'interest' && !hasResult ? ' disabled' : ''}></label>`; }).join('');
  return `<section class="sort-screen"><div class="sort-backdrop" aria-hidden="true"><div class="sort-ghost">Возможности.</div><div class="sort-dim"></div></div><div class="sort-sheet"><div class="sort-head"><h1>Сортировка</h1><button class="sort-close" data-route="#opportunities" aria-label="Закрыть">✕</button></div><p class="screen-subtitle">Как расположить предложения</p><fieldset class="sort-options"><legend class="visually-hidden">Сортировка</legend>${rows}</fieldset><p class="state-body">Сортировка меняет порядок, но сохраняет выбранные фильтры.</p>${hasResult ? '' : '<button class="link-button" data-action="continue">Пройти профтест</button>'}${primaryAction('Применить', 'data-action="apply-sort"')}</div></section>`;
}
