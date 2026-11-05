import test from 'node:test';
import assert from 'node:assert/strict';
import {blocks} from './gen.mjs';

test('프롬프트 입력 준비는 화면별로 읽히고 원 계약·분류·추가 항목이 누락되지 않는다',()=>{
  const out=blocks([{id:'REQ-610',title:'규칙',prompt_contract:{scope:'원 계약',admin_input_preparation:{
    scope:'준비 범위',classification:{llm:'업무 명세',server:'보호값 처리',audit:'변경 근거'},
    conditions:['현재 권한 확인','모델 전송 확인'],
    stages:[{name:'SQL 작성',content:'선택한 카탈로그만'}],guides:{structure:'items[].status의 원소 의미'},
    sources:{business_api:{screen:'SCR-004',reqs:['REQ-608'],llm:'유효 입력 명세',server:'인증값 주입',audit:'검수자',future_field:'새 필드도 보존'},
      catalog_relation:{screen:'SCR-039',llm:'전체 쌍·유일성·근거 요약'}},next_design:'최종 포맷은 후속',unknown_part:{extra:'미분류 내용도 보존'}
  }}}]);
  assert.match(out,/#### REQ-610 관리 화면의 프롬프트 입력 준비/);
  assert.match(out,/###### SCR-004 — 업무 API/);
  assert.match(out,/###### SCR-039 — 테이블 관계/);
  for(const value of ['원 계약','준비 범위','업무 명세','보호값 처리','변경 근거','현재 권한 확인','모델 전송 확인','SQL 작성','선택한 카탈로그만','items[].status의 원소 의미','REQ-608','유효 입력 명세','인증값 주입','검수자','새 필드도 보존','전체 쌍·유일성·근거 요약','최종 포맷은 후속','미분류 내용도 보존']) assert.ok(out.includes(value),value);
  assert.ok(out.indexOf('###### SCR-004')<out.indexOf('인증값 주입'));
  assert.equal((out.match(/원 계약/g)||[]).length,1);
});
