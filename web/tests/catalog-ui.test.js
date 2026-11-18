import test from 'node:test';
import assert from 'node:assert/strict';
import { renderOpportunities, cardDate } from '../js/ui/opportunities.js';
import { renderDetail } from '../js/ui/detail.js';
import { renderSaved, savedCount } from '../js/ui/saved.js';
import { byNearestDate, recommendations } from '../js/domain/catalog.js';

const items = [
  { id:'a', type:'internship', title:'Дизайн <UX>', direction:'Маркетинг', spheres:['business'], date_text:'осень', dates:[{kind:'apply_deadline',date:'2026-10-25',precision:'day'}], url:'https://example.org/apply', url_kind:'program', organizer:'Студия', audience_text:'Для студентов', format:'remote' },
  { id:'b', type:'course', title:'Аналитика', spheres:['it'], dates:[{kind:'start',date:'2027-03',precision:'month'}], url:'javascript:alert(1)', format:'hybrid' },
  { id:'c', type:'event', title:'Открытая встреча', spheres:['biology'], dates:[], organizer:'Студия' }
];
test('карточки подборки показывают географическую пометку и сферу добора',()=>{
  const html=renderOpportunities({items,result:{topSpheres:['business','it','biology'],spheres:[]},recommended:true,city:'Казань',locationPreference:'a'});
  assert.match(html,/Онлайн/);
  assert.match(html,/Также из сферы/);
});
test('поиск учитывает только название и организатора, trim и экранирует карточку', () => {
  const html = renderOpportunities({ items, favorites:['a'], query:'  СТУДИЯ  ', type:'internship' });
  assert.match(html, /Найдено: 1/);
  assert.match(html, /Дизайн &lt;UX&gt;/);
  assert.match(html, /Стажировка/);
  assert.match(html, /data-save="a" aria-label="Удалить из избранного"/);
  assert.match(html, /В избранном/);
  assert.match(html, /class="save-toggle is-saved"/);
  assert.doesNotMatch(html, /Аналитика/);
  assert.match(renderOpportunities({items,query:'маркетинг'}), /Пока ничего не найдено/);
  assert.equal((renderOpportunities({ items, query:'   ' }).match(/class="opportunity-card"/g) || []).length, 3);
});
test('карточка показывает дату и подписи сфер и формата из каталога', () => {
  assert.deepEqual(cardDate(items[0]), { day:'25', month:'ОКТ' });
  assert.deepEqual(cardDate(items[2]), { day:'', month:'Дата уточняется' });
  assert.deepEqual(cardDate({...items[2],is_rolling:true}), {day:'',month:'Круглогодично'});
  const html = renderOpportunities({ items });
  assert.match(html, /Стажировка · Бизнес и управление/);
  assert.match(html, /Курс · Информационные технологии/);
  assert.match(html, /Событие · Биология и медицина/);
  assert.match(html, /Удалённо · Для студентов/);
  assert.match(html, /25<small>ОКТ<\/small>/);
});
test('сортировка дат применяется и в подборке', () => {
  const selected = [
    {...items[0], id:'late', title:'Поздняя', audience_text:'1, 3, 5 курс бакалавриата/специалитета', dates:[{kind:'start',date:'2028-03',precision:'month'}]},
    {...items[1], id:'middle', title:'Средняя', dates:[{kind:'start',date:'2027-03',precision:'month'}]},
    {...items[2], id:'early', title:'Ранняя', audience_text:'2 курс магистратуры', dates:[{kind:'start',date:'2027-01',precision:'month'}]}
  ];
  const options = {items:selected,result:{topSpheres:['business','it','biology']},recommended:true,course:'3 курс бакалавриата/специалитета'};
  const interest = renderOpportunities({...options,sort:'interest'});
  const dated = renderOpportunities({...options,sort:'date'});
  assert.ok(interest.indexOf('Поздняя') < interest.indexOf('Средняя'));
  assert.ok(dated.indexOf('Ранняя') < dated.indexOf('Средняя'));
  assert.ok(dated.indexOf('Средняя') < dated.indexOf('Поздняя'));
});
test('сортировка каталога и рекомендации сохраняют ближайшие даты и топ-сферы', () => {
  const sorted = byNearestDate(items, '2026-10-26');
  assert.deepEqual(sorted.map(item => item.id), ['b','a','c']);
  assert.deepEqual(recommendations(items, {topSpheres:['business','it','biology']}).map(row => row.item.id), ['a','b','c']);
});
test('каталог, детали и избранное используют общую навигацию из пяти вкладок', () => {
  const html = renderOpportunities({items});
  assert.match(html, /<img class="brand-mark"/);
  assert.doesNotMatch(html, /<span class="mark">В<\/span>/);
  assert.equal((html.match(/<nav class="bottom-nav"/g) || []).length, 1);
  assert.equal((html.match(/<a href=/g) || []).length, 5);
  assert.equal((html.match(/class="nav-icon nav-icon-(?:path|opportunities|saved|deadlines|profile)"/g) || []).length, 5);
  assert.match(html, /href="#opportunities" aria-current="page"/);
  assert.match(renderSaved({items,favorites:[]}), /href="#saved" aria-current="page"/);
});
test('детали показывают назначение дат и только безопасную внешнюю ссылку', () => {
  const item = {...items[0], title:'Программа <UX>', dates:[
    {kind:'apply_deadline',date:'2026-10-25',precision:'day'},
    {kind:'start',date:'2027-03',precision:'month'},
    {kind:'end',date:'2027',precision:'year'}
  ]};
  const html = renderDetail({item, favorites:['a']});
  assert.match(html, /Программа &lt;UX&gt;/);
  assert.match(html, /Приём заявок · 25 октября 2026/);
  assert.match(html, /Начало · март 2027/);
  assert.match(html, /Окончание · 2027/);
  assert.match(html, /target="_blank" rel="noopener noreferrer"/);
  assert.match(html, /В избранном/);
  assert.equal((html.match(/<a href=/g) || []).length, 5);
  assert.doesNotMatch(renderDetail({item:items[1]}), /href="javascript:/);
});
test('избранное показывает правильные числительные и собственную навигацию', () => {
  const html = renderSaved({items, favorites:['a']});
  assert.match(html, /1 сохранённая возможность/);
  assert.match(html, /Найти ещё возможности/);
  assert.match(html, /aria-current="page"><span class="nav-icon nav-icon-saved" aria-hidden="true"><\/span><span>Избранное/);
  assert.doesNotMatch(html, /catalog-search|catalog-filters/);
  assert.deepEqual([1,2,4,5,11,21,22].map(savedCount), [
    '1 сохранённая возможность','2 сохранённые возможности','4 сохранённые возможности',
    '5 сохранённых возможностей','11 сохранённых возможностей','21 сохранённая возможность','22 сохранённые возможности'
  ]);
  assert.match(renderSaved({items, favorites:[]}), /Здесь пока пусто\./);
});
