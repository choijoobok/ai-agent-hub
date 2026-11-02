import assert from 'node:assert/strict';
import {test} from 'node:test';
import {snapshotView} from './snapshot-view.mjs';
const snap={id:'101',managementVersion:'2',deleted:false,applicableGroups:['201','202','203','204','205','206','207','9007199254740993'].map((groupId,index)=>({groupId,mappingActive:index%2===0}))};
test('반영 결과 8개를 그대로 풀고 큰 ID를 문자열로 유지한다',()=>{
 const html=snapshotView(JSON.stringify(snap),1,{applicableGroups:'적용 가능 그룹',mappingActive:'매핑 활성 여부'});
 assert.match(html,/data-snapshot-array-count="8"/);assert.match(html,/9007199254740993/);assert.match(html,/적용 가능 그룹/);
 assert.doesNotMatch(html,/<input|<textarea|저장<\/button>/);
});
test('삭제 스냅샷은 마지막 구성을 유지하되 유효 구성으로 표시하지 않는다',()=>{
 const html=snapshotView(JSON.stringify({...snap,deleted:true}),1);
 assert.match(html,/data-snapshot-state="deleted"/);assert.match(html,/마지막 구성/);assert.match(html,/목록 8개/);
});
test('파싱 실패·미지원 형식은 원문을 보존하고 임의 현재값을 만들지 않는다',()=>{
 for(const [raw,version]of [['broken',1],[JSON.stringify(snap),2]]){
  const html=snapshotView(raw,version);assert.match(html,/role="alert"/);assert.match(html,/data-snapshot-raw/);assert.doesNotMatch(html,/data-snapshot-properties/);
 }
});
test('원문이 없으면 원문 보기와 빈 원문 영역을 만들지 않는다',()=>{
 for(const raw of [null,undefined,'',' \t\r\n ',snap]){
  for(const version of [1,2]){
   const html=snapshotView(raw,version);
   assert.match(html,/스냅샷 원문이 없습니다/);
   assert.doesNotMatch(html,/data-snapshot-raw|JSON 원문 보기|<pre|data-snapshot-properties/);
  }
 }
});
test('속성·값·원문의 HTML을 실행 가능한 태그로 만들지 않는다',()=>{
 const html=snapshotView(JSON.stringify({...snap,'<img src=x onerror=alert(1)>':'<script>alert(1)</script>'}),1);
 assert.doesNotMatch(html,/<img|<script>/);assert.match(html,/&lt;script&gt;/);
});
