const fs=require('node:fs'), vm=require('node:vm'), assert=require('node:assert/strict');
const listeners={}, shown=[], opened=[];
const scope='https://xmpp.starception.com:8443/family-helper/';
const self={addEventListener:(name,fn)=>listeners[name]=fn,
  registration:{scope,showNotification:async(title,options)=>shown.push({title,options})},
  clients:{openWindow:async url=>opened.push(url)}};
vm.runInNewContext(fs.readFileSync(__dirname+'/sw.js','utf8'),{self,Date,Number,encodeURIComponent});
(async()=>{
  const id='11111111-2222-3333-4444-555555555555';
  for(const payload of [{callId:id,kind:'video',expires:Date.now()+30000},{callId:id,expires:0},{callId:'https://attacker.test',expires:Date.now()+30000}]){
    let work;listeners.push({data:{json:()=>payload},waitUntil:p=>work=p});await work;
  }
  assert.equal(shown.length,3,'Every delivery must show a visible notification');
  assert.equal(shown[0].title,'Incoming family call');assert.equal(shown[1].title,'Family call alert');
  assert.equal(shown[2].options.data.callId,'');
  let work;listeners.notificationclick({notification:{close(){},data:{callId:id}},waitUntil:p=>work=p});await work;
  assert.equal(opened[0],scope+'open.html#call='+id);
  console.log('PASS: live/expired/malformed alerts remain visible; clicks stay on the helper origin.');
})().catch(error=>{console.error(error);process.exitCode=1});
