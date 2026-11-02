/** 업무 배치 명세의 필수 계약·연결·현행 구현 목록 검사. 정본은 artifact/spec-schema.md. */
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { loadSpecSet } from './spec-set.mjs';

const required = ['title', 'module', 'scope', 'parameters', 'permissions', 'processing', 'concurrency', 'failure', 'cancellation', 'retry', 'outputs', 'observability'];
const relations = { feature: 'FEAT-', usecase: 'UC-', reqs: 'REQ-', screens: 'SCR-', testcases: 'TC-', runbooks: 'RUN-', uses_features: 'FEAT-' };
const pathOf = value => String(value).split('#')[0];
export function batchContractErrors(document, { knownIds, implementationSources = [], sourceExists = () => true } = {}) {
  const errors = [], jobs = document?.jobs ?? [], covered = new Set();
  if (!jobs.length) errors.push('65-batches: jobs가 비어 있다.');
  const ids = new Set();
  for (const job of jobs) {
    const fail = message => errors.push(`${job.id ?? 'JOB 미지정'}: ${message}`);
    if (!/^JOB-\d{3,}$/.test(job.id ?? '') || ids.has(job.id)) fail('배치 ID가 잘못되었거나 중복이다.');
    ids.add(job.id);
    for (const key of required) if (typeof job[key] !== 'string' || !job[key].trim()) fail(`${key} 계약이 필요하다.`);
    if (!['implemented', 'planned'].includes(job.implementation)) fail('implementation은 implemented/planned다.');
    for (const [key, prefix] of Object.entries(relations)) {
      const single = key === 'feature' || key === 'usecase';
      const values = single ? [job[key]] : job[key] ?? [];
      if (!Array.isArray(values) || ((!['runbooks','uses_features'].includes(key)) && !values.length)) { fail(`${key} 연결이 필요하다.`); continue; }
      for (const id of values) if (typeof id !== 'string' || !id.startsWith(prefix) || (knownIds && !knownIds.has(id))) fail(`${key}의 존재하지 않는 참조: ${id}`);
    }
    if (!job.schedule || typeof job.schedule !== 'object') fail('schedule 계약이 필요하다.');
    else {
      for (const key of ['batch_id','bean_id','seed_cron','seed_timezone','seed_use_yn']) {
        if (!(key in job.schedule)) fail(`schedule.${key}를 명시해야 한다.`);
        else if (job.implementation === 'implemented' && !job.schedule[key]) fail(`현행 schedule.${key}가 비어 있다.`);
      }
      if (!job.schedule.activation) fail('schedule.activation 근거가 필요하다.');
    }
    if (job.implementation === 'planned' && !job.pending) fail('계획 배치의 pending 근거가 필요하다.');
    if (!Array.isArray(job.sources)) fail('sources는 배열이다.');
    else {
      for (const source of job.sources) {
        if (typeof source !== 'string' || !source.startsWith('sources/') || source.includes('..') || !sourceExists(pathOf(source))) fail(`잘못된 소스 연결: ${source}`);
      }
      if (job.implementation === 'implemented') {
        const matches = job.sources.map(pathOf).filter(source => implementationSources.includes(source));
        if (!matches.length) fail('실제 BatchJob 구현 연결이 없다.');
        for (const source of matches) { if (covered.has(source)) fail(`같은 구현의 중복 명세: ${source}`); covered.add(source); }
      }
    }
  }
  for (const source of implementationSources) if (!covered.has(source)) errors.push(`65-batches: 미등재 BatchJob 구현 ${source}`);
  return errors;
}

export function checkBatchSpec({ repo, parseYaml }) {
  const loaded = loadSpecSet({ repo, name: '65-batches', parseYaml, required: false });
  if (!loaded) return []; // 기존 부분 fixture/다른 고객 저장소는 새 정본 도입 시 검사한다.
  const knownIds = new Set();
  const collect = value => {
    if (!value || typeof value !== 'object') return;
    if (value.id) knownIds.add(value.id);
    Object.values(value).forEach(collect);
  };
  for (const name of ['10-requirements','30-usecases','31-features','60-screens','70-testcases','80-operations']) {
    collect(loadSpecSet({ repo, name, parseYaml, required: false })?.document);
  }
  const implementationSources = [];
  const walk = path => {
    if (!existsSync(join(repo,path))) return;
    for (const entry of readdirSync(join(repo,path), {withFileTypes:true})) {
      const file = `${path}/${entry.name}`;
      if (entry.isDirectory()) { if (!['build','.gradle'].includes(entry.name)) walk(file); }
      else if (file.includes('/src/main/java/') && file.endsWith('.java') && /\bimplements\s+BatchJob\b/.test(readFileSync(join(repo,file),'utf8'))) implementationSources.push(file);
    }
  };
  walk('sources/backend');
  return batchContractErrors(loaded.document,{knownIds,implementationSources,sourceExists:path=>existsSync(join(repo,path))});
}
