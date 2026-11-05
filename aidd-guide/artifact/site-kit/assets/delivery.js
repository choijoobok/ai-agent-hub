/* file://에서도 동작하는 단일 HTML 사이트 탐색기 */
(() => {
  const root = document.documentElement;
  const pages = [...document.querySelectorAll('[data-page]')];
  const links = [...document.querySelectorAll('[data-page-link]')];
  const filter = document.querySelector('[data-tree-filter]');
  const currentPath = document.querySelector('[data-current-path]');
  const groups = [...document.querySelectorAll('.tree-branch')];
  let searchGroupState = null;
  const backToTop = document.querySelector('[data-back-to-top]');
  const syncBackToTop = () => { if (backToTop) backToTop.hidden = window.scrollY < 400; };
  addEventListener('scroll', syncBackToTop, {passive:true});
  backToTop?.addEventListener('click', () => {
    window.scrollTo(0, 0);
    const current = document.querySelector('[data-page]:not([hidden])');
    current?.setAttribute('tabindex', '-1');
    current?.focus({preventScroll:true});
    syncBackToTop();
  });
  syncBackToTop();

  function setCollapsed(group, collapsed) {
    group.classList.toggle('collapsed', collapsed);
    group.querySelector(':scope > [data-tree-group]')?.setAttribute('aria-expanded', String(!collapsed));
  }

  function revealLink(link) {
    for (let group = link?.closest('.tree-branch'); group; group = group.parentElement?.closest('.tree-branch')) {
      setCollapsed(group, false);
    }
  }

  function resolvedTheme() { return root.dataset.theme === 'light' ? 'light' : 'dark'; }

  let mermaidQueue = Promise.resolve(), mermaidRevision = 0;
  function renderMermaid() {
    const revision = ++mermaidRevision;
    mermaidQueue = mermaidQueue.catch(() => {}).then(() => renderMermaidNow(revision));
    return mermaidQueue;
  }
  async function renderMermaidNow(revision) {
    if (revision !== mermaidRevision) return;
    const mode = resolvedTheme();
    const nodes = [...document.querySelectorAll('.mermaid[data-mermaid-source]')].filter((node) => node.getClientRects().length
      && (!node.querySelector('svg') || node.dataset.renderedTheme !== mode));
    if (!nodes.length) return;
    for (const node of nodes) {
      delete node.dataset.renderedTheme;
      try { node.textContent = decodeURIComponent(node.dataset.mermaidSource); } catch { /* 원문 유지 */ }
      node.removeAttribute('data-processed');
      node.classList.remove('mermaid-error');
    }
    if (!window.mermaid) {
      nodes.forEach((node) => node.classList.add('mermaid-error'));
      return;
    }
    try {
      const palette = getComputedStyle(root);
      const color = (name) => palette.getPropertyValue(name).trim();
      window.mermaid.initialize({ startOnLoad: false, securityLevel: 'strict', theme: 'base', themeVariables: {
        darkMode: resolvedTheme() === 'dark', background: color('--bg-2'),
        primaryColor: color('--bg-3'), primaryTextColor: color('--text'), primaryBorderColor: color('--border-strong'),
        secondaryColor: color('--bg-4'), tertiaryColor: color('--bg-2'), textColor: color('--text'), lineColor: color('--text-dim'),
        actorBkg: color('--bg-3'), actorBorder: color('--border-strong'), actorTextColor: color('--text'), actorLineColor: color('--text-dim'),
        signalColor: color('--text-dim'), signalTextColor: color('--text'),
        labelBoxBkgColor: color('--bg-3'), labelBoxBorderColor: color('--border-strong'), labelTextColor: color('--text'), loopTextColor: color('--text'),
        noteBkgColor: color('--bg-4'), noteBorderColor: color('--border-strong'), noteTextColor: color('--text'),
        activationBkgColor: color('--bg-4'), activationBorderColor: color('--border-strong'), sequenceNumberColor: color('--bg-2'),
      } });
      await window.mermaid.run({ nodes, suppressErrors: false });
      if (revision === mermaidRevision) nodes.forEach((node) => { node.dataset.renderedTheme = mode; });
    } catch (error) {
      console.error('Mermaid 렌더링 실패', error);
      nodes.filter((node) => !node.querySelector('svg')).forEach((node) => node.classList.add('mermaid-error'));
    }
  }

  function syncThemeControls() {
    const dark = resolvedTheme() === 'dark';
    document.querySelectorAll('.theme-icon-dark').forEach((icon) => { icon.hidden = !dark; });
    document.querySelectorAll('.theme-icon-light').forEach((icon) => { icon.hidden = dark; });
    document.querySelector('[data-theme-toggle]')?.setAttribute('aria-label', dark ? '라이트 테마로 전환' : '다크 테마로 전환');
    document.querySelectorAll('[data-mockup-body]').forEach((host) => { host.dataset.theme = resolvedTheme(); });
  }
  function theme(value, rerender = true) {
    root.dataset.theme = value;
    try { localStorage.setItem('delivery-theme', value); } catch { /* file:// 저장소 제한 */ }
    syncThemeControls();
    return rerender ? renderMermaid() : Promise.resolve();
  }
  let savedTheme = matchMedia('(prefers-color-scheme:light)').matches ? 'light' : 'dark';
  try {
    const stored = localStorage.getItem('delivery-theme');
    if (stored === 'light' || stored === 'dark') savedTheme = stored;
  } catch { /* 무시 */ }
  theme(savedTheme, false);
  document.querySelector('[data-theme-toggle]')?.addEventListener('click', () => {
    window.DELIVERY_READY = theme(resolvedTheme() === 'dark' ? 'light' : 'dark');
  });

  function injectMockups(scope = document) {
    const registry = window.DELIVERY_MOCKUPS || {};
    const holders = [scope.matches?.('[data-mockup]') ? scope : null, ...scope.querySelectorAll('[data-mockup]')].filter(Boolean);
    holders.forEach((holder) => {
      const item = registry[holder.dataset.mockup];
      const body = holder.querySelector('[data-mockup-body]');
      if (!item || !body || body.shadowRoot) return;
      body.dataset.theme = resolvedTheme();
      const shadow = body.attachShadow({ mode: 'open' });
      const isolatedStyle = (item.style || '').replace(/:root((?:\[[^\]]+\]|:not\([^)]*\))*)/g, (_, condition) => condition ? `:host(${condition})` : ':host')
        .replace(/(^|[}\s,])body(?=\s*[{,.#[:])/gm, '$1.mockup-root');
      shadow.innerHTML = `<style>:host{display:block;color-scheme:light}:host([data-theme="dark"]){color-scheme:dark}${isolatedStyle}\n.mockup-root [id]{scroll-margin-top:72px}</style><div class="mockup-root">${item.html}</div>`;
      bindDeclaredMockup(shadow);
      shadow.addEventListener('click', (event) => {
        if (event.defaultPrevented) return;
        const request = event.target.closest?.('[data-request-navigation][data-request-screen]');
        if(request){
          const link=links.find(l=>l.textContent.trim().startsWith(request.dataset.requestScreen+' '));
          if(!link)return;
          event.preventDefault();
          const query=request.getAttribute('href').split('?')[1]?.split('#')[0]??'';
          if(new URLSearchParams(query).get('figure')==='restore'){
            const destination=pages.find(p=>p.id===link.dataset.pageLink);
            const dirty=[...destination.querySelectorAll('[data-mockup-body]')].some(host=>host.shadowRoot&&([...host.shadowRoot.querySelectorAll('[data-form-original]')].some(field=>field.value!==field.dataset.formOriginal)||host.shadowRoot.querySelector('[data-grid-row-state="U"],[data-grid-row-state="C"],[data-grid-row-state="D"]')));
            if(dirty&&!confirm('미저장 편집이 있습니다. 기존 입력을 보존한 채 복원 편집 예시를 여시겠습니까?'))return;
          }
          show(link.dataset.pageLink);
          const current=pages.find(p=>p.id===link.dataset.pageLink);
          openRequestMockup(current,query);
          history.replaceState(null,'',`#${link.dataset.pageLink}?${query}`);
          return;
        }
        const anchor = event.target.closest?.('[data-note]');
        if (!anchor) return;
        const note = shadow.getElementById(`note-${anchor.dataset.note}`);
        if (note) { event.preventDefault(); note.scrollIntoView({ behavior: 'smooth', block: 'center' }); }
      });
    });
    syncThemeControls();
  }

  function activateDocumentTab(button, focus = false) {
    const tabs = button.closest('[data-document-tabs]');
    if (!tabs) return;
    const key = button.dataset.documentTab;
    tabs.querySelectorAll('[data-document-tab]').forEach((item) => {
      const selected = item === button;
      item.setAttribute('aria-selected', String(selected));
      item.tabIndex = selected ? 0 : -1;
    });
    tabs.querySelectorAll('[data-document-panel]').forEach((panel) => { panel.hidden = panel.dataset.documentPanel !== key; });
    if (focus) button.focus();
    window.DELIVERY_READY = renderMermaid();
  }
  document.querySelectorAll('[data-document-tab]').forEach((button) => {
    button.addEventListener('click', () => activateDocumentTab(button));
    button.addEventListener('keydown', (event) => {
      if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
      const buttons = [...button.closest('[role="tablist"]').querySelectorAll('[data-document-tab]')];
      const current = buttons.indexOf(button);
      const next = event.key === 'Home' ? 0 : event.key === 'End' ? buttons.length - 1
        : (current + (event.key === 'ArrowRight' ? 1 : -1) + buttons.length) % buttons.length;
      event.preventDefault();
      activateDocumentTab(buttons[next], true);
    });
  });

  function show(id, updateHash = true) {
    const current = pages.find((page) => page.id === id);
    if (!current) return false;
    pages.forEach((page) => { page.hidden = page.id !== id; });
    links.forEach((link) => {
      const active = link.dataset.pageLink === id;
      link.classList.toggle('active', active);
      if (active) {
        link.setAttribute('aria-current', 'page');
        revealLink(link);
        if (currentPath) {
          currentPath.textContent = link.dataset.pagePath || link.textContent.trim();
          currentPath.title = currentPath.textContent;
        }
      } else link.removeAttribute('aria-current');
    });
    if (updateHash) history.replaceState(null, '', `#${id}`);
    window.scrollTo(0, 0);
    syncBackToTop();
    injectMockups(current);
    window.DELIVERY_READY = renderMermaid();
    return true;
  }
  function openRequestMockup(current,query){
    const tab=current?.querySelector('[data-document-tab="mockup"]');
    if(tab)activateDocumentTab(tab);
    current?.querySelectorAll('[data-mockup-body]').forEach(host=>{if(host.shadowRoot)applyRequestContext(host.shadowRoot,query);});
  }
  function closeSidebar() {
    document.querySelector('#sidebar')?.classList.remove('open');
    document.querySelector('.sidebar-backdrop')?.classList.remove('open');
    const button = document.querySelector('[data-menu-toggle]');
    button?.setAttribute('aria-expanded', 'false');
    button?.setAttribute('aria-label', '목차 열기');
  }
  function showHash() {
    const [id, query = ''] = location.hash.slice(1).split('?');
    if (!show(id, false)) return false;
    if(new URLSearchParams(query).has('requestNo')||new URLSearchParams(query).has('figure'))openRequestMockup(pages.find(p=>p.id===id),query);
    let anchor = new URLSearchParams(query).get('anchor');
    if (!anchor) return true;
    try { anchor = decodeURIComponent(anchor); } catch { /* 잘못된 인코딩은 원 문자열로 탐색 */ }
    const current = pages.find((page) => page.id === id);
    const target = [...current.querySelectorAll('[id]')].find((node) => node.id === anchor);
    if (!target) return true;
    const panel = target.closest('[data-document-panel]');
    if (panel?.hidden) {
      const button = [...current.querySelectorAll('[data-document-tab]')].find((item) => item.dataset.documentTab === panel.dataset.documentPanel);
      if (button) activateDocumentTab(button);
    }
    window.DELIVERY_READY.then(() => requestAnimationFrame(() => { if (!current.hidden) target.scrollIntoView({ block: 'center' }); }));
    return true;
  }
  links.forEach((link) => link.addEventListener('click', () => { show(link.dataset.pageLink); closeSidebar(); }));
  if (!showHash() && pages[0]) show(pages[0].id, false);
  addEventListener('hashchange', showHash);

  document.querySelectorAll('[data-tree-group]').forEach((button) => {
    button.addEventListener('click', () => {
      const group = button.closest('.tree-branch');
      if (group) setCollapsed(group, !group.classList.contains('collapsed'));
    });
  });
  document.querySelector('[data-menu-toggle]')?.addEventListener('click', () => {
    const open = document.querySelector('#sidebar')?.classList.toggle('open') ?? false;
    document.querySelector('.sidebar-backdrop')?.classList.toggle('open', open);
    const button = document.querySelector('[data-menu-toggle]');
    button?.setAttribute('aria-expanded', String(open));
    button?.setAttribute('aria-label', open ? '목차 닫기' : '목차 열기');
  });
  document.querySelector('.sidebar-backdrop')?.addEventListener('click', closeSidebar);

  filter?.addEventListener('input', () => {
    const query = filter.value.trim().toLocaleLowerCase();
    if (query && !searchGroupState) searchGroupState = new Map(groups.map((group) => [group, group.classList.contains('collapsed')]));
    links.forEach((link) => {
      const page = document.getElementById(link.dataset.pageLink);
      const hit = !query || `${link.textContent} ${page?.textContent || ''}`.toLocaleLowerCase().includes(query);
      link.closest('li')?.classList.toggle('filtered', !hit);
    });
    [...groups].reverse().forEach((group) => {
      const visible = [...group.querySelectorAll('[data-page-link]')].some((link) => !link.closest('li')?.classList.contains('filtered'));
      group.classList.toggle('filtered', !visible);
      if (query && visible) setCollapsed(group, false);
    });
    if (!query && searchGroupState) {
      searchGroupState.forEach((collapsed, group) => setCollapsed(group, collapsed));
      searchGroupState = null;
      revealLink(links.find((link) => link.classList.contains('active')));
    }
  });

  const settings = { width: 'standard', font: 'system', size: '100' };
  try { Object.assign(settings, JSON.parse(localStorage.getItem('delivery-ui') || '{}')); } catch { /* 무시 */ }
  const controls = {
    width: document.querySelector('[data-ui-width]'), font: document.querySelector('[data-ui-font]'), size: document.querySelector('[data-ui-size]'),
  };
  function applySettings() {
    if (!['standard', 'full'].includes(settings.width)) settings.width = 'standard';
    if (!['system', 'gothic', 'serif', 'mono'].includes(settings.font)) settings.font = 'system';
    if (!['90', '100', '110', '120', '130', '140', '150'].includes(settings.size)) settings.size = '100';
    root.dataset.contentWidth = settings.width;
    root.dataset.uiFont = settings.font;
    root.style.setProperty('--ui-scale', String(Number(settings.size) / 100));
    for (const [key, control] of Object.entries(controls)) if (control) control.value = settings[key];
    try { localStorage.setItem('delivery-ui', JSON.stringify(settings)); } catch { /* 무시 */ }
  }
  for (const [key, control] of Object.entries(controls)) control?.addEventListener('change', () => { settings[key] = control.value; applySettings(); });
  applySettings();

  const settingsButton = document.querySelector('[data-ui-settings-toggle]');
  const settingsPanel = document.querySelector('[data-ui-settings-panel]');
  settingsButton?.addEventListener('click', () => {
    const open = settingsPanel?.hasAttribute('hidden');
    settingsPanel?.toggleAttribute('hidden', !open);
    settingsButton.setAttribute('aria-expanded', String(open));
  });
  document.querySelector('[data-ui-reset]')?.addEventListener('click', () => {
    Object.assign(settings, { width: 'standard', font: 'system', size: '100' }); applySettings();
  });
  document.addEventListener('click', (event) => {
    if (!event.target.closest?.('.ui-settings')) {
      settingsPanel?.setAttribute('hidden', ''); settingsButton?.setAttribute('aria-expanded', 'false');
    }
  });

  document.querySelector('[data-print-page]')?.addEventListener('click', () => window.print());
  window.DELIVERY_READY = renderMermaid();
})();
