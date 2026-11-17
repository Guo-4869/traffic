// One-time migration from hard-coded Chinese text to Android resources.
const fs=require('fs'),path=require('path');
const zh=require('./zh.json'),en=require('./en.json');
if(zh.length!==en.length)throw Error(`Translation count mismatch ${zh.length}/${en.length}`);
const directory='app/src/main/java/com/traffic/app';
for(const file of fs.readdirSync(directory).filter(f=>f.endsWith('.java'))){
  const target=path.join(directory,file);let source=fs.readFileSync(target,'utf8');
  source=source.replace(/"(?:[^"\\]|\\.)*"/g,raw=>{let i=zh.indexOf(JSON.parse(raw));return i<0?raw:`I18n.t(R.string.ui_${String(i).padStart(3,'0')})`;});
  fs.writeFileSync(target,source);
}
const escape=s=>'"'+s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/\\/g,'\\\\').replace(/\n/g,'\\n').replace(/"/g,'\\"').replace(/'/g,"\\'")+'"';
for(const [dir,values] of [['values',en],['values-zh',zh]]){
 fs.mkdirSync('app/src/main/res/'+dir,{recursive:true});
 fs.writeFileSync('app/src/main/res/'+dir+'/strings.xml','<resources>\n'+values.map((s,i)=>`    <string name="ui_${String(i).padStart(3,'0')}" formatted="false">${escape(s)}</string>`).join('\n')+'\n</resources>\n');
}
