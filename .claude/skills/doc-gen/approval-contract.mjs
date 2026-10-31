/** 선언된 이력·선택 스냅샷 복사 모델의 소유·전체 컬럼·리비전 경계를 검증한다. */
export function approvalContractErrors(domain){
 const all=domain.entities??[],byId=new Map(all.map(e=>[e.id,e])),errors=[];
 const names=e=>new Set((e.attributes??[]).map(a=>a.physical_name??a.name));
 for(const source of all.filter(e=>e.copy_mapping)){
  const kinds=Object.hasOwn(source.copy_mapping,'snapshot')?['snapshot','history']:['history'];
  for(const kind of kinds){
   const copy=byId.get(source.copy_mapping[kind]);
   const fail=msg=>errors.push(`${source.id}/${kind}: ${msg}`);
   if(!copy){fail('복사 모델이 없다.');continue;}
   if(copy[kind+'_copy_of']!==source.id)fail('원본 연결이 다르다.');
   const ns=names(copy),isChild=Boolean(copy[kind+'_parent']);
   for(const n of names(source))if(!ns.has(n))fail('원본 컬럼 누락: '+n);
   const key=kind==='snapshot'?'SNP_ID':'HIST_ID',set=kind==='snapshot'?'REQUEST_NO':'HISTORY_NO';
   const pk=copy.attributes.filter(a=>a.pk).map(a=>a.physical_name??a.name);
   if(pk.length!==1||pk[0]!==key||!ns.has(set))fail('복사 행 PK와 세트 키가 필요하다.');
   if(kind==='snapshot')for(const n of ['BASE_REVISION','APPLIED_REVISION'])if(ns.has(n)===isChild)fail('기준/결과 리비전은 마스터 SNP에만 필요하다.');
   if(kind==='history'&&ns.has('HIST_DTM')===isChild)fail('저장일시는 마스터 HIST에만 필요하다.');
   if(isChild){const p=byId.get(copy[kind+'_parent']);if(!p||p.storage_role!==kind)fail('같은 문맥의 마스터 복사 참조가 필요하다.');if(names(source).has('REVISION'))fail('현재 자식에 독립 업무 리비전을 두지 않는다.');}
   else if(!names(source).has('REVISION'))fail('현재 마스터에 업무 리비전이 필요하다.');
  }
 }
 return errors;
}
