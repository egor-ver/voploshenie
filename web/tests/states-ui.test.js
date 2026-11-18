import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { renderLoadError, renderLoading, stateScreen } from '../js/ui/states.js';
import { renderSort } from '../js/ui/sort.js';
import { renderOpportunities, renderSimilar } from '../js/ui/opportunities.js';
import { renderDetail } from '../js/ui/detail.js';
import { renderSaved } from '../js/ui/saved.js';
import { opportunityClosed, similar } from '../js/domain/catalog.js';

const item = (id, type, spheres, dates, extra = {}) => ({ id, type, title:`Название ${id}`, spheres, dates, date_text:'до 25 октября', ...extra });
const closed = item('old', 'internship', ['it'], [{ kind:'apply_deadline', date:'2026-10-25', precision:'day' }]);
const open = item('new', 'internship', ['it', 'finance'], [{ kind:'apply_deadline', date:'2026-10-26', precision:'day' }], { url:'https://example.org', url_kind:'program' });
const course = item('course', 'course', ['it'], []);

test('состояние экрана экранирует заголовок и текст и показывает меню', () => {
  const html = stateScreen({ title:'<b>Заголовок</b>', body:'a & "b"', nav:'<nav></nav>' });
  assert.match(html, /<h1>&lt;b&gt;Заголовок&lt;\/b&gt;<\/h1>/);
  assert.match(html, /a &amp; &quot;b&quot;/);
  assert.match(html, /class="state-grid" aria-hidden="true"/);
  assert.match(html, /<nav><\/nav>/);
});

test('загрузка показывает три заглушки карточек и статус для скринридера', () => {
  const html = renderLoading();
  assert.match(html, /role="status" aria-busy="true"/);
  assert.match(html, /Подбираем возможности/);
  assert.match(html, /Это займёт немного времени/);
  assert.equal(html.match(/class="skeleton-card"/g).length, 2);
  assert.doesNotMatch(html, /bottom-nav/);
});

test('ошибка загрузки предлагает повтор и обещает сохранить избранное', () => {
  const html = renderLoadError();
  assert.match(html, /Не получилось загрузить/);
  assert.match(html, /Сохранённое никуда не пропадёт/);
  assert.match(html, /<button class="primary" data-action="retry">Повторить<span/);
});

test('сортировка: два варианта, выбранный отмечен, интересы недоступны без результата', () => {
  const withResult = renderSort({ choice:'interest', hasResult:true });
  assert.match(withResult, /<h1>Сортировка<\/h1>/);
  assert.match(withResult, /Как расположить предложения/);
  assert.match(withResult, /value="interest" checked>/);
  assert.doesNotMatch(withResult, /disabled|Пройти профтест/);
  assert.match(withResult, /data-action="apply-sort">Применить/);
  const without = renderSort({ choice:'date' });
  assert.match(without, /value="date" checked>/);
  assert.match(without, /value="interest" disabled>/);
  assert.match(without, /data-action="continue">Пройти профтест/);
  assert.equal(without.match(/type="radio" name="sort"/g).length, 2);
});

test('каталог без совпадений показывает пустое состояние со сбросом, ссылка сортировки ведёт на лист', () => {
  const empty = renderOpportunities({ items:[open], query:'нет такого' });
  assert.match(empty, /Пока ничего не найдено/);
  assert.match(empty, /data-action="reset-filters">Сбросить/);
  assert.match(empty, /aria-current="page"/);
  assert.match(empty, /Найдено: 0/);
  assert.match(empty, /name="catalog-query" value="нет такого"/);
  assert.match(empty, /data-type="internship"/);
  const withResult={spheres:[{id:'business',percent:50}],topSpheres:['business']};
  assert.match(renderOpportunities({ items:[open], result:withResult, sort:'interest' }), /data-route="#sort">По вашим интересам ↓/);
  assert.doesNotMatch(renderOpportunities({ items:[open] }), /catalog-sort/);
  assert.match(renderOpportunities({ items:[open], result:withResult, sort:'date' }), /data-route="#sort">Ближайший дедлайн ↓/);
});

test('пустое избранное ведёт к каталогу', () => {
  const html = renderSaved({ items:[open], favorites:[] });
  const heading = html.match(/<h1[^>]*>([\s\S]*?)<\/h1>/g);
  assert.equal(heading.length, 1);
  assert.match(heading[0], /Избранное<span aria-hidden="true">\.<\/span>/);
  assert.equal((html.match(/Здесь пока пусто\./g) || []).length, 1);
  assert.match(html, /Сохраняйте возможности ♡, чтобы вернуться к ним позже и не пропустить дедлайн\./);
  assert.match(html, /data-route="#opportunities">Найти возможности/);
  assert.match(html, /<a href="#saved" aria-current="page"/);
  assert.match(html, /class="saved-art" aria-hidden="true"/);
  assert.doesNotMatch(html, /saved-leaf|saved-ribbon/);
});

test('закрытый приём: «Регистрация уже закрыта» с поиском похожих и сохранением', () => {
  const html = renderDetail({ item:{ ...closed, date_text:'до <25> октября' }, favorites:[], today:'2026-10-26' });
  assert.match(html, /Регистрация<br>уже закрыта/);
  assert.match(html, /Сроки закончились 25 октября 2026/);
  assert.match(html, /data-route="#similar\/old">Найти похожие/);
  assert.match(html, /data-save="old">♡ Сохранить на следующий набор/);
  assert.match(renderDetail({ item:closed, favorites:['old'], today:'2026-10-26' }), /♥ Сохранено на следующий набор/);
  assert.doesNotMatch(renderDetail({ item:closed, today:'2026-10-25' }), /Регистрация<br>уже закрыта/);
});

test('закрыто только если все даты прошли и разобраны', () => {
  const ended = month => item('m', 'event', [], [{ kind:'end', date:month, precision:'month' }]);
  assert.equal(opportunityClosed(closed, '2026-10-26'), true);
  assert.equal(opportunityClosed(closed, '2026-10-25'), false);
  assert.equal(opportunityClosed(course, '2030-01-01'), false);
  assert.equal(opportunityClosed(ended('2026-09'), '2026-10-01'), true);
  assert.equal(opportunityClosed(ended('2026-09'), '2026-09-30'), false);
  assert.equal(opportunityClosed(item('u', 'event', [], [{ kind:'end', date:'скоро', precision:'text' }]), '2030-01-01'), false);
});

test('похожие: тот же тип и пересечение сфер, без самой возможности', () => {
  const same = item('same', 'internship', ['finance'], []);
  const other = item('other', 'internship', ['biology'], []);
  assert.deepEqual(similar([open, same, other, course, closed], open).map(row => row.id), ['same', 'old']);
  const html = renderSimilar({ item:open, items:[open, same, other], favorites:[] });
  assert.match(html, /<h1>Найти похожие<\/h1>/);
  assert.match(html, /Название same/);
  assert.doesNotMatch(html, /Название other/);
  assert.match(renderSimilar({ item:other, items:[open, other] }), /Похожих возможностей нет/);
});

test('цвета только в tokens.css, тёмная тема переопределяет те же токены', async () => {
  const css = file => readFile(new URL(`../css/${file}`, import.meta.url), 'utf8');
  for (const file of ['app.css', 'catalog.css', 'result.css', 'w07.css', 'states.css']) assert.doesNotMatch(await css(file), /#[0-9A-Fa-f]{3,8}\b|rgba?\(/, file);
  const dark = (await css('tokens.css')).match(/prefers-color-scheme:dark\)\{:root\{([^}]*)\}/)[1];
  for (const name of ['background', 'surface', 'primary', 'secondary', 'on-background', 'on-surface-variant', 'outline', 'secondary-text']) assert.match(dark, new RegExp(`--${name}:#`));
});
