import test from 'node:test';
import assert from 'node:assert/strict';
import { renderDeadlines } from '../js/ui/deadlines.js';

test('показывает месяц над избранным дедлайном и экранирует данные', () => {
  const html = renderDeadlines({
    items: [{ id:'<item>', title:'<img src=x>', type:'course', dates:[{ kind:'start', date:'2026-11-01', precision:'day' }] }],
    favorites:['<item>'], today:'2026-10-23'
  });
  assert.match(html, /<h1 class="p-title">Дедлайны<\/h1>/);
  assert.match(html, /<h2 class="deadline-month">Ноябрь <span class="deadline-year">2026<\/span><\/h2>/);
  assert.match(html, /data-item="&lt;item&gt;"/);
  assert.match(html, /&lt;img src=x&gt;/);
  assert.match(html, /Начало · 1 ноября 2026/);
  assert.doesNotMatch(html, /<img src=x>/);
  assert.doesNotMatch(html, /ближайшие дедлайны/);
});
