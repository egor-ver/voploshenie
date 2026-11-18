const KEY = 'voplo.v1';
const record = value => value !== null && typeof value === 'object' && !Array.isArray(value);
function validAnswers(answers) {
  if (!record(answers)) return false;
  const groups = ['profile','pairs','interest','tasks','experience','nad','checks','open'];
  if (groups.some(key => answers[key] !== undefined && !record(answers[key]))) return false;
  if (answers.values !== undefined && !record(answers.values)) return false;
  if (answers.needs !== undefined && !record(answers.needs)) return false;
  for (const [key, value] of Object.entries(answers)) {
    if (key === 'values') {
      if (!record(value) || !Array.isArray(value.top) || !Array.isArray(value.least) || [...value.top, ...value.least].some(id => typeof id !== 'string')) return false;
    } else if (key === 'needs') {
      if (!record(value) || !record(value.ratings) || !Array.isArray(value.top3) || value.top3.some(id => typeof id !== 'string') || Object.values(value.ratings).some(rating => typeof rating !== 'number')) return false;
    } else if (key === 'experience') {
      if (!record(value) || Object.values(value).some(answer => !record(answer) || typeof answer.text !== 'string' || typeof answer.energy !== 'number')) return false;
    } else if (record(value)) {
      if (Object.values(value).some(answer => answer !== null && !['string', 'number', 'boolean'].includes(typeof answer))) return false;
    } else if (Array.isArray(value) || value === null || !['string', 'number', 'boolean'].includes(typeof value)) return false;
  }
  return true;
}
export function initialState(configVersion = null) { return { version: 1, configVersion, index: 0, answers: {}, result: null, favorites: [] }; }
export function loadState(storage = globalThis.localStorage, configVersion = null) {
  try {
    const raw = storage.getItem(KEY);
    if (!raw) return initialState(configVersion);
    const value = JSON.parse(raw);
    if (value.version !== 1 || !Number.isInteger(value.index) || value.index < 0 || !validAnswers(value.answers) || !Array.isArray(value.favorites) || value.favorites.some(id => typeof id !== 'string') || (value.configVersion !== null && !Number.isInteger(value.configVersion)) || (value.result !== null && typeof value.result !== 'string') || ['catalogQuery','catalogType','catalogSort'].some(key => value[key] !== undefined && typeof value[key] !== 'string')) return initialState(configVersion);
    if (value.answers.profile !== undefined && (!record(value.answers.profile) || Object.values(value.answers.profile).some(answer => typeof answer !== 'string'))) return initialState(configVersion);
    if (configVersion != null && value.configVersion !== configVersion) return { ...initialState(value.result === 'complete' ? value.configVersion : configVersion), answers:value.result === 'complete' ? value.answers : {}, favorites:value.favorites, result:value.result === 'complete' ? 'complete' : null, savedResult:value.result === 'complete' ? value.savedResult : null, savedConfig:value.result === 'complete' ? value.savedConfig : null };
    return { ...initialState(configVersion), ...value };
  } catch { return initialState(configVersion); }
}
export function saveState(state, storage = globalThis.localStorage) { storage.setItem(KEY, JSON.stringify(state)); }
