export function isScreenComplete(screen, answers, config) {
  const profile=answers.profile||{}, ids=screen.questions?.map(q=>q.id)||[];
  if(screen.kind==='profile') {
    const questions=config.parts.find(p=>p.id==='profile').questions, grade=questions.find(q=>q.id==='PRF05').scale;
    const other=questions.find(q=>q.id==='PRF06').options.some(o=>o.id===profile.PRF06&&String(o.text||o.id).toLowerCase().startsWith('другое'));
    return (!profile.PRF01||profile.PRF01.length<=60) && ['PRF03','PRF04'].every(id=>typeof profile[id]==='string'&&profile[id].trim()&&profile[id].length<=120) &&
      questions.find(q=>q.id==='PRF02').options.some(o=>o.id===profile.PRF02) && Number.isInteger(Number(profile.PRF05)) && Number(profile.PRF05)>=(grade?.min??1) && Number(profile.PRF05)<=(grade?.max??10) &&
      questions.find(q=>q.id==='PRF06').options.some(o=>o.id===profile.PRF06) && (!other || (typeof profile.PRF06_other==='string'&&profile.PRF06_other.trim()&&profile.PRF06_other.length<=200));
  }
  if(screen.kind==='values') { const {top=[],least=[]}=answers.values||{}; return top.length===3&&new Set(top).size===3&&least.length===2&&new Set(least).size===2&&!top.some(id=>least.includes(id))&&[...top,...least].every(id=>ids.includes(id)); }
  if(screen.kind==='pick3') { const step=screen.step, picked=answers.needs?.top3||[]; return picked.length===step.min&&new Set(picked).size===picked.length&&picked.every(id=>step.options.some(o=>o.id===id)); }
  if(screen.part==='needs') return screen.questions.every(q=>Number.isInteger(Number(answers.needs?.ratings?.[q.id]))&&Number(answers.needs.ratings[q.id])>=(q.scale?.min??1)&&Number(answers.needs.ratings[q.id])<=(q.scale?.max??5));
  const groups={interests:'pairs',sphere_interest:'interest',tasks:'tasks',constraints:'nad',consistency:'checks',open:'open',happiness:'open'}, q=screen.question;
  if(!q) return false;
  const value=screen.part==='experience'?answers.experience?.[q.id]:answers[groups[screen.part]||screen.part]?.[q.id];
  if(q.type==='pair') return ['A','B'].includes(value);
  if(q.type==='likert5') return Number.isInteger(Number(value))&&Number(value)>=(q.scale?.min??1)&&Number(value)<=(q.scale?.max??5);
  if(q.type==='single_choice') return q.options.some(o=>o.id===value);
  if(q.type==='text') return (value===undefined||typeof value==='string')&&String(value||'').length<=(q.max??1000)&&(!q.required||Boolean(value?.trim()));
  if(q.type==='text_energy5') return value===undefined||Boolean(value&&typeof value.text==='string'&&value.text.length<=1000&&(!value.text.trim()||(Number.isInteger(value.energy)&&value.energy>=1&&value.energy<=5)));
  return false;
}

export function quizCompletion(values, config) {
  try {
    const decode=value=>typeof value==='string'?JSON.parse(value):value;
    const answers={profile:decode(values.profile||'{}'),values:decode(values.values||'{}'),needs:{ratings:{},top3:decode(values['needs/pick3']||'[]')}};
    if(!answers.profile||typeof answers.profile!=='object'||Array.isArray(answers.profile)||!answers.values||typeof answers.values!=='object'||Array.isArray(answers.values)||!Array.isArray(answers.needs.top3)) return false;
    const groups={interests:'pairs',sphere_interest:'interest',tasks:'tasks',experience:'experience',constraints:'nad',consistency:'checks',open:'open',happiness:'open'};
    for(const group of Object.values(groups)) answers[group] ||= {};
    for(const part of config.parts) {
      if(part.id==='needs') { for(const q of part.questions) answers.needs.ratings[q.id]=values[q.id]; continue; }
      if(part.id==='profile'||part.id==='values') continue;
      for(const q of part.questions) {
        if(values[q.id]===undefined) continue;
        const value=part.id==='experience'?decode(values[q.id]):values[q.id];
        if(part.id==='experience'&&value!==undefined&&(!value||typeof value!=='object'||Array.isArray(value))) return false;
        answers[groups[part.id]][q.id]=value;
      }
    }
    const screens=config.parts.flatMap(part=>part.id==='profile'||part.id==='values'?[{kind:part.id,questions:part.questions}]:part.id==='needs'?part.steps.map(step=>({kind:step.type==='pick3'?'pick3':'questions',part:'needs',step,questions:part.questions.filter(q=>(step.question_ids||[]).includes(q.id))})):part.questions.map(question=>({part:part.id,question})));
    return screens.every(screen=>isScreenComplete(screen,answers,config));
  } catch { return false; }
}

export function scoringAnswers(values, config) {
  const parse = value => typeof value === 'string' ? JSON.parse(value) : value;
  const profile=parse(values.profile||'{}'), rank=parse(values.values||'{}');
  const pairs=part=>Object.fromEntries(config.questions[part].map(q=>[q.id,values[q.id]==='A'?q.type_a:q.type_b]));
  return {profile,pairs:pairs('interests'),checks:pairs('consistency'),valuesTop3:rank.top,valuesLeast2:rank.least,
    interest:Object.fromEntries(config.questions.sphere_interest.map(q=>[q.id,Number(values[q.id])])),
    tasks:Object.fromEntries(config.questions.tasks.map(q=>[q.id,values[q.id]])),
    experience:Object.fromEntries(config.questions.experience.filter(q=>values[q.id]).map(q=>[q.id,parse(values[q.id])])),
    nad:Object.fromEntries(config.questions.constraints.map(q=>[q.id,values[q.id]||''])),
    needs:{ratings:Object.fromEntries(config.questions.needs.map(q=>[q.id,Number(values[q.id])])),top3:parse(values['needs/pick3']||'[]')},
    open:Object.fromEntries([...config.questions.open,...(config.questions.happiness||[])].map(q=>[q.id,values[q.id]||'']))};
}
