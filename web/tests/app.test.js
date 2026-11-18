import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { parseConfig } from '../js/domain/config.js';
import { compute } from '../js/domain/scoring.js';
import { scoringAnswers } from '../js/domain/completion.js';
import { initialState } from '../js/store.js';
import { prepareQuizSnapshot } from './fixtures/quiz-snapshot.js';

const config = parseConfig(JSON.parse(await readFile(new URL('../data/test_config.json', import.meta.url), 'utf8')));
const catalog = JSON.parse(await readFile(new URL('../data/catalog.json', import.meta.url), 'utf8'));
const demoAnswers = JSON.parse(await readFile(new URL('../data/demo_answers.json', import.meta.url), 'utf8'));
const screens = config.parts.flatMap(part => {
  if (part.id === 'profile' || part.id === 'values') return [part.id];
  if (part.id === 'needs') return part.steps.map(step => `needs/${step.id}`);
  return part.questions.map(question => question.id);
});

test('sorting reload preserves recommendation route', async () => {
  const before=restoreGlobals(), storage=disk(JSON.stringify({...initialState(config.version),result:'complete',savedResult:{...compute({},config),configVersion:config.version}})), location={hash:'#opportunities?recommended'};
  try {
    const catalogPage=await mount(storage,location,201);
    catalogPage.click('route','#sort'); location.hash='#sort'; catalogPage.hashchange();
    assert.equal(JSON.parse(storage.value).sortingRoute,'#opportunities?recommended');
    const reloaded=await mount(storage,location,202);
    reloaded.click('action','apply-sort');
    assert.equal(location.hash,'#opportunities?recommended');
    assert.match(reloaded.root.innerHTML,/<h1>Подборка для вас<\/h1>/);
    assert.equal(JSON.parse(storage.value).sortingRoute,undefined);
  } finally { resetGlobals(before); }
});

function mount(storage, location, serial, loadedConfig=config) {
  const root = { handlers:{}, innerHTML:'', addEventListener(type, handler) { (this.handlers[type] ||= []).push(handler); }, replaceChildren() { this.innerHTML = ''; }, querySelector() { return { focus(){}, setSelectionRange(){} }; } };
  const windowHandlers = {};
  globalThis.document = { querySelector:() => root };
  globalThis.localStorage = storage;
  globalThis.location = location;
  globalThis.window = { addEventListener(type, handler) { (windowHandlers[type] ||= []).push(handler); } };
  globalThis.fetch = async url => ({ json:async () => String(url).includes('catalog.json') ? catalog : String(url).includes('demo_answers.json') ? demoAnswers : loadedConfig });
  const fire = (type, target) => root.handlers[type].forEach(handler => handler({ target, preventDefault(){} }));
  const click = (attribute, value) => fire('click', { closest(selector) { return selector === `[data-${attribute}]` ? { dataset:{ [attribute]:value } } : null; } });
  const hashchange = () => windowHandlers.hashchange.forEach(handler => handler());
  return import(`../js/app.js?run=${serial}`).then(() => ({ root, click, fire, hashchange }));
}

function seedComplete(storage, loadedConfig=config) {
  const answers={profile:JSON.parse(demoAnswers.profile),values:JSON.parse(demoAnswers.values),needs:{ratings:{},top3:JSON.parse(demoAnswers['needs/pick3'])}};
  const groups={interests:'pairs',sphere_interest:'interest',tasks:'tasks',experience:'experience',constraints:'nad',consistency:'checks',open:'open',happiness:'open'};
  for(const part of loadedConfig.parts) {
    if(part.id==='profile'||part.id==='values') continue;
    if(part.id==='needs') { for(const q of part.questions) answers.needs.ratings[q.id]=Number(demoAnswers[q.id]); continue; }
    const group=groups[part.id]; answers[group] ||= {};
    for(const q of part.questions) if(demoAnswers[q.id]!==undefined) answers[group][q.id]=part.id==='experience'?JSON.parse(demoAnswers[q.id]):demoAnswers[q.id];
  }
  const scored=scoringAnswers(demoAnswers,loadedConfig);
  storage.value=JSON.stringify({...initialState(loadedConfig.version),answers,index:screens.length,result:'complete',savedResult:{...compute(scored,loadedConfig),configVersion:loadedConfig.version},savedConfig:loadedConfig});
}

const disk = value => ({ value, getItem() { return this.value; }, setItem(_key, value) { this.value = value; } });
const restoreGlobals = () => Object.fromEntries(['document','localStorage','location','window','fetch'].map(key => [key,globalThis[key]]));
const resetGlobals = before => { for (const [key,value] of Object.entries(before)) { if (value === undefined) delete globalThis[key]; else globalThis[key] = value; } };

test('invalid persisted profile resets before test rendering',async()=>{
  const before=restoreGlobals();
  const storage=disk(JSON.stringify({...initialState(config.version),testStarted:true,answers:{profile:{PRF03:123}}}));
  try {
    const app=await mount(storage,{hash:'#test'},19);
    assert.match(app.root.innerHTML,/data-action="start-test"/);
    assert.doesNotMatch(app.root.innerHTML,/name="profile\.PRF03"/);
    beginTest(app);
    assert.match(app.root.innerHTML,/name="profile\.PRF03"/);
    assert.deepEqual(JSON.parse(storage.value).answers,{});
  } finally { resetGlobals(before); }
});

test('profile transition blocks invalid limits and accepts inclusive limits',async()=>{
  const before=restoreGlobals(), base=JSON.parse(demoAnswers.profile), option=config.parts.find(p=>p.id==='profile').questions.find(q=>q.id==='PRF06').options.at(-1);
  try {
    for(const [index,valid] of [false,true].entries()) {
      const profile={...base,PRF01:'n'.repeat(valid?60:61),PRF03:'x'.repeat(120),PRF04:'y'.repeat(120),PRF06:option.id||option,PRF06_other:'z'.repeat(valid?200:201)};
      const storage=disk(JSON.stringify({...initialState(config.version),testStarted:true,answers:{profile}})), app=await mount(storage,{hash:'#test'},80+index);
      app.click('action','next');
      assert.equal(JSON.parse(storage.value).index,valid?1:0);
    }
  } finally { resetGlobals(before); }
});

test('experience transition accepts no answer and completion without optional experience',async()=>{
  const before=restoreGlobals(), exp=config.questions.experience[0].id, expIndex=screens.indexOf(exp);
  try {
    const transitionStorage=disk(JSON.stringify(initialState(config.version)));
    seedComplete(transitionStorage);
    const state=JSON.parse(transitionStorage.value); delete state.answers.experience[exp]; state.index=expIndex; state.result=null; state.savedResult=null; state.savedConfig=null; state.testStarted=true; transitionStorage.value=JSON.stringify(state);
    const app=await mount(transitionStorage,{hash:'#test'},91); app.click('action','next');
    assert.equal(JSON.parse(transitionStorage.value).index,expIndex+1);
    const final=JSON.parse(transitionStorage.value); final.index=screens.length-1; transitionStorage.value=JSON.stringify(final);
    const finish=await mount(transitionStorage,{hash:'#test'},92); finish.click('action','next');
    assert.equal(JSON.parse(transitionStorage.value).result,'complete');
    assert.equal(JSON.parse(transitionStorage.value).answers.experience[exp],undefined);
    assert.equal(transitionStorage.value && (await mount(transitionStorage,{hash:'#result'},93)).root.innerHTML.includes('Топ-3 направления'),true);
  } finally { resetGlobals(before); }
});

test('invalid persisted catalog query resets before catalog rendering',async()=>{
  const before=restoreGlobals();
  const storage=disk(JSON.stringify({...initialState(config.version),catalogQuery:123}));
  try {
    const app=await mount(storage,{hash:'#opportunities'},20);
    assert.ok(app.root.innerHTML.length>0);
    assert.equal(JSON.parse(storage.value).catalogQuery,123);
  } finally { resetGlobals(before); }
});

test('unknown hash routes to the Path screen instead of starting the test', async () => {
  const before=restoreGlobals();
  const location={hash:'#/'};
  try {
    const app=await mount(disk(JSON.stringify(initialState(config.version))),location,31);
    assert.equal(location.hash,'#path');
    assert.match(app.root.innerHTML,/class="path-home"/);
    assert.doesNotMatch(app.root.innerHTML,/class="answers"/);
    location.hash='#opportunities-unknown'; app.hashchange();
    assert.equal(location.hash,'#path');
    assert.match(app.root.innerHTML,/class="path-home"/);
  } finally { resetGlobals(before); }
});

test('незавершённые ранги блокируют переход; выход, перезагрузка и продолжение сохраняют попытку', async () => {
  const before = restoreGlobals();
  const valueIndex = screens.indexOf('values');
  const storage = disk(JSON.stringify({ ...initialState(config.version), testStarted:true, index:valueIndex, answers:{ values:{top:['','','VAL03'],least:['VAL04','VAL05']} } }));
  const location = { hash:'#test' };
  try {
    const first = await mount(storage,location,1);
    first.click('action','next');
    assert.equal(JSON.parse(storage.value).index,valueIndex);
    assert.match(first.root.innerHTML,/role="alert"/);
    first.click('action','exit');
    first.click('action','confirm-exit');
    assert.equal(location.hash,'#path');
    assert.match(first.root.innerHTML,/data-action="continue"/);
    const reloaded = await mount(storage,location,2);
    assert.match(reloaded.root.innerHTML,/data-action="continue"/);
    reloaded.click('action','continue');
    assert.equal(location.hash,'#test');
    reloaded.hashchange();
    assert.match(reloaded.root.innerHTML,/name="values-rank-3" value="VAL03" checked/);
    assert.equal(JSON.parse(storage.value).index,valueIndex);
  } finally { resetGlobals(before); }
});

test('null во вложенных флагах делает сохранённый результат недействительным и восстанавливается', async () => {
  const before = restoreGlobals();
  const savedResult = compute({}, config);
  savedResult.spheres[0].flags = [null];
  const storage = disk(JSON.stringify({ ...initialState(config.version), result:'complete', answers:{values:{top:[],least:[]},needs:{top3:[],ratings:{}}}, savedResult }));
  try {
    const mounted = await mount(storage,{hash:'#result'},3);
    assert.match(mounted.root.innerHTML,/class="path-home"/);
    assert.equal(JSON.parse(storage.value).result,null);
  } finally { resetGlobals(before); }
});

test('повреждённый результат пересчитывается при открытии результата', async () => {
  const before = restoreGlobals();
  const storage = disk(JSON.stringify({ ...initialState(config.version), result:'complete', answers:{values:{top:[],least:[]},needs:{top3:[],ratings:{}}}, savedResult:{configVersion:config.version} }));
  try {
    const mounted = await mount(storage,{hash:'#result'},4);
    assert.match(mounted.root.innerHTML,/class="path-home"/);
    assert.equal(JSON.parse(storage.value).savedResult,null);
  } finally { resetGlobals(before); }
});

test('демо проходит от старта до результата, подборки, детали и избранного после перезагрузки', async () => {
  const before = restoreGlobals();
  const storage = disk(JSON.stringify(initialState(config.version)));
  const location = { hash:'#path' };
  try {
    let app = await mount(storage,location,5);
    assert.doesNotMatch(app.root.innerHTML,/data-action="demo"/);
    assert.match(app.root.innerHTML,/data-action="continue"/);
    assert.equal((app.root.innerHTML.match(/<nav class="bottom-nav"/g) || []).length,1);
    app.click('action','continue');
    assert.doesNotMatch(app.root.innerHTML,/data-action="demo"/);
    seedComplete(storage);
    location.hash='#result';
    app=await mount(storage,location,56);
    assert.equal(location.hash,'#result');
    assert.match(app.root.innerHTML,/80%/);
    assert.match(app.root.innerHTML,/65%/);
    assert.match(app.root.innerHTML,/56%/);
    const original = JSON.parse(storage.value).savedResult;
    location.hash='#path'; app.hashchange();
    assert.match(app.root.innerHTML,/Открыть результат/);
    assert.doesNotMatch(app.root.innerHTML,/data-action="continue"/);
    app.click('route','#result'); app.hashchange();
    assert.match(app.root.innerHTML,/data-action="catalog"/);
    app.click('action','catalog');
    assert.equal(location.hash,'#opportunities?recommended');
    app.hashchange();
    assert.match(app.root.innerHTML,/Подборка для вас/);
    assert.deepEqual(JSON.parse(storage.value).catalogResult.topSpheres,original.topSpheres);
    const id = app.root.innerHTML.match(/data-item="([^"]+)"/)[1];
    const selectedItem = catalog.find(item => item.id === id);
    app.click('item',id);
    assert.equal(location.hash,`#detail/${encodeURIComponent(id)}`);
    app.hashchange();
    assert.match(app.root.innerHTML,new RegExp(selectedItem.title.replace(/[.*+?^${}()|[\]\\]/g,'\\$&')));
    assert.match(app.root.innerHTML,new RegExp(`data-save="${id}"`));
    app.click('save',id);
    assert.deepEqual(JSON.parse(storage.value).favorites,[id]);
    app.click('route','#saved'); app.hashchange();
    app = await mount(storage,{hash:'#saved'},6);
    assert.match(app.root.innerHTML,/Избранное/);
    assert.match(app.root.innerHTML,new RegExp(selectedItem.title.replace(/[.*+?^${}()|[\]\\]/g,'\\$&')));
    assert.deepEqual(JSON.parse(storage.value).savedResult,original);
  } finally { resetGlobals(before); }
});

test('старт показывает новую попытку, черновик и завершённый результат; в тесте нет нижней панели', async () => {
  const before = restoreGlobals();
  const storage = disk(JSON.stringify(initialState(config.version)));
  try {
    let app = await mount(storage,{hash:'#path'},15);
    assert.match(app.root.innerHTML,/data-action="continue">Начать профтест/);
    assert.equal((app.root.innerHTML.match(/<nav class="bottom-nav"/g) || []).length,1);
    storage.value=JSON.stringify({ ...initialState(config.version),index:1 });
    app=await mount(storage,{hash:'#path'},16);
    assert.match(app.root.innerHTML,/data-action="continue">Продолжить/);
    storage.value=JSON.stringify({ ...initialState(config.version),result:'complete',savedResult:{...compute({},config),configVersion:config.version} });
    app=await mount(storage,{hash:'#path'},17);
    assert.match(app.root.innerHTML,/data-route="#result">Открыть результат/);
    app=await mount(storage,{hash:'#test'},18);
    assert.doesNotMatch(app.root.innerHTML,/class="bottom-nav"/);
  } finally { resetGlobals(before); }
});

test('прямое открытие подборки пересобирает проекцию при повреждённом результате и устаревшей проекции', async () => {
  const before = restoreGlobals();
  const storage = disk(JSON.stringify({ ...initialState(config.version), result:'complete', answers:{values:{top:[],least:[]},needs:{top3:[],ratings:{}}}, savedResult:{configVersion:config.version,spheres:[null]}, catalogResult:{spheres:[],topSpheres:['stale']} }));
  try {
    const app = await mount(storage,{hash:'#opportunities?recommended'},7);
    assert.match(app.root.innerHTML,/Подборка для вас/);
    const repaired = JSON.parse(storage.value);
    assert.equal(repaired.savedResult,null);
    assert.equal(repaired.result,null);
  } finally { resetGlobals(before); }
});

test('прямой обычный каталог восстанавливает повреждённую проекцию до сортировки по интересам', async () => {
  const before = restoreGlobals();
  const storage = disk(JSON.stringify({ ...initialState(config.version), result:'complete', savedResult:{ ...compute({},config), configVersion:config.version }, catalogResult:{ spheres:[null], topSpheres:[] } }));
  try {
    const app = await mount(storage,{hash:'#opportunities'},9);
    const repaired = JSON.parse(storage.value);
    assert.ok(repaired.catalogResult.spheres.every(sphere => sphere && typeof sphere.id === 'string' && Number.isFinite(sphere.percent)));
    app.fire('change',{ name:'sort', value:'interest', type:'radio' });
    assert.equal(JSON.parse(storage.value).catalogSort,undefined);
    app.click('action','apply-sort');
    assert.equal(JSON.parse(storage.value).catalogSort,'interest');
    assert.equal(JSON.parse(storage.value).sortChoice,undefined);
    assert.match(app.root.innerHTML,/Возможности/);
  } finally { resetGlobals(before); }
});

test('корректный результат сохраняется, а поиск через input и change не меняет ответы', async () => {
  const before = restoreGlobals();
  const result = { ...compute({},config), configVersion:config.version };
  const storage = disk(JSON.stringify({ ...initialState(config.version), result:'complete', savedResult:result, catalogQuery:'' }));
  try {
    const app = await mount(storage,{hash:'#opportunities'},8);
    const savedBefore = JSON.parse(storage.value).savedResult;
    const input = { name:'catalog-query', value:'пример', selectionStart:7, tagName:'INPUT', focus(){}, setSelectionRange(){} };
    app.fire('input',input);
    app.fire('change',input);
    const updated = JSON.parse(storage.value);
    assert.equal(updated.catalogQuery,'пример');
    assert.deepEqual(updated.savedResult,savedBefore);
    assert.equal(updated.answers.profile,undefined);
  } finally { resetGlobals(before); }
});

test('ошибка загрузки данных показывает повтор, после повтора приложение стартует', async () => {
  const before = restoreGlobals();
  const root = { innerHTML:'', onclick:null, addEventListener() {}, querySelector() { return null; } };
  let offline = true;
  globalThis.document = { querySelector:() => root };
  globalThis.localStorage = disk(null);
  globalThis.location = { hash:'#path' };
  globalThis.window = { addEventListener() {} };
  globalThis.fetch = async url => {
    if (offline) throw new TypeError('offline');
    return { json:async () => String(url).includes('catalog.json') ? catalog : String(url).includes('demo_answers.json') ? demoAnswers : config };
  };
  try {
    const started = import('../js/app.js?run=boot-error');
    await new Promise(resolve => setTimeout(resolve, 0));
    assert.match(root.innerHTML, /Не получилось загрузить/);
    assert.doesNotMatch(root.innerHTML, /bottom-nav/);
    offline = false;
    root.onclick({ target:{ closest: selector => selector === '[data-action="retry"]' ? {} : null } });
    await started;
    assert.match(root.innerHTML, /Идеи обретают форму/);
    assert.equal(root.onclick, null);
  } finally { resetGlobals(before); }
});

test('сохранение от другой версии конфига начинает заново, повторное прохождение очищает результат', async () => {
  const before = restoreGlobals();
  const stale = { ...initialState(config.version + 1), index:5, answers:{ profile:{ PRF03:'старое' } }, result:'complete', favorites:['x'] };
  const storage = disk(JSON.stringify(stale));
  const location = { hash:'#path' };
  try {
    let app = await mount(storage,location,21);
    assert.match(app.root.innerHTML,/data-action="continue">Начать профтест/);
    assert.doesNotMatch(app.root.innerHTML,/Открыть результат/);
    seedComplete(storage);
    app=await mount(storage,location,22);
    app.click('route','#path'); location.hash = '#path'; app.hashchange();
    assert.match(app.root.innerHTML,/Открыть результат/);
    const saved = JSON.parse(storage.value);
    assert.equal(saved.configVersion,config.version);
    assert.deepEqual(saved.favorites,[]);
    assert.deepEqual(saved.savedResult.spheres.slice(0,3).map(sphere => [sphere.id,sphere.percent]),[['it',80],['finance',65],['engineering',56]]);
    app.click('action','reset-test');
    assert.equal(location.hash,'#test');
    const reset = JSON.parse(storage.value);
    assert.deepEqual([reset.answers,reset.index,reset.result,reset.savedResult],[{},0,null,null]);
    app.hashchange();
    assert.match(app.root.innerHTML,/data-action="start-test"/);
    beginTest(app);
    assert.match(app.root.innerHTML,/name="profile\.PRF03"/);
  } finally { resetGlobals(before); }
});

test('damaged path result with an invalid sphere collection resets on a no-hash reload', async()=>{
  const before=restoreGlobals(), result={...compute({},config),configVersion:config.version}; result.spheres={};
  const storage=disk(JSON.stringify({...initialState(config.version),result:'complete',savedResult:result})), location={hash:''};
  try { const app=await mount(storage,location,43); assert.equal(location.hash,'#path'); assert.match(app.root.innerHTML,/class="path-home"/); assert.doesNotMatch(app.root.innerHTML,/Открыть результат/); assert.equal(JSON.parse(storage.value).result,null); }
  finally { resetGlobals(before); }
});

test('damaged result recovers from a complete same-version attempt on path and no-hash reloads',async()=>{
  const before=restoreGlobals();
  try {
    for(const [index,hash] of ['','#path'].entries()) {
      const storage=disk(JSON.stringify(initialState(config.version)));
      seedComplete(storage);
      const saved=JSON.parse(storage.value); saved.savedResult.spheres={}; storage.value=JSON.stringify(saved);
      const app=await mount(storage,{hash},71+index*2);
      assert.equal(JSON.parse(storage.value).result,'complete');
      assert.equal(JSON.parse(storage.value).savedResult.spheres[0].percent,80);
    }
  } finally { resetGlobals(before); }
});

test('damaged result does not recover from incomplete or old-version answers',async()=>{
  const before=restoreGlobals();
  try {
    for(const [index,changes] of [{answers:{profile:{PRF03:'incomplete'}}},{configVersion:config.version-1}].entries()) {
      const result={...compute({},config),configVersion:config.version}; result.spheres={};
      const storage=disk(JSON.stringify({...initialState(config.version),...changes,result:'complete',savedResult:result,savedConfig:config}));
      const app=await mount(storage,{hash:'#path'},74+index);
      assert.match(app.root.innerHTML,/class="path-home"/);
      assert.equal(JSON.parse(storage.value).result,null);
    }
  } finally { resetGlobals(before); }
});

test('a result from an older config and its valid favorites survive migration', async()=>{
  const before=restoreGlobals(), old={...compute({},config),configVersion:config.version};
  const id=catalog[0].id, storage=disk(JSON.stringify({...initialState(config.version),result:'complete',savedResult:old,savedConfig:config,favorites:[id]}));
  try { const app=await mount(storage,{hash:'#path'},44); assert.match(app.root.innerHTML,/Открыть результат/); const state=JSON.parse(storage.value); assert.equal(state.savedResult.spheres[0].percent,old.spheres[0].percent); assert.deepEqual(state.favorites,[id]); }
  finally { resetGlobals(before); }
});

test('changed config preserves prior result and profile answers; invalid config snapshot safely clears result',async()=>{
  const before=restoreGlobals(), storage=disk(JSON.stringify(initialState(config.version))), location={hash:'#path'};
  try {
    seedComplete(storage);
    const first=await mount(storage,location,45);
    const original=JSON.parse(storage.value), changed=structuredClone(config); changed.version++;
    changed.parts.find(p=>p.id==='profile').questions[0].text+=' changed';
    const second=await mount(storage,{hash:'#path'},46,changed), migrated=JSON.parse(storage.value);
    assert.equal(migrated.savedResult.spheres[0].percent,original.savedResult.spheres[0].percent);
    assert.deepEqual(migrated.answers,original.answers);
    assert.equal(migrated.configVersion,config.version);
    migrated.savedConfig={}; storage.value=JSON.stringify(migrated);
    const repaired=await mount(storage,{hash:''},47,changed);
    assert.match(repaired.root.innerHTML,/class="path-home"/);
    assert.equal(JSON.parse(storage.value).result,null);
  } finally { resetGlobals(before); }
});

test('saved config snapshots validate before path and result render while favorites survive damage',async()=>{
  const before=restoreGlobals(), id=catalog[0].id, changed=structuredClone(config); changed.version++;
  try {
    for(const [index,hash] of ['','#path','#result'].entries()) {
      const result={...compute({},config),configVersion:config.version}, bad=structuredClone(config); bad.texts.spheres.table=[null];
      const storage=disk(JSON.stringify({...initialState(config.version),result:'complete',savedResult:result,savedConfig:bad,favorites:[id]}));
      const app=await mount(storage,{hash},100+index,changed);
      assert.doesNotMatch(app.root.innerHTML,/Топ-3 направления/);
      assert.equal(JSON.parse(storage.value).result,null);
      assert.deepEqual(JSON.parse(storage.value).favorites,[id]);
    }
    for(const [index,hash] of ['','#path','#result'].entries()) {
      const result={...compute({},config),configVersion:config.version}, storage=disk(JSON.stringify({...initialState(config.version),result:'complete',savedResult:result,savedConfig:config,favorites:[id]}));
      const app=await mount(storage,{hash},110+index,changed);
      if(hash==='#result') assert.match(app.root.innerHTML,/Топ-3 направления/);
      else assert.match(app.root.innerHTML,/data-route="#result">Открыть результат/);
      assert.equal(JSON.parse(storage.value).result,'complete');
      assert.deepEqual(JSON.parse(storage.value).favorites,[id]);
    }
  } finally { resetGlobals(before); }
});

test('legacy current result without snapshot metadata remains readable and gains the active snapshot',async()=>{
  const before=restoreGlobals(), id=catalog[0].id, result={...compute({},config)};
  delete result.configVersion;
  const storage=disk(JSON.stringify({...initialState(config.version),result:'complete',savedResult:result,favorites:[id]}));
  try {
    const app=await mount(storage,{hash:'#result'},130);
    const saved=JSON.parse(storage.value);
    assert.match(app.root.innerHTML,/Топ-3 направления/);
    assert.equal(saved.savedResult.configVersion,config.version);
    assert.equal(saved.savedConfig.version,config.version);
    assert.deepEqual(saved.favorites,[id]);
  } finally { resetGlobals(before); }
});

test('invalid score ranges reset corrupted results on every result-bearing route and keep favorites',async()=>{
  const before=restoreGlobals(), id=catalog[0].id;
  try {
    for(const [index,hash] of ['#path','#result','#profile','#opportunities?recommended'].entries()) {
      const result={...compute({},config),configVersion:config.version};
      result.riasec.R=-1; result.domains.logic=999; result.spheres[0].percent=999;
      const storage=disk(JSON.stringify({...initialState(config.version),result:'complete',savedResult:result,savedConfig:config,favorites:[id]}));
      const app=await mount(storage,{hash},140+index);
      const repaired=JSON.parse(storage.value);
      assert.doesNotMatch(app.root.innerHTML,/Ваше направление/);
      assert.equal(repaired.result,null);
      assert.equal(repaired.configVersion,config.version);
      assert.deepEqual(repaired.favorites,[id]);
    }
  } finally { resetGlobals(before); }
});

test('a complete attempt repairs a missing config snapshot and computes with its current config',async()=>{
  const before=restoreGlobals(), id=catalog[0].id, changed=structuredClone(config); changed.version++;
  changed.spheres.forEach(sphere=>sphere.riasec.reverse());
  changed.texts.spheres.table.forEach(row=>row['Вход и первые пробы']='Обновлённый текст');
  try {
    const seed=disk(JSON.stringify(initialState(changed.version))), started=await mount(seed,{hash:'#path'},150,changed);
    seedComplete(seed,changed);
    await mount(seed,{hash:'#path'},152,changed);
    const persisted=JSON.parse(seed.value), expected=compute(scoringAnswers(flattenForScoring(persisted.answers),changed),changed);
    persisted.savedResult.riasec.R=-1; persisted.savedConfig={}; persisted.favorites=[id]; seed.value=JSON.stringify(persisted);
    const app=await mount(seed,{hash:'#result'},151,changed);
    const repaired=JSON.parse(seed.value);
    assert.match(app.root.innerHTML,/Обновлённый текст/);
    assert.deepEqual(repaired.savedResult.spheres,expected.spheres);
    assert.equal(repaired.savedResult.configVersion,changed.version);
    assert.equal(repaired.savedConfig.version,changed.version);
    assert.deepEqual(repaired.favorites,[id]);
  } finally { resetGlobals(before); }
});

function flattenForScoring(answers) {
  const values={profile:JSON.stringify(answers.profile),values:JSON.stringify(answers.values),'needs/pick3':JSON.stringify(answers.needs.top3)};
  Object.assign(values,answers.needs.ratings,answers.pairs,answers.interest,answers.tasks,answers.nad,answers.checks,answers.open);
  for(const [id,answer] of Object.entries(answers.experience)) values[id]=JSON.stringify(answer);
  return values;
}

test('resetting an old result starts a draft on the current config version that survives reload',async()=>{
  const before=restoreGlobals(), old={...compute({},config),configVersion:config.version}, changed=structuredClone(config); changed.version++;
  const storage=disk(JSON.stringify({...initialState(config.version),result:'complete',savedResult:old,savedConfig:config,favorites:[catalog[0].id]}));
  try {
    const app=await mount(storage,{hash:'#path'},120,changed); app.click('action','reset-test');
    const fresh=JSON.parse(storage.value);
    assert.equal(fresh.configVersion,changed.version);
    assert.equal(fresh.result,null);
    beginTest(app);
    app.fire('change',{name:'profile.PRF03',value:'Новая специальность'});
    const reloaded=await mount(storage,{hash:'#test'},121,changed);
    assert.match(reloaded.root.innerHTML,/Новая специальность/);
    assert.equal(JSON.parse(storage.value).configVersion,changed.version);
    assert.deepEqual(JSON.parse(storage.value).favorites,[catalog[0].id]);
  } finally { resetGlobals(before); }
});

test('clearing a damaged old result allows a direct same-page test entry to survive reload',async()=>{
  const before=restoreGlobals(), changed=structuredClone(config); changed.version++;
  const result={...compute({},config),configVersion:config.version}; result.domains.logic=999;
  const storage=disk(JSON.stringify({...initialState(config.version),result:'complete',savedResult:result,savedConfig:config,favorites:[catalog[0].id]}));
  const location={hash:'#path'};
  try {
    const app=await mount(storage,location,160,changed);
    assert.equal(JSON.parse(storage.value).configVersion,changed.version);
    location.hash='#test'; app.hashchange(); beginTest(app);
    app.fire('change',{name:'profile.PRF03',value:'Новая специальность'});
    const reloaded=await mount(storage,{hash:'#test'},161,changed);
    assert.match(reloaded.root.innerHTML,/Новая специальность/);
    assert.equal(JSON.parse(storage.value).configVersion,changed.version);
    assert.deepEqual(JSON.parse(storage.value).favorites,[catalog[0].id]);
  } finally { resetGlobals(before); }
});

test('recovering a full attempt from a damaged result version stores its current config snapshot',async()=>{
  const before=restoreGlobals(), storage=disk(JSON.stringify(initialState(config.version)));
  try {
    seedComplete(storage);
    await mount(storage,{hash:'#path'},170);
    const app=await mount(storage,{hash:'#path'},170);
    const damaged=JSON.parse(storage.value); damaged.result='complete'; damaged.savedResult={...compute({},config),configVersion:'broken'}; damaged.savedConfig=null; storage.value=JSON.stringify(damaged);
    await mount(storage,{hash:'#path'},171);
    const repaired=JSON.parse(storage.value);
    assert.equal(repaired.savedResult.configVersion,config.version);
    assert.equal(repaired.savedConfig.version,config.version);
    assert.deepEqual(repaired.answers,damaged.answers);
  } finally { resetGlobals(before); }
});

test('clicking an already selected value rank clears it',async()=>{
  const before=restoreGlobals(), valuesIndex=screens.indexOf('values'), storage=disk(JSON.stringify({...initialState(config.version),testStarted:true,index:valuesIndex,answers:{values:{top:['VAL01','VAL02','VAL03'],least:['VAL04','VAL05']}}}));
  try {
    const app=await mount(storage,{hash:'#test'},172);
    app.fire('click',{closest(selector){return selector==='input[type="radio"][name^="values-rank-"]'?{checked:true,name:'values-rank-1',value:'VAL01'}:null;},preventDefault(){}});
    assert.equal(JSON.parse(storage.value).answers.values.top[0],'');
  } finally { resetGlobals(before); }
});

function beginTest(app) {
  app.click('action','start-test');
}

test('WP2 snapshot session opens INT01 instead of the session introduction', async () => {
  const before = restoreGlobals();
  const index = screens.indexOf('INT01');
  assert.equal(index, 1);
  const storage = disk(null);
  try {
    const location = { hash:'#path' };
    const clean = await mount(storage, location, 'wp2-before-snapshot');
    clean.click('action', 'continue');
    assert.match(clean.root.innerHTML, /class="session-intro"/);
    await prepareQuizSnapshot();
    assert.equal(location.hash, '#test');
    const app = await mount(storage, location, 'wp2-question');
    const html = app.root.innerHTML;
    assert.match(html, /<strong>ПРОФТЕСТ<\/strong>/);
    assert.match(html, /<strong>01<\/strong><span>из 15 · Часть 1А · Интересы<small>1%<\/small>/);
    assert.match(html, /role="progressbar"[^>]*aria-valuenow="1"/);
    assert.match(html, /<h1 tabindex="-1">Интересы<\/h1>/);
    assert.match(html, /name="INT01" value="A"/);
    assert.match(html, /name="INT01" value="B"/);
    assert.match(html, /class="choice-letter" aria-hidden="true">А<\/span>/);
    assert.match(html, /class="choice-letter" aria-hidden="true">Б<\/span>/);
    assert.match(html, /data-action="back">← Назад<\/button>/);
    assert.match(html, /data-action="next" disabled><span>Далее<\/span>/);
    assert.doesNotMatch(html, /session-intro|Профориентационный тест|test-consent/);
    app.fire('change', { name:'INT01', value:'A' });
    assert.match(app.root.innerHTML, /name="INT01" value="A" checked/);
    assert.match(app.root.innerHTML, /data-action="next"><span>Далее<\/span>/);
    assert.equal(JSON.parse(storage.value).answers.pairs.INT01, 'A');
    const reloaded = await mount(storage, location, 'wp2-question-reload');
    assert.match(reloaded.root.innerHTML, /name="INT01" value="A" checked/);
    assert.doesNotMatch(reloaded.root.innerHTML, /session-intro/);
  } finally { resetGlobals(before); }
});
