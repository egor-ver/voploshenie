import { escapeHtml } from '../escape.js';
import { brandHeader } from './brand.js';
import { dotsHtml } from './dots.js';

const value = (q, name) => q.fields?.[name] || '';
const promptFor = q => q.prompt || value(q, 'Утверждение') || value(q, 'Вопрос') || value(q, 'Поле') || value(q, 'Условие') || value(q, 'Текст') || 'Интересы';
const optionsFor = q => (q.options || []).map((option, i) => {
  const value = typeof option === 'string' ? { id: q.type === 'single_choice' ? String.fromCharCode(97 + i) : String(i), text: option } : option;
  return q.id === 'PRF06' ? { ...value, text:value.text.replace(/\s*\([^)]*200[^)]*\)$/i, '') } : value;
});
const letters = 'АБВГДЕЖЗИКЛМНОПРСТУФХЦЧШЩЭЮЯ';
const choice = (name, id, text, selected, type = 'radio', disabled = false, letter = '') => `<label class="choice${letter?' choice-row':''}${disabled?' is-disabled':''}"><input type="${type}" name="${escapeHtml(name)}" value="${escapeHtml(id)}"${(Array.isArray(selected) ? selected.includes(id) : String(selected) === String(id)) ? ' checked' : ''}${disabled?' disabled':''}>${letter?`<span class="choice-letter" aria-hidden="true">${escapeHtml(letter)}</span>`:''}<span>${escapeHtml(text)}</span></label>`;
const scaleDots = (scale, selected) => {
  const picked = selected !== undefined && selected !== null && selected !== '' && Number.isFinite(Number(selected));
  const count = scale.max - scale.min + 1;
  return `<div class="scale-dots" aria-hidden="true"><i style="width:${picked ? (Number(selected) - scale.min + 1) / count * 100 : 0}%"></i></div>`;
};
const scaleChoices = (name, scale, selected, minimum = String(scale.min), maximum = String(scale.max)) => `<div class="scale-choices${scale.max-scale.min+1>5?' scale-choices--wide':''}">${Array.from({length:scale.max-scale.min+1},(_,i)=>choice(name,String(scale.min+i),String(scale.min+i),selected)).join('')}</div>${scaleDots(scale, selected)}<div class="scale-labels"><span>${escapeHtml(minimum)}</span><span>${escapeHtml(maximum)}</span></div>`;
const counter = (text, limit) => `<span class="text-counter" aria-live="polite">${text.length} / ${escapeHtml(limit)}</span>`;

export function renderSessionIntro({ continuing = false, backRoute = '#path' } = {}) {
  return `<section class="session-intro"><div class="intro-top">${brandHeader()}<button class="intro-back" data-route="${escapeHtml(backRoute)}"><svg aria-hidden="true" viewBox="0 0 24 24"><path d="M20 12H4m7-7-7 7 7 7"/></svg><span>Назад</span></button></div><h1 class="p-title">Профориентационный тест</h1><div class="intro-card"><span class="p-kicker">О тесте</span><div class="intro-time"><b aria-hidden="true">45–55</b><span class="visually-hidden">45–55 минут</span><span aria-hidden="true">минут, можно прерваться</span></div><p class="prompt">Результат — это гипотезы для проверки, а не вердикт о вашей профессии</p></div><span class="p-cta"><button class="primary" data-action="start-test">${continuing?'Продолжить':'Начать'}</button></span></section>`;
}

export function renderQuiz(state) {
  const { screen, index = 0, total = 1, answers = {}, partName = '' } = state;
  if (!screen) return '<p>Вопрос не найден</p>';
  let controls = '';
  if (screen.kind === 'values') {
    const values = screen.questions, limits=values[0]?.ranking||{top:3,least:2}, top=answers.values?.top||[], least=answers.values?.least||[];
    controls = `<p class="choice-counter">Осталось выбрать: важных — ${Math.max(0,limits.top-top.filter(Boolean).length)}, наименее важных — ${Math.max(0,limits.least-least.length)}</p>${values.map(q => `<article class="rank-value"><strong>${escapeHtml(value(q, 'Название'))}</strong><p>${escapeHtml(value(q,'Пояснение на карточке'))}</p><div role="group" aria-label="${escapeHtml(value(q, 'Название'))}">${Array.from({length:limits.top},(_,index)=>{const rank=index+1,selected=top[index]===q.id,slot=top[index];return choice(`values-rank-${rank}`,q.id,String(rank),slot,'radio',!selected&&(least.includes(q.id)||top.includes(q.id)||Boolean(slot)));}).join('')}</div>${choice('values-least', q.id, 'наименее важно', least, 'checkbox',!least.includes(q.id)&&(top.includes(q.id)||least.length>=limits.least))}</article>`).join('')}`;
  } else if (screen.kind === 'pick3') {
    const selected=answers.needs?.top3||[], limit=screen.step.max??screen.step.min??3;
    controls = `<p class="choice-counter">Осталось выбрать: ${Math.max(0,limit-selected.length)}</p>${screen.step.options.map(o=>choice('needs-top3',o.id,o.text,selected,'checkbox',!selected.includes(o.id)&&selected.length>=limit)).join('')}`;
  }
  else if (screen.kind === 'profile') controls = screen.questions.map(q => {
    if (q.type === 'text') { const limit=q.id==='PRF01'?60:120, text=answers.profile?.[q.id]||''; return `<label class="field-label">${escapeHtml(value(q, 'Поле'))}<textarea class="field" name="profile.${escapeHtml(q.id)}" maxlength="${limit}">${escapeHtml(text)}</textarea>${counter(text,limit)}</label>`; }
    if (q.type === 'scale') return `<fieldset><legend>${escapeHtml(value(q, 'Поле'))}</legend>${scaleChoices(`profile.${q.id}`,q.scale,answers.profile?.[q.id])}</fieldset>`;
    return `<fieldset><legend>${escapeHtml(value(q, 'Поле'))}</legend>${optionsFor(q).map((o,i)=>choice(`profile.${q.id}`,o.id,o.text,answers.profile?.[q.id],'radio',false,letters[i])).join('')}${q.id==='PRF06'&&String(answers.profile?.PRF06)===String(optionsFor(q).at(-1)?.id)?`<label class="field-label profile-other">другое<textarea class="field" name="profile.PRF06_other" maxlength="200">${escapeHtml(answers.profile?.PRF06_other||'')}</textarea>${counter(answers.profile?.PRF06_other||'',200)}</label>`:''}</fieldset>`;
  }).join('');
  else if (screen.kind === 'questions') {
    controls = screen.questions.map(q => `<fieldset><legend>${escapeHtml(value(q,'Условие'))}</legend>${scaleChoices(q.id,q.scale,answers.needs?.ratings?.[q.id],'совсем не важно','критически важно')}</fieldset>`).join('');
  } else {
    const q = screen.question;
    if (q.type === 'likert5' || q.type === 'scale' || q.type === 'energy5' || q.type === 'scale10') {
      const scale = q.scale || q.energy;
      const minimum = q.type === 'energy5' ? 'выжало' : q.id.startsWith('NEED') ? 'совсем не важно' : q.type === 'likert5' ? 'совсем не интересно' : String(scale.min);
      const maximum = q.type === 'energy5' ? 'зарядило' : q.id.startsWith('NEED') ? 'критически важно' : q.type === 'likert5' ? 'очень интересно' : String(scale.max);
      controls = scaleChoices(q.id,scale,answerFor(answers,screen),minimum,maximum);
    }
    else if (q.type === 'pair') controls = ['A','B'].map((side,i)=>choice(q.id,side,value(q,`Вариант ${letters[i]}`),answerFor(answers,screen),'radio',false,letters[i])).join('');
    else if (q.type === 'single_choice') controls = optionsFor(q).map((o,i)=>choice(q.id,o.id,o.text,answerFor(answers,screen),'radio',false,letters[i])).join('');
    else if (q.type === 'text') { const limit=q.max||1000, text=answerFor(answers,screen)||''; controls = `<label class="field-label"><textarea class="field" aria-label="${escapeHtml(promptFor(q))}" name="${escapeHtml(q.id)}" maxlength="${escapeHtml(limit)}">${escapeHtml(text)}</textarea>${counter(text,limit)}</label>`; }
    else if (q.type === 'text_energy5') { const entry = answerFor(answers,screen) || {}, limit=q.max||1000; controls = `<label class="field-label"><textarea class="field" aria-label="${escapeHtml(promptFor(q))}" name="${escapeHtml(q.id)}" maxlength="${escapeHtml(limit)}">${escapeHtml(entry.text || '')}</textarea>${counter(entry.text||'',limit)}</label>${scaleChoices(`${q.id}_energy`,q.energy,entry.energy,'выжало','зарядило')}`; }
  }
  const q=screen.question;
  const title = screen.kind === 'values' ? 'Ценности' : screen.kind === 'pick3' ? 'Условия хорошей работы' : screen.kind === 'profile' ? 'Знакомство' : screen.kind === 'questions' || q?.id?.startsWith('NEED') ? 'Насколько для вас важно в идеальной работе…' : q?.id?.startsWith('SPH') ? 'Мне интересно…' : promptFor(q);
  const isScale = ['likert5','scale','energy5','scale10','text_energy5'].includes(screen.question?.type) || screen.kind === 'questions';
  const instruction = screen.kind === 'profile' || screen.kind === 'questions' ? '' : screen.kind === 'values' ? 'Выберите три самые важные для вас ценности (1, 2, 3) и две наименее важные. Остальные оставьте без отметки' : screen.kind==='pick3' ? 'Выберите три самых важных условия.' : q?.type==='text'||q?.type==='text_energy5' ? `До ${q.max||1000} символов` : q?.id?.startsWith('TSK') ? 'Мини-задания' : q?.id?.startsWith('INT') ? 'В каждой паре выберите вариант, который вам ближе, даже если оба нравятся или оба не очень. Пропускать нельзя' : q?.id?.startsWith('CHK') ? 'Ещё пять коротких выборов. Отвечайте, как чувствуете сейчас: не пытайтесь вспомнить, что отвечали раньше' : q?.id?.startsWith('SPH') ? 'Интерес к сферам' : q?.id?.startsWith('NEED') ? 'Условия хорошей работы' : 'Выберите наиболее близкий вариант.';
  const hint = q?.id?.startsWith('TSK') || screen.kind==='questions' ? '' : '<p class="hint">Здесь нет правильных ответов.</p>';
  const complete=state.canAdvance??false;
  const globalIndex=state.globalIndex??index, globalTotal=state.globalTotal??total;
  const statement=q?.id?.startsWith('SPH')?value(q,'Утверждение'):q?.id?.startsWith('NEED')?value(q,'Условие'):'';
  const percent=Number.isFinite(state.percent)?Math.max(0,Math.min(100,state.percent)):null;
  const progress=percent??((index+1)/total*100);
  const exitDialog=state.showExit?'<div class="dialog" role="dialog" aria-modal="true" aria-labelledby="exit-title"><section><h2 id="exit-title">Выйти из теста?</h2><p class="dialog-text">Ответы сохранятся — можно вернуться и продолжить с этого вопроса.</p><div class="dialog-actions"><button class="dialog-btn" data-action="confirm-exit">Выйти</button><button class="dialog-btn dialog-btn--primary" data-action="cancel-exit">Продолжить</button></div></section></div>':'';
  return `<section class="quiz-screen${screen.kind==='questions'?' quiz-needs':''}"><div class="quiz-header"><strong>ПРОФТЕСТ</strong><button class="exit" data-action="exit">Закрыть ✕</button></div><div class="quiz-scroll"><div class="progress-meta"><strong>${escapeHtml(String(index+1).padStart(2,'0'))}</strong><span>${Number(total)>1?`из ${escapeHtml(total)} · `:''}${escapeHtml(partName)}${percent===null?'':`<small>${escapeHtml(percent)}%</small>`}</span></div><div class="track" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${escapeHtml(progress)}"><span style="width:${escapeHtml(progress)}%"></span></div><h1 tabindex="-1">${escapeHtml(title)}</h1>${statement?`<p class="question-statement">${escapeHtml(statement)}</p>`:''}${instruction?`<p class="prompt">${escapeHtml(instruction)}</p>`:''}<section class="answers${isScale?' has-scale':''}${screen.kind==='values'?' has-values':''}${screen.kind==='pick3'?' has-pick3':''}${screen.kind==='profile'?' has-profile':''}" role="group" aria-label="${escapeHtml(title)}">${controls}</section>${hint}${state.error?`<p class="error" role="alert">${escapeHtml(state.error)}</p>`:''}</div><nav class="actions"><button data-action="back"${globalIndex===0?' disabled':''}>← Назад</button><button class="primary" data-action="next"${complete?'':' disabled'}><span>${globalIndex===globalTotal-1?'Показать результат':'Далее'}</span><svg aria-hidden="true" viewBox="0 0 24 24"><path d="M4 12h16m-7-7 7 7-7 7"/></svg></button></nav>${exitDialog}</section>`;
}
function answerFor(answers, screen) {
  if (screen.part === 'profile') return answers.profile?.[screen.question.id];
  if (screen.part === 'needs') return answers.needs?.ratings?.[screen.question.id];
  if (screen.part === 'experience') return answers.experience?.[screen.question.id];
  if (screen.part === 'open' || screen.part === 'happiness') return answers.open?.[screen.question.id];
  const answer = answers[screen.part === 'interests' ? 'pairs' : screen.part === 'sphere_interest' ? 'interest' : screen.part === 'tasks' ? 'tasks' : screen.part === 'constraints' ? 'nad' : screen.part === 'consistency' ? 'checks' : screen.part]?.[screen.question.id];
  return answer;
}
