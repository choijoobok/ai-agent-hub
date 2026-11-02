import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
import { runEmbeddedHook, REPO } from '../../../.claude/hooks/lib.mjs';
import { registryScreenUrls } from '../../../.claude/hooks/doc-drift.mjs';
import { createTraceModel, affectedIds, sourceSeeds, sourceMatches, sourceRef, featureIds, testsByFeature, associatedScreens, relationErrors, linkedDocuments } from './trace-model.mjs';

export function testTraceModel() {
  const groups = {
    requirements: [{ id: 'REQ-001' }],
    features: [
      { id: 'FEAT-001', reqs: ['REQ-001'], sources: ['sources/backend/Shared.java#run'] },
      { id: 'FEAT-002', reqs: ['REQ-001'], uses_features: ['FEAT-001'], screen: 'SCR-001', screens: ['SCR-002', 'SCR-001'] },
      { id: 'FEAT-003', reqs: ['REQ-001'] }, // 같은 요구사항만 공유하면 소비자가 아니다.
      { id: 'FEAT-005', reqs: ['REQ-001'] },
      { id: 'FEAT-004', type: 'batch', uses_features: ['FEAT-001'], sources: ['sources/backend/Job.java'] },
    ],
    screens: [
      { id: 'SCR-004', feature: 'FEAT-005', uses_screens: ['SCR-003'], route: '/admin/consumer' },
      { id: 'SCR-001', feature: 'FEAT-002', uses_screens: ['SCR-003'], route: '/admin/one' },
      { id: 'SCR-002', feature: 'FEAT-002', features: ['FEAT-001'], uses_screens: ['SCR-003'], route: '/admin/two' },
      { id: 'SCR-003', feature: 'FEAT-001', sources: ['sources/frontend/shared/picker/'], current_doc: 'documents/screen/_standalone/picker/requirements.md' },
    ],
    apis: [{ id: 'API-001', feature: 'FEAT-001', source_contract: 'sources/backend/Shared.java#run' }],
    testcases: [{ id: 'TC-001', feature: 'FEAT-004' }],
  };
  const model = createTraceModel(groups);
  assert.deepEqual(featureIds({ feature: 'FEAT-001', features: ['FEAT-002', 'FEAT-001'] }), ['FEAT-001', 'FEAT-002']);
  assert.deepEqual(associatedScreens(['FEAT-002'], groups.features, groups.screens), ['SCR-001', 'SCR-002', 'SCR-003']);
  const affected = affectedIds(model, ['FEAT-001']);
  for (const id of ['FEAT-002', 'FEAT-004', 'SCR-001', 'SCR-002', 'SCR-003', 'API-001', 'TC-001', 'SCR-004', 'FEAT-005']) assert.ok(affected.includes(id), id);
  assert.ok(!affected.includes('FEAT-003'), '같은 요구사항의 무관한 형제 기능으로 전파하면 안 된다');
  assert.ok(!affected.includes('REQ-001'), '근거 문맥은 역방향 전파의 입력이 아니다');
  assert.deepEqual(sourceSeeds(model, 'sources\\backend\\Shared.java'), ['API-001', 'FEAT-001']);
  assert.deepEqual(sourceSeeds(model, 'sources/backend/Shared.java#other'), []);
  assert.deepEqual(sourceSeeds(model, 'sources/backend/New.java', '// @feat FEAT-004'), ['FEAT-004']);
  assert.equal(sourceMatches('sources/frontend/shared/picker/', 'sources/frontend/shared/picker/Removed.tsx'), true);
  assert.equal(sourceMatches('sources/frontend/shared/picker/', 'sources/frontend/shared/picker-other/A.tsx'), false);
  assert.equal(sourceRef('../outside.java'), null);
  assert.equal(sourceRef('D:/outside.java'), null);
  assert.ok(linkedDocuments(model, affected).includes('documents/screen/_standalone/picker/requirements.md'));
  assert.ok(linkedDocuments(model, affected).includes('documents/screen/admin/two/manual.md'));
  assert.deepEqual(relationErrors(model), []);
  assert.ok(relationErrors(createTraceModel({ features: [{ id: 'FEAT-001', uses_features: ['SCR-001', 'FEAT-001'] }] })).length === 2);
  const cycle = createTraceModel({ features: [{ id: 'FEAT-001', uses_features: ['FEAT-002'] }, { id: 'FEAT-002', uses_features: ['FEAT-001'] }] });
  assert.deepEqual(affectedIds(cycle, ['FEAT-001']), ['FEAT-001', 'FEAT-002']);
  assert.deepEqual(associatedScreens(['FEAT-004'], groups.features, groups.screens), [], '화면 없는 배치에 가짜 화면을 요구하지 않는다');
  assert.ok(affectedIds(model, ['API-001']).includes('FEAT-001'));
  const entries = [{ url: '/one', sourceDir: 'modules/a/features/one' }, { url: '/two', sourceDir: 'modules/a/features/two' }, { url: '/three', sourceDir: 'modules/a/features/three' }];
  assert.deepEqual(registryScreenUrls('sources/backend/FooController.java', '@RequestMapping("/api/service/foo")', entries, (dir) => dir.endsWith('three') ? '' : "'/service/foo/getList'"), ['/one', '/two']);
  assert.deepEqual(registryScreenUrls('sources/frontend/src/modules/a/features/one/Page.tsx', '', entries, () => ''), ['/one']);
  const owner = { features: [{ id: 'FEAT-001' }], screens: [{ id: 'SCR-001', feature: 'SCR-002' }, { id: 'SCR-002', feature: 'FEAT-001' }] };
  assert.ok(relationErrors(createTraceModel(owner)).some(([id]) => id === 'SCR-001'));
  for (const bad of [{ uses_features: [123] }, { sources: { file: 'sources/backend/F.java' } }, { docs: [null] }]) {
    assert.ok(relationErrors(createTraceModel({ features: [{ id: 'FEAT-001', ...bad }] })).length > 0);
  }
  const sharedTest = { id: 'TC-002', feature: 'FEAT-001', features: ['FEAT-002'] };
  assert.deepEqual(testsByFeature([sharedTest]).get('FEAT-002'), [sharedTest]);
  const nfr = createTraceModel({ nfrs: [{ id: 'NFR-001', test_case: 'TC-002' }], features: [{ id: 'FEAT-001' }, { id: 'FEAT-002' }], testcases: [sharedTest] });
  assert.ok(affectedIds(nfr, ['NFR-001']).includes('TC-002'));
  const batch = createTraceModel({
    features: [{ id: 'FEAT-001' }], usecases: [{ id: 'UC-001' }],
    jobs: [{ id: 'JOB-001', feature: 'FEAT-001', usecase: 'UC-001', screens: ['SCR-001'], testcases: ['TC-001'], sources: ['sources/backend/Job.java'] }],
    screens: [{ id: 'SCR-001', feature: 'FEAT-001' }], testcases: [{ id: 'TC-001', feature: 'FEAT-001', jobs: ['JOB-001'] }],
  });
  assert.deepEqual(sourceSeeds(batch, 'sources/backend/Job.java'), ['JOB-001']);
  for (const id of ['JOB-001','FEAT-001','SCR-001','TC-001']) assert.ok(affectedIds(batch, ['UC-001']).includes(id));
  assert.ok(affectedIds(batch, ['JOB-001']).includes('FEAT-001'));
}

/** 순수 함수 import와 chain 실행을 구분한다. */
export async function testHookEntry() {
  for (const script of ['trace-check.mjs', 'doc-drift.mjs']) {
    let reads = 0;
    const input = {
      get tool_input() { reads++; return { file_path: `${REPO}/ignored.txt` }; },
    };
    await runEmbeddedHook(`${REPO}/.claude/hooks/${script}`, input);
    assert.ok(reads > 0, `${script}: chain에서 호출한 실행부가 입력을 읽어야 한다`);
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  testTraceModel();
  await testHookEntry();
  console.log('추적 관계 회귀 통과 — 다대다·공통 소비자·삭제 경로·배치·순환·잘못된 참조·chain 실행');
}
