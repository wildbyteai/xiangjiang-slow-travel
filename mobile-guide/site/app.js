(()=>{
  'use strict';
  const trip=window.CS_TRIP,core=window.GuideCore,$=s=>document.querySelector(s);
  let saved;try{saved=JSON.parse(localStorage.getItem(core.KEY));}catch{}
  let state=core.initial(trip,saved),tab=['now','plan','map','prep'].includes(location.hash.slice(1))?location.hash.slice(1):'now',timer;
  const navPlaces={
    'sat-arrival':['长沙南站','湘江中路地铁站'], 'sat-lunch':['湘江中路地铁站','湘江中路地铁站','费大厨辣椒炒肉 坡子街店'],
    'sat-transfer':['橘子洲地铁站'],'sat-south':['橘子洲 青年毛泽东艺术雕塑','橘子洲 问天台'],
    'sat-dinner':['坛宗剁椒鱼头 悦方IDmall店','坡子街'],'sat-evening':['太平街','湘江中路地铁站'],
    'sun-breakfast':['湘江中路地铁站','湘江中路地铁站'],'sun-transfer':['湖南大学地铁站','岳麓书院 前门'],
    'sun-academy':['岳麓书院 前门'],'sun-aiwan':['爱晚亭'],'sun-lunch':['湘江中路地铁站','坡子街'],
    'sun-luggage':['湘江中路地铁站','湘江中路地铁站'],'sun-return':['长沙南站']
  };
  // Mirrors the checklist in ../maps/itinerary-data.json: same 6 items, same order, compressed for
  // a phone screen. Keep the order and coverage aligned when the authoritative list changes.
  const prep=trip.checklist.map((text,i)=>[['往返票面','书院预约','橘子洲预约','住宿与寄存','天气与开放','随身物品'][i],text]);
  const day=()=>trip.days.find(d=>d.id===state.day),step=()=>day().steps.find(s=>s.id===state.step);
  function toast(message){$('#toast').textContent=message;$('#toast').hidden=false;clearTimeout(timer);timer=setTimeout(()=>$('#toast').hidden=true,3000);}
  function save(){try{localStorage.setItem(core.KEY,JSON.stringify(state));}catch{toast('浏览器未允许保存；本次仍可使用，关闭后可能不记进度。');}}
  function element(tag,className,text){const e=document.createElement(tag);if(className)e.className=className;if(text!==undefined)e.textContent=text;return e;}
  function setTab(next){tab=next;history.replaceState(null,'','#'+next);renderTabs();window.scrollTo({top:0,behavior:'instant'});}
  function syncMap(){const f=$('#guide-map');f.contentWindow?.postMessage({type:'cs-guide-select',step:state.step},location.protocol==='file:'?'*':location.origin);}
  function renderTabs(){for(const name of ['now','plan','map','prep'])$('#'+name+'-view').hidden=name!==tab;document.body.dataset.view=tab;$('.day-switch').hidden=tab==='prep';$('#travel-priority').hidden=!['now','plan'].includes(tab);document.querySelectorAll('[data-tab]').forEach(b=>b.setAttribute('aria-current',b.dataset.tab===tab?'page':'false'));if(tab==='map'){syncMap();$('#map-step').textContent=step().time+' · '+step().title;}}
  function choose(id){state=core.select(trip,state,id);save();render();}
  function placeLink(){const name=$('#place-select').value;$('#map-search').href='https://uri.amap.com/search?keyword='+encodeURIComponent(name)+'&city='+encodeURIComponent('长沙')+'&src=changsha-slow-guide&callnative=1';}
  function trainWords(container,train){
    container.replaceChildren(element('strong','',train.primary),document.createTextNode(' '+train.dep+' → '+train.arr));
  }
  function renderTiming(){
    const timing=trip.timing,focus=timing.days[state.day],train=trip.adopted[focus.trainKey];
    $('#priority-title').textContent=focus.title;$('#priority-status').textContent=train.status||timing.status;
    trainWords($('#priority-train'),train);$('#priority-checkpoints').replaceChildren();
    focus.checkpoints.forEach(point=>{const li=element('li');li.append(element('strong','',point.time),element('span','',point.label));$('#priority-checkpoints').append(li);});
    $('#priority-note').textContent=focus.note;$('#show-timing').textContent=state.day==='sun'?'看返程倒排':'看出发详情';
    $('#timing-caution').textContent=timing.caution;$('#timing-days').replaceChildren();
    for(const d of trip.days){const section=element('section','timing-day');section.append(element('h2','',d.label));const list=element('dl','timing-list');
      timing.days[d.id].details.forEach(([time,text])=>{const row=element('div');row.append(element('dt','',time),element('dd','',text));list.append(row);});section.append(list);$('#timing-days').append(section);
    }
    for(const [id,key] of [['#prep-train-out','trainOut'],['#prep-train-back','trainBack']]){const t=trip.adopted[key];trainWords($(id),t);$(id).append(element('small','',t.status||timing.status),element('small','',t.backup));}
  }
  function render(){
    const s=step(),d=day(),i=d.steps.findIndex(x=>x.id===s.id),all=trip.days.flatMap(x=>x.steps),next=all[all.findIndex(x=>x.id===s.id)+1];
    const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
    $('#mode').textContent=['2025-03-15','2025-03-16'].includes(date)?'旅途中':'行程预览';
    document.querySelectorAll('[data-day]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.day===state.day)));
    $('#step-count').textContent='DAY '+(state.day==='sat'?'01':'02')+' / 第 '+String(i+1).padStart(2,'0')+' 站';$('#progress').textContent=state.done.length+' / 13 已走过';
    const titles=s.title.split(' · ');$('#stop-time').textContent=s.time;$('#stop-title').textContent=titles[0];$('#stop-subtitle').textContent=titles.slice(1).join(' · ');$('#stop-location').textContent=s.points.join(' · ');$('#destination-visual').hidden=s.id!=='sat-south';
    $('#step-focus').hidden=!s.focus;$('#focus-title').textContent=s.focus?.title||'';$('#focus-note').textContent=s.focus?.text||'';renderTiming();
    // These are row-KEY tokens, not display text: they must match keys in maps/itinerary-data.json rows.
    // Keep this list identical to the one in h5-changsha-trip/js/app.js (oneLiner). It had drifted:
    // '二选一' was a dead token left after that row was re-keyed to '轻松', and '慢游' was missing
    // entirely (it only worked by falling through to rows[0]). Membership is what matters — the
    // find() below scans rows in order, so this array's own order is irrelevant.
    const quick=(s.rows.find(r=>['已采纳去程','已采纳返程','慢游','交通','落脚','区域','先讲解','顺路','早餐'].includes(r[0]))||s.rows[0]);
    $('#quick-transport').textContent=quick[1];$('#guide-details').replaceChildren();
    s.rows.filter(r=>r!==quick).forEach(([label,text],index)=>{if(['边界','待核','预约','不能漏','提前返程'].includes(label)){const details=element('details','guide-warning');details.append(element('summary','',label+' · 出发前看一眼'),element('p','',text));$('#guide-details').append(details);return;}const a=element('article','guide-block'),h=element('h2');h.append(element('span','guide-number',String(index+1).padStart(2,'0')),document.createTextNode(label));a.append(h);if(text.includes(' → ')){const list=element('ol');text.split(' → ').forEach(t=>list.append(element('li','',t)));a.append(list);}else a.append(element('p','',text));$('#guide-details').append(a);});
    const select=$('#place-select');select.replaceChildren();navPlaces[s.id].forEach(name=>{const option=element('option','',name);option.value=name;select.append(option);});placeLink();
    $('#next-title').textContent=next?next.title:'两天结束，回去好好休息';$('#complete').textContent=next?'完成，去下一站':'标记行程完成';$('#complete').disabled=!next&&state.done.includes(s.id);$('#previous').disabled=all[0].id===s.id;$('#undo-complete').hidden=!state.done.includes(s.id);
    $('#timeline').replaceChildren();for(const t of d.steps){const li=element('li','timeline-item'+(state.done.includes(t.id)?' done':'')+(t.focus?' critical':''));const b=element('button');b.type='button';b.setAttribute('aria-current',t.id===s.id?'step':'false');b.append(element('span','timeline-time',t.time),element('span','timeline-title',t.title));if(t.focus)b.append(element('span','timeline-focus',t.focus.title));b.append(element('span','timeline-status',state.done.includes(t.id)?'已完成 · 可再次查看':t.id===s.id?'正在查看':'查看导览'));b.addEventListener('click',()=>{choose(t.id);setTab('now');});li.append(b);$('#timeline').append(li);}
    $('#checklist').replaceChildren();prep.forEach(([title,text],i)=>{const l=element('label','check-row'),input=element('input');input.type='checkbox';input.checked=state.checks.includes(i);input.addEventListener('change',()=>{state.checks=input.checked?[...new Set([...state.checks,i])]:state.checks.filter(x=>x!==i);save();updatePrep();});const words=element('span');words.append(element('strong','',title),element('span','check-description',text));l.append(input,words);$('#checklist').append(l);});updatePrep();
    renderTabs();syncMap();
  }
  function updatePrep(){$('#prep-count').textContent=state.checks.length;$('#prep-total').textContent=' / '+prep.length+' 已确认';$('#prep-progress').max=prep.length;$('#prep-progress').value=state.checks.length;$('#prep-ring-fill').style.strokeDashoffset=String(144.514*(1-state.checks.length/prep.length));}
  document.querySelectorAll('[data-day]').forEach(b=>b.addEventListener('click',()=>{const d=trip.days.find(x=>x.id===b.dataset.day);choose(d.steps[0].id);}));
  document.querySelectorAll('[data-tab]').forEach(b=>b.addEventListener('click',()=>setTab(b.dataset.tab)));
  $('.wordmark').addEventListener('click',e=>{e.preventDefault();setTab('now');});
  $('#show-map').addEventListener('click',()=>setTab('map'));$('#back-guide').addEventListener('click',()=>setTab('now'));
  $('#show-timing').addEventListener('click',()=>{setTab('prep');$('#timing-details').open=true;const summary=$('#timing-details').querySelector('summary');summary.focus({preventScroll:true});$('#timing-details').scrollIntoView({block:'start',behavior:'instant'});});
  $('#guide-map').addEventListener('load',syncMap);window.addEventListener('message',e=>{if(e.source===$('#guide-map').contentWindow&&e.origin===location.origin&&e.data?.type==='cs-guide-map-ready')syncMap();});
  $('#complete').addEventListener('click',()=>{state=core.complete(trip,state);save();render();window.scrollTo({top:0,behavior:'instant'});toast('已记下，可以按自己的节奏继续。');});
  $('#previous').addEventListener('click',()=>{const all=trip.days.flatMap(d=>d.steps),i=all.findIndex(s=>s.id===state.step);if(i>0)choose(all[i-1].id);window.scrollTo({top:0,behavior:'instant'});});
  $('#undo-complete').addEventListener('click',()=>{state.done=state.done.filter(id=>id!==state.step);save();render();});
  $('#place-select').addEventListener('change',placeLink);$('#copy-place').addEventListener('click',async()=>{try{await navigator.clipboard.writeText($('#place-select').value);toast('地点已复制。');}catch{toast('复制未成功，请长按选择框里的地点名称。');}});
  $('#copy-hotel')?.addEventListener('click',async()=>{try{await navigator.clipboard.writeText('住宿区域示例（湘江中路）长沙市湘江中路住宿区域');toast('酒店地址已复制。');}catch{toast('复制未成功，请长按地址文字。');}});
  const cachePrefix='cs-guide-'+new URL('./',location.href).pathname.replace(/[^a-z0-9]/gi,'_')+'-';
  async function offlineState(){try{const names=await caches.keys();const has=names.some(n=>n.startsWith(cachePrefix)&&/^v\d+$/.test(n.slice(cachePrefix.length)));$('#offline-status').textContent=has?'已保存到本机（含最新行程文案）。出发前请断网试开一次。':'尚未离线保存，联网时点下方保存。';}catch{$('#offline-status').textContent='此处只能在线预览。离线保存需要手机可访问的HTTPS页面。';}}
  $('#offline-download').addEventListener('click',async()=>{const b=$('#offline-download');b.disabled=true;let timeout;try{if(!('serviceWorker' in navigator)||!window.isSecureContext)throw Error('secure');const reg=await navigator.serviceWorker.register('./sw.js',{scope:'./'});const pending=reg.installing||reg.waiting;const installed=pending?new Promise((resolve,reject)=>{const check=()=>{if(['installed','activated'].includes(pending.state))resolve();else if(pending.state==='redundant')reject(Error('install failed'));};pending.addEventListener('statechange',check);check();}):navigator.serviceWorker.ready;await Promise.race([installed,new Promise((_,reject)=>timeout=setTimeout(()=>reject(Error('timeout')),30000))]);if(reg.waiting)reg.waiting.postMessage({type:'activate'});const keys=await caches.keys();if(!keys.some(n=>n.startsWith(cachePrefix)))throw Error('cache missing');await offlineState();toast('已保存到本机，请断网试开一次。');}catch{$('#offline-status').textContent='离线保存未完成。请联网重试；手机需要可访问的HTTPS地址。';}finally{clearTimeout(timeout);b.disabled=false;}});
  $('#offline-remove').addEventListener('click',async()=>{if(!confirm('移除这台设备的导游离线副本？已勾选的进度会保留。'))return;try{for(const k of await caches.keys())if(k.startsWith(cachePrefix))await caches.delete(k);for(const reg of await navigator.serviceWorker.getRegistrations())if(reg.scope===new URL('./',location.href).href)await reg.unregister();await offlineState();toast('离线副本已移除，浏览进度保留。');}catch{toast('未能清除，请在浏览器设置中清理此站点数据。');}});
  window.addEventListener('online',offlineState);window.addEventListener('offline',()=>{$('#mode').textContent='离线查看';});
  const ctx=document.modelContext;if(ctx?.registerTool){try{Promise.resolve(ctx.registerTool({name:'get_trip_guide_state',description:'Read the selected trip step and device-local completion state.',inputSchema:{type:'object',properties:{},additionalProperties:false},annotations:{readOnlyHint:true},execute(){return {day:state.day,step:state.step,completed:[...state.done],view:tab};}})).catch(()=>{});Promise.resolve(ctx.registerTool({name:'show_trip_step',description:'Select a guide step and save this reading position on this device; does not mark it complete or book anything.',inputSchema:{type:'object',properties:{stepId:{type:'string',enum:trip.days.flatMap(d=>d.steps.map(s=>s.id))}},required:['stepId'],additionalProperties:false},annotations:{readOnlyHint:false},execute(input){if(!input||typeof input.stepId!=='string'||Object.keys(input).some(k=>k!=='stepId'))throw Error('Invalid input');choose(input.stepId);setTab('now');return {day:state.day,step:state.step};}})).catch(()=>{});}catch{}}
  save();render();offlineState();
})();
