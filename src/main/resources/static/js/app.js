/* Application shell: session bootstrap, role-aware navigation, hash router and shared page helpers. */
(function () {
  'use strict';
  const { h, clear, icon } = UI;

  const NAV = {
    STUDENT: [
      { href: '#/dashboard', label: 'Dashboard', icon: 'dashboard' },
      { href: '#/classes', label: 'My Classes', icon: 'classes' },
      { href: '#/assignments', label: 'Assignments', icon: 'assignment' },
      { href: '#/projects', label: 'My Projects', icon: 'project' },
      { href: '#/notifications', label: 'Notifications', icon: 'bell', badge: true },
      { href: '#/profile', label: 'My Profile', icon: 'user' },
      { href: '#/settings', label: 'Settings', icon: 'settings' }
    ],
    TEACHER: [
      { href: '#/dashboard', label: 'Dashboard', icon: 'dashboard' },
      { href: '#/classes', label: 'My Classes', icon: 'classes' },
      { href: '#/assignments', label: 'Assignments', icon: 'assignment' },
      { href: '#/projects', label: 'Projects', icon: 'project' },
      { href: '#/evaluation', label: 'Submissions & Evaluation', icon: 'evaluation' },
      { href: '#/notifications', label: 'Notifications', icon: 'bell', badge: true },
      { href: '#/profile', label: 'My Profile', icon: 'user' },
      { href: '#/settings', label: 'Settings', icon: 'settings' }
    ]
  };

  const routes = [];
  let renderToken = 0;

  const App = {
    user: null,
    content: null,

    /** Registers a route. pattern like '#/assignments/:id'; roles: 'STUDENT' | 'TEACHER' | '*'. */
    route(pattern, roles, handler) {
      const keys = [];
      const re = new RegExp('^' + pattern.replace(/\//g, '\\/').replace(/:(\w+)/g, (_, k) => { keys.push(k); return '([^/]+)'; }) + '$');
      routes.push({ re, keys, roles, handler });
    },

    go(hash) {
      if (window.location.hash === hash) App.render();
      else window.location.hash = hash;
    },

    setTitle(title) {
      document.getElementById('page-title').textContent = title;
      document.title = title + ' · CollabBoard';
    },

    isTeacher() { return App.user && App.user.role === 'TEACHER'; },

    /** Standard page header with optional breadcrumb, subtitle and action buttons. */
    header(title, opts) {
      opts = opts || {};
      App.setTitle(opts.pageTitle || title);
      return h('div', { class: 'page-header' },
        h('div', null,
          opts.crumbs ? h('div', { class: 'breadcrumb' }, opts.crumbs.map((c, i) => [
            i > 0 ? ' / ' : null, c.href ? h('a', { href: c.href }, c.label) : c.label])) : null,
          h('h1', null, title),
          opts.sub ? h('p', { class: 'sub' }, opts.sub) : null),
        opts.actions ? h('div', { class: 'btn-row' }, opts.actions) : null);
    },

    /** Runs loader, shows a spinner meanwhile and an error box on failure. Returns the data or null. */
    async load(container, loader) {
      clear(container).appendChild(UI.loading());
      try {
        const data = await loader();
        clear(container);
        return data;
      } catch (err) {
        clear(container).appendChild(UI.errorBox(err));
        if (err.status === 404) {
          container.appendChild(h('p', { class: 'mt-0' }, h('a', { href: '#/dashboard' }, 'Back to dashboard')));
        }
        return null;
      }
    },

    async refreshUnread() {
      try {
        const res = await Api.get('/api/notifications/unread-count');
        const count = res.count || 0;
        document.querySelectorAll('[data-unread]').forEach((el) => {
          el.textContent = count > 99 ? '99+' : String(count);
          el.hidden = count === 0;
        });
        const bell = document.getElementById('bell');
        bell.setAttribute('aria-label', count ? 'Notifications, ' + count + ' unread' : 'Notifications');
      } catch (e) { /* ignore transient errors */ }
    },

    async render() {
      const token = ++renderToken;
      const raw = window.location.hash || '#/dashboard';
      const [path, query] = raw.split('?');
      const q = new URLSearchParams(query || '');
      highlightNav(path);
      closeNav();
      const content = App.content;
      for (const r of routes) {
        const m = path.match(r.re);
        if (!m) continue;
        if (r.roles !== '*' && r.roles !== App.user.role) continue;
        const params = {};
        r.keys.forEach((k, i) => { params[k] = decodeURIComponent(m[i + 1]); });
        clear(content).appendChild(UI.loading());
        try {
          await r.handler(content, params, q, () => token === renderToken);
        } catch (err) {
          if (token === renderToken) clear(content).appendChild(UI.errorBox(err));
        }
        content.focus({ preventScroll: true });
        window.scrollTo(0, 0);
        return;
      }
      App.setTitle('Page not found');
      clear(content).appendChild(UI.empty('Page not found', 'The page you requested does not exist or is not available for your account.',
        h('a', { class: 'btn', href: '#/dashboard' }, 'Go to dashboard')));
    }
  };

  function highlightNav(path) {
    const section = '#/' + (path.split('/')[1] || 'dashboard');
    document.querySelectorAll('#nav a').forEach((a) => {
      if (a.getAttribute('href') === section) a.setAttribute('aria-current', 'page');
      else a.removeAttribute('aria-current');
    });
  }

  function closeNav() {
    document.getElementById('app').classList.remove('nav-open');
    document.getElementById('menu-toggle').setAttribute('aria-expanded', 'false');
  }

  function buildShell(user) {
    const nav = document.getElementById('nav');
    clear(nav);
    NAV[user.role].forEach((item) => {
      nav.appendChild(h('a', { href: item.href }, icon(item.icon), h('span', null, item.label),
        item.badge ? h('span', { class: 'badge badge-count', dataset: { unread: '' }, hidden: true }, '0') : null));
    });
    document.getElementById('role-chip').textContent = user.role === 'TEACHER' ? 'Teacher portal' : 'Student portal';
    const logout = document.getElementById('logout-btn');
    clear(logout).append(icon('logout'), h('span', null, 'Logout'));
    logout.addEventListener('click', async () => {
      const ok = await UI.confirm('Log out', 'Do you want to log out of CollabBoard?', 'Log out');
      if (!ok) return;
      try { await Api.post('/api/auth/logout', {}); } catch (e) { /* session may already be gone */ }
      window.location.replace('/');
    });
    const bell = document.getElementById('bell');
    clear(bell).append(icon('bell'), h('span', { class: 'badge badge-count', dataset: { unread: '' }, hidden: true }, '0'));
    App.updateUserChip(user);
    const toggle = document.getElementById('menu-toggle');
    clear(toggle).appendChild(icon('menu'));
    toggle.addEventListener('click', () => {
      const app = document.getElementById('app');
      const open = !app.classList.contains('nav-open');
      app.classList.toggle('nav-open', open);
      toggle.setAttribute('aria-expanded', String(open));
    });
    document.getElementById('sidebar-backdrop').addEventListener('click', closeNav);
  }

  App.updateUserChip = function (user) {
    const chip = document.getElementById('user-chip');
    clear(chip).append(h('span', { class: 'avatar', 'aria-hidden': 'true' }, UI.initials(user.fullName)),
      h('span', { class: 'user-name' }, user.fullName));
    chip.setAttribute('aria-label', 'My profile: ' + user.fullName);
  };

  App.start = async function () {
    App.content = document.getElementById('content');
    Api.onUnauthorized = () => window.location.replace('/');
    try {
      App.user = await Api.get('/api/auth/me');
    } catch (e) {
      window.location.replace('/');
      return;
    }
    buildShell(App.user);
    window.addEventListener('hashchange', App.render);
    if (!window.location.hash) window.location.replace('#/dashboard');
    App.render();
    App.refreshUnread();
    setInterval(() => { if (!document.hidden) App.refreshUnread(); }, 60000);
  };

  window.App = App;
})();
