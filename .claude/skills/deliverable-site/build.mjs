/**
 * 세 고객 제출 사이트를 하나의 오프라인 폴더로 조립한다.
 * 외부 CDN 없이 file://에서 트리·검색·테마·현재 문서 인쇄가 동작한다.
 */
import {
  copyFileSync, cpSync, existsSync, mkdirSync, readFileSync, readdirSync,
  rmSync, statSync, writeFileSync,
} from 'node:fs';
import { createHash } from 'node:crypto';
import { createRequire } from 'node:module';
import { dirname, extname, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { parseYaml } from '../../hooks/lib.mjs';
import { loadSpecSet } from '../../../aidd-guide/harness/runtime/spec-set.mjs';
import { FIGURE_LINE, escapeHtml, renderMarkdown } from '../manual-site/lib/markdown.mjs';
import { MAIN, figuresOf } from '../manual-site/lib/figure.mjs';
import { menuTree, read as readRepo, REPO, screensOf } from '../manual-site/lib/menu.mjs';
import { themeAware } from '../manual-site/lib/page.mjs';
import { buildFlowViews } from './lib/flow-view.mjs';
import { createDesignNavigation } from './lib/design-navigation.mjs';
import { historyViewMarkdown } from './lib/history-view.mjs';
import { inheritedHistoryMockup } from '../mockup/scaffold.mjs';
import { richTextBrowserAssets } from '../mockup/rich-text.mjs';
import { preparedMockupErrors } from './lib/prepared-mockups.mjs';
import { orderByAdminMenu } from '../doc-gen/menu-contract.mjs';
import { checkTableDocuments, checkSequenceDocuments } from '../doc-gen/gen.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));
const ASSET_SOURCE = resolve(REPO, 'aidd-guide/artifact/site-kit/assets');
const args = process.argv.slice(2);
const mode = args.find((arg) => !arg.startsWith('--')) ?? 'all';
const release = args.includes('--release');
const pdf = args.includes('--pdf');
const standalone = args.includes('--standalone');
const validModes = new Set(['all', 'design', 'user', 'operations']);
if (!validModes.has(mode)) {
  console.error(`대상은 all · design · user · operations 중 하나다 — 받은 값: ${mode}`);
  process.exit(2);
}
if (standalone && mode === 'all') {
  console.error('--standalone은 design · user · operations 중 하나와 함께 쓴다.');
  process.exit(2);
}
const OUT = resolve(REPO, standalone ? `build/deliverables-${mode}` : 'build/deliverables');
const BUILD_README = resolve(REPO, 'aidd-guide/artifact/site-kit/BUILD-README.txt');
const keys = mode === 'all' ? ['design', 'user', 'operations'] : [mode];
// 낡은 테이블 문서를 다시 포장하지 않는다. 검사는 출력 파일을 쓰기 전에 끝낸다.
let schemaInputs = [];
try { schemaInputs = keys.includes('design') ? [...checkTableDocuments(), ...checkSequenceDocuments()] : []; }
catch (error) { console.error(error.message); process.exit(1); }

const config = parseYaml(readFileSync(resolve(REPO, 'documents/delivery.yaml'), 'utf8'));
const siteConfig = new Map((config.sites ?? []).map((site) => [site.key, site]));
const loadedScreens = loadSpecSet({ repo: REPO, name: '60-screens', parseYaml });
const screenSpec = loadedScreens.document;
const specScreens = screenSpec.screens ?? [];
const byRoute = new Map(specScreens.filter((screen) => screen.route).map((screen) => [screen.route, screen]));
const byDoc = new Map(specScreens.filter((screen) => screen.current_doc).map((screen) => [norm(screen.current_doc), screen]));
const issues = [];
const sourceFiles = new Set(['documents/delivery.yaml', ...loadedScreens.files, ...schemaInputs]);
const mockups = {};
const captureRows = [];

// 파생 목업이 로컬에 없다는 이유로 기존 검토 화면을 빈 안내문으로 교체하지 않는다.
if (keys.includes('design')) {
  const missingMockups = preparedMockupErrors({ repo: REPO, screens: specScreens });
  if (missingMockups.length) {
    console.error(missingMockups.join('\n'));
    process.exit(1);
  }
}

if (!/^DLV-\d{3}$/.test(config.meta?.id ?? '')) {
  issues.push({ site: 'all', level: 'error', message: `잘못된 제출 구성 ID: ${config.meta?.id ?? '(없음)'}` });
}
const siteIds = new Set();
for (const site of config.sites ?? []) {
  if (!/^SITE-\d{3}$/.test(site.id ?? '')) issues.push({ site: 'all', level: 'error', message: `잘못된 사이트 ID: ${site.id ?? '(없음)'}` });
  if (siteIds.has(site.id)) issues.push({ site: 'all', level: 'error', message: `중복 사이트 ID: ${site.id}` });
  siteIds.add(site.id);
}
for (const key of validModes) {
  if (key !== 'all' && !siteConfig.has(key)) issues.push({ site: 'all', level: 'error', message: `사이트 구성이 없다: ${key}` });
}

function norm(value) { return String(value).replace(/\\/g, '/').replace(/^\.\//, ''); }
function repoPath(path) { return resolve(REPO, path); }
function read(path) { sourceFiles.add(norm(path)); return readFileSync(repoPath(path), 'utf8'); }
function exists(path) { return existsSync(repoPath(path)); }
function ensure(path) { mkdirSync(path, { recursive: true }); }
function write(path, content) { ensure(dirname(path)); writeFileSync(path, content, 'utf8'); }
function slug(value) {
  return String(value).normalize('NFKD').toLowerCase().replace(/[^a-z0-9가-힣]+/g, '-').replace(/^-|-$/g, '');
}
function walk(path, suffix = '.md') {
  const root = repoPath(path);
  if (!existsSync(root)) return [];
  const out = [];
  const visit = (dir) => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const full = join(dir, entry.name);
      if (entry.isDirectory()) visit(full);
      else if (entry.name.endsWith(suffix)) out.push(norm(relative(REPO, full)));
    }
  };
  visit(root);
  return out.sort((a, b) => a.localeCompare(b, 'ko'));
}
function stripFrontmatter(markdown) {
  return markdown.replace(/^---\s*\n[\s\S]*?\n---\s*\n/, '').replace(/<!--\s*이 문서는 생성물입니다[\s\S]*?-->/, '');
}
function titleOf(markdown, fallback) {
  return stripFrontmatter(markdown).match(/^#\s+(.+)$/m)?.[1]?.replace(/[*`]/g, '') ?? fallback;
}
function sourceMeta(path, extra = []) {
  if (release) return '';
  return `<div class="source-meta review-only"><span>원본 ${escapeHtml(path)}</span>${extra.map((v) => `<span>${escapeHtml(v)}</span>`).join('')}</div>`;
}
function screenFor(doc, route) { return byDoc.get(norm(doc)) ?? byRoute.get(route) ?? null; }
function screenIdentity(meta, menu) { return meta?.id ?? `UNMAPPED-${menu?.code ?? slug(menu?.url ?? 'screen')}`; }
function captureName(key) { return key === MAIN ? 'screen.png' : `screen-${key}.png`; }
function captureSource(url, key) { return `documents/screen${url}/${captureName(key)}`; }

function collectSharedMockups() {
  const userRoutes = new Set(keys.includes('user')
    ? screensOf(menuTree().roots).filter((menu) => !menu.url.startsWith('/sample')).map((menu) => menu.url)
    : []);
  for (const file of walk('documents/screen', 'mockup.html')) {
    const folder = norm(dirname(file)).replace(/^documents\/screen/, '');
    const forUser = userRoutes.has(folder);
    const forDesign = keys.includes('design') && !folder.startsWith('/sample');
    if (!forUser && !forDesign) continue;
    const req = `${norm(dirname(file))}/requirements.md`;
    const meta = screenFor(req, folder);
    const screenId = screenIdentity(meta, { url: folder, code: slug(folder) });
    const { figures, style } = figuresOf(folder, 'mockup');
    if (forUser) {
      for (const [key, figure] of figures) mockups[`${screenId}/${key}`] = { html: figure.html, style: themeAware(style) };
    }
    if (forDesign) {
      const source = read(file);
      const full = source.match(/<main\b[^>]*>([\s\S]*?)<\/main>/i)?.[1];
      if (full) mockups[`${screenId}/__design__`] = { html: full, style: themeAware(style) };
    }
  }
}

function designMockupMarkup(screenId) {
  if (!mockups[`${screenId}/__design__`]) return '<div class="delivery-empty">생성된 목업이 없습니다.</div>';
  return `<div class="design-mockup" data-mockup="${escapeHtml(`${screenId}/__design__`)}"><div class="mockwrap" data-mockup-body></div></div>`;
}

function copyShot(screenId, key, file) {
  const extension = extname(file).toLowerCase();
  const relativeTarget = `assets/screens/${screenId}/${key}${extension}`;
  const target = resolve(OUT, relativeTarget);
  ensure(dirname(target));
  copyFileSync(repoPath(file), target);
  return `../${norm(relativeTarget)}`;
}

function figureMarkup({ site, screenId, screenTitle, url, key, caption, figure, index, total }) {
  const expected = captureSource(url, key);
  const state = figure?.kind === 'png' ? '실제 캡처' : '목업';
  captureRows.push({ site, screenId, screenTitle, key, expected, actual: figure?.kind === 'png' });
  if (!figure) {
    issues.push({ site, level: 'error', message: `${screenId}/${key}: 목업과 캡처가 모두 없다.` });
    return `<div class="delivery-empty">${escapeHtml(caption)} — 화면 그림이 없습니다.</div>`;
  }
  if (figure.file) sourceFiles.add(norm(figure.file));
  if (release && site === 'user' && figure.kind !== 'png') {
    issues.push({ site, level: 'error', message: `${screenId}/${key}: 제출 모드인데 실제 캡처 ${expected}가 없다.` });
  }
  const visual = figure.kind === 'png'
    ? `<img src="${escapeHtml(copyShot(screenId, key, figure.file))}" alt="${escapeHtml(caption)}">`
    : `<div class="mockwrap" data-mockup-body></div>`;
  const mockupAttr = figure.kind === 'mockup' ? ` data-mockup="${escapeHtml(`${screenId}/${key}`)}"` : '';
  const note = release ? '' : `<div class="review-note review-only">
<b>${escapeHtml(screenId)} · 그림 ${index}/${total} · ${escapeHtml(key)}</b><br>
현재 출처: <span class="${figure.kind === 'png' ? 'capture-ok' : 'capture-pending'}">${state}</span><br>
대체 파일: <code>${escapeHtml(expected)}</code>
</div>`;
  return `<figure class="shot ${figure.kind === 'mockup' ? 'mock' : 'capture'}"${mockupAttr}>
${visual}
<figcaption>${escapeHtml(caption)}${figure.kind === 'mockup' ? ' — 배치를 보이는 목업입니다.' : ''}</figcaption>
${note}
</figure>`;
}

function markdownChapter({ id, group, path, title, extraHtml = '', markdown, tabs = null }) {
  const source = markdown ?? read(path);
  return { id, group, path, title: title ?? titleOf(source, path), markdown: source, extraHtml, tabs };
}

function designChapters() {
  const chapters = [];
  const loadedFeatures = loadSpecSet({ repo: REPO, name: '31-features', parseYaml });
  loadedFeatures.files.forEach((file) => sourceFiles.add(file));
  const features = new Map((loadedFeatures.document.features ?? []).map((feature) => [feature.id, feature]));
  const navigation = createDesignNavigation(siteConfig.get('design').navigation, features, parseYaml);
  const addDocument = (path, prefix) => {
    const chapter = markdownChapter({ id: `${prefix}-${slug(path)}`, path });
    const { treePath, order } = navigation(path, chapter.markdown);
    chapters.push({ ...chapter, group: treePath.join(' › '), treePath, navigationOrder: order });
  };
  for (const path of walk('documents/generated')) {
    if (path.endsWith('/_index.md') || path.includes('/80-운영런북') || path.endsWith('80-운영런북.md')) continue;
    addDocument(path, 'design');
  }
  for (const path of walk('documents/design')) {
    addDocument(path, 'detail');
  }
  chapters.sort((a, b) => a.navigationOrder - b.navigationOrder
    || a.group.localeCompare(b.group, 'ko') || a.path.localeCompare(b.path, 'ko', { numeric: true }));
  const { roots } = menuTree();
  const ordered = screensOf(roots).filter((menu) => !menu.url.startsWith('/sample'));
  const menuByRoute = new Map(ordered.map((menu) => [menu.url, menu]));
  const requirementPaths = walk('documents/screen').filter((value) => value.endsWith('/requirements.md') && !value.includes('/sample/'));
  requirementPaths.sort((left, right) => {
    const leftRoute = norm(dirname(left)).replace(/^documents\/screen/, '');
    const rightRoute = norm(dirname(right)).replace(/^documents\/screen/, '');
    const li = ordered.findIndex((menu) => menu.url === leftRoute);
    const ri = ordered.findIndex((menu) => menu.url === rightRoute);
    return (li < 0 ? Number.MAX_SAFE_INTEGER : li) - (ri < 0 ? Number.MAX_SAFE_INTEGER : ri)
      || left.localeCompare(right, 'ko');
  });
  for (const path of requirementPaths) {
    const folder = norm(dirname(path)).replace(/^documents\/screen/, '');
    const meta = screenFor(path, folder);
    const screenId = screenIdentity(meta, { url: folder, code: slug(folder) });
    const menu = menuByRoute.get(folder);
    const moduleLabel = siteConfig.get('design').navigation?.module_labels?.[meta?.module] ?? meta?.module;
    const plannedPath = meta?.entry === 'menu' && moduleLabel ? [moduleLabel, ...(meta.group ? [meta.group] : [])] : ['공통 화면'];
    const treePath = ['화면 요건·목업', ...(menu?.path.slice(0, -1) ?? plannedPath)];
    const sequencePaths = (meta?.usecases ?? []).map((id) => ({ id, path: `documents/generated/33-시퀀스/${id}.md` }))
      .filter((item) => existsSync(repoPath(item.path)));
    chapters.push({ ...markdownChapter({
      id: `screen-${slug(screenId)}`, group: treePath.join(' › '), path,
      title: `${screenId} ${meta?.title ?? titleOf(read(path), '')}`,
      tabs: [
        { key: 'requirements', label: '요건 정의서', markdown: read(path) },
        { key: 'mockup', label: '화면 목업', rendered: designMockupMarkup(screenId) },
        ...sequencePaths.map((item) => ({ key: `sequence-${item.id}`, label: sequencePaths.length === 1 ? '처리 시퀀스' : `처리 시퀀스 ${item.id}`,
          markdown: read(item.path), path: item.path })),
      ],
    }), treePath, screenId });
  }
  for (const screen of specScreens.filter((item) => item.view_kind === 'change_history')) {
    const source = specScreens.find((item) => item.id === screen.source_screen);
    const historyMarkdown=historyViewMarkdown(screen,source);
    let tabs=null;
    if(screen.mockup_projection){
      const sourcePath=`documents/screen${source.route}/requirements.md`;
      mockups[`${screen.id}/__design__`]=inheritedHistoryMockup(read(sourcePath),screen);
      sourceFiles.add(sourcePath);
      tabs=[{key:'requirements',label:'요건 정의서',markdown:historyMarkdown},{key:'mockup',label:'화면 목업',rendered:designMockupMarkup(screen.id)}];
    }
    const moduleLabel = siteConfig.get('design').navigation?.module_labels?.[screen.module] ?? screen.module;
    const treePath = ['화면 요건·목업', moduleLabel, screen.group ?? '변경 이력'];
    chapters.push({ ...markdownChapter({
      id: `screen-${slug(screen.id)}`, group: treePath.join(' › '),
      path: `documents/spec/60-screens/010-change-history.yaml#${screen.id}`,
      title: `${screen.id} ${screen.title}`, markdown: historyMarkdown, tabs,
    }), treePath, screenId: screen.id });
  }
  return orderByAdminMenu(screenSpec, chapters, (chapter) => chapter.screenId);
}

function userChapters() {
  for (const path of walk('sources/backend/bootstrap/src/main/resources/db/changelog/data', '.csv')) {
    if (path.endsWith('/003-init-menu.csv') || path.endsWith('-init-message.csv')) sourceFiles.add(path);
  }
  const { roots } = menuTree();
  const chapters = [];
  for (const menu of screensOf(roots)) {
    if (menu.url.startsWith('/sample')) continue;
    const path = `documents/screen${menu.url}/manual.md`;
    if (!exists(path)) {
      issues.push({ site: 'user', level: release ? 'error' : 'warning', message: `${menu.url}: manual.md가 없다.` });
      continue;
    }
    const meta = byRoute.get(menu.url) ?? byDoc.get(`documents/screen${menu.url}/requirements.md`);
    const screenId = screenIdentity(meta, menu);
    const source = read(path);
    const mockupOnly = figuresOf(menu.url, 'mockup').figures;
    const actual = figuresOf(menu.url, 'auto').figures;
    const requested = [...source.matchAll(new RegExp(FIGURE_LINE.source, 'gm'))].map((match) => ({ caption: match[1], key: match[2] }));
    const slots = requested.length ? requested : [{ caption: menu.name, key: MAIN }];
    let slot = 0;
    const html = renderMarkdown(stripFrontmatter(source), {
      figure: (key, caption) => {
        slot += 1;
        return figureMarkup({ site: 'user', screenId, screenTitle: menu.name, url: menu.url, key, caption,
          figure: actual.get(key) ?? mockupOnly.get(key), index: slot, total: slots.length });
      },
    });
    let body = html;
    if (requested.length === 0) {
      const figure = actual.get(MAIN) ?? mockupOnly.get(MAIN);
      const visual = figureMarkup({ site: 'user', screenId, screenTitle: menu.name, url: menu.url, key: MAIN,
        caption: menu.name, figure, index: 1, total: 1 });
      body = `${visual}\n${html}`;
    }
    const declared = new Set(requested.map((item) => item.key));
    for (const key of mockupOnly.keys()) {
      if (requested.length && !declared.has(key)) issues.push({ site: 'user', level: 'warning', message: `${screenId}/${key}: 목업 그림을 manual.md가 부르지 않는다.` });
    }
    chapters.push({
      id: `manual-${slug(screenId)}`, group: menu.path.slice(0, -1).join(' › ') || '사용자 메뉴얼', path,
      treePath: ['사용자 메뉴얼', ...menu.path.slice(0, -1)],
      title: `${screenId} ${menu.name}`, markdown: null, rendered: body,
    });
  }
  if (!release && captureRows.length) {
    const rows = captureRows.filter((row) => row.site === 'user').map((row) =>
      `<tr><td>${row.actual ? '✅' : '◇'}</td><td>${escapeHtml(row.screenId)}</td><td>${escapeHtml(row.screenTitle)}</td><td><code>${escapeHtml(row.key)}</code></td><td><code>${escapeHtml(row.expected)}</code></td></tr>`).join('');
    chapters.unshift({ id: 'capture-status', group: '검토', treePath: ['검토'], path: 'generated:capture-status', title: '실제 화면 캡처 현황', markdown: null,
      rendered: `<h1>실제 화면 캡처 현황</h1><p>◇는 현재 목업이며 표시된 파일을 넣고 다시 생성하면 같은 자리가 실제 화면으로 바뀝니다.</p><table class="capture-summary"><thead><tr><th>상태</th><th>화면 ID</th><th>화면</th><th>그림 키</th><th>대체 파일</th></tr></thead><tbody>${rows}</tbody></table>` });
  }
  return chapters;
}

function operationsChapters() {
  const chapters = [];
  const runbook = 'documents/generated/80-운영런북.md';
  if (exists(runbook)) chapters.push({ ...markdownChapter({ id: 'operations-runbook', group: '고객 환경 운영 런북', path: runbook }), treePath: ['고객 환경 운영 런북'] });
  for (const path of walk('developer-guide')) {
    chapters.push({ ...markdownChapter({ id: `developer-${slug(path)}`, group: '구축·개발·배포·운영 안내', path }), treePath: ['구축·개발·배포·운영 안내'] });
  }
  return chapters;
}

function rewriteLinks(markdown, from, mapping) {
  return stripFrontmatter(markdown).replace(/\[([^\]]+)\]\(([^)]+)\)/g, (whole, label, target) => {
    if (/^(https?:|mailto:|#)/.test(target) || /mockup\.html#/.test(target)) return whole;
    const [file, ...fragments] = target.split('#');
    const fragment = fragments.join('#');
    const resolved = norm(relative(REPO, resolve(dirname(repoPath(from)), file)));
    if (mapping.has(resolved)) return `[${label}](#${mapping.get(resolved)}${fragment ? `?anchor=${encodeURIComponent(fragment)}` : ''})`;
    return `${label} (\`${target}\`)`;
  });
}

function renderTabs(chapter, mapping) {
  const tabs = chapter.tabs ?? [];
  const buttons = tabs.map((tab, index) => {
    const selected = index === 0;
    return `<button class="document-tab" id="${chapter.id}-tab-${tab.key}" data-document-tab="${tab.key}" type="button" role="tab" aria-selected="${selected}" aria-controls="${chapter.id}-panel-${tab.key}" tabindex="${selected ? '0' : '-1'}">${escapeHtml(tab.label)}</button>`;
  }).join('');
  const panels = tabs.map((tab, index) => {
    const body = tab.rendered ?? renderMarkdown(rewriteLinks(tab.markdown, tab.path ?? chapter.path, mapping));
    return `<section class="document-tab-panel" id="${chapter.id}-panel-${tab.key}" data-document-panel="${tab.key}" role="tabpanel" aria-labelledby="${chapter.id}-tab-${tab.key}"${index === 0 ? '' : ' hidden'}>${body}</section>`;
  }).join('');
  return `<div class="document-tabs" data-document-tabs><div class="document-tab-list" role="tablist" aria-label="화면 설계 보기">${buttons}</div>${panels}</div>`;
}

function renderTree(chapters, siteTitle, collapseDepth = Infinity) {
  const root = { children: new Map(), pages: [] };
  for (const chapter of chapters) {
    let node = root;
    for (const label of chapter.treePath ?? [chapter.group]) {
      if (!node.children.has(label)) node.children.set(label, { children: new Map(), pages: [] });
      node = node.children.get(label);
    }
    node.pages.push(chapter);
  }
  const branch = (node, depth = 0) => [
    ...[...node.children].map(([label, child]) => `<li class="tree-branch${depth >= collapseDepth ? ' collapsed' : ''}" data-tree-depth="${depth}"><button class="tnode group" data-tree-group type="button" aria-expanded="${depth < collapseDepth}"><svg class="icon chev"><use href="#i-chevron-down"/></svg><span class="label">${escapeHtml(label)}</span></button><ul>${branch(child, depth + 1)}</ul></li>`),
    ...node.pages.map((chapter) => {
      const documentPath = [siteTitle, ...(chapter.treePath ?? [chapter.group]), chapter.title].join(' › ');
      return `<li><button class="tnode empty-chev" data-page-link="${chapter.id}" data-page-path="${escapeHtml(documentPath)}" type="button"><span class="label">${escapeHtml(chapter.title)}</span></button></li>`;
    }),
  ].join('');
  return branch(root);
}

function renderSite(key, chapters) {
  const cfg = siteConfig.get(key) ?? { title: key, description: '' };
  const mapping = new Map(chapters.filter((chapter) => !chapter.path.startsWith('generated:')).map((chapter) => [norm(chapter.path), chapter.id]));
  const tree = renderTree(chapters, cfg.title, key === 'design' ? 1 : Infinity);
  const bodies = chapters.map((chapter) => {
    const body = chapter.tabs
      ? renderTabs(chapter, mapping)
      : chapter.rendered ?? renderMarkdown(rewriteLinks(chapter.markdown, chapter.path, mapping));
    return `<section class="delivery-page" data-page id="${chapter.id}" hidden><article>${sourceMeta(chapter.path)}${body}${chapter.extraHtml ?? ''}</article></section>`;
  }).join('\n');
  const html = `<!doctype html>
<html lang="ko"${release ? ' class="release"' : ''}>
<head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="color-scheme" content="light dark"><title>${escapeHtml(cfg.title)}</title>
<link rel="stylesheet" href="../assets/guide.css"><link rel="stylesheet" href="../assets/delivery.css">
<script src="../assets/mockup-mismatch-delete.js" defer></script>
<script src="../assets/icons.js" defer></script><script src="../assets/mermaid.min.js" defer></script><script src="../assets/mockups.js" defer></script><script src="../assets/mockup-math.js" defer></script><script src="../assets/mockup-relation.js" defer></script><script src="../assets/mockup-api.js" defer></script><script src="../assets/mockup-schema.js" defer></script><script src="../assets/mockup-query-example.js" defer></script><script src="../assets/mockup-query-policy.js" defer></script><script src="../assets/mockup-marked.js" defer></script><script src="../assets/mockup-purify.js" defer></script><script src="../assets/mockup-rich-text.js" defer></script><script src="../assets/mockup-prompt-rule.js" defer></script><script src="../assets/mockup-help.js" defer></script><script src="../assets/mockup-controls.js" defer></script><script src="../assets/delivery.js" defer></script></head>
<body class="${release ? 'release' : 'review'}"><header class="topbar"><button class="icon-btn menu-toggle" data-menu-toggle type="button" aria-label="목차 열기" aria-expanded="false"><svg class="icon"><use href="#i-menu"/></svg></button><a class="brand" href="../index.html"><span class="logo"><svg class="icon sm"><use href="#i-book-open"/></svg></span><span>${escapeHtml(cfg.title)}</span></a><div class="current-path" data-current-path aria-live="polite"></div><div class="spacer"></div><div class="site-actions"><button data-print-page type="button">현재 페이지 인쇄·PDF</button><div class="ui-settings"><button class="icon-btn" data-ui-settings-toggle type="button" aria-label="보기 설정" aria-expanded="false" title="보기 설정"><svg class="icon"><use href="#i-settings"/></svg></button><div class="ui-settings-panel" data-ui-settings-panel hidden><label>본문 너비<select data-ui-width><option value="standard">읽기 편한 너비</option><option value="full">화면 100%</option></select></label><label>글꼴<select data-ui-font><option value="system">시스템 기본</option><option value="gothic">고딕</option><option value="serif">명조</option><option value="mono">고정폭</option></select></label><label>글자 크기<select data-ui-size><option value="90">90%</option><option value="100">100%</option><option value="110">110%</option><option value="120">120%</option><option value="130">130%</option><option value="140">140%</option><option value="150">150%</option></select></label><button data-ui-reset type="button">기본값 복원</button></div></div><button class="icon-btn" data-theme-toggle type="button" aria-label="라이트 테마로 전환" title="테마 전환"><svg class="icon theme-icon-dark"><use href="#i-sun"/></svg><svg class="icon theme-icon-light" hidden><use href="#i-moon"/></svg></button></div></header>
<div class="sidebar-backdrop"></div><div class="layout"><aside class="sidebar" id="sidebar"><div class="sb-head">목차</div><div class="sb-filter"><input data-tree-filter type="search" placeholder="제목·본문 검색"></div><nav class="tree"><ul>${tree}</ul></nav></aside><div class="main-wrap"><main class="content">${bodies || '<div class="delivery-empty">표시할 문서가 없습니다.</div>'}</main></div></div><button type="button" class="back-to-top" data-back-to-top aria-label="맨 위로" hidden>↑ 맨 위로</button></body></html>`;
  write(resolve(OUT, key, 'index.html'), html);
}

function rootPage(flowViews) {
  const siteCards = keys.map((key) => siteConfig.get(key)).filter(Boolean).map((site) => `<a class="delivery-home-card" href="${site.key}/index.html"><h2>${escapeHtml(site.title)}</h2><p>${escapeHtml(site.description)}</p></a>`);
  const flowCards = flowViews.map((view) => {
    const title = siteConfig.get('design').flow_views.find((item) => item.key === view.key).title;
    return `<a class="delivery-home-card" href="${escapeHtml(view.path)}"><h2>${escapeHtml(title)}</h2><p>전체 흐름도와 상세 시퀀스, 단계별 설명을 함께 확인합니다.</p></a>`;
  });
  const cards = [...siteCards, ...flowCards].join('');
  const intro = standalone && mode === 'user'
    ? '일반 사용자에게 전달하는 사용자 매뉴얼 전용 오프라인 패키지입니다. 설계 산출물과 운영자 자료는 포함하지 않습니다.'
    : '아래 산출물을 인터넷 연결 없이 열람할 수 있습니다.';
  return `<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>고객 제출 산출물</title><link rel="stylesheet" href="assets/guide.css"><link rel="stylesheet" href="assets/delivery.css"></head><body><main class="content"><article><h1>고객 제출 산출물</h1><p>${intro}</p><div class="delivery-home-grid">${cards}</div></article></main></body></html>`;
}

async function makePdf(chaptersBySite) {
  let chromium;
  try {
    const requireFrontend = createRequire(resolve(REPO, 'sources/frontend/package.json'));
    ({ chromium } = requireFrontend('playwright'));
  }
  catch { throw new Error('--pdf는 선택 의존성 playwright와 chromium이 필요하다. HTML 제출본은 정상 생성됐다.'); }
  const browser = await chromium.launch({ headless: true });
  const files = [];
  try {
    ensure(resolve(OUT, 'pdf'));
    for (const [key, chapters] of Object.entries(chaptersBySite)) {
      ensure(resolve(OUT, 'pdf', key));
      for (const [index, chapter] of chapters.entries()) {
        const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
        await page.goto(`${pathToFileURL(resolve(OUT, key, 'index.html')).href}?print=page#${chapter.id}`, { waitUntil: 'load' });
        await page.evaluate(() => window.DELIVERY_READY);
        await page.emulateMedia({ media: 'print' });
        const filename = `${String(index + 1).padStart(3, '0')}-${slug(chapter.title) || chapter.id}.pdf`;
        const target = resolve(OUT, 'pdf', key, filename);
        await page.pdf({ path: target, format: 'A4', printBackground: true });
        files.push(norm(relative(OUT, target)));
        await page.close();
      }
    }
  } finally { await browser.close(); }
  return files;
}

const expectedRoot = `${resolve(REPO, 'build')}${sep}`;
if (!OUT.startsWith(expectedRoot)) throw new Error(`제출 폴더가 build 밖이다: ${OUT}`);
if (mode === 'all' || standalone) rmSync(OUT, { recursive: true, force: true });
else rmSync(resolve(OUT, mode), { recursive: true, force: true });
ensure(resolve(OUT, 'assets'));
copyFileSync(BUILD_README, resolve(REPO, 'build/README.txt'));
for (const asset of ['guide.css', 'icons.js', 'delivery.css', 'delivery.js', 'mockup-math.js', 'mockup-relation.js', 'mockup-api.js', 'mockup-schema.js', 'mockup-query-example.js', 'mockup-query-policy.js', 'mockup-rich-text.js', 'mockup-prompt-rule.js', 'mockup-help.js', 'mockup-mismatch-delete.js', 'mockup-controls.js']) {
  const source = resolve(ASSET_SOURCE, asset);
  sourceFiles.add(norm(relative(REPO, source)));
  copyFileSync(source, resolve(OUT, 'assets', asset));
}
for(const asset of richTextBrowserAssets()){
  sourceFiles.add(norm(relative(REPO,asset.path)));copyFileSync(asset.path,resolve(OUT,'assets',asset.name));
}
const mermaidAsset = resolve(REPO, 'sources/frontend/node_modules/mermaid/dist/mermaid.min.js');
if (!existsSync(mermaidAsset)) issues.push({ site: 'all', level: 'error', message: '오프라인 Mermaid 자산이 없다. sources/frontend에서 npm install을 실행한다.' });
else copyFileSync(mermaidAsset, resolve(OUT, 'assets/mermaid.min.js'));

collectSharedMockups();

const makers = { design: designChapters, user: userChapters, operations: operationsChapters };
const allChapters = Object.fromEntries(keys.map((key) => [key, makers[key]()]));
write(resolve(OUT, 'assets/mockups.js'), `window.DELIVERY_MOCKUPS=${JSON.stringify(mockups).replace(/<\//g, '<\\/')};\n`);
for (const key of keys) renderSite(key, allChapters[key]);

let flowViews = [];
if (keys.includes('design') && siteConfig.get('design')?.flow_views?.length) {
  const loaded = loadSpecSet({ repo: REPO, name: '10-requirements', parseYaml });
  loaded.files.forEach((file) => sourceFiles.add(file));
  flowViews = buildFlowViews({ views: siteConfig.get('design').flow_views, requirements: loaded.document.requirements,
    repo: REPO, out: OUT, read, write, mermaidAsset });
  const links = flowViews.map((view) => `<p><a href="${view.key}.html">${escapeHtml(view.requirement)} 전체 흐름·시퀀스 연속 보기 ↗</a></p>`).join('');
  const designIndex = resolve(OUT, 'design/index.html');
  write(designIndex, readFileSync(designIndex, 'utf8').replace('<div class="sb-head">목차</div>', `<div class="sb-head">목차</div><div class="sb-filter">${links}</div>`));
}
write(resolve(OUT, 'index.html'), rootPage(flowViews));

let pdfFiles = [];
if (pdf) {
  try { pdfFiles = await makePdf(allChapters); }
  catch (error) { issues.push({ site: 'all', level: 'error', message: error.message }); console.error(error.message); }
}

const selectedIssues = issues.filter((issue) => issue.site === 'all' || keys.includes(issue.site));
const manifest = {
  id: config.meta?.id ?? 'DLV-001', generatedAt: new Date().toISOString(), mode, release, standalone, pdfFiles, flowViews,
  sites: Object.fromEntries(keys.map((key) => [key, { chapters: allChapters[key].length }])),
  sources: [...sourceFiles].sort().map((path) => ({ path, sha256: createHash('sha256').update(readFileSync(repoPath(path))).digest('hex') })),
  issues: selectedIssues,
};
write(resolve(OUT, 'delivery-manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`);
const readmeScope = standalone && mode === 'user'
  ? '이 폴더는 일반 사용자용 사용자 매뉴얼만 포함합니다. 설계 산출물과 운영자 자료는 별도 전체 제출본에서 관리합니다.'
  : `이 폴더에는 ${keys.join(' · ')} 사이트가 포함되어 있습니다.`;
write(resolve(OUT, 'README.txt'), `고객 제출 산출물\r\n==================\r\n${readmeScope}\r\nindex.html을 열면 인터넷 연결 없이 볼 수 있습니다.\r\n각 사이트의 현재 페이지 인쇄·PDF 버튼은 보고 있는 문서만 인쇄합니다.\r\n--pdf 옵션으로 만든 PDF는 pdf/{사이트}/ 아래에 문서별로 저장됩니다.\r\n`);

for (const issue of selectedIssues) console.log(`${issue.level === 'error' ? '오류' : '경고'}: ${issue.message}`);
console.log(`제출 사이트 ${keys.join(' · ')} — ${OUT}`);
for (const key of keys) console.log(`  ${key}: ${allChapters[key].length}장`);
const errors = issues.filter((issue) => issue.level === 'error' && (issue.site === 'all' || keys.includes(issue.site)));
if (errors.length) process.exitCode = 1;
