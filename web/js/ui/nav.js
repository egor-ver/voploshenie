export function renderMainNav(active) {
  const tabs = [['path','Путь','#path'],['opportunities','Возможности','#opportunities'],['saved','Избранное','#saved'],['deadlines','Дедлайны','#deadlines'],['profile','Профиль','#profile']];
  return `<nav class="bottom-nav" aria-label="Основная навигация">${tabs.map(([id,label,href]) => `<a href="${href}" ${active === id ? 'aria-current="page"' : ''}><span class="nav-icon nav-icon-${id}" aria-hidden="true"></span><span>${label}</span></a>`).join('')}</nav>`;
}
