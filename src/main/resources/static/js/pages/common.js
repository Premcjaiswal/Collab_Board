/* Pages shared by both roles (notifications, profile, settings) and reusable components. */
(function () {
  'use strict';
  const { h, clear, icon, fmtDateTime, fmtDate, fileSize, statusBadge, marks, busy, toast, showErrors, clearErrors, val } = UI;

  // ------------------------------------------------------------------ shared components
  const Shared = {};

  Shared.fileItem = function (f, extra) {
    return h('li', { class: 'file-item' },
      h('span', { class: 'icon', 'aria-hidden': 'true' }, UI.ext(f.name) || 'file'),
      h('div', { class: 'info' },
        h('div', { class: 'name', title: f.name }, f.name),
        h('div', { class: 'meta' }, fileSize(f.sizeBytes), f.uploadedBy ? ' · ' + f.uploadedBy : '', f.uploadedAt ? ' · ' + fmtDateTime(f.uploadedAt) : '')),
      h('a', { class: 'btn btn-secondary btn-sm', href: f.downloadUrl, download: '', 'aria-label': 'Download ' + f.name }, icon('download'), 'Download'),
      extra || null);
  };

  Shared.versions = function (versions) {
    if (!versions || !versions.length) {
      return UI.empty('No submissions yet', 'Submitted versions will appear here.');
    }
    return h('div', { class: 'stack-sm' }, versions.map((v) => h('div', { class: 'version' },
      h('div', { class: 'version-head' },
        h('strong', null, 'Version ' + v.versionNo),
        h('span', { class: 'small muted' }, 'Submitted by ' + v.submittedBy + ' · ' + fmtDateTime(v.submittedAt)),
        v.late ? statusBadge('LATE') : null),
      v.githubUrl ? h('div', { class: 'small' }, icon('link', 'inline-icon'), ' ',
        h('a', { href: v.githubUrl, target: '_blank', rel: 'noopener noreferrer' }, v.githubUrl)) : null,
      v.notes ? h('div', { class: 'feedback-box small' }, v.notes) : null,
      v.files.length ? h('ul', { class: 'file-list' }, v.files.map((f) => Shared.fileItem(f))) :
        h('div', { class: 'small muted' }, 'No files in this version.'))));
  };

  Shared.evaluation = function (e) {
    const head = h('div', { class: 'row between' },
      h('div', { class: 'row' },
        h('span', { class: 'badge status-' + e.decision }, UI.DECISION_LABELS[e.decision] || e.decision),
        e.versionNo ? h('span', { class: 'small muted' }, 'for version ' + e.versionNo) : null,
        e.published ? null : h('span', { class: 'badge status-DRAFT' }, 'Draft — not visible to students'),
        e.current ? null : h('span', { class: 'badge' }, 'Superseded')),
      h('span', { class: 'small muted' }, (e.published ? 'Published ' + fmtDateTime(e.publishedAt) : 'Saved ' + fmtDateTime(e.createdAt)) + ' · ' + e.evaluator));
    const body = [];
    if (e.totalMarks !== null && e.totalMarks !== undefined) {
      body.push(h('div', { class: 'marks' }, marks(e.totalMarks), h('small', null, ' / ' + e.maxMarks)));
    }
    if (e.scores && e.scores.length) {
      body.push(h('div', { class: 'table-wrap' }, h('table', { class: 'table rubric-table' },
        h('thead', null, h('tr', null, h('th', null, 'Criterion'), h('th', null, 'Score'))),
        h('tbody', null, e.scores.map((s) => h('tr', null, h('td', null, s.criterion), h('td', null, marks(s.score) + ' / ' + s.maxMarks)))))));
    }
    if (e.reason) body.push(h('div', null, h('div', { class: 'label' }, 'Reason'), h('div', { class: 'feedback-box' }, e.reason)));
    if (e.feedback) body.push(h('div', null, h('div', { class: 'label' }, 'Feedback'), h('div', { class: 'feedback-box' }, e.feedback)));
    return h('div', { class: 'version' }, head, body);
  };

  Shared.evaluations = function (list, emptyText) {
    if (!list || !list.length) return UI.empty('No results yet', emptyText || 'Marks and feedback appear here once your teacher publishes them.');
    return h('div', { class: 'stack-sm' }, list.map(Shared.evaluation));
  };

  Shared.rubric = function (rubric) {
    if (!rubric || !rubric.length) return null;
    return h('div', { class: 'card' },
      h('div', { class: 'card-header' }, h('h3', null, 'Evaluation rubric')),
      h('div', { class: 'table-wrap' }, h('table', { class: 'table rubric-table' },
        h('thead', null, h('tr', null, h('th', null, 'Criterion'), h('th', null, 'Marks'))),
        h('tbody', null, rubric.map((c) => h('tr', null,
          h('td', null, h('div', { class: 'title' }, c.title), c.description ? h('div', { class: 'small muted' }, c.description) : null),
          h('td', null, String(c.maxMarks))))))));
  };

  Shared.policy = function (item) {
    return h('dl', { class: 'dl' },
      h('dt', null, 'Class'), h('dd', null, h('a', { href: '#/classes/' + item.classroom.id }, item.classroom.displayName)),
      h('dt', null, 'Deadline'), h('dd', null, fmtDateTime(item.deadline), ' ', h('span', { class: 'small muted' }, '(' + UI.dueLabel(item.deadline) + ')')),
      h('dt', null, 'Maximum marks'), h('dd', null, String(item.maxMarks)),
      h('dt', null, 'Allowed files'), h('dd', null, item.allowedExtensions.map((e) => '.' + e).join(', ') + ' · up to ' + item.maxFileSizeMb + ' MB each'),
      h('dt', null, 'Resubmission'), h('dd', null, item.allowResubmission ? 'Allowed until the deadline' : 'Not allowed (one submission)'),
      h('dt', null, 'Late submissions'), h('dd', null, item.allowLate ? 'Accepted after the deadline (marked late)' : 'Not accepted after the deadline'));
  };

  /**
   * File picker with client-side checks (type and size) and a review list. Server-side validation is authoritative.
   * Returns {el, files(), reset()}.
   */
  Shared.filePicker = function (rules, opts) {
    opts = opts || {};
    const multiple = opts.multiple !== false;
    let files = [];
    const input = h('input', { type: 'file', multiple, accept: rules.allowedExtensions.map((e) => '.' + e).join(','), id: 'fp-' + Math.random().toString(36).slice(2) });
    const list = h('ul', { class: 'file-list' });
    const zone = h('div', { class: 'dropzone' },
      h('p', { class: 'mb-0' }, h('label', { for: input.id, class: 'label' }, multiple ? 'Choose files' : 'Choose a file'), ' or drag and drop here'),
      h('p', { class: 'small muted mb-0' }, 'Allowed: ' + rules.allowedExtensions.map((e) => '.' + e).join(', ') + ' · max ' + rules.maxFileSizeMb + ' MB each'),
      input);

    function problem(f) {
      if (!rules.allowedExtensions.includes(UI.ext(f.name))) return 'File type not allowed';
      if (f.size > rules.maxFileSizeMb * 1024 * 1024) return 'Larger than ' + rules.maxFileSizeMb + ' MB';
      if (f.size === 0) return 'File is empty';
      return null;
    }

    function renderList() {
      clear(list);
      files.forEach((f, i) => {
        const p = problem(f);
        list.appendChild(h('li', { class: 'file-item' + (p ? ' bad' : '') },
          h('span', { class: 'icon', 'aria-hidden': 'true' }, UI.ext(f.name) || 'file'),
          h('div', { class: 'info' }, h('div', { class: 'name' }, f.name),
            h('div', { class: 'meta' }, fileSize(f.size), p ? ' · ' + p : ' · ready')),
          h('button', { type: 'button', class: 'btn btn-ghost btn-sm', 'aria-label': 'Remove ' + f.name,
            onClick: () => { files.splice(i, 1); renderList(); if (opts.onChange) opts.onChange(files); } }, 'Remove')));
      });
    }

    function add(fileList) {
      const incoming = Array.from(fileList || []);
      files = multiple ? files.concat(incoming).slice(0, 10) : incoming.slice(0, 1);
      renderList();
      if (opts.onChange) opts.onChange(files);
    }

    input.addEventListener('change', () => { add(input.files); input.value = ''; });
    zone.addEventListener('dragover', (e) => { e.preventDefault(); zone.classList.add('drag'); });
    zone.addEventListener('dragleave', () => zone.classList.remove('drag'));
    zone.addEventListener('drop', (e) => { e.preventDefault(); zone.classList.remove('drag'); add(e.dataTransfer.files); });

    return {
      el: h('div', { class: 'stack-sm' }, zone, list),
      files: () => files.slice(),
      invalid: () => files.filter(problem),
      reset: () => { files = []; renderList(); }
    };
  };

  /** Review-then-submit confirmation listing the selected files. */
  Shared.confirmFiles = function (title, files, extraLines) {
    const body = h('div', { class: 'stack-sm' },
      h('p', { class: 'mb-0' }, 'Please review before submitting. A new version will be recorded with these files:'),
      files.length ? h('ul', { class: 'file-list' }, files.map((f) => h('li', { class: 'file-item' },
        h('span', { class: 'icon', 'aria-hidden': 'true' }, UI.ext(f.name) || 'file'),
        h('div', { class: 'info' }, h('div', { class: 'name' }, f.name), h('div', { class: 'meta' }, fileSize(f.size)))))) :
        h('p', { class: 'muted mb-0' }, 'No files attached.'),
      (extraLines || []).map((l) => h('p', { class: 'small mb-0' }, l)));
    return new Promise((resolve) => {
      let ok = false;
      UI.modal({
        title, body, onClose: () => resolve(ok),
        actions: [
          { label: 'Back', class: 'btn-secondary', onClick: (c) => c() },
          { label: 'Submit now', onClick: (c) => { ok = true; c(); } }
        ]
      });
    });
  };

  Shared.progress = function () {
    const bar = h('span');
    const el = h('div', { class: 'progress', role: 'progressbar', 'aria-valuemin': '0', 'aria-valuemax': '100', hidden: true }, bar);
    return {
      el,
      set(p) { el.hidden = false; bar.style.width = p + '%'; el.setAttribute('aria-valuenow', String(p)); },
      hide() { el.hidden = true; bar.style.width = '0'; }
    };
  };

  Shared.card = function (title, body, actions) {
    return h('section', { class: 'card' },
      h('div', { class: 'card-header' }, h('h2', null, title), actions || null),
      h('div', { class: 'card-body' }, body));
  };

  Shared.listCard = function (title, items, emptyEl, actions) {
    return h('section', { class: 'card' },
      h('div', { class: 'card-header' }, h('h2', null, title), actions || null),
      items.length ? h('ul', { class: 'list' }, items) : emptyEl);
  };

  Shared.notificationItem = function (n, onOpened) {
    const el = h('div', { class: 'notif ' + (n.read ? 'read' : 'unread'), role: 'button', tabindex: '0' },
      h('span', { class: 'dot', 'aria-hidden': 'true' }),
      h('div', { class: 'grow' },
        h('div', { class: 'ntitle' }, n.title, n.read ? null : h('span', { class: 'sr-only' }, ' (unread)')),
        h('div', { class: 'nmsg' }, n.message),
        h('div', { class: 'nwhen' }, fmtDateTime(n.createdAt))));
    async function open() {
      if (!n.read) {
        try { await Api.post('/api/notifications/' + n.id + '/read', {}); } catch (e) { /* ignore */ }
        n.read = true;
        el.className = 'notif read';
        App.refreshUnread();
      }
      if (onOpened) onOpened(n);
      if (n.link && n.link.startsWith('#/')) App.go(n.link);
    }
    el.addEventListener('click', open);
    el.addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); open(); } });
    return el;
  };

  window.Shared = Shared;

  // ------------------------------------------------------------------ notifications page
  App.route('#/notifications', '*', async (root) => {
    let unreadOnly = false;
    const listBox = h('div');
    const tabs = h('div', { class: 'tabs', role: 'tablist' });
    const markAll = h('button', { class: 'btn btn-secondary btn-sm', type: 'button' }, 'Mark all as read');
    clear(root).append(App.header('Notifications', { sub: 'Updates about your classes, work, teams and results.', actions: [markAll] }), tabs,
      h('section', { class: 'card' }, listBox));

    function renderTabs() {
      clear(tabs);
      [['All', false], ['Unread', true]].forEach(([label, v]) => {
        tabs.appendChild(h('button', { role: 'tab', 'aria-selected': String(unreadOnly === v), type: 'button',
          onClick: () => { unreadOnly = v; renderTabs(); load(); } }, label));
      });
    }

    async function load() {
      const list = await App.load(listBox, () => Api.get('/api/notifications' + Api.qs({ unreadOnly, limit: 100 })));
      if (!list) return;
      if (!list.length) {
        listBox.appendChild(UI.empty(unreadOnly ? 'No unread notifications' : 'No notifications yet',
          'You will be notified about enrollments, new work, invitations, deadlines and results.'));
        return;
      }
      list.forEach((n) => listBox.appendChild(Shared.notificationItem(n)));
    }

    markAll.addEventListener('click', () => busy(markAll, async () => {
      await Api.post('/api/notifications/read-all', {});
      toast('All notifications marked as read', 'success');
      App.refreshUnread();
      load();
    }));
    renderTabs();
    load();
  });

  // ------------------------------------------------------------------ profile page
  App.route('#/profile', '*', async (root) => {
    const p = await App.load(root, () => Api.get('/api/profile'));
    if (!p) return;
    const student = p.role === 'STUDENT';
    const form = h('form', { class: 'form', novalidate: true },
      UI.formAlert(),
      h('div', { class: 'form-grid' },
        UI.field({ label: 'Full name', name: 'fullName', value: p.fullName, required: true, class: 'span-2', attrs: { maxlength: 120 } }),
        UI.field({ label: 'College email', name: 'email', value: p.email, class: 'span-2', attrs: { readOnly: true },
          hint: 'Your verified email identifies you for enrollment and grading and cannot be changed here.' }),
        student ? [
          UI.field({ label: 'Roll number', name: 'rollNumber', value: p.rollNumber, attrs: { readOnly: true },
            hint: 'Contact your administrator to correct a roll number.' }),
          UI.field({ label: 'Branch / program', name: 'branch', value: p.branch || '', attrs: { maxlength: 100 } }),
          UI.field({ label: 'Year', name: 'yearOfStudy', type: 'select', value: p.yearOfStudy || '',
            options: [{ value: '', label: 'Not set' }].concat([1, 2, 3, 4, 5, 6].map((y) => ({ value: y, label: 'Year ' + y }))) }),
          UI.field({ label: 'Semester', name: 'semester', type: 'select', value: p.semester || '',
            options: [{ value: '', label: 'Not set' }].concat([1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12].map((s) => ({ value: s, label: 'Semester ' + s }))) })
        ] : [
          UI.field({ label: 'College name', name: 'collegeName', value: p.collegeName || '', class: 'span-2', attrs: { maxlength: 150 } }),
          UI.field({ label: 'Designation', name: 'designation', value: p.designation || '', class: 'span-2', attrs: { maxlength: 100 } })
        ]),
      h('div', { class: 'btn-row end' }, h('button', { class: 'btn', type: 'submit' }, 'Save changes')));

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      clearErrors(form);
      const body = { fullName: val(form, 'fullName').trim() };
      if (!body.fullName) return showErrors(form, { message: 'Please correct the highlighted fields.', fieldErrors: { fullName: 'Full name is required.' } });
      if (student) {
        body.branch = val(form, 'branch').trim() || null;
        body.yearOfStudy = UI.intOrNull(val(form, 'yearOfStudy'));
        body.semester = UI.intOrNull(val(form, 'semester'));
      } else {
        body.collegeName = val(form, 'collegeName').trim() || null;
        body.designation = val(form, 'designation').trim() || null;
      }
      await busy(form.querySelector('button[type=submit]'), async () => {
        try {
          const updated = await Api.put('/api/profile', body);
          App.user = updated;
          App.updateUserChip(updated);
          toast('Profile updated', 'success');
        } catch (err) { showErrors(form, err); }
      });
    });

    root.append(App.header('My Profile', { sub: 'View and update your profile details.' }),
      h('div', { class: 'layout-main-side' },
        Shared.card('Profile details', form),
        Shared.card('Account', h('dl', { class: 'dl' },
          h('dt', null, 'Role'), h('dd', null, student ? 'Student' : 'Teacher'),
          h('dt', null, 'Email'), h('dd', null, p.email),
          h('dt', null, 'Email status'), h('dd', null, p.emailVerified ? h('span', { class: 'badge status-EVALUATED' }, 'Verified') : 'Unverified'),
          h('dt', null, 'Member since'), h('dd', null, fmtDate(p.createdAt))))));
  });

  // ------------------------------------------------------------------ settings page
  App.route('#/settings', '*', async (root) => {
    const form = h('form', { class: 'form', novalidate: true },
      UI.formAlert(),
      UI.field({ label: 'Current password', name: 'currentPassword', type: 'password', required: true, attrs: { autocomplete: 'current-password' } }),
      UI.field({ label: 'New password', name: 'newPassword', type: 'password', required: true, attrs: { autocomplete: 'new-password', maxlength: 72 },
        hint: 'At least 8 characters with a letter and a digit.' }),
      UI.field({ label: 'Confirm new password', name: 'confirmPassword', type: 'password', required: true, attrs: { autocomplete: 'new-password', maxlength: 72 } }),
      h('div', { class: 'btn-row end' }, h('button', { class: 'btn', type: 'submit' }, 'Change password')));

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      clearErrors(form);
      const currentPassword = val(form, 'currentPassword');
      const newPassword = val(form, 'newPassword');
      const fe = {};
      if (!currentPassword) fe.currentPassword = 'Current password is required.';
      if (!/^(?=.*[A-Za-z])(?=.*\d).{8,72}$/.test(newPassword)) fe.newPassword = 'Password must be 8-72 characters and contain a letter and a digit.';
      if (newPassword !== val(form, 'confirmPassword')) fe.confirmPassword = 'Passwords do not match.';
      if (Object.keys(fe).length) return showErrors(form, { message: 'Please correct the highlighted fields.', fieldErrors: fe });
      await busy(form.querySelector('button[type=submit]'), async () => {
        try {
          await Api.post('/api/profile/password', { currentPassword, newPassword });
          form.reset();
          toast('Password changed', 'success');
        } catch (err) { showErrors(form, err); }
      });
    });

    const logoutBtn = h('button', { class: 'btn btn-danger-outline', type: 'button' }, icon('logout'), 'Log out');
    logoutBtn.addEventListener('click', () => document.getElementById('logout-btn').click());

    clear(root).append(App.header('Settings', { sub: 'Manage your password and session.' }),
      h('div', { class: 'layout-main-side' },
        Shared.card('Change password', form),
        h('div', { class: 'stack' },
          Shared.card('Account', h('dl', { class: 'dl' },
            h('dt', null, 'Signed in as'), h('dd', null, App.user.email),
            h('dt', null, 'Account type'), h('dd', null, App.user.role === 'TEACHER' ? 'Teacher' : 'Student'))),
          Shared.card('Session', h('div', { class: 'stack-sm' },
            h('p', { class: 'small muted mb-0' }, 'For security you are signed out automatically after a period of inactivity. Always log out on shared computers.'),
            h('div', null, logoutBtn))))));
  });
})();
