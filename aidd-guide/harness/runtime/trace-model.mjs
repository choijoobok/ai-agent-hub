/** 설계 연결의 공용 해석. 규칙의 정본은 artifact/spec-schema.md다. */
export function relationIds(item, ...keys) {
  return [...new Set(keys.flatMap((key) => {
    const value = item?.[key];
    return typeof value === 'string' ? [value] : Array.isArray(value) ? value.filter((v) => typeof v === 'string') : [];
  }))];
}

export const featureIds = (item) => relationIds(item, 'feature', 'features');
export const screenIds = (item) => relationIds(item, 'screen', 'screens');

export function testsByFeature(testcases) {
  const result = new Map();
  for (const testcase of testcases) for (const id of featureIds(testcase)) {
    if (!result.has(id)) result.set(id, []);
    result.get(id).push(testcase);
  }
  return result;
}

/** 파일이 이미 삭제되어도 선언된 경로로 찾는다. 저장소 밖 경로는 받지 않는다. */
export function sourceRef(value) {
  const [raw, ...symbol] = String(value ?? '').replaceAll('\\', '/').split('#');
  const path = raw.replace(/^\.\//, '');
  if (!path || path.startsWith('/') || /^[A-Za-z]:/.test(path) || path.split('/').includes('..')) return null;
  return { path, symbol: symbol.join('#') };
}

export function sourceMatches(declared, changed) {
  const a = sourceRef(declared), b = sourceRef(changed);
  if (!a || !b) return false;
  const matches = a.path.endsWith('/') ? b.path.startsWith(a.path) : a.path === b.path;
  return matches && (!b.symbol || !a.symbol || a.symbol === b.symbol);
}

export function sourceEntries(item) {
  return relationIds(item, 'sources', 'source_contract');
}

export function associatedScreens(ids, features, screens) {
  const wanted = new Set(ids);
  const result = new Set(features.filter((f) => wanted.has(f.id)).flatMap(screenIds));
  for (const screen of screens) if (featureIds(screen).some((id) => wanted.has(id))) result.add(screen.id);
  // 호출 화면의 문서를 읽을 때 공통 화면 계약도 함께 읽는다.
  const queue = [...result];
  for (let i = 0; i < queue.length; i++) {
    for (const id of relationIds(screens.find((s) => s.id === queue[i]), 'uses_screens')) {
      if (!result.has(id)) { result.add(id); queue.push(id); }
    }
  }
  return [...result].sort();
}

export function createTraceModel(groups) {
  const items = Object.values(groups).flat().filter((item) => item?.id);
  const byId = new Map(items.map((item) => [item.id, item]));
  const consumers = new Map();
  const edge = (dependency, consumer) => {
    if (!byId.has(dependency) || !byId.has(consumer) || dependency === consumer) return;
    if (!consumers.has(dependency)) consumers.set(dependency, new Set());
    consumers.get(dependency).add(consumer);
  };
  for (const r of groups.requirements ?? []) {
    for (const id of r.relations?.depends_on ?? []) edge(id, r.id);
    for (const id of r.relations?.constrains ?? []) edge(r.id, id);
    for (const id of r.relations?.conflicts_with ?? []) { edge(id, r.id); edge(r.id, id); }
  }
  for (const u of groups.usecases ?? []) for (const id of relationIds(u, 'req', 'reqs')) edge(id, u.id);
  for (const n of groups.nfrs ?? []) for (const id of relationIds(n, 'test_case')) edge(n.id, id);
  for (const f of groups.features ?? []) {
    for (const id of relationIds(f, 'reqs', 'usecases', 'nfrs', 'uses_features', 'entities', 'apis', 'interfaces', 'jobs')) edge(id, f.id);
    for (const id of screenIds(f)) { edge(f.id, id); edge(id, f.id); }
  }
  for (const s of groups.screens ?? []) {
    for (const id of featureIds(s)) { edge(id, s.id); edge(s.id, id); }
    for (const id of relationIds(s, 'uses_screens', 'apis')) edge(id, s.id);
  }
  for (const a of groups.apis ?? []) {
    // 소유 기능과 API의 계약은 함께 검토한다. 호출 기능은 FEAT.apis에서 역으로 모인다.
    for (const id of featureIds(a)) { edge(a.id, id); edge(id, a.id); }
  }
  for (const t of groups.testcases ?? []) {
    for (const id of [...featureIds(t), ...relationIds(t, 'reqs', 'apis', 'screen', 'screens', 'jobs')]) edge(id, t.id);
  }
  for (const job of groups.jobs ?? []) {
    for (const id of featureIds(job)) edge(job.id,id);
    for (const id of [...featureIds(job), ...relationIds(job, 'usecase','reqs','uses_features')]) edge(id,job.id);
    for (const id of relationIds(job,'screens','testcases','runbooks')) edge(job.id,id);
  }
  for (const item of [...(groups.interfaces ?? []), ...(groups.operations ?? [])]) {
    for (const id of [...featureIds(item), ...relationIds(item, 'traces_to', 'reqs', 'apis')]) edge(id, item.id);
  }
  return { byId, consumers, groups };
}

export function affectedIds(model, seeds) {
  const found = new Set(seeds.filter((id) => model.byId.has(id)));
  const queue = [...found];
  for (let i = 0; i < queue.length; i++) for (const next of model.consumers.get(queue[i]) ?? []) {
    if (!found.has(next)) { found.add(next); queue.push(next); }
  }
  return [...found].sort();
}

export function sourceSeeds(model, path, content = '') {
  if (!sourceRef(path)) return [];
  const ids = new Set();
  for (const item of model.byId.values()) {
    if (sourceEntries(item).some((source) => sourceMatches(source, path))) ids.add(item.id);
  }
  for (const match of content.slice(0, 1500).matchAll(/@(feat|scr)\s+((?:FEAT|SCR)-\d+)\b/g)) {
    if (model.byId.has(match[2])) ids.add(match[2]);
  }
  return [...ids].sort();
}

/** 관계의 종류와 소유권을 검증한다. 구현 여부·파일 존재는 단계별 검사에서 판단한다. */
export function relationErrors(model) {
  const errors = [];
  for (const item of model.byId.values()) {
    for (const key of ['features', 'screens', 'uses_features', 'uses_screens', 'sources', 'docs', 'screen_cases']) {
      const value = item[key];
      if (value !== undefined && (!Array.isArray(value) || value.some((id) => typeof id !== 'string' || !id.trim()))) {
        errors.push([item.id, `${key}는 비어 있지 않은 문자열의 배열이어야 한다`]);
      }
    }
    if (/^(API|SCR|TC)-/.test(item.id) && (typeof item.feature !== 'string' || !item.feature.startsWith('FEAT-') || !model.byId.has(item.feature))) {
      errors.push([item.id, '소유 feature는 존재하는 FEAT 식별자여야 한다']);
    }
    for (const id of relationIds(item, 'features')) {
      if (!id.startsWith('FEAT-') || !model.byId.has(id)) errors.push([item.id, `잘못된 참여 기능: ${id}`]);
    }
    if (item.source_contract !== undefined && typeof item.source_contract !== 'string') errors.push([item.id, 'source_contract는 소스 경로 문자열이어야 한다']);
    for (const [key, prefix] of [['uses_features', 'FEAT-'], ['uses_screens', 'SCR-']]) {
      for (const id of relationIds(item, key)) {
        if (!id.startsWith(prefix) || !model.byId.has(id) || id === item.id) errors.push([item.id, `${key}의 잘못된 참조: ${id}`]);
      }
    }
    for (const source of sourceEntries(item)) {
      if (!sourceRef(source)?.path.startsWith('sources/')) errors.push([item.id, `저장소 sources/ 아래 경로가 아닌 소스 연결: ${source}`]);
    }
    for (const doc of relationIds(item, 'docs', 'current_doc', 'target_doc')) {
      if (!sourceRef(doc)?.path.startsWith('documents/')) errors.push([item.id, `저장소 documents/ 아래 경로가 아닌 문서 연결: ${doc}`]);
    }
  }
  return errors;
}

export function linkedDocuments(model, ids) {
  const docs = new Set();
  for (const id of ids) {
    const item = model.byId.get(id);
    for (const doc of relationIds(item, 'current_doc', 'target_doc', 'docs')) if (sourceRef(doc)) docs.add(sourceRef(doc).path);
    for (const ref of relationIds(item, 'screen_cases')) docs.add(ref.split('#')[0]);
    if (id.startsWith('SCR-') && item?.route && !item.current_doc && !item.target_doc) {
      docs.add(`documents/screen${item.route}/requirements.md`);
    }
  }
  for (const doc of [...docs]) {
    if (doc.endsWith('/requirements.md') && !doc.includes('/_standalone/')) docs.add(doc.replace(/requirements\.md$/, 'manual.md'));
  }
  return [...docs].sort();
}
