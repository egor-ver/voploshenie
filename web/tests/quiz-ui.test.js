import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { renderQuiz, renderSessionIntro } from '../js/ui/quiz.js';
import { parseConfig } from '../js/domain/config.js';

const config = parseConfig(JSON.parse(await readFile(new URL('../data/test_config.json', import.meta.url), 'utf8')));

test('text maxlength is HTML-escaped', () => {
  const html=renderQuiz({screen:{kind:'question',part:'open',question:{id:'x',type:'text',max:'1000"><img src=x onerror="alert(1)>',prompt:'Вопрос'}}});
  assert.match(html,/maxlength="1000&quot;&gt;&lt;img src=x onerror=&quot;alert\(1\)&gt;"/);
  assert.doesNotMatch(html,/<img src=x onerror=/);
});

test('quiz navigation uses global boundaries across part transitions',()=>{
  const first=renderQuiz({screen:{kind:'question',part:'values',question:{id:'x',type:'scale',scale:{min:1,max:2}}},index:0,total:1,globalIndex:3,globalTotal:10,canAdvance:true});
  assert.match(first,/<button data-action="back">← Назад<\/button>/);
  assert.match(first,/<button class="primary" data-action="next"><span>Далее<\/span><svg aria-hidden="true"/);
  const last=renderQuiz({screen:{kind:'question',part:'values',question:{id:'x',type:'scale',scale:{min:1,max:2}}},index:0,total:1,globalIndex:9,globalTotal:10,canAdvance:true});
  assert.match(last,/<span>Показать результат<\/span><svg/);
});

test('sphere interest question renders its configured statement',()=>{
  const q=config.parts.find(part=>part.id==='sphere_interest').questions[0];
  const html=renderQuiz({screen:{kind:'question',part:'sphere_interest',question:q}});
  assert.ok(html.includes(q.fields['Утверждение']));
  assert.match(html,/class="question-statement"/);
});

test('PRF06 presents the other option without its instruction and keeps the 200 character field', () => {
  const q = config.parts.find(part => part.id === 'profile').questions.find(item => item.id === 'PRF06');
  const html = renderQuiz({ screen:{ kind:'profile', part:'profile', questions:[q] }, answers:{ profile:{ PRF06:'другое (с полем текста до 200 символов)' } }, index:0, total:28, partName:'Профиль', partNumber:1, percent:0 });
  assert.match(html, /<span>другое<\/span>/i);
  assert.doesNotMatch(html, /<span>другое \(с полем текста до 200 символов\)<\/span>/i);
  assert.match(html, /name="profile\.PRF06_other" maxlength="200"/);
});

test('scales use five-column 52px target rows and TSK uses task instructions without the no-right-answer hint', () => {
  const scale = question('likert5');
  const scaleHtml = render(scale);
  assert.match(scaleHtml, /class="scale-choices"/);
  assert.equal((scaleHtml.match(/class="choice"/g) || []).length, 5);
  assert.match(scaleHtml, /Интерес к сферам/);
  const task = config.parts.find(part => part.id === 'tasks').questions.find(item => item.id.startsWith('TSK'));
  const taskHtml = render(task);
  assert.match(taskHtml, /Мини-задания/);
  assert.doesNotMatch(taskHtml, /Здесь нет правильных ответов/);
});

test('EXP01 renders a horizontal five-choice energy scale with endpoint labels', () => {
  const q = config.parts.find(part => part.id === 'experience').questions.find(item => item.id === 'EXP01');
  const html = renderQuiz({ screen:{ kind:'question', part:'experience', question:q }, answers:{ experience:{ EXP01:{ text:'Опыт', energy:4 } } } });
  assert.match(html, /class="scale-choices"/);
  assert.equal((html.match(/name="EXP01_energy"/g) || []).length, 5);
  assert.match(html, /<span>выжало<\/span><span>зарядило<\/span>/);
  assert.match(html, /name="EXP01_energy" value="4" checked/);
});

const question = type => config.parts.flatMap(part => part.questions).find(item => item.type === type);
const render = (q, answers = {}) => renderQuiz({ screen: { kind:'question', part:'tasks', question:q }, answers, index: 0, total: 28, partName:'Задания', partNumber:6, percent:12 });

test('рендерит типы вопросов с реальными контролами и подписанными группами', () => {
  for (const type of ['likert5','pair','single_choice','text','text_energy5','scale']) {
    const q = question(type) || { id: type, type, scale: { min: 1, max: 5 }, fields: { text: 'Проверочный вопрос' }, prompt: 'Проверочный вопрос' };
    const html = render(q);
    assert.match(html, /role="group" aria-label=/);
    assert.match(html, /data-action="next"/);
    assert.match(html,/из 28 · Задания<small>12%<\/small>/);
    if(type==='pair'||type==='single_choice') assert.match(html,/type="radio"/);
    if(type==='text') assert.match(html,/<textarea/);
  }
});

test('SessionIntro starts a new attempt without extra checkboxes and offers continuation',()=>{
  const fresh=renderSessionIntro();
  assert.doesNotMatch(fresh,/test-consent|на этом устройстве/);
  assert.match(fresh,/data-action="start-test">Начать/);
  assert.match(fresh,/45–55 минут/);
  assert.match(fresh,/data-route="#path"/);
  const draft=renderSessionIntro({continuing:true});
  assert.match(draft,/data-action="start-test">Продолжить/);
});

test('text counters track entered boundaries, rank conflicts and pick3 cap are disabled',()=>{
  const q=config.parts.find(part=>part.id==='open').questions[0];
  const limit=q.max||1000;
  const text=renderQuiz({screen:{kind:'question',part:'open',question:q},answers:{open:{[q.id]:'я'.repeat(limit)}},percent:0});
  assert.match(text,new RegExp(`${limit} \/ ${limit}`));
  const values=config.parts.find(part=>part.id==='values').questions;
  const ranks=renderQuiz({screen:{kind:'values',part:'values',questions:values},answers:{values:{top:['VAL01'],least:['VAL02']}},percent:0});
  assert.match(ranks,/name="values-rank-1" value="VAL02"[^>]* disabled/);
  assert.match(ranks,/name="values-least" value="VAL01"[^>]* disabled/);
  const step=config.parts.find(part=>part.id==='needs').steps.find(item=>item.type==='pick3');
  const picked=step.options.slice(0,3).map(item=>item.id);
  const pick=renderQuiz({screen:{kind:'pick3',part:'needs',step},answers:{needs:{top3:picked}},percent:0});
  assert.match(pick,/Осталось выбрать: 0/);
  assert.match(pick,new RegExp(`value="${step.options[3].id}"[^>]* disabled`));
});

test('отмечает выбор, выводит прогресс и экранирует пользовательские данные', () => {
  const q = config.parts.find(p => p.id === 'tasks').questions[0];
  const option = typeof q.options[0] === 'string' ? { id: String.fromCharCode(97), text: q.options[0] } : q.options[0];
  const html = render(q, { tasks: { [q.id]: option.id } });
  assert.match(html, new RegExp(`value="${option.id}" checked`));
  assert.match(html, /aria-valuenow="12"/);
  const escaped = render({ id: 'x', type: 'text', prompt: '<img src=x onerror=1>', fields: {} }, { tasks:{x:'<script>alert(1)</script>'} });
  assert.ok(!escaped.includes('<script>'));
  assert.ok(escaped.includes('&lt;script&gt;'));
});

test('ценности дают раздельный выбор top 3 и least 2, а needs включает pick3', () => {
  const values=config.parts.find(p=>p.id==='values');
  const rank=renderQuiz({screen:{kind:'values',part:'values',questions:values.questions},answers:{values:{top:['VAL01'],least:['VAL02']}},total:28,percent:0});
  assert.match(rank, /Выберите три самые важные для вас ценности \(1, 2, 3\) и две наименее важные/);
  const cards=rank.match(/<article class="rank-value">[\s\S]*?<\/article>/g);
  assert.equal(cards.length,values.questions.length);
  for(const card of cards) {
    assert.equal((card.match(/name="values-rank-/g)||[]).length,3);
    assert.equal((card.match(/name="values-least"/g)||[]).length,1);
    assert.match(card,/<span>наименее важно<\/span>/);
  }
  assert.match(rank,/name="values-rank-1" value="VAL01" checked/);
  const step=config.parts.find(p=>p.id==='needs').steps.find(s=>s.type==='pick3');
  const html=renderQuiz({screen:{kind:'pick3',part:'needs',step},answers:{needs:{top3:['NEED01']}},total:28,percent:0});
  assert.match(html,/name="needs-top3"/);
  assert.match(html,/value="NEED01" checked/);
});

test('важным ценностям назначаются видимые ранги, а INT02 сохраняет позицию ответа', () => {
  const values=config.parts.find(p=>p.id==='values');
  const rank=renderQuiz({screen:{kind:'values',part:'values',questions:values.questions},answers:{values:{top:['VAL03','VAL01']}},total:28,percent:0});
  assert.match(rank,/name="values-rank-1" value="VAL03" checked/);
  assert.match(rank,/name="values-rank-2" value="VAL01" checked/);
  const pair=config.parts.find(p=>p.id==='interests').questions.find(q=>q.id==='INT02');
  const html=renderQuiz({screen:{kind:'question',part:'interests',question:pair},answers:{pairs:{INT02:'A'}}});
  assert.match(html,/name="INT02" value="A" checked/);
  assert.doesNotMatch(html,/name="INT02" value="B" checked/);
});

test('pair and single choices have separate letters, configured text and native radio groups',()=>{
  const q=config.parts.find(p=>p.id==='interests').questions[0];
  const html=renderQuiz({screen:{kind:'question',part:'interests',question:q},answers:{pairs:{[q.id]:'B'}}});
  const rows=html.match(/<label class="choice choice-row">[\s\S]*?<\/label>/g);
  assert.equal(rows.length,2);
  for(const [i,row] of rows.entries()) {
    const letter=['А','Б'][i];
    assert.ok(row.includes(q.fields[`Вариант ${letter}`]));
    assert.match(row,new RegExp(`<span class="choice-letter" aria-hidden="true">${letter}<\\/span>`));
    assert.match(row,new RegExp(`name="${q.id}" value="${['A','B'][i]}"${i===1?' checked':''}>`));
    assert.doesNotMatch(row,/[АБ]\. /);
  }
  const task=config.parts.find(p=>p.id==='tasks').questions[0];
  const taskHtml=render(task);
  assert.equal((taskHtml.match(/class="choice choice-row"/g)||[]).length,task.options.length);
  assert.match(taskHtml,/>В<\/span>/);
  assert.match(taskHtml,/>Г<\/span>/);
});

test('profile has no repeated instruction and uses last-option condition for the other field',()=>{
  const q=config.parts.find(p=>p.id==='profile').questions.find(q=>q.id==='PRF06');
  for(const option of q.options) {
    const html=renderQuiz({screen:{kind:'profile',part:'profile',questions:[q]},answers:{profile:{PRF06:option.id}}});
    assert.doesNotMatch(html,/class="prompt"/);
    assert.equal(html.includes('name="profile.PRF06_other"'),option===q.options.at(-1));
    assert.match(html,/<p class="hint">Здесь нет правильных ответов\.<\/p>/);
  }
});

test('text and experience retain Android hint and do not repeat the question above the input',()=>{
  for(const part of ['open','experience']) {
    const q=config.parts.find(p=>p.id===part).questions[0];
    const html=renderQuiz({screen:{kind:'question',part,question:q}});
    assert.match(html,/<p class="hint">Здесь нет правильных ответов\.<\/p>/);
    assert.match(html,/<p class="prompt">До 1000 символов<\/p>/);
    assert.match(html,/<label class="field-label"><textarea class="field" aria-label="/);
    assert.equal((html.match(/<h1 /g)||[]).length,1);
  }
});

test('ten-point scales keep all ten values and selection across both rows',()=>{
  const html=renderQuiz({screen:{kind:'question',part:'happiness',question:{id:'HAP',type:'scale10',scale:{min:1,max:10},prompt:'Шкала'}},answers:{open:{HAP:'10'}}});
  assert.equal((html.match(/type="radio" name="HAP"/g)||[]).length,10);
  for(let i=1;i<=10;i++) assert.ok(html.includes(`value="${i}"${i===10?' checked':''}><span>${i}</span>`));
  assert.match(html,/<div class="scale-labels"><span>1<\/span><span>10<\/span>/);
});

test('needs ratings use configured conditions, endpoint labels and no generic hint',()=>{
  const questions=config.parts.find(p=>p.id==='needs').questions.slice(0,2);
  const html=renderQuiz({screen:{kind:'questions',part:'needs',questions},answers:{needs:{ratings:{[questions[0].id]:5}}}});
  assert.match(html,/Насколько для вас важно в идеальной работе…/);
  assert.equal((html.match(/<fieldset>/g)||[]).length,2);
  for(const q of questions) assert.ok(html.includes(q.fields['Условие']));
  assert.match(html,new RegExp(`name="${questions[0].id}" value="5" checked`));
  assert.equal((html.match(/<span>совсем не важно<\/span><span>критически важно<\/span>/g)||[]).length,2);
  assert.doesNotMatch(html,/class="hint"|class="prompt"/);
  const single=renderQuiz({screen:{kind:'question',part:'needs',question:questions[0]}});
  assert.match(single,/<div class="scale-labels"><span>совсем не важно<\/span><span>критически важно<\/span>/);
  assert.ok(single.includes(`<p class="question-statement">${questions[0].fields['Условие']}</p>`));
});

test('quiz styles only reference declared design tokens',async()=>{
  const tokens=await readFile(new URL('../css/tokens.css',import.meta.url),'utf8');
  const styles=await readFile(new URL('../css/w10.css',import.meta.url),'utf8');
  const declared=new Set([...tokens.matchAll(/(--[\w-]+):/g)].map(match=>match[1]));
  const quiz=styles.split('\n').filter(line=>line.startsWith('.quiz-screen')).join('\n');
  for(const [,name] of quiz.matchAll(/var\((--[\w-]+)\)/g)) assert.ok(declared.has(name),name);
  assert.doesNotMatch(quiz,/#[0-9A-Fa-f]{3,8}\b|\d+px/);
});

test('progress and actions preserve Android labels and escape state',()=>{
  const screen={kind:'question',part:'interests',question:config.parts.find(p=>p.id==='interests').questions[0]};
  const html=renderQuiz({screen,index:0,total:15,percent:1,partName:'<Часть>',error:'<ошибка>',showExit:true});
  assert.match(html,/<strong>01<\/strong><span>из 15 · &lt;Часть&gt;<small>1%<\/small>/);
  assert.match(html,/class="quiz-header"[\s\S]*class="quiz-scroll"[\s\S]*<\/div><nav class="actions">/);
  assert.match(html,/<button data-action="back" disabled>← Назад/);
  assert.match(html,/class="primary" data-action="next" disabled/);
  assert.match(html,/role="alert">&lt;ошибка&gt;/);
  for(const action of ['confirm-exit','cancel-exit']) assert.ok(html.includes(`data-action="${action}"`));
  for(const [percent,expected] of [[-10,0],[200,100],['1" onclick="x',100/15]]) {
    const progress=renderQuiz({screen,percent,total:15});
    assert.ok(progress.includes(`aria-valuenow="${expected}"`));
    assert.doesNotMatch(progress,/onclick|undefined|NaN/);
  }
});
