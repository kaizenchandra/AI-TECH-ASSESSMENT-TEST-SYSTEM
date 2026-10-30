import https from 'node:https';
import http from 'node:http';
import {readFileSync} from 'node:fs';
import {timingSafeEqual,randomUUID} from 'node:crypto';
import {validateInput,podSpec} from './policy.mjs';
const namespace=process.env.EXECUTION_NAMESPACE??'assessment-sandbox';
if(!/^[a-z0-9-]+$/.test(namespace))throw Error('Invalid namespace');
const image=process.env.RUNNER_IMAGE??'',auth=process.env.RUNNER_TOKEN??'';
if(auth.length<32)throw Error('RUNNER_TOKEN must have at least 32 characters');
const ca=readFileSync('/var/run/secrets/kubernetes.io/serviceaccount/ca.crt');
const host=process.env.KUBERNETES_SERVICE_HOST,port=process.env.KUBERNETES_SERVICE_PORT_HTTPS??'443';
async function kube(path,method='GET',body,limit=2_000_000,raw=false){return new Promise((resolve,reject)=>{
 const req=https.request({hostname:host,port,ca,path,method,timeout:10000,headers:{Authorization:'Bearer '+readFileSync('/var/run/secrets/kubernetes.io/serviceaccount/token','utf8'),'Content-Type':'application/json'}},res=>{let size=0;const parts=[];res.on('data',part=>{size+=part.length;if(size>limit){res.destroy();reject(Error('Response exceeds limit'));}else parts.push(part);});res.on('error',reject);res.on('end',()=>{const text=Buffer.concat(parts).toString();if(res.statusCode>=300){reject(Error('Kubernetes operation failed '+res.statusCode));return;}try{resolve(raw?text:JSON.parse(text));}catch{resolve(text);}});});req.on('error',reject);req.on('timeout',()=>req.destroy(Error('Kubernetes timeout')));req.end(body?JSON.stringify(body):undefined);
 });}
const pause=ms=>new Promise(r=>setTimeout(r,ms));
// A crash can happen between ConfigMap creation and Job creation. Sweep bounded,
// labeled resources after the maximum request/lease interval, including on restart.
async function sweep(){
 const core=`/api/v1/namespaces/${namespace}`,batch=`/apis/batch/v1/namespaces/${namespace}/jobs`;
 for(const [path,kind] of [[core+'/configmaps','configmaps'],[batch,'jobs']]){
  const list=await kube(path+'?labelSelector=app%3Dassessment-execution&limit=100');
  for(const item of list.items??[])if(Date.now()-Date.parse(item.metadata.creationTimestamp)>900000)
   await kube(path+'/'+item.metadata.name,'DELETE').catch(()=>{});
 }
}
await sweep();
setInterval(()=>sweep().catch(()=>console.error('Execution cleanup failed')),60000).unref();
async function execute(input,index){const name='exec-'+randomUUID(),core=`/api/v1/namespaces/${namespace}`,batch=`/apis/batch/v1/namespaces/${namespace}/jobs`;let created=false;
 try {
  await kube(core+'/configmaps','POST',{apiVersion:'v1',kind:'ConfigMap',metadata:{name,labels:{app:'assessment-execution'}},immutable:true,data:{source:input.source,stdin:input.inputs[index]}});created=true;
  await kube(batch,'POST',podSpec(name,name,input,image));
  const deadline=Date.now()+60000;let pod;
  while(Date.now()<deadline){const pods=await kube(core+'/pods?labelSelector='+encodeURIComponent('job-name='+name));pod=pods.items?.[0];if(pod?.status?.phase==='Succeeded'||pod?.status?.phase==='Failed')break;await pause(500);}
  const terminated=pod?.status?.containerStatuses?.[0]?.state?.terminated;
  if(!terminated)throw Error('Runner infrastructure timeout');
  if(terminated.exitCode===70)throw Error('Runner runtime configuration failed');
  const log=await kube(core+`/pods/${pod.metadata.name}/log?limitBytes=65537`, 'GET',undefined,65537,true);
  const stdout=typeof log==='string'?log:JSON.stringify(log);
  const status=terminated.reason==='OOMKilled'?'MEMORY_LIMIT':Buffer.byteLength(stdout)>65536?'OUTPUT_LIMIT':({0:'OK',42:'COMPILE_ERROR',122:'OUTPUT_LIMIT',124:'TIME_LIMIT',137:'TIME_LIMIT'})[terminated.exitCode]??'RUNTIME_ERROR';
  return {status,stdout:stdout.slice(0,65536)};
 }finally{await kube(batch+'/'+name+'?propagationPolicy=Foreground','DELETE').catch(()=>{});if(created)await kube(core+'/configmaps/'+name,'DELETE').catch(()=>{});}
}
let active=false;
http.createServer(async(req,res)=>{
 res.setHeader('Content-Type','application/json');const supplied=Buffer.from(req.headers.authorization??''),expected=Buffer.from('Bearer '+auth);
 if(supplied.length!==expected.length||!timingSafeEqual(supplied,expected)){res.writeHead(401).end('{}');return;}
 if(req.method!=='POST'||req.url!=='/v1/executions'){res.writeHead(404).end('{}');return;}
 if(active){res.writeHead(429,{'Retry-After':'10'}).end('{}');return;}
 active=true;
 try {let size=0;const chunks=[];for await(const chunk of req){size+=chunk.length;if(size>750000)throw Error('Request too large');chunks.push(chunk);}const input=validateInput(JSON.parse(Buffer.concat(chunks)));await kube('/apis/node.k8s.io/v1/runtimeclasses/gvisor');
  const cases=[];for(let i=0;i<input.inputs.length;i+=4)cases.push(...await Promise.all(input.inputs.slice(i,i+4).map((_,j)=>execute(input,i+j))));res.end(JSON.stringify({cases}));
 }catch{res.writeHead(503).end(JSON.stringify({error:'EXECUTION_INFRASTRUCTURE_UNAVAILABLE'}));}finally{active=false;}
}).listen(8080,'0.0.0.0');
