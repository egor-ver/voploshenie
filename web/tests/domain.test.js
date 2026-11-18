import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {parseConfig,validate} from '../js/domain/config.js';
import {compute,interestCode,level,roundPercent} from '../js/domain/scoring.js';
import {quizCompletion,scoringAnswers,isScreenComplete} from '../js/domain/completion.js';
import {byCourse,byInterests,byNearestDate,recommendations} from '../js/domain/catalog.js';
import {escapeHtml} from '../js/escape.js';
import {localDate} from '../js/domain/local-date.js';

const config=parseConfig(await readFile(new URL('../data/test_config.json',import.meta.url),'utf8'));
test('config rejects invalid question maxima',()=>{
  const question=config.parts.flatMap(part=>part.questions).find(q=>q.type==='text');
  for(const max of ['10',0,-1,1.5,Number.MAX_SAFE_INTEGER+1,'1000"><img src=x>']) {
    const invalid=structuredClone(config);
    invalid.parts.flatMap(part=>part.questions).find(q=>q.id===question.id).max=max;
    assert.throws(()=>parseConfig(invalid),/предел длины вопроса/);
  }
  const valid=structuredClone(config);
  valid.parts.flatMap(part=>part.questions).find(q=>q.id===question.id).max=1;
  assert.doesNotThrow(()=>parseConfig(valid));
});
test('config requires every scoring section, needs steps, and sphere maps',()=>{
  const missingPart=structuredClone(config); missingPart.parts=missingPart.parts.filter(part=>part.id!=='experience');
  assert.throws(()=>parseConfig(missingPart),/обязательн/);
  const missingSteps=structuredClone(config); delete missingSteps.parts.find(part=>part.id==='needs').steps;
  assert.throws(()=>parseConfig(missingSteps),/Некорректная структура/);
  const missingMap=structuredClone(config); delete missingMap.spheres[0].domains;
  assert.throws(()=>parseConfig(missingMap),/Некорректная структура/);
});
const data=JSON.parse(await readFile(new URL('../data/demo_answers.json',import.meta.url),'utf8'));
const answers={pairs:Object.fromEntries(config.questions.interests.map((q,i)=>[q.id,data[q.id]==='A'?q.type_a:q.type_b])),tasks:Object.fromEntries(config.questions.tasks.map(q=>[q.id,data[q.id]])),interest:Object.fromEntries(config.questions.sphere_interest.map(q=>[q.id,Number(data[q.id])])),valuesTop3:['VAL10','VAL07','VAL01'],valuesLeast2:['VAL08','VAL04'],needs:{top3:['NEED02','NEED01','NEED08']}};
const reference={pairs:Object.fromEntries(config.questions.interests.map((q,i)=>[q.id,[1,6,7,8,9,10].includes(i+1)?q.type_a:q.type_b])),tasks:{TSK01:'в',TSK02:'в',TSK03:'б',TSK04:'а',TSK05:'в',TSK06:'б',TSK07:'а',TSK08:'а',TSK09:'в',TSK10:'а'},interest:Object.fromEntries(Array.from({length:14},(_,i)=>[`SPH${String(i+1).padStart(2,'0')}`,[3,5,2,3,2,4,3][i%7]])),valuesTop3:['VAL10','VAL07','VAL01'],valuesLeast2:['VAL08','VAL04'],needs:{top3:['NEED02','NEED01','NEED08']},checks:Object.fromEntries(config.questions.consistency.map((q,i)=>[q.id,i<2?q.type_a:q.type_b])),nad:{}};

test('config validates, rejects duplicates and reproduces section 13 scores',()=>{
  assert.deepEqual(validate(config),[]);
  assert.throws(()=>parseConfig({...config,parts:[...config.parts,{...config.parts[0]}]}),/Повторяющиеся ID/);
  const result=compute(answers,config);
  assert.deepEqual(result.spheres.map(({id,total,percent})=>[id,total,percent]),[['it',57,80],['finance',46,65],['engineering',40,56],['biology',40,56],['telecom',36,51],['business',36,51],['public_service',35,49]]);
  assert.deepEqual(result.topSpheres,['it','finance','engineering']);
});
test('percent rounding matches Kotlin half-up and clamps negatives through scoring function',()=>{
  assert.deepEqual([-1.5,.49,.5,1.5,2.5,49.5,100].map(roundPercent),[0,0,1,2,3,50,100]);
});
test('config rejects RIASEC values that are not single known types',()=>{
  const invalid=structuredClone(config); invalid.spheres[0].riasec[0]='RI';
  assert.ok(validate(invalid).some(error=>error.includes(invalid.spheres[0].id)));
});
test('catalog sorting uses highest matched percent and nearest live date',()=>{
  const items=[{id:'later',title:'Later',spheres:['finance'],dates:[{date:'2027-03',precision:'month'}]},{id:'soon',title:'Soon',spheres:['it'],dates:[{date:'2026-10-21',precision:'day'}]}];
  assert.deepEqual(byInterests(items,{spheres:[{id:'it',percent:80},{id:'finance',percent:65}]}).map(i=>i.id),['soon','later']);
  assert.deepEqual(byNearestDate(items,'2026-10-20').map(i=>i.id),['soon','later']);
});
test('HTML escaping covers all markup delimiters',()=>assert.equal(escapeHtml(`<a x='"'>&`),'&lt;a x=&#39;&quot;&#39;&gt;&amp;'));

test('ScoringEngineTest reference code, equivalence, levels and presentation',()=>{
  const r=compute(reference,config);
  assert.equal(r.riasecCode,'ICE'); assert.deepEqual(r.equivalentPairs,[['engineering','biology'],['telecom','business'],['telecom','public_service'],['business','public_service']]);
  assert.equal(r.mismatches,0);assert.equal(r.consistencyWarning,null);assert.equal(r.label,'Исследователь идей');assert.equal(r.anchor,'Профессиональная компетентность');
  assert.equal(r.riasecLevels.I,'Выраженный');assert.equal(r.riasecLevels.E,'Умеренный');assert.equal(r.riasecLevels.R,'Слабый');
  assert.equal(r.domainLevels.logic,'Сильная зона');assert.equal(r.domainLevels.numbers,'Рабочий уровень');assert.equal(r.domainLevels.space,'Зона роста');
  assert.equal(r.spheres[0].level,'Сильное совпадение');assert.equal(r.spheres[2].level,'Заметное совпадение');assert.deepEqual(r.spheres[0].professions.map(p=>p.name),['разработка','аналитика данных','тестирование']);
  assert.ok(r.spheres.every(s=>s.professions.length===3&&s.professions.every(p=>p.percent===s.percent)));assert.deepEqual(r.spheres[2].flags.map(f=>f.id),['skill_gap']);assert.ok(r.spheres.slice(3).every(s=>!s.flags.length));
});
test('ScoringEngineTest resolves pair tie, cycle and canonical order',()=>{
  const scores={R:4,I:4,A:0,S:0,E:0,C:0};
  assert.ok(interestCode(scores,{...reference.pairs,INT01:'R'},config.questions.interests).startsWith('RI'));
  assert.ok(interestCode(scores,{...reference.pairs,INT01:'I'},config.questions.interests).startsWith('IR'));
  const cycle={...reference.pairs,INT01:'R',INT02:'A',INT06:'I'};
  assert.equal(interestCode({...scores,A:4},cycle,config.questions.interests),'RIA');
  assert.equal(interestCode({...scores,A:4},{...cycle,INT01:'I'},config.questions.interests),'IAR');
});
test('ScoringEngineTest compares consistency and honors warning threshold',()=>{
  const one={...reference,checks:{...reference.checks,CHK01:'S'}},two={...one,checks:{...one.checks,CHK02:'S'}};
  assert.equal(compute(one,config).mismatches,1);assert.equal(compute(one,config).consistencyWarning,null);
  assert.equal(compute(two,config).mismatches,2);assert.equal(compute(two,config).consistencyWarning,config.analyst.consistency_warning);
  const raised=structuredClone(config);raised.analyst.thresholds.consistency_mismatches=3;assert.equal(compute(two,raised).consistencyWarning,null);
  const swapped=structuredClone(config);swapped.questions.consistency=swapped.questions.consistency.map(q=>({...q,type_a:q.type_b,type_b:q.type_a}));assert.equal(compute(reference,swapped).mismatches,0);
});
test('ScoringEngineTest applies skill, interest and conditions flag boundaries',()=>{
  const flags=(id,a=reference,c=config)=>compute(a,{...c,spheres:c.spheres.filter(s=>s.id===id)}).spheres[0].flags.map(f=>f.id);
  assert.ok(flags('engineering').includes('skill_gap'));assert.ok(!flags('engineering',{...reference,interest:{...reference.interest,SPH06:3}}).includes('skill_gap'));
  const space=config.questions.tasks.find(q=>q.domain==='space');assert.ok(!flags('engineering',{...reference,tasks:{...reference.tasks,[space.id]:space.correct}}).includes('skill_gap'));
  const skillConfig=structuredClone(config);skillConfig.analyst.thresholds.skill_gap.score_interest_min=17;assert.ok(!flags('engineering',reference,skillConfig).includes('skill_gap'));
  const finance={...reference,interest:{...reference.interest,SPH04:2,SPH11:2}};assert.ok(flags('finance',finance).includes('interest_gap'));
  assert.ok(!flags('finance',{...finance,interest:{...finance.interest,SPH04:3}}).includes('interest_gap'));
  const numberQ=config.questions.tasks.find(q=>q.domain==='numbers'&&finance.tasks[q.id]===q.correct);assert.ok(!flags('finance',{...finance,tasks:{...finance.tasks,[numberQ.id]:'wrong'}}).includes('interest_gap'));
  const interestConfig=structuredClone(config);interestConfig.analyst.thresholds.interest_gap.score_ability_min=10;assert.ok(!flags('finance',finance,interestConfig).includes('interest_gap'));
  assert.ok(flags('public_service').includes('conditions_gap'));assert.ok(!flags('public_service',{...reference,needs:{top3:['NEED07']}}).includes('conditions_gap'));
  const needsConfig=structuredClone(config);needsConfig.analyst.thresholds.conditions_gap_score_needs=1;assert.ok(!flags('public_service',reference,needsConfig).includes('conditions_gap'));
  assert.equal(compute(reference,config).spheres[2].flags[0].text,config.analyst.flag_texts.skill_gap);
});
test('ScoringEngineTest returns each NAD hint only under its conditions',()=>{
  const cases=[['H01','NAD03','a','b','biology'],['H02','NAD04','a','c','it'],['H03','NAD05','c','b','business'],['H04','NAD02','a','b','it'],['H05','NAD01','d','c','public_service']];
  for(const [id,key,yes,no,sphere] of cases){const restricted={...config,spheres:config.spheres.filter(s=>s.id===sphere)};const result=compute({...reference,nad:{[key]:yes}},restricted);assert.equal(result.hints[0].id,id);assert.equal(result.hints[0].text,config.hints.find(h=>h.ID===id)['Текст подсказки']);assert.equal(compute({...reference,nad:{[key]:no}},restricted).hints.length,0);}
  assert.equal(compute({...reference,nad:{NAD04:'b'}},config).hints[0].id,'H02');
  assert.equal(compute({...reference,nad:{NAD01:'d'}},{...config,spheres:config.spheres.filter(s=>s.id==='biology')}).hints[0].id,'H05');
});
test('ScoringEngineTest uses configured levels, equivalence thresholds and rank-one anchor',()=>{
  const r=config.analyst.levels;
  assert.deepEqual([0,1,2,3,4,5].map(n=>level(n,r.riasec)),['Слабый','Слабый','Умеренный','Умеренный','Выраженный','Выраженный']);
  assert.deepEqual([0,1,2].map(n=>level(n,r.domain)),['Зона роста','Рабочий уровень','Сильная зона']);
  assert.deepEqual([0,44,45,59,60,100].map(n=>level(n,r.match)),['Слабое совпадение','Слабое совпадение','Заметное совпадение','Заметное совпадение','Сильное совпадение','Сильное совпадение']);
  const changed=structuredClone(config);changed.analyst.levels.match=[{min:81,label:'Сильное совпадение'},{min:45,label:'Заметное совпадение'},{min:0,label:'Слабое совпадение'}];assert.equal(compute(reference,changed).spheres[0].level,'Заметное совпадение');
  const threshold=structuredClone(config);threshold.analyst.thresholds.equivalent_percent_difference=2;assert.deepEqual(compute(reference,threshold).equivalentPairs,[['engineering','biology'],['telecom','business']]);
  threshold.analyst.thresholds.equivalent_percent_difference=0;assert.deepEqual(compute(reference,threshold).equivalentPairs,[]);
  assert.equal(compute({...reference,valuesTop3:['VAL01','VAL10','VAL07']},config).anchor,'Автономия');
  assert.equal(compute({...reference,valuesTop3:['VAL03','VAL10','VAL07']},config).anchor,config.analyst.income_anchor);
});
test('ScoringEngineConfigTest reference 13 preserves every score and top sphere',()=>{
  const r=compute(reference,config);assert.deepEqual(r.riasec,{R:1,I:4,A:2,S:1,E:3,C:4});assert.deepEqual(r.domains,{logic:2,numbers:1,text:2,space:0,people:1});
  assert.deepEqual(r.spheres.map((s,i)=>[s.id,s.scoreInterest,s.scoreRiasec,s.scoreAbility,s.scoreValues,s.scoreNeeds,s.total,s.percent,i+1]),[
    ['it',20,18,10,6,3,57,80,1],['finance',12,22,9,2,1,46,65,2],['engineering',16,15,4,4,1,40,56,3],['biology',8,15,11,4,2,40,56,4],['telecom',12,15,8,0,1,36,51,5],['business',12,14,7,2,1,36,51,6],['public_service',8,19,10,-2,0,35,49,7]]);
  assert.deepEqual(r.topSpheres,['it','finance','engineering']);assert.equal(r.configVersion,config.version);assert.equal(reference.pairs.INT09,'I');
});
test('ScoringEngineConfigTest minimum ratings and wrong tasks remain nonnegative',()=>{
  const tasks=Object.fromEntries(config.questions.tasks.map(q=>[q.id,q.options.find(o=>o.id!==q.correct).id]));
  const a={...reference,interest:Object.fromEntries(Object.keys(reference.interest).map(id=>[id,1])),tasks,valuesTop3:['VAL04','VAL06','VAL09'],valuesLeast2:['VAL10','VAL07'],needs:{top3:['NEED03','NEED05','NEED06']}};
  const r=compute(a,config),it=r.spheres.find(s=>s.id==='it');assert.deepEqual(new Set(Object.values(r.domains)),new Set([0]));assert.ok(r.spheres.every(s=>s.scoreInterest===4&&s.scoreAbility===0&&s.percent>=0));assert.equal(it.scoreValues,-4);assert.equal(it.scoreNeeds,0);assert.equal(it.total,18);assert.equal(it.percent,25);
});
test('ScoringEngineConfigTest complete score ties preserve config order',()=>{
  const first=config.spheres[0],spheres=[...config.spheres].reverse().map(s=>({...first,id:s.id})),r=compute(reference,{...config,spheres});
  assert.deepEqual(r.spheres.map(s=>s.id),spheres.map(s=>s.id));assert.deepEqual(r.topSpheres,spheres.slice(0,3).map(s=>s.id));assert.equal(new Set(r.spheres.map(s=>`${s.total}:${s.scoreInterest}:${s.scoreRiasec}`)).size,1);
});
test('ScoringEngineConfigTest roundPercent boundaries run through production calculation',()=>{
  assert.deepEqual([-1.5,.49,.5,1.5,2.5,49.5,100].map(roundPercent),[0,0,1,2,3,50,100]);
});
test('ScoringEngineConfigTest ignores unscored answers and preserves config version',()=>{
  const r=compute(reference,config),other=compute({...reference,profile:{PRF05:6},experience:{EXP01:{text:'',energy:1}},nad:{NAD07:''},open:{OPEN01:''},checks:{CHK01:'S'},needs:{...reference.needs,ratings:{NEED01:1}}},config);
  assert.deepEqual(other.riasec,r.riasec);assert.deepEqual(other.domains,r.domains);assert.deepEqual(other.spheres,r.spheres);
  const newer={...config,version:config.version+1};assert.deepEqual(compute(reference,newer),{...r,configVersion:newer.version});
});

test('demo answers pass QuizCompletion and score from stored values',()=>{
  assert.equal(quizCompletion(data,config),true);
  const result=compute(scoringAnswers(data,config),config);
  assert.deepEqual(result.spheres.map(({id,percent})=>[id,percent]),[['it',80],['finance',65],['engineering',56],['biology',56],['telecom',51],['business',51],['public_service',49]]);
  assert.deepEqual(result.topSpheres,['it','finance','engineering']);
});
test('completion rejects invalid profile text, unknown value IDs, fractional ratings and oversized NAD07',()=>{
  const badProfile={...data,profile:JSON.stringify({...JSON.parse(data.profile),PRF01:'n'.repeat(61)})};assert.equal(quizCompletion(badProfile,config),false);
  const badValues={...data,values:JSON.stringify({top:['NOPE','VAL07','VAL01'],least:['VAL08','VAL04']})};assert.equal(quizCompletion(badValues,config),false);
  assert.equal(quizCompletion({...data,NEED01:'1.5'},config),false);
  assert.equal(quizCompletion({...data,NAD07:'x'.repeat(1001)},config),false);
  const profile=JSON.parse(data.profile),canonical='другое (с полем текста до 200 символов)';
  assert.equal(quizCompletion({...data,profile:JSON.stringify({...profile,PRF06:canonical})},config),false);
  assert.equal(quizCompletion({...data,profile:JSON.stringify({...profile,PRF06:canonical,PRF06_other:'x'})},config),true);
});
test('screen completion enforces profile text limits and canonical other text boundary',()=>{
  const screen={kind:'profile',questions:config.parts.find(p=>p.id==='profile').questions};
  const profile=JSON.parse(data.profile), option=screen.questions.find(q=>q.id==='PRF06').options.find(o=>String(o.id||o).startsWith('другое'));
  profile.PRF06=option.id||option; profile.PRF06_other='x'.repeat(200);
  assert.equal(isScreenComplete(screen,{profile},config),true);
  profile.PRF06_other+='x'; assert.equal(isScreenComplete(screen,{profile},config),false);
  profile.PRF06_other='ok'; profile.PRF03='x'.repeat(121); assert.equal(isScreenComplete(screen,{profile},config),false);
});
test('screen completion accepts configured grade bounds, A/B, numeric strings and open text',()=>{
  const profileScreen={kind:'profile',questions:config.parts.find(p=>p.id==='profile').questions};
  const profile=JSON.parse(data.profile); profile.PRF05='10';
  assert.equal(isScreenComplete(profileScreen,{profile},config),true);
  const pair=config.parts.find(p=>p.id==='interests').questions[0];
  assert.equal(isScreenComplete({part:'interests',question:pair},{pairs:{[pair.id]:'A'}},config),true);
  const likert=config.parts.find(p=>p.id==='sphere_interest').questions[0];
  assert.equal(isScreenComplete({part:'sphere_interest',question:likert},{interest:{[likert.id]:'5'}},config),true);
  const open=config.parts.find(p=>p.id==='open').questions[0];
  assert.equal(isScreenComplete({part:'open',question:open},{open:{[open.id]:''}},config),true);
  assert.equal(isScreenComplete({part:'open',question:open},{open:{[open.id]:'x'.repeat(1001)}},config),false);
});
test('screen and full completion share profile, rank, pick3, text and energy boundaries',()=>{
  const good={...data}, profile=JSON.parse(data.profile);
  profile.PRF01='n'.repeat(60); profile.PRF03='x'.repeat(120); profile.PRF04='y'.repeat(120); profile.PRF05=String(config.parts.find(p=>p.id==='profile').questions.find(q=>q.id==='PRF05').scale.min);
  const other=config.parts.find(p=>p.id==='profile').questions.find(q=>q.id==='PRF06').options.at(-1); profile.PRF06=other.id||other; profile.PRF06_other='z'.repeat(200); good.profile=JSON.stringify(profile);
  assert.equal(quizCompletion(good,config),true);
  for(const [key,value] of [['PRF01','n'.repeat(61)],['PRF03','x'.repeat(121)],['PRF04','y'.repeat(121)],['PRF06_other','z'.repeat(201)]]) {
    const invalid={...profile,[key]:value}; assert.equal(isScreenComplete({kind:'profile',questions:config.parts.find(p=>p.id==='profile').questions},{profile:invalid},config),false);
  }
  const rank=JSON.parse(data.values); for(const invalid of [{...rank,top:rank.top.slice(1)},{...rank,top:[rank.top[0],rank.top[0],rank.top[2]]},{...rank,least:[rank.top[0],rank.least[1]]}]) assert.equal(isScreenComplete({kind:'values',questions:config.parts.find(p=>p.id==='values').questions},{values:invalid},config),false);
  const step=config.parts.find(p=>p.id==='needs').steps.find(s=>s.type==='pick3');
  assert.equal(isScreenComplete({kind:'pick3',step},{needs:{top3:step.options.slice(0,step.min).map(o=>o.id)}},config),true);
  assert.equal(isScreenComplete({kind:'pick3',step},{needs:{top3:['invalid',...step.options.slice(1,step.min).map(o=>o.id)]}},config),false);
  const text=config.parts.find(p=>p.id==='open').questions[0];
  assert.equal(isScreenComplete({part:'open',question:text},{open:{[text.id]:'x'.repeat(1000)}},config),true);
  assert.equal(isScreenComplete({part:'open',question:text},{open:{[text.id]:'x'.repeat(1001)}},config),false);
  const exp=config.parts.find(p=>p.id==='experience').questions[0].id;
  const expScreen={part:'experience',question:config.questions.experience[0]};
  const withoutExperience={...data}; delete withoutExperience[exp];
  assert.equal(isScreenComplete(expScreen,{experience:{}},config),true);
  assert.equal(quizCompletion(withoutExperience,config),true);
  for(const [energy,valid] of [[0,false],[1,true],[5,true],[6,false]]) {
    const answer={text:'work',energy};
    assert.equal(isScreenComplete(expScreen,{experience:{[exp]:answer}},config),valid);
    assert.equal(quizCompletion({...data,[exp]:JSON.stringify(answer)},config),valid);
  }
  for(const [length,valid] of [[1000,true],[1001,false]]) {
    const answer={text:'x'.repeat(length),energy:3};
    assert.equal(isScreenComplete(expScreen,{experience:{[exp]:answer}},config),valid);
    assert.equal(quizCompletion({...data,[exp]:JSON.stringify(answer)},config),valid);
  }
  assert.equal(quizCompletion({...data,profile:'{'},config),false);
});
test('catalog recommendations fill from the next ranked spheres without duplicates',()=>{
  const items=[{id:'a',spheres:['it']},{id:'b',spheres:['finance']},{id:'c',spheres:['finance','engineering']},{id:'d',spheres:['engineering']},{id:'e',spheres:['arts']}];
  assert.deepEqual(recommendations(items,{topSpheres:['it','finance','engineering']}).map(r=>[r.item.id,r.fromSphere]),[['a',null],['b','finance'],['c','finance'],['d','engineering'],['e','other']]);
  const crowded=[{id:'a',spheres:['it']},{id:'b',spheres:['it']},{id:'c',spheres:['it']},{id:'d',spheres:['finance']},{id:'e',spheres:['arts']}];
  assert.deepEqual(recommendations(crowded,{topSpheres:['it','finance']}).map(r=>[r.item.id,r.fromSphere]),[['a',null],['b',null],['c',null],['d','finance'],['e','other']]);
});
test('course audience ranges prioritize matching students and keep unparsed items',()=>{
  const candidates=[{id:'unparsed',audience_text:'студенты'},{id:'senior',audience_text:'старшие курсы/выпускники'},{id:'range',audience_text:'1–4 курс, магистратура'}];
  assert.deepEqual(byCourse('3 курс бакалавриата/специалитета',candidates).map(item=>item.id),['range','unparsed','senior']);
  assert.deepEqual(byCourse('3 курс бакалавриата/специалитета',candidates.map(item=>({item,fromSphere:null}))).map(row=>row.item.id),['range','unparsed','senior']);
  assert.deepEqual(byCourse('курс вне формата',candidates).map(item=>item.id),['unparsed','senior','range']);
});
test('course matching preserves separate stages and course enumerations',()=>{
  const candidates=[{id:'enum',audience_text:'1, 3, 5 курс'},{id:'specialty',audience_text:'4–5 специалитета'},{id:'master',audience_text:'магистратура'},{id:'graduate',audience_text:'аспирантура'},{id:'unknown',audience_text:'студенты'}];
  assert.deepEqual(byCourse('1 курс бакалавриата/специалитета',candidates).map(item=>item.id),['enum','specialty','master','graduate','unknown']);
  assert.deepEqual(byCourse('3 курс бакалавриата/специалитета',candidates).map(item=>item.id),['enum','specialty','master','graduate','unknown']);
  assert.deepEqual(byCourse('2 курс магистратуры',candidates).map(item=>item.id),['master','enum','specialty','graduate','unknown']);
  assert.deepEqual(byCourse('аспирантура',candidates).map(item=>item.id),['graduate','enum','specialty','master','unknown']);
});
test('localDate uses the local calendar across UTC offsets and month/year boundaries',()=>{
  const original=process.env.TZ || 'Europe/Moscow';
  try {
    process.env.TZ='Pacific/Kiritimati';
    assert.equal(localDate(new Date('2026-01-01T10:30:00Z')),'2026-01-02');
    process.env.TZ='America/Los_Angeles';
    assert.equal(localDate(new Date('2026-01-01T00:30:00Z')),'2025-12-31');
    process.env.TZ='Asia/Kolkata';
    assert.equal(localDate(new Date('2026-03-31T19:00:00Z')),'2026-04-01');
    process.env.TZ='America/Adak';
    assert.equal(localDate(new Date('2026-04-01T03:00:00Z')),'2026-03-31');
  } finally { process.env.TZ=original; }
});
