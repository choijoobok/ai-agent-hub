/**
 * 추적성 검사 — `PostToolUse`(`documents/spec/**` 쓰기) 훅 겸 `/trace` 스킬의 실행기.
 *
 * <pre>
 *   node .claude/hooks/trace-check.mjs --all      전체 검사 후 사람이 읽는 리포트
 *   node .claude/hooks/trace-check.mjs --all --workstream admin  작업흐름 G3 범위 검사
 *   node .claude/hooks/trace-check.mjs --report   〃 + 추적성 매트릭스
 *   node .claude/hooks/trace-check.mjs --g1       G1 청구 — T14 카드 완전성을 오류로 센다
 *   node .claude/hooks/trace-check.mjs --impact REQ-116   카드 연계를 따른 변경 영향 분석
 *   node .claude/hooks/trace-check.mjs --source sources/.../Controller.java   소스 변경 영향
 *   (인자 없이 표준 입력으로 훅 입력을 받으면 spec 변경 직후 축약 검사)
 * </pre>
 *
 * <p>규칙은 `aidd-guide/process/05-traceability.md` 의 T1~T14 다. <b>여기는 집행이고 정의는
 * 그쪽이다</b> — 규칙을 두 곳에 적으면 갈리고, 갈리면 초록불이 거짓이 된다.
 */

import { existsSync, readdirSync, statSync } from 'node:fs';
import { join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  REPO,
  readHookInput,
  targetPath,
  repoRelative,
  readText,
  parseYaml,
  warn,
  pass,
  needsFeatTag,
  skillRef,
  readState,
  gateStatus,
  featureWorkstream,
  workstreamGateStatus,
  testcaseCodeRequired,
  requirementBelongsToWorkstream,
  unrealizedRequirementDeferral,
} from './lib.mjs';
import { loadSpecSet } from '../../aidd-guide/harness/runtime/spec-set.mjs';
import { createTraceModel, featureIds, testsByFeature, associatedScreens, affectedIds, sourceSeeds, sourceRef, sourceEntries, relationErrors, linkedDocuments } from '../../aidd-guide/harness/runtime/trace-model.mjs';
import { entityDesignIssues } from '../../aidd-guide/harness/runtime/liquibase-schema.mjs';
import { checkBatchSpec } from '../../aidd-guide/harness/runtime/batch-contract.mjs';

const SPEC_DIR = 'documents/spec';
const SPEC = {
  req: '10-requirements',
  nfr: '21-nfr',
  uc: '30-usecases',
  feat: '31-features',
  ent: '40-domain',
  api: '50-api',
  scr: '60-screens',
  tc: '70-testcases',
};

const ID_RE =
  /\b(ACT|CON|PRC|REQ|TRM|ADR|NFR|UC|FEAT|ENT|API|SCR|JOB|IF|TC|SLICE|DEF|ASM|CR|MNU|GOAL|NGOAL|PRN)-[0-9]{2,}(?:-[0-9]{2,})?\b/g;

/** 정의가 다른 원장에 있는 접두 — dangling 판정에서 뺀다. */
const EXTERNAL = ['ASM-', 'CR-', 'DEF-', 'SLICE-', 'ADR-'];

/** T14 정련 카드의 칸 — 정의는 aidd-guide/phase/P1-discovery.md §5 다. */
const CARD_CORE = ['summary', 'intent', 'goal_trace', 'decider', 'preconditions', 'relations', 'realization', 'examples', 'volatility', 'open', 'maturity'];
const CARD_CONDITIONAL = ['data', 'business_rules', 'exceptions', 'lifecycle', 'permissions', 'audit', 'volume', 'config', 'risk'];
const CARD_COULD = ['summary', 'intent', 'relations', 'maturity'];
const G1_DEFER = /@defer\s+(지금|G0|G1|P0|P1)\b/;

const isMain = Boolean(process.argv[1]) && resolve(process.argv[1]) === fileURLToPath(import.meta.url);
const argv = isMain ? process.argv.slice(2) : [];
const workstreamIndex = argv.indexOf('--workstream');
const requestedWorkstream = workstreamIndex >= 0 ? String(argv[workstreamIndex + 1] ?? '').trim() : null;
const impactIndex = argv.indexOf('--impact');
const sourceIndex = argv.indexOf('--source');
if (isMain || new URL(import.meta.url).searchParams.has('embedded')) {
  if (sourceIndex >= 0) {
    process.stdout.write(sourceImpactReport(String(argv[sourceIndex + 1] ?? '').trim()) + '\n');
    process.exit(0);
  }
  if (impactIndex >= 0) {
    process.stdout.write(impactReport(String(argv[impactIndex + 1] ?? '').trim()) + '\n');
    process.exit(0);
  }
  if (argv.includes('--all') || argv.includes('--report') || argv.includes('--g1')) {
    const options = { g1: argv.includes('--g1') };
    process.stdout.write(traceReport(requestedWorkstream, argv.includes('--report'), options) + '\n');
    process.exit(0);
  }

  const input = await readHookInput();
  const path = repoRelative(targetPath(input));
  if (!path || !path.startsWith(`${SPEC_DIR}/`)) pass();

  const { errors } = fullCheck();
  if (errors.length === 0) pass();

  const head = errors.slice(0, 8).map(([rule, target, msg]) => `  [${rule}] ${target}: ${msg}`);
  warn(
    [
      `추적성 오류 ${errors.length}건이 남아 있다 (게이트 통과 불가):`,
      ...head,
      errors.length > 8 ? `  … 외 ${errors.length - 8}건. ${skillRef('trace')} 로 전체 확인` : '',
    ]
      .filter(Boolean)
      .join('\n'),
  );
}

// ── 검사 ──────────────────────────────────────────────────────────

function load(name) {
  return loadSpecSet({ repo: REPO, name, parseYaml, required: false })?.document ?? null;
}

/** 문서에서 항목 리스트를 꺼낸다 — 최상위 리스트이거나, 리스트를 담은 첫 칸이다. */
function asList(doc, key) {
  if (!doc) return [];
  if (Array.isArray(doc)) return doc.filter(Boolean);
  if (typeof doc === 'object') {
    if (Array.isArray(doc[key])) return doc[key].filter(Boolean);
    for (const v of Object.values(doc)) if (Array.isArray(v)) return v.filter(Boolean);
  }
  return [];
}

/** 여러 칸에 흩어진 참조 ID 를 하나의 집합으로 모은다. */
function refs(item, ...keys) {
  const out = new Set();
  for (const k of keys) {
    const v = item?.[k];
    if (typeof v === 'string') out.add(v);
    else if (Array.isArray(v)) for (const x of v) if (typeof x === 'string') out.add(x);
    else if (v && typeof v === 'object') {
      for (const vv of Object.values(v)) {
        if (typeof vv === 'string') out.add(vv);
        else if (Array.isArray(vv)) for (const x of vv) if (typeof x === 'string') out.add(x);
      }
    }
  }
  return out;
}

/** spec 전체에서 `id:` 정의와 본문 참조를 모은다 — T11 · T12 의 근거. */
function collectIds() {
  const defined = new Map();
  const referenced = new Map();
  const base = join(REPO, SPEC_DIR);
  if (!existsSync(base)) return { defined, referenced };
  for (const file of walk(base)) {
    if (!/\.(ya?ml)$/.test(file)) continue;
    const text = readText(file);
    const rel = relative(REPO, file).split(sep).join('/');
    for (const line of text.split('\n')) {
      const m = line.match(/(?:^|[-\s{])id:\s*([A-Z]+-[0-9][\w-]*)/);
      if (m) {
        if (!defined.has(m[1])) defined.set(m[1], []);
        defined.get(m[1]).push(rel);
      }
    }
    for (const m of text.matchAll(ID_RE)) {
      if (!referenced.has(m[0])) referenced.set(m[0], new Set());
      referenced.get(m[0]).add(rel);
    }
  }
  return { defined, referenced };
}

function walk(dir, acc = []) {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) walk(full, acc);
    else acc.push(full);
  }
  return acc;
}

/** `RM-P2` → 2. 차수가 없으면 0 — <b>모든 차수에 걸린다</b>는 뜻이다. */
function roadmapOrder(v) {
  const m = /^RM-P([0-9]+)$/.exec(String(v ?? '').trim());
  return m ? Number(m[1]) : 0;
}

/**
 * 항목이 <b>현재 차수보다 뒤</b>면 그 차수 이름을, 아니면 `null` 을 준다.
 * `current.roadmap` 을 읽지 못하면 아무것도 뒤가 아니다 — 강도를 낮추지 않는다.
 */
function laterThanCurrentRoadmap() {
  const cur = roadmapOrder(readState()?.current?.roadmap) || Infinity;
  return (x) => (roadmapOrder(x?.roadmap) > cur ? x.roadmap : null);
}

function fullCheck(targetWorkstream = null, { g1 = false } = {}) {
  const errors = [];
  const warnings = [];
  for (const message of checkBatchSpec({ repo: REPO, parseYaml })) errors.push(['T9', 'JOB', message]);

  const reqs = asList(load(SPEC.req), 'requirements');
  const nfrs = asList(load(SPEC.nfr), 'nfrs');
  const ucs = asList(load(SPEC.uc), 'usecases');
  const feats = asList(load(SPEC.feat), 'features');
  const ents = asList(load(SPEC.ent), 'entities');
  const apis = asList(load(SPEC.api), 'apis');
  const scrs = asList(load(SPEC.scr), 'screens');
  const tcs = asList(load(SPEC.tc), 'testcases');
  const state = readState();
  if (targetWorkstream && !state?.workstreams?.[targetWorkstream]) {
    errors.push(['WS', targetWorkstream || '(없음)', '등록되지 않은 작업흐름']);
  }

  const selectedFeats = targetWorkstream
    ? feats.filter((feature) => featureWorkstream(feature) === targetWorkstream)
    : feats;
  const selectedFeatIds = new Set(selectedFeats.map((feature) => feature?.id).filter(Boolean));
  const selectedReqIds = new Set(selectedFeats.flatMap((feature) => [...refs(feature, 'reqs')]));
  const selectedUcIds = new Set(selectedFeats.flatMap((feature) => [...refs(feature, 'usecases')]));
  const selectedNfrIds = new Set(selectedFeats.flatMap((feature) => [...refs(feature, 'nfrs')]));
  const selectedEntIds = new Set(selectedFeats.flatMap((feature) => [...refs(feature, 'entities')]));
  const selectedReqs = targetWorkstream
    ? reqs.filter((item) => selectedReqIds.has(item?.id) || requirementBelongsToWorkstream(item, targetWorkstream))
    : reqs;
  const selectedUcs = targetWorkstream
    ? ucs.filter((item) => selectedUcIds.has(item?.id) || [...refs(item, 'reqs', 'req')].some((id) => selectedReqIds.has(id)))
    : ucs;
  const selectedNfrs = targetWorkstream ? nfrs.filter((item) => selectedNfrIds.has(item?.id)) : nfrs;
  const selectedEnts = targetWorkstream ? ents.filter((item) => selectedEntIds.has(item?.id)) : ents;
  const selectedApis = targetWorkstream ? apis.filter((item) => featureIds(item).some((id) => selectedFeatIds.has(id)) || selectedFeats.some((f) => refs(f, 'apis').has(item.id))) : apis;
  const selectedScrIds = new Set(associatedScreens([...selectedFeatIds], feats, scrs));
  const selectedScrs = targetWorkstream ? scrs.filter((item) => selectedScrIds.has(item.id)) : scrs;
  const selectedTcs = targetWorkstream ? tcs.filter((item) => featureIds(item).some((id) => selectedFeatIds.has(id))) : tcs;
  for (const [id, message] of relationErrors(createTraceModel({ features: feats, screens: scrs, apis, testcases: tcs }))) errors.push(['T9', id, message]);

  // T12 — ID 중복 정의
  const { defined, referenced } = collectIds();
  for (const [id, files] of defined) {
    if (files.length > 1) errors.push(['T12', id, `ID 중복 정의: ${[...new Set(files)].sort().join(', ')}`]);
  }

  // T11 — dangling reference
  for (const [id, files] of referenced) {
    if (defined.has(id)) continue;
    if (EXTERNAL.some((p) => id.startsWith(p))) continue;
    errors.push(['T11', id, `정의되지 않은 ID 참조: ${[...files].sort().join(', ')}`]);
  }

  // 커버리지 맵
  const ucReqs = new Set();
  for (const u of ucs) for (const r of refs(u, 'reqs', 'req')) ucReqs.add(r);
  const featUp = new Set();
  for (const f of feats) for (const r of refs(f, 'usecases', 'reqs')) featUp.add(r);

  // T1 — REQ ↔ ACT
  for (const r of selectedReqs) {
    if (r?.type === 'functional' && refs(r, 'actors', 'actor').size === 0) {
      errors.push(['T1', r?.id, '액터 연결 없음']);
    }
  }

  // T2 — 미실현 REQ
  for (const r of selectedReqs) {
    if (r?.type !== 'functional' || r?.priority === 'wont') continue;
    if (!ucReqs.has(r?.id) && !featUp.has(r?.id)) {
      const deferred = unrealizedRequirementDeferral(r, state, targetWorkstream);
      (deferred ? warnings : errors).push([
        'T2',
        r?.id,
        `미실현 요구사항 — UC/FEAT 연결 없음${deferred ? ` — ${deferred}` : ''}`,
      ]);
    }
  }

  // T14 — 요구사항 정련 카드
  checkRequirementCards({ reqs, selectedReqs, feats, scrs, state, g1, errors, warnings });

  // T3 — UC → REQ
  for (const u of selectedUcs) {
    if (refs(u, 'reqs', 'req').size === 0) errors.push(['T3', u?.id, '근거 REQ 없음']);
  }

  /*
    T4 — FEAT → UC / REQ / **NFR**

    `nfrs` 를 받아들이는 것이 원래 규칙과 다르다. **비기능 목표만 실현하는 기능이 실재한다** —
    모델 단가 관리 · 전사 예산 계획 · 토큰 집계 · 스케줄러는 어느 기능 요구사항의 실현체도
    아니고 비용·안정성 목표의 실현체다. 그것을 고아로 부르면 **없는 REQ 를 만들어 붙이게
    되고**, 그 REQ 는 추적성을 위해서만 존재하는 거짓 항목이 된다.
    → aidd-guide/process/05-traceability.md §3
  */
  for (const f of selectedFeats) {
    if (refs(f, 'usecases', 'reqs', 'nfrs').size === 0) {
      errors.push(['T4', f?.id, '고아 기능 — UC/REQ/NFR 역추적 불가']);
    }
  }

  /*
    T5 · T6 — FEAT → TC. **차수와 소유 작업흐름이 강도를 정한다.**

    <p>`FEAT` 와 `NFR` 은 로드맵 차수와 소유 작업흐름을 갖는다. 아직 오지 않은 차수나
    G3를 청구하지 않은 작업흐름의 TC 결손은 순서다. 전체 검사에서는 경고로 보이고,
    `--workstream` 게이트 검사에서는 그 소유 범위만 오류가 된다.

    <p><b>현재 차수나 소유 작업흐름을 모르면 낮추지 않는다.</b> 모르는 것을 이유로
    게이트를 비우는 것이 가장 나쁜 실패다.
  */
  const tcByFeat = testsByFeature(tcs);
  const EXCEPTION_TYPES = ['business_exception', 'validation', 'permission', 'exception'];
  const later = laterThanCurrentRoadmap();
  const featureDeferral = (feature) => {
    const ahead = later(feature);
    if (ahead) return `${ahead} 은 아직 오지 않았다`;
    if (targetWorkstream) return null;
    const owner = featureWorkstream(feature);
    if (!state?.workstreams) return null;
    const status = workstreamGateStatus(state, owner);
    return status && status !== 'approved' ? `${owner} 작업흐름 G3 전이다` : null;
  };
  for (const f of selectedFeats) {
    const mine = tcByFeat.get(f?.id) ?? [];
    if (mine.length === 0) {
      const deferred = featureDeferral(f);
      (deferred ? warnings : errors).push([
        'T5',
        f?.id,
        deferred ? `테스트 케이스 없음 — ${deferred}` : '테스트 케이스 없음',
      ]);
    } else if (!mine.some((t) => EXCEPTION_TYPES.includes(t?.type))) {
      warnings.push(['T6', f?.id, '정상 케이스만 있다 — 예외 TC 필요']);
    }
  }

  /*
    T7 — NFR 측정. **칸 셋을 본다**: 무엇을 재나(measurement) · 어디서 나오나(source) ·
    무엇이 그것을 잠그나(test_case).

    <p><b>`source` 가 규칙에 들어온 이유</b>: 2026-09-10 이전에는 19건 중 수집 주체를 적은
    것이 하나뿐이었다. 재는 방법만 있고 어디서 나오는지가 없으면 <b>계측을 심을 때 「어디에
    심나」부터 다시 정해야 하고, 그때 정하면 지표마다 다르게 정해진다.</b>

    <p><b>빠진 칸을 이름으로 말한다</b> — 셋을 뭉쳐 하나의 문장으로 보고하면 어느 칸이
    비었는지 파일을 열어야 안다.
  */
  const T7_FIELDS = [['measurement', '측정 방법'], ['source', '측정 원천'], ['test_case', 'TC']];
  const nfrOwners = new Map();
  for (const feature of feats) {
    for (const nfr of refs(feature, 'nfrs')) {
      if (!nfrOwners.has(nfr)) nfrOwners.set(nfr, new Set());
      nfrOwners.get(nfr).add(featureWorkstream(feature));
    }
  }
  const nfrDeferral = (nfr) => {
    const ahead = later(nfr);
    if (ahead) return `${ahead} 은 아직 오지 않았다`;
    if (targetWorkstream || !state?.workstreams) return null;
    const owners = [...(nfrOwners.get(nfr?.id) ?? new Set(['integration']))].filter(Boolean);
    if (owners.length > 0 && owners.every((owner) => workstreamGateStatus(state, owner) !== 'approved')) {
      return `${owners.join('·')} 작업흐름 G3 전이다`;
    }
    return null;
  };
  for (const n of selectedNfrs) {
    const missing = T7_FIELDS.filter(([k]) => !n?.[k]).map(([, ko]) => ko);
    if (missing.length > 0) {
      const deferred = nfrDeferral(n);
      (deferred ? warnings : errors).push([
        'T7',
        n?.id,
        `${missing.join(' · ')} 없음 — ` +
          (deferred || '측정 못 하는 목표는 목표가 아니다'),
      ]);
    }
  }

  // T8 — 미사용 ENT
  const usedEnt = new Set();
  for (const f of selectedFeats) for (const e of refs(f, 'entities')) usedEnt.add(e);
  for (const e of selectedEnts) {
    if (!usedEnt.has(e?.id)) warnings.push(['T8', e?.id, '어떤 FEAT 에서도 쓰이지 않는다']);
  }

  // T9 — API/SCR → FEAT
  for (const a of selectedApis) if (!a?.feature) errors.push(['T9', a?.id, '소속 FEAT 없음']);
  for (const s of selectedScrs) if (!s?.feature) errors.push(['T9', s?.id, '소속 FEAT 없음']);
  // 화면 사례의 기존 번호는 보존하되 TC와의 명시 연결은 끊길 수 없다.
  for (const testcase of selectedTcs) for (const value of refs(testcase, 'screen_cases')) {
    const ref = sourceRef(value);
    if (!ref?.path.startsWith('documents/screen/') || !ref.symbol || !existsSync(join(REPO, ref.path))) {
      errors.push(['T13', testcase.id, `존재하지 않는 화면 사례 연결: ${value}`]);
      continue;
    }
    const escaped = ref.symbol.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    if (!new RegExp(`(?:^|[^A-Za-z0-9-])${escaped}(?:$|[^A-Za-z0-9-])`, 'm').test(readText(join(REPO, ref.path)))) {
      errors.push(['T13', testcase.id, `화면 사례번호 없음: ${value}`]);
    }
  }
  for (const screen of selectedScrs) for (const doc of refs(screen, 'current_doc', 'target_doc')) {
    const ref = sourceRef(doc);
    if (!ref?.path.startsWith('documents/screen/') || !existsSync(join(REPO, ref.path))) continue;
    for (const match of readText(join(REPO, ref.path)).matchAll(/\bTC-\d+\b/g)) {
      if (!defined.has(match[0])) errors.push(['T13', screen.id, `화면 명세가 정의되지 않은 ${match[0]}을 참조한다`]);
    }
  }

  // UC 예외 흐름 (G3 필수)
  for (const u of selectedUcs) {
    if (!u?.exception_flows) errors.push(['UC', u?.id, '예외 흐름 없음 — 예외 없는 유즈케이스는 미완성']);
  }

  // ENT 필수 속성
  for (const e of selectedEnts) {
    for (const message of entityDesignIssues(e)) (targetWorkstream ? errors : warnings).push(['ENT', e.id, message]);
    if (e.implementation === 'planned' && e.design_stage === 'logical') continue;
    const attrs = Array.isArray(e?.attributes) ? e.attributes : [];
    if (!attrs.some((a) => a?.pk)) errors.push(['ENT', e?.id, 'PK 없음']);
    if (attrs.some((a) => a?.enum && a?.name === 'status') && !e?.state_machine) {
      errors.push(['ENT', e?.id, 'status 컬럼이 있으나 state_machine 미정의']);
    }
    if (!e?.delete_policy) warnings.push(['ENT', e?.id, '삭제 정책(delete_policy) 미정의']);
  }

  /*
    T10 — 코드 태그. **면제가 둘이고 뜻이 다르다.**

    ① <b>기능 없는 층</b>({@link FEATURELESS} — lib.mjs). 영구 규칙이다.
    ② <b>템플릿 명단</b>(`feat-baseline.txt`). 갚아야 할 빚이다.

    백오피스 템플릿이 이 추적성 체계보다 앞서 들어왔다. 그것을 다 세면 경고가 <b>영구히
    켜져 있고, 영구히 켜진 경고는 사람에게 경고를 무시하는 법을 가르친다</b> — T6·T8 이 그
    아래 묻힌다. 그래서 오늘의 빚을 명단으로 고정하고 <b>명단 밖만</b> 센다.

    명단은 **줄어들기만 해야 한다** — 태그를 달면 지우고, 새 파일을 더하는 것은 규칙을
    끄는 것이다. → aidd-guide/process/05-traceability.md §3
  */
  const src = join(REPO, 'sources');
  const srcFiles = existsSync(src) ? walk(src) : [];
  if (srcFiles.length > 0) {
    const exempt = new Set(
      readText(join(REPO, '.claude/hooks/feat-baseline.txt'))
        .split('\n')
        .map((l) => l.trim())
        .filter((l) => l && !l.startsWith('#')),
    );
    const untagged = [];
    for (const file of srcFiles) {
      const rel = relative(REPO, file).split(sep).join('/');
      if (!/\.(java|kt|ts|tsx|js|jsx)$/.test(rel)) continue;
      if (/\/(node_modules|build|dist|target|\.gradle)\//.test(rel)) continue;
      if (/\/(test|spec)\//.test(rel) || /\.(test|spec)\.[jt]sx?$/.test(rel)) continue;
      if (!needsFeatTag(rel)) continue;
      if (exempt.has(rel)) continue;
      const head = readText(file).slice(0, 1500);
      const tags = [...head.matchAll(/@feat\s+(FEAT-\d+)\b/g)].map((m) => m[1]);
      if (tags.length === 0) untagged.push(rel);
      for (const tag of tags) if (!defined.has(tag)) errors.push(['T10', rel, `정의되지 않은 기능 태그: ${tag}`]);
    }
    if (untagged.length > 0) {
      warnings.push([
        'T10',
        `소스 ${untagged.length}개`,
        `@feat FEAT-### 태그 없음 — 예: ${untagged.slice(0, 3).join(' · ')}${untagged.length > 3 ? ' …' : ''}`,
      ]);
    }
  }

  /*
    T13 — <b>TC 가 테스트 코드에 있는가.</b> 방향이 T10 과 반대다.

    <p>T10 은 <i>모든 소스가 태그를 갖는가</i> 라서, 템플릿이 먼저 들어온 이 저장소에서
    817건이 한꺼번에 위반이 됐고 명단으로 고정해야 했다. <b>T13 은 <i>모든 TC 가 코드에
    있는가</i> 다</b> — 이미 선 테스트는 대상이 아니므로 <b>면제 명단이 없고 앞으로도
    필요 없다.</b>

    <p><b>비어 있을 때 켜는 것이 맞는 시점이다.</b> 논리 문서 `70-testcases`가 빈 지금 켜면
    위반 0 으로 시작하고 첫 TC 부터 규칙이 돈다 — 나중에 켜면 그때 쌓인 TC 가 전부 위반이
    되고 <b>다시 명단을 만들게 된다</b>(사용자 결정 2026-09-08).

    <p><b>이름에 박는다 — 파일 머리 태그가 아니다.</b> {@code @DisplayName("TC-0122 …")} ·
    {@code it('TC-0122 …')}. 케이스 단위로 잠기고, <b>실패 리포트에 TC 번호가 그대로 떠서</b>
    어느 요건이 깨졌는지 바로 보인다 — 검사보다 이쪽이 더 값이 크다.

    <p><b>강도는 소유 구현 슬라이스가 정한다.</b> G3 승인 뒤에도 뒤 슬라이스의 TC를 전부
    요구하면 수직 슬라이스와 TEST-FIRST가 서로를 막는다. `planned`는 <b>경고</b>이고,
    `slices[].testcases`로 매핑된 슬라이스가 시작된 뒤부터 그 TC만 <b>오류</b>다.
    다만 <b>거꾸로는 언제나 오류다</b>: spec 에 없는 TC 를 테스트가 부르는 것은 미구현이
    아니라 <b>없는 것을 가리키는 이름</b>이다(T11 과 같은 성격이며 T11 은 spec 안만 본다).

    → aidd-guide/process/05-traceability.md §3
  */
  if (srcFiles.length > 0) {
    const inCode = new Map();
    for (const file of srcFiles) {
      const rel = relative(REPO, file).split(sep).join('/');
      if (!/\.(java|kt|ts|tsx|js|jsx)$/.test(rel)) continue;
      if (/\/(node_modules|build|dist|target|\.gradle)\//.test(rel)) continue;
      if (!/\/(test|spec)\//.test(rel) && !/\.(test|spec)\.[jt]sx?$/.test(rel)) continue;
      for (const m of readText(file).matchAll(/\bTC-[0-9]{2,}\b/g)) {
        if (!inCode.has(m[0])) inCode.set(m[0], new Set());
        inCode.get(m[0]).add(rel);
      }
    }
    const declared = new Set(tcs.map((t) => t?.id).filter(Boolean));
    const selectedDeclared = new Set(selectedTcs.map((t) => t?.id).filter(Boolean));
    const tcFeature = new Map(tcs.map((tc) => [tc?.id, tc?.feature]));
    const testcaseById = new Map(tcs.map((tc) => [tc?.id, tc]));
    const featureById = new Map(feats.map((feature) => [feature?.id, feature]));

    const missing = [...selectedDeclared].filter((id) => !inCode.has(id)).sort();
    const strictMissing = [];
    const deferredMissing = [];
    for (const id of missing) {
      const feature = featureById.get(tcFeature.get(id));
      const strict = testcaseCodeRequired(state, testcaseById.get(id), feature);
      (strict ? strictMissing : deferredMissing).push(id);
    }
    if (strictMissing.length > 0) {
      errors.push([
        'T13',
        `TC ${strictMissing.length}건`,
        `테스트 코드에 없다 — 테스트 이름에 ID 를 적는다. 예: ${strictMissing.slice(0, 3).join(' · ')}${strictMissing.length > 3 ? ' …' : ''}`,
      ]);
    }
    if (deferredMissing.length > 0) {
      warnings.push([
        'T13',
        `TC ${deferredMissing.length}건`,
        `테스트 코드에 없다 — 소유 구현 슬라이스 시작 전. 예: ${deferredMissing.slice(0, 3).join(' · ')}${deferredMissing.length > 3 ? ' …' : ''}`,
      ]);
    }

    const unknown = [...inCode.keys()].filter((id) => !declared.has(id)).sort();
    if (unknown.length > 0) {
      errors.push([
        'T13',
        `TC ${unknown.length}건`,
        `spec 에 없는 TC 를 테스트가 부른다 — ${unknown.slice(0, 3).join(' · ')}${unknown.length > 3 ? ' …' : ''}`,
      ]);
    }
  }

  return { errors, warnings };
}

// ── T14 — 요구사항 정련 카드 ──────────────────────────────────────

/*
  T14 는 <b>두 가지를 다르게 센다.</b>

  ① <b>완전성</b>(카드가 있는가 · 칸이 있는가 · 확정됐는가)은 <b>G1 청구 때만 오류</b>다.
  P1 을 쓰는 동안 매 저장마다 오류가 울리면 훅을 끄게 된다. 그래서 평소에는 경고이고,
  `--g1` 로 부를 때 <b>소유 작업흐름의 G1 이 아직 승인 전인 REQ</b> 만 오류로 올린다.
  이미 G1 을 받은 작업흐름은 소급 작성 대상이라 `--g1` 에서도 경고다(2026-09-28 사용자 결정).

  ② <b>정합</b>(연계 비대칭 · realization 과 FEAT.reqs 의 어긋남 · 확정인데 G1 미확정이 남음)은
  <b>언제나 오류</b>다. 이미 쓴 카드끼리 서로 다른 말을 하는 것은 미완성이 아니라 모순이다.

  → aidd-guide/process/05-traceability.md §3 · aidd-guide/phase/P1-discovery.md §5
*/
function relationIds(r, key) {
  const v = r?.relations?.[key];
  return Array.isArray(v) ? v.filter((x) => typeof x === 'string') : [];
}

function hasCard(r) {
  return Boolean(r && typeof r === 'object' && r.maturity);
}

/** REQ 를 소유하는 작업흐름 — REQ 의 명시값과, 그 REQ 를 가리키는 FEAT 의 작업흐름. */
function requirementOwners(r, feats) {
  const owners = new Set();
  const own = featureWorkstream(r);
  if (own) owners.add(own);
  for (const f of feats) {
    if (!refs(f, 'reqs').has(r?.id)) continue;
    const w = featureWorkstream(f);
    if (w) owners.add(w);
  }
  return owners;
}

/** 소유 작업흐름이 모두 G1 을 받았나. 소유자를 모르면 전역 G1 을 본다. */
function requirementG1Approved(state, owners) {
  const known = [...owners].filter((w) => state?.workstreams?.[w]);
  if (known.length === 0) return gateStatus(state, 'P1_discovery') === 'approved';
  return known.every((w) => workstreamGateStatus(state, w, 'P1_discovery') === 'approved');
}

function checkRequirementCards({ reqs, selectedReqs, feats, scrs, state, g1, errors, warnings }) {
  const later = laterThanCurrentRoadmap();
  const byId = new Map(reqs.map((r) => [r?.id, r]));
  const featById = new Map(feats.map((f) => [f?.id, f]));

  for (const r of selectedReqs) {
    if (r?.type !== 'functional' || r?.priority === 'wont' || later(r)) continue;
    const owners = requirementOwners(r, feats);
    const approved = requirementG1Approved(state, owners);
    const strict = g1 && !approved;
    const why = approved ? '소유 작업흐름 G1 승인 뒤 — 소급 작성 대상' : 'G1 청구 전';
    const completeness = (msg) => (strict ? errors : warnings).push(['T14', r?.id, strict ? msg : `${msg} — ${why}`]);

    if (!hasCard(r)) {
      completeness('정련 카드 없음');
      continue;
    }
    const could = r?.priority === 'could';
    const required = could ? CARD_COULD : [...CARD_CORE, ...CARD_CONDITIONAL];
    const missing = required.filter((k) => !(k in r));
    if (missing.length > 0) completeness(`카드 칸 없음: ${missing.join(', ')}`);
    if (!could) {
      const kinds = new Set((Array.isArray(r.examples) ? r.examples : []).map((e) => e?.kind));
      if (!kinds.has('정상') || !kinds.has('예외')) completeness('구체 사례에 정상·예외가 한 건씩 있어야 한다');
      if (r.maturity?.level !== 'confirmed') completeness(`성숙도 ${r.maturity?.level ?? '없음'} — 사용자 확인 전`);
    }

    // ② 정합 — 언제나 오류
    const open = Array.isArray(r.open) ? r.open : r.open ? [r.open] : [];
    if (r.maturity?.level === 'confirmed' && open.some((x) => G1_DEFER.test(String(x)))) {
      errors.push(['T14', r?.id, '확정(confirmed)인데 G1 전에 풀어야 할 open 이 남아 있다']);
    }
    for (const key of ['depends_on', 'constrains', 'conflicts_with']) {
      for (const other of relationIds(r, key)) {
        if (!other.startsWith('REQ-')) {
          errors.push(['T14', r?.id, `relations.${key} 에 REQ 가 아닌 ${other} — 전제는 preconditions 에 둔다`]);
          continue;
        }
        const peer = byId.get(other);
        if (!hasCard(peer)) continue;
        const back = key === 'depends_on' ? 'constrains' : key === 'constrains' ? 'depends_on' : 'conflicts_with';
        if (!relationIds(peer, back).includes(r.id)) {
          errors.push(['T14', r?.id, `연계 비대칭 — ${key}: ${other} 이지만 ${other}.${back} 에 ${r.id} 가 없다`]);
        }
      }
    }
    const listed = new Set((r.realization?.features ?? []).filter((x) => /^FEAT-/.test(String(x))));
    for (const id of listed) {
      const f = featById.get(id);
      if (f && !refs(f, 'reqs').has(r.id)) errors.push(['T14', r?.id, `realization 의 ${id} 가 이 REQ 를 reqs 에 두지 않았다`]);
    }
    for (const f of feats) {
      if (refs(f, 'reqs').has(r.id) && !listed.has(f?.id)) errors.push(['T14', r?.id, `${f?.id} 가 이 REQ 를 실현하는데 realization.features 에 없다`]);
    }
    const screensOfFeatures = new Set(associatedScreens([...listed], feats, scrs));
    for (const s of (r.realization?.screens ?? []).filter((x) => /^SCR-/.test(String(x)))) {
      if (!screensOfFeatures.has(s)) warnings.push(['T14', r?.id, `realization 의 ${s} 가 나열한 기능의 화면이 아니다`]);
    }
  }
}

// ── 변경 영향 분석 (`--impact`) ───────────────────────────────────

/**
 * 식별자·소스가 바뀌면 소유 계약과 소비 관계를 따라 검토 범위를 모은다.
 * 근거 문맥은 따로 표시하며 무관한 형제 기능으로 확산시키지 않는다.
 * 보고만 하고 고치지 않는다(process/05-traceability.md §5).
 */
export function loadTraceModel() {
  const nestedItems = (value) => {
    if (!value || typeof value !== 'object') return [];
    return [...(value.id ? [value] : []), ...Object.values(value).flatMap(nestedItems)];
  };
  return createTraceModel({
    requirements: asList(load(SPEC.req), 'requirements'), nfrs: asList(load(SPEC.nfr), 'nfrs'),
    usecases: asList(load(SPEC.uc), 'usecases'), features: asList(load(SPEC.feat), 'features'),
    entities: asList(load(SPEC.ent), 'entities'), apis: asList(load(SPEC.api), 'apis'),
    screens: asList(load(SPEC.scr), 'screens'), testcases: asList(load(SPEC.tc), 'testcases'),
    interfaces: asList(load('51-interfaces'), 'interfaces'), operations: nestedItems(load('80-operations')),
    jobs: asList(load('65-batches'), 'jobs'),
  });
}

export function sourceImpactData(path) {
  const model = loadTraceModel();
  const file = sourceRef(path);
  const seeds = sourceSeeds(model, path, file ? readText(join(REPO, file.path)) : '');
  const ids = affectedIds(model, seeds);
  return { model, seeds, ids, docs: linkedDocuments(model, ids) };
}

function impactDetails(model, seeds) {
  const ids = affectedIds(model, seeds);
  const items = ids.map((id) => model.byId.get(id));
  const features = items.filter((item) => item.id.startsWith('FEAT-'));
  // 근거는 표시만 한다. 공유 REQ를 거쳐 무관한 형제 기능까지 확산시키지 않는다.
  const context = new Set(features.flatMap((f) => [...refs(f, 'reqs', 'usecases', 'nfrs', 'entities', 'apis', 'interfaces')]));
  const screens = associatedScreens(features.map((f) => f.id), model.groups.features, model.groups.screens);
  for (const id of screens) context.add(id);
  const all = [...new Set([...ids, ...context])].sort();
  const list = (xs) => xs.length ? xs.join(' · ') : '—';
  const out = ['**직접 변경 대상**', `- ${list(seeds)}`, '', '**검토 영향 — 소유 계약·공통 소비자**', `- ${list(ids)}`, '', '**연결된 명세**'];
  for (const prefix of ['REQ', 'UC', 'FEAT', 'ENT', 'API', 'SCR', 'IF', 'TC', 'NFR', 'RUN']) {
    out.push(`- ${prefix}: ${list(all.filter((id) => id.startsWith(prefix + '-')))}`);
  }
  const definitions = collectIds().defined;
  const specFiles = [...new Set(all.flatMap((id) => definitions.get(id) ?? []))].sort();
  const sources = [...new Set(all.flatMap((id) => sourceEntries(model.byId.get(id))))].sort();
  const docs = linkedDocuments(model, all);
  const slices = (readState()?.slices ?? []).filter((slice) => (slice.features ?? []).some((id) => ids.includes(id)));
  out.push('', '**정본·소스·구현 단위**', ...specFiles.map((file) => `- ${file}`), ...docs.map((file) => `- ${file}`),
    `- 명시 소스: ${list(sources)}`, `- 구현 슬라이스: ${list(slices.map((slice) => `${slice.id}(${slice.status})`))}`,
    '', '기록된 연결의 검토 범위다. 자동 수정·승인하지 않는다. 소스 태그는 파일→기능 진입에 쓰며, 역방향 소스 전체는 명시 sources/source_contract와 실제 호출을 함께 확인한다.',
    '연결을 새로 만들거나 지운 변경은 변경 전후를 모두 조회한다. 미등록 소비자·동적 호출·권한/실행 의미는 별도 대조한다.');
  return out.join('\n');
}

export function sourceImpactReport(path) {
  const { model, seeds } = sourceImpactData(path);
  const header = `## 소스 변경 영향 — ${path || '(없음)'}`;
  if (!sourceRef(path)?.path.startsWith('sources/')) return `${header}\n\n저장소 기준 sources/ 경로를 준다. 저장소 밖 경로는 조회하지 않는다.`;
  if (!seeds.length) return `${header}\n\n연결된 명세를 찾지 못했다. 영향 없음으로 판정하지 말고 source_contract·sources·@feat 및 호출자를 확인한다.`;
  return `${header}\n\n${impactDetails(model, seeds)}`;
}

function impactReport(id) {
  const model = loadTraceModel();
  if (!model.byId.has(id)) return `## 변경 영향 분석 — ${id || '(없음)'}\n\n정의된 REQ·UC·FEAT·ENT·API·SCR·IF·TC·NFR 식별자를 준다.`;
  return `## 변경 영향 분석 — ${id}\n\n${impactDetails(model, [id])}`;
}

// ── 리포트 ────────────────────────────────────────────────────────

/**
 * <b>같은 사유는 한 줄로 접는다</b> — 대상은 세어서 옆에 적는다.
 *
 * <p>펴 두면 <b>한 사실이 백 번 운다</b>. 2026-09-06 에 오류 104건이 전부
 * <i>테스트 케이스 없음</i> 하나였고, 원인도 하나였다({@code 70-testcases}가 비었다).
 * 그 백 줄이 흐르는 동안 <b>새로 생긴 한 건은 보이지 않는다</b> — 표가 길어서가 아니라
 * <b>읽히지 않게 되어서</b>다.
 *
 * <p><b>건수를 줄이지 않는다.</b> 머리말의 <i>오류 N건</i> 은 그대로이고 게이트도 그대로
 * 빨갛다 — 접는 것은 <b>보고</b> 이지 판정이 아니다.
 */
function fold(rows) {
  const byReason = new Map();
  for (const [rule, target, msg] of rows) {
    const key = `${rule}\u0000${msg}`;
    if (!byReason.has(key)) byReason.set(key, { rule, msg, targets: [] });
    byReason.get(key).targets.push(target ?? '');
  }
  return [...byReason.values()];
}

/** 접힌 한 줄 — 하나면 대상을 그대로, 여럿이면 <b>건수와 보기 셋</b>. */
function foldedRow({ rule, msg, targets }) {
  if (targets.length === 1) return `| ${rule} | ${targets[0]} | ${msg} |`;
  const sample = targets.slice(0, 3).join(' · ');
  const more = targets.length > 3 ? ` 외 ${targets.length - 3}` : '';
  return `| ${rule} | **${targets.length}건** — ${sample}${more} | ${msg} |`;
}

function section(out, rows, title, cap) {
  if (rows.length === 0) return;
  const folded = fold(rows);
  out.push('', title, '', '| 규칙 | 대상 | 내용 |', '|---|---|---|');
  for (const row of folded.slice(0, cap)) out.push(foldedRow(row));
  if (folded.length > cap) out.push(`| … | | 외 ${folded.length - cap}종 |`);
}

function report(errors, warnings) {
  const out = ['## 추적성 검사 결과', '', `**오류 ${errors.length}건 / 경고 ${warnings.length}건**`];
  section(out, errors, '### 오류 (게이트 통과 불가)', 40);
  section(out, warnings, '### 경고', 30);
  if (errors.length === 0 && warnings.length === 0) out.push('', '모든 추적성 규칙을 통과했다.');
  return out.join('\n');
}

/** 추적성 매트릭스 — `/trace --report` 가 `documents/generated/` 로 옮겨 담는다. */
function matrix() {
  const jobs = asList(load('65-batches'), 'jobs');
  const reqs = asList(load(SPEC.req), 'requirements');
  const ucs = asList(load(SPEC.uc), 'usecases');
  const feats = asList(load(SPEC.feat), 'features');
  const apis = asList(load(SPEC.api), 'apis');
  const scrs = asList(load(SPEC.scr), 'screens');
  const tcs = asList(load(SPEC.tc), 'testcases');

  const rows = ['## 추적성 매트릭스', '', '| REQ | 우선순위 | UC | FEAT | ENT | API | SCR | JOB | TC |', '|---|---|---|---|---|---|---|---|---|'];
  for (const r of reqs) {
    const myUcs = ucs.filter((u) => refs(u, 'reqs', 'req').has(r?.id));
    const myFeats = feats.filter(
      (f) => refs(f, 'reqs').has(r?.id) || myUcs.some((u) => refs(f, 'usecases').has(u?.id)),
    );
    const featIds = myFeats.map((f) => f?.id);
    const cell = (xs) => (xs.length > 0 ? xs.join(', ') : '—');
    rows.push(
      `| ${r?.id} | ${r?.priority ?? '—'} | ${cell(myUcs.map((u) => u?.id))} | ${cell(featIds)} | ` +
        `${cell([...new Set(myFeats.flatMap((f) => [...refs(f, 'entities')]))])} | ` +
        `${cell(apis.filter((a) => featureIds(a).some((id) => featIds.includes(id)) || myFeats.some((f) => refs(f, 'apis').has(a.id))).map((a) => a?.id))} | ` +
        `${cell(associatedScreens(featIds, feats, scrs))} | ` +
        `${cell(jobs.filter(j => refs(j,'reqs').has(r?.id) || featIds.includes(j.feature)).map(j => j.id))} | ` +
        `${cell(tcs.filter((t) => featureIds(t).some((id) => featIds.includes(id))).map((t) => t?.id))} |`,
    );
  }
  rows.push('', '`—`는 해당 연결이 없다는 뜻이다. 화면·HTTP API가 없는 배치/내부 기능은 정상이며, 미실현 요구사항은 T2, 테스트 결손은 T5/T7의 단계별 판정을 따른다.');
  return rows.join('\n');
}

/** 하위 프로세스 없이 `/doc-gen`이 같은 판정과 매트릭스를 재사용한다. */
export function traceReport(workstream = null, includeMatrix = true, options = {}) {
  const { errors, warnings } = fullCheck(workstream, options);
  const parts = [report(errors, warnings)];
  if (includeMatrix) parts.push(matrix());
  return parts.join('\n\n');
}
