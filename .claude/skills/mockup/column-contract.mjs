/** 업무 컬럼 목록에 공통 감사 컬럼을 합성한다. 조회 전용 여부는 데이터 수명주기를 바꾸지 않는다. */
const plain=value=>String(value??'').replace(/`|\*/g,'').trim();
const fields=[['등록자','regId','REG_ID','텍스트','120/좌'],['등록일시','regDtm','REG_DTM','일시','170/좌'],['수정자','modId','MOD_ID','텍스트','120/좌'],['수정일시','modDtm','MOD_DTM','일시','170/좌']];
const index=(table,name)=>table.header.findIndex(h=>plain(h)===name);
const auditField=(table,row)=>fields.find(f=>plain(row[0])===f[0]||plain(row[index(table,'필드명')])===f[1]);
export function withAuditColumns(table,policy=table.auditPolicy??{kind:'등록·수정'}) {
  if(/—\s*폼$/.test(table.heading))return table;
  if(!['등록·수정','추가 전용','사용자 제외'].includes(policy.kind))throw new Error('감사 컬럼의 데이터 성격이 잘못됐다');
  if(policy.kind==='사용자 제외'&&!/^사용자 명시[:：]/.test(policy.reason??''))throw new Error('감사 컬럼 제외에는 사용자 명시 근거가 필요하다');
  const header=[...table.header];
  for(const name of ['필드명','입력방식','폭/정렬','필수','편집','선택 출처','검증','설명','예시','기본 표시'])if(!header.some(h=>plain(h)===name))header.push(name);
  const original=table.rows.filter(row=>!/^감사\s*[24넷둘]/.test(plain(row[0])));
  const business=original.filter(row=>!auditField(table,row)).map(row=>header.map((_,i)=>row[i]??''));
  const selected=policy.kind==='사용자 제외'?[]:fields.slice(0,policy.kind==='추가 전용'?2:4);
  const audits=selected.map(field=>{
    const old=original.find(row=>auditField(table,row)===field);
    const row=header.map((_,i)=>old?.[i]??'');
    const defaults={'필드명':field[1],'입력방식':field[3],'폭/정렬':field[4],'필수':'입력 대상 아님','편집':'읽기전용','선택 출처':'서버 기록','검증':'서버가 기록하며 직접 입력·수정하지 않는다.','설명':`${field[0]} — ${policy.kind==='추가 전용'?'이 행의 최초 기록':'원본 행의 최초 등록·최종 수정'} 감사값`,'예시':'—','기본 표시':'기본 표시'};
    row[0]=field[0];
    for(const [name,value] of Object.entries(defaults)){
      const i=header.findIndex(h=>plain(h)===name);
      if(['편집','필수','기본 표시'].includes(name)||!row[i]||row[i]==='—')row[i]=value;
    }
    return row;
  });
  return {...table,header,rows:[...business,...audits],auditPolicy:policy};
}
