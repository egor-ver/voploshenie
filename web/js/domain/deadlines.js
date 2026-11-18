const kinds = ['apply_deadline', 'start', 'end'];

function valid(date) {
  if (!date || !['day', 'month', 'year'].includes(date.precision) || typeof date.date !== 'string') return false;
  if (date.precision === 'day') return /^\d{4}-\d{2}-\d{2}$/.test(date.date) && !Number.isNaN(Date.parse(`${date.date}T00:00:00Z`)) && new Date(`${date.date}T00:00:00Z`).toISOString().slice(0, 10) === date.date;
  if (date.precision === 'month') return /^\d{4}-(0[1-9]|1[0-2])$/.test(date.date);
  return /^\d{4}$/.test(date.date) && Number(date.date) > 0;
}

function expired(date, today) {
  if (date.precision === 'day') return date.date < today;
  if (date.precision === 'month') return date.date < today.slice(0, 7);
  return Number(date.date) < Number(today.slice(0, 4));
}

export function groupDeadlines(items, today) {
  const deadlines = items.map(item => {
    const dates = (Array.isArray(item.dates) ? item.dates : []).filter(valid);
    let date;
    for (const kind of kinds) {
      date = dates.map((row, index) => ({ row, index })).filter(({ row }) => row.kind === kind && !expired(row, today))
        .sort((a, b) => sortDate(a.row).localeCompare(sortDate(b.row)) || a.index - b.index)[0]?.row;
      if (date) break;
    }
    if (date) return { item, date, month:date.precision === 'year' ? null : date.precision === 'month' ? date.date : date.date.slice(0, 7), year:date.precision === 'year' ? Number(date.date) : null };
    if (!dates.length || dates.some(row => !expired(row, today))) return { item, date:null, month:null, year:null };
    return null;
  }).filter(Boolean);
  const grouped = new Map();
  for (const deadline of deadlines) {
    const key = deadline.month || (deadline.year ? String(deadline.year) : 'undated');
    if (!grouped.has(key)) grouped.set(key, { month:deadline.month, year:deadline.year, entries:[] });
    grouped.get(key).entries.push(deadline);
  }
  return [...grouped.values()].sort((a,b) => {
    if (!a.month && !a.year) return 1;
    if (!b.month && !b.year) return -1;
    return (a.month || `${a.year}-13`).localeCompare(b.month || `${b.year}-13`);
  });
}

function sortDate(date) {
  if (date.precision === 'day') return date.date;
  if (date.precision === 'month') return `${date.date}-01`;
  return `${date.date}-01-01`;
}
