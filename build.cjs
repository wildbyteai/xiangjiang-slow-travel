// Generates only public-demo assets from checked-in sources. No network or private inputs.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=__dirname,read=p=>fs.readFileSync(path.join(root,p),'utf8');
const write=(p,data)=>{const dest=path.join(root,p);fs.mkdirSync(path.dirname(dest),{recursive:true});fs.writeFileSync(dest,data);};
const trip=JSON.parse(read('maps/itinerary-data.json')),geo=JSON.parse(read('maps/route-data.json'));
assert.equal(trip.example,true,'Only synthetic public data allowed in this distribution');
const json=x=>JSON.stringify(x).replace(/</g,'\\u003c');
const fragment=read('maps/route-map.template.html').replace('/*__D3_LIBRARY__*/',()=>read('maps/vendor/d3.min.js')).replace('__MAP_DATA__',()=>json(geo)).replace('__ITINERARY_DATA__',()=>json(trip));
assert(!/__MAP_DATA__|__ITINERARY_DATA__|__D3_LIBRARY__/.test(fragment));write('maps/route-map.fragment.html',fragment);
const stepDays=Object.fromEntries(trip.days.flatMap(d=>d.steps.map(s=>[s.id,d.id])));
const bridge=`<script>window.addEventListener('message',e=>{if(e.source!==parent||e.origin!==location.origin||e.data?.type!=='cs-guide-select')return;const id=e.data.step;if(typeof id!=='string')return;const day=${JSON.stringify(stepDays)}[id];if(!day)return;const select=document.querySelector('select[data-day]');if(select){select.value=day;select.dispatchEvent(new Event('change'));}const b=[...document.querySelectorAll('[data-step-id]')].find(b=>b.dataset.stepId===id);b?.click();});parent.postMessage({type:'cs-guide-map-ready'},location.protocol==='file:'?'*':location.origin);</script>`;
const overrides='body{margin:0;padding:10px;font-family:sans-serif}#changsha-slow-route>h2,#changsha-slow-route>.viz-row:first-of-type,#changsha-slow-route>[data-day-summary],#changsha-slow-route .route-steps,#changsha-slow-route .route-selected,#changsha-slow-route .route-prep,#changsha-slow-route .selection{display:none}#changsha-slow-route .viz-controls>label:first-child{display:none}#changsha-slow-route .route-canvas{max-height:500px}';
write('mobile-guide/site/map.html','<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>长沙示例路线</title><style>'+read('mobile-guide/vendor/map-base.css')+overrides+'</style><body>'+fragment+bridge+'</body></html>');
write('mobile-guide/site/trip.js','window.CS_TRIP='+json(trip)+';\n');
// Geographic anchors for the Android map (no hotel door or residential point).
const appGeo=JSON.parse(JSON.stringify(geo));
for(const [id,name] of [['way/184960645','坡子街·餐饮片区'],['way/445814171','太平街·可选闲逛']]){const f=geo.base.features.find(f=>f.properties.id===id);assert(f);appGeo.points.push({name,kind:'area',coordinates:f.geometry.coordinates[0]});}
appGeo.points.push({name:'湘江中路·住宿区域',kind:'hotel',coordinates:geo.points.find(p=>p.name==='湘江中路站').coordinates});
const mapData='window.GEO='+json(appGeo)+';';write('android-app/map-data.js',mapData);
for(const [name,data] of [['trip.json',JSON.stringify(trip)],['map-data.js',mapData],['map.html',read('android-app/map.html')]])write('android-app/app/src/main/assets/'+name,data);
write('legacy-h5/js/map-data.js','window.MAP_DATA='+json(geo)+';');
write('legacy-h5/js/itinerary-data.js','window.ITINERARY_DATA='+json(trip)+';');
console.log('Public map, mobile guide, legacy H5 and Android assets generated locally.');
