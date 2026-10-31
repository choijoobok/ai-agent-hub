#!/usr/bin/env node
/**
 * 목업 골격 생성기 · 검사기.
 *
 * <p><b>목업은 요건서의 파생물이다</b> — 결정은 `requirements.md` 가 갖고 목업은 그것을
 * 눈으로 확인하는 그림이다. 그래서 기계가 만들 수 있는 것은 기계가 만든다:
 * 공용 스타일 블록 · 개요 바 · 테스트 케이스 표 · 메뉴얼이 요구하는 그림 키 목록.
 * <b>남는 것은 그림 하나이고 그것만 AI 가 그린다.</b>
 *
 * <p>세 갈래로 쓴다:
 * <pre>
 *   node scaffold.mjs --list                 화면 목록과 문서 유무
 *   node scaffold.mjs &lt;화면&gt;                 골격을 stdout 으로 낸다
 *   node scaffold.mjs --check [&lt;화면&gt;]        있는 목업을 요건서·메뉴얼과 대조한다
 * </pre>
 *
 * <p>화면은 페이지 경로(`/log/access`) · 폴더(`log/access`) · 이름(`access`) 아무거나 받는다.
 */
import fs from 'node:fs';
import path from 'node:path';
import { declaredFigures, declaredScreen, searchComboScript } from './declared-layout.mjs';
import {annotationBook, annotationIssues, surfaceRows, surfaceIntroduction, surfaceOf} from './presentation.mjs';
import {genericScreen, areaTitle} from './generic-layout.mjs';
import {requestDeclaration, inheritRequest} from './request-layout.mjs';

const REPO = path.resolve(import.meta.dirname, '../../..');
const SCREENS = path.join(REPO, 'documents/screen');
const TEMPLATE = path.join(REPO, 'aidd-guide/artifact/template/03-mockup.html');
const MARK = String.fromCharCode(0xe000); // 코드 스팬 자리표시자 — 본문에 나올 수 없는 문자
const MISSING_OK = MARK + 'none';     // 목업 없음 — 정상
const MISSING_NEEDED = MARK + 'need'; // 목업 없음 — 메뉴얼이 그림을 부른다

const read = (p) => (fs.existsSync(p) ? fs.readFileSync(p, 'utf8') : null);

/** documents/screen/ 아래의 화면 폴더 — requirements.md 를 가진 곳이 화면이다. */
function allScreens() {
  const out = [];
  (function walk(d) {
    for (const e of fs.readdirSync(d, { withFileTypes: true })) {
      if (!e.isDirectory()) continue;
      const p = path.join(d, e.name);
      if (fs.existsSync(path.join(p, 'requirements.md'))) {
        out.push(path.relative(SCREENS, p).split(path.sep).join('/'));
      }
      walk(p);
    }
  })(SCREENS);
  return out.sort();
}

/** 인자 하나를 화면 폴더로 푼다. 여러 개가 걸리면 세우고 묻는다. */
function resolveScreen(arg) {
  const all = allScreens();
  const key = arg.replace(/^\/+/, '').replace(/\/+$/, '');
  if (all.includes(key)) return key;
  const hits = all.filter((s) => s === key || s.endsWith(`/${key}`));
  if (hits.length === 1) return hits[0];
  if (hits.length > 1) {
    console.error(`'${arg}' 에 여러 화면이 걸린다 — 하나를 골라 다시 부른다:\n  ${hits.join('\n  ')}`);
    process.exit(2);
  }
  console.error(`'${arg}' 에 해당하는 화면이 없다. --list 로 목록을 본다.`);
  process.exit(2);
}

/** 마크다운 표 → [[칸…]]. 구분선(|---|)은 건너뛴다. */
function mdTable(block) {
  const rows = [];
  for (const line of block.split('\n')) {
    if (!/^\s*\|/.test(line)) continue;
    if (/^\s*\|[\s|:-]+\|\s*$/.test(line)) continue;
    const cells = line.trim().replace(/^\|/, '').replace(/\|$/, '').split(/(?<!\\)\|/);
    rows.push(cells.map((c) => c.trim()));
  }
  return rows;
}

/** `## N. 제목` 절의 본문 — 다음 `## ` 까지. */
function section(md, titleRe) {
  const lines = md.split('\n');
  const i = lines.findIndex((l) => /^## /.test(l) && titleRe.test(l));
  if (i < 0) return null;
  let j = i + 1;
  while (j < lines.length && !/^## /.test(lines[j])) j++;
  return { head: lines[i], body: lines.slice(i + 1, j).join('\n') };
}

/**
 * 마크다운 인라인 → HTML. 표 칸 안에서만 쓴다.
 *
 * <p><b>코드 스팬을 먼저 빼 둔다.</b> 백틱 안에 별이 든 값이 실제로 있어(마스킹 표시)
 * 코드를 태그로 바꾼 뒤 강조를 매칭하면 그 별이 강조 구분자로 읽히고
 * <code>&lt;b&gt;&lt;code&gt;&lt;/b&gt;</code> 처럼 겹친 태그가 나온다.
 */
function html(s) {
  const spans = [];
  // ① 코드 스팬을 자리표시자로 치운다 — 그 안의 * 는 강조가 아니다.
  //    구분자는 사용자 영역 문자다. 본문에 나올 수 없어야 실제 값과 겹치지 않는다.
  let t = s.replace(/`([^`]+)`/g, (_, x) => {
    spans.push(x);
    return MARK + (spans.length - 1) + MARK;
  });
  t = t.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  // ② 강조 — 자리표시자에는 * 가 없으므로 겹치지 않는다
  t = t.replace(/\*\*\*([^*]+)\*\*\*/g, (_, x) => `<b><i>${x}</i></b>`);
  // 굵기와 기울기가 한쪽에서만 겹치는 둘 — **가 *나*** · ***가* 나**
  t = t.replace(/\*\*([^*]*)\*([^*]+)\*\*\*/g, (_, a, b) => `<b>${a}<i>${b}</i></b>`);
  t = t.replace(/\*\*\*([^*]+)\*([^*]*)\*\*/g, (_, a, b) => `<b><i>${a}</i>${b}</b>`);
  t = t.replace(/\*\*([\s\S]+?)\*\*/g, (_, x) => `<b>${x}</b>`);
  t = t.replace(/\*([^*]+)\*/g, (_, x) => `<i>${x}</i>`);
  // ③ 코드 스팬을 되돌린다 — 이때 이스케이프한다
  t = t.replace(new RegExp(MARK + '(\\d+)' + MARK, 'g'), (_, i) => {
    const x = spans[+i].replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    return `<code>${x}</code>`;
  });
  return t.split('\\|').join('|');
}

/** 메뉴얼이 요구하는 그림 키 — `[설명](mockup.html#키)` 한 줄 표기. */
function manualKeys(dir) {
  const md = read(path.join(SCREENS, dir, 'manual.md'));
  if (!md) return [];
  const out = [];
  for (const m of md.matchAll(/^\[(.*)\]\(\.?\/?mockup\.html#([\w-]+)\)$/gm)) {
    out.push({ key: m[2], caption: m[1] });
  }
  return out;
}

/** 요건서 §테스트 케이스 → { groups: [{title, rows}] } */
function testcases(md) {
  const sec = section(md, /테스트 케이스/);
  if (!sec) return null;
  const groups = [];
  let title = null;
  let buf = [];
  const flush = () => {
    if (!buf.length) return;
    const rows = mdTable(buf.join('\n'));
    // 첫 행은 머리(# / 조작 · 상황 / 기대)
    const body = rows.slice(1).filter((r) => r.length >= 3);
    if (body.length) groups.push({ title: title || '케이스', rows: body });
    buf = [];
  };
  for (const line of sec.body.split('\n')) {
    if (/^### /.test(line)) {
      flush();
      title = line.replace(/^###\s*/, '').trim();
      continue;
    }
    if (/^\s*\|/.test(line)) buf.push(line);
    else flush();
  }
  flush();
  return { number: sec.head.match(/^## (\d+)\./)?.[1] ?? '?', groups };
}

/** 요건서 §0 메타 표 → [[항목, 값]] (머리 제외) */
function meta(md) {
  const sec = section(md, /메타/);
  if (!sec) return [];
  return mdTable(sec.body).slice(1).filter((r) => r.length >= 2);
}

/** 공용 스타일 블록 — 서식이 원본이다. 여기에 색을 적지 않는다. */
function styleBlock() {
  const t = read(TEMPLATE);
  if (!t) {
    console.error(`서식을 찾지 못했다: ${path.relative(REPO, TEMPLATE)}`);
    process.exit(2);
  }
  const m = t.match(/<style>[\s\S]*?<\/style>/i);
  if (!m) {
    console.error('서식에 <style> 블록이 없다.');
    process.exit(2);
  }
  return m[0];
}

/** 한 절 안의 표와 바로 앞 소제목을 함께 보존한다. */
function tablesIn(body) {
  const out = [];
  let heading = '';
  let lines = [];
  const flush = () => {
    const rows = mdTable(lines.join('\n'));
    if (rows.length >= 2) out.push({ heading, header: rows[0], rows: rows.slice(1) });
    lines = [];
  };
  for (const line of body.split('\n')) {
    if (/^#{2,6}\s+/.test(line)) {
      flush();
      heading = line.replace(/^#+\s*/, '').trim();
    } else if (/^\s*\|/.test(line)) {
      lines.push(line);
    } else {
      flush();
    }
  }
  flush();
  return out;
}

/** 마크다운 장식 없이 사람이 보는 표제어. 비교와 예시 값에만 쓴다. */
function plain(s = '') {
  return String(s)
    .replace(/`([^`]+)`/g, '$1')
    .replace(/\*+/g, '')
    .replace(/<[^>]+>/g, '')
    .replace(/\s+/g, ' ')
    .trim();
}

function findTable(sec, firstHeader) {
  return sec ? tablesIn(sec.body).find((t) => firstHeader.test(plain(t.header[0]))) : null;
}

/** 요건서의 화면·조회·컬럼·버튼 표를 한 번만 읽어 화면 모델로 만든다. */
function screenModel(md, type, allowInheritance = true) {
  const composition = section(md, /화면 구성/);
  const search = findTable(section(md, /조회조건/), /조건|검색/);
  const columns = (section(md, /^컬럼$|컬럼/) ? tablesIn(section(md, /^컬럼$|컬럼/).body) : [])
    .filter((t) => /컬럼/.test(plain(t.header[0])) && !/기본값|연쇄|재계산|비노출/.test(t.heading) && !t.header.some(h=>/^(기본값|변경 시)$/.test(plain(h))));
  const actionTables = tablesIn(section(md, /버튼.*액션|액션.*버튼/)?.body??'').filter(t=>/버튼|액션/.test(plain(t.header[0])));
  const text = `${type}\n${composition?.body ?? ''}`;
  const surfaces=surfaceRows(tablesIn(composition?.body??'').find(t=>t.header.includes('관리 대상·목적')));
  for(const table of columns){table.rows=table.rows.filter(r=>plain(r[0]));table.readonly=/^조회 전용/.test(surfaces.find(s=>s.name===areaTitle(table))?.save??'');}
  let model = {
    strictButtons: /상태별 버튼: 허용 목록/.test(composition?.body??''),
    screenNavigation: findTable(composition, /^화면 이동$/),
    figures: declaredFigures(findTable(composition, /그림 키/), plain),
    interactive: columns.length > 1,
    collapsibleMaster: /상단 목록은 접을 수/.test(composition?.body ?? ''),
    addRowTabs: (composition?.body.match(/^행 추가 탭:\s*(.+)$/m)?.[1] ?? '').split('·').map(s=>s.trim()),
    selectionModes: Object.fromEntries((findTable(composition, /^그리드 선택$/)?.rows??[]).map(row=>[plain(row[0]),plain(row[1])])),
    removeGuardField: composition?.body.match(/^행 제거 보호:\s*(.+)$/m)?.[1]?.trim(),
    rowExamples: findTable(composition, /^그리드 예시$/),
    popups: findTable(composition, /^선택창$/),
    commandFields: findTable(composition, /^명령 입력창$/),
    addChoices: findTable(composition, /^추가 방식$/),
    figureNavigation: findTable(composition, /^목업 이동$/),
    masterLink: composition?.body.match(/^목록 연동:\s*(.+)$/m)?.[1]?.trim(),
    masterEditRule: composition?.body.match(/^대상 편집 조건:\s*(.+)$/m)?.[1]?.trim(),
    independentButtons: (composition?.body.match(/^선택 무관 버튼:\s*(.+)$/m)?.[1]??'').split('·').map(s=>s.trim()),
    calculationGuardButtons: (composition?.body.match(/^산정 검증 버튼:\s*(.+)$/m)?.[1]??'').split('·').map(s=>s.trim()).filter(Boolean),
    savedActions: (composition?.body.match(/^저장 후 실행 버튼:\s*(.+)$/m)?.[1]??'').split('·').map(s=>s.trim()).filter(Boolean),
    allocationValidity: (()=>{const value=composition?.body.match(/^배분 유효성:\s*(.+)$/m)?.[1];if(!value)return null;const [field,...rest]=value.split('=');return {field:field.trim(),inputs:rest.join('=').split('·').map(s=>s.trim())};})(),
    addReferences: findTable(composition,/^행 추가 참조$/),
    calculations: (findTable(composition,/^목업 계산$/)?.rows??[]).map(row=>({area:plain(row[0]),target:plain(row[1]),kind:plain(row[2]),a:plain(row[3]),b:plain(row[4]),scale:plain(row[5])})),
    surfaces,
    overview: plain(section(md,/개요/)?.body.split('\n').find(l=>l.includes('무엇을 하는 화면'))??''),
    searchTable: search,
    detailSearchTables: tablesIn(section(md, /조회조건/)?.body ?? '').filter((t) => /조건|검색/.test(plain(t.header[0]))).slice(1),
    search: search?.rows ?? [],
    columns,
    actionTables,
    actions: actionTables.flatMap(t=>t.rows),
    masterDetail: /마스터.?디테일/.test(text) || columns.length > 1,
    vertical: /(상하|위.*아래|상·하)/.test(text),
  };
  for (const extra of [false,true]) {
    const declaration=requestDeclaration(md,extra);
    if(!declaration)continue;
    if(!allowInheritance)throw new Error('요청 원 화면의 재귀 상속은 허용하지 않는다');
    const sourceMd=read(path.join(SCREENS,declaration.path,'requirements.md'));
    if(!sourceMd)throw new Error('요청 원 화면 요건서가 없다');
    model=inheritRequest(model,screenModel(sourceMd,'마스터-디테일(상하)',false),declaration.areas,{mergeMaster:!extra});
  }
  return model;
}

function columnLabel(row) { return plain(row[0] || '컬럼'); }

// ── 골격 ────────────────────────────────────────────────────────────
function scaffold(dir) {
  const md = read(path.join(SCREENS, dir, 'requirements.md'));
  if (!md) {
    console.error(`요건서가 없다: documents/screen/${dir}/requirements.md`);
    process.exit(2);
  }
  const title = (md.match(/^#\s*요건 정의서\s*—\s*(.+)$/m) || [, dir])[1].trim();
  const rows = meta(md);
  const pathRow = rows.find((r) => /페이지 경로|라우트|자리/.test(r[0]));
  const where = pathRow ? pathRow[1] : `/${dir}`;
  const typeRow = rows.find((r) => /화면 유형/.test(r[0]));
  const type = typeRow ? typeRow[1] : '';
  const model = screenModel(md, type);
  model.screenPath=dir;
  const tc = testcases(md);
  const keys = manualKeys(dir);
  const open = section(md, /미결/);

  const L = [];
  L.push('<!doctype html>');
  L.push('<meta charset="utf-8">');
  L.push(`<title>목업 · ${title} (${where.replace(/[`]/g, '')})</title>`);
  L.push(styleBlock());
  L.push('');
  L.push('<main>');
  L.push('');
  L.push('<!--');
  L.push('  이 파일은 생성물이다 — `/mockup` 이 requirements.md 에서 만든다.');
  L.push('  결정도 테스트 케이스도 요건서가 갖는다. 여기서 고치면 다음 생성에서 사라진다.');
  L.push('-->');
  L.push('');
  L.push(`<h1>${html(title)}</h1>`);
  L.push('<div class="head">');
  L.push('  <dl>');
  for (const [k, v] of rows) L.push(`    <dt>${html(k)}</dt><dd>${html(v)}</dd>`);
  L.push('    <dt>요건서</dt><dd><a href="requirements.md">requirements.md</a></dd>');
  if (read(path.join(SCREENS, dir, 'manual.md'))) {
    L.push('    <dt>메뉴얼</dt><dd><a href="manual.md">manual.md</a></dd>');
  }
  L.push('  </dl>');
  L.push('</div>');
  L.push('');
  const figures = model.figures.length ? model.figures : keys.length ? keys : [{ key: 'main', caption: '기본 상태' }];
  const book=annotationBook();
  const sharedAnnotator=()=>({badge:(...args)=>book.badge(...args),render:()=>''});
  const renderFigure = (figure,options={}) => model.figures.length
    ? declaredScreen(title, model, figure, { annotator:sharedAnnotator, plain, manualKey: keys.some((k) => k.key === figure.key), ...options })
    : genericScreen(title, model, figure, { annotator:sharedAnnotator, manualKey:keys.some(k=>k.key===figure.key),...options});
  const areas=[...new Set(model.columns.slice(1).filter(t=>surfaceOf(model,areaTitle(t))?.kind!=='탭 내부').map(t=>model.figures.length?areaTitle(t).split(' / ')[0]:areaTitle(t)))];
  const used=new Set([figures[0].key]);
  const walkthrough=areas.map((name,index)=>{
    const candidates=figures.slice(1).filter(f=>f.detail===name&&!/실패|없|충돌|잠금|확인 중|거부/.test(f.caption));
    const chosen=candidates.find(f=>f.editable)??candidates[0];
    const figure=chosen??{...figures[0],key:`area-${index+1}`,caption:name,detail:name};
    return {name,figure:{...figure,stateKey:figure.key,key:`walkthrough-${index+1}`}};
  });
  L.push(`<nav class="walkthrough-nav"><a href="#${figures[0].key}">초기 화면</a>${walkthrough.map(({name,figure})=>`<a href="#${figure.key}">${html(name)}</a>`).join('')}<a href="#element-descriptions">요소 상세 설명</a><a href="#state-examples">상태별 예시</a></nav>`);
  L.push('<h2>1. 초기 화면</h2>');
  L.push(surfaceIntroduction(surfaceOf(model,areaTitle(model.columns[0]??{heading:''}))));
  const initialHtml=renderFigure(figures[0]);
  L.push(initialHtml);
  L.push('<h2>2. 탭·상세 영역별 작업</h2>');
  L.push('<p>같은 상위 대상을 선택한 상태에서 각 영역을 순서대로 설명합니다. 반복되는 상단 목록은 생략했습니다.</p>');
  for(const {name,figure} of walkthrough){
    L.push(`<section class="walkthrough-area" data-walkthrough-area="${html(name)}"><h3>${html(name)}</h3>${surfaceIntroduction(surfaceOf(model,name))}${renderFigure(figure,{compact:true,surfaceName:name})}</section>`);
  }
  const extras=figures.slice(1).filter(f=>!used.has(f.key));
  const extraHtml=extras.map(figure=>`<details class="state-example"><summary>${html(figure.caption)}</summary>${renderFigure(figure)}</details>`).join('');
  const renumber=book.prioritize(initialHtml);
  for(let index=0;index<L.length;index++)L[index]=renumber(L[index]);
  L.push(book.render());
  const rules=section(md,/규칙.*저장/);
  if(rules){
    L.push('<details id="shared-rules" class="state-example"><summary>공통 검증·실행 규칙 — 요건서 §6</summary>');
    let rows=[];
    const flush=()=>{if(rows.length){const parsed=mdTable(rows.join('\n'));L.push(`<table>${parsed.map((r,i)=>`<tr>${r.map(c=>`<${i?'td':'th'}>${html(c)}</${i?'td':'th'}>`).join('')}</tr>`).join('')}</table>`);rows=[];}};
    for(const line of rules.body.split('\n')){if(line.startsWith('|'))rows.push(line);else{flush();if(line.trim())L.push(`<p>${html(line.replace(/^#{1,6} /,''))}</p>`);}}flush();
    L.push('</details>');
  }
  L.push(`<section id="state-examples"><h2>상태별 예시</h2><p>빈 목록·권한·검증 오류 등의 예시는 필요한 경우 펼쳐 확인합니다. 같은 항목의 설명은 위 목록을 함께 사용합니다.</p>${renumber(extraHtml)||'<p>별도 상태 예시 없음</p>'}</section>`);
  L.push('');

  // ── 테스트 케이스 — 요건서에서 그대로 옮긴다 ──
  const n = 3;
  L.push(`<h2>${n}. 테스트 케이스 — 위 화면들의 주장을 전부 덮는다</h2>`);
  L.push('');
  L.push('<p style="font-size:12.5px;margin:0 0 10px">');
  L.push(`  <b>이 표는 요건서 §${tc?.number ?? '?'} 를 그대로 옮긴 것이다</b> — 고칠 곳은 요건서다.`);
  L.push('  <b>화면은 <i>"왜 이렇게 그렸는가"</i> 를 말하고 여기는 <i>"그것이 지켜졌는가"</i> 를 묻는다.</b>');
  L.push('</p>');
  L.push('');
  for (const g of tc?.groups ?? []) {
    L.push(`<h3>${html(g.title)}</h3>`);
    L.push('<table>');
    L.push('  <thead><tr><th>#</th><th>조작 · 상황</th><th>기대</th></tr></thead>');
    L.push('  <tbody>');
    for (const r of g.rows) {
      L.push(`    <tr><td class="c">${html(r[0]).replace(/<\/?code>/g, '')}</td><td>${html(r[1])}</td><td>${html(r[2])}</td></tr>`);
    }
    L.push('  </tbody>');
    L.push('</table>');
    L.push('');
  }

  // ── 확인 목록 — 요건서의 미결을 그대로 보인다 ──
  L.push(`<h2>${n + 1}. 이 목업으로 확인할 것</h2>`);
  L.push('');
  L.push('<div class="todo">');
  L.push('  <b>결정하면 요건서에 적는다. 이 파일이 아니라.</b>');
  const items = (open?.body ?? '').split('\n').filter((l) => /^[-*] /.test(l));
  if (items.length) {
    L.push('  <ul>');
    for (const it of items) L.push(`    <li>${html(it.replace(/^[-*]\s*/, ''))}</li>`);
    L.push('  </ul>');
  } else {
    L.push('  <p style="margin:6px 0 0">요건서의 미결이 없다 — 확인할 갈림이 남아 있지 않다.</p>');
  }
  L.push('</div>');
  L.push('');
  L.push('<footer>');
  L.push('  <b>이 파일은 생성물이다</b> — <code>/mockup</code> 이 <code>requirements.md</code> 에서 만든다.');
  L.push('  결정과 테스트 케이스는 요건서가 갖고 여기는 확인용이다. 손으로 고치면 다음 생성에서 사라진다.<br>');
  L.push('  정적 HTML, 프레임워크 없음, 픽셀 충실도는 목표가 아니다.');
  L.push('</footer>');
  L.push('');
  L.push('</main>');
  L.push(searchComboScript());
  L.push('');
  return L.join('\n');
}

// ── 검사 ────────────────────────────────────────────────────────────
function check(dir) {
  const problems = [];
  const mockPath = path.join(SCREENS, dir, 'mockup.html');
  const mock = read(mockPath);
  const md = read(path.join(SCREENS, dir, 'requirements.md'));
  if (!mock) {
    // 목업이 없는 것은 결손이 아니다 — 파생물이라 없는 것이 정상이다(CLAUDE.md §4-5).
    // 다만 메뉴얼이 그림을 부르고 있으면 그때는 만들어야 한다.
    const want = manualKeys(dir);
    return want.length
      ? [MISSING_NEEDED, `메뉴얼이 그림 ${want.length}장을 부른다 — /mockup 으로 먼저 만든다`]
      : [MISSING_OK];
  }

  if (!/<div\b[^>]*class="[^"]*\bscreen\b[^"]*"/i.test(mock)) {
    problems.push('화면 블록이 없다 — 공용 스타일·테스트 표만으로는 목업이 아니다');
  }

  // 같은 요소의 여러 배지는 한 설명을 공유하며 설명 자체는 한 번만 낸다.
  const badgeIds = [...mock.matchAll(/data-note="([\w-]+)"/g)].map((m) => m[1]);
  const noteIds = [...mock.matchAll(/\bid="note-([\w-]+)"/g)].map((m) => m[1]);
  if (!badgeIds.length) problems.push('화면 요소를 설명하는 번호 배지가 없다');
  for (const id of badgeIds) if (!noteIds.includes(id)) problems.push(`번호 배지 ${id}의 하단 설명이 없다`);
  for (const id of noteIds) if (!badgeIds.includes(id)) problems.push(`하단 설명 ${id}을 가리키는 화면 번호 배지가 없다`);
  if ([...mock.matchAll(/data-annotation-catalog/g)].length!==1) problems.push('화면 요소 상세 설명 목록은 정확히 한 번 있어야 한다');
  if (new Set(noteIds).size !== noteIds.length) problems.push('같은 하단 설명 id가 중복됐다');
  problems.push(...annotationIssues(mock));

  // ① 화면 계약의 핵심인 컬럼 이름은 목업에서도 그대로 보여야 한다. 일반적인
  // "이름·상태" 헤더로 바뀌면 화면 구조가 맞아 보여도 실제 업무 화면은 틀린다.
  if (md) {
    const type = meta(md).find((r) => /화면 유형/.test(r[0]))?.[1] ?? '';
    const model = screenModel(md, type);
    if (dir.split(path.sep).join('/').startsWith('admin/hub/') && [...model.figures.flatMap(figure=>figure.buttons),...model.surfaces.flatMap(surface=>surface.buttons)].some(name=>['수정','편집 취소'].includes(name))) {
      problems.push('그리드 툴바에 수정/편집 취소 버튼을 두지 않는다 — 셀 더블 클릭 편집과 상태 컬럼의 행별 되돌리기를 사용한다');
    }
    model.screenPath=dir;
    if(model.surfaces.length){
      const names=model.columns.map(areaTitle);
      if(new Set(model.surfaces.map(s=>s.name)).size!==model.surfaces.length)problems.push('영역별 작업 표의 영역 이름이 중복됐다');
      for(const name of names)if(!surfaceOf(model,name))problems.push(`컬럼 영역 '${name}'의 관리 대상·작업·버튼 계약이 없다`);
      for(const surface of model.surfaces)if(!names.includes(surface.name))problems.push(`영역 '${surface.name}'의 컬럼 표가 없다`);
    }
    const expected = model.columns.flatMap((t) => t.rows.map(columnLabel));
    const shown = [...mock.matchAll(/<th\b[^>]*>([\s\S]*?)<\/th>/gi)]
      .map((m) => plain(m[1].replace(/<[^>]+>/g, '')));
    shown.push(...[...mock.matchAll(/data-field-label="([^"]+)"/g)].map((m) => m[1]));
    for (const figure of model.figures) {
      if (!mock.includes(`id="${figure.key}"`)) problems.push(`요건서의 목업 상태 ${figure.key}가 없다`);
    }
    if (model.figures.length && !mock.includes(styleBlock())) problems.push('공통 목업 스타일이 서식과 다르다 — 재생성한다');
    const missingColumns = expected.filter((label) => !shown.some((head) => head.includes(label)));
    if (missingColumns.length) {
      problems.push(`요건서 컬럼이 목업 헤더에 없다 ${missingColumns.length}건: ${missingColumns.slice(0, 8).join(' · ')}`);
    }
  }

  // ② 메뉴얼이 부르는 그림 키와 짝이 맞나
  const want = manualKeys(dir).map((k) => k.key);
  const have = [...mock.matchAll(/data-manual="([\w-]+)"/g)].map((m) => m[1]);
  for (const k of want) if (!have.includes(k)) problems.push(`메뉴얼이 #${k} 를 부르는데 그 그림이 없다`);
  for (const k of have) if (!want.includes(k)) problems.push(`data-manual="${k}" 를 부르는 메뉴얼 줄이 없다`);

  // ③ 테스트 케이스가 요건서와 같은가 — 목업이 케이스를 몰래 늘리지 못하게 한다
  const tc = md ? testcases(md) : null;
  const wantIds = (tc?.groups ?? []).flatMap((g) => g.rows.map((r) => r[0].replace(/`/g, '')));
  const haveIds = [...mock.matchAll(/<td class="c">([^<]+)<\/td>/g)].map((m) => m[1].trim());
  const missing = wantIds.filter((x) => !haveIds.includes(x));
  const extra = haveIds.filter((x) => !wantIds.includes(x));
  if (missing.length) problems.push(`요건서에 있고 목업에 없는 케이스 ${missing.length}건: ${missing.slice(0, 8).join(' ')}`);
  if (extra.length) problems.push(`목업에만 있는 케이스 ${extra.length}건: ${extra.slice(0, 8).join(' ')} — 요건서로 옮긴다`);

  // ④ 한 파일로 완결되나
  if (/<link[^>]+rel=["']?stylesheet/i.test(mock)) problems.push('외부 스타일시트를 부른다 — 한 파일로 완결해야 한다');
  if (/<script[^>]+src=/i.test(mock)) problems.push('외부 스크립트를 부른다 — 한 파일로 완결해야 한다');
  if (/<img[^>]+src=["']?https?:/i.test(mock)) problems.push('외부 이미지를 부른다 — 한 파일로 완결해야 한다');

  // ⑤ 생성물임을 밝히나
  if (!/생성물이다/.test(mock)) problems.push('생성물 표식이 없다 — 손편집을 부른다');
  return problems;
}

// ── 진입 ────────────────────────────────────────────────────────────
const args = process.argv.slice(2);

if (args.includes('--list')) {
  const all = allScreens();
  console.log(`${'화면'.padEnd(34)} 요건서  목업  메뉴얼  그림키`);
  for (const s of all) {
    const has = (f) => (fs.existsSync(path.join(SCREENS, s, f)) ? '  ●  ' : '  ·  ');
    console.log(`${s.padEnd(34)}${has('requirements.md')}${has('mockup.html')}${has('manual.md')}   ${manualKeys(s).length || '·'}`);
  }
  console.log(`\n화면 ${all.length}`);
  process.exit(0);
}

if (args.includes('--check')) {
  const rest = args.filter((a) => a !== '--check');
  const targets = rest.length ? rest.map(resolveScreen) : allScreens();
  let bad = 0;
  let none = 0;
  const needed = [];
  for (const s of targets) {
    const ps = check(s);
    if (ps[0] === MISSING_OK) {
      none++;
      if (rest.length) console.log(`· ${s} — 목업이 없다. 파생물이라 정상이며 메뉴얼도 그림을 부르지 않는다`);
      continue;
    }
    if (ps[0] === MISSING_NEEDED) {
      none++;
      needed.push(s);
      console.log(`△ ${s} — ${ps[1]}`);
      continue;
    }
    if (!ps.length) {
      if (rest.length) console.log(`● ${s} — 이상 없다`);
      continue;
    }
    bad++;
    console.log(`★ ${s}`);
    for (const p of ps) console.log(`    ${p}`);
  }
  const have = targets.length - none;
  console.log(`
화면 ${targets.length} — 목업 있음 ${have}(어긋남 ${bad}) · 없음 ${none}`);
  if (needed.length) {
    console.log(`△ 메뉴얼이 그림을 부르는데 목업이 없는 화면 ${needed.length} — /manual-site 앞에 만든다`);
  }
  console.log('목업이 없는 것은 결손이 아니다 — 요건서에서 언제든 만든다(CLAUDE.md §4-5).');
  process.exit(bad ? 1 : 0);
}

if (args.includes('--write')) {
  const rest = args.filter((a) => a !== '--write');
  if (rest.length !== 1) {
    console.error('--write 는 화면 하나만 받는다. 생성 내용을 확인한 뒤 화면별로 기록한다.');
    process.exit(2);
  }
  const dir = resolveScreen(rest[0]);
  fs.writeFileSync(path.join(SCREENS, dir, 'mockup.html'), scaffold(dir), 'utf8');
  console.log(`목업 생성 — documents/screen/${dir}/mockup.html`);
  process.exit(0);
}

if (args.includes('--write-existing')) {
  const targets=allScreens().filter(dir=>fs.existsSync(path.join(SCREENS,dir,'mockup.html')));
  for(const dir of targets)fs.writeFileSync(path.join(SCREENS,dir,'mockup.html'),scaffold(dir),'utf8');
  console.log(`기존 목업 ${targets.length}개 재생성`);
  process.exit(0);
}

if (!args.length) {
  console.error('화면을 하나 받는다. --list 로 목록을, --check 로 검사를 한다.');
  process.exit(2);
}
process.stdout.write(scaffold(resolveScreen(args[0])));
