/**
 * spec → 사람이 읽는 문서. `documents/spec/`의 논리 문서에서 `documents/generated/` 를 만든다.
 *
 * <p><b>왜 스크립트인가</b> — 파생물의 값은 *정본에서 언제든 같은 것이 다시 나온다* 는
 * 데 있다. 생성을 산문 지시로만 두면 부를 때마다 결과가 흔들리고, 그러면 그 값이 사라진다.
 * 여기 있는 대상은 **결정적으로 다시 나온다.**
 *
 * <p><b>의존성을 갖지 않는다</b> — YAML 은 `hooks/lib.mjs` 의 `parseYaml` 로 읽는다.
 * 그 파서는 일부러 좁고, 좁은 것이 값이다. PyYAML 과 전 spec 을 대조해 차이가 셋뿐임을
 * 확인했고(2026-09-07) 셋 다 *인용 없는 날짜를 문자열로 읽는다* 는 같은 것이다 —
 * 문서 생성에는 문자열이 맞다.
 *
 * <p><b>설계 원칙: 필드를 하나도 떨어뜨리지 않는다.</b> 컬럼을 골라 표를 짜면 고르지 않은
 * 칸이 조용히 사라지고, <b>파생 뷰에서 사라진 항목은 없는 항목이 된다.</b> 그래서 렌더러가
 * 항목의 모든 키를 싣고, `--check` 가 그것을 기계로 센다. 실제로 그 검사가 손으로 짠
 * 아키텍처 표에서 규범 셋이 빠진 것을 잡았다.
 *
 * <pre>
 *   node .claude/skills/doc-gen/gen.mjs                 # 구현된 대상 전부
 *   node .claude/skills/doc-gen/gen.mjs 요구사항정의서 화면정의서
 *   node .claude/skills/doc-gen/gen.mjs --check         # 생성물이 spec 을 다 담았나
 * </pre>
 */

import { readFileSync, writeFileSync, mkdirSync, readdirSync, existsSync, rmSync } from 'node:fs';
import { dirname, join, normalize, posix, resolve as resolvePath } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseYaml } from '../../hooks/lib.mjs';
import { traceReport } from '../../hooks/trace-check.mjs';
import { associatedScreens, featureIds, relationIds } from '../../../aidd-guide/harness/runtime/trace-model.mjs';
import { listSpecSetNames, loadSpecSet } from '../../../aidd-guide/harness/runtime/spec-set.mjs';
import { domainSchema } from '../../../aidd-guide/harness/runtime/domain-schema.mjs';
import { assertHistoryContract } from './history-contract.mjs';
import { approvalContractErrors } from './approval-contract.mjs';
import { menuContractErrors, menuReviewMarkdown, orderByAdminMenu } from './menu-contract.mjs';
import { checkBatchSpec } from '../../../aidd-guide/harness/runtime/batch-contract.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO = join(HERE, '..', '..', '..');
const SPEC = 'documents/spec/';
const OUT = 'documents/generated/';
const RQ = `${OUT}10-요구사항정의서/`;
const TODAY = new Date().toISOString().slice(0, 10);

/** 필드 이름 → 사람이 읽는 라벨. **없는 키는 그대로 낸다** — 조용히 숨기지 않는다. */
const LABEL = {
  title: '제목', name: '이름', detail: '내용', note: '비고', why: '왜', goals: '목적',
  kind: '유형', module: '모듈', scope: '범위', constraint: '제약', area: '영역',
  decide: '확정할 내용', owner: '소유', hard_rule: '**강한 규칙**', hard_rules: '**강한 규칙**',
  hard_requirement: '**전제**', traces_to: '연결', reqs: '요구사항', who: '누가', risk: '위험',
  term: '용어', key: '영문 키', group: '분류', definition: '정의', origin: '출처',
  relation: '우리 것과의 관계', type: '유형', priority: '우선순위', actors: '액터',
  open: '미확정', open2: '미확정', category: '분류', requirement: '요구',
  measurement: '측정 방법', test_case: '테스트 케이스', target: '목표값', direction: '방향',
  aggregation: '집계 규칙',
  jobs: '배치 명세', usecase: '유즈케이스', runbooks: '운영 절차', schedule: '실행 일정',
  batch_id: '기본 등록 ID', bean_id: '구현 빈', seed_cron: '시드 주기', seed_timezone: '시드 시간대', seed_use_yn: '시드 사용 여부',
  seed_lock_seconds: '시드 락 시간(초)', seed_cooldown_seconds: '시드 쿨다운(초)', activation: '활성화 전제',
  parameters: '입력·설정', concurrency: '중복·경합', failure: '실패 처리', cancellation: '중단', retry: '재실행', observability: '결과 확인',
  outcomes: '실행 결과', internal_timers: '내부 주기 작업', contracts_from: '계약 근거',
  peer: '상대', protocol: '프로토콜', prerequisites: '전제', status: '상태', screen: '화면',
  uses_features: '사용 기능', uses_screens: '호출 화면', sources: '구현 소스', source_contract: '구현 메서드',
  screen_cases: '화면 사례 연결', implementation: '구현 상태', entry: '진입 유형', menu_code: '메뉴 코드', menu_parent: '상위 메뉴',
  roadmap: '차수', feature: '소유 기능', agent_contact: 'agent 와의 접점', undecided: '미확정',
  claim: '주장', axis: '축', rank: '순위', metrics: '지표', why_first: '왜 첫째인가',
  was: '전에는', why_demoted: '왜 내렸나', includes: '포함', excludes: '제외', goal: '목표',
  object: '대상', mapping: '매핑', meaning: '뜻', users: '사용자', form: '형태',
  responsibility: '책임', forbidden: '금지', holds: '무엇을 갖나', source: '원천',
  layer: '계층', layers: '계층', infra_requirements: '인프라 요건', implementation: '구현',
  not_supported: '지원하지 않는 것', stages: '단계', means: '수단', pair: '짝',
  severity: '성격', left: '한쪽', right: '다른 쪽', middle: '가운데', evidence: '근거',
  rule: '규칙', rules: '규칙', state: '상태', next: '다음 상태',
  waits_for: '무엇을 기다리나', resume: '재개 처리', path: '경로', increments: '회차 상승',
  exceptions: '예외', baselined: '동결일', zone: '구역', contains: '무엇이 있나',
  label: '이름', from: '에서', to: '으로', mode: '방식', caution: '주의',
  core_claim: '핵심', gained: '얻은 것', catalog: '카탈로그', unique: '유일성',
  why_safe: '왜 안전한가', context: '컨텍스트', steps: '단계', abstraction: '추상화',
  account_source: '계정 원천', mapping_rules: '매핑 규칙', handling: '처리', limits: '한계',
  complement: '보완', notes: '비고', edge_invariants: '불변식', retired_at: '폐기일',
  reason: '사유', replaced_by: '대체', nodes: '구성요소', edges: '간선',
  l1: 'Level 1 에서', deploy: '배포 단위', systems: '상자', units: '배포 단위',
  decided_at: '확정일', review_signal: '재검토 신호', ownership: '데이터 소유권',
  operations: '운영 분담',
  process_step: '프로세스 단계', acceptance: '수용 기준',
  summary: '개요', intent: '목적과 의도', problem: '해결할 문제', not: '아닌 것',
  goal_trace: '상위 목표', decider: '결정권자', preconditions: '전제 조건', data: '필수 데이터',
  item: '항목', sensitivity: '민감도', retention: '보존', business_rules: '업무 규칙',
  lifecycle: '상태 전이', permissions: '권한', audit: '이력·감사', volume: '규모·빈도',
  config: '운영 설정', relations: '연계', depends_on: '의존', constrains: '제약하는 대상',
  conflicts_with: '충돌 후보', nfr: '비기능 요구', interfaces: '외부 연계',
  realization: '실현 후보', features: '기능', screens: '화면', examples: '구체 사례',
  case: '사례', volatility: '변동 가능성', level: '수준', what: '무엇이',
  maturity: '성숙도', at: '일자', by: '작성', confirmed_by: '확인자',
  next_targets: '다음 파기 대상',
  entity: '엔티티', description: '설명', order: '순서', column: '컬럼', nullable: 'NULL 허용',
  default: '기본값', keys: '키·제약', method: '방식', file: '파일', author: '작성자', change_id: 'changeset',
  application: '적용 방법', rules_from: '규칙 근거', owners: '계약 소유 모듈',
  specify: '상세 명세에서 정할 항목', counterexample: '검증 반례', focus: '중점 검토',
  contract_completion: '모듈 간 계약의 완료 조건',
  usecases: '유즈케이스', design_status: '설계 상태', design_stage: '설계 단계',
  entities: '엔티티', domain: '업무 영역', attributes: '논리 속성', collection: '집합',
  cardinality: '관계 수', structure: '구조', related_features: '관련 기능',
  trigger: '시작 조건', operation_boundary: '처리 범위', inputs: '입력', outputs: '출력',
  processing: '처리 규칙', public_contract_needs: '필요한 공개 계약', failure_contract: '실패 처리',
  purpose: '목적', boundary: '경계', refs: '근거 참조', pending: '후속 설계',
  feature_contract: '기능 명세 연결', integration_history_link: '연동 자산 이력 연결',
  view_kind: '화면 종류', source_screen: '상속 원 화면', inheritance: '화면 상속 규칙', history_copy_of: '이력 복사 원본',
  history_contract: '변경 이력 계약', history_pattern: '변경 이력 패턴', history_adoption: '기존 구현 이력 이행',
  external_observations: '외부 관측 정보', state_machine: '상태와 판정', delete_policy: '삭제 정책',
  read: '조회', create: '등록', change: '변경', references: '참조 보호', consumption: '소비 시 검증',
  group_change: '그룹 변경', save: '저장', management: '관리 상태', availability: '호출 가능 판정',
  connection_observation: '연결 관측', query_eligibility: '조회 가능 판정',
  metadata: '메타데이터', query_constraints: '필수 조회 제약', policy_boundary: '정책 관리 경계',
  observation: '원천 관측', sampling: '값 후보 샘플링', impact: '영향 확인', validation: '검증', content: '내용',
  catalog_candidate_contract: '카탈로그 후보 계약', catalog_history_link: '카탈로그 이력 연결',
  catalog_evidence_link: '카탈로그 변경 근거 연결',
  connection: '연결 선택', fields: '필드 명세', input_guide: '입력 확보 가이드',
  code_names: '코드명 제공', call_contract: '호출 계약', guide: '공통 가이드',
  api_tool_candidate_contract: '업무 API·도구 후보 계약', api_tool_history_link: '업무 API·도구 이력 연결',
  reference_validation: '세트 참조 검증', candidate_set: '세트 내 후보 집합',
  external_reference: '외부 유효본 참조', role_resolution: '참조 역할 대조',
  impact_only: '영향·과거 근거', current_policy: '현재 직접 정책', final_validation: '최종 검증과 반영',
  counterexamples: '검증 반례', api_tool_mapping: 'API·도구 대응', relation_management: '관계 관리측',
  tool_reference: '대응 도구 참조', api_reference: 'API 원천 참조',
  contracts_from: '상세 계약 근거', shared_sequences: '공통 처리 연결',
  reading_order: '읽는 순서', transaction_boundary: '트랜잭션 경계',
  admin_input_preparation: '관리 화면의 프롬프트 입력 준비',
};
const lab = (k) => LABEL[k] ?? k;
const PROMPT_INPUT_LABEL = {
  scope: '작성 범위', ownership: '정본과 책임', classification: '입력 분류',
  llm: 'LLM 포함 후보', server: '서버 집행', audit: '관리·감사', default: '미분류 항목',
  stages: '호출 단계별 제공', guides: '해석 가이드', next_design: '후속 설계',
  sources: '화면별 원천 분류', conditions: '제공 조건', guide: '해석 방법',
  identity: '대상 식별', structure: '필드 구조', values: '값과 자료형', acquisition: '입력값 확보',
  meanings: '코드값 의미', relationship: '테이블 관계', outcome: '결과 판정', trust: '지침의 적용 경계',
  model: '모델 관리', model_usage: '모델 용도·우선순위', portal_user: 'Portal 사용자 관리',
  user_group: '사용자 그룹', token_rate: '토큰 기준 단가', quota_grade: '쿼터 등급',
  quota_plan: '월별 개인 쿼터 기준', target_system: '대상 시스템', data_source: '데이터소스',
  catalog_relation: '테이블 관계', business_api: '업무 API', business_tool: '업무 도구',
};
const promptInputLabel = (key) => PROMPT_INPUT_LABEL[key] ?? lab(key);

/** 스칼라·리스트를 한 줄로. 리스트는 ` · ` 로 잇는다. */
function flat(v) {
  if (v === null || v === undefined) return '—';
  if (typeof v === 'boolean') return v ? '예' : '아니오';
  if (Array.isArray(v)) return v.length ? v.map(flat).join(' · ') : '—';
  if (typeof v === 'object') {
    return Object.entries(v).map(([k, x]) => `${lab(k)}: ${flat(x)}`).join(' · ');
  }
  return String(v).replace(/\n/g, ' ').trim();
}

const keysOf = (items) => [...new Set(items.flatMap((it) => Object.keys(it)))];
const isShort = (items, keys) => items.every((it) => keys.every((k) => flat(it[k]).length <= 90));

function table(items, keys) {
  const rows = [`| ${keys.map(lab).join(' | ')} |`, `|${keys.map(() => '---').join('|')}|`];
  for (const it of items) rows.push(`| ${keys.map((k) => flat(it[k]).replace(/\|/g, '\\|')).join(' | ')} |`);
  return rows.join('\n');
}

/** 항목마다 소제목 + 남은 필드를 불릿으로. **키를 떨어뜨리지 않는다.** */
export function blocks(items, headKeys = ['id'], titleKey = 'title', level = '###') {
  const out = [];
  for (const it of items) {
    const hid = headKeys.filter((k) => it[k] !== undefined && it[k] !== null).map((k) => String(it[k])).join(' · ');
    const ttl = it[titleKey] ?? it.name ?? it.term ?? it.area ?? it.label ?? '';
    if (it.overview_diagram) out.push(`${level} ${hid} 요구사항`, '', `**${ttl}**`, '');
    else out.push(`${level} ${hid}${ttl ? ` — ${ttl}` : ''}`, '');
    for (const [k, v] of Object.entries(it)) {
      if (headKeys.includes(k) || k === titleKey) continue;
      if (k === 'overview_diagram' && typeof v === 'string') {
        out.push(`#### ${it.id} 전체 흐름도`, '',
          it.sequence_routes ? `S1~S${it.sequence_routes.length}는 상세 시퀀스의 검토 번호입니다. 실행 순서를 뜻하지 않습니다. 아래 [단계별 대응표](#${String(it.id).toLowerCase()}-단계별-대응표)에서 다이어그램과 순서 설명으로 이동할 수 있습니다.` : '',
          '', '```mermaid', v.trimEnd(), '```', '');
      } else if (k === 'sequence_routes' && Array.isArray(v)) {
        const route = (r, steps = false) => `${posix.relative(`${RQ}04-요구사항`, r.document)}#${steps ? r.steps_anchor : r.anchor}`;
        const byKey = new Map(v.map((r) => [r.key, r]));
        out.push(`#### ${it.id} 단계별 대응표`, '',
          '그림의 S 번호를 찾아 다이어그램 또는 설명을 여세요. 관련 경로는 실제 분기·공통 처리 연결이며, 검토 번호의 앞뒤 순서와 다릅니다.', '',
          '| 검토 번호 | 다이어그램·순서 설명 | 확인할 내용 | 관련 경로 |',
          '|---|---|---|---|');
        for (const r of v) {
          const related = (r.related ?? []).map((key) => {
            const target = byKey.get(key);
            if (!target) throw new Error(`시퀀스 대응표의 연결 대상이 없습니다: ${key}`);
            return `[${key}](${route(target)})`;
          }).join(' · ');
          out.push(`| ${r.key} | [${r.title}](${route(r)}) · [순서 설명](${route(r, true)}) | ${r.focus} | ${related} |`);
        }
        out.push('', '**요구사항 반영 위치 대조** — 흐름 노드는 원본 Mermaid의 노드 이름입니다.', '',
          '| 검토 번호 | 전체 흐름 노드 | 관련 요구사항 |', '|---|---|---|');
        for (const r of v) out.push(`| [${r.key}](${route(r)}) | ${r.nodes.join(', ')} | ${r.requirements.join(', ')} |`);
        out.push('', `[↑ 전체 흐름도로 돌아가기](#${String(it.id).toLowerCase()}-전체-흐름도)`, '');
      } else if (k === 'sequence_design' && /^UC-\d+$/.test(it.id ?? '') && v && typeof v === 'object' && !Array.isArray(v)) {
        out.push(`- **핵심 처리 시퀀스** — [별도 명세](33-시퀀스/${it.id}.md)`);
      } else if (k === 'prompt_contract' && v?.admin_input_preparation) {
        const { admin_input_preparation: preparation, ...contract } = v;
        out.push(`- **${lab(k)}** — ${flat(contract)}`, '', `#### ${it.id} ${lab('admin_input_preparation')}`, '');
        for (const [part, value] of Object.entries(preparation)) {
          out.push(`##### ${promptInputLabel(part)}`, '');
          if (part === 'sources' && value && typeof value === 'object' && !Array.isArray(value)) {
            for (const [key, row] of Object.entries(value)) {
              out.push(`###### ${row.screen} — ${promptInputLabel(key)}`, '');
              for (const [field, item] of Object.entries(row)) {
                if (field !== 'screen') out.push(`- **${promptInputLabel(field)}** — ${flat(item)}`);
              }
              out.push('');
            }
          } else if (Array.isArray(value)) {
            for (const item of value) out.push(`- ${flat(item)}`);
          } else if (value && typeof value === 'object') {
            for (const [key, item] of Object.entries(value)) out.push(`- **${promptInputLabel(key)}** — ${flat(item)}`);
          } else out.push(flat(value));
          out.push('');
        }
      } else if (['processing', 'state_machine'].includes(k) && v && typeof v === 'object' && !Array.isArray(v)) {
        out.push(`- **${lab(k)}**`);
        for (const [part, value] of Object.entries(v)) out.push(`  - **${lab(part)}** — ${flat(value)}`);
      } else if (Array.isArray(v) && v.length && typeof v[0] === 'object' && v[0] !== null) {
        out.push(`- **${lab(k)}**`);
        for (const x of v) out.push(`  - ${flat(x)}`);
      } else if (Array.isArray(v) && v.length > 1) {
        out.push(`- **${lab(k)}**`);
        for (const x of v) out.push(`  - ${flat(x)}`);
      } else {
        out.push(`- **${lab(k)}** — ${flat(v)}`);
      }
    }
    out.push('');
  }
  return out.join('\n');
}

/** 짧으면 표, 길면 블록. **어느 쪽이든 모든 키를 싣는다.** */
function auto(items, { keys, level = '###', titleKey = 'title' } = {}) {
  const ks = keys ?? keysOf(items);
  return isShort(items, ks) ? table(items, ks) : blocks(items, ['id'], titleKey, level);
}

const bullets = (seq) => seq.map((x) => `- ${flat(x)}`).join('\n');

/**
 * 문서 머리. `status` 가 승인 상태를 말하며 **`mixed` 는 배너를 달지 않는다** —
 * 목차처럼 장마다 상태가 다른 문서에 하나를 적으면 거짓이 된다.
 */
function head(id, title, from, status, baselined, traces, extra = '') {
  const banner = {
    migrated:
      '> **아직 승인 전이다**(`status: migrated` · `baselined: null`). 여기의 어떤 항목도\n'
      + '> *합의된 것* 으로 인용하지 않는다 — 인용하려면 `/phase-gate` 승인이 먼저다.\n',
    baselined: `> **승인됐다**(\`baselined: ${baselined}\`). 이후 변경은 \`/change-request\` 를 거친다.\n`,
    empty: '> **비어 있다.** 아래에 *왜 비어 있고 무엇에 막혀 있는지* 를 적는다.\n',
  }[status] ?? '';
  return `---
id: ${id}
title: "${title}"
traces_to: [${(traces ?? []).join(', ')}]
status: ${status}
baselined: ${baselined ?? 'null'}
generated_from: ${from}
last_sync: ${TODAY}
---
<!-- 이 문서는 생성물입니다. 직접 수정하지 마세요. ${from} 을 수정 후 /doc-gen 하세요. -->

# ${title}

${banner}${extra}`;
}

const loadedSpecs = new Map();
const specSet = (name) => {
  if (!loadedSpecs.has(name)) loadedSpecs.set(name, loadSpecSet({ repo: REPO, name, parseYaml }));
  return loadedSpecs.get(name);
};
const load = (name) => specSet(name).document;
const rawSpec = (name) => specSet(name).raw;
const specSource = (name) => specSet(name).source;

const written = [];
function write(path, parts) {
  const text = `${parts.join('\n\n').replace(/\n+$/, '')}\n`;
  const full = join(REPO, path);
  mkdirSync(dirname(full), { recursive: true });
  writeFileSync(full, text, 'utf8');
  written.push([path, text.split('\n').length]);
}

// ── 43행 · 요구사항 정의서 — P0~P2 정본 여덟의 읽는 뷰 ──────────────
function 요구사항정의서() {
  const c = load('00-charter');
  const p = [head('RQ-00-charter', '개요 — 목적 · 목표 · 성공 기준 · 로드맵', specSource('00-charter'),
    c.meta.status, c.meta.baselined, c.goals.map((g) => g.id))];
  p.push(`## 1. 목적\n\n**${flat(c.purpose)}**\n\n${flat(c.purpose_note)}`);
  p.push(`## 2. 배경 — 현행 시스템의 한계\n\n${auto(c.problems)}`);
  p.push('## 3. 구축 형태 — 둘을 수용한다\n\n'
    + '> **제품은 하나다.** 차이는 업무 시스템이 저장소 **밖에** 있느냐 **안에** 있느냐뿐이며,\n'
    + '> 형태에 따라 **가치의 근거가 달라진다**.\n\n'
    + auto(c.deployment_shapes));
  p.push(`## 4. 이 제품의 경제성\n\n**목적이 참인가는 이 다섯이 참인가에 달려 있다.**\n\n${auto(c.economics, { titleKey: 'claim' })}`);
  p.push(`## 5. 최종 목표\n\n${auto(c.goals)}`);
  p.push(`## 6. 비목표 — 하지 않는 것\n\n**하지 않겠다고 적지 않은 것은 하겠다고 읽힌다.**\n\n${auto(c.non_goals)}`);
  p.push(`## 7. 범위 (In)\n\n${bullets(c.scope_in)}\n\n**방향** — ${flat(c.direction)}`);
  const policy = c.measurement_first
    ? '**방침 — 측정을 먼저 만든다(`measurement_first: true`).** 목표를 미리 세우지 않고\n'
      + '*재는 것을 먼저 만들고 → 정확히 재고 → 개선이 필요한지 판단한다*. 그래서 `목표값` 의\n'
      + '빈칸은 **빠진 것이 아니라 아직 재지 않았다**는 뜻이다.'
    : '**방침** — 목표를 먼저 세운다.';
  p.push(`## 8. 성공 기준\n\n${policy}\n\n${auto(c.success_criteria)}`);
  p.push(`### 8.1 성공 기준에서 내린 지표 — 추적은 계속한다\n\n${auto(c.tracked_metrics, { level: '####' })}`);
  p.push(`### 8.2 불변식\n\n${auto(c.invariants, { level: '####' })}\n\n${bullets(c.success_notes)}`);
  p.push(`## 9. 로드맵\n\n${auto(c.roadmap)}\n\n${bullets(c.roadmap_notes)}`);
  p.push(`## 10. 트랙과 기대치\n\n- **트랙** — ${flat(c.track)} · ${flat(c.track_why)}\n`
    + Object.entries(c.expectations).map(([k, v]) => `- **${lab(k)}** — ${flat(v)}`).join('\n'));
  write(`${RQ}00-개요.md`, p);

  const a = load('01-actors');
  const p1 = [head('RQ-01-actors', '이해관계자 — 액터와 모듈', specSource('01-actors'),
    a.meta.status, a.meta.baselined, a.actors.map((x) => x.id),
    '> **액터는 요구사항의 근거다**(추적성 T1) — 모든 요구사항은 최소 하나의 액터와 연결된다.\n')];
  const seen = new Set();
  for (const [kind, ttl] of [['human', '사람'], ['system', '외부 시스템'], ['org', '외부 조직']]) {
    const sel = a.actors.filter((x) => x.kind === kind);
    sel.forEach((x) => seen.add(x.id));
    if (sel.length) p1.push(`## ${ttl}\n\n${blocks(sel, ['id'], 'name')}`);
  }
  const rest = a.actors.filter((x) => !seen.has(x.id));
  if (rest.length) p1.push(`## 그 밖\n\n${blocks(rest, ['id'], 'name')}`);
  p1.push(`## 모듈\n\n${blocks(a.modules, ['id'], 'name')}`);
  write(`${RQ}01-이해관계자.md`, p1);

  const k = load('02-constraints');
  write(`${RQ}02-제약과-원칙.md`, [
    head('RQ-02-constraints', '제약과 설계 원칙', specSource('02-constraints'),
      k.meta.status, k.meta.baselined, k.principles.map((x) => x.id)),
    '## 1. 설계 원칙\n\n> **이후 모든 판단의 기준이다.** 선택지가 갈릴 때 이 원칙에 부합하는 쪽을 택한다.\n\n'
      + blocks(k.principles, ['id'], 'title'),
    `## 2. 기술 제약\n\n${blocks(k.technical, ['id'], 'area')}`,
    `## 3. 배포·망 전제\n\n${blocks(k.deployment, ['id'], 'title')}`,
    '## 4. 착수 시 확정해야 하는 것\n\n'
      + '> **시스템 기능이 아니므로 여기서 값을 확정하지 않는다.** 대신 **확정되지 않은 상태로\n'
      + '> 구축에 들어가지 않는다**는 것을 요건으로 둔다.\n\n'
      + blocks(k.customer_agreements, ['id'], 'area'),
    `## 5. 개발 기간의 제약\n\n${blocks(k.development, ['id'], 'title')}`,
  ]);

  const g = load('03-glossary');
  const p3 = [head('RQ-03-glossary', '용어 사전 — 유비쿼터스 언어', specSource('03-glossary'),
    g.meta.status, g.meta.baselined, null,
    '> **이 사전이 모든 문서·DB 테이블·API 필드 명명의 기준이다.** 동의어를 섞어 쓰지 않는다.\n'
    + '> `영문 키` 는 DB 컬럼 값과 코드 상수에서 대문자 스네이크로 쓴다.\n')];
  for (const grp of [...new Set(g.terms.map((x) => x.group))]) {
    p3.push(`## 용어 — ${grp}\n\n${blocks(g.terms.filter((x) => x.group === grp), ['id'], 'term')}`);
  }
  p3.push(`## 개념 계층\n\n\`\`\`\n${flat(g.hierarchy)}\n\`\`\``);
  p3.push(`## 턴 상태 모델\n\n${auto(g.turn_states)}\n\n### 대기 유형\n\n`
    + `${blocks(g.wait_types, ['id'], 'type', '####')}\n### 상태 규칙\n\n${bullets(g.state_rules)}`);
  p3.push(`## 되돌림과 회차\n\n${auto(g.revision_rules, { titleKey: 'path' })}\n\n${bullets(g.revision_notes)}`);
  p3.push('## 프레임워크 용어\n\n'
    + '> **우리 명명의 기준이 아니다** — 그래서 `영문 키` 가 없다. 구현과\n'
    + '> [`design/turn-execution.md`](../../design/turn-execution.md) 를 읽을 때 필요해서 싣는다.');
  for (const grp of [...new Set(g.framework_terms.map((x) => x.group))]) {
    p3.push(`### ${grp}\n\n${auto(g.framework_terms.filter((x) => x.group === grp), { titleKey: 'term', level: '####' })}`);
  }
  p3.push(`### 훅과 인터럽트의 실행 순서\n\n**우리 설계가 이 순서에 의존한다.**\n\n`
    + `\`\`\`\n${flat(g.hook_order)}\n\`\`\`\n\n${bullets(g.hook_order_notes)}`);
  p3.push('## 헷갈리는 짝 — **이 장이 사전의 본론이다**\n\n'
    + '> 정의를 나란히 두는 것으로는 혼동이 막히지 않는다. **실제로 어긋난 것만** 여기 있다.\n\n'
    + blocks(g.confusions, ['title'], null));
  write(`${RQ}03-용어.md`, p3);

  /*
    기능 요구사항은 **정본 조각 하나를 생성 파일 하나로** 쓴다. 영역 이름은 조각 첫머리의
    `# 영역: …` 한 줄이 정본이다 — ID 범위를 여기에 다시 적으면 조각을 나눌 때 두 곳이 갈린다.
    정련 카드가 붙으면 요구사항 하나가 50줄을 넘어, 84건을 한 파일에 담으면 읽을 수 없다(2026-09-28).
  */
  const rs = specSet('10-requirements');
  const r = rs.document;
  const RQ4 = `${RQ}04-요구사항/`;
  const legacyRq4 = join(REPO, `${RQ}04-요구사항.md`);
  if (existsSync(legacyRq4)) rmSync(legacyRq4);
  const areas = [];
  rs.files.slice(1).forEach((file, i) => {
    const text = readFileSync(join(REPO, file), 'utf8');
    const part = parseYaml(text) ?? {};
    const stem = posix.basename(file, '.yaml');
    const area = (text.match(/^# 영역: (.+)$/m)?.[1] ?? stem).trim();
    const name = `${String(i + 1).padStart(2, '0')}-${area.replace(/[^\p{L}\p{N}]+/gu, '-').replace(/^-|-$/g, '')}.md`;
    const reqs = part.requirements ?? [];
    const body = [head(`RQ-10-${stem}`, `기능 요구사항 — ${area}`, file, r.meta.status, r.meta.baselined,
      reqs.map((x) => x.id), '> 영역 목록은 [00-목록](00-목록.md)에 있다.\n')];
    if (reqs.length) body.push(`## ${area} (${reqs.length}건)\n\n${blocks(reqs, ['id'], 'title')}`);
    if (part.consistency_review) {
      body.push(`## 요구사항 일관성 검토\n\n${blocks([{ id: 'consistency-review', ...part.consistency_review }], ['id'], 'name')}`);
    }
    write(`${RQ4}${name}`, body);
    areas.push({ name, area, reqs, file });
  });
  // 주제별 조각에는 비연속 ID가 들어간다. 실제로 연속된 번호만 범위로 묶는다.
  const requirementIds = (reqs) => {
    const ranges = [];
    for (const { id } of reqs) {
      const last = ranges.at(-1);
      if (last && /^REQ-\d+$/.test(id) && /^REQ-\d+$/.test(last[1])
          && Number(id.slice(4)) === Number(last[1].slice(4)) + 1) last[1] = id;
      else ranges.push([id, id]);
    }
    return ranges.map(([first, last]) => first === last ? first : `${first}~${last}`).join(', ') || '—';
  };
  const areaRow = ({ name, area, reqs, file }) =>
    `| [${area}](${name}) | ${requirementIds(reqs)} | ${reqs.length} | \`${file}\` |`;
  write(`${RQ4}00-목록.md`, [
    head('RQ-10-requirements', '기능 요구사항 (FR)', specSource('10-requirements'),
      r.meta.status, r.meta.baselined, null, `> **채번** — ${r.meta.numbering}\n`),
    `## 영역\n\n| 영역 | 요구사항 | 건수 | 정본 조각 |\n|---|---|---|---|\n${areas.map(areaRow).join('\n')}`,
  ]);
  // 분할·이름 변경으로 대체된 요구사항 생성물만 제거한다. 다른 파일은 보존한다.
  const currentAreaFiles = new Set(['00-목록.md', ...areas.map(({ name }) => name)]);
  for (const entry of readdirSync(join(REPO, RQ4), { withFileTypes: true })) {
    if (!entry.isFile() || !entry.name.endsWith('.md') || currentAreaFiles.has(entry.name)) continue;
    const obsolete = join(REPO, RQ4, entry.name);
    const content = readFileSync(obsolete, 'utf8');
    if (/^id: RQ-10-/m.test(content)
        && /^generated_from: documents\/spec\/10-requirements\//m.test(content)
        && content.includes('<!-- 이 문서는 생성물입니다. 직접 수정하지 마세요.')) rmSync(obsolete);
  }

  const pr = load('11-processes');
  const why = rawSpec('11-processes').split('\n').filter((l) => l.startsWith('#'))
    .map((l) => l.replace(/^#\s?/, '').trimEnd()).join('\n');
  const processParts = [head('RQ-11-processes', '업무 프로세스 (As-Is / To-Be)', specSource('11-processes'),
    pr.meta.status, pr.meta.baselined, (pr.processes ?? []).map((x) => x.id),
    pr.meta.blocked_by ? `> **막고 있는 것** — ${flat(pr.meta.blocked_by)}\n` : '')];
  if (pr.processes?.length) {
    processParts.push(`## 범용 Hub 프로세스 ${pr.processes.length}건\n\n${blocks(pr.processes, ['id'], 'name')}`);
    if (pr.customer_pilot) processParts.push(`## 고객 파일럿\n\n${blocks([{ id: 'customer-pilot', ...pr.customer_pilot }], ['id'], 'name')}`);
  } else {
    processParts.push(`## 왜 비어 있나\n\n${why}`,
      '## 이 장이 비어 있는 결과\n\n'
        + '- **P1 을 닫을 수 없다** — 채울 근거가 없다(`project.state.yaml` 의 `OQ-002`).\n'
        + '- **효과 측정의 기준선이 없다** — 성공 기준이 그 값을 기다린다.\n'
        + '- **장을 빼지 않고 비워 둔다** — 빼면 읽는 사람이 이 주제를 다룬 줄 알게 된다.');
  }
  write(`${RQ}05-업무프로세스.md`, processParts);

  const n = load('21-nfr');
  const p6 = [head('RQ-21-nfr', '비기능 요구 — 성능 · 응답성 · 비용 · 안정성', specSource('21-nfr'),
    n.meta.status, n.meta.baselined, null,
    '> **목표값이 전부 `—` 다.** 미리 세우지 않고 *측정을 만들고 → 재고 → 개선이 필요한지\n'
    + '> 판단한다* 는 방침을 택했다(`ASM-002`). 그래서 빈칸은 **빠진 것이 아니라 아직 재지\n'
    + '> 않았다**는 뜻이다. 추적성 T7 은 모든 NFR 에 측정 방법과 테스트 케이스를 요구하며,\n'
    + '> **테스트 케이스가 없어 지금 T7 오류가 난다.**\n')];
  for (const cat of [...new Set(n.nfrs.map((x) => x.category))]) {
    const sel = n.nfrs.filter((x) => x.category === cat);
    p6.push(`## ${cat} (${sel.length}건)\n\n${blocks(sel, ['id'], 'title')}`);
  }
  write(`${RQ}06-비기능요구.md`, p6);

  const i = load('51-interfaces');
  const p7 = [head('RQ-51-interfaces', '외부 인터페이스', specSource('51-interfaces'),
    i.meta.status, i.meta.baselined, i.interfaces.map((x) => x.id))];
  const seenIf = new Set();
  for (const [dr, ttl] of [['outbound', '나가는 연동'], ['inbound', '들어오는 연동']]) {
    const sel = i.interfaces.filter((x) => x.direction === dr);
    sel.forEach((x) => seenIf.add(x.id));
    if (sel.length) p7.push(`## ${ttl} (${sel.length}건)\n\n${blocks(sel, ['id'], 'name')}`);
  }
  const restIf = i.interfaces.filter((x) => !seenIf.has(x.id));
  if (restIf.length) p7.push(`## 그 밖\n\n${blocks(restIf, ['id'], 'name')}`);
  p7.push(`## 인터페이스가 아닌 것\n\n${auto(i.not_interfaces, { titleKey: 'name' })}`);
  write(`${RQ}07-외부인터페이스.md`, p7);

  // ── _index — 목차에는 단일 상태를 붙이지 않는다(장마다 다르다) ──
  const STAT = { migrated: '이관 · **승인 전**', baselined: '**승인됨**', empty: '**비어 있다**' };
  const CH = [
    ['00-개요.md', `문제 ${c.problems.length} · 경제성 ${c.economics.length} · 목표 ${c.goals.length} · 비목표 ${c.non_goals.length} · 성공 기준 ${c.success_criteria.length} · 로드맵 ${c.roadmap.length}`, '00-charter', c.meta.status],
    ['01-이해관계자.md', `액터 ${a.actors.length} · 모듈 ${a.modules.length}`, '01-actors', a.meta.status],
    ['02-제약과-원칙.md', `설계 원칙 ${k.principles.length} · 기술 제약 ${k.technical.length} · 배포 전제 ${k.deployment.length} · 착수 확정 ${k.customer_agreements.length} · 개발 제약 ${k.development.length}`, '02-constraints', k.meta.status],
    ['03-용어.md', `용어 ${g.terms.length} · 프레임워크 어휘 ${g.framework_terms.length} · **헷갈리는 짝 ${g.confusions.length}** · 턴 상태 ${g.turn_states.length}`, '03-glossary', g.meta.status],
    ['04-요구사항/00-목록.md', `기능 요구사항 ${r.requirements.length} · 영역 ${areas.length}`, '10-requirements', r.meta.status],
    ['05-업무프로세스.md', pr.processes?.length ? `범용 Hub 프로세스 ${pr.processes.length}건` : `**비어 있다** — ${flat(pr.meta.blocked_by)} 에 막혀 있다`, '11-processes', pr.meta.status],
    ['06-비기능요구.md', `비기능 ${n.nfrs.length} (**목표값 전부 미정**)`, '21-nfr', n.meta.status],
    ['07-외부인터페이스.md', `외부 인터페이스 ${i.interfaces.length}`, '51-interfaces', i.meta.status],
  ];
  write(`${RQ}_index.md`, [
    head('RQ-INDEX', '요구사항 정의서', `${SPEC}(P0~P2 정본 여덟)`, 'mixed', null, null,
      '> **읽는 사람은 사용자(고객·의사결정자)다.** 판단하는 것은 *이대로 만들어도 되는가* 이며,\n'
      + '> 그 판단이 곧 게이트 승인이다(CLAUDE.md §5-12).\n>\n'
      + '> **정본은 `documents/spec/` 이다.** 고칠 것이 있으면 여기가 아니라 그쪽을 고치고\n'
      + '> `/doc-gen 요구사항정의서` 로 다시 만든다 — 여기 고친 것은 다음 생성에서 사라진다.\n'),
    '## 장 목록\n\n| 장 | 담는 것 | 원본 | 상태 |\n|---|---|---|---|\n'
      + CH.map(([f, d, src, st]) => `| [${f.replace('.md', '')}](./${f}) | ${d} | \`${src}.yaml\` | ${STAT[st] ?? st} |`).join('\n'),
    '## 읽는 순서\n\n'
      + '**위에서 아래로다.** 목적(00)이 액터(01)를 정하고, 제약(02)이 선택지를 좁히고, 용어(03)가\n'
      + '말을 고정한 뒤에야 요구사항(04)이 읽힌다. 프로세스(05)에서 요구사항을 실제 업무 흐름과\n'
      + '연결하고, 비기능(06)과 외부 인터페이스(07)에서 실행 조건과 시스템 경계를 확인한다.',
    '## 질문과 가정의 현재 상태\n\n'
      + '사용자 답변이 필요한 질문은 [프로젝트 상태](../../project.state.yaml)의 `open_questions`, '
      + '가정의 확인·기각과 근거는 [가정 원장](../../decisions/ASSUMPTIONS.md)을 따른다. '
      + '이 목차에 과거 미결 목록을 고정 복제하지 않는다.',
  ]);
}

// ── 8행 · 아키텍처 ────────────────────────────────────────────────
const ZONE = { 사용자: 'USERZ', 외부망: 'EXT', DMZ: 'DMZ', Hub: 'AI', 레거시: 'LEGACY', 고객사: 'CUST' };
const ZLABEL = {
  USERZ: '사용자 구역', EXT: '외부망', DMZ: 'DMZ',
  AI: '내부 폐쇄망 — Agentic AI Hub (독립 배포 · 단일 WAS)',
  LEGACY: '내부 폐쇄망 — 레거시 업무 시스템 (복수)', CUST: '고객사 환경',
};

/** 괄호를 Mermaid 가 문서화한 엔티티로 바꾼다 — 원통 노드 `[("…")]` 안의 `)` 가
 *  셰이프 종료로 읽히면 **그림이 통째로 렌더되지 않고, 생성물이라 아무도 고치지 않는다.** */
const mmEscape = (s) => s.replace(/\(/g, '#40;').replace(/\)/g, '#41;');

/** 노드 하나를 Mermaid 상자로 — 원통은 DB 다. */
function mmBox(nd) {
  const label = mmEscape(nd.label + (nd.note ? `<br/>${nd.note}` : ''));
  const db = nd.kind === 'db';
  return `${nd.key}[${db ? '(' : ''}"${label}"${db ? ')' : ''}]`;
}

/**
 * C4 Level 1 — 시스템 컨텍스트. **노드를 `l1:` 로 접어서 그린다.**
 *
 * <p>상자와 간선을 두 벌 적지 않는 것이 요점이다(논리 문서 20-architecture의 불변식) —
 * 두 벌이면 한쪽만 고쳤을 때 <b>어느 그림이 참인지 알 방법이 없다</b>. 같은 상자로
 * 접힌 간선(예: portal → AI 전용 DB)은 사라지고, 남은 간선의 라벨은 합친다.
 */
function 컨텍스트도(t) {
  const fold = new Map(t.nodes.map((n) => [n.key, n.l1]));
  const L = ['```mermaid', 'flowchart LR'];
  for (const s of t.context.systems) {
    L.push(`  ${s.key}["${mmEscape(s.label + (s.note ? `<br/>${s.note}` : ''))}"]`);
  }
  const merged = new Map();
  for (const e of t.edges) {
    const a = fold.get(e.from);
    const b = fold.get(e.to);
    if (!a || !b || a === b) continue;
    const k = `${a}>${b}`;
    if (!merged.has(k)) merged.set(k, new Set());
    if (e.label) merged.get(k).add(e.label);
  }
  for (const [k, labels] of merged) {
    const [a, b] = k.split('>');
    const l = [...labels].join(' · ');
    L.push(`  ${a} -->${l ? `|"${l}"|` : ''} ${b}`);
  }
  L.push('```');
  return L.join('\n');
}

/**
 * C4 Level 2 — 컨테이너. 구역 안에서 <b>배포 단위</b>(`deploy:`)로 한 겹 더 묶는다.
 *
 * <p>상자가 하나뿐인 배포 단위는 감싸지 않는다 — 테두리가 상자를 한 겹 덧그릴 뿐이고,
 * 그 테두리를 <i>무언가 더 있다</i> 로 읽게 만든다.
 */
function 컨테이너도(t) {
  const unit = new Map((t.containers?.units ?? []).map((u) => [u.key, u]));
  const byZone = new Map();
  for (const nd of t.nodes) {
    const z = ZONE[nd.zone] ?? 'CUST';
    if (!byZone.has(z)) byZone.set(z, []);
    byZone.get(z).push(nd);
  }
  const L = ['```mermaid', 'flowchart LR'];
  for (const z of ['EXT', 'DMZ', 'USERZ', 'AI', 'LEGACY', 'CUST']) {
    const ns = byZone.get(z) ?? [];
    if (!ns.length) continue;
    L.push(`  subgraph ${z}["${ZLABEL[z]}"]`);
    const groups = new Map();
    for (const nd of ns) {
      const g = nd.deploy ?? '';
      if (!groups.has(g)) groups.set(g, []);
      groups.get(g).push(nd);
    }
    for (const [g, list] of groups) {
      if (!g || list.length < 2) {
        for (const nd of list) L.push(`    ${mmBox(nd)}`);
        continue;
      }
      L.push(`    subgraph U_${g}["${mmEscape(unit.get(g)?.label ?? g)}"]`);
      for (const nd of list) L.push(`      ${mmBox(nd)}`);
      L.push('    end');
    }
    L.push('  end');
  }
  for (const e of t.edges) L.push(`  ${e.from} -->${e.label ? `|"${e.label}"|` : ''} ${e.to}`);
  L.push('```');
  return L.join('\n');
}

function 아키텍처() {
  const d = load('20-architecture');
  const t = d.topology;

  const p = [head('ARCH', '아키텍처 — 구성 · 경계 · 연동', specSource('20-architecture'),
    d.meta.status, d.meta.baselined,
    [t.id, t.context.id, t.containers.id, d.application_entry.id,
      ...d.module_boundaries.map((x) => x.id)],
    '> **이 아키텍처를 세운 결정**은 [`ADR-001`](../decisions/ADR-001-오케스트레이션-프레임워크.md) ·\n'
    + '> [`ADR-002`](../decisions/ADR-002-플랫폼-노선.md) · [`ADR-003`](../decisions/ADR-003-상태-원천-단일화.md) 이며,\n'
    + '> 그 **근거 자료**는 [`documents/research/`](../research/agent-frameworks.md) 가 갖는다.\n')];
  p.push(`## 1. 논리 구성도 — C4 두 계층\n\n${flat(t.core_claim)}\n\n${bullets(t.notes)}\n\n`
    + '> **두 그림은 한 원본에서 나온다.** 아래 노드·간선이 정본이고, Level 1 은 그것을 시스템\n'
    + '> 단위로 접은 것이다 — 따로 그린 그림이 아니므로 둘이 어긋날 수 없다.');
  p.push(`### 1.1 Level 1 — 시스템 컨텍스트 (${t.context.id})\n\n${컨텍스트도(t)}\n\n`
    + `${flat(t.context.claim)}\n\n${flat(t.context.note)}\n\n`
    + `${auto(t.context.systems, { titleKey: 'label', level: '####' })}`);
  p.push(`### 1.2 Level 2 — 컨테이너 (${t.containers.id})\n\n${컨테이너도(t)}\n\n`
    + `${flat(t.containers.claim)}\n\n`
    + `${auto(t.containers.units, { titleKey: 'label', level: '####' })}`);
  p.push('### 1.3 이 그림이 지켜야 하는 불변식\n\n'
    + '**그림이 예쁜 것으로는 낡음을 알 수 없다.** 아래 중 하나가 깨진 그림은 틀린 그림이다.\n\n'
    + t.edge_invariants.map((x, n) => `${n + 1}. ${flat(x)}`).join('\n'));
  p.push(`### 1.4 구역\n\n${auto(t.zones, { titleKey: 'zone', level: '####' })}`);
  p.push(`### 1.5 구성요소\n\n${auto(t.nodes, { titleKey: 'label', level: '####' })}`);
  p.push(`### 1.6 간선 — 무엇이 무엇을 부르나\n\n${auto(t.edges, { titleKey: 'label', level: '####' })}`);
  p.push('## 2. 모듈 경계\n\n'
    + '> 모듈의 정의와 책임은 [`01-이해관계자.md`](./10-요구사항정의서/01-이해관계자.md) 가 갖는다. 여기는 **경계** 다.\n\n'
    + blocks(d.module_boundaries, ['id'], 'rule'));

  p.push('## 3. 업무 확장 경계 (SHAPE-02)\n\n'
    + '> 업무 확장은 Hub 모듈이 아니다 — ARCH-010 의 공통 Entity·DB 규칙이 적용되지 않는다.\n'
    + '> **구현은 Hub 완성 뒤이며, 지금 굳으면 나중에 뜯어야 하는 자리 셋만 연다.**\n\n'
    + blocks(d.business_extension, ['id'], 'rule'));

  const SECTIONS = [['write_ownership', '쓰기 책임'], ['deeplink', 'portal 진입 경로 — 레거시 딥링크'],
    ['application_entry', '화면 영역 · API 경로 · 로그인 진입'],
    ['system_transition', 'system 모듈 이행 경계'],
    ['portal_agent_contract', 'portal-agent 계약 경계'],
    ['datasource', '데이터소스 전략'], ['llm_layer', 'LLM 연동 계층'],
    ['identity', '인증 · 계정 원천과 신원 매핑'], ['knowledge_search', '지식 검색 구성'],
    ['execution_lease', '턴 실행 점유']];
  for (const [key, ttl] of SECTIONS) {
    const body = [];
    const walk = (obj, lvl) => {
      for (const [k, v] of Object.entries(obj)) {
        if (Array.isArray(v) && v.length && typeof v[0] === 'object' && v[0] !== null) {
          body.push('', `${'#'.repeat(lvl)} ${lab(k)}`, '', auto(v), '');
        } else if (Array.isArray(v)) {
          body.push(`- **${lab(k)}**`, ...v.map((x) => `  - ${flat(x)}`));
        } else if (v && typeof v === 'object') {
          body.push('', `${'#'.repeat(lvl)} ${lab(k)}`, '');
          walk(v, lvl + 1);
        } else {
          body.push(`- **${lab(k)}** — ${flat(v)}`);
        }
      }
    };
    walk(d[key], 4);
    p.push(`## ${ttl} (${d[key].id ?? '—'})\n\n${body.join('\n')}`);
  }
  write(`${OUT}20-아키텍처.md`, p);
}

// ── 11행 · 기능 목록 ──────────────────────────────────────────────
function 기능목록() {
  const f = load('31-features');
  const screens = load('60-screens').screens ?? [];
  const mods = [...new Set(f.features.map((x) => x.module))];
  const p = [head('FEAT-LIST', '기능 목록 · 메뉴 구조', specSource('31-features'),
    f.meta.status, f.meta.baselined, null, `> **채번** — ${f.meta.numbering}\n`)];
  if (f.delivery_strategy) {
    const { admin_design_review: review, ...strategy } = f.delivery_strategy;
    p.push(`## 전달 전략\n\n${blocks([{ id: 'delivery-strategy', ...strategy }], ['id'], 'name')}`);
    if (review) {
      const { axes, area_application, shared_feature, ...context } = review;
      p.push('## admin 공통 상세 설계 기준\n\n'
        + blocks([{ title: '적용 범위와 계약 경계', ...context }], ['title'], '', '###'));
      p.push(blocks(Object.entries(axes ?? {}).map(([axis, value]) => ({ axis, ...value })), ['axis'], 'title', '###'));
      p.push('### 업무 영역별 적용\n\n' + blocks(area_application ?? [], ['area'], 'name', '####'));
      if (shared_feature) p.push(blocks([{ title: '공통 승인 기능의 적용 경계', ...shared_feature }], ['title'], '', '###'));
    }
  }
  p.push('## 요약\n\n| 모듈 | 기능 수 | 차수 분포 |\n|---|---:|---|\n'
    + mods.map((m) => {
      const sel = f.features.filter((x) => x.module === m);
      const phases = [...new Set(sel.map((x) => x.roadmap).filter(Boolean))].sort();
      return `| ${m} | ${sel.length} | ${phases.map((ph) => `${ph} ${sel.filter((x) => x.roadmap === ph).length}`).join(' · ')} |`;
    }).join('\n'));
  for (const m of mods) {
    const sel = f.features.filter((x) => x.module === m);
    p.push(`## ${m} (${sel.length}건)\n\n${table(sel.map((item) => ({ ...item, screens: associatedScreens([item.id], f.features, screens) })), ['id', 'title', 'screens', 'roadmap', 'reqs'])}`
      + `\n\n### 상세\n\n${blocks(sel, ['id'], 'title', '####')}`);
  }
  p.push('## 폐기한 기능\n\n**ID 는 재사용하지 않는다** — 결번으로 남기고 사유를 적는다.\n\n'
    + auto(f.retired));
  write(`${OUT}31-기능목록.md`, p);
}

function 요건정의서() {
  const d = load('31-features');
  const screens = load('60-screens').screens ?? [];
  const apis = load('50-api').apis ?? [];
  const folder = `${OUT}32-요건정의서/`;
  const current = new Set(d.features.map((f) => `${f.id}.md`));
  const full = join(REPO, folder);
  if (existsSync(full)) for (const name of readdirSync(full)) {
    if (!/^FEAT-\d+\.md$/.test(name) || current.has(name)) continue;
    const file = join(full, name);
    if (/^generated_from: documents\/spec\/31-features/m.test(readFileSync(file, 'utf8'))) rmSync(file);
  }
  for (const feature of d.features) {
    const linkedApis = apis.filter((a) => featureIds(a).includes(feature.id) || relationIds(feature, 'apis').includes(a.id));
    write(`${folder}${feature.id}.md`, [
      head(feature.id, feature.title, specSource('31-features'), d.meta.status, d.meta.baselined, relationIds(feature, 'reqs', 'usecases', 'nfrs')),
      '> 기능의 정본은 31-features이며 업무 규칙은 연결된 요구사항을 따른다. 화면·HTTP API가 없는 기능도 정상이다.',
      blocks([feature], ['id'], 'title', '##'),
      `## 연결 화면\n\n${associatedScreens([feature.id], d.features, screens).join(' · ') || '없음 — 화면 없는 기능 또는 설계 전'}`,
      `## 연결 API\n\n${table(linkedApis, ['id', 'summary', 'method', 'path', 'source_contract'])}`,
    ]);
  }
  write(`${folder}_index.md`, [head('FEAT-SPECS', '기능 명세 목록', specSource('31-features'), d.meta.status, d.meta.baselined),
    '| 기능 | 이름 |\n|---|---|\n' + d.features.map((f) => `| [${f.id}](${f.id}.md) | ${flat(f.title)} |`).join('\n')]);
}

// ── 16행 · 화면 목록 · 화면 정의서 ────────────────────────────────
function 화면정의서() {
  const s = load('60-screens');
  const errors=menuContractErrors(s);if(errors.length)throw new Error(errors.join('\n'));
  const screens = orderByAdminMenu(s, s.screens);
  const mc = s.menu_criteria;
  const p = [head('SCR-LIST', '화면 목록 · 화면 정의서', specSource('60-screens'),
    s.meta.status, s.meta.baselined, null,
    '> **화면 하나의 컬럼·검증·테스트 케이스는 여기 없다.** 그것은\n'
    + '> `documents/screen/{모듈}/{화면경로}/requirements.md` 가 갖는다(산출물 지도 33행) — 설계자가\n'
    + '> 목업과 함께 보고 승인하는 대상이며 spec 에 담기지 않는 분량이다. 여기는 **목록과 소속**이다.\n')];
  if (s.delivery_strategy) {
    p.push(`## 전달 전략\n\n${blocks([{ id: 'delivery-strategy', ...s.delivery_strategy }], ['id'], 'name')}`);
  }
  if (s.menu_tree) p.push(menuReviewMarkdown(s, p=>existsSync(join(REPO,p))));
  p.push(`## 1. 무엇을 메뉴로 두는가\n\n**${flat(mc.claim)}** — ${flat(mc.why)}\n\n${bullets(mc.tests)}`
    + (mc.note ? `\n\n> ${flat(mc.note)}` : ''));
  const menuRows = screens.map((screen) => ({ ...screen, entry: screen.entry ?? (screen.route === null ? 'standalone' : screen.route ? 'menu' : '미정') }));
  p.push('## 메뉴·진입 목록\n\n' + table(menuRows, ['id', 'title', 'entry', 'menu_code', 'menu_parent', 'route'])
    + '\n\n메뉴 코드·계층·진입 유형 미정은 상세 설계에서 정하며, 구현 후 실제 메뉴 트리와 대조한다. 공통 화면을 메뉴로 만들지 않는다.');
  p.push(`## 2. 통제 경계\n\n${auto(s.control_boundaries)}`);
  p.push(`## 3. 규칙\n\n${bullets(s.rules)}`);
  let n = 3;
  for (const m of [...new Set(screens.map((x) => x.module))]) {
    const sel = screens.filter((x) => x.module === m);
    n += 1;
    p.push(`## ${n}. ${m} 화면 (${sel.length}건)`);
    for (const grp of [...new Set(sel.map((x) => x.group))]) {
      const gs = sel.filter((x) => x.group === grp);
      p.push(`### ${grp ?? '(분류 없음)'}\n\n${table(gs.map((item) => ({ ...item, features: featureIds(item) })), ['id', 'title', 'feature', 'features', 'uses_screens', 'roadmap'])}`
        + `\n\n${blocks(gs, ['id'], 'title', '####')}`);
    }
  }
  p.push(`## ${n + 1}. 차수별 노출\n\n`
    + Object.entries(s.roadmap_rollout).map(([k, v]) => `- **${k}** — ${flat(v)}`).join('\n'));
  p.push(`## ${n + 2}. 메뉴로 나타나지 않는 것\n\n${auto(s.not_menus, { titleKey: 'name' })}`);
  p.push(`## ${n + 3}. 미확정 (${s.undecided.length}건)\n\n${auto(s.undecided)}`);
  p.push(`## ${n + 4}. 화면 문서의 단위와 순서\n\n`
    + Object.entries(s.doc_order).map(([k, v]) => `- **${lab(k)}** — ${flat(v)}`).join('\n'));
  write(`${OUT}60-화면정의서.md`, p);
}

// ── 19행 · 권한 매트릭스 ──────────────────────────────────────────
function 권한매트릭스() {
  const z = load('22-authz');
  const p = [head('AUTHZ', '권한 매트릭스 — 그룹 판정 · 매핑 · SQL 통제', specSource('22-authz'),
    z.meta.status, z.meta.baselined, null,
    '> **요구사항은 `04-요구사항/`(REQ-103·105·111)이 갖는다** — 여기는 *모델* 이다.\n')];
  p.push(`## 1. 통제의 축\n\n**${flat(z.axis.claim)}** — ${flat(z.axis.why)}`);
  const a = z.application_access;
  p.push(`## 2. 애플리케이션 진입 권한 (${a.id})\n\n`
    + `- **결정** — ${flat(a.decision)}\n- **주장** — ${flat(a.claim)}\n${bullets(a.rules)}\n\n`
    + `- **남은 설계** — ${flat(a.unresolved_design)}\n- **추적** — ${flat(a.traces_to)}`);
  const j = z.judgement;
  p.push(`## 3. 그룹 판정 (${j.id})\n\n**${flat(j.rule)}**\n\n`
    + `- **적용 대상** — ${flat(j.applies_to)}\n- **공통 컴포넌트** — ${flat(j.single_component)}\n`
    + bullets(j.rules));
  p.push(`## 4. 매핑 구조 — 역할 × 대상\n\n${auto(z.mappings)}\n\n${bullets(z.mapping_rules)}`);
  const SEC = [['row_scope', '행 스코프'], ['sql_control', 'SQL 통제'], ['injection_control', '주입 통제'],
    ['default_filter', '기본 필터'], ['operational_means', '운영 수단'],
    ['responsibility_layers', '책임의 층'], ['rate_limit_ownership', '유량 제한의 소유'],
    ['operational_role_boundary', '운영 역할 경계']];
  for (const [key, ttl] of SEC) {
    const v = z[key];
    const body = [];
    for (const [k, x] of Object.entries(v)) {
      if (k === 'id') continue;
      if (Array.isArray(x) && x.length && typeof x[0] === 'object' && x[0] !== null) {
        body.push(`- **${lab(k)}**`, '', auto(x), '');
      } else if (Array.isArray(x)) {
        body.push(`- **${lab(k)}**`, ...x.map((y) => `  - ${flat(y)}`));
      } else {
        body.push(`- **${lab(k)}** — ${flat(x)}`);
      }
    }
    p.push(`## ${ttl} (${v.id ?? '—'})\n\n${body.join('\n')}`);
  }
  write(`${OUT}22-권한매트릭스.md`, p);
}

// ── 26행 · 추적성 매트릭스 ────────────────────────────────────────
/** **셈은 `trace-check` 가 낸다.** 여기서 다시 세지 않는다 — 두 곳에서 세면 갈린다. */
function 추적성() {
  const body = traceReport().trim();
  write(`${OUT}90-추적성매트릭스.md`, [
    head('TRACE', '추적성 매트릭스', `${SPEC}(전체)`, 'mixed', null, null,
      '> **셈은 `node .claude/hooks/trace-check.mjs --report` 가 낸다.** 여기서 다시 세지 않는다 —\n'
      + '> 두 곳에서 세면 갈리고, 갈렸을 때 어느 쪽이 참인지 알 방법이 없다. 이 문서는 그\n'
      + '> 출력을 사람이 읽는 형태로 담은 것뿐이다.\n>\n'
      + '> 규칙 T1~T14 는 [`aidd-guide/process/05-traceability.md`](../../aidd-guide/process/05-traceability.md) 가 갖는다.\n'),
    body,
  ]);
}

// ── P3 설계 산출물 ────────────────────────────────────────────────
/** P3 정본의 모든 필드를 사람이 읽는 블록으로 낸다. */
function 유즈케이스() {
  const d = load('30-usecases');
  const items = d.usecases ?? [];
  write(`${OUT}30-유즈케이스.md`, [
    head('UC', '유즈케이스 명세', specSource('30-usecases'), d.meta.status,
      d.meta.baselined, items.map((x) => x.id)),
    `## 유즈케이스 ${items.length}건\n\n${blocks(items, ['id'], 'name', '###')}`,
  ]);
}

/** UC의 흐름 정본에서 시퀀스와 해설을 함께 만든다. 승인·구현 상태를 올리지 않는다. */
export function renderSequenceDocuments(usecases) {
  const folder = `${OUT}33-시퀀스/`, documents = new Map(), rows = [];
  const hasText = (value) => typeof value === 'string' && value.trim().length > 0;
  const textList = (value) => Array.isArray(value) && value.length > 0 && value.every(hasText);
  for (const uc of usecases) {
    if (!uc.sequence_design) continue;
    if (!/^UC-\d+$/.test(uc.id)) throw new Error('시퀀스의 상위 UC 식별자가 잘못됐다');
    const { diagrams, ...metadata } = uc.sequence_design;
    if (!hasText(metadata.scope) || !textList(metadata.contracts_from)) throw new Error(`${uc.id}: 범위·상세 계약 근거가 필요하다`);
    if (!Array.isArray(diagrams) || !diagrams.length) throw new Error(`${uc.id}: 시퀀스가 비었다`);
    const keys = new Set();
    const parts = [
      head(`SEQ-${uc.id}`, `${uc.id} — ${uc.name}`, specSource('30-usecases'), uc.design_status ?? 'draft', null,
        [uc.id, ...(uc.reqs ?? []), ...(uc.feature_contract ? [uc.feature_contract] : [])])
        .replace('generated_from:', `module: ${uc.workstream ?? '전체'}\ngenerated_from:`),
      '> 설계 초안이다. 화살표는 조건에 맞는 분기만 실행한다. 다른 영역의 미결 계약이나 제품 실행 시험 완료를 뜻하지 않는다.',
      blocks([{ title: '범위와 계약', ...metadata }], [], 'title', '##'),
    ];
    for (const diagram of diagrams) {
      const { key, title, mermaid, ...details } = diagram;
      if (!/^[a-z][a-z0-9-]*$/.test(key ?? '') || keys.has(key)) throw new Error(`${uc.id}: 시퀀스 키 중복/오류 ${key}`);
      keys.add(key);
      if (!hasText(title) || typeof mermaid !== 'string' || !/^sequenceDiagram(?:\s*[\r\n]|$)/.test(mermaid.trimStart())
          || mermaid.includes('```') || !/^\s*alt\s/m.test(mermaid)
          || !hasText(details.transaction_boundary) || !textList(details.reading_order)) {
        throw new Error(`${uc.id}/${key}: 제목·sequenceDiagram·예외 분기·트랜잭션 경계·읽는 순서가 필요하다`);
      }
      if (mermaid.split('\n').some((line) => /^\s*Note\b[^:]*:/.test(line) && line.replace(/#[a-z0-9]+;/gi, '').includes(';'))) {
        throw new Error(`${uc.id}/${key}: Note 설명의 세미콜론은 문장 구분 기호로 바꾸거나 #59;로 이스케이프한다`);
      }
      parts.push(`<a id="${key}"></a>\n\n## ${key} — ${title}`, '```mermaid\n' + mermaid.trimEnd() + '\n```',
        blocks([{ title: '순서와 검증', ...details }], [], 'title', '###'));
    }
    documents.set(`${folder}${uc.id}.md`, `${parts.join('\n\n').replace(/\n+$/, '')}\n`);
    rows.push({ id: uc.id, module: uc.workstream ?? '전체', name: `[${uc.name}](${uc.id}.md)`, diagrams: diagrams.length, status: uc.design_status ?? 'draft' });
  }
  documents.set(`${folder}_index.md`, [head('SEQUENCES', '핵심 처리 시퀀스', specSource('30-usecases'), 'mixed', null, rows.map((x) => x.id)),
    'sequence_design이 작성된 유즈케이스만 싣는다. 이 목록의 존재가 다른 유즈케이스의 시퀀스 완료를 뜻하지 않는다.',
    table(rows, ['id', 'module', 'name', 'diagrams', 'status'])].join('\n\n') + '\n');
  return documents;
}

/** 날짜만 달라진 생성물은 허용하며 내용 누락·변조·폐기 문서 잔존은 거부한다. */
export function checkSequenceDocuments(repo = REPO, usecases = load('30-usecases').usecases ?? []) {
  const documents = renderSequenceDocuments(usecases), problems = [];
  const normalizeDoc = (text) => text.replace(/^last_sync: .*$/m, 'last_sync: <date>');
  for (const [file, expected] of documents) {
    const actual = join(repo, file);
    if (!existsSync(actual) || normalizeDoc(readFileSync(actual, 'utf8')) !== normalizeDoc(expected)) problems.push(`${file}: 시퀀스 생성물 누락/노후화`);
  }
  const folder = `${OUT}33-시퀀스/`;
  if (existsSync(join(repo, folder))) for (const file of readdirSync(join(repo, folder))) {
    if (file.endsWith('.md') && !documents.has(folder + file)) problems.push(`${folder}${file}: 폐기된 시퀀스 잔존`);
  }
  if (problems.length) throw new Error(problems.join('\n') + '\n조치: doc-gen 시퀀스를 재생성한다.');
  return specSet('30-usecases').files;
}

function 시퀀스() {
  const documents = renderSequenceDocuments(load('30-usecases').usecases ?? []), folder = `${OUT}33-시퀀스/`;
  for (const [file, content] of documents) write(file, [content]);
  if (existsSync(join(REPO, folder))) for (const file of readdirSync(join(REPO, folder))) {
    if (file.endsWith('.md') && !documents.has(folder + file)) rmSync(join(REPO, folder, file));
  }
}

/** 괄호가 Mermaid 셰이프를 닫지 않도록 공통 escape를 적용한다. */
const mermaidLabel = (v) => flat(v).replace(/\(/g, '#40;').replace(/\)/g, '#41;').replace(/"/g, '&quot;');

function ERD() {
  const d = load('40-domain');
  const items = d.entities ?? [];
  const diagram = ['```mermaid', 'erDiagram'];
  for (const ent of items) {
    if (ent.storage_kind === 'nonpersistent' || ent.design_stage === 'logical') continue;
    diagram.push(`  ${ent.table ?? ent.id} {`);
    const physicalTypes = new Map((ent.physical?.attributes ?? []).map((column) => [column.name, column.type]));
    for (const attr of ent.attributes ?? []) {
      const flags = [attr.pk ? 'PK' : '', attr.fk ? 'FK' : ''].filter(Boolean).join(',');
      const type = physicalTypes.get(attr.physical_name ?? attr.name) ?? attr.type ?? 'string';
      diagram.push(`    ${String(type).replace(/[^A-Za-z0-9_]/g, '_')} ${attr.physical_name ?? attr.name}${flags ? ` ${flags}` : ''}`);
    }
    diagram.push('  }');
  }
  diagram.push('```');
  write(`${OUT}40-ERD.md`, [
    head('ERD', '도메인·데이터 모델', specSource('40-domain'), d.meta.status,
      d.meta.baselined, items.map((x) => x.id)),
    `## 공통 규약\n\n${blocks([{ id: 'conventions', ...(d.conventions ?? {}) }], ['id'], 'name', '###')}`,
    `## 관계 개요\n\n${diagram.join('\n')}`,
    '논리 설계 단계의 엔티티는 아래 논리명·속성·관계로 검토한다. 물리명이 정해지기 전에는 물리 ERD에 임시 테이블명으로 넣지 않는다.',
    '컬럼별 물리 정의·키·제약·인덱스·Liquibase 근거는 [테이블 목록·명세서](41-테이블명세서/_index.md)에서 확인한다. 아래 속성과 관계는 업무 모델이며 물리 외래키를 대신하지 않는다.',
    `## 엔티티 ${items.length}건\n\n${blocks(items.map(({ physical, ...entity }) => entity), ['id', 'table'], 'name', '###')}`,
  ]);
}

let checkedDomain;
function schema() {
  const batchErrors = checkBatchSpec({ repo: REPO, parseYaml });
  if (batchErrors.length) throw new Error(batchErrors.join('\n'));
  if (!checkedDomain) { const snapshot=domainSchema(REPO); assertHistoryContract(snapshot.domain); const errors=approvalContractErrors(snapshot.domain); if(errors.length)throw new Error(errors.join('\n')); checkedDomain=snapshot; }
  return checkedDomain;
}

/** 독립 물리 명세. 구현 전 계약과 소스 기준 예상 스키마를 명확히 나눈다. */
function 테이블명세서(verify = false, snapshot = schema(), capture = null) {
  const { domain, catalog } = snapshot;
  const folder = `${OUT}41-테이블명세서/`;
  const technical = domain.conventions.liquibase.technical_tables ?? [];
  const items = [
    ...domain.entities.map((e) => ({ ...e, key: e.id, physical: e.physical, description: e.name })),
    ...technical.map((t) => ({ ...t, key: t.table, implementation: 'implemented', description: t.reason, physical: catalog.tables.get(t.table) })),
  ];
  // 검사를 모두 통과한 뒤에만 이 생성기가 소유한 파생 파일을 갱신한다.
  const problems = [], emitted = new Set();
  const emit = (file, parts) => {
    emitted.add(posix.basename(file));
    if (capture) { capture.set(file, parts.join('\n\n').replace(/\n+$/, '') + '\n'); return; }
    if (!verify) { write(file, parts); return; }
    const normalizeDate = (s) => s.replace(/^last_sync:.*$/m, 'last_sync: DATE');
    const expected = parts.join('\n\n').replace(/\n+$/, '') + '\n';
    if (!existsSync(join(REPO, file)) || normalizeDate(readFileSync(join(REPO, file), 'utf8')) !== normalizeDate(expected)) problems.push(`${file}: 재생성 필요`);
  };
  if (!verify && !capture) mkdirSync(join(REPO, folder), { recursive: true });
  const rows = [];
  for (const e of items) {
    const file = `${folder}${e.key}.md`, physical = e.physical;
    const actual = catalog.tables.get(e.table);
    const logical = e.design_stage === 'logical';
    const nonpersistent = e.storage_kind === 'nonpersistent';
    const state = nonpersistent ? '비저장 논리 투영 · 테이블 없음' : logical ? '논리 설계 · 물리명 미정' : e.implementation === 'implemented' ? '소스 기준 물리 정의' : '설계 초안 · 미구현';
    const display = e.table ?? `${e.name} (${nonpersistent ? '비저장' : '물리명 미정'})`;
    rows.push({ module: e.module, table: `[${display}](${e.key}.md)`, entity: e.id ?? '기술 기반', description: e.description, state });
    const parts = [head(`TABLE-${e.key}`, `${display} — ${e.name ?? e.description}`, specSource('40-domain'), e.implementation === 'implemented' ? 'current_source' : 'draft', null, e.id ? [e.id, ...(e.features ?? [])] : []).replace('generated_from:', `module: ${e.module}\ngenerated_from:`),
      `**${state}** — 실제 DB 적용 확인을 뜻하지 않는다.`,
      table([{ owner: e.module, entity: e.id ?? '업무 ENT 없는 기술 기반', features: e.features ?? [], description: e.description }], ['owner', 'entity', 'features', 'description']),
      '[테이블 목록으로](./_index.md) · [도메인·데이터 모델](../40-ERD.md)',
    ];
    if (physical) {
      if (physical.comment) parts.push(`**DB 테이블 설명** — ${flat(physical.comment)}`);
      const constraints = physical.constraints ?? [];
      const columns = (physical.attributes ?? []).map((a, n) => ({
        order: n + 1, column: a.name, type: a.type, nullable: a.nullable, default: a.default ?? '없음',
        keys: constraints.filter((c) => {
          const cols = c.definition.match(/^(PRIMARY KEY|UNIQUE|FOREIGN KEY)\s*\(([^)]+)\)/i)?.[2];
          return cols?.split(',').map((s) => s.trim().toUpperCase()).includes(a.name);
        }).map((c) => c.name),
        description: a.comment ?? (e.attributes ?? []).find((x) => (x.physical_name ?? x.name) === a.name)?.description ?? '미기재',
      }));
      parts.push(`## 컬럼 ${columns.length}개\n\n${table(columns, ['order', 'column', 'type', 'nullable', 'default', 'keys', 'description'])}`,
        '## 키·제약\n\n' + (constraints.length ? table(constraints, ['name', 'definition']) : '명시된 물리 제약 없음'),
        '## 별도 인덱스\n\n' + ((physical.indexes ?? []).length ? table(physical.indexes, ['name', 'unique', 'method', 'definition']) : '별도 인덱스 없음'),
        '기본키·유일성 제약이 생성하는 인덱스는 키·제약에 포함한다. 논리 관계를 물리 외래키로 간주하지 않는다. 컬럼 설명이 미기재이면 이름으로 의미를 추정하지 않는다.');
    } else if (nonpersistent) parts.push(`## 비저장 계약\n\n${flat(e.nonpersistent_reason)}\n\n독립 테이블·컬럼·키·HIST·Liquibase changeset의 대상이 아니다.`);
    else parts.push('## 물리 상세\n\n아직 설계하지 않았다. 컬럼·키·제약을 확정할 때 40-domain의 physical을 작성한다.');
    if (e.id) parts.push('## 업무 모델 연결\n\n' + blocks([{ id: e.id, name: e.name, term: e.term, domain: e.domain, attributes: logical ? e.attributes : undefined, rules: e.rules, relations: e.relations, audit: e.audit, delete_policy: e.delete_policy, retention: e.retention, state_machine: e.state_machine, planned_changes: e.planned_changes }], ['id'], 'name', '###'));
    if (actual) {
      parts.push('## Liquibase 변경 근거\n\n' + table(actual.changesets.map((c) => ({ file: `[${c.file}](${posix.relative(folder, c.file)})`, author: c.author, change_id: c.id })), ['file', 'author', 'change_id']),
        '### 활성 include 경로\n\n' + actual.sources.map((s) => `- [${s}](${posix.relative(folder, s)})`).join('\n'));
    }
    emit(file, parts);
  }
  emit(`${folder}_index.md`, [head('TABLES', '테이블 목록·명세서', specSource('40-domain'), 'mixed', null, domain.entities.map((e) => e.id)),
    '구현된 테이블은 활성 Liquibase 정의와 도메인 물리 계약의 일치를 검사한 뒤 생성한다. 신규 설계 초안은 아직 DB에 적용되지 않았음을 표시한다. 운영 DB 접속이나 마이그레이션 실행 결과가 아니다.',
    `업무 엔티티 ${domain.entities.length}건 · 기술 기반 ${technical.length}건`,
    table(rows, ['module', 'table', 'entity', 'description', 'state']),
  ]);
  if (!capture && existsSync(join(REPO, folder))) for (const file of readdirSync(join(REPO, folder))) {
    if (!file.endsWith('.md') || emitted.has(file)) continue;
    if (verify) problems.push(`${folder}${file}: 폐기된 명세서 잔존`);
    else rmSync(join(REPO, folder, file));
  }
  if (problems.length) throw new Error(['생성 보류 — 테이블 명세서가 현재 원본과 다르다', ...problems,
    '조치: AI가 node .claude/skills/doc-gen/gen.mjs 와 --check를 실행한 뒤 HTML build/check를 다시 실행한다.',
    '원본 대조에서 새 불일치가 나오면 그 안내의 보완 파일부터 처리한다. 생성 문서를 직접 수정하거나 사용자에게 수동 수정을 요구하지 않는다.',
    '기존 HTML은 남아 있지만 최신 검증본은 아니다.'].join('\n'));
}

/** 출력 없이 명세 내용을 계산하여 논리/물리 렌더링을 독립 검증한다. */
export function renderTableDocuments(snapshot) {
  const documents = new Map();
  테이블명세서(false, snapshot, documents);
  return documents;
}

export function checkTableDocuments() {
  const { catalog } = schema();
  테이블명세서(true);
  return [...specSet('40-domain').files, ...catalog.files];
}

function openApiText(d) {
  const lines = [
    'openapi: 3.1.0',
    'info:',
    '  title: system API',
    `  version: ${JSON.stringify(TODAY)}`,
    `  description: ${JSON.stringify(flat(d.meta?.cutover ?? `${specSource('50-api')}에서 생성`))}`,
    'paths:',
  ];
  for (const api of d.apis ?? []) {
    const contract = d.feature_contracts?.[api.feature] ?? {};
    const requestContract = flat(api.request ?? 'none');
    const responseContract = flat(api.response ?? contract.response_rule ?? 'unspecified');
    lines.push(`  ${JSON.stringify(api.path)}:`, `    ${String(api.method).toLowerCase()}:`,
      `      operationId: ${api.id}`, `      summary: ${JSON.stringify(flat(api.summary))}`,
      `      x-feature: ${api.feature}`, `      x-action: ${api.action}`,
      `      x-source-contract: ${JSON.stringify(flat(api.source_contract ?? 'unspecified'))}`,
      `      x-request-contract: ${JSON.stringify(requestContract)}`,
      `      x-response-contract: ${JSON.stringify(responseContract)}`,
      `      x-business-errors: ${JSON.stringify(flat(contract.exception_map ?? {}))}`,
      `      description: ${JSON.stringify(`${flat(contract.request_rule)} / ${flat(contract.response_rule)}`)}`);
    const body = requestContract.match(/(?:^|; )body=([^;]+)/)?.[1];
    const parameters = [];
    for (const [kind, where] of [['query', 'query'], ['path', 'path']]) {
      const group = requestContract.match(new RegExp(`(?:^|; )${kind}=([^;]+)`))?.[1];
      for (const item of group?.split('|') ?? []) {
        const [name, ...typeParts] = item.split(':');
        const rawType = typeParts.join(':');
        parameters.push({ name, where, type: rawType.replace(/\?$/, ''), optional: rawType.endsWith('?') });
      }
    }
    if (parameters.length) {
      lines.push('      parameters:');
      for (const parameter of parameters) {
        lines.push(`        - name: ${JSON.stringify(parameter.name)}`, `          in: ${parameter.where}`,
          `          required: ${parameter.where === 'path' || !parameter.optional}`, '          schema:', '            type: string',
          `            x-java-type: ${JSON.stringify(parameter.type)}`);
      }
    }
    if (body) {
      lines.push('      requestBody:', '        required: true', '        description: ' + JSON.stringify(flat(contract.request_rule)),
        '        content:', '          application/json:', '            schema:', '              type: object',
        `              x-java-type: ${JSON.stringify(body)}`);
    }
    lines.push('      responses:', '        "200":', `          description: ${JSON.stringify(flat(contract.response_rule))}`,
      '          content:', '            application/json:', '              schema:', '                type: object',
      `                x-java-type: ${JSON.stringify(responseContract)}`);
    for (const [code, name] of Object.entries(d.meta?.common_errors ?? {})) {
      lines.push(`        ${JSON.stringify(String(code))}:`, `          description: ${JSON.stringify(flat(name))}`);
    }
  }
  return `${lines.join('\n')}\n`;
}

/**
 * 고객 제출 문서에 싣는 <b>`meta` 키의 허용목록</b> — 허용한 것만 나간다.
 *
 * <p><b>2026-09-15 에 실제로 샜다</b> — 이관 메모를 담은 `meta.source_commentary` 가
 * API 명세와 테스트 케이스 문서에 그대로 실려 제출 사이트까지 갔다. 원인은 그 필드가 아니라
 * <i>`meta` 를 통째로 찍던 렌더러</i>였고, 필드를 지워도 다음 내부 키가 같은 길로 나간다.
 *
 * <p>요점은 <b>분류를 강제하는 것</b>이다 — 어느 쪽에도 없는 키를 만나면 생성이 멈춘다.
 * 허용목록만 두면 업무 값을 빠뜨렸을 때 조용히 사라지는데, `--check` 는 `meta` 를 세지
 * 않으므로(`collect` 가 `meta` 를 건너뛴다) <b>여기서 막지 않으면 아무도 막지 않는다.</b>
 *
 * <p>`status`·`baselined` 는 문서 머리(`head`)가 이미 갖고, `workstreams` 는 진척 표시라
 * 고객 문서의 것이 아니다.
 */
const META_INTERNAL = ['status', 'baselined', 'workstreams'];
const META_PUBLIC = {
  '50-api': ['path_convention', 'cutover', 'contract_rule', 'error_inheritance', 'common_errors'],
  '70-testcases': ['migration_fixture', 'hard_rules'],
};

function publicMeta(name, meta) {
  const allowed = META_PUBLIC[name] ?? [];
  const unknown = Object.keys(meta ?? {})
    .filter((key) => !allowed.includes(key) && !META_INTERNAL.includes(key));
  if (unknown.length) {
    throw new Error(
      `${name}.meta 에 분류되지 않은 키가 있다: ${unknown.join(' · ')}\n`
      + '고객 문서에 실을 것이면 gen.mjs 의 META_PUBLIC 에, 내부용이면 META_INTERNAL 에 더한다.',
    );
  }
  const out = { id: 'meta' };
  for (const key of allowed) if (meta?.[key] !== undefined) out[key] = meta[key];
  return out;
}

function API() {
  const d = load('50-api');
  const items = d.apis ?? [];
  const contracts = Object.entries(d.feature_contracts ?? {}).map(([id, value]) => ({ id, ...value }));
  write(`${OUT}50-API.md`, [
    head('API', 'API 명세', specSource('50-api'), d.meta.status,
      d.meta.baselined, items.map((x) => x.id)),
    `## 공통 계약\n\n${blocks([publicMeta('50-api', d.meta)], ['id'], 'name', '###')}`,
    `## 기능별 계약\n\n${blocks(contracts, ['id'], 'name', '###')}`,
    `## API 목록\n\n${table(items, ['id', 'summary', 'method', 'path', 'feature', 'implementation', 'source_contract'])}`,
    `## API ${items.length}건\n\n${auto(items)}`,
  ]);
  const path = `${OUT}50-openapi.yaml`;
  const full = join(REPO, path);
  mkdirSync(dirname(full), { recursive: true });
  const text = openApiText(d);
  writeFileSync(full, text, 'utf8');
  written.push([path, text.split('\n').length]);
}

function 스케줄배치명세서(verify = false) {
  const d = load('65-batches'), folder = `${OUT}65-스케줄배치명세서/`;
  const jobs = d.jobs ?? [], expected = new Map();
  const emit = (file, parts) => expected.set(file, `${parts.join('\n\n').replace(/\n+$/, '')}\n`);
  emit(`${folder}00-목록.md`, [
    head('JOB', '스케줄 배치 목록·공통 계약', specSource('65-batches'), d.meta.status, d.meta.baselined, jobs.map(j => j.id)),
    '> JOB는 설계 식별자입니다. 구현 상태·시드 기본값과 실제 운영 활성/성공 상태를 구분합니다.',
    table(jobs.map(j => ({ id: `[${j.id}](${j.id}.md)`, title: j.title, module: j.module, implementation: j.implementation, feature: j.feature })), ['id','title','module','implementation','feature']),
    blocks([{ id: '공통 실행 계약', ...d.common }]),
  ]);
  for (const job of jobs) emit(`${folder}${job.id}.md`, [
    head(job.id, job.title, specSource('65-batches'), job.implementation === 'implemented' ? 'current_source' : 'draft', null, [job.feature, ...job.reqs]).replace('generated_from:', `module: ${job.module}\ngenerated_from:`),
    '[배치 목록·공통 계약](./00-목록.md)',
    blocks([job]),
  ]);
  const errors = [], normalizeDate = text => text.replace(/^last_sync:.*$/m, 'last_sync: DATE');
  for (const [file, text] of expected) {
    if (verify) {
      if (!existsSync(join(REPO,file)) || normalizeDate(readFileSync(join(REPO,file),'utf8')) !== normalizeDate(text)) errors.push(`${file}: 재생성 필요`);
    } else write(file,[text]);
  }
  if (existsSync(join(REPO,folder))) for (const name of readdirSync(join(REPO,folder))) {
    if (!/^JOB-\d+\.md$/.test(name) || expected.has(`${folder}${name}`)) continue;
    if (verify) errors.push(`${folder}${name}: 정본에서 제외된 파생 명세`);
    else rmSync(join(REPO,folder,name));
  }
  if (errors.length) throw new Error(errors.join('\n'));
}

function 테스트() {
  const d = load('70-testcases');
  const items = d.testcases ?? [];
  write(`${OUT}70-테스트케이스.md`, [
    head('TC', '테스트 케이스', specSource('70-testcases'), d.meta.status,
      d.meta.baselined, items.map((x) => x.id)),
    `## 공통 시험 계약\n\n${blocks([publicMeta('70-testcases', d.meta)], ['id'], 'name', '###')}`,
    `## 테스트 케이스 ${items.length}건\n\n${blocks(items, ['id', 'feature'], 'title', '###')}`,
  ]);
}

function 운영런북() {
  const d = load('80-operations');
  const sections = [
    ['시스템 구성 한눈에', d.system_overview ?? []],
    ['일상 점검', d.daily_checks ?? []],
    ['장애 대응', d.incidents ?? []],
    ['백업과 복구', d.backup_restore ?? []],
    ['모니터링 지표와 임계값', d.monitoring ?? []],
    ['정기 작업', d.periodic_tasks ?? []],
    ['에스컬레이션', d.escalation ?? []],
  ];
  const items = sections.flatMap(([, values]) => values);
  write(`${OUT}80-운영런북.md`, [
    head('RUNBOOK', '운영 런북', specSource('80-operations'), d.meta.status,
      d.meta.baselined, items.map((x) => x.id)),
    `> 운영 인력이 장애 상황에서 순서대로 실행할 절차다. 값과 절차는 정본 \`${specSource('80-operations')}\`에서만 고친다.`,
    ...sections.map(([title, values], index) =>
      `## ${index + 1}. ${title}\n\n${values.length ? blocks(values, ['id'], 'title', '###') : '> 아직 등록된 절차가 없다.'}`),
  ]);
}

// ── 대상 등록 ─────────────────────────────────────────────────────
const TARGETS = { 요구사항정의서, 아키텍처, 기능목록, 요건정의서, 화면정의서, 권한매트릭스, 유즈케이스, 시퀀스, ERD, 테이블명세서, API, 스케줄배치명세서, 테스트, 운영런북, 추적성 };

/**
 * 같은 대상의 다른 이름. `SKILL.md` 의 인자 줄은 짧은 이름(`화면`·`권한`)을 쓰고
 * 「생성 대상」 표는 긴 이름(`화면정의서`·`권한매트릭스`)을 쓴다. **둘 다 받는다** —
 * 이름이 갈린 것을 사용자가 외워서 피하게 만들지 않는다(CLAUDE.md §5-13 과 같은 뿌리).
 */
const ALIAS = {
  화면: '화면정의서', '메뉴·화면정의서': '화면정의서', 권한: '권한매트릭스', '기능목록·메뉴': '기능목록',
  테스트케이스: '테스트', 런북: '운영런북',
  테이블: '테이블명세서', 테이블목록: '테이블명세서',
};
const resolve = (a) => ALIAS[a] ?? a;

/**
 * **아직 스크립트가 만들지 않는 대상.** 산문 지시(`SKILL.md` 의 「생성 대상」)가 갖는다.
 * <b>여기에 적어 두는 이유는 조용히 빠지지 않게 하는 것이다</b> — 목록에 없으면 아무도
 * 부르지 않고, 부르지 않으면 없는 기능이 된다.
 */
const NOT_YET = {
  DDL: '기존 system 물리 구조는 보존하며, 승인된 신규 물리 변경이 생길 때 생성기로 옮긴다',
};

// ── --check · 생성물이 spec 을 다 담았나 ──────────────────────────
/**
 * <b>파생 뷰에서 조용히 사라진 항목은 없는 항목이 된다.</b> 그것이 이 프로젝트가
 * 이관에서 겪은 사고이므로 기계로 센다. 셋을 본다 — spec ID · 25자 넘는 스칼라 값 ·
 * 상대 링크.
 *
 * <p><b>P3 spec 이 채워지면 이 검사가 빨개지는 것이 정상이다</b> — 새 대상을 만들라는
 * 신호이며, 초록으로 만들려고 검사를 좁히지 않는다.
 */
function check() {
  schema();
  스케줄배치명세서(true);
  테이블명세서(true);
  checkSequenceDocuments();
  const mdFiles = [];
  const walkDir = (dir) => {
    if (!existsSync(dir)) return;
    for (const e of readdirSync(dir, { withFileTypes: true })) {
      const full = join(dir, e.name);
      if (e.isDirectory()) walkDir(full);
      else if (e.name.endsWith('.md')) mdFiles.push(full);
    }
  };
  walkDir(join(REPO, OUT));
  if (!mdFiles.length) {
    console.log('생성물이 없다 — 먼저 생성한다.');
    process.exitCode = 1;
    return;
  }
  const texts = new Map(mdFiles.map((f) => [f, readFileSync(f, 'utf8')]));
  const all = [...texts.values()].join('\n');
  // Mermaid 레이블은 괄호가 escape 돼 있으므로 풀어서 대조한다
  const norm = all.replace(/#40;/g, '(').replace(/#41;/g, ')').replace(/\s+/g, ' ');

  const specNames = listSpecSetNames({ repo: REPO });
  const ids = new Set();
  const leaves = [];
  const collect = (v, path) => {
    if (Array.isArray(v)) v.forEach((x, n) => collect(x, `${path}[${n}]`));
    else if (v && typeof v === 'object') {
      for (const [k, x] of Object.entries(v)) if (k !== 'meta') collect(x, `${path}.${k}`);
    } else if (typeof v === 'string' && v.length > 25) leaves.push([path, v]);
  };
  for (const name of specNames) {
    const loaded = specSet(name);
    const raw = loaded.raw;
    for (const m of raw.matchAll(/^\s*(?:-\s*)?id:\s*([A-Z][A-Z0-9]*-[0-9A-Za-z]+)\s*$/gm)) ids.add(m[1]);
    collect(loaded.document, name);
  }
  const goneIds = [...ids].filter((i) => !all.includes(i)).sort();
  const goneVals = leaves.filter(([, v]) => !norm.includes(v.trim().replace(/\s+/g, ' ').slice(0, 40)));

  const badLinks = [];
  for (const [file, text] of texts) {
    for (const m of text.matchAll(/\]\(([^)#][^)]*?)\)/g)) {
      const t = m[1].split('#')[0];
      if (!t || /^(https?:|mailto:)/.test(t)) continue;
      if (!existsSync(normalize(join(dirname(file), t)))) badLinks.push([posix.basename(file), t]);
    }
  }

  console.log(`생성물 ${mdFiles.length}개 · ${all.split('\n').length}줄`);
  console.log(`spec ID ${ids.size}개 중 생성물에 없는 것: ${goneIds.length}`);
  goneIds.slice(0, 20).forEach((i) => console.log(`  ${i}`));
  console.log(`25자 넘는 스칼라 ${leaves.length}개 중 생성물에서 못 찾은 것: ${goneVals.length}`);
  goneVals.slice(0, 20).forEach(([p, v]) => console.log(`  ${p}\n    ${v.slice(0, 90)}`));
  console.log(`깨진 상대 링크: ${badLinks.length}`);
  badLinks.slice(0, 20).forEach(([f, t]) => console.log(`  ${f} → ${t}`));
  const bad = goneIds.length + goneVals.length + badLinks.length;
  console.log(bad === 0 ? '\n초록 — spec 이 생성물에 빠짐없이 실렸다.' : `\n빨강 — ${bad}건. 새 대상이 필요하거나 렌더러가 필드를 떨어뜨렸다.`);
  process.exitCode = bad === 0 ? 0 : 1;
}

// ── 진입 ──────────────────────────────────────────────────────────
function main() {
const argv = process.argv.slice(2);
// 어느 문서도 쓰기 전에 구조 불일치를 막는다. 기존 산출물은 실패 증거로 보존한다.
schema();
if (argv.includes('--check')) {
  check();
} else {
  const want = argv.filter((a) => !a.startsWith('--')).map(resolve);
  const unknown = want.filter((a) => !(a in TARGETS));
  for (const u of unknown) {
    if (u in NOT_YET) console.log(`건너뜀 — \`${u}\` : 아직 스크립트가 만들지 않는다 (${NOT_YET[u]})`);
    else console.log(`모르는 대상 — \`${u}\`. 아는 것: ${Object.keys(TARGETS).join(' ')}`);
  }
  const run = want.length ? want.filter((a) => a in TARGETS) : Object.keys(TARGETS);
  for (const name of run) TARGETS[name]();
  for (const [p, n] of written) console.log(`${p}  ${n}줄`);
  if (!want.length) {
    console.log(`\n아직 만들지 않는 대상 ${Object.keys(NOT_YET).length}개 — ${Object.entries(NOT_YET).map(([k, v]) => `${k}(${v})`).join(' · ')}`);
  }
}
}
if (process.argv[1] && resolvePath(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { main(); } catch (error) { console.error(error.message); process.exitCode = 1; }
}
