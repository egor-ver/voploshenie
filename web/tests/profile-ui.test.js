import test from 'node:test';
import assert from 'node:assert/strict';
import { renderProfile } from '../js/ui/profile.js';
import { readFile } from 'node:fs/promises';

const config = JSON.parse(await readFile(new URL('../data/test_config.json', import.meta.url), 'utf8'));

test('пустой профиль предлагает пройти тест и содержит навигацию', () => {
  const html = renderProfile();
  assert.match(html, /<h1>Мой профиль<\/h1>/);
  assert.match(html, /Пройдите профтест, чтобы увидеть свой профиль/);
  assert.match(html, /data-action="continue"/);
  assert.match(html, /class="profile-empty"/);
  assert.equal((html.match(/nav-icon-/g) || []).length, 5);
});

test('заполненный профиль показывает результат и экранирует имя и ответы', () => {
  const html = renderProfile({ result:{ label:'Исследователь идей', topSpheres:['it','business'], spheres:[{ id:'it', percent:84 }] }, answers:{ profile:{ PRF01:'<img src=x>', PRF03:'Специальность' } }, config });
  assert.match(html, /Исследователь идей/);
  assert.match(html, /Информационные технологии · Бизнес и управление/);
  assert.match(html, /class="profile-avatar"/);
  assert.match(html, /class="profile-identity"/);
  assert.doesNotMatch(html, /Ответы профиля|class="profile-card"/);
  assert.match(html, /&lt;img src=x&gt;/);
  assert.match(html, /data-action="reset-test"/);
  assert.match(html, /data-route="#result"/);
  assert.match(html, /Моё направление/);
  assert.match(html, /aria-valuenow="84"/);
  assert.match(html, /class="profile-score-value">84<small>%<\/small>/);
  assert.match(html, /class="profile-score-bar" style="--w:84%"/);
  assert.match(html, /class="profile-summary"/);
  assert.match(html, /Открыть подборку/);
  assert.doesNotMatch(html, /<img src=x>/);
  const named = renderProfile({ result:{ label:'Практик', topSpheres:['it'], spheres:[{ id:'it', percent:0 }] }, answers:{ profile:{ PRF01:'Анна Иванова' } }, config });
  assert.match(named, /class="profile-avatar"[^>]*><span>АИ<\/span>/);
});
