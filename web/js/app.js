import { loadState, saveState } from './store.js';
import { renderQuiz, renderSessionIntro } from './ui/quiz.js';
import { quizCompletion, scoringAnswers, isScreenComplete } from './domain/completion.js';
import { parseConfig } from './domain/config.js';
import { compute } from './domain/scoring.js';
import { renderResult } from './ui/result.js';
import { brandHeader } from './ui/brand.js';
import { renderOpportunities, renderSimilar } from './ui/opportunities.js';
import { renderSort } from './ui/sort.js';
import { renderLoadError, renderLoading } from './ui/states.js';
import { renderDetail } from './ui/detail.js';
import { renderSaved } from './ui/saved.js';
import { catalogResult, matchPercent, opportunityClosed } from './domain/catalog.js';
import { localDate } from './domain/local-date.js';
import { renderPath } from './ui/path.js';
import { renderMainNav } from './ui/nav.js';
import { renderDeadlines } from './ui/deadlines.js';
import { renderProfile } from './ui/profile.js';
import { renderGames, bindGames } from './ui/games.js';

const root = document.querySelector('#app');
const loadJson = async name => (await fetch(`./data/${name}.json`)).json();
const loadData = async () => ({ config:parseConfig(await loadJson('test_config')), catalog:await loadJson('catalog'), extra:await loadJson('catalog_extra').catch(() => []) });
const retryClicked = () => new Promise(resolve => { root.onclick = event => event.target.closest('[data-action="retry"]') && resolve(); });
let data;
while (!data) {
  root.innerHTML = renderLoading();
  try { data = await loadData(); } catch { root.innerHTML = renderLoadError(); await retryClicked(); }
}
root.onclick = null;
const { config } = data;
// Автоудаление: завершившиеся возможности не показываются. Дополнительные позиции лежат в data/catalog_extra.json,
// основной каталог (data/catalog.json) — побайтная копия из Android, поэтому его файл не правим.
const catalog = [...data.catalog, ...(Array.isArray(data.extra) ? data.extra : [])].filter(item => !opportunityClosed(item, localDate()));
const partNames = { profile:'Часть 0 · Знакомство', interests:'Часть 1А · Интересы', values:'Часть 1Б · Ценности', open:'Часть 1В · Вопросы о себе', sphere_interest:'Часть 1Г · Интерес к сферам', tasks:'Часть 2А · Мини-задания', experience:'Часть 2Б · Реальный опыт', constraints:'Часть 3 · Реальность', needs:'Часть 4А · Условия хорошей работы', happiness:'Часть 4Б · Представление о счастье', consistency:'Часть 5' };
const screens = config.parts.flatMap(part => {
  if (part.id === 'profile' || part.id === 'values') return [{ id:part.id, part:part.id, kind:part.id, questions:part.questions }];
  if (part.id === 'needs') return part.steps.map(step => ({ id:`needs/${step.id}`, part:part.id, kind:step.type === 'pick3' ? 'pick3' : 'questions', step, questions:part.questions.filter(q => (step.question_ids || []).includes(q.id)) }));
  return part.questions.map(question => ({ id:question.id, part:part.id, kind:'question', question }));
});
let state = loadState(localStorage, config.version);
delete state.sortChoice;
state.favorites = state.favorites.filter(id => catalog.some(item => item.id === id));
state.index = Math.min(state.index, screens.length - 1);
const save = () => saveState(state);
const answerGroups = { interests:'pairs', sphere_interest:'interest', tasks:'tasks', constraints:'nad', consistency:'checks', open:'open', happiness:'open' };
let restoreExitFocus=false, focusScreenAfterDraw=false, detailFromRoute=null, lastScreenKey='', lastProgress=0, prevRoute='', currentRoute=location.hash;
// Анимация входа — только при смене экрана, а не при каждой перерисовке (выбор ответа, ввод в поиск)
const screenKey=()=>`${location.hash}|${state.testStarted?state.index:'intro'}`;
function playEnter() {
  const key=screenKey();
  if(key===lastScreenKey||!root.classList) return;
  lastScreenKey=key;
  root.classList.remove('is-entering'); void root.offsetWidth; root.classList.add('is-entering');
  clearTimeout(playEnter.timer); playEnter.timer=setTimeout(()=>root.classList.remove('is-entering'),1600);
}
function savedConfig() {
  try {
    const saved=parseConfig(state.savedConfig);
    const record=value=>value!==null&&typeof value==='object'&&!Array.isArray(value);
    const strings=value=>record(value)&&Object.values(value).every(text=>typeof text==='string');
    const table=value=>Array.isArray(value)&&value.every(row=>strings(row));
    const groups=['profile','interests','values','open','sphere_interest','tasks','experience','constraints','needs','happiness','consistency'];
    const parts=groups.every(id=>saved.parts.some(part=>part.id===id));
    const questions=groups.every(group=>Array.isArray(saved.questions[group])&&saved.questions[group].every(question=>record(question)&&strings(question.fields||{})&&Array.isArray(question.options)&&question.options.every(option=>record(option)&&typeof option.id==='string'&&typeof option.text==='string')));
    const spheres=saved.spheres.length>0&&saved.spheres.every(sphere=>record(sphere)&&typeof sphere.id==='string'&&strings(sphere.fields||{})&&strings(sphere.details||{}));
    const texts=record(saved.texts)&&table(saved.texts.spheres?.table)&&table(saved.texts.riasec?.table)&&Array.isArray(saved.texts.disclaimer?.paragraphs)&&saved.texts.disclaimer.paragraphs.every(text=>typeof text==='string')&&Array.isArray(saved.texts.experiments?.paragraphs)&&saved.texts.experiments.paragraphs.every(text=>typeof text==='string');
    const resultVersion=state.savedResult?.configVersion??state.configVersion;
    return saved.version===resultVersion&&parts&&questions&&spheres&&texts ? saved : null;
  }
  catch { return null; }
}
function ensureResult() {
  const saved=state.savedResult;
  const resultVersion=saved?.configVersion??state.configVersion;
  const snapshot=savedConfig();
  const resultConfig=resultVersion===config.version?config:snapshot;
  const sphereIds=resultConfig?.spheres.map(sphere=>sphere.id)||[];
  const riasecIds=['R','I','A','S','E','C'], domainIds=['logic','numbers','text','space','people'];
  const taskCounts=Object.fromEntries(domainIds.map(id=>[id,(resultConfig?.questions.tasks||[]).filter(question=>question.domain===id).length]));
  const numberMap=(value,ids,maxima)=>value&&typeof value==='object'&&!Array.isArray(value)&&Object.keys(value).length===ids.length&&ids.every(id=>Number.isInteger(value[id])&&value[id]>=0&&value[id]<=maxima[id]);
  const labelMap=(value,ids)=>value&&typeof value==='object'&&!Array.isArray(value)&&Object.keys(value).length===ids.length&&ids.every(id=>typeof value[id]==='string');
  const maxima=Object.fromEntries(riasecIds.map(id=>[id,resultConfig?.questions.interests.filter(question=>question.type_a===id||question.type_b===id).length||0]));
  const valid = resultConfig && resultVersion===resultConfig.version && Array.isArray(saved?.spheres) && saved.spheres.length===sphereIds.length && saved.spheres.every(item=>item&&typeof item==='object'&&sphereIds.includes(item.id)&&Number.isInteger(item.total)&&item.total>=0&&item.total<=71&&Number.isInteger(item.percent)&&item.percent>=0&&item.percent<=100&&item.percent===Math.max(0,Math.floor(item.total/71*100+0.5))&&Array.isArray(item.flags)&&item.flags.every(flag=>flag&&typeof flag.text==='string')&&Array.isArray(item.professions)&&item.professions.every(role=>role&&typeof role.name==='string'&&Number.isInteger(role.percent)&&role.percent>=0&&role.percent<=100))&&new Set(saved.spheres.map(item=>item.id)).size===sphereIds.length&&Array.isArray(saved.topSpheres)&&saved.topSpheres.length===3&&new Set(saved.topSpheres).size===3&&saved.topSpheres.every(id=>sphereIds.includes(id))&&saved.topSpheres.every((id,index)=>saved.spheres[index]?.id===id)&&numberMap(saved.riasec,riasecIds,maxima)&&labelMap(saved.riasecLevels,riasecIds)&&numberMap(saved.domains,domainIds,taskCounts)&&labelMap(saved.domainLevels,domainIds)&&typeof saved.riasecCode==='string'&&saved.riasecCode.length===3&&[...saved.riasecCode].every(id=>riasecIds.includes(id))&&typeof saved.label==='string'&&Array.isArray(saved.hints)&&saved.hints.every(hint=>hint&&typeof hint.id==='string'&&typeof hint.text==='string')&&Array.isArray(saved.equivalentPairs)&&saved.equivalentPairs.every(pair=>Array.isArray(pair)&&pair.length===2&&pair.every(id=>sphereIds.includes(id)));
  const flattened=flattenAnswers(state.answers);
  const recoverable=state.configVersion===config.version && quizCompletion(flattened,config);
  const result = valid ? {...saved,configVersion:resultVersion} : recoverable ? {...compute(scoringAnswers(flattened,config),config),computedAt:Date.now()} : null;
  if (!result) { state.result=null; state.savedResult=null; state.savedConfig=null; state.catalogResult=null; state.answers={}; state.index=0; state.configVersion=config.version; save(); location.hash='#path'; return null; }
  state.savedResult = { ...result };
  if(resultConfig===config&&!snapshot || resultConfig===null&&result.configVersion===config.version) state.savedConfig=config;
  state.catalogResult = catalogResult(state.savedResult);
  save();
  return state.savedResult;
}
const lastDots = new Map();
let lastDotsKey = '';
const draw = () => {
  // Перерисовка на том же экране (ответ, фильтр, ввод) сохраняет положение страницы — без скачка наверх
  const same=screenKey()===lastScreenKey, wantTop=focusScreenAfterDraw, y=window.scrollY||0;
  // Перерисовка того же экрана не должна заново проигрывать вход (иначе при быстром нажатии всё «мигает»)
  if(same) { clearTimeout(playEnter.timer); root.classList?.remove('is-entering'); }
  drawScreen();
  if(same&&!wantTop&&y>0) window.scrollTo?.({top:y,behavior:'instant'});
  playEnter();
};
const drawScreen = () => {
  let route = location.hash || '#path';
  if (!location.hash || route === '#home') { location.hash = '#path'; route = '#path'; }
  const knownRoute = route === '#path' || route === '#deadlines' || route === '#profile' || route === '#catalog' || route === '#sort' || route === '#games' || route.startsWith('#games/') || route === '#saved' || route === '#result' || route === '#test' || route === '#opportunities' || route === '#opportunities?recommended' || route.startsWith('#detail/') || route.startsWith('#similar/');
  if (!knownRoute) { location.hash = '#path'; route = '#path'; }
  if (route === '#path') {
    const result=state.result==='complete'?ensureResult():null;
    root.innerHTML = renderPath({ hasDraft:state.result !== 'complete' && (state.index > 0 || Object.keys(state.answers).length > 0), hasResult:Boolean(result), result, config:result ? (savedConfig()||config) : config });
    return;
  }
  if (route === '#games' || route.startsWith('#games/')) {
    root.innerHTML = renderGames(route);
    return;
  }
  if (route === '#deadlines') {
    root.innerHTML = renderDeadlines({ items:catalog, favorites:state.favorites });
    return;
  }
  if (route === '#profile') {
    root.innerHTML = renderProfile({ result:state.result === 'complete' ? ensureResult() : null, answers:state.answers, hasDraft:state.result!=='complete'&&(state.index>0||Object.values(state.answers).some(value=>value&&typeof value==='object'&&Object.keys(value).length>0)) });
    return;
  }
  if (route === '#catalog') { location.hash = '#opportunities?recommended'; return; }
  if (route === '#sort') {
    if (state.sortingRoute !== '#opportunities?recommended') state.sortingRoute = '#opportunities';
    if (state.result === 'complete') ensureResult();
    if (state.sortChoice !== 'date' && state.sortChoice !== 'interest') state.sortChoice = state.catalogSort === 'date' ? 'date' : state.catalogResult ? 'interest' : 'date';
    root.innerHTML = renderSort({ choice:state.sortChoice, hasResult:Boolean(state.catalogResult) });
    return;
  }
  if (route.startsWith('#opportunities') || route === '#saved' || route.startsWith('#detail/') || route.startsWith('#similar/')) {
    if (route.startsWith('#opportunities') && state.result === 'complete') ensureResult();
    const courseQuestion=config.parts.find(part=>part.id==='profile')?.questions.find(question=>question.id==='PRF02');
    const courseAnswer=state.answers.profile?.PRF02||'';
    const course=courseQuestion?.options.find(option=>(option.id||option)===courseAnswer)?.text||courseAnswer;
    const shared = { items:catalog, favorites:state.favorites, result:state.catalogResult, query:state.catalogQuery || '', type:state.catalogType || '', sort:state.catalogSort || (state.catalogResult?'interest':'date'), course, city:state.answers.profile?.PRF04||'', locationPreference:state.answers.nad?.NAD02||'' };
    try { root.innerHTML = route === '#saved' ? renderSaved(shared) : route.startsWith('#detail/') ? renderDetail({ item:catalog.find(item => item.id === decodeURIComponent(route.slice(8))), favorites:state.favorites, matchPercent:state.catalogResult&&catalog.find(item=>item.id===decodeURIComponent(route.slice(8)))?matchPercent(catalog.find(item=>item.id===decodeURIComponent(route.slice(8))),state.catalogResult):null, backRoute:['#saved','#deadlines','#opportunities','#opportunities?recommended'].includes(detailFromRoute)?detailFromRoute:'#opportunities' }) : route.startsWith('#similar/') ? renderSimilar({ item:catalog.find(item => item.id === decodeURIComponent(route.slice(9))), items:catalog, favorites:state.favorites }) : renderOpportunities({ ...shared, recommended: route.includes('recommended') }); }
    catch (error) { if (!(error instanceof URIError)) throw error; location.hash='#opportunities'; }
    return;
  }
  if (location.hash === '#result' && state.result === 'complete') {
    const result = ensureResult();
    if (!result) { root.innerHTML=renderPath({hasDraft:false,hasResult:false,result:null,config}); return; }
    const resultConfig=savedConfig()||config;
    root.innerHTML=renderResult({result,config:resultConfig,answers:scoringAnswers(flattenAnswers(state.answers),resultConfig),computedAt:result.computedAt}).replace('<header class="brand">Воплощение</header>',brandHeader()) + renderMainNav('path');
    return;
  }
  if(route==='#test'&&!state.testStarted) {
    const continuing=state.index>0||Object.values(state.answers).some(value=>value&&typeof value==='object'&&Object.keys(value).length>0);
    root.innerHTML=renderSessionIntro({continuing,backRoute:prevRoute&&prevRoute!=='#test'&&prevRoute!=='#result'?prevRoute:'#path'});
    return;
  }
  const screen = screens[state.index];
  if (!screen) return;
  const partScreens=screens.filter(item=>item.part===screen.part);
  const partIndex=partScreens.findIndex(item=>item.id===screen.id);
  const percent=Math.floor(state.index/screens.length*100);
  // Перерисовка на том же вопросе (выбор ответа, ввод) не должна сбрасывать прокрутку списка
  const keepScroll=screenKey()===lastScreenKey?root.querySelector?.('.quiz-scroll')?.scrollTop:0;
  root.innerHTML = renderQuiz({ ...state, screen, index:partIndex, total:partScreens.length, globalIndex:state.index, globalTotal:screens.length, partName:partNames[screen.part] || screen.part, partNumber:state.index+1, percent, canAdvance:canAdvance(screen) });
  if(keepScroll>0) { const box=root.querySelector?.('.quiz-scroll'); if(box) box.scrollTop=keepScroll; }
  // Линейка прогресса дорастает от прошлого значения до текущего (см. css/polish.css, track-grow)
  const track=root.querySelector?.('.quiz-screen .track span');
  if(track?.style&&percent>0) track.style.setProperty('--from',`${Math.max(0,Math.min(100,100-lastProgress/percent*100))}%`);
  lastProgress=percent;
  if(!capturing) syncDots();
  if(state.showExit) root.querySelector?.('[data-action="cancel-exit"]')?.focus({focusVisible:false});
  if(restoreExitFocus) { root.querySelector?.('[data-action="exit"]')?.focus(); restoreExitFocus=false; }
  if(focusScreenAfterDraw) { root.querySelector?.('h1')?.focus(); window.scrollTo?.({top:0,behavior:'instant'}); focusScreenAfterDraw=false; }
};
function setAnswer(name, value) {
  const screen = screens[state.index];
  const q = screen.question || screen.questions?.find(item => item.id === name);
  if (name.startsWith('profile.')) {
    const id = name.slice(8); state.answers.profile ||= {}; state.answers.profile[id] = value;
  } else if (name === 'values-top' || name === 'values-least') {
    state.answers.values ||= { top:[], least:[] };
    const key = name === 'values-top' ? 'top' : 'least';
    const target = state.answers.values[key];
    state.answers.values[key] = target.includes(value) ? target.filter(id => id !== value) : [...target, value];
  } else if (name.startsWith('values-rank-')) {
    state.answers.values ||= { top:[], least:[] };
    const rank = Number(name.slice('values-rank-'.length)) - 1;
    const top = Array.from({ length:3 }, (_, index) => state.answers.values.top?.[index] || '');
    const occupied = top.indexOf(value);
    if (occupied >= 0 && occupied !== rank) top[occupied] = '';
    top[rank] = top[rank] === value ? '' : value;
    state.answers.values.top = top;
  } else if (name === 'needs-top3') {
    const target = state.answers.needs?.top3 || [];
    state.answers.needs ||= { ratings:{}, top3:[] };
    state.answers.needs.top3 = target.includes(value) ? target.filter(id => id !== value) : [...target, value];
  } else if (screen.kind === 'pick3' || screen.kind === 'values') return;
  else if (screen.part === 'needs') { state.answers.needs ||= {ratings:{},top3:[]}; state.answers.needs.ratings[q.id] = Number(value); }
  else if (screen.part === 'experience') {
    const current = state.answers.experience?.[q.id] || {text:'',energy:0};
    const next = name.endsWith('_energy') ? { ...current, energy:Number(value) } : { ...current, text:value };
    state.answers.experience ||= {}; state.answers.experience[q.id] = next;
  } else {
    const group = answerGroups[screen.part] || screen.part;
    state.answers[group] ||= {};
    state.answers[group][q.id] = value;
  }
  state.error = '';
  save();
}
function canAdvance(screen) {
  return isScreenComplete(screen,state.answers,config);
}
root.addEventListener('change', event => {
  const input=event.target;
  if (input.name === 'catalog-query') return;
  if (input.name === 'sort') { state.sortChoice = input.value; save(); draw(); return; }
  if (!input.name) return;
  const name=input.name,value=input.value;
  setAnswer(name,value);
  if(redrawKeepingField(input)) { markPicked(input); return; }
  draw();
  const replacement=[...(root.querySelectorAll?.('input')||[])].find(field=>field.name===name&&field.value===value);
  replacement?.focus({focusVisible:false,preventScroll:true});
  markPicked(replacement);
});
// Перерисовка без пересоздания сфокусированного поля: экран собирается как обычно, но HTML перехватывается и «вливается» в живой DOM.
// Поле и его предки остаются теми же узлами — не теряется фокус и клавиатура на телефоне, а выбор варианта плавно анимируется CSS-переходами.
function syncAttributes(live, fresh) {
  for (const { name, value } of [...fresh.attributes]) if (live.getAttribute(name) !== value) live.setAttribute(name, value);
  for (const { name } of [...live.attributes]) if (!fresh.hasAttribute(name)) live.removeAttribute(name);
}
function morphKeeping(live, fresh, field) {
  if (live === field) return true;
  if (live.nodeType === 3) { if (live.nodeValue !== fresh.nodeValue) live.nodeValue = fresh.nodeValue; return true; }
  if (live.nodeType !== 1 || live.tagName !== fresh.tagName) return false;
  syncAttributes(live, fresh);
  const a = [...live.childNodes], b = [...fresh.childNodes];
  const n = Math.min(a.length, b.length);
  const compatible = a.slice(0, n).every((node, i) => node.nodeType === b[i].nodeType && (node.nodeType !== 1 || node.tagName === b[i].tagName));
  if (!compatible) { if (live.contains(field)) return false; live.replaceChildren(...b); return true; }
  // лишние хвостовые узлы убираем/добавляем, общие — вливаем (так живые кнопки и шкалы плавно меняют состояние)
  if (a.slice(n).some(node => field && node.contains(field))) return false;
  a.slice(n).forEach(node => node.remove());
  b.slice(n).forEach(node => live.appendChild(node));
  return a.slice(0, n).every((node, i) => morphKeeping(node, b[i], field));
}
let capturing = false, dotsFrom = [];
function redrawKeepingField(field = null) {
  if (typeof document === 'undefined' || !document.createElement || (field && !field.isConnected) || !root.firstElementChild) return false;
  let html = null;
  Object.defineProperty(root, 'innerHTML', { configurable: true, get: () => '', set: value => { html = value; } });
  capturing = true;
  try { draw(); } finally { capturing = false; delete root.innerHTML; }
  if (html === null) return true;
  const scratch = document.createElement('div');
  scratch.innerHTML = html;
  const ok = scratch.firstElementChild && morphKeeping(root.firstElementChild, scratch.firstElementChild, field);
  if (ok) syncDots();
  return Boolean(ok);
}
// Растровая шкала под выбором 1–10 «доезжает» от прошлого значения до нового, а не рисуется с нуля
function syncDots() {
  const key = screenKey();
  if (key !== lastDotsKey) { lastDots.clear(); lastDotsKey = key; }
  dotsFrom = [];
  [...(root.querySelectorAll?.('.quiz-screen .scale-dots i') || [])].forEach((dots, index) => {
    if (!dots?.style) return;
    const width = parseFloat(dots.style.width) || 0;
    dotsFrom[index] = lastDots.get(index) ?? 0;
    lastDots.set(index, width);
    dots.style.setProperty('--from', `${dotsFrom[index]}%`);
  });
}
function markPicked(input) {
  if (!input?.checked) return;
  const dots = input.closest?.('.scale-choices')?.nextElementSibling;
  if (!dots?.classList?.contains('scale-dots')) return;
  dots.classList.remove('is-moving'); void dots.offsetWidth; dots.classList.add('is-moving');
}
root.addEventListener('input', event => {
  const input=event.target;
  if (input.name === 'catalog-query') {
    state.catalogQuery = input.value;
    save();
    if(redrawKeepingField(input)) return;
    const position = input.selectionStart;
    draw();
    const updated = root.querySelector('[name="catalog-query"]');
    updated?.focus({preventScroll:true}); updated?.setSelectionRange(position, position);
    return;
  }
  if(input.tagName!=='TEXTAREA') return;
  setAnswer(input.name,input.value);
  if(redrawKeepingField(input)) return;
  const position=input.selectionStart;
  draw();
  const updated=[...(root.querySelectorAll?.('textarea')||[])].find(field=>field.name===input.name);
  updated?.focus({preventScroll:true});
  updated?.setSelectionRange(position,position);
});
root.addEventListener('click', event => {
  const rankInput=event.target.closest('input[type="radio"][name^="values-rank-"]');
  if(rankInput?.checked) { event.preventDefault(); setAnswer(rankInput.name,rankInput.value); draw(); return; }
  const route = event.target.closest('[data-route]')?.dataset.route;
  if (route) { if (route.startsWith('#detail/')) detailFromRoute=location.hash; location.hash = route; return; }
  const itemId = event.target.closest('[data-item]')?.dataset.item;
  if (itemId) { detailFromRoute=location.hash; location.hash = `#detail/${encodeURIComponent(itemId)}`; return; }
  const saveId = event.target.closest('[data-save]')?.dataset.save;
  if (saveId) { state.favorites = state.favorites.includes(saveId) ? state.favorites.filter(id => id !== saveId) : [...state.favorites, saveId]; save(); draw(); return; }
  const type = event.target.closest('[data-type]')?.dataset.type;
  if (type !== undefined) { state.catalogType = type; save(); draw(); return; }
  const action=event.target.closest('[data-action]')?.dataset.action;
  if(!action) return;
  if(action==='start-test') { state.testStarted=true; state.testConsent=false; save(); draw(); root.querySelector?.('[data-action="next"]')?.focus(); return; }
  if(action==='next') {
    if(!canAdvance(screens[state.index])) { state.error='Заполните обязательные поля.'; save(); draw(); return; }
    if(state.index===screens.length-1) {
      if(!quizCompletion(flattenAnswers(state.answers),config)) { state.error='Проверьте обязательные ответы теста.'; save(); draw(); return; }
      state.result='complete'; state.configVersion=config.version; state.savedResult={...compute(scoringAnswers(flattenAnswers(state.answers),config),config),computedAt:Date.now()}; state.savedConfig=config;
      state.savedResult.configVersion = config.version;
      state.catalogResult = catalogResult(state.savedResult);
      location.hash='#result';
    } else { state.index++; focusScreenAfterDraw=true; }
  }
  if(action==='back') { state.index=Math.max(0,state.index-1); focusScreenAfterDraw=true; }
  if(action==='exit') state.showExit=true;
  if(action==='cancel-exit') { state.showExit=false; restoreExitFocus=true; }
  if(action==='confirm-exit') { state.showExit=false; location.hash='#path'; }
  if(action==='continue') { if(state.configVersion!==config.version) { state.configVersion=config.version; state.answers={}; state.index=0; } location.hash='#test'; }
  if(action==='reset-test') {
    state.answers = {}; state.index = 0; state.configVersion=config.version; state.result = null; state.savedResult = null; state.savedConfig=null; state.catalogResult = null; state.testStarted=false; state.testConsent=false;
    save(); location.hash = '#test'; draw(); return;
  }
  if(action==='apply-sort') { state.catalogSort = state.sortChoice || (state.catalogResult?'interest':'date'); delete state.sortChoice; location.hash = state.sortingRoute || '#opportunities'; delete state.sortingRoute; }
  if(action==='reset-filters') { state.catalogQuery = ''; state.catalogType = ''; }
  if(action==='reset-type') state.catalogType='';
  if(action==='reset-recommended') { state.catalogQuery = ''; state.catalogType = ''; location.hash='#opportunities'; }
  if(action==='catalog') location.hash='#opportunities?recommended';
  if(action==='internships') { state.catalogType='internship'; location.hash='#opportunities?recommended'; }
  save(); draw();
});
root.addEventListener('keydown',event=>{
  if(!state.showExit) return;
  if(event.key==='Escape') { event.preventDefault(); state.showExit=false; restoreExitFocus=true; save(); draw(); return; }
  if(event.key!=='Tab') return;
  const buttons=[...(root.querySelectorAll?.('.dialog button')||[])];
  if(!buttons.length) return;
  const first=buttons[0],last=buttons.at(-1);
  if(event.shiftKey&&document.activeElement===first) { event.preventDefault(); last.focus(); }
  else if(!event.shiftKey&&document.activeElement===last) { event.preventDefault(); first.focus(); }
});
bindGames({ root, redraw:draw, morph:() => redrawKeepingField() });
window.addEventListener('hashchange',()=>{ prevRoute=currentRoute; currentRoute=location.hash; if(location.hash==='#sort') { if (!state.sortingRoute) state.sortingRoute=root.innerHTML.includes('<h1>Подборка для вас</h1>')?'#opportunities?recommended':'#opportunities'; save(); } else { delete state.sortChoice; if (location.hash !== (state.sortingRoute || '')) delete state.sortingRoute; save(); } draw(); });
function flattenAnswers(a) {
  const out={ profile:JSON.stringify(a.profile||{}), values:JSON.stringify(a.values||{}), 'needs/pick3':JSON.stringify(a.needs?.top3||[]) };
  for(const [id,value] of Object.entries(a.needs?.ratings||{})) out[id]=value;
  for(const key of ['pairs','interest','tasks','checks','nad','open']) Object.assign(out,a[key]||{});
  for(const [id,value] of Object.entries(a.experience||{})) out[id]=JSON.stringify(value);
  return out;
}
draw();
