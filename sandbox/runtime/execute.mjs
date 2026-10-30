// Trusted image entrypoint. Runs only inside a gVisor pod; never use on the application host.
import {spawn} from 'node:child_process';
import {readFile,writeFile} from 'node:fs/promises';
const language=process.argv[2], timeoutMs=Number(process.argv[3]);
const commands={java:['java',['-Xmx128m','-XX:ActiveProcessorCount=1','Main']],javascript:['node',['--max-old-space-size=128','program.js']],python:['python3',['-I','program.py']]};
if(!commands[language]||!Number.isInteger(timeoutMs)||timeoutMs<100||timeoutMs>10000)process.exit(70);
const filename=language==='java'?'Main.java':language==='python'?'program.py':'program.js';
await writeFile('/work/'+filename,await readFile('/source/source'));
async function execute(command,args,input,timeout){return new Promise(resolve=>{
 const child=spawn(command,args,{cwd:'/work',shell:false,detached:true,env:{PATH:'/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin',LANG:'C.UTF-8',HOME:'/work'},stdio:['pipe','pipe','pipe']});
 let size=0,reason=null;const kill=()=>{try{process.kill(-child.pid,'SIGKILL');}catch{}};
 const timer=setTimeout(()=>{reason=124;kill();},timeout);
 const output=chunk=>{if(reason)return;size+=chunk.length;if(size>65536){reason=122;kill();return;}process.stdout.write(chunk);};
 child.stdout.on('data',output);child.stderr.on('data',output);child.stdin.on('error',()=>{});child.on('error',()=>{reason=70;});child.on('close',(code)=>{clearTimeout(timer);resolve(reason??code??1);});child.stdin.end(input);
 });}
if(language==='java'){const compiled=await execute('javac',['-J-Xmx128m','Main.java'],'',10000);if(compiled!==0)process.exit(compiled===70?70:42);}
const [command,args]=commands[language];process.exit(await execute(command,args,await readFile('/source/stdin'),timeoutMs));
