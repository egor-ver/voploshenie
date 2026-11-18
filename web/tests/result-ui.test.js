import test from 'node:test';
import assert from 'node:assert/strict';
import { renderResult } from '../js/ui/result.js';

test('result renders sphere names, top card details, and safely escaped answers', () => {
  const config = { spheres:[{id:'it',details:{'Сфера':'ИТ'}},{id:'finance',details:{'Сфера':'Финансы'}}], questions:{constraints:[{id:'NAD07',fields:{'Вопрос':'Другие ограничения'},options:[]},{id:'NAD01',fields:{'Вопрос':'Переезд'},options:[{id:'a',text:'Готов'}]}]} };
  const result = { spheres:[{id:'it',percent:80,level:'Высокий',flags:[{text:'Флаг'}],professions:[{name:'<script>'}]},{id:'finance',percent:65,level:'Средний',flags:[],professions:[]}], topSpheres:['it','finance'], equivalentPairs:[['it','finance']], riasecCode:'ICE',label:'Исследовательский',hints:[],domains:{logic:3,numbers:3,text:1} };
  const html = renderResult({result,config,answers:{nad:{NAD07:'<текст>',NAD01:'a'},profile:{PRF03:'Специальность',PRF05:'8'}}});
  assert.match(html, /<strong>Информационные технологии<\/strong>/);
  assert.match(html, /<strong>Финансы<\/strong>/);
  assert.match(html, /80 %[\s\S]*Высокий[\s\S]*Равноценные направления[\s\S]*&lt;script&gt;|&lt;script&gt;[\s\S]*Равноценные направления/);
  assert.match(html, /Другие ограничения: &lt;текст&gt;/);
  assert.match(html, /Переезд: Готов/);
  assert.match(html, /Специальность «Специальность» · оценка 8\/10 · топ-сферы: Информационные технологии, Финансы/);
  assert.match(html, /Сильные зоны: логика, числа\./);
  assert.match(html, /Ваш код интересов — ICE/);
  assert.match(html, /class="actions p-cta"><button class="primary p-btn"/);
  assert.equal((html.match(/class="result-chart-item"/g) || []).length, 2);
});

test('result displays consistency warnings and safely renders hint text', () => {
  const config = {spheres:[{id:'it',details:{'Сфера':'ИТ'}}],questions:{constraints:[]}};
  const result = {spheres:[{id:'it',percent:0,flags:[],professions:[]}],topSpheres:['it'],riasecCode:'R',label:'Реалистичный',hints:[{id:'H04',text:'<подсказка>'}],consistencyWarning:'Ответы расходятся'};
  const html = renderResult({result,config});
  assert.match(html, /Ответы расходятся/);
  assert.match(html, /&lt;подсказка&gt;/);
  assert.doesNotMatch(html, /<подсказка>/);
});

test('result shows both consistency warning and profile consistency line', () => {
  const config = {spheres:[{id:'it',details:{'Сфера':'ИТ'}}],questions:{constraints:[]}};
  const result = {spheres:[{id:'it',percent:0,flags:[],professions:[]}],topSpheres:['it'],riasecCode:'R',label:'Реалистичный',hints:[],consistencyWarning:'Ответы расходятся'};
  const html = renderResult({result,config,answers:{profile:{PRF03:'Специальность',PRF05:'8'}}});
  assert.match(html, /Ответы расходятся/);
  assert.match(html, /Специальность «Специальность» · оценка 8\/10 · топ-сферы: Информационные технологии/);
});

test('result renders Android sections, answer blocks, and escapes free text', () => {
  const config = {spheres:[{id:'it',details:{'Сфера':'ИТ'}}],questions:{values:[{id:'VAL01',fields:{'Название':'Самостоятельность'}}],needs:[{id:'NEED01',fields:{'Условие':'Гибкий график'}}],open:[{id:'OPEN04',fields:{'Текст':'Что вам интересно?'}}],happiness:[],constraints:[]},texts:{experiments:{paragraphs:['Пробуйте и оценивайте.']},disclaimer:{paragraphs:['Короткий текст.','Полный текст в «кавычках».']}}};
  const result = {spheres:[{id:'it',percent:80,level:'Высокий',flags:[],professions:[]}],topSpheres:['it'],equivalentPairs:[],riasec:{I:4},riasecLevels:{I:'Выраженный'},riasecCode:'I',label:'Исследователь',domains:{logic:4},domainLevels:{logic:'Сильная зона'},hints:[],anchor:'Компетентность'};
  const html = renderResult({result,config,answers:{valuesTop3:['VAL01'],needs:{top3:['NEED01']},open:{OPEN04:'<script>alert(1)</script>'}}});
  for (const text of ['Профиль интересов','Сильные стороны','Самостоятельность','Компетентность','Гибкий график','Что вам интересно?','&lt;script&gt;alert(1)&lt;/script&gt;','Пробуйте и оценивайте.']) assert.ok(html.includes(text), text);
  assert.equal((html.match(/class="result-chart-item"/g)||[]).length,1);
  assert.doesNotMatch(html, /<script>alert/);
});

test('free answers render consecutively without commas and escape each answer', () => {
  const config = {spheres:[{id:'it',details:{'Сфера':'ИТ'}}],questions:{values:[],needs:[],open:[{id:'OPEN01',fields:{'Текст':'Первый вопрос'}},{id:'OPEN02',fields:{'Текст':'Второй вопрос'}}],happiness:[],constraints:[]},texts:{experiments:{paragraphs:['']},disclaimer:{paragraphs:['','']}}};
  const result = {spheres:[{id:'it',percent:0,flags:[],professions:[]}],topSpheres:['it'],riasec:{},riasecLevels:{},riasecCode:'',label:'',domains:{},domainLevels:{},hints:[]};
  const html = renderResult({result,config,answers:{open:{OPEN01:'Первый ответ',OPEN02:'<опасный>'}}});
  assert.match(html, /Первый вопрос<\/h3><p class="result-answer">Первый ответ<\/p><h3>Второй вопрос<\/h3><p class="result-answer">&lt;опасный&gt;<\/p>/);
  assert.doesNotMatch(html, /Первый ответ,\s*<h3>/);
  assert.doesNotMatch(html, /<опасный>/);
});

test('free answer section is omitted when answers are empty or whitespace', () => {
  const config = {spheres:[{id:'it',details:{'Сфера':'ИТ'}}],questions:{values:[],needs:[],open:[],happiness:[],constraints:[]},texts:{experiments:{paragraphs:['']},disclaimer:{paragraphs:['','']}}};
  const result = {spheres:[{id:'it',percent:0,flags:[],professions:[]}],topSpheres:['it'],riasec:{},riasecLevels:{},riasecCode:'',label:'',domains:{},domainLevels:{},hints:[]};
  for (const open of [{}, {OPEN01:''}, {OPEN01:'   ',OPEN02:'\n\t'}]) {
    const html = renderResult({result,config,answers:{open}});
    assert.doesNotMatch(html, /Ваши слова/);
  }
});

test('result shows six full RIASEC profiles, identity, flower, and dotted score scales', () => {
  const config = {spheres:[{id:'it',details:{'Сфера':'ИТ'}}],questions:{values:[],needs:[],open:[],happiness:[],constraints:[]},texts:{riasec:{table:[{Тип:'I Исследовательский','Описание (1–2 предложения)':'Изучаете закономерности.'}]},spheres:{table:[]},disclaimer:{paragraphs:['Короткий дисклеймер','Полный дисклеймер']},experiments:{paragraphs:['Пробуйте.']}}};
  const result = {spheres:[{id:'it',percent:80,level:'Высокий',flags:[],professions:[]}],topSpheres:['it'],equivalentPairs:[],riasec:{I:4},riasecLevels:{I:'Выраженный'},riasecCode:'I',label:'Исследователь',domains:{},domainLevels:{},hints:[],computedAt:Date.UTC(2026,0,2)};
  const html = renderResult({result,config,answers:{profile:{PRF01:'Анна'} }});
  for (const text of ['Анна','2 января 2026 г.','Короткий дисклеймер','Исследовательский','Изучаете закономерности.','result-flower','result-meter']) assert.ok(html.includes(text),text);
});

test('RIASEC bars share a compact row and descriptions render below it', async () => {
  const config={spheres:[{id:'it',details:{'Сфера':'ИТ'}}],questions:{values:[],needs:[],open:[],happiness:[],constraints:[]},texts:{riasec:{table:[{'Тип':'I Исследовательский','Описание (1–2 предложения)':'Изучает закономерности и проверяет гипотезы.'}]},spheres:{table:[]},disclaimer:{paragraphs:['Кратко','Подробно']},experiments:{paragraphs:['Попробуйте.']}}};
  const result={spheres:[{id:'it',percent:80,level:'Высокий',flags:[],professions:[]}],topSpheres:['it'],equivalentPairs:[],riasec:{I:4},riasecLevels:{I:'Выраженный'},riasecCode:'I',label:'Исследовательский',domains:{},domainLevels:{},hints:[],anchor:''};
  const html=renderResult({result,config});
  assert.match(html,/class="riasec-bar"[^>]*[\s\S]*<span style="height:80%;width:var\(--full\)"/);
  assert.ok(html.includes('Изучает закономерности и проверяет гипотезы.'));
  assert.match(html,/class="riasec-chart"><div class="riasec-type"><strong>I<\/strong>/);
  assert.match(html,/class="riasec-descriptions"><p><strong>Исследовательский — выраженный<\/strong>/);
  const { readFile } = await import('node:fs/promises');
  const css=await readFile(new URL('../css/print/result.css',import.meta.url),'utf8');
  assert.match(css,/\.riasec-bar span\{[^}]*max-height:100%/);
  assert.match(css,/\.riasec-descriptions\{display:grid;gap:12px/);
});

test('PRF06 moves consistency before the top and makes internship CTA filtered', () => {
  const config = {spheres:[{id:'it',details:{'Сфера':'ИТ'}}],questions:{values:[],needs:[],open:[],happiness:[],constraints:[]},texts:{riasec:{table:[]},spheres:{table:[]},disclaimer:{paragraphs:['Короткий','Полный']},experiments:{paragraphs:['Пробуйте.']}}};
  const result = {spheres:[{id:'it',percent:80,level:'Высокий',flags:[],professions:[]}],topSpheres:['it'],equivalentPairs:[],riasec:{},riasecLevels:{},riasecCode:'',label:'',domains:{},domainLevels:{},hints:[],consistencyWarning:'Предупреждение'};
  const internship = renderResult({result,config,answers:{profile:{PRF06:'ищу стажировку или практику'}}});
  assert.match(internship,/data-action="internships"/);
  assert.doesNotMatch(internship,/data-action="catalog"/);
  const change = renderResult({result,config,answers:{profile:{PRF06:'хочу сменить направление'}}});
  assert.ok(change.indexOf('Согласованность') < change.indexOf('Топ-3 направления'));
});

test('sphere flags appear in matching cards and entry blocks; entry content is not duplicated', () => {
  const config = {spheres:[{id:'biology',details:{'Сфера':'Биология'}},{id:'business',details:{'Сфера':'Бизнес'}}],questions:{values:[],needs:[],open:[],happiness:[],constraints:[]},texts:{riasec:{table:[]},spheres:{table:[{'Сфера':'Биология','Вход и первые пробы':'Попробуйте наблюдения.','На что обратить внимание':'Учитывайте условия лаборатории.'},{'Сфера':'Бизнес','Вход и первые пробы':'Поговорите с предпринимателем.','На что обратить внимание':'Учитывайте рынок.'}]},disclaimer:{paragraphs:['Короткий','Полный']},experiments:{paragraphs:['Пробуйте.']}}};
  const result = {spheres:[{id:'biology',percent:72,level:'Высокий',flags:[{text:'<Флаг биологии>'}],professions:[]},{id:'business',percent:64,level:'Средний',flags:[{text:'Флаг бизнеса'}],professions:[]}],topSpheres:['biology','business'],equivalentPairs:[],riasec:{},riasecLevels:{},riasecCode:'',label:'',domains:{},domainLevels:{},hints:[{id:'H01',text:'Проверьте требования к биологии.'},{id:'H03',text:'Проверьте интерес к бизнесу.'}],anchor:''};
  const html = renderResult({result,config});
  const cardStart = html.indexOf('<li class="result-card">');
  const cardEnd = html.indexOf('</li>',cardStart) + 5;
  const firstCard = html.slice(cardStart,cardEnd);
  const secondCardStart = html.indexOf('<li class="result-card">',cardEnd);
  const secondCard = html.slice(secondCardStart,html.indexOf('</li>',secondCardStart)+5);
  const entryStart = html.indexOf('<h2>Как войти и что попробовать</h2>');
  const entryEnd = html.indexOf('</section>',entryStart) + 10;
  const firstEntry = html.slice(entryStart,entryEnd);
  const secondEntryStart = html.indexOf('<h2>Как войти и что попробовать</h2>',entryEnd);
  const secondEntry = html.slice(secondEntryStart,html.indexOf('</section>',secondEntryStart)+10);
  assert.ok(firstCard.includes('&lt;Флаг биологии&gt;'));
  assert.ok(firstEntry.includes('&lt;Флаг биологии&gt;'));
  assert.doesNotMatch(firstCard+firstEntry,/Флаг бизнеса/);
  assert.ok(secondCard.includes('Флаг бизнеса'));
  assert.ok(secondEntry.includes('Флаг бизнеса'));
  assert.doesNotMatch(secondCard+secondEntry,/Флаг биологии/);
  for (const text of ['Попробуйте наблюдения.','Учитывайте условия лаборатории.','Поговорите с предпринимателем.','Учитывайте рынок.','Проверьте требования к биологии.','Проверьте интерес к бизнесу.']) assert.equal(html.split(text).length - 1,1,text);
  assert.doesNotMatch(firstCard+secondCard,/Попробуйте наблюдения|Учитывайте условия лаборатории|Поговорите с предпринимателем|Учитывайте рынок|Проверьте требования|Проверьте интерес/);
});
