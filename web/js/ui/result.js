import { escapeHtml } from '../escape.js';
import { sphereName } from '../domain/sphere-name.js';
const meterHtml = percent => `<div class="result-meter" role="img" aria-label="${percent} %" style="--w:${percent}%"><i></i><i></i><i></i></div>`;
const pinwheel = '<span class="result-flower" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i></span>';

const field = (item, name) => item?.details?.[name] || item?.fields?.[name] || '';
const sphereHints = { H01:['biology'], H03:['business'], H05:['public_service','biology'] };
const domainNames = {logic:'Логика',numbers:'Числа',text:'Текст',space:'Пространство',people:'Люди'};

export function renderResult({ result, config, answers = {}, computedAt = result.computedAt }) {
  const spheres = new Map(config.spheres.map(item => [item.id, item]));
  const questionText = (item, key) => item?.fields?.[key] || '';
  const values = new Map((config.questions.values || []).map(item => [item.id, questionText(item, 'Название')]));
  const needs = new Map((config.questions.needs || []).map(item => [item.id, questionText(item, 'Условие')]));
  const heading = id => escapeHtml(sphereName(id) || field(spheres.get(id), 'Сфера') || id);
  const flags = item => (item?.flags || []).map(flag => `<p class="result-note">${escapeHtml(flag.text)}</p>`).join('');
  const hintsFor = id => result.hints.filter(hint => sphereHints[hint.id]?.includes(id)).map(hint => `<p class="result-note">${escapeHtml(hint.text)}</p>`).join('');
  const internship = (answers.profile || {}).PRF06 === 'ищу стажировку или практику';
  const top = result.topSpheres.map((id, index) => {
    const item = result.spheres.find(sphere => sphere.id === id);
    const equivalent = result.equivalentPairs?.some(pair => pair.includes(id));
    const roles = (item?.professions || []).slice(0, 3).map(role => `<li>${escapeHtml(role.name)}</li>`).join('');
    const percent = Math.max(0, Math.min(100, Number(item?.percent) || 0));
    const flower = index === 0 ? pinwheel : '';
    const kicker = index === 0 ? '<span class="p-kicker result-kicker">Лучшее совпадение</span>' : '';
    return `<li class="result-card"><div class="result-card-heading">${kicker}<strong>${heading(id)}</strong></div>${flower}<div class="result-percent">${meterHtml(percent)}<b>${percent}<small> %</small></b></div><p class="result-level">${escapeHtml(item?.level || '')}</p>${equivalent?'<p class="result-equal">Равноценные направления</p>':''}${flags(item)}${roles?`<div class="result-roles"><span class="p-kicker">Чем заниматься</span><ul>${roles}</ul></div>`:''}</li>`;
  });
  const cta = `<div class="actions p-cta"><button class="primary p-btn" data-action="${internship ? 'internships' : 'catalog'}"><span>Открыть подборку</span><span aria-hidden="true">→</span></button></div>`;
  const chart = `<h2>Все 7 сфер</h2>${result.spheres.map(item => `<div class="result-chart-item"><span>${heading(item.id)}</span><span class="result-chart-track"><span style="width:${Math.max(0,Math.min(100,Number(item.percent)||0))}%"></span></span><strong>${escapeHtml(item.percent)}%</strong></div>`).join('')}`;
  const domains = Object.entries(result.domainLevels || {}).map(([id, level]) => `<p>${escapeHtml(domainNames[id] || id)} — ${escapeHtml(String(level).toLowerCase())}</p>`).join('');
  const energy = Object.values(answers.experience || {}).filter(item => item?.text?.trim() && item.energy >= 4 && item.energy <= 5).map(item => `<p class="result-answer">${escapeHtml(item.text)}</p>`).join('');
  const valuesBlock = (answers.valuesTop3 || []).map(id => `<p>${escapeHtml(values.get(id) || id)}</p>`).join('');
  const anchor = result.anchor ? `<h3>Ведущий якорь</h3><p>${escapeHtml(result.anchor)}</p>` : '';
  const needsBlock = (answers.needs?.top3 || []).map(id => `<p>${escapeHtml(needs.get(id) || id)}</p>`).join('');
  const words = Object.entries(answers.open || {}).filter(([,text]) => typeof text === 'string' && text.trim()).map(([id,text]) => {
    const question = [...(config.questions.open || []), ...(config.questions.happiness || [])].find(item => item.id === id);
    return `<h3>${escapeHtml(questionText(question,'Текст') || questionText(question,'Вопрос') || id)}</h3><p class="result-answer">${escapeHtml(text)}</p>`;
  }).join('');
  const nad = config.questions.constraints.map(question => {
    const answer = answers.nad?.[question.id];
    const text = question.options?.find(option => option.id === answer)?.text || (question.id === 'NAD07' ? answer : '');
    return text ? `<p>${escapeHtml(questionText(question,'Вопрос'))}: ${escapeHtml(text)}</p>` : '';
  }).join('');
  const profile = answers.profile || {};
  const name = typeof profile.PRF01 === 'string' ? profile.PRF01.trim() : '';
  const date = Number.isFinite(Number(computedAt)) ? new Date(Number(computedAt)).toLocaleDateString('ru-RU',{day:'numeric',month:'long',year:'numeric'}) : '';
  const identity = [name,date].filter(Boolean).join(' · ');
  const specialty = typeof profile.PRF03 === 'string' ? profile.PRF03.trim() : '';
  const satisfaction = Number(profile.PRF05);
  const consistencyLine = specialty && Number.isInteger(satisfaction) && satisfaction >= 1 && satisfaction <= 10 && result.topSpheres.length ? `Специальность «${specialty}» · оценка ${satisfaction}/10 · топ-сферы: ${result.topSpheres.map(id => sphereName(id) || field(spheres.get(id),'Сфера') || id).join(', ')}` : '';
  const hasConsistency = Boolean(result.consistencyWarning || consistencyLine);
  const consistency = hasConsistency ? `<section class="result-block"><h2>Согласованность</h2>${result.consistencyWarning?`<p>${escapeHtml(result.consistencyWarning)}</p>`:''}${consistencyLine?`<p>${escapeHtml(consistencyLine)}</p>`:''}</section>` : '';
  const changeDirection = profile.PRF06 === 'хочу сменить направление';
  const best = Math.max(0,...Object.values(result.domains || {}));
  const strongest = Object.entries(result.domains || {}).filter(([,score]) => score > 0 && score === best).map(([id]) => (domainNames[id] || id).toLowerCase());
  const riasec = config.texts?.riasec?.table || [];
  const typeRows = Object.entries(result.riasec || {}).map(([type,score]) => {
    const row = riasec.find(item => String(item['Тип'] || '').startsWith(`${type} `));
    const typeName = String(row?.['Тип'] || type).slice(2);
    const description = row?.['Описание (1–2 предложения)'] || '';
    const percentage = Math.max(0,Math.min(100,Number(score)*20));
    return { type, score, typeName, description, level:String(result.riasecLevels?.[type] || '').toLowerCase(), percentage };
  });
  const typeDescriptions = typeRows.filter(({type}) => result.riasecCode.includes(type)).map(({typeName,level,description}) => `<p><strong>${escapeHtml(typeName)} — ${escapeHtml(level)}</strong><br>${escapeHtml(description)}</p>`).join('');
  const types = `${typeRows.map(({type,score,typeName,percentage}) => `<div class="riasec-type"><strong>${escapeHtml(type)}</strong><div class="riasec-bar" role="img" aria-label="${escapeHtml(typeName)}: ${escapeHtml(score)} из 5"><span style="height:${percentage}%;width:var(--full)"></span></div><span>${escapeHtml(score)} / 5</span></div>`).join('')}</div><div class="riasec-descriptions">${typeDescriptions}`;
  const entries = result.topSpheres.map(id => {
    const description = config.texts?.spheres?.table?.find(row => row['Сфера'] === field(spheres.get(id), 'Сфера'));
    const sphere = result.spheres.find(item => item.id === id);
    return description ? `<section class="result-block"><h2>Как войти и что попробовать</h2><h3>${heading(id)}</h3><strong>Вход и первые пробы</strong><p>${escapeHtml(description['Вход и первые пробы'] || '')}</p><strong>На что обратить внимание</strong><p>${escapeHtml(description['На что обратить внимание'] || '')}</p>${flags(sphere)}${hintsFor(id)}</section>` : '';
  }).join('');
  const disclaimer = config.texts?.disclaimer?.paragraphs?.[0]?.split('«').at(-1)?.split('»')[0] || '';
  const fullDisclaimer = config.texts?.disclaimer?.paragraphs?.at(-1)?.split('«').at(-1)?.split('»')[0] || '';
  return `<header class="brand"><img class="brand-mark" src="icons/logo_mark.svg" alt="">Воплощение<span class="p-kicker result-status">Профтест · готово</span></header><h1 class="p-title">Ваше направление</h1>${identity?`<p class="result-subtitle">${escapeHtml(identity)}</p>`:''}<p class="result-subtitle">${escapeHtml(disclaimer)}</p>${internship?cta:''}${changeDirection&&hasConsistency?consistency:''}<section class="result-lead"><h2>Топ-3 направления</h2><ol class="result-top">${top[0] || ''}</ol></section><section class="result-why"><h2>Почему это может подойти</h2><p>Ваш код интересов — ${escapeHtml(result.riasecCode)}, «${escapeHtml(result.label)}».</p>${strongest.length?`<p>Сильные зоны: ${escapeHtml(strongest.join(', '))}.</p>`:''}${!internship?cta:''}</section><section class="result-more">${top.length>1?`<ol class="result-top result-rest">${top.slice(1).join('')}</ol>`:''}<div class="result-chart" aria-label="Все 7 сфер">${chart}</div></section><section class="result-block"><h2>Профиль интересов</h2><p>${escapeHtml(result.riasecCode)}</p><div class="riasec-chart">${types}</div></section><section class="result-block"><h2>Сильные стороны</h2>${domains}</section>${energy?`<section class="result-block"><h2>Что вас заряжает</h2>${energy}</section>`:''}<section class="result-block"><h2>Ценности и условия</h2>${valuesBlock}${anchor}</section><section class="result-block"><h2>Главные условия хорошей работы</h2>${needsBlock}</section>${words?`<section class="result-block"><h2>Ваши слова</h2>${words}</section>`:''}<section class="result-block"><h2>Реальность (НАДО)</h2>${nad}${result.hints.filter(hint=>!sphereHints[hint.id]).map(hint=>`<p>${escapeHtml(hint.text)}</p>`).join('')}</section>${entries}${!changeDirection?consistency:''}<section class="result-block"><h2>Идеи проб на 30 дней</h2><p>${escapeHtml(config.texts?.experiments?.paragraphs?.[0] || '')}</p></section><section class="result-block"><h2>Ограничения теста</h2><p>${escapeHtml(fullDisclaimer)}</p></section>`;
}
