import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { renderPath } from '../js/ui/path.js';
import { pathProfileData } from '../js/domain/path.js';

const config = JSON.parse(await readFile(new URL('../data/test_config.json', import.meta.url), 'utf8'));

test('pathProfileData переносит имя, RIASEC, три сферы, первую сферу и проценты', () => {
  const data = pathProfileData({ riasecCode:'IAS', topSpheres:['it','business','finance','biology'], spheres:[{id:'it',percent:84}] }, 'Алекс', config);
  assert.deepEqual(data, { name:'Алекс', labelCode:'I', topSpheres:['it','business','finance'], topSphere:'it', experiment:config.texts.spheres.table[2]['Вход и первые пробы'], percent:84 });
});

test('путь сохраняет порядок этапов и доступную с клавиатуры прокрутку', ()=>{
  const html = renderPath({ hasResult:true, result:{ topSpheres:['it'] }, config });
  assert.match(html, /class="path-timeline" tabindex="0" role="region" aria-label="Этапы пути"/);
  assert.equal((html.match(/class="path-rail" aria-hidden="true"/g) || []).length, 5);
  const titles = ['Профтест', 'Навыки', 'Олимпиады и курсы', 'Стажировка', 'Работа'];
  const positions = titles.map(title => html.indexOf(`class="path-step-title">${title}`));
  assert.ok(positions.every((position, index) => position >= 0 && (!index || position > positions[index - 1])));
  assert.equal((html.match(/data-status=/g) || []).length, 5);
  assert.equal((html.match(/class="path-step-number"/g) || []).length, 5);
  assert.match(html, /Ваш следующий шаг/);
  assert.equal((html.match(/aria-current="page"/g) || []).length, 1);
  for (const label of ['Путь','Возможности','Избранное','Дедлайны','Профиль']) assert.ok(html.includes(label));
});

test('без результата показывается стартовый экран с фото и одним главным действием', () => {
  const html = renderPath({ hasDraft:false, hasResult:false, config });
  assert.match(html, /class="path-home" data-start/);
  assert.match(html, /start-photo.jpg/);
  assert.match(html, /Идеи обретают форму/);
  assert.match(html, /Пройдите профтест/);
  assert.match(html, /class="primary" data-action="continue">Начать профтест/);
  assert.match(html, /data-route="#opportunities">Сначала посмотреть возможности/);
  assert.doesNotMatch(html, /path-timeline|data-status=|data-route="#result"|Ты |Твой/);
  assert.equal((html.match(/aria-current="page"/g) || []).length, 1);
});

test('завершённый тест показывает сферу, первые пробы и ссылку на результат', () => {
  const html = renderPath({ hasResult:true, result:{ topSpheres:['it'] }, config });
  assert.match(html, /data-status="complete"/);
  assert.match(html, /Информационные технологии/);
  assert.match(html, /хакатон/);
  assert.match(html, /data-route="#result"/);
  assert.match(html, /data-status="current"/);
  assert.match(html, /class="path-passed">✓ пройден/);
  assert.match(html, /class="path-now">СЕЙЧАС/);
});

test('действия соответствуют наличию результата и черновика', () => {
  for (const hasResult of [false, true]) for (const hasDraft of [false, true]) {
    const html = renderPath({ hasResult, hasDraft, result:{ topSpheres:['it'] }, config });
    assert.equal((html.match(/data-action="continue"/g) || []).length, hasDraft || !hasResult ? 1 : 0);
    assert.equal((html.match(/<button /g) || []).length, hasResult ? hasDraft ? 4 : 3 : 3);
    assert.match(html, /data-route="#games"/);
    if (hasResult) {
      assert.ok(html.indexOf('Открыть подборку') < html.indexOf('Открыть результат'));
      assert.match(html, /class="primary" data-route="#opportunities\?recommended">Открыть подборку/);
      if (hasDraft) assert.ok(html.indexOf('Открыть подборку') < html.indexOf('Открыть результат') && html.indexOf('Открыть результат') < html.indexOf('Продолжить тест'));
    } else {
      assert.match(html, new RegExp(`class="primary" data-action="continue">${hasDraft ? 'Продолжить тест' : 'Начать профтест'}`));
    }
  }
});

test('путь показывает ресурсное название бизнес-сферы', () => {
  const html = renderPath({ hasResult:true, result:{ topSpheres:['business'] }, config });
  assert.match(html, /Бизнес и управление/);
});

test('сведения о пробах экранируются', () => {
  const unsafe = structuredClone(config);
  unsafe.texts.spheres.table[2]['Вход и первые пробы'] = '<script>alert(1)</script>';
  const html = renderPath({ hasResult:true, result:{ topSpheres:['it'] }, config:unsafe });
  assert.ok(html.includes('&lt;script&gt;alert(1)&lt;/script&gt;'));
  assert.ok(!html.includes('<script>'));
});
