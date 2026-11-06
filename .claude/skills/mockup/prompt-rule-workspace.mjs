export {richTextBrowserAssets as promptRuleBrowserAssets,richTextBrowserScripts as promptRuleMarkdownScripts} from './rich-text.mjs';
/** 두 영역과 필드 이름은 화면 요건의 설정 한 곳에서만 선언한다. */
export function promptRuleWorkspace(table,model){
 if(!table)return null;
 const setting=table.rows.find(row=>row[0].trim()==='설정');if(!setting)throw new Error('프롬프트 규칙 작업대 설정이 없다');
 const config=JSON.parse(setting[1]),title=table=>table.heading.replace(/^\d+(?:-\d+)?\.?\s*/,'').replace(/\s*—\s*(폼|그리드)$/,'');
 if(!config||Array.isArray(config)||typeof config!=='object')throw new Error('프롬프트 규칙 작업대 설정은 객체다');
 for(const key of ['list','form','id','title','enabled','description','body'])if(typeof config[key]!=='string'||!config[key])throw new Error('프롬프트 규칙 작업대 설정 누락: '+key);
 const list=model.columns.find(table=>title(table)===config.list),form=model.columns.find(table=>title(table)===config.form);
 if(list!==model.columns[0]||!form||!/—\s*폼$/.test(form.heading))throw new Error('프롬프트 규칙 작업대 목록·폼을 확인한다');
 for(const key of ['id','title','enabled'])if(!list.rows.some(row=>row[0]===config[key]))throw new Error('프롬프트 규칙 목록 필드가 없다: '+key);
 if(form.rows.length!==2||![config.description,config.body].every(field=>form.rows.some(row=>row[0]===field)))throw new Error('프롬프트 규칙 폼에는 설명·본문 두 필드만 둔다');
 return config;
}
