import test from 'node:test';
import assert from 'node:assert/strict';
import { renderDetail } from '../js/ui/detail.js';

const sample = {
  id:'detail-1', type:'course', title:'Основы <анализа>', spheres:['it'], format:'remote',
  audience_text:'Студенты', organizer:'Организатор & партнёры', direction:'Данные <и> анализ',
  dates:[{kind:'start', precision:'day', date:'2026-11-01'}], url:'https://example.org/apply', url_kind:'program'
};

test('detail renders Android content order, localized date, and safe external action', () => {
  const html = renderDetail({item:sample, matchPercent:80, today:'2026-10-21'});
  assert.ok(html.indexOf('Основы &lt;анализа&gt;') < html.indexOf('detail-facts'));
  assert.ok(html.indexOf('detail-facts') < html.indexOf('Что вы будете делать'));
  assert.ok(html.indexOf('detail-direction') < html.indexOf('Совпадение с вашим профилем: 80%'));
  assert.match(html, /Начало · 1 ноября 2026/);
  assert.match(html, /href="https:\/\/example\.org\/apply" target="_blank" rel="noopener noreferrer"/);
  assert.match(html, /Подать заявку/);
  assert.match(html, /Организатор &amp; партнёры/);
});

test('detail escapes identifiers, rejects non-http links, and reflects saved state', () => {
  const html = renderDetail({item:{...sample, id:'x" onmouseover="x', url:'javascript:alert(1)'}, favorites:['x" onmouseover="x'], today:'2026-10-21'});
  assert.match(html, /data-save="x&quot; onmouseover=&quot;x"/);
  assert.match(html, /♥ В избранном/);
  assert.doesNotMatch(html, /javascript:/);
  assert.doesNotMatch(html, /class="detail-primary"/);
});

test('missing item has a readable empty state', () => {
  assert.match(renderDetail(), /Возможность не найдена/);
});
