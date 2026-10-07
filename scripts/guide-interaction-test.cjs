// Detached element model only: no browser, real GPS/camera/network or rendered layout.
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'),read=p=>fs.readFileSync(path.join(root,p),'utf8');
const html=read('mobile-guide/site/index.html'),trip=JSON.parse(read('maps/itinerary-data.json'));
class Element{
 constructor(tag='div'){this.tagName=tag;this.children=[];this.listeners={};this.attributes={};this.dataset={};this.style={};this.hidden=false;this._text='';this._value='';}
 set textContent(v){this._text=String(v);this.children=[];}get textContent(){return this._text+this.children.map(x=>x.textContent).join('');}
 set value(v){this._value=v;}get value(){return this._value||(this.tagName==='select'?this.children[0]?.value:'')||'';}
 append(...x){this.children.push(...x);}replaceChildren(...x){this._text='';this.children=x;this._value='';}
 addEventListener(t,f){(this.listeners[t]??=[]).push(f);}setAttribute(k,v){this.attributes[k]=String(v);}
 querySelector(t){return this.children.find(x=>x.tagName===t)||null;}focus(){}scrollIntoView(){}
 click(){for(const f of this.listeners.click||[])f({preventDefault(){}});}change(){for(const f of this.listeners.change||[])f({});}
}
const ids=[...html.matchAll(/\bid="([^"]+)"/g)].map(m=>m[1]),elements=new Map(ids.map(id=>['#'+id,new Element(id==='place-select'?'select':'div')]));
for(const c of ['day-switch','wordmark'])elements.set('.'+c,new Element());
const get=s=>{assert(elements.has(s),'selector missing '+s);return elements.get(s);};
const days=trip.days.map(d=>Object.assign(new Element('button'),{dataset:{day:d.id}})),tabs=['now','plan','map','prep'].map(tab=>Object.assign(new Element('button'),{dataset:{tab}}));
get('#timing-details').append(new Element('summary'));const messages=[];get('#guide-map').contentWindow={postMessage:(data,origin)=>messages.push({data,origin})};
const storage=new Map(),location={href:'https://guide.example/index.html',hash:'#now',origin:'https://guide.example',protocol:'https:'};
const document={body:{dataset:{}},querySelector:get,querySelectorAll:s=>s==='[data-day]'?days:s==='[data-tab]'?tabs:[],createElement:t=>new Element(t),createTextNode:t=>Object.assign(new Element('text'),{textContent:t})};
const ctx={document,location,history:{replaceState(a,b,h){location.hash=h;}},localStorage:{getItem:k=>storage.get(k),setItem:(k,v)=>storage.set(k,v)},navigator:{},caches:{keys:async()=>[]},URL,Intl,Date,JSON,Promise,Error,Set,setTimeout:()=>1,clearTimeout(){}};ctx.window=ctx;ctx.addEventListener=()=>{};ctx.scrollTo=()=>{};vm.createContext(ctx);
for(const p of ['trip.js','core.js','app.js'])vm.runInContext(read('mobile-guide/site/'+p),ctx,{filename:p});
const state=()=>JSON.parse(storage.get(ctx.GuideCore.KEY));let count=0;function test(n,fn){fn();count++;console.log('PASS '+n);}
test('unique page IDs and app boots all selectors',()=>{assert.equal(new Set(ids).size,ids.length);assert.equal(state().step,'sun-academy');});
test('both days show synthetic unpaid transport',()=>{assert.match(get('#priority-status').textContent,/未购票/);days[1].click();assert.equal(state().day,'sun');assert.equal(state().done.length,0);assert.match(get('#priority-train').textContent,/示例返程/);});
test('timing details opens without completing steps',()=>{get('#show-timing').click();assert.equal(document.body.dataset.view,'prep');assert.equal(get('#timing-details').open,true);assert.equal(state().done.length,0);});
test('all six checklist entries share authoritative source',()=>{assert.equal(get('#checklist').children.length,6);const b=get('#checklist').children[0].children[0];b.checked=true;b.change();assert(state().checks.includes(0));});
test('all thirteen stops render, map follows, final completion idempotent',()=>{for(const d of trip.days){days[d.id==='sat'?0:1].click();for(let i=0;i<d.steps.length;i++){get('#timeline').children[i].children[0].click();assert.equal(state().step,d.steps[i].id);assert.equal(messages.at(-1).data.step,d.steps[i].id);assert(get('#quick-transport').textContent);}}get('#complete').click();assert(state().done.includes('sun-return'));get('#complete').click();assert.equal(state().done.filter(x=>x==='sun-return').length,1);});
test('map tab hides time panel and preserves selection',()=>{tabs[2].click();assert(get('#travel-priority').hidden);assert.equal(messages.at(-1).data.step,'sun-return');});
console.log(count+' detached DOM interaction checks passed. Layout/device APIs not verified.');
