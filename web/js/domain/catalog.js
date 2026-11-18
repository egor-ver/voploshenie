export function catalogResult(result) { return {spheres:result.spheres.map(({id,percent})=>({id,percent})),topSpheres:result.topSpheres}; }
export function matchPercent(item,result) { return Math.max(0,...result.spheres.filter(s=>item.spheres.includes(s.id)).map(s=>s.percent)); }
export function byInterests(items,result) { return [...items].sort((a,b)=>matchPercent(b,result)-matchPercent(a,result)); }
export function byCourse(course,candidates) {
  const text=String(course||'').toLocaleLowerCase('ru');
  const stage=text.includes('аспирантур')?'graduate':text.includes('магистратур')?'master':text.includes('бакалавриат')||text.includes('специалитет')||/\d+\s*курс/.test(text)?'undergraduate':null;
  if(!stage)return candidates;
  const year=Number(text.match(/\d+/)?.[0])||null;
  if(stage!=='graduate'&&(!year||year>6))return candidates;
  const matches=audience=> {
    const clauses=String(audience||'').toLocaleLowerCase('ru').split(/[,;]/);
    return clauses.some((clause,index)=>{
      if(stage==='graduate')return clause.includes('аспирантур');
      const master=clause.includes('магистратур');
      const under=clause.includes('бакалавриат')||clause.includes('специалитет')||/\d+\s*[-–—]\s*\d+\s*курс|\d+\s*курс|курс\s*от\s*\d+|от\s*\d+\s*курс/.test(clause);
      if(stage==='master'?!master:master||!under)return false;
      if(!year||master&&!/\d+\s*курс/.test(clause))return true;
      let prior='';
      if(clause.includes('курс')) for(let previous=index-1;previous>=0&&/^\s*\d+\s*$/.test(clauses[previous]);previous--) prior=`${clauses[previous]} ${prior}`;
      const numbers=[...`${prior} ${clause}`.matchAll(/(\d+)\s*(?:[-–—]\s*(\d+))?/g)].flatMap(match=>{const first=Number(match[1]),last=Number(match[2])||(/старше|от\s/.test(clause)?6:first);return Array.from({length:last-first+1},(_,n)=>first+n);});
      return numbers.includes(year);
    });
  };
  const audience=row=>row.item?.audience_text ?? row.audience_text;
  return [...candidates.filter(row=>matches(audience(row))),...candidates.filter(row=>!matches(audience(row)))];
}
function period(date) { try { if(date.precision==='day')return [date.date,date.date]; if(date.precision==='month'){const [y,m]=date.date.split('-').map(Number);return [`${date.date}-01`,`${date.date}-${String(new Date(Date.UTC(y,m,0)).getUTCDate()).padStart(2,'0')}`];} if(date.precision==='year')return [`${date.date}-01-01`,`${date.date}-12-31`]; } catch {} return null; }
export function nearestDate(item,today) { return item.dates.map(period).filter(Boolean).map(([start,end])=>end<today?null:start<today?today:start).filter(Boolean).sort()[0]||'9999-12-31'; }
export function byNearestDate(items,today) { return [...items].sort((a,b)=>nearestDate(a,today).localeCompare(nearestDate(b,today))||a.title.localeCompare(b.title)); }
export function recommendations(items,result) {
  const spheres=result.topSpheres||[];
  const ids=new Set();
  const first=(items.filter(item=>spheres[0]&&item.spheres.includes(spheres[0])&&!ids.has(item.id)));
  first.forEach(item=>ids.add(item.id));
  const rest=spheres.slice(1,3).flatMap(sphere=>items.filter(item=>item.spheres.includes(sphere)&&!ids.has(item.id)&&ids.add(item.id)).map(item=>({item,fromSphere:sphere})));
  const other=items.filter(item=>!ids.has(item.id)&&ids.add(item.id)).map(item=>({item,fromSphere:'other'}));
  return [...first.map(item=>({item,fromSphere:null})),...rest,...other];
}

export function similar(items,item) { return items.filter(candidate=>candidate.id!==item.id&&candidate.type===item.type&&candidate.spheres.some(sphere=>item.spheres.includes(sphere))); }
export function opportunityClosed(item,today) { return item.dates.length>0&&item.dates.every(date=>{const range=period(date);return range!==null&&range[1]<today;}); }
