'use strict';
const http=require('node:http'),fs=require('node:fs/promises'),path=require('node:path');
const SOURCE='https://batclient.xyz/changelog.html',MAX=1024*1024,ROOT=path.resolve(__dirname,'../ui');
async function fetchChangelog(){
  const response=await fetch(SOURCE,{redirect:'error',signal:AbortSignal.timeout(8000),headers:{Accept:'text/html','User-Agent':'BatClient-Launcher-Preview/0.1'}});
  if(!response.ok)throw Error('Changelog response failed');
  if(Number(response.headers.get('content-length'))>MAX)throw Error('Changelog too large');
  const chunks=[];let length=0;
  for await(const chunk of response.body){length+=chunk.length;if(length>MAX)throw Error('Changelog too large');chunks.push(chunk);}
  return new TextDecoder('utf-8',{fatal:true}).decode(Buffer.concat(chunks));
}
function createPreviewServer({getChangelog=fetchChangelog}={}){
  return http.createServer(async(req,res)=>{
    try{
      if(req.method!=='GET'){res.writeHead(405);res.end();return;}
      const pathname=new URL(req.url,'http://127.0.0.1').pathname;
      if(pathname==='/api/changelog'){
        try{const html=await getChangelog();res.writeHead(200,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store'});res.end(JSON.stringify({html}));}
        catch{res.writeHead(502,{'Content-Type':'application/json'});res.end('{"error":"Changelog unavailable"}');}return;
      }
      const decoded=decodeURIComponent(pathname==='/'?'/index.html':pathname);
      const file=path.resolve(ROOT,'.'+decoded);
      if(!file.startsWith(ROOT+path.sep)||decoded.includes('\\')||decoded.includes('\0')){res.writeHead(403);res.end();return;}
      const mime={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.png':'image/png'}[path.extname(file)];
      if(!mime){res.writeHead(404);res.end();return;}
      const body=await fs.readFile(file);res.writeHead(200,{'Content-Type':mime,'Content-Security-Policy':"default-src 'self'; img-src 'self' data:; script-src 'self'; style-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'"});res.end(body);
    }catch{res.writeHead(404);res.end();}
  });
}
module.exports={createPreviewServer,fetchChangelog};
if(require.main===module){const server=createPreviewServer();server.listen(4173,'127.0.0.1',()=>console.log('Launcher preview: http://127.0.0.1:4173'));}
