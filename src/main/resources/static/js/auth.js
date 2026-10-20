/* Logic for the public authentication pages. Email addresses are passed between pages via sessionStorage, never the URL. */
(function () {
  'use strict';
  const { val, showErrors, clearErrors, busy } = UI;
  const page = document.body.dataset.page;

  const store = {
    set(k, v) { try { sessionStorage.setItem('cb.' + k, v); } catch (e) { /* storage unavailable */ } },
    take(k) { try { const v = sessionStorage.getItem('cb.' + k); sessionStorage.removeItem('cb.' + k); return v; } catch (e) { return null; } },
    get(k) { try { return sessionStorage.getItem('cb.' + k); } catch (e) { return null; } }
  };

  function flash(el, text) {
    if (!el || !text) return;
    el.textContent = text;
    el.hidden = false;
  }

  function roleToggle(onChange, initial) {
    const group = document.getElementById('role-toggle');
    let role = initial || 'STUDENT';
    const buttons = group.querySelectorAll('button');
    function set(r) {
      role = r;
      buttons.forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.role === r)));
      onChange(r);
    }
    buttons.forEach((b) => b.addEventListener('click', () => set(b.dataset.role)));
    set(role);
    return () => role;
  }

  function localError(form, fieldErrors, message) {
    showErrors(form, { message: message || 'Please correct the highlighted fields.', fieldErrors });
  }

  async function redirectIfSignedIn() {
    try {
      await Api.get('/api/auth/me', { skipAuthRedirect: true });
      window.location.replace('/app.html');
    } catch (e) { /* not signed in */ }
  }

  // ------------------------------------------------------------------ login
  if (page === 'login') {
    redirectIfSignedIn();
    Api.ensureCsrf();
    const form = document.getElementById('login-form');
    const btn = document.getElementById('login-btn');
    const reg = document.getElementById('register-link');
    const verifyHelp = document.getElementById('verify-help');
    flash(document.getElementById('flash'), store.take('flash'));
    const remembered = store.get('lastRole');
    const getRole = roleToggle((r) => {
      btn.textContent = r === 'TEACHER' ? 'Sign in as teacher' : 'Sign in as student';
      reg.textContent = r === 'TEACHER' ? 'Create a teacher account' : 'Create a student account';
      reg.href = '/register.html';
      reg.onclick = () => store.set('registerRole', r);
    }, remembered === 'TEACHER' ? 'TEACHER' : 'STUDENT');

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      clearErrors(form);
      verifyHelp.hidden = true;
      const email = val(form, 'email').trim();
      const password = val(form, 'password');
      const fe = {};
      if (!email) fe.email = 'Email is required.';
      if (!password) fe.password = 'Password is required.';
      if (Object.keys(fe).length) return localError(form, fe);
      await busy(btn, async () => {
        try {
          await Api.post('/api/auth/login', { email, password, role: getRole() }, { skipAuthRedirect: true });
          store.set('lastRole', getRole());
          window.location.replace('/app.html');
        } catch (err) {
          if (err.code === 'EMAIL_NOT_VERIFIED') {
            store.set('verifyEmail', email);
            verifyHelp.hidden = false;
            clearErrors(form);
          } else {
            showErrors(form, err);
          }
        }
      });
    });
  }

  // ------------------------------------------------------------------ register
  if (page === 'register') {
    redirectIfSignedIn();
    const form = document.getElementById('register-form');
    const btn = document.getElementById('register-btn');
    const hint = document.getElementById('domain-hint');
    let domains = { studentEmailDomains: [], teacherEmailDomains: [] };
    Api.get('/api/auth/config', { skipAuthRedirect: true }).then((c) => { domains = c; updateHint(getRole()); }).catch(() => {});

    function updateHint(r) {
      const list = r === 'TEACHER' ? domains.teacherEmailDomains : domains.studentEmailDomains;
      hint.textContent = list && list.length ? 'Allowed domains: ' + list.join(', ') : 'Use your official college email address.';
    }

    const getRole = roleToggle((r) => {
      form.querySelectorAll('.student-only').forEach((el) => { el.hidden = r !== 'STUDENT'; });
      form.querySelectorAll('.teacher-only').forEach((el) => { el.hidden = r !== 'TEACHER'; });
      document.getElementById('rollNumber').required = r === 'STUDENT';
      btn.textContent = r === 'TEACHER' ? 'Create teacher account' : 'Create student account';
      updateHint(r);
      clearErrors(form);
    }, store.take('registerRole') === 'TEACHER' ? 'TEACHER' : 'STUDENT');

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      clearErrors(form);
      const role = getRole();
      const fe = {};
      const fullName = val(form, 'fullName').trim();
      const email = val(form, 'email').trim();
      const password = val(form, 'password');
      if (!fullName) fe.fullName = 'Full name is required.';
      if (!email || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) fe.email = 'Enter a valid email address.';
      if (!/^(?=.*[A-Za-z])(?=.*\d).{8,72}$/.test(password)) fe.password = 'Password must be 8-72 characters and contain a letter and a digit.';
      if (password !== val(form, 'confirmPassword')) fe.confirmPassword = 'Passwords do not match.';
      let url;
      let body;
      if (role === 'STUDENT') {
        const rollNumber = val(form, 'rollNumber').trim();
        if (!rollNumber) fe.rollNumber = 'Roll number is required.';
        url = '/api/auth/register/student';
        body = { fullName, email, password, rollNumber, branch: val(form, 'branch').trim() || null,
          yearOfStudy: UI.intOrNull(val(form, 'yearOfStudy')), semester: UI.intOrNull(val(form, 'semester')) };
      } else {
        url = '/api/auth/register/teacher';
        body = { fullName, email, password, collegeName: val(form, 'collegeName').trim() || null,
          designation: val(form, 'designation').trim() || null };
      }
      if (Object.keys(fe).length) return localError(form, fe);
      await busy(btn, async () => {
        try {
          const res = await Api.post(url, body, { skipAuthRedirect: true });
          store.set('verifyEmail', res.email);
          store.set('flash', res.message);
          window.location.href = '/verify.html';
        } catch (err) {
          showErrors(form, err);
        }
      });
    });
  }

  // ------------------------------------------------------------------ verify
  if (page === 'verify') {
    const form = document.getElementById('verify-form');
    const resend = document.getElementById('resend-btn');
    const flashEl = document.getElementById('flash');
    form.elements.email.value = store.get('verifyEmail') || '';
    flash(flashEl, store.take('flash'));
    (form.elements.email.value ? form.elements.code : form.elements.email).focus();

    let timer = null;
    function cooldown(seconds) {
      let left = seconds;
      resend.disabled = true;
      resend.textContent = 'Resend code (' + left + 's)';
      clearInterval(timer);
      timer = setInterval(() => {
        left -= 1;
        if (left <= 0) {
          clearInterval(timer);
          resend.disabled = false;
          resend.textContent = 'Resend code';
        } else {
          resend.textContent = 'Resend code (' + left + 's)';
        }
      }, 1000);
    }

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      clearErrors(form);
      const email = val(form, 'email').trim();
      const code = val(form, 'code').trim();
      const fe = {};
      if (!email) fe.email = 'Email is required.';
      if (!/^\d{6}$/.test(code)) fe.code = 'Enter the 6-digit code.';
      if (Object.keys(fe).length) return localError(form, fe);
      await busy(form.querySelector('button[type=submit]'), async () => {
        try {
          const res = await Api.post('/api/auth/verify', { email, code }, { skipAuthRedirect: true });
          store.set('flash', res.message);
          store.take('verifyEmail');
          window.location.href = '/';
        } catch (err) {
          showErrors(form, err);
        }
      });
    });

    resend.addEventListener('click', async () => {
      clearErrors(form);
      const email = val(form, 'email').trim();
      if (!email) return localError(form, { email: 'Enter your email first.' });
      try {
        const res = await Api.post('/api/auth/resend-verification', { email }, { skipAuthRedirect: true });
        flashEl.className = 'alert alert-info';
        flash(flashEl, res.message);
        store.set('verifyEmail', email);
        cooldown(60);
      } catch (err) {
        showErrors(form, err);
      }
    });
  }

  // ------------------------------------------------------------------ forgot password
  if (page === 'forgot') {
    const form = document.getElementById('forgot-form');
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      clearErrors(form);
      const email = val(form, 'email').trim();
      if (!email) return localError(form, { email: 'Email is required.' });
      await busy(form.querySelector('button[type=submit]'), async () => {
        try {
          const res = await Api.post('/api/auth/forgot-password', { email }, { skipAuthRedirect: true });
          store.set('resetEmail', email);
          store.set('flash', res.message);
          window.location.href = '/reset-password.html';
        } catch (err) {
          showErrors(form, err);
        }
      });
    });
  }

  // ------------------------------------------------------------------ reset password
  if (page === 'reset') {
    const form = document.getElementById('reset-form');
    form.elements.email.value = store.get('resetEmail') || '';
    flash(document.getElementById('flash'), store.take('flash'));
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      clearErrors(form);
      const email = val(form, 'email').trim();
      const code = val(form, 'code').trim();
      const newPassword = val(form, 'newPassword');
      const fe = {};
      if (!email) fe.email = 'Email is required.';
      if (!/^\d{6}$/.test(code)) fe.code = 'Enter the 6-digit code.';
      if (!/^(?=.*[A-Za-z])(?=.*\d).{8,72}$/.test(newPassword)) fe.newPassword = 'Password must be 8-72 characters and contain a letter and a digit.';
      if (newPassword !== val(form, 'confirmPassword')) fe.confirmPassword = 'Passwords do not match.';
      if (Object.keys(fe).length) return localError(form, fe);
      await busy(form.querySelector('button[type=submit]'), async () => {
        try {
          const res = await Api.post('/api/auth/reset-password', { email, code, newPassword }, { skipAuthRedirect: true });
          store.take('resetEmail');
          store.set('flash', res.message);
          window.location.href = '/';
        } catch (err) {
          showErrors(form, err);
        }
      });
    });
  }

})();
