export function parseConfig(config) {
  if (typeof config === 'string') config = JSON.parse(config);
  const record=value=>value!==null&&typeof value==='object'&&!Array.isArray(value);
  if (!record(config) || !Number.isInteger(config.version) || config.version<1 || !Array.isArray(config.parts) || !config.parts.length || config.parts.some(part=>!record(part)||typeof part.id!=='string'||!Array.isArray(part.questions)||(part.id==='needs'&&(!Array.isArray(part.steps)||!part.steps.length||part.steps.some(step=>!record(step)||(step.type!=='pick3'&&!Array.isArray(step.question_ids))||(step.type==='pick3'&&!Array.isArray(step.options)))))) || !Array.isArray(config.spheres) || !config.spheres.length || config.spheres.some(sphere=>!record(sphere)||typeof sphere.id!=='string'||!Array.isArray(sphere.questions)||!Array.isArray(sphere.riasec)||!Array.isArray(sphere.domains)||!Array.isArray(sphere.values)||!Array.isArray(sphere.needs))) throw new TypeError('Некорректная структура конфига теста');
  config = {...config, parts:config.parts.map(part => ({...part, questions:part.questions.map(q => !record(q)?q:{...q, options:Array.isArray(q.options) ? q.options.map((option,index) => typeof option === 'string' ? {id:q.id.startsWith('TSK') ? ['а','б','в','г'][index] : option,text:option} : option) : []}), steps:Array.isArray(part.steps)?part.steps.map(step=>!record(step)?step:{...step,options:Array.isArray(step.options)?step.options.map(option=>typeof option==='string'?{id:option,text:option}:option):[]}):part.steps}))};
  const requiredParts=['profile','interests','values','open','sphere_interest','tasks','experience','constraints','needs','happiness','consistency'];
  if(new Set(config.parts.map(part=>part.id)).size!==config.parts.length) throw new TypeError('Повторяющиеся ID частей');
  if(requiredParts.some(id=>!config.parts.some(part=>part.id===id))) throw new TypeError('Отсутствует обязательная часть теста');
  const questions = config.parts.flatMap(part => part.questions);
  if (questions.some(question => !record(question) || typeof question.id!=='string' || !question.id || typeof question.type!=='string' || !question.type || !Array.isArray(question.options) || question.options.some(option=>!record(option)||typeof option.id!=='string'||typeof option.text!=='string'))) throw new TypeError('Некорректный вопрос');
  if (questions.some(question => question.max !== undefined && (!Number.isSafeInteger(question.max) || question.max <= 0))) throw new TypeError('Некорректный предел длины вопроса');
  const ids = [...questions.map(q => q.id), ...config.spheres.map(s => s.id)];
  if (new Set(ids).size !== ids.length) throw new TypeError('Повторяющиеся ID');
  const parsed = {...config, questions:Object.fromEntries([...new Set(config.parts.map(part => part.id))].map(id => [id, config.parts.find(part => part.id === id).questions]))};
  const errors = validate(parsed);
  if (errors.length) throw new TypeError(errors.join('; '));
  return parsed;
}

export function validate(config) {
  const errors = [];
  const questions = config.parts.flatMap(part => part.questions);
  const allIds = [...questions.map(q => q.id), ...config.spheres.map(s => s.id)];
  if (new Set(allIds).size !== allIds.length) errors.push('Повторяющиеся ID');
  const byId = Object.fromEntries(questions.map(q => [q.id, q]));
  const ints = questions.filter(q => q.id.startsWith('INT'));
  const expected = new Set(['R','I','A','S','E','C'].flatMap(a => ['R','I','A','S','E','C'].filter(b => b > a).map(b => [a,b].sort().join(':'))));
  if (ints.length !== 15 || ints.some(q => !q.type_a || !q.type_b) || new Set(ints.map(q => [q.type_a,q.type_b].sort().join(':'))).size !== 15 || ints.some(q => !expected.has([q.type_a,q.type_b].sort().join(':')))) errors.push('INT должны покрывать все пары RIASEC');
  for (const sphere of config.spheres) {
    const sphereQuestions = questions.filter(q => q.id.startsWith('SPH') && q.sphere_id === sphere.id);
    if (sphereQuestions.length !== 2 || sphereQuestions.some(q => q.type !== 'likert5') || sphere.questions.length !== 2 || sphere.questions.some(id => !sphereQuestions.some(q => q.id === id))) errors.push(`Sphere ${sphere.id}: invalid SPH questions`);
    if (!sphere.riasec.length||sphere.riasec.length>6||new Set(sphere.riasec).size!==sphere.riasec.length||sphere.riasec.some(type => !['R','I','A','S','E','C'].includes(type)) || !sphere.domains.length||sphere.domains.length>5||new Set(sphere.domains).size!==sphere.domains.length||sphere.domains.some(domain => !['logic','numbers','text','space','people'].includes(domain))) errors.push(`Сфера ${sphere.id}: неизвестный или повторный тип RIASEC/домен`);
  }
  for (const q of questions.filter(q => q.id.startsWith('TSK'))) {
    if (!q.options.some(option => option.id === q.correct)) errors.push(`${q.id}: correct option missing`);
    if (!['logic','numbers','text','space','people'].includes(q.domain)) errors.push(`${q.id}: unknown domain`);
  }
  for (const q of questions.filter(q => q.id.startsWith('CHK'))) {
    const linked = byId[q.linked];
    if (!linked?.id.startsWith('INT') || !q.type_a || !q.type_b || !linked.type_a || !linked.type_b || [q.type_a,q.type_b].sort().join(':') !== [linked.type_a,linked.type_b].sort().join(':')) errors.push(`${q.id}: invalid linked INT pair`);
  }
  for (const sphere of config.spheres) {
    if (sphere.values.some(id => byId[id]?.type !== 'rank_values' || !id.startsWith('VAL'))) errors.push(`${sphere.id}: invalid VAL reference`);
    if (sphere.needs.some(id => byId[id]?.type !== 'likert5' || !id.startsWith('NEED'))) errors.push(`${sphere.id}: invalid NEED reference`);
  }
  return errors;
}


