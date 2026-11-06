/** 조회 정책 작업대는 화면 요건의 명시 설정만 사용한다. */
export function queryPolicyWorkspace(table,model){
 if(!table)return null;
 const row=table.rows.find(row=>row[0].trim()==='설정');
 if(!row)throw new Error('조회 정책 작업대 설정이 없다');
 const config=JSON.parse(row[1]);
 if(!config||Array.isArray(config)||typeof config!=='object')throw new Error('조회 정책 작업대 설정은 객체다');
 const title=table=>table.heading.replace(/^\d+(?:-\d+)?\.?\s*/,'').replace(/\s*—\s*(폼|그리드)$/,'');
 for(const key of ['scopeList','scopeForm','parameters','constraints','filters','master'])if(typeof config[key]!=='string'||!model.columns.some(table=>title(table)===config[key]))throw new Error('조회 정책 작업대 영역이 없다: '+key);
 if(typeof config.catalogKey!=='string'||!model.columns.find(table=>title(table)===config.master).rows.some(row=>row[0]===config.catalogKey))throw new Error('조회 정책 작업대 카탈로그 필드를 확인한다');
 if(!Array.isArray(config.columns)||config.columns.some(column=>!column||typeof column['컬럼명']!=='string'||typeof column['자료형']!=='string'))throw new Error('조회 정책 작업대 컬럼 후보를 확인한다');
 const keys=config.columns.map(column=>String(column[config.catalogKey]??'')+'\0'+column['컬럼명']);
 if(new Set(keys).size!==keys.length)throw new Error('조회 정책 작업대 컬럼 후보가 중복이다');
 return config;
}
