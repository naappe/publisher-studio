const seed = [
  {id:"demo-1",topic:"technology",text:"Testing the new X Autopilot queue. Original posts only, duplicate protection on.",confidence:96,status:"auto"},
  {id:"demo-2",topic:"business",text:"A useful business insight belongs here once a verified source is available.",confidence:91,status:"auto"},
  {id:"demo-3",topic:"politics",text:"Political content is intentionally held for manual review.",confidence:94,status:"review"}
];

const key="publisher-x-autopilot-v1";
let items=JSON.parse(localStorage.getItem(key)||"null")||seed;

function save(){localStorage.setItem(key,JSON.stringify(items));render()}
function statusFor(x){return (x.topic==="politics"||x.topic==="breaking-news"||x.confidence<85)?"review":"auto"}
function render(){
  const q=document.getElementById("queue"); q.innerHTML="";
  items.forEach((x,i)=>{
    const el=document.createElement("div"); el.className="post";
    el.innerHTML=`<div class="meta"><span class="pill">${x.topic}</span><span>${x.confidence}% · ${x.status==="auto"?"AUTO":"REVIEW"}</span></div><p></p><div style="margin-top:10px;display:flex;gap:8px"><button class="btn secondary" data-toggle="${i}">${x.status==="auto"?"Send to review":"Approve"}</button><button class="btn danger" data-del="${i}">Remove</button></div>`;
    el.querySelector("p").textContent=x.text;
    q.appendChild(el);
  });
  document.getElementById("queuedCount").textContent=items.length;
  document.getElementById("approvedCount").textContent=items.filter(x=>x.status==="auto").length;
  document.getElementById("manualCount").textContent=items.filter(x=>x.status==="review").length;
  document.getElementById("postedCount").textContent=JSON.parse(localStorage.getItem(key+"-posted")||"[]").length;
}
document.addEventListener("click",e=>{
  if(e.target.dataset.del!==undefined){items.splice(+e.target.dataset.del,1);save()}
  if(e.target.dataset.toggle!==undefined){const x=items[+e.target.dataset.toggle];x.status=x.status==="auto"?"review":"auto";save()}
});
document.getElementById("addBtn").onclick=()=>{
  const text=document.getElementById("draft").value.trim();
  if(!text)return;
  const topic=document.getElementById("category").value;
  const confidence=Math.max(0,Math.min(100,+document.getElementById("confidence").value||0));
  items.unshift({id:crypto.randomUUID(),topic,text,confidence,status:statusFor({topic,confidence})});
  document.getElementById("draft").value="";
  save();
};
render();