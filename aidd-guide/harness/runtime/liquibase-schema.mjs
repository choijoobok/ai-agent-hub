/** 활성 Liquibase include 순서의 PostgreSQL 물리 정의를 읽는다. DB 접속/적용은 하지 않는다. */
import { readFileSync, existsSync } from 'node:fs';
import { resolve, relative, dirname } from 'node:path';

const ident = '[a-zA-Z_][a-zA-Z0-9_]*';
const upper = (v) => String(v).toUpperCase();
const fail = (source, message) => { throw new Error(`${source}: ${message}`); };
const plain = (sql) => sql.replace(/'(?:[^']|'')*'/gs, "''");
const typePattern = /^(?:bigint|integer|smallint|boolean|text|bytea|uuid|jsonb?|date|timestamptz|(?:character varying|varchar|character|char)\s*\(\d+\)|(?:numeric|decimal)(?:\(\d+(?:,\s*\d+)?\))?|(?:timestamp|time)(?:\(\d+\))?(?: (?:with|without) time zone)?|double precision|real)$/i;
const inlineConstraint = /\b(CONSTRAINT|PRIMARY|REFERENCES|GENERATED|COLLATE|NOT NULL|CHECK|UNIQUE)\b/i;

/** 문자열의 세미콜론/쉼표와 주석을 DDL 경계로 오인하지 않는다. */
export function splitSql(text, delimiter = ';') {
  const out = []; let buf = '', quote = '', depth = 0;
  for (let i = 0; i < text.length; i++) {
    const c = text[i], next = text[i + 1];
    if (quote) {
      buf += c;
      if (c === quote) { if (next === quote) buf += text[++i]; else quote = ''; }
      continue;
    }
    if (c === "'" || c === '"') { quote = c; buf += c; continue; }
    if (c === '-' && next === '-') { while (i < text.length && text[i] !== '\n') i++; buf += ' '; continue; }
    if (c === '/' && next === '*') {
      const end = text.indexOf('*/', i + 2);
      if (end < 0) throw new Error('닫히지 않은 SQL 주석');
      i = end + 1; buf += ' '; continue;
    }
    if (c === '$') throw new Error('지원하지 않는 SQL dollar 구문 — 파서를 보완한 뒤 생성한다');
    if (c === '(') depth++;
    if (c === ')') depth--;
    if (depth < 0) throw new Error('SQL 괄호 불일치');
    if (c === delimiter && depth === 0) { if (buf.trim()) out.push(buf.trim()); buf = ''; }
    else buf += c;
  }
  if (quote || depth) throw new Error('닫히지 않은 SQL 문자열/괄호');
  if (buf.trim()) out.push(buf.trim());
  return out;
}

function column(def, source) {
  const m = def.trim().match(new RegExp(`^(${ident})\\s+(.+)$`, 'is'));
  if (!m || /^(CONSTRAINT|PRIMARY|FOREIGN|UNIQUE|CHECK)$/i.test(m[1])) fail(source, '지원하지 않는 컬럼 정의');
  let rest = m[2].trim(), nullable = true;
  if (/\s+NOT NULL$/i.test(rest)) { nullable = false; rest = rest.replace(/\s+NOT NULL$/i, ''); }
  const parts = rest.split(/\s+DEFAULT\s+/i);
  if (parts.length > 2 || !typePattern.test(parts[0].trim()) || inlineConstraint.test(plain(parts[1] ?? ''))) fail(source, '지원하지 않는 컬럼 타입/제약');
  return { name: upper(m[1]), type: parts[0].trim().toLowerCase().replace(/\s+/g, ' '), nullable, default: parts[1]?.trim() ?? null };
}

export function applySql(tables, sql, source, provenance = [source], change = null) {
  const record = (t) => {
    t.sources = [...new Set([...t.sources, ...provenance])];
    if (change && !t.changesets.some((c) => JSON.stringify(c) === JSON.stringify(change))) t.changesets.push(change);
    return t;
  };
  const touch = (name) => {
    const t = tables.get(upper(name));
    if (!t) fail(source, `없는 테이블 ${name}`);
    return record(t);
  };
  for (const statement of splitSql(sql)) {
    let m;
    const alter = statement.match(new RegExp(`^ALTER TABLE (?:ONLY )?${ident}\\s+([\\s\\S]+)$`, 'i'));
    if (alter && splitSql(alter[1], ',').length !== 1) fail(source, '복수 ALTER 작업은 지원하지 않는다');
    // 데이터 값과 비밀번호는 모델/문서에 복사하지 않는다.
    if (/^(INSERT INTO|UPDATE\s|DELETE FROM)/i.test(statement)) continue;
    if (/^SET (?:statement_timeout|lock_timeout|idle_in_transaction_session_timeout|client_encoding|standard_conforming_strings|check_function_bodies|xmloption|client_min_messages|row_security|default_tablespace|default_table_access_method) = /i.test(statement)) continue;
    if ((m = statement.match(new RegExp(`^CREATE TABLE (${ident})\\s*\\(([\\s\\S]*)\\)$`, 'i')))) {
      const name = upper(m[1]);
      if (tables.has(name)) fail(source, `중복 테이블 ${name}`);
      const attrs = splitSql(m[2], ',').map((v) => column(v, source));
      if (new Set(attrs.map((a) => a.name)).size !== attrs.length) fail(source, `중복 컬럼 ${name}`);
      tables.set(name, record({ name, attributes: attrs, constraints: [], indexes: [], sources: [], changesets: [] }));
    } else if ((m = statement.match(new RegExp(`^ALTER TABLE (?:ONLY )?(${ident})\\s+ADD CONSTRAINT (${ident})\\s+([\\s\\S]+)$`, 'i')))) {
      const t = touch(m[1]), definition = m[3].trim();
      if (!/^(PRIMARY KEY|UNIQUE|FOREIGN KEY|CHECK)\s*\(/i.test(definition)) fail(source, '지원하지 않는 테이블 제약');
      if (t.constraints.some((c) => c.name === upper(m[2]))) fail(source, `중복 제약 ${m[2]}`);
      t.constraints.push({ name: upper(m[2]), definition });
      const pk = definition.match(/^PRIMARY KEY\s*\(([^)]+)\)/i);
      if (pk) for (const name of pk[1].split(',').map((v) => upper(v.trim()))) {
        const a = t.attributes.find((v) => v.name === name);
        if (!a) fail(source, `PK 컬럼 없음 ${name}`);
        a.nullable = false;
      }
    } else if ((m = statement.match(new RegExp(`^CREATE (UNIQUE )?INDEX (${ident}) ON (${ident}) (?:USING ([a-z]+) )?(\\([\\s\\S]+)$`, 'i')))) {
      const t = touch(m[3]);
      if (t.indexes.some((x) => x.name === upper(m[2]))) fail(source, `중복 인덱스 ${m[2]}`);
      t.indexes.push({ name: upper(m[2]), unique: !!m[1], method: m[4]?.toLowerCase() ?? 'btree', definition: m[5].trim() });
    } else if ((m = statement.match(new RegExp(`^ALTER TABLE (?:ONLY )?(${ident})\\s+ADD (?:COLUMN )?([\\s\\S]+)$`, 'i')))) {
      const t = touch(m[1]), a = column(m[2], source);
      if (t.attributes.some((v) => v.name === a.name)) fail(source, `이미 있는 컬럼 ${a.name}`);
      t.attributes.push(a);
    } else if ((m = statement.match(new RegExp(`^ALTER TABLE (?:ONLY )?(${ident})\\s+ALTER COLUMN (${ident})\\s+([\\s\\S]+)$`, 'i')))) {
      const t = touch(m[1]), a = t.attributes.find((v) => v.name === upper(m[2]));
      if (!a) fail(source, `없는 컬럼 ${m[2]}`);
      const change = m[3].trim();
      if (/^SET NOT NULL$/i.test(change)) a.nullable = false;
      else if (/^DROP NOT NULL$/i.test(change)) a.nullable = true;
      else if (/^SET DEFAULT /i.test(change) && splitSql(change, ',').length === 1 && !inlineConstraint.test(plain(change))) a.default = change.replace(/^SET DEFAULT /i, '');
      else if (/^DROP DEFAULT$/i.test(change)) a.default = null;
      else if (/^TYPE /i.test(change) && typePattern.test(change.slice(5).trim())) a.type = change.slice(5).trim().toLowerCase();
      else fail(source, '지원하지 않는 ALTER COLUMN');
    } else if ((m = statement.match(new RegExp(`^COMMENT ON (TABLE|COLUMN) (${ident})(?:\\.(${ident}))? IS ('(?:[^']|'')*'|NULL)$`, 'is')))) {
      const t = touch(m[2]), comment = /^NULL$/i.test(m[4]) ? null : m[4].slice(1, -1).replaceAll("''", "'");
      if (m[1].toUpperCase() === 'TABLE' && !m[3]) t.comment = comment;
      else {
        const a = t.attributes.find((v) => v.name === upper(m[3]));
        if (!a) fail(source, '주석 대상 컬럼 없음');
        a.comment = comment;
      }
    } else fail(source, `지원하지 않는 SQL(${statement.split(/\s+/).slice(0, 3).join(' ')}) — 누락 없이 파서를 보완한다`);
  }
}

/** 조건부 changelog를 임의로 합치지 않는다. 현재 지원하는 무조건 적용 경로만 명세한다. */
export function readLiquibase({ repo, config, parseYaml }) {
  if (!config?.root || !config.resources) throw new Error('conventions.liquibase의 root/resources가 필요하다');
  const tables = new Map(), files = [], stack = new Set(), seen = new Set(), changesets = new Set();
  const changeKey = (file, author, id) => {
    const key = `${file}#${author}:${id}`;
    if (!author || !id || changesets.has(key)) fail(file, 'changeset ID/author 누락 또는 중복');
    changesets.add(key);
    return key;
  };
  const safe = (path) => {
    const abs = resolve(repo, path), rel = relative(repo, abs).replaceAll('\\', '/');
    if (rel.startsWith('../') || rel === '..' || /^[A-Za-z]:/.test(rel)) throw new Error('저장소 밖 changelog 경로');
    if (!existsSync(abs)) throw new Error(`changelog 없음: ${rel}`);
    return rel;
  };
  const visit = (path, parents = []) => {
    path = safe(path);
    if (stack.has(path) || seen.has(path)) fail(path, '순환/중복 include');
    stack.add(path); seen.add(path); files.push(path);
    const text = readFileSync(resolve(repo, path), 'utf8'), chain = [...parents, path];
    if (path.endsWith('.sql')) {
      if (!/^--liquibase formatted sql/m.test(text)) fail(path, 'formatted SQL만 지원한다');
      if (/^--(?:changeset[^\n]*(?:dbms:|context|labels:|runOnChange:|runAlways:)|precondition|include)/im.test(text)) fail(path, '조건부/특수 SQL changeset은 지원하지 않는다');
      const starts = [...text.matchAll(/^--changeset ([^:\s]+):([^\s]+)([^\n]*)$/gm)];
      if (!starts.length) fail(path, 'SQL changeset 식별자 없음');
      if (splitSql(text.slice(0, starts[0].index)).length) fail(path, 'changeset 밖 SQL');
      for (let i = 0; i < starts.length; i++) {
        const m = starts[i], change = { file: path, author: m[1], id: m[2] };
        if (m[3].trim()) fail(path, 'SQL changeset의 추가 옵션은 지원하지 않는다');
        const key = changeKey(path, m[1], m[2]);
        applySql(tables, text.slice(m.index, starts[i + 1]?.index ?? text.length), key, chain, change);
      }
    } else if (/\.ya?ml$/.test(path)) {
      const doc = parseYaml(text);
      if (!Array.isArray(doc?.databaseChangeLog)) fail(path, 'databaseChangeLog 목록 없음');
      for (const item of doc.databaseChangeLog) {
        if (item.include) {
          const inc = item.include;
          if (Object.keys(inc).some((k) => !['file', 'relativeToChangelogFile'].includes(k))) fail(path, '조건부/특수 include는 지원하지 않는다');
          visit(resolve(repo, inc.relativeToChangelogFile ? dirname(path) : config.resources, inc.file), chain);
        } else if (item.changeSet) {
          const cs = item.changeSet;
          changeKey(path, cs.author, cs.id);
          if (Object.keys(cs).some((k) => !['id', 'author', 'comment', 'changes', 'rollback'].includes(k))) fail(path, '조건부/특수 changeset은 지원하지 않는다');
          for (const change of cs.changes ?? []) {
            if (Object.keys(change).length !== 1) fail(path, '변경 항목 형식 오류');
            const [kind, value] = Object.entries(change)[0];
            if (['insert', 'update', 'delete', 'loadData', 'loadUpdateData'].includes(kind)) continue;
            if (kind === 'sql') {
              if (typeof value !== 'string' && Object.keys(value).some((k) => !['sql', 'comment'].includes(k))) fail(path, 'sql의 특수 옵션은 지원하지 않는다');
              applySql(tables, typeof value === 'string' ? value : value.sql, `${path}#${cs.author}:${cs.id}`, chain, { file: path, author: cs.author, id: cs.id });
            } else fail(path, `지원하지 않는 Liquibase 변경 ${kind} — 누락 없이 파서를 보완한다`);
          }
        } else fail(path, '지원하지 않는 changelog 항목');
      }
    } else fail(path, '지원하지 않는 changelog 형식');
    stack.delete(path);
  };
  visit(config.root);
  return { tables, files };
}

export function physicalContract(table) {
  return { attributes: table.attributes, constraints: table.constraints, indexes: table.indexes, ...(table.comment !== undefined ? { comment: table.comment } : {}) };
}

/** G3에서 요구할 물리 설계의 완성 조건. 초안 허용 여부와 분리한다. */
export function entityDesignIssues(entity) {
  if (entity.storage_kind !== undefined) {
    if (entity.storage_kind !== 'nonpersistent') return ['storage_kind는 nonpersistent만 명시할 수 있다'];
    const issues = [];
    if (entity.design_stage !== 'logical' || !entity.name || !entity.module) issues.push('비저장 투영은 logical·논리명·소유 모듈이 필요하다');
    if (typeof entity.nonpersistent_reason !== 'string' || !entity.nonpersistent_reason.trim()) issues.push('비저장 근거(nonpersistent_reason) 없음');
    for (const key of ['table', 'physical', 'columns', 'history_storage']) if (Object.hasOwn(entity, key)) issues.push(`비저장 투영에 ${key} 지정 금지`);
    if ((entity.attributes ?? []).some((a) => Object.hasOwn(a, 'physical_name'))) issues.push('비저장 투영에 physical_name 지정 금지');
    return issues;
  }
  if (entity.design_stage === 'logical') return ['논리 설계 중 — 소유 작업흐름 G3 전에 물리명·키·제약을 확정한다'];
  const issues = [];
  if (!entity.table) issues.push('물리 테이블명 미정');
  const physical = entity.physical;
  if (!physical) return [...issues, '물리 계약(physical) 없음'];
  const attrs = Array.isArray(physical.attributes) ? physical.attributes : [];
  if (!attrs.length) issues.push('물리 컬럼 없음');
  for (const attr of attrs) if (!attr?.name || !attr.type || typeof attr.nullable !== 'boolean' || !Object.hasOwn(attr, 'default')) issues.push('물리 컬럼 name/type/nullable/default 미완성');
  if (!Array.isArray(physical.constraints) || !Array.isArray(physical.indexes)) issues.push('물리 제약/인덱스 목록 미정');
  const constraints = Array.isArray(physical.constraints) ? physical.constraints : [];
  const keys = constraints.flatMap((c) => c?.definition?.match(/^PRIMARY KEY\s*\(([^)]+)\)/i)?.[1].split(',').map((v) => upper(v.trim())) ?? []);
  if (!keys.length) issues.push('물리 기본키 정의 없음');
  for (const key of keys) if (!attrs.some((a) => a?.name === key && a.nullable === false)) issues.push(`기본키 컬럼/NOT NULL 누락: ${key}`);
  for (const attr of entity.attributes ?? []) {
    const name = attr.physical_name ?? attr.name;
    if (!attrs.some((a) => a?.name === name)) issues.push(`논리→물리 컬럼 연결 누락: ${attr.name}`);
    if (attr.pk && !keys.includes(name)) issues.push(`논리→물리 기본키 불일치: ${attr.name}`);
  }
  return issues;
}

/** 배열 순서는 컬럼만 의미가 있다. 업무 설명은 물리 비교 대상이 아니다. */
export function schemaDifferences(entities, catalog, external = []) {
  const errors = [], used = new Set(), exempt = new Map(external.map((x) => [x.table, x]));
  const comparable = (v) => JSON.stringify(v, function (key, value) {
    return value && typeof value === 'object' && !Array.isArray(value)
      ? Object.fromEntries(Object.keys(value).sort().map((k) => [k, value[k]])) : value;
  });
  for (const e of entities) {
    if (!['planned', 'implemented'].includes(e.implementation)) { errors.push(`${e.id}: implementation 누락`); continue; }
    if (e.design_stage !== undefined && !['logical', 'physical'].includes(e.design_stage)) errors.push(`${e.id}: design_stage는 logical/physical이어야 한다`);
    if (e.storage_kind !== undefined) {
      errors.push(...entityDesignIssues(e).map((message) => `${e.id}: ${message}`));
      continue; // 실제 테이블을 분류한 것으로 세지 않는다. catalog 미분류 검사는 유지한다.
    }
    if (e.design_stage === 'logical') {
      if (e.implementation !== 'planned' || e.table || e.physical) errors.push(`${e.id}: 논리 설계는 planned이며 table/physical을 아직 지정하지 않는다`);
      if (!e.name || !e.module) errors.push(`${e.id}: 논리명/소유 모듈 누락`);
      continue;
    }
    if (!e.module || !e.table || used.has(e.table)) errors.push(`${e.id}: 모듈/테이블 누락 또는 중복`);
    used.add(e.table);
    if (e.physical) {
      for (const key of ['attributes', 'constraints', 'indexes']) {
        const values = e.physical[key];
        if (!Array.isArray(values) || values.some((x) => !x || typeof x.name !== 'string' || !x.name) || new Set(values.filter(Boolean).map((x) => x.name)).size !== values.length) errors.push(`${e.id}: physical.${key} 형식/중복 오류`);
      }
      for (const a of Array.isArray(e.physical.attributes) ? e.physical.attributes : []) {
        if (!a || !a.name || typeof a.type !== 'string' || !a.type || typeof a.nullable !== 'boolean' || !Object.hasOwn(a, 'default') || (a.default !== null && typeof a.default !== 'string')) errors.push(`${e.id}: 컬럼 ${a?.name}의 type/nullable/default 명세 오류`);
      }
      for (const c of Array.isArray(e.physical.constraints) ? e.physical.constraints : []) {
        if (typeof c?.definition !== 'string' || !c.definition.trim()) errors.push(`${e.id}: 제약 definition 누락`);
      }
      for (const i of Array.isArray(e.physical.indexes) ? e.physical.indexes : []) {
        if (typeof i?.unique !== 'boolean' || typeof i.method !== 'string' || !i.method || typeof i.definition !== 'string' || !i.definition) errors.push(`${e.id}: 인덱스 unique/method/definition 오류`);
      }
    }
    const actual = catalog.tables.get(e.table);
    if (e.implementation === 'planned') {
      if (actual) errors.push(`${e.id}: planned인데 활성 Liquibase에 테이블 존재`);
      continue;
    }
    if (!actual) { errors.push(`${e.id}: 활성 Liquibase 테이블 없음 (${e.table})`); continue; }
    if (!e.physical) { errors.push(`${e.id}: physical 계약 없음`); continue; }
    if ((e.physical.comment ?? null) !== (actual.comment ?? null)) errors.push(`${e.id}: 테이블 주석 불일치`);
    for (const key of ['attributes', 'constraints', 'indexes']) {
      const expected = e.physical[key], found = actual[key];
      if (!Array.isArray(expected)) { errors.push(`${e.id}: physical.${key} 목록 없음`); continue; }
      const names = new Set([...expected, ...found].map((x) => x.name));
      for (const name of names) {
        const a = expected.find((x) => x.name === name), b = found.find((x) => x.name === name);
        if (comparable(a) !== comparable(b)) errors.push(`${e.id} ${e.table}.${key}.${name}: 설계/Liquibase 불일치`);
      }
      if (key === 'attributes' && comparable(expected.map((x) => x.name)) !== comparable(found.map((x) => x.name))) errors.push(`${e.id}: 컬럼 순서/목록 불일치`);
    }
    for (const source of actual.sources) if (!e.sources?.includes(source)) errors.push(`${e.id}: Liquibase 소스 연결 누락 ${source}`);
    const names = actual.attributes.map((a) => a.name);
    if (e.columns && comparable([...e.columns].sort()) !== comparable([...names].sort())) errors.push(`${e.id}: 논리 columns 목록과 물리 컬럼 불일치`);
    for (const attr of e.attributes ?? []) {
      const columnName = attr.physical_name ?? attr.name;
      if (!names.includes(columnName)) errors.push(`${e.id}: 물리 정의에 없는 논리 속성 ${attr.name} → ${columnName}`);
      if (attr.pk && !actual.constraints.some((c) => c.definition.match(/^PRIMARY KEY\s*\(([^)]+)\)/i)?.[1].split(',').map((v) => upper(v.trim())).includes(columnName))) errors.push(`${e.id}: 논리 PK 불일치 ${attr.name}`);
    }
  }
  for (const [name] of catalog.tables) if (!used.has(name) && !exempt.has(name)) errors.push(`${name}: ENT 또는 기술 기반 테이블 분류 누락`);
  for (const x of external) if (!catalog.tables.has(x.table) || used.has(x.table) || !x.module || !x.reason) errors.push(`${x.table}: 기술 기반 분류 오류`);
  return errors;
}
