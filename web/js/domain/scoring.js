const TYPES=['R','I','A','S','E','C'];
export const roundPercent=value=>Math.max(0,Math.floor(value+0.5));
export const level=(score,rules)=>rules.find(rule=>score>=rule.min).label;
const selectedType=(question,answer)=>answer===question.type_a?question.type_a:answer===question.type_b?question.type_b:null;

export function interestCode(scores,pairs,questions){
  const groups=new Map(); for(const type of TYPES){const score=scores[type];groups.set(score,[...(groups.get(score)||[]),type]);}
  return [...groups].sort((a,b)=>b[0]-a[0]).flatMap(([,tied])=>{
    const wins=Object.fromEntries(tied.map(type=>[type,questions.filter(q=>tied.includes(q.type_a)&&tied.includes(q.type_b)&&selectedType(q,pairs[q.id])===type).length]));
    const ordered=[...tied].sort((a,b)=>wins[b]-wins[a]);
    return ordered.map((type,index)=>type).every((type,index)=>wins[type]===tied.length-index-1)?ordered:tied;
  }).slice(0,3).join('');
}

export function compute(answers,config){
  answers={pairs:{},tasks:{},interest:{},valuesTop3:[],valuesLeast2:[],needs:{top3:[]},checks:{},nad:{},...answers};
  const riasec=Object.fromEntries(TYPES.map(type=>[type,0]));
  for(const q of config.questions.interests){const type=selectedType(q,answers.pairs[q.id]);if(type)riasec[type]++;}
  const domains=Object.fromEntries(['logic','numbers','text','space','people'].map(key=>[key,0]));
  for(const q of config.questions.tasks)if(answers.tasks[q.id]===q.correct&&q.domain)domains[q.domain]++;
  const spheres=config.spheres.map(sphere=>{
    const scoreInterest=2*sphere.questions.reduce((sum,id)=>sum+(answers.interest[id]||0),0);
    const scoreRiasec=sphere.riasec.reduce((sum,type,index)=>sum+(3-index)*riasec[type],0);
    const scoreAbility=sphere.domains.reduce((sum,domain,index)=>sum+(3-index)*domains[domain],0);
    const scoreValues=2*sphere.values.filter(id=>answers.valuesTop3.includes(id)).length-2*sphere.values.filter(id=>answers.valuesLeast2.includes(id)).length;
    const scoreNeeds=sphere.needs.filter(id=>answers.needs.top3.includes(id)).length;
    const total=scoreInterest+scoreRiasec+scoreAbility+scoreValues+scoreNeeds;
    return {id:sphere.id,scoreInterest,scoreRiasec,scoreAbility,scoreValues,scoreNeeds,total,percent:roundPercent(total/71*100)};
  }).sort((a,b)=>b.total-a.total||b.scoreInterest-a.scoreInterest||b.scoreRiasec-a.scoreRiasec);
  const analyst=config.analyst,topSpheres=spheres.slice(0,3).map(s=>s.id),thresholds=analyst.thresholds;
  const enriched=spheres.map(s=>{
    const flagIds=[];
    if(topSpheres.includes(s.id)){
      const skill=thresholds.skill_gap,interest=thresholds.interest_gap;
      if(s.scoreInterest>=skill.score_interest_min&&s.scoreAbility<=skill.score_ability_max)flagIds.push('skill_gap');
      if(s.scoreAbility>=interest.score_ability_min&&s.scoreInterest<=interest.score_interest_max)flagIds.push('interest_gap');
      if(s.scoreNeeds===thresholds.conditions_gap_score_needs)flagIds.push('conditions_gap');
    }
    return {...s,level:level(s.percent,analyst.levels.match),flags:flagIds.map(id=>({id,text:analyst.flag_texts[id]})),professions:analyst.professions[s.id].map(name=>({name,percent:s.percent}))};
  });
  const interests=config.questions.interests,byId=Object.fromEntries(interests.map(q=>[q.id,q]));
  const mismatches=config.questions.consistency.filter(q=>selectedType(q,answers.checks[q.id])!==selectedType(byId[q.linked],answers.pairs[q.linked])).length;
  const hintIds=[];
  if(answers.nad.NAD03==='a'&&topSpheres.includes('biology'))hintIds.push('H01');
  if(['a','b'].includes(answers.nad.NAD04))hintIds.push('H02');
  if(answers.nad.NAD05==='c'&&topSpheres.includes('business'))hintIds.push('H03');
  if(answers.nad.NAD02==='a')hintIds.push('H04');
  if(answers.nad.NAD01==='d'&&topSpheres.some(id=>['public_service','biology'].includes(id)))hintIds.push('H05');
  const code=interestCode(riasec,answers.pairs,interests),value=answers.valuesTop3[0];
  return {configVersion:config.version,riasec,domains,spheres:enriched,topSpheres,riasecCode:code,
    riasecLevels:Object.fromEntries(TYPES.map(type=>[type,level(riasec[type],analyst.levels.riasec)])),
    domainLevels:Object.fromEntries(Object.keys(domains).map(key=>[key,level(domains[key],analyst.levels.domain)])),
    equivalentPairs:spheres.flatMap((sphere,index)=>spheres.slice(index+1).filter(other=>sphere.percent-other.percent<thresholds.equivalent_percent_difference).map(other=>[sphere.id,other.id])),
    mismatches,consistencyWarning:mismatches>=thresholds.consistency_mismatches?analyst.consistency_warning:null,
    hints:hintIds.map(id=>({id,text:config.hints.find(h=>h.ID===id)['Текст подсказки']})),
    anchor:value==='VAL03'?analyst.income_anchor:value?config.anchors[value]:null,label:analyst.riasec_labels[code[0]]};
}
