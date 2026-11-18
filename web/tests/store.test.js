import test from 'node:test';
import assert from 'node:assert/strict';
import { initialState, loadState, saveState } from '../js/store.js';

test('invalid profile and state field types reset to initial state', () => {
  for (const broken of [
    { ...initialState(2), answers:{ profile:{ PRF03:123 } } },
    { ...initialState(2), answers:{ pairs:123 } },
    { ...initialState(2), catalogQuery:123 },
    { ...initialState(2), favorites:['ok',123] },
    { ...initialState(2), result:{} }
  ]) assert.deepEqual(loadState(storage(JSON.stringify(broken)),2),initialState(2));
});

function storage(value = null) { return { value, getItem() { return this.value; }, setItem(key, next) { this.key = key; this.value = next; } }; }

test('сохраняет и восстанавливает ответы и позицию с версией конфигурации', () => {
  const disk = storage();
  const state = { ...initialState(3), answers: { Q1: 'a' }, index: 7, result: 'r', favorites: ['x'] };
  saveState(state, disk);
  assert.equal(disk.key, 'voplo.v1');
  assert.deepEqual(loadState(disk, 3), state);
  assert.equal(loadState(disk, 4).index, 0);
});

test('повреждённое и несовместимое хранилище безопасно начинает заново', () => {
  assert.deepEqual(loadState(storage('{broken'), 2), initialState(2));
  assert.deepEqual(loadState(storage(JSON.stringify({ version: 9 })), 2), initialState(2));
  assert.deepEqual(loadState(storage(null), 2), initialState(2));
});

test('повреждённая вложенная структура ответов целиком сбрасывается', () => {
  const broken = { ...initialState(2), index:8, answers:{ values:{top:'abc',least:'de'} } };
  assert.deepEqual(loadState(storage(JSON.stringify(broken)),2),initialState(2));
  const invalidNeeds = { ...initialState(2), index:4, answers:{ needs:{ratings:[],top3:'bad'} } };
  assert.deepEqual(loadState(storage(JSON.stringify(invalidNeeds)),2),initialState(2));
  const invalidGroup = { ...initialState(2), answers:{ experience:[] } };
  assert.deepEqual(loadState(storage(JSON.stringify(invalidGroup)),2),initialState(2));
});

test('config migration keeps completed answers and the original result snapshot',()=>{
  const completed={...initialState(3),configVersion:3,result:'complete',answers:{profile:{PRF03:'Имя'},values:{top:['VAL01','VAL02','VAL03'],least:['VAL04','VAL05']},open:{OPEN01:'Ответ'}},savedResult:{configVersion:3},savedConfig:{version:3},favorites:['x']};
  const migrated=loadState(storage(JSON.stringify(completed)),4);
  assert.deepEqual(migrated.answers,completed.answers);
  assert.deepEqual(migrated.savedResult,completed.savedResult);
  assert.deepEqual(migrated.savedConfig,completed.savedConfig);
  assert.equal(migrated.configVersion,3);
  assert.deepEqual(migrated.favorites,['x']);
});

test('config migration resets an incompatible draft to the current version across reloads',()=>{
  const disk=storage(JSON.stringify({...initialState(3),index:8,answers:{profile:{PRF03:'старый ответ'}},favorites:['x']}));
  const migrated=loadState(disk,4);
  assert.deepEqual(migrated.answers,{});
  assert.equal(migrated.index,0);
  assert.equal(migrated.configVersion,4);
  migrated.answers={profile:{PRF03:'новый ответ'}};
  saveState(migrated,disk);
  assert.deepEqual(loadState(disk,4).answers,{profile:{PRF03:'новый ответ'}});
  assert.equal(loadState(disk,4).configVersion,4);
});
