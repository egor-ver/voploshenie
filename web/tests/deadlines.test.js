import test from 'node:test';
import assert from 'node:assert/strict';
import { groupDeadlines } from '../js/domain/deadlines.js';
import { renderDeadlines } from '../js/ui/deadlines.js';

const today = '2026-10-23';
const item = (id, dates = [], date_text = '') => ({ id, title:id, dates, date_text });
const date = (kind, value, precision = 'day') => ({ kind, date:value, precision });

test('исключает прошедший день, сохраняя сегодня и более позднюю дату месяца', () => {
  const groups = groupDeadlines([item('past',[date('apply_deadline','2026-10-01')]),item('today',[date('apply_deadline',today)]),item('future',[date('apply_deadline','2026-10-25')])], today);
  assert.equal(groups.length, 1);
  assert.deepEqual(groups[0].entries.map(row => row.item.id), ['today','future']);
  assert.equal(groups[0].month, '2026-10');
});

test('предпочитает приём заявок, затем ближайшую дату выбранного типа', () => {
  const grouped = groupDeadlines([item('choice',[date('start','2026-11-20'),date('start','2026-11-05'),date('end','2026-10-30'),date('apply_deadline','2026-12-01'),date('apply_deadline','2026-11-08')])], today);
  assert.deepEqual(grouped[0].entries[0].date, date('apply_deadline','2026-11-08'));
  const fallback = groupDeadlines([item('fallback',[date('start','2026-11-20'),date('start','2026-11-05'),date('end','2026-10-30')])], today);
  assert.deepEqual(fallback[0].entries[0].date, date('start','2026-11-05'));
});

test('группирует месяц и год по порядку и оставляет записи без дат в конце', () => {
  const groups = groupDeadlines([item('dec',[date('apply_deadline','2026-12-31')]),item('jan',[date('start','2027-01','month')]),item('year',[date('end','2028','year')]),item('none',[],'Дата уточняется')], today);
  assert.deepEqual(groups.map(group => group.month || group.year || null), ['2026-12','2027-01',2028,null]);
  assert.deepEqual(groups[2].entries.map(row => row.item.id), ['year']);
  assert.equal(groups[3].entries[0].item.date_text, 'Дата уточняется');
});

test('сохраняет текущие месяц и год, исключая завершившийся год', () => {
  const groups = groupDeadlines([item('month',[date('apply_deadline','2026-10','month')]),item('year',[date('start','2026','year')]),item('past',[date('start','2025','year')])], today);
  assert.deepEqual(groups.map(group => group.month || group.year), ['2026-10',2026]);
});

test('невалидные даты остаются без даты, дедлайн-экран берёт только избранное', () => {
  const groups = groupDeadlines([item('bad',[date('apply_deadline','2026-02-30')])], today);
  assert.equal(groups[0].entries[0].date, null);
  const html = renderDeadlines({ items:[item('one',[date('apply_deadline','2026-10-25')]),item('two',[date('apply_deadline','2026-10-26')])], favorites:['one'], today });
  assert.ok(html.includes('one'));
  assert.ok(!html.includes('>two<'));
  assert.equal((html.match(/nav-icon-/g) || []).length, 5);
  assert.match(html, /Приём заявок · 25 октября 2026/);
  assert.match(html, /<h1 class="p-title">Дедлайны<\/h1>/);
  assert.doesNotMatch(html, /Посмотреть стажировку/);
  const internship = renderDeadlines({ items:[{ ...item('intern',[date('apply_deadline','2026-10-25')]),type:'internship' }], favorites:['intern'], today });
  assert.match(internship, /data-route="#detail\/intern">Посмотреть стажировку/);
  const empty = renderDeadlines({ items:[], favorites:[], today });
  assert.doesNotMatch(empty, /Посмотреть стажировку/);
});

test('равные даты дня и месяца сохраняют дату, идущую первой во входном списке', () => {
  const day = date('apply_deadline','2026-11-01','day');
  const month = date('apply_deadline','2026-11','month');
  assert.equal(groupDeadlines([item('day-first',[day,month])],today)[0].entries[0].date, day);
  assert.equal(groupDeadlines([item('month-first',[month,day])],today)[0].entries[0].date, month);
});
