import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { opportunityClosed } from '../js/domain/catalog.js';

const read = async name => JSON.parse(await readFile(new URL(`../data/${name}.json`, import.meta.url), 'utf8'));
const base = await read('catalog');
const extra = await read('catalog_extra');
const spheres = new Set(base.flatMap(item => item.spheres));

test('дополнительные возможности: уникальные id, нужные поля, известные сферы и безопасные ссылки', () => {
  const ids = [...base, ...extra].map(item => item.id);
  assert.equal(new Set(ids).size, ids.length);
  for (const item of extra) {
    for (const key of ['id', 'type', 'title', 'direction', 'format', 'audience_text', 'date_text', 'url', 'url_kind']) assert.ok(item[key], `${item.id}: ${key}`);
    assert.ok(['internship', 'course', 'olympiad', 'event'].includes(item.type), item.id);
    assert.ok(['remote', 'offline', 'hybrid'].includes(item.format), item.id);
    assert.ok(item.spheres.length && item.spheres.every(sphere => spheres.has(sphere)), item.id);
    assert.match(item.url, /^https:\/\//, item.id);
    assert.ok(Array.isArray(item.dates) && typeof item.is_rolling === 'boolean', item.id);
  }
});

test('автоудаление: завершившиеся возможности определяются по датам, круглогодичные и без дат остаются', () => {
  const today = '2026-11-18';
  const ended = { dates:[{ kind:'start', date:'2026-10-24', precision:'day' }] };
  const monthEnded = { dates:[{ kind:'start', date:'2026-10', precision:'month' }] };
  const running = { dates:[{ kind:'start', date:'2026-09', precision:'month' }, { kind:'end', date:'2027-02-24', precision:'day' }] };
  const yearLong = { dates:[{ kind:'start', date:'2026', precision:'year' }] };
  const noDates = { dates:[] };
  assert.equal(opportunityClosed(ended, today), true);
  assert.equal(opportunityClosed(monthEnded, today), true);
  assert.equal(opportunityClosed(running, today), false);
  assert.equal(opportunityClosed(yearLong, today), false);
  assert.equal(opportunityClosed(noDates, today), false);
  // у позиций из дополнительного списка срок проверяется той же функцией
  const timed = extra.filter(item => item.dates.length);
  assert.ok(timed.length >= 2 && timed.every(item => opportunityClosed(item, '2030-01-01')));
  assert.ok(timed.every(item => !opportunityClosed(item, '2026-11-18')));
});
