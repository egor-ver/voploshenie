// Единый источник данных мини-игр и дополнительных возможностей — web/. Скрипт выгружает их в assets Android.
// Запуск из корня репозитория: node tools/export_games_data.mjs
import { copyFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const assets = resolve(root, 'app/src/main/assets');
mkdirSync(resolve(assets, 'games'), { recursive: true });
const load = path => import(pathToFileURL(resolve(root, path)).href);

const { WORDS, MYTHS } = await load('web/js/games/data.js');
const { RESUMES } = await load('web/js/games/resume-data.js');
const write = (name, value) => writeFileSync(resolve(assets, 'games', name), JSON.stringify(value, null, 1) + '\n');

write('words.json', WORDS.map(({ w, d, t, h }) => ({ word: w, display: d ?? null, topic: t, hint: h })));
write('myths.json', MYTHS.map(({ id, t, s, why }) => ({ id, truth: t, statement: s, why })));
write('resumes.json', RESUMES.map(({ id, role, name, sub, items }) => ({
  id, role, name, sub,
  items: items.map(item => item.h ? { heading: item.h } : { text: item.t, weak: Boolean(item.bad), why: item.why ?? null, fix: item.fix ?? null }),
})));
copyFileSync(resolve(root, 'web/data/catalog_extra.json'), resolve(assets, 'catalog_extra.json'));
console.log(`words ${WORDS.length}, myths ${MYTHS.length}, resumes ${RESUMES.length}`);
