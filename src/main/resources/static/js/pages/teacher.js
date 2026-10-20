/* Teacher pages: dashboard, classes, assignments, projects, teams, submissions & evaluation. */
(function () {
  'use strict';
  const { h, clear, icon, fmtDateTime, statusBadge, marks, busy, toast, showErrors, clearErrors, val } = UI;
  const T = 'TEACHER';

  function stat(label, value, href) {
    const inner = [h('div', { class: 'label' }, label), h('div', { class: 'value' }, String(value))];
    return href ? h('a', { class: 'card stat link-card', href }, inner) : h('div', { class: 'card stat' }, inner);
  }

  // ------------------------------------------------------------------ dashboard

  App.route('#/dashboard', T, async (root) => {
    const d = await App.load(root, () => Api.get('/api/dashboard'));
    if (!d) return;
    root.appendChild(App.header('Welcome, ' + App.user.fullName.split(' ')[0], {
      pageTitle: 'Dashboard', sub: 'Overview of your classes, submissions and evaluations.',
      actions: [
        h('a', { class: 'btn btn-secondary', href: '#/classes?new=1' }, icon('plus'), 'New class'),
        h('a', { class: 'btn btn-secondary', href: '#/assignments/new' }, icon('plus'), 'New assignment'),
        h('a', { class: 'btn', href: '#/projects/new' }, icon('plus'), 'New project')]
    }));
    root.appendChild(h('div', { class: 'stats' },
      stat('Classes', d.classes.length, '#/classes'),
      stat('Enrolled students', d.totalStudents),
      stat('Assignments', d.assignmentsCreated, '#/assignments'),
      stat('Projects', d.projectsCreated, '#/projects'),
      stat('Pending submissions', d.pendingSubmissions, '#/evaluation?status=PENDING'),
      stat('Drafts to publish', d.awaitingPublication, '#/evaluation')));

    const queueItem = (s) => h('li', null,
      h('div', { class: 'grow' }, h('a', { class: 'title', href: '#/evaluation/' + s.submissionId }, s.title),
        h('div', { class: 'meta' }, s.owner + ' · ' + s.className + ' · ' + fmtDateTime(s.submittedAt))),
      statusBadge(s.status));

    root.appendChild(h('div', { class: 'grid grid-2' },
      Shared.listCard('Evaluations awaiting completion', d.pendingQueue.map(queueItem),
        UI.empty('No pending submissions', 'New submissions from your students appear here.'),
        h('a', { href: '#/evaluation?status=PENDING', class: 'small' }, 'Open queue')),
      Shared.listCard('Recently evaluated', d.recentlyEvaluated.map(queueItem),
        UI.empty('Nothing evaluated yet', 'Published evaluations appear here.'))));

    root.appendChild(h('div', { class: 'grid grid-2 mt' },
      Shared.listCard('Upcoming deadlines', d.upcomingDeadlines.map((x) => h('li', null,
        h('div', { class: 'grow' }, h('a', { class: 'title', href: (x.kind === 'ASSIGNMENT' ? '#/assignments/' : '#/projects/') + x.id }, x.title),
          h('div', { class: 'meta' }, (x.kind === 'ASSIGNMENT' ? 'Assignment' : x.group ? 'Group project' : 'Project') + ' · ' + x.className)),
        h('span', { class: 'small muted nowrap', title: fmtDateTime(x.deadline) }, UI.dueLabel(x.deadline)))),
        UI.empty('No upcoming deadlines', 'Create an assignment or project to get started.')),
      Shared.listCard('My classes', d.classes.map((c) => h('li', null,
        h('div', { class: 'grow' }, h('a', { class: 'title', href: '#/classes/' + c.id }, c.displayName), h('div', { class: 'meta' }, c.subjectName)),
        h('span', { class: 'badge' }, c.students + ' student' + (c.students === 1 ? '' : 's')))),
        UI.empty('No classes yet', 'Create a class and share its join code with students.',
          h('a', { class: 'btn', href: '#/classes?new=1' }, 'Create class')))));

    root.appendChild(h('div', { class: 'mt' }, h('section', { class: 'card' },
      h('div', { class: 'card-header' }, h('h2', null, 'Notifications'), h('a', { href: '#/notifications', class: 'small' }, 'View all')),
      d.notifications.length ? h('div', null, d.notifications.map((n) => Shared.notificationItem(n)))
        : UI.empty('No notifications', 'Enrollments and other updates appear here.'))));
  });

  // ------------------------------------------------------------------ classes

  function classDialog(existing, onSaved) {
    const form = h('form', { class: 'form', novalidate: true },
      UI.formAlert(),
      UI.field({ label: 'Class name', name: 'name', required: true, value: existing && existing.name, attrs: { maxlength: 150, placeholder: 'e.g. DLCS' } }),
      UI.field({ label: 'Subject name', name: 'subjectName', required: true, value: existing && existing.subjectName, attrs: { maxlength: 150, placeholder: 'e.g. Digital Logic and Computer Systems' } }),
      h('div', { class: 'form-grid' },
        UI.field({ label: 'Semester / year', name: 'semester', required: true, value: existing && existing.semester, attrs: { maxlength: 40, placeholder: 'e.g. Semester 7' } }),
        UI.field({ label: 'Division / section', name: 'division', value: existing && existing.division, attrs: { maxlength: 40, placeholder: 'Optional, e.g. Division A' } })),
      UI.field({ label: 'Description', name: 'description', type: 'textarea', value: existing && existing.description, attrs: { maxlength: 1000, rows: 3 } }));
    UI.modal({
      title: existing ? 'Edit class' : 'Create a class',
      body: form,
      actions: [
        { label: 'Cancel', class: 'btn-secondary', onClick: (c) => c() },
        { label: existing ? 'Save changes' : 'Create class', onClick: async (close) => {
          clearErrors(form);
          const body = { name: val(form, 'name').trim(), subjectName: val(form, 'subjectName').trim(), semester: val(form, 'semester').trim(),
            division: val(form, 'division').trim() || null, description: val(form, 'description').trim() || null };
          const fe = {};
          if (!body.name) fe.name = 'Class name is required.';
          if (!body.subjectName) fe.subjectName = 'Subject name is required.';
          if (!body.semester) fe.semester = 'Semester/year is required.';
          if (Object.keys(fe).length) return showErrors(form, { message: 'Please correct the highlighted fields.', fieldErrors: fe });
          try {
            const c = existing ? await Api.put('/api/classes/' + existing.id, body) : await Api.post('/api/classes', body);
            close();
            toast(existing ? 'Class updated' : 'Class created — share the join code ' + c.joinCode, 'success');
            onSaved(c);
          } catch (err) { showErrors(form, err); }
        } }
      ]
    });
  }

  App.route('#/classes', T, async (root, params, q) => {
    const classes = await App.load(root, () => Api.get('/api/classes'));
    if (!classes) return;
    const create = h('button', { class: 'btn', type: 'button', onClick: () => classDialog(null, (c) => App.go('#/classes/' + c.id)) }, icon('plus'), 'New class');
    root.appendChild(App.header('My Classes', { sub: 'Each class is a teaching instance of a subject with its own join code, students and work.', actions: [create] }));
    root.appendChild(classes.length ? h('div', { class: 'grid grid-cards' }, classes.map((c) =>
      h('a', { class: 'card link-card class-card', href: '#/classes/' + c.id },
        h('div', { class: 'card-body' },
          h('h3', { class: 'mb-0' }, c.displayName),
          h('div', { class: 'subject' }, c.subjectName),
          h('div', null, h('span', { class: 'small muted' }, 'Join code '), h('span', { class: 'join-code' }, c.joinCode)),
          h('div', { class: 'meta' }, h('span', null, c.studentCount + ' students'), h('span', null, c.assignmentCount + ' assignments'), h('span', null, c.projectCount + ' projects')),
          c.archived ? h('span', { class: 'badge' }, 'Archived') : null))))
      : h('div', { class: 'card' }, UI.empty('No classes yet', 'Create your first class, then share its join code with your students.', create.cloneNode(true))));
    const emptyBtn = root.querySelector('.empty .btn');
    if (emptyBtn) emptyBtn.addEventListener('click', () => classDialog(null, (c) => App.go('#/classes/' + c.id)));
    if (q.get('new') === '1') classDialog(null, (c) => App.go('#/classes/' + c.id));
  });

  App.route('#/classes/:id', T, async (root, params, q) => {
    const data = await App.load(root, () => Promise.all([
      Api.get('/api/classes/' + params.id), Api.get('/api/classes/' + params.id + '/students'),
      Api.get('/api/assignments' + Api.qs({ classId: params.id })), Api.get('/api/projects' + Api.qs({ classId: params.id }))]));
    if (!data) return;
    const [c, roster, assignments, projects] = data;
    const reload = () => App.render();

    const edit = h('button', { class: 'btn btn-secondary', type: 'button', onClick: () => classDialog(c, reload) }, 'Edit');
    const archive = h('button', { class: 'btn btn-secondary', type: 'button' }, c.archived ? 'Unarchive' : 'Archive');
    archive.addEventListener('click', async () => {
      const ok = await UI.confirm(c.archived ? 'Unarchive class' : 'Archive class', c.archived
        ? 'Students will be able to join this class again with its code.'
        : 'Archived classes stay visible but no longer accept new students or new work. Existing work is preserved.', c.archived ? 'Unarchive' : 'Archive');
      if (!ok) return;
      try { await Api.post('/api/classes/' + c.id + '/archive', { archived: !c.archived }); toast('Class updated', 'success'); reload(); } catch (err) { toast(err.message, 'error'); }
    });
    root.appendChild(App.header(c.displayName, {
      crumbs: [{ label: 'My Classes', href: '#/classes' }, { label: c.name }],
      sub: c.subjectName + (c.archived ? ' · Archived' : ''), actions: [edit, archive]
    }));

    const regen = h('button', { class: 'btn btn-ghost btn-sm', type: 'button' }, 'Generate new code');
    regen.addEventListener('click', async () => {
      if (!await UI.confirm('Generate a new join code', 'The current code will stop working. Students already enrolled are not affected.', 'Generate', true)) return;
      try { await Api.post('/api/classes/' + c.id + '/regenerate-code', {}); toast('New join code generated', 'success'); reload(); } catch (err) { toast(err.message, 'error'); }
    });
    root.appendChild(h('div', { class: 'card card-body row between' },
      h('div', null, h('div', { class: 'label' }, 'Join code'), h('span', { class: 'join-code join-code-lg' }, c.joinCode),
        h('div', { class: 'small muted' }, 'Students enter this code to join instantly.')),
      h('div', { class: 'btn-row' }, h('button', { class: 'btn btn-secondary btn-sm', type: 'button', onClick: () => UI.copyText(c.joinCode) }, icon('copy'), 'Copy'), regen)));
    if (c.description) root.appendChild(h('div', { class: 'card card-body pre-wrap mt' }, c.description));

    let tab = q.get('tab') || 'students';
    const tabs = h('div', { class: 'tabs mt', role: 'tablist' });
    const body = h('div');
    const tabDefs = [['students', 'Students (' + roster.length + ')'], ['assignments', 'Assignments (' + assignments.length + ')'], ['projects', 'Projects (' + projects.length + ')']];
    function renderTabs() {
      clear(tabs);
      tabDefs.forEach(([k, label]) => tabs.appendChild(h('button', { type: 'button', role: 'tab', 'aria-selected': String(tab === k),
        onClick: () => { tab = k; renderTabs(); renderBody(); } }, label)));
    }
    function renderBody() {
      clear(body);
      if (tab === 'students') {
        body.appendChild(h('section', { class: 'card' }, roster.length ? h('div', { class: 'table-wrap' }, h('table', { class: 'table' },
          h('thead', null, h('tr', null, h('th', null, 'Name'), h('th', null, 'Roll no.'), h('th', null, 'Email'), h('th', null, 'Branch'), h('th', null, 'Joined'), h('th', { class: 'actions' }, h('span', { class: 'sr-only' }, 'Actions')))),
          h('tbody', null, roster.map((s) => {
            const rm = h('button', { class: 'btn btn-danger-outline btn-sm', type: 'button' }, 'Remove');
            rm.addEventListener('click', async () => {
              if (!await UI.confirm('Remove student', 'Remove ' + s.fullName + ' from this class?\nThey will also leave any project teams in this class. Their submissions and grades are preserved.', 'Remove', true)) return;
              try { await Api.del('/api/classes/' + c.id + '/students/' + s.studentId); toast('Student removed'); reload(); } catch (err) { toast(err.message, 'error'); }
            });
            return h('tr', null, h('td', null, s.fullName), h('td', { class: 'mono' }, s.rollNumber || '—'), h('td', null, s.email),
              h('td', null, s.branch || '—'), h('td', { class: 'nowrap' }, UI.fmtDate(s.joinedAt)), h('td', { class: 'actions' }, rm));
          }))))
          : UI.empty('No students yet', 'Share the join code ' + c.joinCode + ' with your students.')));
      } else if (tab === 'assignments') {
        body.appendChild(Shared.listCard('Assignments', assignments.map(itemRow('assignments')),
          UI.empty('No assignments yet', 'Create an individual assignment for this class.'),
          c.archived ? null : h('a', { class: 'btn btn-sm', href: '#/assignments/new?classId=' + c.id }, icon('plus'), 'New assignment')));
      } else {
        body.appendChild(Shared.listCard('Projects', projects.map(itemRow('projects')),
          UI.empty('No projects yet', 'Create an individual or group project for this class.'),
          c.archived ? null : h('a', { class: 'btn btn-sm', href: '#/projects/new?classId=' + c.id }, icon('plus'), 'New project')));
      }
    }
    root.append(tabs, body);
    renderTabs();
    renderBody();
  });

  function itemRow(kind) {
    return (it) => h('li', null,
      h('div', { class: 'grow' },
        h('a', { class: 'title', href: '#/' + kind + '/' + it.id }, it.title),
        h('div', { class: 'meta' }, (kind === 'assignments' ? UI.CATEGORY_LABELS[it.category] : (it.participationMode === 'GROUP' ? 'Group project · max ' + it.maxTeamSize : 'Individual project')) +
          ' · ' + it.classroom.displayName + ' · ' + UI.dueLabel(it.deadline))),
      it.stats ? h('div', { class: 'row' },
        h('span', { class: 'badge' }, it.stats.submitted + '/' + it.stats.expected + ' submitted'),
        it.stats.pendingEvaluation ? h('span', { class: 'badge status-SUBMITTED' }, it.stats.pendingEvaluation + ' to evaluate') : null) : null);
  }

  // ------------------------------------------------------------------ assignment & project lists

  function listPage(kind) {
    return async (root, params, q) => {
      const data = await App.load(root, () => Promise.all([Api.get('/api/' + kind), Api.get('/api/classes')]));
      if (!data) return;
      const [items, classes] = data;
      let classFilter = q.get('classId') || '';
      const box = h('section', { class: 'card' });
      const filters = h('div', { class: 'filters' }, UI.field({ label: 'Class', name: 'classId', type: 'select', value: classFilter,
        options: [{ value: '', label: 'All classes' }].concat(classes.map((c) => ({ value: c.id, label: c.displayName }))) }));
      filters.querySelector('select').addEventListener('change', (e) => { classFilter = e.target.value; render(); });
      function render() {
        const list = items.filter((i) => !classFilter || String(i.classroom.id) === classFilter);
        clear(box).appendChild(list.length ? h('ul', { class: 'list' }, list.map(itemRow(kind)))
          : UI.empty(items.length ? 'No items for this class' : (kind === 'assignments' ? 'No assignments yet' : 'No projects yet'),
            classes.length ? 'Create one to publish it to your students.' : 'Create a class first.'));
      }
      const label = kind === 'assignments' ? 'Assignments' : 'Projects';
      root.append(App.header(label, {
        sub: kind === 'assignments' ? 'Individual assignments, practicals and reports.' : 'Individual and group projects.',
        actions: [h('a', { class: 'btn', href: '#/' + kind + '/new' + (classFilter ? '?classId=' + classFilter : '') }, icon('plus'), 'New ' + (kind === 'assignments' ? 'assignment' : 'project'))]
      }), filters, box);
      render();
    };
  }

  App.route('#/assignments', T, listPage('assignments'));
  App.route('#/projects', T, listPage('projects'));

  // ------------------------------------------------------------------ assignment / project form

  function rubricBuilder(initial, getMax) {
    let rows = (initial || []).map((r) => ({ title: r.title, description: r.description || '', maxMarks: r.maxMarks }));
    const box = h('div', { class: 'stack-sm' });
    const total = h('div', { class: 'rubric-total' });
    function updateTotal() {
      if (!rows.length) { total.textContent = 'No rubric: you will enter total marks directly.'; total.className = 'rubric-total muted'; return; }
      const sum = rows.reduce((a, r) => a + (Number(r.maxMarks) || 0), 0);
      const max = Number(getMax()) || 0;
      total.textContent = 'Criteria total: ' + sum + ' / ' + max + (sum !== max ? ' — must equal the maximum marks' : ' ✓');
      total.className = 'rubric-total' + (sum !== max ? ' bad' : '');
    }
    function render() {
      clear(box);
      rows.forEach((r, i) => {
        const t = h('input', { type: 'text', value: r.title, maxlength: 150, placeholder: 'Criterion', 'aria-label': 'Criterion ' + (i + 1) + ' title' });
        const d = h('input', { type: 'text', value: r.description, maxlength: 500, placeholder: 'Description (optional)', 'aria-label': 'Criterion ' + (i + 1) + ' description' });
        const m = h('input', { type: 'number', value: r.maxMarks, min: 1, max: 1000, placeholder: 'Marks', 'aria-label': 'Criterion ' + (i + 1) + ' marks' });
        t.addEventListener('input', () => { r.title = t.value; });
        d.addEventListener('input', () => { r.description = d.value; });
        m.addEventListener('input', () => { r.maxMarks = m.value; updateTotal(); });
        box.appendChild(h('div', { class: 'rubric-row' }, t, d, m,
          h('button', { type: 'button', class: 'btn btn-ghost btn-sm', 'aria-label': 'Remove criterion ' + (i + 1), onClick: () => { rows.splice(i, 1); render(); } }, icon('trash'))));
      });
      box.appendChild(h('div', { class: 'row between' },
        h('button', { type: 'button', class: 'btn btn-secondary btn-sm', onClick: () => { rows.push({ title: '', description: '', maxMarks: '' }); render(); } }, icon('plus'), 'Add criterion'),
        total));
      updateTotal();
    }
    render();
    return {
      el: box,
      refresh: updateTotal,
      value: () => rows.map((r) => ({ title: (r.title || '').trim(), description: (r.description || '').trim() || null, maxMarks: UI.intOrNull(r.maxMarks) })),
      check: () => {
        if (!rows.length) return null;
        if (rows.some((r) => !(r.title || '').trim() || !(UI.intOrNull(r.maxMarks) > 0))) return 'Every rubric criterion needs a title and marks of at least 1.';
        const sum = rows.reduce((a, r) => a + (UI.intOrNull(r.maxMarks) || 0), 0);
        if (sum !== Number(getMax())) return 'Rubric criteria must add up to the maximum marks.';
        return null;
      }
    };
  }

  function itemFormPage(kind) {
    const isAssignment = kind === 'assignments';
    return async (root, params, q) => {
      const data = await App.load(root, () => Promise.all([
        Api.get('/api/classes'), params.id ? Api.get('/api/' + kind + '/' + params.id) : Promise.resolve(null)]));
      if (!data) return;
      const [classes, existing] = data;
      const editing = !!existing;
      const label = isAssignment ? 'assignment' : 'project';
      const activeClasses = classes.filter((c) => !c.archived || (existing && existing.classroom.id === c.id));
      root.appendChild(App.header(editing ? 'Edit ' + label : 'New ' + label, {
        crumbs: [{ label: isAssignment ? 'Assignments' : 'Projects', href: '#/' + kind }].concat(editing ? [{ label: existing.title, href: '#/' + kind + '/' + existing.id }] : []),
        sub: isAssignment ? 'Assignments are always individual: each student submits and is graded separately.' : 'Projects can be individual or group work.'
      }));
      if (!activeClasses.length) {
        root.appendChild(h('div', { class: 'card' }, UI.empty('Create a class first', 'Work is published to a class.', h('a', { class: 'btn', href: '#/classes?new=1' }, 'Create class'))));
        return;
      }
      const e = existing || {};
      const defaultDeadline = new Date(Date.now() + 7 * 86400000);
      defaultDeadline.setHours(23, 59, 0, 0);
      const form = h('form', { class: 'form', novalidate: true });
      const maxMarksField = UI.field({ label: 'Maximum marks', name: 'maxMarks', type: 'number', required: true, value: e.maxMarks || (isAssignment ? 10 : 50), attrs: { min: 1, max: 1000 } });
      const rubric = rubricBuilder(e.rubric, () => val(form, 'maxMarks'));
      maxMarksField.querySelector('input').addEventListener('input', rubric.refresh);

      let groupBox = null;
      if (!isAssignment) {
        groupBox = h('div', { class: 'form-grid' },
          UI.field({ label: 'Team formation', name: 'teamFormation', type: 'select', required: true, value: e.teamFormation || '',
            options: [{ value: '', label: 'Select how teams are formed' },
              { value: 'STUDENT_INVITE', label: 'Student-created teams (a student creates a team and shares its join code)' },
              { value: 'TEACHER_ASSIGNED', label: 'Teacher-assigned teams (you create teams and assign students)' }] }),
          UI.field({ label: 'Maximum team size', name: 'maxTeamSize', type: 'number', required: true, value: e.maxTeamSize || '', attrs: { min: 2, max: 20, placeholder: '2 to 20' }, hint: 'Pending invitations count toward the limit.' }));
      }
      const modeField = isAssignment ? null : UI.field({ label: 'Participation', name: 'participationMode', type: 'select', required: true, value: e.participationMode || '',
        options: [{ value: '', label: 'Select individual or group' }, { value: 'INDIVIDUAL', label: 'Individual project' }, { value: 'GROUP', label: 'Group project' }] });
      if (modeField) {
        const sel = modeField.querySelector('select');
        const sync = () => { groupBox.hidden = sel.value !== 'GROUP'; };
        sel.addEventListener('change', sync);
        sync();
      }

      UI.append(form, [
        UI.formAlert(),
        h('div', { class: 'form-grid' },
          UI.field({ label: 'Class', name: 'classId', type: 'select', required: true, value: e.classroom ? e.classroom.id : (q.get('classId') || ''),
            options: [{ value: '', label: 'Select a class' }].concat(activeClasses.map((c) => ({ value: c.id, label: c.displayName }))),
            attrs: editing ? { disabled: true } : {}, hint: editing ? 'The class cannot be changed after creation.' : null, class: 'span-2' }),
          UI.field({ label: 'Title', name: 'title', required: true, value: e.title, attrs: { maxlength: 200 }, class: 'span-2' }),
          UI.field({ label: isAssignment ? 'Instructions & description' : 'Description & requirements', name: 'description', type: 'textarea', required: true,
            value: e.description, attrs: { maxlength: 20000, rows: 7 }, class: 'span-2' }),
          isAssignment ? UI.field({ label: 'Category', name: 'category', type: 'select', required: true, value: e.category || 'ASSIGNMENT',
            options: Object.entries(UI.CATEGORY_LABELS).map(([value, l]) => ({ value, label: l })) }) : modeField,
          UI.field({ label: 'Deadline', name: 'deadline', type: 'datetime-local', required: true, value: UI.toLocalInput(e.deadline || defaultDeadline.toISOString()),
            hint: 'Your local time.' }),
          maxMarksField,
          UI.field({ label: 'Maximum file size (MB)', name: 'maxFileSizeMb', type: 'number', required: true, value: e.maxFileSizeMb || (isAssignment ? 10 : 25), attrs: { min: 1, max: 50 } }),
          UI.field({ label: 'Permitted file types', name: 'allowedExtensions', required: true, class: 'span-2',
            value: e.allowedExtensions ? e.allowedExtensions.join(', ') : (isAssignment ? 'pdf, docx' : 'pdf, docx, pptx, zip'),
            hint: 'Comma separated, e.g. pdf, docx, pptx, zip, txt, java, py, ipynb, png, jpg.' })),
        groupBox,
        h('div', { class: 'stack-sm' },
          h('label', { class: 'check' }, h('input', { type: 'checkbox', name: 'allowResubmission', checked: editing ? e.allowResubmission : true }),
            h('span', null, h('strong', null, 'Allow resubmission'), h('span', { class: 'muted' }, ' — students can submit new versions until the deadline.'))),
          h('label', { class: 'check' }, h('input', { type: 'checkbox', name: 'allowLate', checked: editing ? e.allowLate : false }),
            h('span', null, h('strong', null, 'Accept late submissions'), h('span', { class: 'muted' }, ' — first submissions after the deadline are accepted and marked late.')))),
        h('div', { class: 'stack-sm' }, h('h3', { class: 'mb-0' }, 'Evaluation rubric (optional)'),
          h('p', { class: 'small muted mb-0' }, 'Define criteria specific to this ' + label + '. Criteria marks must add up to the maximum marks.'), rubric.el),
        h('div', { class: 'btn-row end' }, h('a', { class: 'btn btn-secondary', href: editing ? '#/' + kind + '/' + e.id : '#/' + kind }, 'Cancel'),
          h('button', { class: 'btn', type: 'submit' }, editing ? 'Save changes' : 'Publish ' + label))
      ]);

      form.addEventListener('submit', async (ev) => {
        ev.preventDefault();
        clearErrors(form);
        const fe = {};
        const body = {
          classId: editing ? e.classroom.id : UI.intOrNull(val(form, 'classId')),
          title: val(form, 'title').trim(),
          description: val(form, 'description').trim(),
          deadline: UI.fromLocalInput(val(form, 'deadline')),
          maxMarks: UI.intOrNull(val(form, 'maxMarks')),
          allowedExtensions: val(form, 'allowedExtensions').trim(),
          maxFileSizeMb: UI.intOrNull(val(form, 'maxFileSizeMb')),
          allowResubmission: val(form, 'allowResubmission'),
          allowLate: val(form, 'allowLate'),
          rubric: rubric.value()
        };
        if (isAssignment) {
          body.category = val(form, 'category');
        } else {
          body.participationMode = val(form, 'participationMode');
          if (!body.participationMode) fe.participationMode = 'Choose individual or group project.';
          if (body.participationMode === 'GROUP') {
            body.teamFormation = val(form, 'teamFormation');
            if (!body.teamFormation) fe.teamFormation = 'Choose how teams are formed.';
            body.maxTeamSize = UI.intOrNull(val(form, 'maxTeamSize'));
            if (body.maxTeamSize == null) fe.maxTeamSize = 'Choose the maximum team size.';
            else if (!(body.maxTeamSize >= 2 && body.maxTeamSize <= 20)) fe.maxTeamSize = 'Team size must be between 2 and 20.';
          }
        }
        if (!body.classId) fe.classId = 'Choose a class.';
        if (!body.title) fe.title = 'Title is required.';
        if (!body.description) fe.description = 'This field is required.';
        if (!body.deadline) fe.deadline = 'Choose a deadline.';
        else if (!editing && new Date(body.deadline) <= new Date()) fe.deadline = 'The deadline must be in the future.';
        if (!(body.maxMarks >= 1 && body.maxMarks <= 1000)) fe.maxMarks = 'Enter marks between 1 and 1000.';
        if (!(body.maxFileSizeMb >= 1 && body.maxFileSizeMb <= 50)) fe.maxFileSizeMb = 'Enter a size between 1 and 50 MB.';
        if (!body.allowedExtensions) fe.allowedExtensions = 'List at least one file type.';
        const rubricErr = rubric.check();
        if (Object.keys(fe).length || rubricErr) return showErrors(form, { message: rubricErr || 'Please correct the highlighted fields.', fieldErrors: fe });
        await busy(form.querySelector('button[type=submit]'), async () => {
          try {
            const saved = editing ? await Api.put('/api/' + kind + '/' + e.id, body) : await Api.post('/api/' + kind, body);
            toast(editing ? 'Changes saved' : (isAssignment ? 'Assignment' : 'Project') + ' published to the class', 'success');
            App.go('#/' + kind + '/' + saved.id);
          } catch (err) { showErrors(form, err); }
        });
      });
      root.appendChild(h('section', { class: 'card card-body' }, form));
    };
  }

  App.route('#/assignments/new', T, itemFormPage('assignments'));
  App.route('#/assignments/:id/edit', T, itemFormPage('assignments'));
  App.route('#/projects/new', T, itemFormPage('projects'));
  App.route('#/projects/:id/edit', T, itemFormPage('projects'));

  // ------------------------------------------------------------------ roster / submissions table

  function rosterTable(rows, opts) {
    opts = opts || {};
    if (!rows.length) return UI.empty(opts.emptyTitle || 'No submissions', opts.emptyText || 'Nothing to show for these filters.');
    return h('div', { class: 'table-wrap' }, h('table', { class: 'table' },
      h('thead', null, h('tr', null,
        h('th', null, rows.every((r) => r.ownerType === 'TEAM') ? 'Team' : rows.every((r) => r.ownerType === 'STUDENT') ? 'Student' : 'Student / team'),
        opts.showItem ? h('th', null, 'Work') : null,
        h('th', null, 'Status'), h('th', null, 'Submitted'), h('th', null, 'Marks'),
        h('th', { class: 'actions' }, h('span', { class: 'sr-only' }, 'Actions')))),
      h('tbody', null, rows.map((r) => {
        const ev = r.evaluation;
        let marksCell = '—';
        if (ev) {
          marksCell = h('span', null, ev.decision === 'EVALUATED' ? marks(ev.totalMarks) + ' / ' + r.maxMarks : UI.DECISION_LABELS[ev.decision],
            ev.published ? null : h('span', { class: 'badge status-DRAFT' }, 'Draft'));
        }
        return h('tr', null,
          h('td', null, h('div', { class: 'title' }, r.ownerName), h('div', { class: 'small muted' }, r.ownerDetail || '')),
          opts.showItem ? h('td', null, h('div', null, r.itemTitle), h('div', { class: 'small muted' }, (r.kind === 'ASSIGNMENT' ? 'Assignment' : 'Project') + ' · ' + r.className)) : null,
          h('td', null, statusBadge(r.status), r.late && r.status !== 'LATE' ? h('span', { class: 'badge status-LATE' }, 'Late') : null),
          h('td', { class: 'nowrap' }, r.lastSubmittedAt ? fmtDateTime(r.lastSubmittedAt) : '—', r.versionCount > 1 ? h('div', { class: 'small muted' }, r.versionCount + ' versions') : null),
          h('td', null, marksCell),
          h('td', { class: 'actions' }, r.submissionId && r.versionCount > 0
            ? h('a', { class: 'btn btn-sm ' + (r.status === 'SUBMITTED' || r.status === 'LATE' ? '' : 'btn-secondary'), href: '#/evaluation/' + r.submissionId },
              r.status === 'SUBMITTED' || r.status === 'LATE' ? 'Evaluate' : 'Open')
            : h('span', { class: 'small muted' }, 'Nothing submitted')));
      }))));
  }

  function statsRow(stats) {
    return h('div', { class: 'stats' },
      stat('Expected', stats.expected), stat('Submitted', stats.submitted), stat('To evaluate', stats.pendingEvaluation), stat('Evaluated', stats.evaluated));
  }

  function deleteButton(kind, item) {
    const btn = h('button', { class: 'btn btn-danger-outline', type: 'button' }, icon('trash'), 'Delete');
    btn.addEventListener('click', async () => {
      if (!await UI.confirm('Delete ' + (kind === 'assignments' ? 'assignment' : 'project'), 'Delete "' + item.title + '"? This cannot be undone.\nItems with submissions cannot be deleted.', 'Delete', true)) return;
      try { await Api.del('/api/' + kind + '/' + item.id); toast('Deleted'); App.go('#/' + kind); } catch (err) { toast(err.message, 'error'); }
    });
    return btn;
  }

  // ------------------------------------------------------------------ assignment detail (teacher)

  App.route('#/assignments/:id', T, async (root, params) => {
    const data = await App.load(root, () => Promise.all([
      Api.get('/api/assignments/' + params.id),
      Api.get('/api/evaluation/submissions' + Api.qs({ kind: 'ASSIGNMENT', itemId: params.id }))]));
    if (!data) return;
    const [a, rows] = data;
    root.appendChild(App.header(a.title, {
      crumbs: [{ label: 'Assignments', href: '#/assignments' }, { label: a.classroom.name, href: '#/classes/' + a.classroom.id }],
      sub: UI.CATEGORY_LABELS[a.category] + ' · Individual',
      actions: [h('a', { class: 'btn btn-secondary', href: '#/assignments/' + a.id + '/edit' }, 'Edit'), deleteButton('assignments', a)]
    }));
    root.appendChild(statsRow(a.stats));
    root.appendChild(h('div', { class: 'layout-main-side' },
      h('div', { class: 'stack' },
        Shared.card('Student submissions', rosterTable(rows, { emptyTitle: 'No students enrolled', emptyText: 'Students who join the class appear here.' })),
        Shared.card('Instructions', h('div', { class: 'pre-wrap' }, a.description))),
      h('div', { class: 'stack' }, Shared.card('Details', Shared.policy(a)), Shared.rubric(a.rubric))));
  });

  // ------------------------------------------------------------------ project detail (teacher)

  App.route('#/projects/:id', T, async (root, params) => {
    const p = await App.load(root, () => Api.get('/api/projects/' + params.id));
    if (!p) return;
    const group = p.participationMode === 'GROUP';
    const [rows, teams, unassigned] = await Promise.all([
      Api.get('/api/evaluation/submissions' + Api.qs({ kind: 'PROJECT', itemId: p.id })),
      group ? Api.get('/api/projects/' + p.id + '/teams') : Promise.resolve([]),
      group ? Api.get('/api/projects/' + p.id + '/eligible-students') : Promise.resolve([])]);
    root.appendChild(App.header(p.title, {
      crumbs: [{ label: 'Projects', href: '#/projects' }, { label: p.classroom.name, href: '#/classes/' + p.classroom.id }],
      sub: group ? 'Group project · max ' + p.maxTeamSize + ' per team · ' + (p.teamFormation === 'TEACHER_ASSIGNED' ? 'teacher-assigned teams' : 'student-created teams') : 'Individual project',
      actions: [h('a', { class: 'btn btn-secondary', href: '#/projects/' + p.id + '/edit' }, 'Edit'), deleteButton('projects', p)]
    }));
    root.appendChild(statsRow(p.stats));
    const main = h('div', { class: 'stack' });
    if (group) main.appendChild(teamsCard(p, teams, unassigned));
    main.append(Shared.card(group ? 'Team submissions' : 'Student submissions', rosterTable(rows, {
      emptyTitle: group ? 'No teams yet' : 'No students enrolled',
      emptyText: group ? 'Teams appear here once they are formed.' : 'Students who join the class appear here.' })),
    Shared.card('Description & requirements', h('div', { class: 'pre-wrap' }, p.description)));
    root.appendChild(h('div', { class: 'layout-main-side' }, main,
      h('div', { class: 'stack' }, Shared.card('Details', Shared.policy(p)), Shared.rubric(p.rubric))));
  });

  function teamsCard(p, teams, unassigned) {
    const reload = () => App.render();
    const create = h('button', { class: 'btn btn-sm', type: 'button' }, icon('plus'), 'Create team');
    create.addEventListener('click', () => {
      const form = h('form', { class: 'form', novalidate: true },
        UI.formAlert(),
        UI.field({ label: 'Team name', name: 'name', required: true, attrs: { maxlength: 100 } }),
        h('fieldset', { class: 'stack-sm' }, h('legend', { class: 'label' }, 'Members (optional, up to ' + p.maxTeamSize + ')'),
          unassigned.length ? unassigned.map((s) => h('label', { class: 'check' }, h('input', { type: 'checkbox', name: 'student', value: s.id }),
            h('span', null, s.fullName + (s.rollNumber ? ' (' + s.rollNumber + ')' : '')))) : h('p', { class: 'small muted mb-0' }, 'All enrolled students are already in teams.')));
      UI.modal({
        title: 'Create team', body: form,
        actions: [{ label: 'Cancel', class: 'btn-secondary', onClick: (c) => c() }, { label: 'Create team', onClick: async (close) => {
          clearErrors(form);
          const name = val(form, 'name').trim();
          const studentIds = Array.from(form.querySelectorAll('input[name=student]:checked')).map((i) => Number(i.value));
          if (!name) return showErrors(form, { message: 'Enter a team name.', fieldErrors: { name: 'Team name is required.' } });
          if (studentIds.length > p.maxTeamSize) return showErrors(form, { message: 'Select at most ' + p.maxTeamSize + ' students.' });
          try { await Api.post('/api/projects/' + p.id + '/teams', { name, studentIds }); close(); toast('Team created', 'success'); reload(); } catch (err) { showErrors(form, err); }
        } }]
      });
    });

    const cards = teams.map((t) => {
      const add = h('select', { 'aria-label': 'Add a student to ' + t.name },
        h('option', { value: '' }, unassigned.length ? 'Add student…' : 'No unassigned students'),
        unassigned.map((s) => h('option', { value: s.id }, s.fullName + (s.rollNumber ? ' (' + s.rollNumber + ')' : ''))));
      add.disabled = !unassigned.length || t.slotsLeft === 0;
      add.addEventListener('change', async () => {
        if (!add.value) return;
        try { await Api.post('/api/teams/' + t.id + '/members', { studentId: Number(add.value) }); toast('Student added', 'success'); reload(); } catch (err) { toast(err.message, 'error'); add.value = ''; }
      });
      const del = h('button', { class: 'btn btn-danger-outline btn-sm', type: 'button' }, 'Delete team');
      del.addEventListener('click', async () => {
        if (!await UI.confirm('Delete team', 'Delete "' + t.name + '"? Teams that have uploaded files or submitted cannot be deleted; remove members instead.', 'Delete', true)) return;
        try { await Api.del('/api/teams/' + t.id); toast('Team deleted'); reload(); } catch (err) { toast(err.message, 'error'); }
      });
      return h('div', { class: 'version' },
        h('div', { class: 'version-head' },
          h('div', null, h('strong', null, t.name), ' ', h('span', { class: 'small muted' }, t.members.length + '/' + t.maxTeamSize + ' members'),
            t.joinCode ? h('span', { class: 'small muted' }, ' · code ', h('span', { class: 'join-code' }, t.joinCode)) : null),
          h('div', { class: 'row' }, statusBadge(t.submission.status),
            h('a', { class: 'btn btn-secondary btn-sm', href: '#/teams/' + t.id }, 'Workspace'),
            t.submission.submissionId && t.submission.versionCount ? h('a', { class: 'btn btn-sm', href: '#/evaluation/' + t.submission.submissionId }, 'Evaluate') : null)),
        t.members.length ? h('ul', { class: 'list' }, t.members.map((m) => {
          const rm = h('button', { class: 'btn btn-ghost btn-sm', type: 'button', 'aria-label': 'Remove ' + m.fullName }, 'Remove');
          rm.addEventListener('click', async () => {
            if (!await UI.confirm('Remove member', 'Remove ' + m.fullName + ' from "' + t.name + '"? Submission history is preserved.', 'Remove', true)) return;
            try { await Api.del('/api/teams/' + t.id + '/members/' + m.studentId); toast('Member removed'); reload(); } catch (err) { toast(err.message, 'error'); }
          });
          return h('li', null, h('div', { class: 'grow' }, h('div', { class: 'title' }, m.fullName), h('div', { class: 'meta' }, (m.rollNumber ? m.rollNumber + ' · ' : '') + m.email)), rm);
        })) : h('p', { class: 'small muted mb-0' }, 'No members.'),
        t.pendingInvitations.length ? h('div', { class: 'small muted' }, 'Pending invitations: ' + t.pendingInvitations.map((i) => i.invitee.fullName).join(', ')) : null,
        h('div', { class: 'row between' }, add, del));
    });

    return Shared.card('Teams (' + teams.length + ')', h('div', { class: 'stack' },
      unassigned.length ? h('div', { class: 'alert alert-warning' }, unassigned.length + ' student(s) not in a team: ' + unassigned.map((s) => s.fullName).join(', ')) : null,
      cards.length ? cards : UI.empty('No teams yet', p.teamFormation === 'TEACHER_ASSIGNED' ? 'Create teams and assign students.' : 'Students create teams and share each team join code with classmates. You can also create teams.')),
    create);
  }

  // ------------------------------------------------------------------ team workspace (read-only for teachers)

  App.route('#/teams/:id', T, async (root, params) => {
    const ws = await App.load(root, () => Api.get('/api/teams/' + params.id + '/workspace'));
    if (!ws) return;
    const t = ws.team;
    root.appendChild(App.header('Workspace: ' + t.name, {
      crumbs: [{ label: 'Projects', href: '#/projects' }, { label: t.projectTitle, href: '#/projects/' + t.projectId }],
      sub: 'Read-only view of the team\'s shared workspace.'
    }));
    root.appendChild(h('div', { class: 'layout-main-side' },
      h('div', { class: 'stack' },
        Shared.card('Members', h('ul', { class: 'list' }, t.members.map((m) => h('li', null, h('div', { class: 'grow' }, h('div', { class: 'title' }, m.fullName), h('div', { class: 'meta' }, (m.rollNumber ? m.rollNumber + ' · ' : '') + m.email)))))),
        Shared.card('Current files', ws.activeFiles.length ? h('ul', { class: 'file-list' }, ws.activeFiles.map((wf) => Shared.fileItem(wf.file,
          h('span', { class: 'badge' }, UI.WORKSPACE_LABELS[wf.category])))) : UI.empty('No files', 'The team has not uploaded files yet.')),
        Shared.card('Repository & notes', h('dl', { class: 'dl' },
          h('dt', null, 'GitHub'), h('dd', null, ws.githubUrl ? h('a', { href: ws.githubUrl, target: '_blank', rel: 'noopener noreferrer' }, ws.githubUrl) : '—'),
          h('dt', null, 'Notes'), h('dd', { class: 'pre-wrap' }, ws.notes || '—'))),
        Shared.card('Submission history', Shared.versions(ws.submission.versions))),
      h('div', { class: 'stack' },
        Shared.card('Activity', ws.activity.length ? h('ul', { class: 'timeline' }, ws.activity.map((a) =>
          h('li', null, h('div', null, a.details || a.action), h('div', { class: 'when' }, a.actor + ' · ' + fmtDateTime(a.createdAt))))) : UI.empty('No activity', '')),
        Shared.card('File history', ws.history.length ? h('ul', { class: 'file-list' }, ws.history.map((wf) => Shared.fileItem(wf.file,
          h('span', { class: 'badge' }, wf.status === 'DELETED' ? 'Deleted' : 'Replaced')))) : UI.empty('No history', '')))));
  });

  // ------------------------------------------------------------------ submissions & evaluation list

  App.route('#/evaluation', T, async (root, params, q) => {
    const data = await App.load(root, () => Promise.all([Api.get('/api/classes'), Api.get('/api/assignments'), Api.get('/api/projects')]));
    if (!data) return;
    const [classes, assignments, projects] = data;
    const state = { classId: q.get('classId') || '', kind: q.get('kind') || '', itemId: q.get('itemId') || '', status: q.get('status') || '' };
    const box = h('section', { class: 'card' });
    const filters = h('div', { class: 'filters' });

    function itemOptions() {
      const list = [];
      if (state.kind !== 'PROJECT') assignments.filter((a) => !state.classId || String(a.classroom.id) === state.classId).forEach((a) => list.push({ value: 'ASSIGNMENT:' + a.id, label: 'Assignment · ' + a.title }));
      if (state.kind !== 'ASSIGNMENT') projects.filter((p) => !state.classId || String(p.classroom.id) === state.classId).forEach((p) => list.push({ value: 'PROJECT:' + p.id, label: 'Project · ' + p.title }));
      return [{ value: '', label: 'All work' }].concat(list);
    }

    function renderFilters() {
      clear(filters).append(
        UI.field({ label: 'Class', name: 'classId', type: 'select', value: state.classId,
          options: [{ value: '', label: 'All classes' }].concat(classes.map((c) => ({ value: c.id, label: c.displayName }))) }),
        UI.field({ label: 'Type', name: 'kind', type: 'select', value: state.kind,
          options: [{ value: '', label: 'Assignments & projects' }, { value: 'ASSIGNMENT', label: 'Assignments' }, { value: 'PROJECT', label: 'Projects' }] }),
        UI.field({ label: 'Assignment / project', name: 'item', type: 'select', value: state.itemId ? state.kind + ':' + state.itemId : '', options: itemOptions() }),
        UI.field({ label: 'Status', name: 'status', type: 'select', value: state.status, options: [
          { value: '', label: 'Any status' }, { value: 'PENDING', label: 'Pending evaluation' }, { value: 'EVALUATED', label: 'Evaluated' },
          { value: 'REVISION_REQUESTED', label: 'Revision requested' }, { value: 'REJECTED', label: 'Rejected' }, { value: 'NOT_SUBMITTED', label: 'Not submitted (select a work item)' }] }));
      filters.querySelector('[name=classId]').addEventListener('change', (e) => { state.classId = e.target.value; state.itemId = ''; renderFilters(); load(); });
      filters.querySelector('[name=kind]').addEventListener('change', (e) => { state.kind = e.target.value; state.itemId = ''; renderFilters(); load(); });
      filters.querySelector('[name=item]').addEventListener('change', (e) => {
        if (e.target.value) { const [k, id] = e.target.value.split(':'); state.kind = k; state.itemId = id; } else state.itemId = '';
        renderFilters(); load();
      });
      filters.querySelector('[name=status]').addEventListener('change', (e) => { state.status = e.target.value; load(); });
    }

    async function load() {
      const rows = await App.load(box, () => Api.get('/api/evaluation/submissions' + Api.qs({
        classId: state.classId, kind: state.kind, itemId: state.itemId, status: state.status })));
      if (!rows) return;
      box.appendChild(rosterTable(rows, { showItem: !state.itemId, mixed: true,
        emptyTitle: 'No submissions found', emptyText: state.itemId ? 'Nothing matches these filters.' : 'Submitted work from your classes appears here. Select a specific assignment or project to also see who has not submitted.' }));
    }

    root.append(App.header('Submissions & Evaluation', { sub: 'Review submitted work, assign marks, give feedback and publish results.' }), filters, box);
    renderFilters();
    load();
  });

  // ------------------------------------------------------------------ evaluation detail

  App.route('#/evaluation/:id', T, async (root, params) => {
    const d = await App.load(root, () => Api.get('/api/evaluation/submissions/' + params.id));
    if (!d) return;
    const itemHref = (d.item.kind === 'ASSIGNMENT' ? '#/assignments/' : '#/projects/') + d.item.id;
    const owner = d.team ? d.team.name : d.student.fullName;
    root.appendChild(App.header(owner, {
      pageTitle: 'Evaluate · ' + owner,
      crumbs: [{ label: 'Submissions & Evaluation', href: '#/evaluation' }, { label: d.item.title, href: itemHref }],
      sub: d.item.title + ' · ' + d.classroom.displayName
    }));
    const current = d.evaluations.find((e) => e.current);
    root.appendChild(h('div', { class: 'layout-main-side' },
      h('div', { class: 'stack' },
        Shared.card('Evaluate', evaluateForm(d, current)),
        Shared.card('Submitted versions', Shared.versions(d.versions)),
        Shared.card('Evaluation history', d.evaluations.length ? h('div', { class: 'stack-sm' }, d.evaluations.map((e) => {
          const el = Shared.evaluation(e);
          if (e.current && !e.published) {
            const pub = h('button', { class: 'btn btn-success btn-sm', type: 'button' }, 'Publish to ' + (d.team ? 'team' : 'student'));
            pub.addEventListener('click', async () => {
              if (!await UI.confirm('Publish evaluation', 'Publish this evaluation? ' + (d.team ? 'All current team members' : 'The student') + ' will be notified and can see it.', 'Publish')) return;
              await busy(pub, async () => {
                try { await Api.post('/api/evaluation/evaluations/' + e.id + '/publish', {}); toast('Evaluation published', 'success'); App.render(); } catch (err) { toast(err.message, 'error'); }
              });
            });
            el.appendChild(h('div', { class: 'btn-row end' }, pub));
          }
          return el;
        })) : UI.empty('Not evaluated yet', 'Saved evaluations are kept here; earlier records are never overwritten.'))),
      h('div', { class: 'stack' },
        Shared.card('Submission', h('dl', { class: 'dl' },
          h('dt', null, 'Status'), h('dd', null, statusBadge(d.status)),
          h('dt', null, d.team ? 'Team' : 'Student'), h('dd', null, owner),
          d.student ? [h('dt', null, 'Roll no.'), h('dd', null, d.student.rollNumber || '—'), h('dt', null, 'Email'), h('dd', null, d.student.email)] : null,
          h('dt', null, 'Work'), h('dd', null, h('a', { href: itemHref }, d.item.title)),
          h('dt', null, 'Deadline'), h('dd', null, fmtDateTime(d.item.deadline)),
          h('dt', null, 'Maximum marks'), h('dd', null, String(d.item.maxMarks)),
          h('dt', null, 'Versions'), h('dd', null, String(d.versions.length)))),
        d.team ? Shared.card('Team members', h('ul', { class: 'list' }, d.team.members.map((m) => h('li', null,
          h('div', { class: 'grow' }, h('div', { class: 'title' }, m.fullName), h('div', { class: 'meta' }, (m.rollNumber ? m.rollNumber + ' · ' : '') + m.email))))),
          h('a', { class: 'btn btn-secondary btn-sm', href: '#/teams/' + d.team.id }, 'Workspace')) : null,
        Shared.rubric(d.rubric))));
  });

  function evaluateForm(d, current) {
    if (!d.versions.length) return h('div', { class: 'alert alert-info' }, 'Nothing has been submitted yet.');
    const hasRubric = d.rubric.length > 0;
    const form = h('form', { class: 'form', novalidate: true });
    const decisions = [['EVALUATED', 'Award marks'], ['REVISION_REQUESTED', 'Request revision'], ['REJECTED', 'Reject']];
    let decision = current ? current.decision : 'EVALUATED';
    const seg = h('div', { class: 'segmented', role: 'group', 'aria-label': 'Decision' });
    const marksBox = h('div');
    const reasonField = UI.field({ label: 'Reason for rejection', name: 'reason', type: 'textarea', required: true,
      value: current && current.reason, attrs: { maxlength: 1000, rows: 3 }, hint: 'Required. Shown to the ' + (d.team ? 'team' : 'student') + '.' });
    const feedbackField = UI.field({ label: 'Written feedback', name: 'feedback', type: 'textarea', value: current && current.feedback,
      attrs: { maxlength: 10000, rows: 5 } });
    const totalOut = h('strong');
    const scoreInputs = {};

    if (hasRubric) {
      const prev = {};
      if (current && current.scores) current.scores.forEach((s) => { prev[s.criterionId] = s.score; });
      const rowsEl = d.rubric.map((c) => {
        const inp = h('input', { type: 'number', min: 0, max: c.maxMarks, step: '0.01', value: prev[c.id] != null ? Number(prev[c.id]) : '', 'aria-label': c.title + ' score out of ' + c.maxMarks });
        inp.addEventListener('input', updateTotal);
        scoreInputs[c.id] = inp;
        return h('tr', null, h('td', null, h('div', { class: 'title' }, c.title), c.description ? h('div', { class: 'small muted' }, c.description) : null),
          h('td', null, h('div', { class: 'row' }, inp, h('span', { class: 'small muted nowrap' }, '/ ' + c.maxMarks))));
      });
      marksBox.append(h('div', { class: 'table-wrap' }, h('table', { class: 'table rubric-table' },
        h('thead', null, h('tr', null, h('th', null, 'Criterion'), h('th', null, 'Score'))), h('tbody', null, rowsEl))),
      h('p', { class: 'mb-0' }, 'Total: ', totalOut, ' / ' + d.item.maxMarks));
    } else {
      marksBox.appendChild(UI.field({ label: 'Marks awarded (out of ' + d.item.maxMarks + ')', name: 'marks', type: 'number', required: true,
        value: current && current.totalMarks != null ? Number(current.totalMarks) : '', attrs: { min: 0, max: d.item.maxMarks, step: '0.01' } }));
    }

    function updateTotal() {
      if (!hasRubric) return;
      const sum = Object.values(scoreInputs).reduce((a, i) => a + (Number(i.value) || 0), 0);
      totalOut.textContent = String(Math.round(sum * 100) / 100);
    }

    function renderSeg() {
      clear(seg);
      decisions.forEach(([k, label]) => seg.appendChild(h('button', { type: 'button', 'aria-pressed': String(decision === k), onClick: () => { decision = k; renderSeg(); } }, label)));
      marksBox.hidden = decision !== 'EVALUATED';
      reasonField.hidden = decision !== 'REJECTED';
      feedbackField.querySelector('label').textContent = decision === 'REVISION_REQUESTED' ? 'What should be revised?' : 'Written feedback';
      feedbackField.querySelector('label').className = decision === 'REVISION_REQUESTED' ? 'req' : '';
    }

    const publish = h('label', { class: 'check' }, h('input', { type: 'checkbox', name: 'publish', checked: true }),
      h('span', null, h('strong', null, 'Publish now'), h('span', { class: 'muted' }, ' — ' + (d.team ? 'all current team members' : 'the student') + ' will be notified. Uncheck to save a draft.')));

    UI.append(form, [UI.formAlert(),
      current ? h('div', { class: 'alert alert-info' }, 'Saving creates a new evaluation record; the previous one stays in the history.') : null,
      h('div', { class: 'field' }, h('span', { class: 'label' }, 'Decision'), seg),
      marksBox, feedbackField, reasonField, publish,
      h('div', { class: 'btn-row end' }, h('button', { class: 'btn', type: 'submit' }, 'Save evaluation'))]);
    renderSeg();
    updateTotal();

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      clearErrors(form);
      const body = { decision, feedback: val(form, 'feedback').trim() || null, reason: val(form, 'reason').trim() || null, publish: val(form, 'publish') };
      const fe = {};
      let msg = null;
      if (decision === 'EVALUATED') {
        if (hasRubric) {
          body.scores = [];
          for (const c of d.rubric) {
            const raw = scoreInputs[c.id].value;
            const n = Number(raw);
            if (raw === '' || !Number.isFinite(n) || n < 0 || n > c.maxMarks) { msg = 'Enter a score between 0 and ' + c.maxMarks + ' for "' + c.title + '".'; break; }
            body.scores.push({ criterionId: c.id, score: n });
          }
        } else {
          const raw = val(form, 'marks');
          const n = Number(raw);
          if (raw === '' || !Number.isFinite(n) || n < 0 || n > d.item.maxMarks) fe.marks = 'Enter marks between 0 and ' + d.item.maxMarks + '.';
          else body.marks = n;
        }
      }
      if (decision === 'REJECTED' && !body.reason) fe.reason = 'A reason is required to reject.';
      if (decision === 'REVISION_REQUESTED' && !body.feedback) fe.feedback = 'Explain what needs to be revised.';
      if (msg || Object.keys(fe).length) return showErrors(form, { message: msg || 'Please correct the highlighted fields.', fieldErrors: fe });
      if (body.publish && decision === 'REJECTED') {
        if (!await UI.confirm('Reject submission', 'Rejection is final: the ' + (d.team ? 'team' : 'student') + ' will not be able to resubmit. Continue?', 'Reject', true)) return;
      }
      await busy(form.querySelector('button[type=submit]'), async () => {
        try {
          await Api.post('/api/evaluation/submissions/' + d.submissionId + '/evaluations', body);
          toast(body.publish ? 'Evaluation published' : 'Draft saved', 'success');
          App.render();
        } catch (err) { showErrors(form, err); }
      });
    });
    return form;
  }
})();
