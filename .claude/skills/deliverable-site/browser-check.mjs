/** 실제 Chromium에서 탐색·설정·목업 격리·Mermaid 렌더링을 검사한다.
 * --model-contracts는 비저장/물리 모델 분류 변경의 집중 재검증이며 전체 화면 순회를 대체하지 않는다.
 */
import { createRequire } from 'node:module';
import { existsSync } from 'node:fs';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { REPO } from '../manual-site/lib/menu.mjs';
import { parseYaml } from '../../hooks/lib.mjs';
import { loadSpecSet } from '../../../aidd-guide/harness/runtime/spec-set.mjs';

const rootArg = process.argv.slice(2).find((arg) => arg.startsWith('--root='))?.slice('--root='.length);
const site = process.argv.slice(2).find((arg) => !arg.startsWith('--')) ?? 'design';
const modelContractFocus = process.argv.includes('--model-contracts');
if (modelContractFocus && site !== 'design') throw new Error('--model-contracts는 design 검사에만 사용한다.');
const root = resolve(REPO, rootArg || 'build/deliverables');
const requireFrontend = createRequire(resolve(REPO, 'sources/frontend/package.json'));
const { chromium } = requireFrontend('playwright');
const channel = process.argv.slice(2).find((arg) => arg.startsWith('--browser='))?.slice(10);
if (channel && !['chrome', 'msedge'].includes(channel)) throw new Error('--browser는 chrome 또는 msedge다.');
const browser = await chromium.launch({ headless: true, ...(channel ? { channel } : {}) });
const errors = [];
if (modelContractFocus) console.log('집중 검사: 비저장/물리 모델 분류와 공통 브라우저 동작. 전체 화면 순회는 별도다.');

try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  page.setDefaultTimeout(20000);
  page.on('pageerror', (error) => errors.push(`브라우저 스크립트 오류: ${error.message}`));
  await page.goto(pathToFileURL(resolve(root, site, 'index.html')).href, { waitUntil: 'load' });
  await page.evaluate(() => window.DELIVERY_READY);
  const openHash = async (id) => {
    await page.evaluate((value) => { location.hash = value; }, id);
    await page.locator(`[data-page-link="${id}"][aria-current="page"]`).waitFor({ state: 'visible' });
    await page.evaluate(() => window.DELIVERY_READY);
  };
  if (site === 'design') {
    const target = await page.locator('[data-page-link]').evaluateAll((nodes) => {
      const link = nodes.find((node) => node.closest('.collapsed'));
      return link ? { id: link.dataset.pageLink, title: link.textContent.trim(), path: link.dataset.pagePath } : null;
    });
    if (!target) errors.push('설계 목차의 하위 그룹이 처음부터 모두 펼쳐져 있다.');
    else {
      const targetLink = page.locator(`[data-page-link="${target.id}"]`);
      const search = page.locator('[data-tree-filter]');
      await search.fill(target.title);
      if (!await targetLink.isVisible()) errors.push('검색 결과의 접힌 상위 경로가 펼쳐지지 않는다.');
      await search.fill('');
      if (await targetLink.isVisible()) errors.push('검색을 지워도 이전 접힘 상태로 돌아가지 않는다.');
      await openHash(target.id);
      if (await page.locator('[data-current-path]').textContent() !== target.path) errors.push('해시 링크의 현재 경로가 새 목차와 다르다.');
      const group = targetLink.locator('xpath=ancestor::li[contains(@class,"tree-branch")][1]').locator(':scope > [data-tree-group]');
      await group.click();
      if (await targetLink.isVisible()) errors.push('목차 그룹 접기가 동작하지 않는다.');
      await group.click();
      if (!await targetLink.isVisible()) errors.push('목차 그룹 펼치기가 동작하지 않는다.');
    }
    const plannedTables = loadSpecSet({ repo: REPO, name: '40-domain', parseYaml }).document.entities
      .filter((entity) => entity.implementation === 'planned' && entity.design_stage === 'physical')
      .map((entity) => entity.table);
    const tablePages = await page.locator('[data-page-link]').evaluateAll((nodes, planned) => nodes
      .filter((node) => node.dataset.pagePath?.includes('테이블 목록·명세서') && / — /.test(node.textContent))
      .map((node) => ({ id: node.dataset.pageLink, path: node.dataset.pagePath,
        logical: node.textContent.includes('물리명 미정'),
        nonpersistent: node.textContent.includes(' (비저장) — '),
        planned: planned.includes(node.textContent.split(' — ')[0].trim()),
      })), plannedTables);
    const physicalPage = tablePages.find((entry) => !entry.logical && !entry.planned && !entry.nonpersistent);
    const plannedPages = tablePages.filter((entry) => entry.planned);
    const logicalPages = tablePages.filter((entry) => entry.logical);
    const nonpersistentPages = tablePages.filter((entry) => entry.nonpersistent);
    const expectedNonpersistent = loadSpecSet({ repo: REPO, name: '40-domain', parseYaml }).document.entities
      .filter((entity) => entity.storage_kind === 'nonpersistent').length;
    if (!physicalPage) errors.push('물리 테이블 상세 명세의 모듈별 목차가 없다.');
    if (plannedPages.length !== plannedTables.length) errors.push('미구현 물리 설계의 테이블 목차 수가 정본과 다르다.');
    if (nonpersistentPages.length !== expectedNonpersistent) errors.push('비저장 모델의 목차 수가 정본과 다르다.');
    const tableChecks = [...(physicalPage ? [physicalPage] : []), ...(modelContractFocus ? [] : [...plannedPages, ...logicalPages]), ...nonpersistentPages];
    let checkedTables = 0;
    console.log(`데이터 모델 순회 시작: ${tableChecks.length}개`);
    for (const tablePage of tableChecks) {
      await openHash(tablePage.id);
      const text = await page.locator(`[data-page][id="${tablePage.id}"]`).innerText();
      const labels = tablePage.nonpersistent
        ? ['비저장 논리 투영 · 테이블 없음', '비저장 계약', '독립 테이블·컬럼·키·HIST·Liquibase changeset의 대상이 아니다.']
        : tablePage.logical
        ? ['논리 설계 · 물리명 미정', '논리 속성', '업무 모델 연결', '아직 설계하지 않았다']
        : ['NULL 허용', '기본값', '키·제약', tablePage.planned ? '설계 초안 · 미구현' : 'Liquibase 변경 근거'];
      for (const label of labels) if (!text.includes(label)) errors.push(`${tablePage.path} 명세 항목 누락: ${label}`);
      if (tablePage.nonpersistent && ['## 물리 컬럼', '## Liquibase 변경 근거'].some((label) => text.includes(label.slice(3)))) errors.push(`${tablePage.path} 비저장 모델에 물리 테이블 명세가 섞였다.`);
      if (await page.locator('[data-current-path]').textContent() !== tablePage.path) errors.push('테이블 명세의 현재 경로가 다르다.');
      checkedTables++;
      if (checkedTables % 20 === 0 || checkedTables === tableChecks.length) console.log(`데이터 모델 순회: ${checkedTables}/${tableChecks.length}`);
    }
    console.log(`데이터 모델 표시: 구현 명세 ${physicalPage ? 1 : 0}개 · 물리 초안 ${plannedPages.length}개 · 논리 초안 ${logicalPages.length}개 · 비저장 ${nonpersistentPages.length}개`);
    const plannedScreens = modelContractFocus ? [] : loadSpecSet({ repo: REPO, name: '60-screens', parseYaml }).document.screens
      .filter((screen) => screen.target_doc && existsSync(resolve(REPO, screen.target_doc)));
    const usecases = loadSpecSet({ repo: REPO, name: '30-usecases', parseYaml }).document.usecases;
    let sequenceTabs = 0, initialTabs = 0, themeRace = null;
    let checkedScreens = 0;
    console.log(`설계 화면 순회 시작: ${plannedScreens.length}개`);
    for (const screen of plannedScreens) {
      const linkId = await page.locator('[data-page-link]').evaluateAll((nodes, id) => nodes.find((node) => node.textContent.trim().startsWith(id + ' '))?.dataset.pageLink, screen.id);
      if (!linkId) { errors.push(`${screen.id}: 설계 화면 목차가 없다.`); continue; }
      await openHash(linkId);
      const article = page.locator(`[data-page][id="${linkId}"]`);
      await article.locator('[data-document-tab="mockup"]').click();
      const mockupFile = screen.target_doc.replace(/requirements\.md$/, 'mockup.html');
      if (existsSync(resolve(REPO, mockupFile))) {
        const mockup = article.locator(`[data-mockup="${screen.id}/__design__"]`);
        if (await mockup.count() !== 1 || !await mockup.locator('.screen').count()) errors.push(`${screen.id}: 생성 목업 식별/화면이 HTML에 연결되지 않았다.`);
        else {
          const initial = mockup.locator('.screen').first();
          const tabs = initial.locator('[data-detail-tab]');
          if (await initial.locator('.tabs > span').count()) errors.push(`${screen.id}: 초기 화면의 탭이 정적 텍스트다.`);
          for (let i = 0; i < await tabs.count(); i++) {
            const name = await tabs.nth(i).getAttribute('data-detail-tab');
            const beforeHash = await page.evaluate(() => location.hash);
            await tabs.nth(i).click({position:{x:8,y:8}});
            const state = await initial.evaluate(node=>({
              selected:[...node.querySelectorAll('[data-detail-tab][aria-selected="true"]')].map(tab=>tab.dataset.detailTab),
              shown:[...node.querySelectorAll('[data-detail-panel]')].filter(panel=>!panel.hidden).map(panel=>panel.dataset.detailPanel),
            }));
            if (state.selected.length !== 1 || state.selected[0] !== name || state.shown.length !== 1 || state.shown[0] !== name
              || await page.evaluate(() => location.hash) !== beforeHash) errors.push(`${screen.id}: 삽입 목업의 '${name}' 탭 전환/문서 격리가 잘못됐다.`);
            initialTabs++;
          }
          const jump = mockup.locator('nav a[href^="#"]').last();
          if (await jump.count()) {
            const beforeHash = await page.evaluate(() => location.hash);
            const targetKey = (await jump.getAttribute('href')).slice(1);
            await jump.click();
            const position = await mockup.locator(`[id="${targetKey}"]`).evaluate(node=>node.getBoundingClientRect().top);
            if (position < 49 || position >= 200 || await page.evaluate(() => location.hash) !== beforeHash) errors.push(`${screen.id}: 목업 상태 링크가 해당 그림으로 이동하지 않거나 사이트 문서 선택을 바꿨다.`);
            await page.locator('[data-back-to-top]').waitFor({state:'visible'});
            await page.locator('[data-back-to-top]').click();
            if (await page.evaluate(() => window.scrollY) !== 0 || await page.locator('[data-back-to-top]').isVisible()) errors.push(`${screen.id}: 맨 위로 버튼의 이동/숨김이 잘못됐다.`);
          }
          const settings = mockup.locator('[data-grid-settings]').first();
          const optionalColumn = settings.locator('input[data-grid-column]:not(:checked)').first();
          if (await optionalColumn.count()) {
            await settings.locator(':scope > summary').click();
            const column = await optionalColumn.getAttribute('data-grid-column');
            const toggle = settings.locator(`input[data-grid-column="${column}"]`);
            await toggle.check();
            const shown = await toggle.evaluate((node, id) => [...node.closest('[data-grid-view]').querySelectorAll('[data-grid-col]')]
              .filter((cell) => cell.dataset.gridCol === id).every((cell) => !cell.hidden), column);
            if (!shown) errors.push(`${screen.id}: HTML 목업의 선택 열이 표시되지 않는다.`);
            await toggle.uncheck();
            const hidden = await toggle.evaluate((node, id) => [...node.closest('[data-grid-view]').querySelectorAll('[data-grid-col]')]
              .filter((cell) => cell.dataset.gridCol === id).every((cell) => cell.hidden), column);
            if (!hidden) errors.push(`${screen.id}: HTML 목업의 선택 열 숨김이 동작하지 않는다.`);
            await settings.locator(':scope > summary').click();
          }
          const viewer = mockup.locator('[data-snapshot-viewer]:visible').first();
          if (await viewer.count()) {
            await viewer.locator(':scope > summary').click();
            if (!await viewer.locator('.modal').isVisible()) errors.push(`${screen.id}: 스냅샷 모달이 열리지 않는다.`);
            await viewer.locator('[data-close-editor]').click();
            if (await viewer.locator('.modal').isVisible()) errors.push(`${screen.id}: 스냅샷 모달이 닫히지 않는다.`);
          }
        }
      }
      for (const id of screen.usecases ?? []) {
        const design = usecases.find((uc) => uc.id === id)?.sequence_design;
        if (!design) continue;
        const button = article.locator(`[data-document-tab="sequence-${id}"]`);
        if (await button.count() !== 1) { errors.push(`${screen.id}/${id}: 화면별 시퀀스 탭이 없다.`); continue; }
        await button.click();
        await page.evaluate(() => window.DELIVERY_READY);
        const panel = article.locator(`[data-document-panel="sequence-${id}"]`);
        if (!await panel.isVisible() || await panel.locator('.mermaid svg').count() !== design.diagrams.length
          || await panel.locator('.mermaid-error').count()) errors.push(`${screen.id}/${id}: 화면별 시퀀스 렌더링이 불완전하다.`);
        const themed = {};
        for (const mode of ['dark', 'light']) {
          if (await page.locator('html').getAttribute('data-theme') !== mode) await page.locator('[data-theme-toggle]').click();
          await page.evaluate(() => window.DELIVERY_READY);
          const mockup = article.locator(`[data-mockup="${screen.id}/__design__"]`);
          if (await mockup.count()) {
            await article.locator('[data-document-tab="mockup"]').click();
            themed[mode] = await mockup.locator('.screen').first().evaluate((node) => ({ background: getComputedStyle(node).backgroundColor, text: getComputedStyle(node).color }));
          } else themed[mode] = {};
          await button.click();
          await page.evaluate(() => window.DELIVERY_READY);
          themed[mode].actor = await panel.locator('.mermaid svg rect.actor').first().evaluate((node) => getComputedStyle(node).fill);
        }
        if (themed.dark.background === themed.light.background || themed.dark.text === themed.light.text || themed.dark.actor === themed.light.actor) {
          errors.push(`${screen.id}/${id}: 목업/시퀀스의 다크·라이트 색상이 전환되지 않는다.`);
        }
        themeRace = { pageId: linkId, tab: `sequence-${id}` };
        sequenceTabs++;
      }
      checkedScreens++;
      if (checkedScreens % 10 === 0 || checkedScreens === plannedScreens.length) console.log(`설계 화면 순회: ${checkedScreens}/${plannedScreens.length}`);
    }
    if (themeRace) {
      await openHash(themeRace.pageId);
      const article = page.locator(`[data-page][id="${themeRace.pageId}"]`);
      await article.locator(`[data-document-tab="${themeRace.tab}"]`).click();
      await page.evaluate(() => window.DELIVERY_READY);
      const panel = article.locator(`[data-document-panel="${themeRace.tab}"]`);
      const expectedActor = await panel.locator('svg rect.actor').first().evaluate((node) => getComputedStyle(node).fill);
      await page.evaluate(() => {
        const original = window.mermaid.run;
        window.__testMermaidOriginal = original;
        window.__testMermaidWaiting = false;
        let hold = true;
        window.mermaid.run = async function(options) {
          if (hold) { hold = false; window.__testMermaidWaiting = true; await new Promise((done) => { window.__testMermaidRelease = done; }); }
          return original.call(window.mermaid, options);
        };
      });
      await page.locator('[data-theme-toggle]').click();
      await page.waitForFunction(() => window.__testMermaidWaiting);
      await page.locator('[data-theme-toggle]').click();
      await page.evaluate(() => window.__testMermaidRelease());
      await page.evaluate(() => window.DELIVERY_READY);
      await page.evaluate(() => { window.mermaid.run = window.__testMermaidOriginal; });
      const actual = await panel.locator('.mermaid').first().evaluate((node) => ({ theme: node.dataset.renderedTheme, actor: getComputedStyle(node.querySelector('rect.actor')).fill }));
      if (actual.theme !== 'light' || actual.actor !== expectedActor) errors.push('이전 다크 렌더와 라이트 선택이 겹치면 최종 SVG 테마가 남는다.');
      console.log('테마 경합: 라이트→지연된 다크→라이트 최종 색상 확인');
    }
    for (const uc of (modelContractFocus ? [] : usecases.filter((item) => item.sequence_design?.shared_sequences?.length))) {
      const linkId = await page.locator('[data-page-link]').evaluateAll((nodes, id) => nodes.find((node) => node.textContent.trim().startsWith(id + ' '))?.dataset.pageLink, uc.id);
      if (!linkId) continue;
      for (const shared of uc.sequence_design.shared_sequences) {
        const match = shared.match(/^\[([^\]]+)\]\([^#]+#([^)]+)\)$/);
        if (!match) continue;
        await openHash(linkId);
        const link = page.locator(`[data-page][id="${linkId}"]`).getByRole('link', { name: match[1], exact: true });
        const href = await link.getAttribute('href');
        if (new URLSearchParams(href?.split('?')[1]).get('anchor') !== match[2]) errors.push(`${uc.id}: 공통 시퀀스 링크의 그림 앵커가 사라졌다.`);
        await link.click();
        await page.waitForFunction((key) => {
          const current = document.querySelector('[data-page]:not([hidden])');
          const target = [...(current?.querySelectorAll('[id]') ?? [])].find((node) => node.id === key);
          const rect = target?.nextElementSibling?.getBoundingClientRect();
          return rect && rect.top >= 0 && rect.top < innerHeight;
        }, match[2]);
      }
    }
    console.log(`설계 화면 ${plannedScreens.length}개: 정본 식별자·생성 목업·초기 화면 탭 ${initialTabs}개·시퀀스 탭 ${sequenceTabs}개 확인`);
    const mockupPage = await page.locator('[data-mockup$="/__design__"]').first().evaluate((node) => node.closest('[data-page]')?.id);
    if (mockupPage) await openHash(mockupPage);
  }
  const syntaxProblems = await page.evaluate(async () => {
    const problems = [];
    for (const node of document.querySelectorAll('.mermaid[data-mermaid-source]')) {
      try { await window.mermaid.parse(decodeURIComponent(node.dataset.mermaidSource)); }
      catch (error) { problems.push(String(error)); }
    }
    return problems;
  });
  errors.push(...syntaxProblems.map((problem) => `Mermaid 구문 오류: ${problem}`));
  const state = await page.evaluate(() => ({
    pages: document.querySelectorAll('[data-page]').length,
    visiblePages: [...document.querySelectorAll('[data-page]')].filter((node) => !node.hidden).length,
    currentPath: document.querySelector('[data-current-path]')?.textContent?.trim(),
    mermaids: document.querySelectorAll('.mermaid[data-mermaid-source]').length,
    diagrams: document.querySelectorAll('.mermaid svg').length,
    visibleMermaids: [...document.querySelectorAll('.mermaid[data-mermaid-source]')].filter((node) => node.getClientRects().length).length,
    visibleDiagrams: [...document.querySelectorAll('.mermaid[data-mermaid-source]')].filter((node) => node.getClientRects().length && node.querySelector('svg')).length,
    designMockups: document.querySelectorAll('[data-mockup$="/__design__"]').length,
    describedMockups: [...document.querySelectorAll('[data-mockup$="/__design__"]')].filter((node) => {
      const shadow = node.querySelector('[data-mockup-body]')?.shadowRoot;
      return shadow?.querySelector('[data-note]') && shadow?.querySelector('[id^="note-"]') && shadow?.textContent.includes('테스트 케이스');
    }).length,
    currentMockups: [...document.querySelectorAll('[data-page]:not([hidden]) [data-mockup$="/__design__"]')].filter((node) => node.querySelector('[data-mockup-body]')?.shadowRoot?.querySelector('.screen')).length,
  }));
  if (state.visiblePages !== 1) errors.push(`현재 문서가 하나가 아니다: ${state.visiblePages}`);
  if (!state.currentPath) errors.push('상단 현재 문서 경로가 비었다.');
  if (state.visibleMermaids !== state.visibleDiagrams) errors.push(`현재 Mermaid ${state.visibleMermaids}개 중 SVG ${state.visibleDiagrams}개만 렌더링됐다.`);
  if (site === 'design' && (state.designMockups === 0 || state.describedMockups < 1 || state.currentMockups !== 1)) {
    errors.push(`현재 문서의 상세 목업이 완결되지 않았다: 전체 자리 ${state.designMockups} · 주입 완료 ${state.describedMockups}`);
  }
  await page.locator('[data-ui-settings-toggle]').click();
  await page.locator('select[data-ui-width]').selectOption('full');
  await page.locator('select[data-ui-font]').selectOption('serif');
  await page.locator('select[data-ui-size]').selectOption('150');
  const settings = await page.evaluate(() => ({
    width: document.documentElement.dataset.contentWidth,
    font: document.documentElement.dataset.uiFont,
    scale: document.documentElement.style.getPropertyValue('--ui-scale'),
  }));
  if (settings.width !== 'full' || settings.font !== 'serif' || settings.scale !== '1.5') errors.push('보기 설정이 문서 루트에 반영되지 않았다.');
  await page.locator('[data-theme-toggle]').click();
  await page.evaluate(() => window.DELIVERY_READY);
  await page.emulateMedia({ media: 'print' });
  const printVisible = await page.evaluate(() => [...document.querySelectorAll('[data-page]')]
    .filter((node) => getComputedStyle(node).display !== 'none').length);
  if (printVisible !== 1) errors.push(`인쇄 매체에서 현재 문서 외 ${printVisible - 1}개가 더 보인다.`);
  if (await page.locator('[data-back-to-top]').isVisible()) errors.push('맨 위로 버튼이 인쇄에 나타난다.');
  console.log(`${site}: 문서 ${state.pages} · Mermaid SVG ${state.diagrams} · 상세 목업 ${state.describedMockups} · 인쇄 문서 ${printVisible}`);
} finally {
  await browser.close();
}

for (const error of errors) console.log(`오류: ${error}`);
console.log(errors.length ? `빨강 — ${errors.length}건` : modelContractFocus ? '초록 — 비저장/물리 모델 경계 집중 검사가 완결됐다.' : '초록 — 실제 브라우저 동작이 완결됐다.');
process.exitCode = errors.length ? 1 : 0;
