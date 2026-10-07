(function(root){
  const KEY='xiangjiang-public-demo-v1';
  function initial(trip,saved){
    const days=trip.days;const ids=new Set(days.flatMap(d=>d.steps.map(s=>s.id)));
    // Stable step IDs survive a schedule move; their prefixes are not dates.
    const owner=days.find(d=>d.steps.some(s=>s.id===saved?.step));
    const day=owner?.id||(days.some(d=>d.id===saved?.day)?saved.day:'sat');
    const current=days.find(d=>d.id===day);
    // Checklist length comes from the authoritative maps/itinerary-data.json checklist, not a
    // literal — a hardcoded bound silently drops persisted ticks whenever the list grows.
    const checklistSize=Array.isArray(trip.checklist)?trip.checklist.length:0;
    const changed=Boolean(trip.scheduleRevision&&saved?.planRevision!==trip.scheduleRevision);
    return {day,step:current.steps.some(s=>s.id===saved?.step)?saved.step:current.defaultStep,done:Array.isArray(saved?.done)?[...new Set(saved.done.filter(x=>ids.has(x)))]:[],checks:Array.isArray(saved?.checks)?[...new Set(saved.checks.filter(i=>Number.isInteger(i)&&i>=0&&i<checklistSize&&(!changed||![1,2].includes(i))))]:[],planRevision:trip.scheduleRevision};
  }
  function select(trip,state,id){const day=trip.days.find(d=>d.steps.some(s=>s.id===id));if(!day)throw Error('Unknown step');return {...state,day:day.id,step:id};}
  function complete(trip,state){const steps=trip.days.flatMap(d=>d.steps);const i=steps.findIndex(s=>s.id===state.step);const done=[...new Set([...state.done,state.step])];const next=steps[i+1];return next?select(trip,{...state,done},next.id):{...state,done};}
  const api={KEY,initial,select,complete};if(typeof module!=='undefined')module.exports=api;else root.GuideCore=api;
})(typeof window==='undefined'?globalThis:window);
