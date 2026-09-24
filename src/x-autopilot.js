const key="publisher-x-autopilot-v1";
const feedbackKey=key+"-feedback";
let localItems=JSON.parse(localStorage.getItem(key)||"[]");
let feedback=JSON.parse(localStorage.getItem(feedbackKey)||"[]");
let remoteItems=[];

function statusFor(x){
  return (x.topic==="politics"||x.topic==="breaking-news"||x.confidence<85)?"review":"auto";
}
function allItems(){
  const seen=new Set();
  return [...remoteItems,...localItems].filter(x=>{
    const id=x.id||x.text;
    if(seen.has(id)) return false;
    seen.add(id);
    return true;
  });
}
function saveLocal(){
  localStorage.setItem(key,JSON.stringify(localItems));
  render();
}
function saveFeedback(){
  localStorage.setItem(feedbackKey,JSON.stringify(feedback));
  renderFeedbackStatus();
}
function renderFeedbackStatus(){
  const el=document.getElementById("feedbackStatus");
  if(el) el.textContent=feedback.length+" feedback item"+(feedback.length===1?"":"s")+" saved";
}
function recordFeedback(x,action){
  feedback.push({
    id:x.id||crypto.randomUUID(),
    action,
    interest:x.interest||"",
    topic:x.topic||"",
    category:x.topic||"",
    source_title:x.source_title||"",
    created_at:new Date().toISOString(),
    applied:false
  });
  saveFeedback();
}
async function loadRemoteQueue(){
  try{
    const r=await fetch("./automation/queue.json?ts="+Date.now(),{cache:"no-store"});
    if(!r.ok) throw new Error("HTTP "+r.status);
    const data=await r.json();
    remoteItems=Array.isArray(data)?data:[];
  }catch(err){
    console.warn("Queue load failed",err);
    remoteItems=[];
  }
  render();
}
function makeFeedbackButton(label,action,x,cls="secondary"){
  const b=document.createElement("button");
  b.className="btn "+cls;
  b.textContent=label;
  b.onclick=()=>{recordFeedback(x,action); b.textContent=label+" ✓";};
  return b;
}
function render(){
  const items=allItems();
  const q=document.getElementById("queue");
  q.innerHTML="";
  if(!items.length){
    q.innerHTML="<div class=\"post\"><p class=\"muted\">No queued items yet. Run the X Autopilot workflow in dry-run mode to discover fresh content.</p></div>";
  }
  items.forEach(x=>{
    const isRemote=remoteItems.includes(x);
    const el=document.createElement("div");
    el.className="post";

    const meta=document.createElement("div");
    meta.className="meta";
    const pill=document.createElement("span");
    pill.className="pill";
    pill.textContent=x.interest||x.topic||"general";
    const info=document.createElement("span");
    const source=x.source_name||x.source_title||"";
    info.textContent=(x.confidence||0)+"% · "+(x.status==="auto"?"AUTO":"REVIEW")+" · "+(isRemote?"GITHUB QUEUE":"LOCAL DRAFT")+(source?" · "+source:"");
    meta.appendChild(pill); meta.appendChild(info); el.appendChild(meta);

    const p=document.createElement("p");
    p.textContent=x.text||"";
    el.appendChild(p);

    const actions=document.createElement("div");
    actions.style.cssText="margin-top:10px;display:flex;gap:8px;flex-wrap:wrap";

    actions.appendChild(makeFeedbackButton("Keep","keep",x));
    actions.appendChild(makeFeedbackButton("Engage","engage",x));
    actions.appendChild(makeFeedbackButton("Reject","reject",x,"danger"));
    actions.appendChild(makeFeedbackButton("Ignore","ignore",x));

    if(x.source && x.source!=="manual-browser"){
      const a=document.createElement("a");
      a.href=x.source; a.target="_blank"; a.rel="noopener noreferrer";
      a.className="btn secondary"; a.textContent="Open source";
      actions.appendChild(a);
    }

    if(!isRemote){
      const toggle=document.createElement("button");
      toggle.className="btn secondary";
      toggle.textContent=x.status==="auto"?"Send to review":"Approve";
      toggle.onclick=()=>{x.status=x.status==="auto"?"review":"auto";saveLocal();};
      actions.appendChild(toggle);

      const del=document.createElement("button");
      del.className="btn danger"; del.textContent="Remove";
      del.onclick=()=>{localItems=localItems.filter(y=>y.id!==x.id);saveLocal();};
      actions.appendChild(del);
    }

    el.appendChild(actions);
    q.appendChild(el);
  });

  document.getElementById("queuedCount").textContent=items.length;
  document.getElementById("approvedCount").textContent=items.filter(x=>x.status==="auto").length;
  document.getElementById("manualCount").textContent=items.filter(x=>x.status==="review").length;
  document.getElementById("postedCount").textContent="—";
  renderFeedbackStatus();
}

document.getElementById("addBtn").onclick=()=>{
  const text=document.getElementById("draft").value.trim();
  if(!text)return;
  const topic=document.getElementById("category").value;
  const confidence=Math.max(0,Math.min(100,+document.getElementById("confidence").value||0));
  const item={id:crypto.randomUUID(),topic,text,confidence,status:statusFor({topic,confidence}),source:"manual-browser"};
  localItems.unshift(item);
  document.getElementById("draft").value="";
  saveLocal();
};

document.getElementById("copyFeedbackBtn").onclick=async()=>{
  const data=JSON.stringify(feedback,null,2);
  try{
    await navigator.clipboard.writeText(data);
    document.getElementById("feedbackStatus").textContent=feedback.length+" feedback items copied";
  }catch(e){
    prompt("Copy this feedback JSON:",data);
  }
};

render();
loadRemoteQueue();
