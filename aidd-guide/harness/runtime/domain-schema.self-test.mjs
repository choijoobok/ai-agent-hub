import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, writeFileSync, rmSync, readFileSync } from 'node:fs';
import { resolve, join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseYaml } from '../../../.claude/hooks/lib.mjs';
import { readLiquibase, applySql, physicalContract, schemaDifferences, splitSql, entityDesignIssues } from './liquibase-schema.mjs';
import { domainSchema, recoveryMessage } from './domain-schema.mjs';
import { createTraceModel, sourceSeeds, affectedIds } from './trace-model.mjs';
import { renderTableDocuments } from '../../../.claude/skills/doc-gen/gen.mjs';

const repo = resolve(fileURLToPath(new URL('../../..', import.meta.url)));
const root = resolve(repo, 'build/schema-fixtures');
mkdirSync(root, { recursive: true });
const fixture = mkdtempSync(join(root, 'case-'));
try {
  const sql = `--liquibase formatted sql
--changeset test:one
CREATE TABLE parent (a bigint NOT NULL, b bigint NOT NULL);
ALTER TABLE parent ADD CONSTRAINT pk_parent PRIMARY KEY (a, b);
CREATE TABLE child (id bigint NOT NULL, pa bigint, pb bigint, amount numeric(18,4) DEFAULT 1.25, note character varying(20) DEFAULT 'a;b,c');
ALTER TABLE child ADD CONSTRAINT pk_child PRIMARY KEY (id);
ALTER TABLE child ADD CONSTRAINT fk_child FOREIGN KEY (pa, pb) REFERENCES parent(a, b) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED;
CREATE UNIQUE INDEX ix_child ON child USING btree (pa, pb);
INSERT INTO child (note) VALUES ('ALTER TABLE child; DROP TABLE parent;');
`;
  writeFileSync(join(fixture, 'master.yaml'), 'databaseChangeLog:\n  - include:\n      file: base.sql\n      relativeToChangelogFile: true\n');
  writeFileSync(join(fixture, 'base.sql'), sql);
  const config = { root: 'master.yaml', resources: '.' };
  const read = () => readLiquibase({ repo: fixture, config, parseYaml });
  const c = read(), child = c.tables.get('CHILD');
  assert.equal(c.tables.size, 2);
  assert.deepEqual(child.attributes[3], { name: 'AMOUNT', type: 'numeric(18,4)', nullable: true, default: '1.25' });
  assert.deepEqual(child.attributes[4], { name: 'NOTE', type: 'character varying(20)', nullable: true, default: "'a;b,c'" });
  assert.equal(child.constraints[1].definition, 'FOREIGN KEY (pa, pb) REFERENCES parent(a, b) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED');
  assert.equal(child.indexes[0].unique, true);
  assert.deepEqual(child.changesets, [{ file: 'base.sql', author: 'test', id: 'one' }]);
  assert.equal(splitSql("SELECT 'it''s;one'; -- comment ;\n SELECT 2;").length, 2);
  const entities = [...c.tables.values()].map((t, i) => ({ id: `ENT-${i + 1}`, module: 'test', table: t.name, implementation: 'implemented', sources: t.sources, physical: structuredClone(physicalContract(t)) }));
  assert.deepEqual(schemaDifferences(entities, c), []);
  const bad = (mutate) => { const changed = structuredClone(entities); mutate(changed[1], changed); assert.ok(schemaDifferences(changed, c).length); };
  bad((e) => e.physical.attributes.pop());
  bad((e) => e.physical.attributes[3].type = 'numeric(18,2)');
  bad((e) => e.physical.attributes[3].nullable = false);
  bad((e) => e.physical.attributes[3].default = '2.50');
  bad((e, all) => all[0].physical.constraints[0].definition = 'PRIMARY KEY (b, a)');
  bad((e) => e.physical.constraints[1].definition = e.physical.constraints[1].definition.replace(' ON DELETE CASCADE', ''));
  bad((e) => e.physical.constraints[1].definition = e.physical.constraints[1].definition.replace(' DEFERRABLE INITIALLY DEFERRED', ''));
  bad((e) => e.physical.indexes[0].unique = false);
  bad((e) => e.sources = ['base.sql']);
  bad((e) => e.implementation = 'planned');
  bad((e, all) => all.push({ ...e, id: 'ENT-3' }));
  assert.ok(schemaDifferences(entities.slice(0, 1), c).some((v) => v.includes('분류 누락')));
  assert.ok(schemaDifferences(entities, { tables: new Map() }).length);
  const future = { id: 'ENT-9', module: 'test', table: 'FUTURE', implementation: 'planned' };
  assert.deepEqual(schemaDifferences([...entities, future], c), []);
  const logical = { id: 'ENT-10', name: '논리 업무 항목', module: 'test', implementation: 'planned', design_stage: 'logical', attributes: [{ name: '업무 항목 식별자' }] };
  assert.deepEqual(schemaDifferences([...entities, logical, { ...logical, id: 'ENT-11' }], c), []);
  assert.ok(entityDesignIssues(logical).length);
  const projection = { ...logical, id: 'ENT-12', storage_kind: 'nonpersistent', nonpersistent_reason: '현재 계약에서 파생하며 독립 저장/DDL을 갖지 않는다.' };
  assert.deepEqual(entityDesignIssues(projection), []);
  assert.deepEqual(schemaDifferences([...entities, projection], c), []);
  assert.deepEqual(schemaDifferences([...entities, { ...projection, implementation: 'implemented' }], c), []);
  for (const patch of [{ nonpersistent_reason: '' }, { storage_kind: 'unknown' }, { table: 'CHILD' }, { physical: {} }, { columns: [] }, { history_storage: {} }, { attributes: [{ name: '식별자', physical_name: 'ID' }] }]) {
    assert.ok(entityDesignIssues({ ...projection, ...patch }).length, JSON.stringify(patch));
    assert.ok(schemaDifferences([...entities, { ...projection, ...patch }], c).length, JSON.stringify(patch));
  }
  const projectionDocs = renderTableDocuments({ domain: { entities: [projection], conventions: { liquibase: { technical_tables: [] } } }, catalog: { tables: new Map() } });
  const projectionDoc = projectionDocs.get('documents/generated/41-테이블명세서/ENT-12.md');
  assert.ok(projectionDoc.includes('비저장 논리 투영 · 테이블 없음'));
  assert.ok(projectionDoc.includes(projection.nonpersistent_reason));
  assert.ok(!projectionDoc.includes('물리명 미정'));
  assert.ok(!projectionDoc.includes('아직 설계하지 않았다'));
  assert.ok(entityDesignIssues({ ...future, design_stage: 'physical', attributes: [{ name: 'ID', pk: true }] }).some((s) => s.includes('physical')));
  const draftDocs = renderTableDocuments({ domain: { entities: [logical], conventions: { liquibase: { technical_tables: [] } } }, catalog: { tables: new Map() } });
  const draft = draftDocs.get('documents/generated/41-테이블명세서/ENT-10.md');
  for (const expected of ['논리 설계 · 물리명 미정', '논리 업무 항목', '업무 항목 식별자']) assert.ok(draft.includes(expected));
  assert.ok(!draft.includes('undefined'));
  assert.ok(!draft.includes('소스 기준 물리 정의'));
  assert.ok(schemaDifferences([...entities, { ...logical, implementation: 'implemented' }], c).length);
  assert.ok(schemaDifferences([...entities, { ...logical, table: 'TEMP_NAME' }], c).length);
  assert.ok(schemaDifferences([...entities, { ...logical, physical: {} }], c).length);
  const mapped = structuredClone(entities);
  mapped[1].attributes = [{ name: '업무 식별자', physical_name: 'ID', pk: true }];
  assert.deepEqual(schemaDifferences(mapped, c), []);
  assert.deepEqual(entityDesignIssues(mapped[1]), []);
  delete mapped[1].attributes[0].physical_name;
  assert.ok(schemaDifferences(mapped, c).length);
  assert.ok(entityDesignIssues(mapped[1]).some((s) => s.includes('연결 누락')));
  const message = recoveryMessage(['ENT-1의 타입 불일치'], { entityFiles: new Map([['ENT-1', 'documents/spec/40-domain/002-entities.yaml']]), entities, root: 'master.yaml' });
  for (const expected of ['002-entities.yaml', 'base.sql', '후속 조치', 'AI가', '사용자 결정', '다시 실행', '기존 HTML']) assert.ok(message.includes(expected), expected);
  assert.throws(() => applySql(new Map(), 'DROP TABLE child;', 'unsupported.sql'), /지원하지 않는 SQL/);
  assert.throws(() => applySql(new Map(), 'DO $$ BEGIN END $$;', 'unsupported.sql'), /dollar/);
  for (const definition of ['id bigint DEFAULT 1 UNIQUE', 'id bigint DEFAULT 1 CHECK (id > 0)', 'id bigint DEFAULT 1 REFERENCES parent(a)']) {
    assert.throws(() => applySql(new Map(), `CREATE TABLE x (${definition});`, 'inline.sql'), /지원하지 않는 컬럼/);
  }
  assert.throws(() => applySql(new Map(), 'CREATE TABLE x (id bigint); ALTER TABLE x ALTER COLUMN id TYPE bigint, ADD COLUMN extra text;', 'multi.sql'), /복수 ALTER/);
  for (const action of ['ADD COLUMN y integer DEFAULT 0, ADD COLUMN extra text', 'ADD CONSTRAINT pk_x PRIMARY KEY(id), ADD COLUMN extra text']) {
    assert.throws(() => applySql(new Map(), `CREATE TABLE x (id bigint); ALTER TABLE x ${action};`, 'multi.sql'), /복수 ALTER/);
  }
  assert.throws(() => applySql(new Map(), 'SET search_path TO archive; CREATE TABLE x (id bigint);', 'namespace.sql'), /지원하지 않는 SQL/);
  const deferred = new Map();
  applySql(deferred, 'CREATE TABLE x (id bigint); ALTER TABLE x ADD CONSTRAINT pk_x PRIMARY KEY (id) DEFERRABLE INITIALLY DEFERRED;', 'deferred.sql');
  assert.equal(deferred.get('X').attributes[0].nullable, false);
  applySql(deferred, "COMMENT ON TABLE x IS '업무 테이블'; COMMENT ON COLUMN x.id IS '식별자';", 'comments.sql');
  assert.equal(physicalContract(deferred.get('X')).comment, '업무 테이블');
  assert.equal(deferred.get('X').attributes[0].comment, '식별자');
  const invalid = { ...future, physical: { attributes: [{ name: 'ID', type: 'bigint', nullable: false, default: null }], constraints: [{ name: 'PK_X' }], indexes: [] } };
  assert.ok(schemaDifferences([...entities, invalid], c).some((s) => s.includes('definition')));
  // 새 changeset/include가 누적 계약을 바꾼다. rollback은 합산하지 않는다.
  writeFileSync(join(fixture, 'next.yaml'), "databaseChangeLog:\n  - changeSet:\n      id: two\n      author: test\n      changes:\n        - sql: 'ALTER TABLE child ADD COLUMN added bigint; ALTER TABLE child ALTER COLUMN amount SET DEFAULT 2.5;'\n      rollback:\n        - sql: 'DROP TABLE child;'\n");
  writeFileSync(join(fixture, 'master.yaml'), readFileSync(join(fixture, 'master.yaml'), 'utf8') + '  - include:\n      file: next.yaml\n      relativeToChangelogFile: true\n');
  const next = read();
  assert.equal(next.tables.get('CHILD').attributes.length, 6);
  assert.equal(next.tables.get('CHILD').attributes[3].default, '2.5');
  assert.equal(next.tables.get('CHILD').changesets[1].id, 'two');
  assert.ok(schemaDifferences(entities, next).length);
  writeFileSync(join(fixture, 'next.yaml'), 'databaseChangeLog:\n  - changeSet:\n      id: three\n      author: test\n      changes:\n        - createTable:\n            tableName: unknown\n');
  assert.throws(read, /지원하지 않는 Liquibase 변경/);
  writeFileSync(join(fixture, 'next.yaml'), 'databaseChangeLog:\n  - include:\n      file: master.yaml\n      relativeToChangelogFile: true\n');
  assert.throws(read, /순환\/중복/);
  writeFileSync(join(fixture, 'next.yaml'), 'databaseChangeLog:\n  - include:\n      file: missing.sql\n      relativeToChangelogFile: true\n');
  assert.throws(read, /changelog 없음/);
  writeFileSync(join(fixture, 'next.yaml'), 'databaseChangeLog:\n  - changeSet:\n      id: conditional\n      author: test\n      context: local\n      changes: []\n');
  assert.throws(read, /조건부/);
  // 프로젝트의 실제 매핑도 검사하되 테이블 수/이름은 공통 검증기에 하드코딩하지 않는다.
  const current = domainSchema(repo);
  assert.ok(current.catalog.tables.size > 0);
  const example = { id: 'ENT-001', sources: ['sources/schema.sql'] };
  const model = createTraceModel({ entities: [example], features: [{ id: 'FEAT-001', entities: ['ENT-001'] }] });
  const seeded = sourceSeeds(model, 'sources/schema.sql');
  assert.ok(seeded.includes('ENT-001'));
  assert.ok(affectedIds(model, seeded).includes('FEAT-001'));
  console.log('데이터 모델 회귀 통과: 물리 변경 12개 반례·include/미지원/조건부/rollback·실제 스키마·변경 영향 연결');
} finally {
  assert.equal(dirname(fixture), root); // 재귀 정리 대상은 방금 생성한 fixture 디렉터리 안으로 제한한다.
  rmSync(fixture, { recursive: true, force: true });
}
