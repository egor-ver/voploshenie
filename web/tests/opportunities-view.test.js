import test from 'node:test';
import assert from 'node:assert/strict';
import { renderOpportunities } from '../js/ui/opportunities.js';
import { renderSort } from '../js/ui/sort.js';

test('экран возможностей показывает поиск, выбор типа, число результатов и билет даты', () => {
  const html = renderOpportunities({
    items: [{ id: 'one', type: 'internship', title: 'Стажировка <UX>', spheres: [], format: 'remote', dates: [{ kind: 'apply_deadline', date: '2026-10-25', precision: 'day' }] }],
    query: 'стаж', type: 'internship'
  });
  assert.match(html, /name="catalog-query" value="стаж"/);
  assert.match(html, /data-type="internship" aria-pressed="true"/);
  assert.match(html, /Найдено: 1/);
  assert.match(html, /Стажировка &lt;UX&gt;/);
  assert.match(html, /25<small>ОКТ<\/small>/);
});

test('экран сортировки блокирует сортировку по интересам без результата теста', () => {
  const html = renderSort({ choice: 'date', hasResult: false });
  assert.match(html, /value="date" checked/);
  assert.match(html, /value="interest" disabled/);
  assert.match(html, /data-action="apply-sort"/);
});
