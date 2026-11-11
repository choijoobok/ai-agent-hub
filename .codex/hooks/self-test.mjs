/** Codex 어댑터의 patch 정규화와 공통 차단 연결을 실제 자식 프로세스로 확인한다. */

import { strict as assert } from 'node:assert';
import { readFileSync, rmSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import {
  REPO,
  provider,
  skillRef,
  featureWorkstream,
  workstreamGateStatus,
  currentWorkstream,
  readState,
  parseYaml,
  gateStatus,
  requirementBelongsToWorkstream,
  unrealizedRequirementDeferral,
  testcaseCodeRequired,
} from '../../.claude/hooks/lib.mjs';
import { allowHookRepair, emitHookResults, runHookChain } from '../../.claude/hooks/hook-chain.mjs';
import { patchTargets, runFileChain } from './file-chain.mjs';
import { shellWriteTargets } from '../../.claude/hooks/guard-shell-write.mjs';
import { maskQuoted, maskReadOnlyArguments } from '../../aidd-guide/harness/runtime/shell-parse.mjs';
import { PRE_FILE, PRE_SHELL } from '../../aidd-guide/harness/runtime/chains.mjs';
import {
  parseScreenRegistry,
  screenUrlOfFrontend,
} from '../../.claude/hooks/screen-registry.mjs';
import { crashOutput, patchTargets as bootstrapTargets } from '../../aidd-guide/harness/runtime/pretool-bootstrap.mjs';
import { SESSION_NOTICE, SESSION_NOTICE_CONTEXT } from './session-notice.mjs';
import { loadSpecSet } from '../../aidd-guide/harness/runtime/spec-set.mjs';
import { testSpecSet } from '../../aidd-guide/harness/runtime/spec-set.self-test.mjs';

testSpecSet();

const generated = 'sources/backend/bootstrap/src/main/resources/db/changelog/data/003-init-menu.csv';
const patch = `*** Begin Patch\n*** Update File: ${generated}\n@@\n-old\n+new\n*** End Patch`;
const input = {
  cwd: REPO,
  hook_event_name: 'PreToolUse',
  tool_name: 'apply_patch',
  tool_input: { command: patch },
};

assert.deepEqual(patchTargets(input), [join(REPO, ...generated.split('/'))]);
const denied = await runFileChain(input);
assert.equal(denied.some((x) => x?.hookSpecificOutput?.permissionDecision === 'deny'), true);

const generatedSkill = await runFileChain({
  ...input,
  tool_input: {
    command: '*** Begin Patch\n*** Update File: .agents/skills/trace/SKILL.md\n*** End Patch',
  },
});
assert.equal(generatedSkill.some((x) => x?.hookSpecificOutput?.permissionDecision === 'deny'), true);

const safe = await runFileChain({
  ...input,
  tool_input: { command: '*** Begin Patch\n*** Update File: AGENTS.md\n*** End Patch' },
});
assert.equal(safe.some((x) => x?.hookSpecificOutput?.permissionDecision === 'deny'), false);

/* spec-set 조각을 _index에 등재하지 않은 상태에서도 실제 사전 훅 경로는 fail-open 하지 않는다.
   leaf(guard-write)를 직접 실행하면 예외가 보일 수 있으나, provider adapter가 그 예외를 deny로
   바꾸는 것이 계약이다. Claude와 Codex의 공통 chain, Codex의 실제 patch adapter를 함께 센다. */
const orphanPart = join(REPO, 'documents', 'spec', '31-features', '999-orphan.yaml');
writeFileSync(orphanPart, 'features: []\n', 'utf8');
try {
  const malformedInput = {
    cwd: REPO,
    hook_event_name: 'PreToolUse',
    tool_name: 'Edit',
    tool_input: {
      file_path: join(REPO, 'sources', 'frontend', 'src', 'features', '_spec-set-probe.ts'),
      new_string: '/** @feat FEAT-001 */',
    },
  };
  const claudeMalformed = await runHookChain(malformedInput, PRE_FILE);
  assert.equal(claudeMalformed.some((x) => x?.hookSpecificOutput?.permissionDecision === 'deny'), true);
  assert.match(
    String(claudeMalformed[0]?.hookSpecificOutput?.permissionDecisionReason ?? ''),
    /parts에 없는 YAML/,
  );
  const codexMalformed = await runFileChain({
    ...input,
    tool_input: {
      command: '*** Begin Patch\n*** Add File: sources/frontend/src/features/_spec-set-probe.ts\n+/** @feat FEAT-001 */\n*** End Patch',
    },
  });
  assert.equal(codexMalformed.some((x) => x?.hookSpecificOutput?.permissionDecision === 'deny'), true);
  assert.match(
    String(codexMalformed[0]?.hookSpecificOutput?.permissionDecisionReason ?? ''),
    /parts에 없는 YAML/,
  );
} finally {
  rmSync(orphanPart, { force: true });
}

/* 전역 G3가 열리지 않아도 소유 작업흐름을 정확히 찾아 그 작업흐름 상태로 막는다. */
const workstreamDenied = await runFileChain({
  ...input,
  tool_input: {
    command: '*** Begin Patch\n*** Add File: sources/frontend/src/features/_workstream-probe.ts\n+/** @feat FEAT-126 */\n*** End Patch',
  },
});
const workstreamReason = workstreamDenied
  .map((x) => x?.hookSpecificOutput?.permissionDecisionReason ?? '')
  .join('\n');
const liveState = readState();
const probeFeature = loadSpecSet({ repo: REPO, name: '31-features', parseYaml }).document.features.find((feature) => feature.id === 'FEAT-126');
assert.ok(probeFeature, '소유 작업흐름 검사용 기능이 정본에 있어야 한다');
const probeOwner = featureWorkstream(probeFeature);
const integrationG3 = workstreamGateStatus(liveState, probeOwner);
const shouldDenyWorkstream = gateStatus(liveState, 'P3_design') !== 'approved'
  && liveState?.current?.template_maintenance !== true
  && integrationG3 !== 'approved';
assert.equal(
  workstreamDenied.some((x) => x?.hookSpecificOutput?.permissionDecision === 'deny'),
  shouldDenyWorkstream,
);
if (shouldDenyWorkstream) assert.match(workstreamReason, new RegExp(`${probeOwner}\\(G3=${integrationG3}\\)`));
assert.equal(featureWorkstream({ module: 'common' }), 'integration');
assert.equal(featureWorkstream({ module: 'portal', workstream: 'integration' }), 'integration');
const workstreamFixture = {
  current: { workstream: 'admin' },
  workstreams: { admin: { phase: 'P3', gates: { P3_design: { status: 'approved' } } } },
};
assert.equal(workstreamGateStatus(workstreamFixture, 'admin'), 'approved');
assert.equal(workstreamGateStatus(workstreamFixture, 'missing'), null);
assert.equal(currentWorkstream(workstreamFixture)?.phase, 'P3');

/* 화면 역인덱스는 과도기 features와 제품 module 경로를 같은 결과로 읽는다. */
const screenRegistryFixture = parseScreenRegistry(`
  '/legacy': lazy(async () => ({
    default: (await import('@/features/legacy/pages/LegacyPage')).LegacyPage,
  })),
  '/admin/system/role': lazy(async () => ({
    default: (await import('@/modules/system/features/role/pages/RolePage')).RolePage,
  })),
`);
assert.deepEqual(screenRegistryFixture, [
  { url: '/legacy', sourceDir: 'features/legacy', feature: 'legacy' },
  {
    url: '/admin/system/role',
    sourceDir: 'modules/system/features/role',
    feature: 'role',
  },
]);
assert.equal(
  screenUrlOfFrontend(
    'sources/frontend/src/modules/system/features/role/pages/RolePage.tsx',
    screenRegistryFixture,
  ),
  '/admin/system/role',
);

/* T2는 P3 연결을 P1·P2에 강요하지 않되, G3 청구에서 소유 REQ를 빠뜨리지 않는다. */
const traceState = {
  current: { roadmap: 'RM-P1' },
  workstreams: { system: { gates: { P3_design: { status: 'not_started' } } } },
};
const systemRequirement = { id: 'REQ-901', workstream: 'system', roadmap: 'RM-P1' };
assert.equal(unrealizedRequirementDeferral(systemRequirement, traceState), 'system 작업흐름 G3 전이다');
assert.equal(unrealizedRequirementDeferral({ ...systemRequirement, roadmap: 'RM-P2' }, traceState), 'RM-P2 은 아직 오지 않았다');
assert.equal(unrealizedRequirementDeferral(systemRequirement, traceState, 'system'), null);
assert.equal(requirementBelongsToWorkstream(systemRequirement, 'system'), true);
assert.equal(requirementBelongsToWorkstream(systemRequirement, 'admin'), false);

/* T13은 G3 전체가 아니라 TC를 소유한 구현 슬라이스가 시작된 뒤 코드 부재를 오류로 올린다. */
const systemFeature = { id: 'FEAT-901', module: 'system' };
const systemTestcase = { id: 'TC-9011', feature: 'FEAT-901' };
assert.equal(testcaseCodeRequired(traceState, systemTestcase, systemFeature), false);
const approvedTraceState = {
  ...traceState,
  workstreams: { system: { gates: { P3_design: { status: 'approved' } } } },
  slices: [{ id: 'SLICE-901', features: ['FEAT-901'], testcases: ['TC-9011'], status: 'planned' }],
};
assert.equal(testcaseCodeRequired(approvedTraceState, systemTestcase, systemFeature), false);
assert.equal(testcaseCodeRequired({
  ...approvedTraceState,
  slices: [{ ...approvedTraceState.slices[0], status: 'in_progress' }],
}, systemTestcase, systemFeature), true);
assert.equal(testcaseCodeRequired({
  ...approvedTraceState,
  slices: [{ ...approvedTraceState.slices[0], status: 'done' }],
}, systemTestcase, systemFeature), true);
assert.equal(testcaseCodeRequired({
  ...approvedTraceState,
  slices: [
    { id: 'SLICE-901', features: ['FEAT-901'], status: 'done' },
    { id: 'SLICE-902', features: ['FEAT-901'], status: 'planned' },
  ],
}, { id: 'TC-9012', feature: 'FEAT-901' }, systemFeature), false);

const post = await runFileChain({
  ...input,
  hook_event_name: 'PostToolUse',
  tool_input: { command: '*** Begin Patch\n*** Update File: README.md\n*** End Patch' },
});
assert.equal(post.some((x) => String(x?.systemMessage ?? '').includes('실행 실패')), false);

const shell = await runHookChain({
  cwd: REPO,
  hook_event_name: 'PreToolUse',
  tool_name: 'Bash',
  tool_input: { command: 'DROP TABLE customer' },
}, ['guard-bash.mjs', 'guard-commit.mjs']);
assert.equal(shell.some((x) => x?.hookSpecificOutput?.permissionDecision === 'deny'), true);

/* 커밋 본문은 POSIX heredoc과 PowerShell here-string을 모두 읽고 provider의 정확한 마지막
   trailer를 요구한다. Codex에서 실제로 쓰는 PowerShell 쪽을 빼면 표식 없이 통과한다. */
const preShell = (command) => runHookChain({
  cwd: REPO,
  hook_event_name: 'PreToolUse',
  tool_name: 'Bash',
  tool_input: { command },
}, ['guard-commit.mjs']);
const SUBJECT = 'fix: 훅이 아무것도 막지 않았다 — 본문이 있는 자리에서 본다';
const posixCommit = (body) => `git commit -F - -- AGENTS.md <<'EOF'\n${body}\nEOF`;
const powershellCommit = (body) => `$message = @'\n${body}\n'@\n$message | git commit -F - -- AGENTS.md`;
const isDenied = (outputs) => outputs.some((x) => x?.hookSpecificOutput?.permissionDecision === 'deny');

assert.equal(isDenied(await preShell(posixCommit(`${SUBJECT}\n\nAgent: Codex`))), false);
assert.equal(isDenied(await preShell(posixCommit(SUBJECT))), true);
assert.equal(isDenied(await preShell(powershellCommit(SUBJECT))), true);
assert.equal(isDenied(await preShell(posixCommit(`${SUBJECT}\n\nAgent: Potato`))), true);
assert.equal(isDenied(await preShell(posixCommit(`${SUBJECT}\n\nAgent: Codex\ntrailing body`))), true);
assert.equal(isDenied(await preShell(`git commit -m '${SUBJECT}' -- AGENTS.md`)), true);
assert.equal(isDenied(await preShell('git pull origin main')), true);
assert.equal(isDenied(await preShell('git pull --ff-only origin main')), false);
assert.equal(isDenied(await preShell('git merge origin/main')), true);
assert.equal(isDenied(await preShell('git merge --continue')), true);
assert.equal(isDenied(await preShell('git merge --squash --no-commit origin/main')), true);
assert.equal(isDenied(await preShell('git merge --no-commit origin/main')), false);
assert.equal(isDenied(await preShell('git merge --ff-only origin/main')), false);

const savedProvider = process.env.AI_HOOK_PROVIDER;
process.env.AI_HOOK_PROVIDER = 'claude';
assert.equal(isDenied(await preShell(posixCommit(
  `${SUBJECT}\n\nAgent: Claude\nCo-Authored-By: Claude Opus 5 <noreply@anthropic.com>`,
))), false);
if (savedProvider === undefined) delete process.env.AI_HOOK_PROVIDER;
else process.env.AI_HOOK_PROVIDER = savedProvider;
/* `git-sync` 는 작업 중간(`compact`)에 아무것도 하지 않는다 — 여기서 원격을 당기면 AI 가
   방금 읽은 파일과 디스크가 어긋난다. **당기는 쪽은 시험하지 않는다**: 시험이 네트워크를
   타고 CI 의 트리를 실제로 바꾸게 된다. */
const midSession = await runHookChain(
  { cwd: REPO, hook_event_name: 'SessionStart', source: 'compact' },
  ['git-sync.mjs'],
);
assert.deepEqual(midSession, []);

/* 공통 스킬 원본을 고쳐도 chain 이 깨지지 않는다 — 생성은 `skill-sync` 가 맡는다. */
const skillSrc = await runFileChain({
  ...input,
  hook_event_name: 'PostToolUse',
  tool_input: {
    command: '*** Begin Patch\n*** Update File: aidd-guide/harness/skill-src/adr.md\n*** End Patch',
  },
});
assert.equal(skillSrc.some((x) => String(x?.systemMessage ?? '').includes('실행 실패')), false);

/* provider 판정의 전제 — 이 파일 자체가 `.codex/hooks/` 에서 돌므로 codex 로 보여야 한다.
   틀리면 커밋 표식과 대화 기록이 **조용히 남의 이름으로 남는다.** */
assert.equal(provider(), 'codex');
assert.equal(skillRef('trace'), '`$trace`');

const hookConfig = JSON.parse(readFileSync(join(REPO, '.codex', 'hooks.json'), 'utf8'));
const startupGroup = hookConfig.hooks.SessionStart.find((group) => group.matcher === 'startup');
const resumeGroup = hookConfig.hooks.SessionStart.find((group) => group.matcher === 'resume|clear|compact');
assert.equal(startupGroup?.hooks?.[0]?.command.includes('session-start.mjs'), true);
assert.equal(resumeGroup?.hooks?.[0]?.command.includes('session-brief.mjs'), true);
assert.equal(startupGroup?.hooks?.[0]?.statusMessage, 'Codex 훅 승인 상태 확인');
assert.equal(resumeGroup?.hooks?.[0]?.statusMessage, 'Codex 훅 승인 상태 확인');
assert.equal(JSON.stringify(hookConfig.hooks).includes('approval-'), false);
assert.match(SESSION_NOTICE, /`\/hooks`에서 승인 여부를 반드시 확인하세요/);
assert.match(SESSION_NOTICE, /기존 Windows 앱 세션에는 적용되지 않으므로 새 세션 창을 여세요/);
assert.equal(SESSION_NOTICE_CONTEXT.startsWith(SESSION_NOTICE), true);
assert.match(SESSION_NOTICE_CONTEXT, /final_answer/);
assert.match(SESSION_NOTICE_CONTEXT, /commentary/);
assert.match(SESSION_NOTICE_CONTEXT, /충족한 것으로 보지/);
assert.match(SESSION_NOTICE_CONTEXT, /매 요청에 적용하는 지시가 아닙니다/);
assert.match(SESSION_NOTICE_CONTEXT, /이후 응답에서는 절대 반복하지 마세요/);
const agentsContract = readFileSync(join(REPO, 'AGENTS.md'), 'utf8');
assert.match(agentsContract, /이후 응답에서는 절대 반복하지 않는다/);

/* 수리 경로 — 훅이 죽었을 때 **훅 파일만** 열리고 나머지는 닫혀 있어야 한다.
   여는 쪽만 시험하면 게이트가 통째로 열린 것을 못 본다. 양쪽을 다 센다. */
const crashed = [{ crashed: 'guard-write.mjs', hookSpecificOutput: { hookEventName: 'PreToolUse', permissionDecision: 'deny', permissionDecisionReason: 'x' } }];
const asWrite = (rel) => ({ hook_event_name: 'PreToolUse', tool_input: { file_path: join(REPO, ...rel.split('/')) } });

assert.equal(allowHookRepair(asWrite('.claude/hooks/guard-write.mjs'), crashed)[0]?.hookSpecificOutput?.permissionDecision, undefined);
assert.equal(allowHookRepair(asWrite('.codex/hooks/file-chain.mjs'), crashed)[0]?.hookSpecificOutput?.permissionDecision, undefined);
assert.equal(allowHookRepair(asWrite('documents/INDEX.md'), crashed)[0]?.hookSpecificOutput?.permissionDecision, 'deny');
assert.equal(allowHookRepair(asWrite('.claude/skills/trace/SKILL.md'), crashed)[0]?.hookSpecificOutput?.permissionDecision, 'deny');

/* adapter나 lib import 전 오류는 bootstrap이 같은 범위만 열어야 한다. */
const adapterError = new SyntaxError('adapter parse failed');
assert.deepEqual(bootstrapTargets(asWrite('.codex/hooks/file-chain.mjs')), [join(REPO, '.codex', 'hooks', 'file-chain.mjs')]);
assert.equal(crashOutput(asWrite('.codex/hooks/file-chain.mjs'), 'file-chain.mjs', adapterError)?.hookSpecificOutput?.permissionDecision, undefined);
assert.equal(crashOutput(asWrite('documents/INDEX.md'), 'file-chain.mjs', adapterError)?.hookSpecificOutput?.permissionDecision, 'deny');
const mcpShellCrash = crashOutput(
  { hook_event_name: 'PreToolUse', tool_name: 'mcp__server__start_process', tool_input: { command: 'echo x' } },
  '.claude/hooks/pre-shell.mjs',
  adapterError,
);
assert.equal(mcpShellCrash?.hookSpecificOutput?.permissionDecision, 'deny');
assert.equal(mcpShellCrash?.hookSpecificOutput?.permissionDecisionReason.startsWith('셸 보호 진입부'), true);

/* 구문이 깨진 훅을 **그 자리에서** 잡는다. 깨진 파일을 실제로 만들어야 의미가 있으므로
   `finally` 로 반드시 지운다 — 시험이 저장소에 쓰레기를 남기면 다음 사람이 그것을 의심한다. */
const probe = join(REPO, '.claude', 'hooks', '_syntax-probe.mjs');
writeFileSync(probe, 'const x = `닫히지 않은 템플릿;\n', 'utf8');
try {
  const broken = await runHookChain(
    { hook_event_name: 'PostToolUse', tool_input: { file_path: probe } },
    ['hook-syntax.mjs'],
  );
  assert.match(String(broken[0]?.systemMessage ?? ''), /구문이 깨졌다|구문 검사를 실행하지 못했다/);
} finally {
  rmSync(probe, { force: true });
}

const healthy = await runHookChain(
  { hook_event_name: 'PostToolUse', tool_input: { file_path: join(REPO, '.claude', 'hooks', 'lib.mjs') } },
  ['hook-syntax.mjs'],
);
if (healthy.length) {
  assert.match(String(healthy[0]?.systemMessage ?? ''), /구문 검사를 실행하지 못했다/);
  assert.doesNotMatch(String(healthy[0]?.systemMessage ?? ''), /구문이 깨졌다/);
} else {
  assert.deepEqual(healthy, []);
}

/* 수리 경로는 chain 의 **실행 원본**까지 연다 — 그것이 깨지면 모든 chain 이 서지 못하는데
   열어 주지 않으면 잠긴 사람이 잠근 파일을 못 고친다. */
assert.equal(allowHookRepair(asWrite('aidd-guide/harness/runtime/chains.mjs'), crashed)[0]?.hookSpecificOutput?.permissionDecision, undefined);
assert.equal(allowHookRepair(asWrite('aidd-guide/harness/runtime/pretool-bootstrap.mjs'), crashed)[0]?.hookSpecificOutput?.permissionDecision, undefined);
assert.equal(allowHookRepair(asWrite('aidd-guide/harness/skill-src/adr.md'), crashed)[0]?.hookSpecificOutput?.permissionDecision, 'deny');

/* 정책 차단(`crashed` 없음)은 훅 파일이라도 절대 열리지 않는다. */
const policy = [{ hookSpecificOutput: { hookEventName: 'PreToolUse', permissionDecision: 'deny', permissionDecisionReason: 'x' } }];
assert.equal(allowHookRepair(asWrite('.claude/hooks/guard-write.mjs'), policy)[0]?.hookSpecificOutput?.permissionDecision, 'deny');

/* 커밋 제목의 형식과 언어 — **막히는 쪽을 함께 센다.** 통과만 확인하면 아무것도 안
   막는 검사가 초록으로 보인다(2026-09-11 에 실제로 그랬다). */
for (const [subject, shouldDeny] of [
  ['fix: 훅이 아무것도 막지 않았다 — 본문이 있는 자리에서 본다', false],
  ['feat: harness was out of sync — align both providers', true],
  ['fix: 대시가 없는 제목이다', true],
  ['wip: 모르는 타입이다 — 고친다', true],
  ['타입이 없다 — 고친다', true],
]) {
  const denied = isDenied(await preShell(posixCommit(`${subject}\n\nAgent: Codex`)));
  assert.equal(denied, shouldDeny, `커밋 제목 판정이 틀렸다: ${subject}`);
}

/*
  `SessionStart` 출력의 **모양** — 판정 함수만 시험하면 `checkCodexHookContext` 가
  `ok:true` 인데도 **실행 확인이 문맥에 닿지 못하는** 상태를 못 본다. 2026-09-11 에 실제로
  그랬다: 훅은 셸·`commandWindows`·`codex sandbox` 어디서 직접 돌려도 `exit 0` 이었고,
  Codex 만 `Failed` 로 잡아 하네스가 자기 자신을 잠갔다. **여기서 세는 것은 실제로 쓰인
  바이트다.**
*/
function captureEmit(outputs, eventName, options) {
  const chunks = [];
  const original = process.stdout.write.bind(process.stdout);
  process.stdout.write = (chunk) => (chunks.push(String(chunk)), true);
  try {
    emitHookResults(outputs, eventName, options);
  } finally {
    process.stdout.write = original;
  }
  return chunks.join('');
}

/* Codex 는 JSON 만 읽는다 — 날 것이면 `JSON.parse` 가 던지고, 그것이 이 시험의 요점이다. */
const codexStart = JSON.parse(captureEmit(
  [SESSION_NOTICE_CONTEXT, '[프로젝트 상태] …'],
  'SessionStart',
  { systemMessage: SESSION_NOTICE },
));
assert.equal(codexStart.systemMessage, SESSION_NOTICE);
assert.equal(codexStart.hookSpecificOutput.hookEventName, 'SessionStart');
assert.equal(codexStart.hookSpecificOutput.additionalContext.includes(SESSION_NOTICE), true);
assert.equal(codexStart.hookSpecificOutput.additionalContext.includes('final_answer'), true);
assert.equal(codexStart.hookSpecificOutput.additionalContext.includes('commentary'), true);
assert.equal(codexStart.hookSpecificOutput.additionalContext.includes('[프로젝트 상태] …'), true);

/* 공식 계약상 빈 출력도 성공이지만, 실측된 `PreToolUse Failed` 표시를 되살리지 않도록
   허용 경로도 결정 없는 JSON 하나를 낸다. `allow`를 쓰면 Codex가 지원하지 않는 결정이다. */
const codexPretoolPass = JSON.parse(captureEmit([], 'PreToolUse'));
assert.deepEqual(codexPretoolPass, {
  hookSpecificOutput: { hookEventName: 'PreToolUse' },
});
assert.equal(codexPretoolPass.hookSpecificOutput.permissionDecision, undefined);

/* Claude 는 반대다 — 여기까지 JSON 으로 바꾸면 문맥에 JSON 이 그대로 들어간다. */
process.env.AI_HOOK_PROVIDER = 'claude';
const claudeStart = captureEmit([SESSION_NOTICE], 'SessionStart');
if (savedProvider === undefined) delete process.env.AI_HOOK_PROVIDER;
else process.env.AI_HOOK_PROVIDER = savedProvider;
assert.equal(claudeStart.trim(), SESSION_NOTICE);
assert.throws(() => JSON.parse(claudeStart));

/*
  `guard-shell-write` — <b>셸로 쓰는 것도 파일 게이트를 지난다.</b>

  <p>여는 쪽만 세면 안 된다. 오탐은 사람이 훅을 끄게 만들고, 꺼진 훅은 없는 훅이다 —
  그래서 <b>읽기만 하는 명령과 인용부 안의 꺾쇠·파이프가 통과하는지</b>를 함께 센다.
*/
const targetsOf = (command) =>
  shellWriteTargets(command, REPO).map((path) => path.replace(/\\/g, '/').replace(`${REPO.replace(/\\/g, '/')}/`, ''));

assert.deepEqual(targetsOf('printf x > documents/canary.md'), ['documents/canary.md']);
assert.deepEqual(targetsOf('echo y >>AGENTS.md'), ['AGENTS.md']);
assert.deepEqual(targetsOf('cat a | tee -a documents/x.md'), ['documents/x.md']);
assert.deepEqual(targetsOf("sed -i 's/a/b/' AGENTS.md"), ['AGENTS.md']);
assert.deepEqual(targetsOf('Set-Content documents/x.md "hi"'), ['documents/x.md']);
assert.deepEqual(targetsOf('Copy-Item -Path AGENTS.md -Destination documents/x.md'), ['documents/x.md']);
assert.deepEqual(targetsOf('Copy-Item AGENTS.md documents/x.md'), ['documents/x.md']);
assert.deepEqual(targetsOf('Move-Item -LiteralPath AGENTS.md -Destination documents/x.md'), ['documents/x.md']);
assert.deepEqual(targetsOf('Copy-Item -Path AGENTS.md -Destination documents'), ['documents/AGENTS.md']);
assert.deepEqual(targetsOf('cp AGENTS.md documents'), ['documents/AGENTS.md']);
assert.deepEqual(targetsOf('cp -t documents AGENTS.md'), ['documents/AGENTS.md']);
assert.deepEqual(targetsOf('cp --target-directory=documents AGENTS.md'), ['documents/AGENTS.md']);
assert.deepEqual(targetsOf('cp -tdocuments AGENTS.md'), ['documents/AGENTS.md']);
assert.deepEqual(targetsOf('echo x >| documents/x.md'), ['documents/x.md']);
assert.deepEqual(targetsOf('exec 3<> documents/x.md'), ['documents/x.md']);
assert.deepEqual(targetsOf('cat <<EOF > documents/x.md\n> 본문의 꺾쇠\nEOF'), ['documents/x.md']);
/* 읽기·저장소 밖·인용부 안·`/dev/null` 은 건드리지 않는다. */
assert.deepEqual(targetsOf("sed -n '1,5p' AGENTS.md"), []);
assert.deepEqual(targetsOf('grep -n "a > documents/no.md" AGENTS.md'), []);
assert.deepEqual(targetsOf('echo "cat a | tee documents/no.md"'), []);
assert.deepEqual(targetsOf('node x.mjs 2>&1 > /dev/null'), []);
assert.deepEqual(targetsOf('node x.mjs 2>$null'), []);
assert.deepEqual(targetsOf('printf x > /tmp/scratch.txt'), []);
/* 정직한 커밋 메시지의 꺾쇠를 리다이렉션으로 읽지 않는다 — `guard-bash` 와 같은 이유다. */
assert.deepEqual(targetsOf(posixCommit('fix: a > b')), []);

/* chain 끝까지 — 지도 밖 경로는 막히고, 읽기는 지나간다. 여기서 `pass()` 가 프로세스를
   끝내지 않는 것 자체가 `runEmbeddedHook` 중첩(바깥 훅 안에서 정책 엔진을 다시 부르는
   자리)이 살아 있다는 증거다. */
const shellChain = (command) =>
  runHookChain({ cwd: REPO, hook_event_name: 'PreToolUse', tool_name: 'Bash', tool_input: { command } }, PRE_SHELL);
assert.equal(isDenied(await shellChain('printf x > documents/지도밖.md')), true);
assert.equal(isDenied(await shellChain('Copy-Item AGENTS.md documents/지도밖.md')), true);
assert.equal(isDenied(await shellChain('Copy-Item AGENTS.md documents')), true);
assert.equal(isDenied(await shellChain('echo x >| documents/지도밖.md')), true);
assert.equal(isDenied(await shellChain('cp -t documents AGENTS.md')), true);
assert.equal(isDenied(await shellChain('cat AGENTS.md | head -5')), false);

/* 어댑터는 emitter 밖에서 stdout 에 쓰지 않는다 — **두 곳에서 쓰면 형태가 섞이고**,
   섞인 출력은 파싱되지 않아 훅 전체가 실패로 잡힌다. 그것이 위 버그의 실제 모양이었다. */
for (const adapter of ['session-start.mjs', 'session-brief.mjs']) {
  const source = readFileSync(join(REPO, '.codex', 'hooks', adapter), 'utf8');
  assert.equal(
    /process\.stdout\.write/.test(source),
    false,
    `${adapter} 가 emitter 밖에서 stdout 에 쓴다 — outputs 배열에 넣어 emitHookResults 로 보낸다`,
  );
}

/* **따옴표 안은 데이터다 — 여는 쪽과 닫히는 쪽을 함께 센다.**
   2026-09-14 에 `guard-bash` 가 `rg` 의 검색어를, `guard-commit` 이 `node -e` 안의 문자열을
   명령으로 읽어 정당한 작업을 막았다. 오탐은 사람이 훅을 끄게 만들고, 꺼진 훅은 없는 훅이다.
   그렇다고 따옴표를 통째로 무시하면 `psql -c "…"` 로 가드가 열리므로 둘을 같이 시험한다. */
{
  const sql = `${'DR'}${'OP DATABA'}SE bo`;
  const git = `${'g'}it`;

  /* 읽기 도구의 인자는 가린다 — 검색이 막히지 않는다. */
  assert.equal(maskReadOnlyArguments(`rg -n "${sql}" docs/`).includes(sql), false);
  assert.equal(maskReadOnlyArguments(`grep -rn '${sql}' AGENTS.md`).includes(sql), false);

  /* 실행 도구의 인자는 그대로 둔다 — 여기가 열리면 가드가 통째로 무력해진다. */
  assert.equal(maskReadOnlyArguments(`psql -c "${sql}"`).includes(sql), true);
  assert.equal(maskReadOnlyArguments(`echo "${sql}" | psql -d bo`).includes(sql), true);
  assert.equal(maskReadOnlyArguments(`rg -n "${sql}" f && psql -c "${sql}"`).includes(sql), true);

  /* 따옴표 <b>밖</b>은 한 글자도 바뀌지 않는다 — 리다이렉션 판정이 살아 있어야 한다. */
  assert.equal(maskReadOnlyArguments('cat x > /dev/sda'), 'cat x > /dev/sda');

  /* 인용부 안의 줄바꿈이 구획을 끊지 않는다 — 없는 커밋 명령이 생기지 않는다. */
  assert.equal(maskQuoted(`node -e "a\n${git} commit -F - -- a"`).includes('\n'), false);
  /* 진짜 커밋은 한 글자도 안 바뀐다 — 규약 검사가 그대로 돌아야 한다. */
  const realCommit = `${git} add a && ${git} commit -F - -- a b`;
  assert.equal(maskQuoted(realCommit), realCommit);

  /* 닫히지 않은 따옴표는 임의로 고쳐 읽지 않는다. */
  const unclosed = 'rg -n "안 닫힘';
  assert.equal(maskQuoted(unclosed), unclosed);
}

console.log('Codex hook adapter self-test 통과');
