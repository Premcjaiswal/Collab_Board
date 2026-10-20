/* Student pages: dashboard, classes, assignments, projects, team workspace. */
(function () {
  'use strict';
  const { h, clear, icon, fmtDateTime, statusBadge, marks, busy, toast, showErrors, clearErrors, val } = UI;
  const S = 'STUDENT';

  // ------------------------------------------------------------------ helpers

  function displayStatus(summary, item) {
    const st = summary ? summary.status : 'NOT_SUBMITTED';
    if (st === 'NOT_SUBMITTED' && item.pastDeadline && !item.allowLate) return 'OVERDUE';
    return st;
  }

  function resultText(summary) {
    if (!summary || !summary.publishedDecision) return null;
    if (summary.publishedDecision === 'EVALUATED') return marks(summary.publishedMarks) + ' / ' + summary.maxMarks;
    return UI.DECISION_LABELS[summary.publishedDecision];
  }

  function joinClassForm(onJoined) {
    const form = h('form', { class: 'form', novalidate: true },
      UI.formAlert(),
      UI.field({ label: 'Class code', name: 'code', required: true, hint: 'Ask your teacher for the 8-character class code.',
        attrs: { maxlength: 20, autocomplete: 'off', class: 'mono', placeholder: 'e.g. K7M2QX9P' } }),
      h('div', { class: 'btn-row' }, h('button', { class: 'btn', type: 'submit' }, icon('plus'), 'Join class')));
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      clearErrors(form);
      const code = val(form, 'code').trim();
      if (!code) return showErrors(form, { message: 'Enter a class code.', fieldErrors: { code: 'Enter a class code.' } });
      await busy(form.querySelector('button[type=submit]'), async () => {
        try {
          const c = await Api.post('/api/classes/join', { code });
          toast('Joined ' + c.displayName, 'success');
          form.reset();
          App.refreshUnread();
          if (onJoined) onJoined(c);
        } catch (err) { showErrors(form, err); }
      });
    });
    return form;
  }

  function deadlineItem(d) {
    const href = d.kind === 'ASSIGNMENT' ? '#/assignments/' + d.id : '#/projects/' + d.id;
    return h('li', null,
      h('div', { class: 'grow' },
        h('a', { class: 'title', href }, d.title),
        h('div', { class: 'meta' }, (d.kind === 'ASSIGNMENT' ? 'Assignment' : d.group ? 'Group project' : 'Project') + ' · ' + d.className)),
      h('div', { class: 'row' },
        d.status ? statusBadge(d.status) : null,
        h('span', { class: 'small muted nowrap', title: fmtDateTime(d.deadline) }, UI.dueLabel(d.deadline))));
  }

  // ------------------------------------------------------------------ dashboard

  App.route('#/dashboard', S, async (root) => {
    const d = await App.load(root, () => Api.get('/api/dashboard'));
    if (!d) return;
    root.appendChild(App.header('Welcome, ' + App.user.fullName.split(' ')[0], { pageTitle: 'Dashboard', sub: 'Here is what needs your attention.' }));

    if (d.pendingInvitations > 0) {
      root.appendChild(h('div', { class: 'alert alert-info', role: 'status' },
        'You have ' + d.pendingInvitations + ' pending team invitation' + (d.pendingInvitations > 1 ? 's' : '') + '. ',
        h('a', { href: '#/projects?tab=invitations' }, 'Review invitations')));
      root.appendChild(h('div', { class: 'mt-0' }, ' '));
    }

    root.appendChild(h('div', { class: 'stats' },
      stat('Joined classes', d.classes.length),
      stat('Pending work', d.pending.length),
      stat('Upcoming deadlines', d.upcomingAssignments.length + d.upcomingProjects.length),
      stat('Unread notifications', d.unreadNotifications)));

    root.appendChild(h('div', { class: 'grid grid-2' },
      Shared.listCard('Pending submissions', d.pending.map(deadlineItem),
        UI.empty('Nothing pending', 'You are all caught up.')),
      Shared.card('Join a class', joinClassForm((c) => App.go('#/classes/' + c.id)))));

    root.appendChild(h('div', { class: 'grid grid-2 mt' },
      Shared.listCard('Upcoming assignment deadlines', d.upcomingAssignments.map(deadlineItem),
        UI.empty('No upcoming assignments', 'New assignments from your classes appear here.'),
        h('a', { href: '#/assignments', class: 'small' }, 'View all')),
      Shared.listCard('Upcoming project deadlines', d.upcomingProjects.map(deadlineItem),
        UI.empty('No upcoming projects', 'Projects from your classes appear here.'),
        h('a', { href: '#/projects', class: 'small' }, 'View all'))));

    root.appendChild(h('div', { class: 'grid grid-2 mt' },
      Shared.listCard('Results & feedback', d.results.map((r) => h('li', null,
        h('div', { class: 'grow' },
          h('a', { class: 'title', href: (r.kind === 'ASSIGNMENT' ? '#/assignments/' : '#/projects/') + r.itemId }, r.title),
          h('div', { class: 'meta' }, r.className + ' · published ' + UI.relative(r.publishedAt))),
        r.decision === 'EVALUATED'
          ? h('span', { class: 'badge status-EVALUATED' }, marks(r.marks) + ' / ' + r.maxMarks)
          : h('span', { class: 'badge status-' + r.decision }, UI.DECISION_LABELS[r.decision]))),
        UI.empty('No results yet', 'Published marks and feedback will appear here.')),
      Shared.listCard('Recently submitted', d.recentSubmissions.map((r) => h('li', null,
        h('div', { class: 'grow' },
          h('a', { class: 'title', href: (r.kind === 'ASSIGNMENT' ? '#/assignments/' : '#/projects/') + r.itemId }, r.title),
          h('div', { class: 'meta' }, r.className + ' · ' + fmtDateTime(r.submittedAt))),
        statusBadge(r.status))),
        UI.empty('No submissions yet', 'Work you submit will be listed here.'))));

    root.appendChild(h('div', { class: 'grid grid-2 mt' },
      Shared.listCard('My classes', d.classes.map((c) => h('li', null,
        h('div', { class: 'grow' }, h('a', { class: 'title', href: '#/classes/' + c.id }, c.displayName),
          h('div', { class: 'meta' }, c.subjectName + ' · ' + c.teacherName)),
        c.archived ? h('span', { class: 'badge' }, 'Archived') : null)),
        UI.empty('You have not joined any classes', 'Enter a class code from your teacher to get started.')),
      h('section', { class: 'card' },
        h('div', { class: 'card-header' }, h('h2', null, 'Notifications'), h('a', { href: '#/notifications', class: 'small' }, 'View all')),
        d.notifications.length ? h('div', null, d.notifications.map((n) => Shared.notificationItem(n)))
          : UI.empty('No notifications', 'Updates about your classes and work appear here.'))));
  });

  function stat(label, value) {
    return h('div', { class: 'card stat' }, h('div', { class: 'label' }, label), h('div', { class: 'value' }, String(value)));
  }

  // ------------------------------------------------------------------ classes

  App.route('#/classes', S, async (root) => {
    const classes = await App.load(root, () => Api.get('/api/classes'));
    if (!classes) return;
    root.appendChild(App.header('My Classes', { sub: 'Classes you have joined. Each class has its own assignments and projects.' }));
    root.appendChild(h('div', { class: 'layout-main-side' },
      classes.length
        ? h('div', { class: 'grid grid-cards' }, classes.map((c) => h('a', { class: 'card link-card class-card', href: '#/classes/' + c.id },
          h('div', { class: 'card-body' },
            h('h3', { class: 'mb-0' }, c.displayName),
            h('div', { class: 'subject' }, c.subjectName),
            h('div', { class: 'meta' }, h('span', null, c.teacherName), h('span', null, c.assignmentCount + ' assignments'), h('span', null, c.projectCount + ' projects')),
            c.archived ? h('span', { class: 'badge' }, 'Archived') : null))))
        : h('div', { class: 'card' }, UI.empty('No classes yet', 'Join your first class using the code shared by your teacher.')),
      Shared.card('Join a class', joinClassForm((c) => App.go('#/classes/' + c.id)))));
  });

  App.route('#/classes/:id', S, async (root, params) => {
    const data = await App.load(root, () => Promise.all([
      Api.get('/api/classes/' + params.id),
      Api.get('/api/assignments' + Api.qs({ classId: params.id })),
      Api.get('/api/projects' + Api.qs({ classId: params.id }))]));
    if (!data) return;
    const [c, assignments, projects] = data;
    root.appendChild(App.header(c.displayName, {
      crumbs: [{ label: 'My Classes', href: '#/classes' }, { label: c.name }],
      sub: c.subjectName + ' · Teacher: ' + c.teacherName + (c.archived ? ' · Archived' : '')
    }));
    if (c.description) root.appendChild(h('div', { class: 'card card-body pre-wrap' }, c.description));
    root.appendChild(h('div', { class: 'grid grid-2 mt' },
      Shared.listCard('Assignments', assignments.map(assignmentRow),
        UI.empty('No assignments yet', 'Your teacher has not published any assignments for this class.')),
      Shared.listCard('Projects', projects.map(projectRow),
        UI.empty('No projects yet', 'Your teacher has not published any projects for this class.'))));
  });

  function assignmentRow(a) {
    const st = displayStatus(a.mySubmission, a);
    const res = resultText(a.mySubmission);
    return h('li', null,
      h('div', { class: 'grow' },
        h('a', { class: 'title', href: '#/assignments/' + a.id }, a.title),
        h('div', { class: 'meta' }, UI.CATEGORY_LABELS[a.category] + ' · ' + a.classroom.displayName + ' · ' + UI.dueLabel(a.deadline))),
      h('div', { class: 'row' }, res ? h('span', { class: 'badge status-EVALUATED' }, res) : null, statusBadge(st)));
  }

  function projectRow(p) {
    const st = displayStatus(p.mySubmission, p);
    const res = resultText(p.mySubmission);
    return h('li', null,
      h('div', { class: 'grow' },
        h('a', { class: 'title', href: '#/projects/' + p.id }, p.title),
        h('div', { class: 'meta' }, (p.participationMode === 'GROUP' ? 'Group (max ' + p.maxTeamSize + ')' : 'Individual') + ' · ' +
          p.classroom.displayName + ' · ' + UI.dueLabel(p.deadline))),
      h('div', { class: 'row' },
        p.myTeam ? h('span', { class: 'badge badge-primary' }, icon('team'), p.myTeam.name) : null,
        p.pendingInvitations ? h('span', { class: 'badge status-REVISION_REQUESTED' }, p.pendingInvitations + ' invitation(s)') : null,
        res ? h('span', { class: 'badge status-EVALUATED' }, res) : null,
        statusBadge(st)));
  }

  // ------------------------------------------------------------------ assignments

  App.route('#/assignments', S, async (root, params, q) => {
    const data = await App.load(root, () => Promise.all([Api.get('/api/assignments'), Api.get('/api/classes')]));
    if (!data) return;
    const [all, classes] = data;
    let classFilter = q.get('classId') || '';
    let statusFilter = q.get('status') || '';
    const listBox = h('section', { class: 'card' });
    const filters = h('div', { class: 'filters' },
      UI.field({ label: 'Class', name: 'classId', type: 'select', value: classFilter,
        options: [{ value: '', label: 'All classes' }].concat(classes.map((c) => ({ value: c.id, label: c.displayName }))) }),
      UI.field({ label: 'Status', name: 'status', type: 'select', value: statusFilter, options: [
        { value: '', label: 'All' }, { value: 'TODO', label: 'To do' }, { value: 'SUBMITTED', label: 'Submitted' },
        { value: 'EVALUATED', label: 'Evaluated' }, { value: 'REVISION_REQUESTED', label: 'Revision requested' }] }));
    filters.querySelector('[name=classId]').addEventListener('change', (e) => { classFilter = e.target.value; render(); });
    filters.querySelector('[name=status]').addEventListener('change', (e) => { statusFilter = e.target.value; render(); });

    function render() {
      const list = all.filter((a) => !classFilter || String(a.classroom.id) === classFilter).filter((a) => {
        const st = a.mySubmission ? a.mySubmission.status : 'NOT_SUBMITTED';
        if (!statusFilter) return true;
        if (statusFilter === 'TODO') return st === 'NOT_SUBMITTED' || st === 'REVISION_REQUESTED';
        if (statusFilter === 'SUBMITTED') return st === 'SUBMITTED' || st === 'LATE';
        return st === statusFilter;
      });
      clear(listBox).appendChild(list.length ? h('ul', { class: 'list' }, list.map(assignmentRow))
        : UI.empty(all.length ? 'No matching assignments' : 'No assignments yet',
          all.length ? 'Try a different filter.' : 'Assignments from your classes will appear here.'));
    }
    root.append(App.header('Assignments', { sub: 'Individual assignments from all your classes.' }), filters, listBox);
    render();
  });

  App.route('#/assignments/:id', S, async (root, params) => {
    const data = await App.load(root, () => Promise.all([
      Api.get('/api/assignments/' + params.id), Api.get('/api/assignments/' + params.id + '/my-submission')]));
    if (!data) return;
    const [a, sub] = data;
    root.appendChild(App.header(a.title, {
      crumbs: [{ label: 'Assignments', href: '#/assignments' }, { label: a.classroom.name, href: '#/classes/' + a.classroom.id }],
      sub: UI.CATEGORY_LABELS[a.category] + ' · Individual submission'
    }));
    root.appendChild(h('div', { class: 'layout-main-side' },
      h('div', { class: 'stack' },
        Shared.card('Instructions', h('div', { class: 'pre-wrap' }, a.description)),
        submitPanel(a, sub, '/api/assignments/' + a.id + '/submissions', false, () => App.render()),
        Shared.card('Submission history', Shared.versions(sub.versions))),
      h('div', { class: 'stack' },
        Shared.card('Details', Shared.policy(a)),
        Shared.card('Results & feedback', Shared.evaluations(sub.evaluations)),
        Shared.rubric(a.rubric))));
  });

  /** Individual submission panel used by assignments and individual projects. */
  function submitPanel(item, sub, url, withGithub, done) {
    const status = h('div', { class: 'row' }, h('span', { class: 'label' }, 'Status:'), statusBadge(sub.status),
      sub.versions.length ? h('span', { class: 'small muted' }, 'Last submitted ' + fmtDateTime(sub.versions[0].submittedAt)) : null);
    if (!sub.canSubmit) {
      return Shared.card('Your submission', h('div', { class: 'stack-sm' }, status,
        h('div', { class: 'alert alert-info' }, sub.blockedReason || 'Submissions are closed.')));
    }
    const picker = Shared.filePicker(item);
    const progress = Shared.progress();
    const form = h('form', { class: 'form', novalidate: true },
      UI.formAlert(),
      sub.status === 'REVISION_REQUESTED' ? h('div', { class: 'alert alert-warning' }, 'Your teacher requested a revision. Read the feedback and submit an updated version.') : null,
      sub.nextSubmissionLate ? h('div', { class: 'alert alert-warning' }, 'The deadline has passed. This submission will be marked late.') : null,
      sub.versions.length && sub.status !== 'REVISION_REQUESTED' ? h('div', { class: 'alert alert-info' },
        'Resubmitting creates version ' + (sub.versions.length + 1) + '. Earlier versions are kept in the history.') : null,
      h('div', { class: 'field' }, h('span', { class: 'label' }, 'Files'), picker.el),
      withGithub ? UI.field({ label: 'GitHub repository URL (optional)', name: 'githubUrl', type: 'url',
        attrs: { placeholder: 'https://github.com/owner/repository', maxlength: 300 } }) : null,
      UI.field({ label: 'Notes for your teacher (optional)', name: 'notes', type: 'textarea', attrs: { maxlength: 5000, rows: 3 } }),
      progress.el,
      h('div', { class: 'btn-row end' }, h('button', { class: 'btn', type: 'submit' }, icon('upload'), sub.versions.length ? 'Review & resubmit' : 'Review & submit')));

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      clearErrors(form);
      const files = picker.files();
      const githubUrl = withGithub ? val(form, 'githubUrl').trim() : '';
      if (picker.invalid().length) return showErrors(form, { message: 'Remove the files marked as not allowed before submitting.' });
      if (!files.length && !githubUrl) {
        return showErrors(form, { message: withGithub ? 'Attach at least one file or provide a GitHub repository URL.' : 'Attach at least one file to submit.' });
      }
      const ok = await Shared.confirmFiles('Review your submission', files, [
        githubUrl ? 'GitHub: ' + githubUrl : null,
        sub.nextSubmissionLate ? 'This submission will be marked late.' : null].filter(Boolean));
      if (!ok) return;
      const fd = new FormData();
      files.forEach((f) => fd.append('files', f, f.name));
      const notes = val(form, 'notes').trim();
      if (notes) fd.append('notes', notes);
      if (githubUrl) fd.append('githubUrl', githubUrl);
      await busy(form.querySelector('button[type=submit]'), async () => {
        try {
          await Api.upload(url, fd, (p) => progress.set(p));
          toast('Submitted successfully', 'success');
          App.refreshUnread();
          done();
        } catch (err) {
          progress.hide();
          showErrors(form, err);
        }
      });
    });
    return Shared.card('Your submission', h('div', { class: 'stack' }, status, form));
  }

  // ------------------------------------------------------------------ projects list + invitations

  App.route('#/projects', S, async (root, params, q) => {
    const data = await App.load(root, () => Promise.all([Api.get('/api/projects'), Api.get('/api/invitations')]));
    if (!data) return;
    const [projects, invitations] = data;
    let tab = q.get('tab') === 'invitations' ? 'invitations' : 'projects';
    const tabs = h('div', { class: 'tabs', role: 'tablist' });
    const body = h('div');

    function renderTabs() {
      clear(tabs);
      [['projects', 'Projects (' + projects.length + ')'], ['invitations', 'Team invitations (' + invitations.length + ')']].forEach(([k, label]) => {
        tabs.appendChild(h('button', { type: 'button', role: 'tab', 'aria-selected': String(tab === k), onClick: () => { tab = k; renderTabs(); renderBody(); } }, label));
      });
    }

    function renderBody() {
      clear(body);
      if (tab === 'projects') {
        body.appendChild(h('section', { class: 'card' }, projects.length ? h('ul', { class: 'list' }, projects.map(projectRow))
          : UI.empty('No projects yet', 'Individual and group projects from your classes appear here.')));
      } else {
        body.appendChild(invitationsCard(invitations, () => App.render()));
      }
    }
    root.append(App.header('My Projects', { sub: 'Individual and group projects from your classes.' }), tabs, body);
    renderTabs();
    renderBody();
  });

  function invitationsCard(invitations, after) {
    return h('section', { class: 'card' }, invitations.length ? h('ul', { class: 'list' }, invitations.map((i) => {
      const accept = h('button', { class: 'btn btn-success btn-sm', type: 'button' }, 'Accept');
      const decline = h('button', { class: 'btn btn-secondary btn-sm', type: 'button' }, 'Decline');
      accept.addEventListener('click', () => busy(accept, async () => {
        try {
          await Api.post('/api/invitations/' + i.id + '/accept', {});
          toast('You joined ' + i.teamName, 'success');
          App.go('#/projects/' + i.projectId);
          if (after) after();
        } catch (err) { toast(err.message, 'error'); }
      }));
      decline.addEventListener('click', async () => {
        if (!await UI.confirm('Decline invitation', 'Decline the invitation to join "' + i.teamName + '"?', 'Decline', true)) return;
        try {
          await Api.post('/api/invitations/' + i.id + '/decline', {});
          toast('Invitation declined');
          if (after) after();
        } catch (err) { toast(err.message, 'error'); }
      });
      return h('li', null,
        h('div', { class: 'grow' },
          h('div', { class: 'title' }, i.teamName),
          h('div', { class: 'meta' }, h('a', { href: '#/projects/' + i.projectId }, i.projectTitle), ' · ' + i.className + ' · invited by ' + i.invitedBy + ' · ' + UI.relative(i.createdAt))),
        h('div', { class: 'btn-row' }, decline, accept));
    })) : UI.empty('No pending invitations', 'When a classmate invites you to a team, it will appear here.'));
  }

  // ------------------------------------------------------------------ project detail

  App.route('#/projects/:id', S, async (root, params) => {
    const p = await App.load(root, () => Api.get('/api/projects/' + params.id));
    if (!p) return;
    root.appendChild(App.header(p.title, {
      crumbs: [{ label: 'My Projects', href: '#/projects' }, { label: p.classroom.name, href: '#/classes/' + p.classroom.id }],
      sub: p.participationMode === 'GROUP'
        ? 'Group project · teams of up to ' + p.maxTeamSize + (p.teamFormation === 'TEACHER_ASSIGNED' ? ' · teams assigned by teacher' : ' · student-created teams')
        : 'Individual project'
    }));
    const side = h('div', { class: 'stack' }, Shared.card('Details', Shared.policy(p)), Shared.rubric(p.rubric));
    const main = h('div', { class: 'stack' }, Shared.card('Description & requirements', h('div', { class: 'pre-wrap' }, p.description)));
    root.appendChild(h('div', { class: 'layout-main-side' }, main, side));

    if (p.participationMode === 'INDIVIDUAL') {
      const sub = await Api.get('/api/projects/' + p.id + '/my-submission');
      main.append(submitPanel(p, sub, '/api/projects/' + p.id + '/submissions', true, () => App.render()),
        Shared.card('Submission history', Shared.versions(sub.versions)));
      side.insertBefore(Shared.card('Results & feedback', Shared.evaluations(sub.evaluations)), side.children[1] || null);
      return;
    }

    const mine = await Api.get('/api/projects/' + p.id + '/my-team');
    if (!mine.team) {
      main.appendChild(noTeamPanel(p, mine.invitations));
      return;
    }
    const ws = await Api.get('/api/teams/' + mine.team.id + '/workspace');
    renderWorkspace(main, side, p, ws);
  });

  function noTeamPanel(p, invitations) {
    const parts = [];
    if (invitations.length) {
      parts.push(h('div', null, h('h3', null, 'Invitations for this project'), invitationsCard(invitations, () => App.render())));
    }
    if (p.teamFormation === 'STUDENT_INVITE') {
      parts.push(h('div', null, h('h3', null, 'Join a team with a code'), joinCodeForm(p)));
      const form = h('form', { class: 'form', novalidate: true },
        UI.formAlert(),
        UI.field({ label: 'Team name', name: 'name', required: true, attrs: { maxlength: 100, placeholder: 'e.g. Team Quantum' } }),
        h('p', { class: 'small muted mb-0' }, 'You will be a member of the new team and get a team code to share with classmates. All members have equal permissions.'),
        h('div', { class: 'btn-row end' }, h('button', { class: 'btn', type: 'submit' }, icon('plus'), 'Create team')));
      form.addEventListener('submit', async (e) => {
        e.preventDefault();
        clearErrors(form);
        const name = val(form, 'name').trim();
        if (!name) return showErrors(form, { message: 'Enter a team name.', fieldErrors: { name: 'Team name is required.' } });
        await busy(form.querySelector('button[type=submit]'), async () => {
          try {
            await Api.post('/api/projects/' + p.id + '/teams', { name });
            toast('Team created', 'success');
            App.render();
          } catch (err) { showErrors(form, err); }
        });
      });
      parts.push(h('div', null, h('h3', null, 'Or create a new team'), form));
    } else {
      parts.push(h('div', { class: 'alert alert-info' }, 'Your teacher assigns teams for this project. You will be notified when you are added to a team.'));
    }
    return Shared.card('Your team', h('div', { class: 'stack' },
      h('div', { class: 'alert alert-warning' }, 'You are not in a team for this project yet.'), parts));
  }

  function joinCodeForm(p) {
    const form = h('form', { class: 'form', novalidate: true },
      UI.formAlert(),
      UI.field({ label: 'Team code', name: 'code', required: true, attrs: { maxlength: 20, autocomplete: 'off', placeholder: 'e.g. 7KQ2MXRA' },
        hint: 'Ask a member of the team for its code. You can join one team per project.' }),
      h('div', { class: 'btn-row end' }, h('button', { class: 'btn', type: 'submit' }, 'Join team')));
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      clearErrors(form);
      const code = val(form, 'code').trim();
      if (!code) return showErrors(form, { message: 'Enter the team code.', fieldErrors: { code: 'Team code is required.' } });
      await busy(form.querySelector('button[type=submit]'), async () => {
        try {
          const team = await Api.post('/api/projects/' + p.id + '/teams/join', { code });
          toast('You joined ' + team.name, 'success');
          App.render();
        } catch (err) { showErrors(form, err); }
      });
    });
    return form;
  }

  function joinCodeRow(team, canEdit, reload) {
    const regen = canEdit ? h('button', { class: 'btn btn-ghost btn-sm', type: 'button' }, 'New code') : null;
    if (regen) {
      regen.addEventListener('click', async () => {
        if (!await UI.confirm('Generate a new team code', 'The current code will stop working. Current members are not affected.', 'Generate', true)) return;
        try { await Api.post('/api/teams/' + team.id + '/join-code', {}); toast('New team code generated'); reload(); } catch (err) { toast(err.message, 'error'); }
      });
    }
    return h('div', { class: 'row' }, h('span', { class: 'small muted' }, 'Team code'), h('span', { class: 'join-code' }, team.joinCode),
      h('button', { class: 'btn btn-secondary btn-sm', type: 'button', onClick: () => UI.copyText(team.joinCode) }, icon('copy'), 'Copy'), regen,
      h('span', { class: 'small muted' }, 'Classmates in this class can join with this code while the team has open slots.'));
  }

  // ------------------------------------------------------------------ team workspace

  function renderWorkspace(main, side, p, ws) {
    const team = ws.team;
    const reload = () => App.render();

    // --- team card
    const memberList = h('ul', { class: 'list' }, team.members.map((m) => h('li', null,
      h('div', { class: 'row' }, h('span', { class: 'avatar', 'aria-hidden': 'true' }, UI.initials(m.fullName)),
        h('div', null, h('div', { class: 'title' }, m.fullName + (m.studentId === App.user.id ? ' (you)' : '')),
          h('div', { class: 'meta' }, (m.rollNumber ? m.rollNumber + ' · ' : '') + m.email))),
      h('span', { class: 'small muted' }, 'Joined ' + UI.fmtDate(m.joinedAt)))));
    const pendingList = team.pendingInvitations.length ? h('ul', { class: 'list' }, team.pendingInvitations.map((i) => {
      const cancel = h('button', { class: 'btn btn-ghost btn-sm', type: 'button', disabled: !ws.canEdit }, 'Withdraw');
      cancel.addEventListener('click', async () => {
        if (!await UI.confirm('Withdraw invitation', 'Withdraw the invitation sent to ' + i.invitee.fullName + '?', 'Withdraw', true)) return;
        try { await Api.del('/api/invitations/' + i.id); toast('Invitation withdrawn'); reload(); } catch (err) { toast(err.message, 'error'); }
      });
      return h('li', null, h('div', { class: 'grow' }, h('div', { class: 'title' }, i.invitee.fullName),
        h('div', { class: 'meta' }, i.invitee.email + ' · pending · invited by ' + i.invitedBy)), cancel);
    })) : null;

    const teamActions = [];
    if (p.teamFormation === 'STUDENT_INVITE' && ws.canEdit) {
      const leave = h('button', { class: 'btn btn-danger-outline btn-sm', type: 'button' }, 'Leave team');
      leave.addEventListener('click', async () => {
        const last = team.members.length === 1;
        if (!await UI.confirm('Leave team', 'Leave "' + team.name + '"? You will lose access to the shared workspace.' +
          (last ? '\n\nYou are the last member. If nothing has been uploaded or submitted, the team will be removed.' : '') +
          '\n\nSubmission and activity history is preserved' + (last ? ' unless the empty team is removed.' : '.'), 'Leave team', true)) return;
        try { await Api.post('/api/teams/' + team.id + '/leave', {}); toast('You left the team'); reload(); } catch (err) { toast(err.message, 'error'); }
      });
      teamActions.push(leave);
    }

    const teamBody = h('div', { class: 'stack' },
      h('div', { class: 'row' }, h('span', { class: 'badge badge-primary' }, team.members.length + ' member' + (team.members.length === 1 ? '' : 's')),
        h('span', { class: 'badge' }, team.slotsLeft + ' open slot' + (team.slotsLeft === 1 ? '' : 's')),
        h('span', { class: 'small muted' }, 'Maximum team size ' + team.maxTeamSize + ' (pending invitations reserve slots)')),
      team.joinCode ? joinCodeRow(team, ws.canEdit, reload) : null,
      h('div', { class: 'card' }, memberList),
      pendingList ? h('div', null, h('h3', null, 'Pending invitations'), h('div', { class: 'card' }, pendingList)) : null,
      p.teamFormation === 'STUDENT_INVITE' && ws.canEdit && team.slotsLeft > 0 ? inviteForm(p, team, reload) : null,
      p.teamFormation === 'TEACHER_ASSIGNED' ? h('p', { class: 'small muted mb-0' }, 'Team membership for this project is managed by your teacher.') : null);
    main.appendChild(Shared.card('Team: ' + team.name, teamBody, teamActions.length ? h('div', { class: 'btn-row' }, teamActions) : null));

    // --- workspace files
    main.appendChild(filesCard(p, ws, reload));
    // --- links & notes
    main.appendChild(detailsCard(ws, team, reload));
    // --- final submission
    main.appendChild(teamSubmitCard(p, ws, team, reload));
    main.appendChild(Shared.card('Submission history', Shared.versions(ws.submission.versions)));

    side.insertBefore(Shared.card('Results & feedback', Shared.evaluations(ws.submission.evaluations,
      'Your team shares one set of marks and feedback, visible here once published.')), side.children[1] || null);
    side.appendChild(historyCard(ws, reload));
    side.appendChild(Shared.card('Activity', ws.activity.length ? h('ul', { class: 'timeline' }, ws.activity.map((a) =>
      h('li', null, h('div', null, a.details || a.action), h('div', { class: 'when' }, a.actor + ' · ' + fmtDateTime(a.createdAt)))))
      : UI.empty('No activity yet', 'Uploads, edits and submissions are recorded here.')));
  }

  function inviteForm(p, team, reload) {
    const select = h('select', { id: 'invite-pick', name: 'pick' }, h('option', { value: '' }, 'Loading classmates…'));
    const form = h('form', { class: 'form', novalidate: true },
      UI.formAlert(),
      h('div', { class: 'form-grid' },
        h('div', { class: 'field' }, h('label', { for: 'invite-pick' }, 'Choose a classmate'), select,
          h('div', { class: 'hint' }, 'Only enrolled classmates who are not yet in a team are listed.')),
        UI.field({ label: 'or enter their college email', name: 'email', type: 'email', attrs: { maxlength: 190 } })),
      h('div', { class: 'btn-row end' }, h('button', { class: 'btn', type: 'submit' }, 'Send invitation')));
    Api.get('/api/projects/' + p.id + '/eligible-students').then((list) => {
      clear(select).appendChild(h('option', { value: '' }, list.length ? 'Select a classmate' : 'No eligible classmates'));
      list.forEach((s) => select.appendChild(h('option', { value: s.email }, s.fullName + (s.rollNumber ? ' (' + s.rollNumber + ')' : ''))));
    }).catch(() => clear(select).appendChild(h('option', { value: '' }, 'Could not load classmates')));
    select.addEventListener('change', () => { if (select.value) form.elements.email.value = select.value; });
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      clearErrors(form);
      const email = val(form, 'email').trim() || select.value;
      if (!email) return showErrors(form, { message: 'Choose a classmate or enter an email.', fieldErrors: { email: 'Enter an email.' } });
      await busy(form.querySelector('button[type=submit]'), async () => {
        try {
          await Api.post('/api/teams/' + team.id + '/invitations', { email });
          toast('Invitation sent', 'success');
          reload();
        } catch (err) { showErrors(form, err); }
      });
    });
    return h('div', null, h('h3', null, 'Invite a classmate'), form);
  }

  function filesCard(p, ws, reload) {
    const byCat = {};
    ws.activeFiles.forEach((f) => { (byCat[f.category] = byCat[f.category] || []).push(f); });
    const groups = Object.keys(UI.WORKSPACE_LABELS).filter((c) => byCat[c]).map((c) => h('div', { class: 'stack-sm' },
      h('h3', { class: 'mb-0' }, UI.WORKSPACE_LABELS[c]),
      h('ul', { class: 'file-list' }, byCat[c].map((wf) => {
        const actions = ws.canEdit ? h('div', { class: 'btn-row' },
          h('button', { class: 'btn btn-secondary btn-sm', type: 'button', onClick: () => replaceDialog(p, ws, wf, reload) }, 'Replace'),
          h('button', { class: 'btn btn-danger-outline btn-sm', type: 'button', 'aria-label': 'Delete ' + wf.file.name, onClick: async () => {
            if (!await UI.confirm('Delete file', 'Delete "' + wf.file.name + '" from the workspace?\nIt stays in the file history and can be restored.', 'Delete', true)) return;
            try { await Api.del('/api/teams/' + ws.team.id + '/files/' + wf.id); toast('File deleted'); reload(); } catch (err) { toast(err.message, 'error'); }
          } }, icon('trash'))) : null;
        return Shared.fileItem(wf.file, actions);
      }))));

    const body = h('div', { class: 'stack' },
      groups.length ? groups : UI.empty('No files yet', 'Upload your report, presentation, documentation and source code ZIP.'),
      ws.canEdit ? uploadForm(ws, reload) : h('div', { class: 'alert alert-info' }, 'The workspace is locked because the project has been evaluated.'));
    return Shared.card('Shared workspace files', body);
  }

  function uploadForm(ws, reload) {
    const picker = Shared.filePicker(ws, { multiple: false });
    const progress = Shared.progress();
    const form = h('form', { class: 'form', novalidate: true },
      h('hr', { class: 'divider' }),
      h('h3', { class: 'mb-0' }, 'Upload a file'),
      UI.formAlert(),
      UI.field({ label: 'Category', name: 'category', type: 'select', value: 'REPORT',
        options: Object.entries(UI.WORKSPACE_LABELS).map(([value, label]) => ({ value, label })) }),
      picker.el, progress.el,
      h('div', { class: 'btn-row end' }, h('button', { class: 'btn', type: 'submit' }, icon('upload'), 'Upload')));
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      clearErrors(form);
      const files = picker.files();
      if (!files.length) return showErrors(form, { message: 'Choose a file to upload.' });
      if (picker.invalid().length) return showErrors(form, { message: 'This file type or size is not allowed.' });
      const fd = new FormData();
      fd.append('file', files[0], files[0].name);
      fd.append('category', val(form, 'category'));
      await busy(form.querySelector('button[type=submit]'), async () => {
        try {
          await Api.upload('/api/teams/' + ws.team.id + '/files', fd, (pct) => progress.set(pct));
          toast('File uploaded', 'success');
          reload();
        } catch (err) { progress.hide(); showErrors(form, err); }
      });
    });
    return form;
  }

  function replaceDialog(p, ws, wf, reload) {
    const picker = Shared.filePicker(ws, { multiple: false });
    const progress = Shared.progress();
    const alert = UI.formAlert();
    UI.modal({
      title: 'Replace "' + wf.file.name + '"',
      body: h('div', { class: 'stack-sm' }, h('p', { class: 'small muted mb-0' }, 'The current file is kept in the file history.'), alert, picker.el, progress.el),
      actions: [
        { label: 'Cancel', class: 'btn-secondary', onClick: (c) => c() },
        { label: 'Replace file', onClick: async (close) => {
          const files = picker.files();
          if (!files.length || picker.invalid().length) { alert.textContent = 'Choose an allowed file.'; alert.hidden = false; return; }
          const fd = new FormData();
          fd.append('file', files[0], files[0].name);
          fd.append('replaceId', wf.id);
          try {
            await Api.upload('/api/teams/' + ws.team.id + '/files', fd, (pct) => progress.set(pct));
            close();
            toast('File replaced', 'success');
            reload();
          } catch (err) { progress.hide(); alert.textContent = err.message; alert.hidden = false; }
        } }
      ]
    });
  }

  function detailsCard(ws, team, reload) {
    const form = h('form', { class: 'form', novalidate: true },
      UI.formAlert(),
      UI.field({ label: 'GitHub repository URL', name: 'githubUrl', type: 'url', value: ws.githubUrl || '',
        attrs: { placeholder: 'https://github.com/owner/repository', maxlength: 300, readOnly: !ws.canEdit } }),
      UI.field({ label: 'Project notes', name: 'notes', type: 'textarea', value: ws.notes || '',
        attrs: { maxlength: 20000, rows: 5, readOnly: !ws.canEdit },
        hint: ws.notesUpdatedBy ? 'Last edited by ' + ws.notesUpdatedBy + ' · ' + fmtDateTime(ws.notesUpdatedAt) : 'Shared with all team members.' }),
      ws.canEdit ? h('div', { class: 'btn-row end' }, h('button', { class: 'btn btn-secondary', type: 'submit' }, 'Save links & notes')) : null);
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      clearErrors(form);
      await busy(form.querySelector('button[type=submit]'), async () => {
        try {
          await Api.put('/api/teams/' + team.id + '/details', { githubUrl: val(form, 'githubUrl').trim() || null, notes: val(form, 'notes') });
          toast('Saved', 'success');
          reload();
        } catch (err) { showErrors(form, err); }
      });
    });
    return Shared.card('Repository & notes', form);
  }

  function teamSubmitCard(p, ws, team, reload) {
    const sub = ws.submission;
    const status = h('div', { class: 'row' }, h('span', { class: 'label' }, 'Team status:'), statusBadge(sub.status),
      sub.versions.length ? h('span', { class: 'small muted' }, 'Last submitted ' + fmtDateTime(sub.versions[0].submittedAt) + ' by ' + sub.versions[0].submittedBy) : null);
    if (!sub.canSubmit) {
      return Shared.card('Final submission', h('div', { class: 'stack-sm' }, status, h('div', { class: 'alert alert-info' }, sub.blockedReason || 'Submissions are closed.')));
    }
    const form = h('form', { class: 'form', novalidate: true },
      UI.formAlert(),
      h('p', { class: 'small muted mb-0' }, 'Any team member can submit. The submission records the workspace\'s current files, GitHub URL and notes as a new version.'),
      sub.status === 'REVISION_REQUESTED' ? h('div', { class: 'alert alert-warning' }, 'Your teacher requested a revision. Update the workspace and resubmit.') : null,
      sub.nextSubmissionLate ? h('div', { class: 'alert alert-warning' }, 'The deadline has passed. This submission will be marked late.') : null,
      UI.field({ label: 'Submission note (optional)', name: 'notes', type: 'textarea', attrs: { maxlength: 5000, rows: 2 },
        hint: 'Leave empty to use the workspace notes.' }),
      h('div', { class: 'btn-row end' }, h('button', { class: 'btn', type: 'submit' }, sub.versions.length ? 'Resubmit project' : 'Submit final project')));
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      clearErrors(form);
      if (!ws.activeFiles.length && !ws.githubUrl) return showErrors(form, { message: 'Upload at least one file or add a GitHub URL before submitting.' });
      const files = ws.activeFiles.map((f) => ({ name: f.file.name, size: f.file.sizeBytes }));
      const ok = await Shared.confirmFiles('Submit project for ' + team.name, files, [
        ws.githubUrl ? 'GitHub: ' + ws.githubUrl : null,
        'All ' + team.members.length + ' team members will share the marks and feedback.',
        sub.nextSubmissionLate ? 'This submission will be marked late.' : null].filter(Boolean));
      if (!ok) return;
      await busy(form.querySelector('button[type=submit]'), async () => {
        try {
          await Api.post('/api/teams/' + team.id + '/submit', { notes: val(form, 'notes').trim() || null });
          toast('Project submitted', 'success');
          App.refreshUnread();
          reload();
        } catch (err) { showErrors(form, err); }
      });
    });
    return Shared.card('Final submission', h('div', { class: 'stack' }, status, form));
  }

  function historyCard(ws, reload) {
    if (!ws.history.length) return Shared.card('File history', UI.empty('No history yet', 'Replaced and deleted files are kept here.'));
    return Shared.card('File history', h('ul', { class: 'file-list' }, ws.history.map((wf) => {
      const restore = wf.status === 'DELETED' && ws.canEdit ? h('button', { class: 'btn btn-secondary btn-sm', type: 'button', onClick: async () => {
        try { await Api.post('/api/teams/' + ws.team.id + '/files/' + wf.id + '/restore', {}); toast('File restored', 'success'); reload(); } catch (err) { toast(err.message, 'error'); }
      } }, icon('restore'), 'Restore') : null;
      const li = Shared.fileItem(wf.file, restore);
      li.querySelector('.meta').appendChild(document.createTextNode(' · ' + (wf.status === 'DELETED' ? 'deleted' : 'replaced') +
        (wf.removedBy ? ' by ' + wf.removedBy : '') + ' ' + UI.relative(wf.removedAt)));
      return li;
    })));
  }
})();
