/*
 * Small UI toolkit: safe DOM building (text is never parsed as HTML), icons, modals, toasts, formatting.
 */
(function () {
  'use strict';

  /** h('div', {class: 'x', onClick: fn}, 'text', child, [more]) — children strings become text nodes. */
  function h(tag, props, ...children) {
    const el = document.createElement(tag);
    if (props) {
      for (const [k, v] of Object.entries(props)) {
        if (v === null || v === undefined || v === false) continue;
        if (k === 'class') el.className = v;
        else if (k === 'dataset') Object.assign(el.dataset, v);
        else if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2).toLowerCase(), v);
        else if (k === 'value') el.value = v;
        else if (k === 'checked' || k === 'selected' || k === 'disabled' || k === 'multiple' || k === 'required' || k === 'readOnly' || k === 'hidden') el[k] = !!v;
        else el.setAttribute(k, v === true ? '' : String(v));
      }
    }
    append(el, children);
    return el;
  }

  function append(el, children) {
    for (const c of children) {
      if (c === null || c === undefined || c === false) continue;
      if (Array.isArray(c)) append(el, c);
      else if (c instanceof Node) el.appendChild(c);
      else el.appendChild(document.createTextNode(String(c)));
    }
    return el;
  }

  function clear(el) {
    while (el.firstChild) el.removeChild(el.firstChild);
    return el;
  }

  // ---------- icons (inline SVG built with DOM APIs) ----------
  const ICONS = {
    dashboard: 'M3 13h8V3H3v10zm0 8h8v-6H3v6zm10 0h8V11h-8v10zm0-18v6h8V3h-8z',
    classes: 'M12 3L1 9l11 6 9-4.91V17h2V9L12 3zM5 13.18v4L12 21l7-3.82v-4L12 17l-7-3.82z',
    assignment: 'M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8l-6-6zm-1 7V3.5L18.5 9H13zM8 13h8v2H8v-2zm0 4h8v2H8v-2z',
    project: 'M10 4H4a2 2 0 00-2 2v12a2 2 0 002 2h16a2 2 0 002-2V8a2 2 0 00-2-2h-8l-2-2z',
    evaluation: 'M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z',
    bell: 'M12 22a2 2 0 002-2h-4a2 2 0 002 2zm6-6V11c0-3.07-1.64-5.64-4.5-6.32V4a1.5 1.5 0 00-3 0v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z',
    user: 'M12 12a4 4 0 100-8 4 4 0 000 8zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z',
    settings: 'M19.14 12.94a7.07 7.07 0 000-1.88l2.03-1.58a.5.5 0 00.12-.64l-1.92-3.32a.5.5 0 00-.6-.22l-2.39.96a7 7 0 00-1.63-.94l-.36-2.54A.5.5 0 0013.9 2h-3.84a.5.5 0 00-.49.42l-.36 2.54a7.3 7.3 0 00-1.63.94l-2.39-.96a.5.5 0 00-.6.22L2.67 8.48a.5.5 0 00.12.64l2.03 1.58a7.07 7.07 0 000 1.88l-2.03 1.58a.5.5 0 00-.12.64l1.92 3.32c.13.22.39.3.6.22l2.39-.96c.5.38 1.05.7 1.63.94l.36 2.54c.05.24.25.42.49.42h3.84c.24 0 .45-.18.49-.42l.36-2.54a7.3 7.3 0 001.63-.94l2.39.96c.22.08.47 0 .6-.22l1.92-3.32a.5.5 0 00-.12-.64l-2.03-1.58zM12 15.6a3.6 3.6 0 110-7.2 3.6 3.6 0 010 7.2z',
    logout: 'M10.09 15.59L11.5 17l5-5-5-5-1.41 1.41L12.67 11H3v2h9.67l-2.58 2.59zM19 3H5a2 2 0 00-2 2v4h2V5h14v14H5v-4H3v4a2 2 0 002 2h14a2 2 0 002-2V5a2 2 0 00-2-2z',
    menu: 'M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z',
    plus: 'M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z',
    download: 'M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z',
    upload: 'M9 16h6v-6h4l-7-7-7 7h4v6zm-4 2h14v2H5v-2z',
    trash: 'M6 19a2 2 0 002 2h8a2 2 0 002-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z',
    team: 'M16 11a3 3 0 100-6 3 3 0 000 6zm-8 0a3 3 0 100-6 3 3 0 000 6zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5C15 14.17 10.33 13 8 13zm8 0c-.29 0-.62.02-.97.05A4.22 4.22 0 0117 16.5V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z',
    inbox: 'M19 3H5a2 2 0 00-2 2v14a2 2 0 002 2h14a2 2 0 002-2V5a2 2 0 00-2-2zm0 12h-4a3 3 0 01-6 0H5V5h14v10z',
    copy: 'M16 1H4a2 2 0 00-2 2v14h2V3h12V1zm3 4H8a2 2 0 00-2 2v14a2 2 0 002 2h11a2 2 0 002-2V7a2 2 0 00-2-2zm0 16H8V7h11v14z',
    restore: 'M13 3a9 9 0 00-9 9H1l3.89 3.89.07.14L9 12H6a7 7 0 112.05 4.95l-1.42 1.42A9 9 0 1013 3zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z',
    link: 'M3.9 12a3.1 3.1 0 013.1-3.1h4V7H7a5 5 0 000 10h4v-1.9H7A3.1 3.1 0 013.9 12zM8 13h8v-2H8v2zm9-6h-4v1.9h4a3.1 3.1 0 010 6.2h-4V17h4a5 5 0 000-10z'
  };

  function icon(name, cls) {
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('viewBox', '0 0 24 24');
    svg.setAttribute('fill', 'currentColor');
    svg.setAttribute('aria-hidden', 'true');
    svg.setAttribute('focusable', 'false');
    if (cls) svg.setAttribute('class', cls);
    const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    path.setAttribute('d', ICONS[name] || ICONS.inbox);
    svg.appendChild(path);
    return svg;
  }

  // ---------- formatting ----------
  const dateFmt = new Intl.DateTimeFormat(undefined, { day: '2-digit', month: 'short', year: 'numeric' });
  const dateTimeFmt = new Intl.DateTimeFormat(undefined, { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });

  function fmtDate(iso) { return iso ? dateFmt.format(new Date(iso)) : '—'; }
  function fmtDateTime(iso) { return iso ? dateTimeFmt.format(new Date(iso)) : '—'; }

  function relative(iso) {
    if (!iso) return '';
    const diff = new Date(iso).getTime() - Date.now();
    const abs = Math.abs(diff);
    const mins = Math.round(abs / 60000);
    const hours = Math.round(abs / 3600000);
    const days = Math.round(abs / 86400000);
    let s;
    if (mins < 1) s = 'just now';
    else if (mins < 60) s = mins + ' min';
    else if (hours < 48) s = hours + ' h';
    else s = days + ' days';
    if (s === 'just now') return s;
    return diff >= 0 ? 'in ' + s : s + ' ago';
  }

  function dueLabel(iso) {
    const diff = new Date(iso).getTime() - Date.now();
    if (diff < 0) return 'Deadline passed ' + relative(iso);
    return 'Due ' + relative(iso);
  }

  /** ISO instant -> value for <input type="datetime-local"> in local time. */
  function toLocalInput(iso) {
    if (!iso) return '';
    const d = new Date(iso);
    const pad = (n) => String(n).padStart(2, '0');
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + 'T' + pad(d.getHours()) + ':' + pad(d.getMinutes());
  }

  function fromLocalInput(value) {
    if (!value) return null;
    const d = new Date(value);
    return isNaN(d.getTime()) ? null : d.toISOString();
  }

  function fileSize(bytes) {
    if (bytes == null) return '';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  }

  function ext(name) {
    const i = (name || '').lastIndexOf('.');
    return i < 0 ? '' : name.slice(i + 1).toLowerCase();
  }

  function marks(value) {
    if (value === null || value === undefined) return '—';
    return String(Number(value)).replace(/\.0+$/, '');
  }

  function initials(name) {
    return (name || '?').split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join('');
  }

  const STATUS_LABELS = {
    NOT_SUBMITTED: 'Not submitted', SUBMITTED: 'Submitted', LATE: 'Submitted late', EVALUATED: 'Evaluated',
    REVISION_REQUESTED: 'Revision requested', REJECTED: 'Rejected', DRAFT: 'Draft', OVERDUE: 'Overdue'
  };
  const CATEGORY_LABELS = { ASSIGNMENT: 'Assignment', LAB_REPORT: 'Practical / Lab report', PROJECT_REPORT: 'Project report' };
  const DECISION_LABELS = { EVALUATED: 'Evaluated', REVISION_REQUESTED: 'Revision requested', REJECTED: 'Rejected' };
  const WORKSPACE_LABELS = { REPORT: 'Project report', PRESENTATION: 'Presentation / PPT', DOCUMENTATION: 'Documentation', SOURCE_CODE: 'Source code (ZIP)', OTHER: 'Other' };

  function statusBadge(status) {
    return h('span', { class: 'badge status-' + status }, STATUS_LABELS[status] || status);
  }

  // ---------- states ----------
  function loading(text) {
    return h('div', { class: 'loading', role: 'status' }, h('span', { class: 'spinner' }), text || 'Loading…');
  }

  function empty(title, text, action) {
    return h('div', { class: 'empty' }, icon('inbox'), h('div', { class: 'empty-title' }, title), text ? h('div', null, text) : null, action || null);
  }

  function errorBox(err) {
    return h('div', { class: 'alert alert-error', role: 'alert' }, (err && err.message) || 'Something went wrong.');
  }

  // ---------- toasts ----------
  let toastHost = null;
  function toast(message, type) {
    if (!toastHost) {
      toastHost = h('div', { class: 'toasts', role: 'status', 'aria-live': 'polite' });
      document.body.appendChild(toastHost);
    }
    const t = h('div', { class: 'toast ' + (type || '') }, message);
    toastHost.appendChild(t);
    setTimeout(() => t.remove(), type === 'error' ? 6000 : 3500);
  }

  // ---------- modal ----------
  /**
   * modal({title, body: Node, actions: [{label, class, onClick: async (close) => {}}], size})
   * Returns {close, el}. Escape and backdrop click close it; focus is trapped loosely and restored.
   */
  function modal(opts) {
    const previous = document.activeElement;
    const titleId = 'm-' + Math.random().toString(36).slice(2);
    const footer = h('div', { class: 'modal-footer' });
    const dialog = h('div', { class: 'modal' + (opts.size === 'lg' ? ' lg' : ''), role: 'dialog', 'aria-modal': 'true', 'aria-labelledby': titleId },
      h('div', { class: 'modal-header' },
        h('h2', { id: titleId }, opts.title),
        h('button', { class: 'close-x', type: 'button', 'aria-label': 'Close', onClick: () => close() }, '×')),
      h('div', { class: 'modal-body' }, opts.body),
      footer);
    const backdrop = h('div', { class: 'modal-backdrop' }, dialog);
    function onKey(e) { if (e.key === 'Escape') close(); }
    let closed = false;
    function close() {
      if (closed) return;
      closed = true;
      document.removeEventListener('keydown', onKey);
      backdrop.remove();
      if (opts.onClose) opts.onClose();
      if (previous && previous.focus) previous.focus();
    }
    (opts.actions || [{ label: 'Close', class: 'btn-secondary', onClick: (c) => c() }]).forEach((a) => {
      const btn = h('button', { type: 'button', class: 'btn ' + (a.class || '') }, a.label);
      btn.addEventListener('click', async () => {
        if (!a.onClick) return close();
        await busy(btn, () => a.onClick(close));
      });
      footer.appendChild(btn);
    });
    backdrop.addEventListener('mousedown', (e) => { if (e.target === backdrop) close(); });
    document.addEventListener('keydown', onKey);
    document.body.appendChild(backdrop);
    const focusable = dialog.querySelector('input, select, textarea, button:not(.close-x)');
    (focusable || dialog.querySelector('.close-x')).focus();
    return { close, el: dialog };
  }

  /** Confirmation dialog for destructive or irreversible actions. Resolves true/false. */
  function confirm(title, message, confirmLabel, danger) {
    return new Promise((resolve) => {
      let result = false;
      modal({
        title,
        body: h('p', { class: 'mb-0 pre-wrap' }, message),
        onClose: () => resolve(result),
        actions: [
          { label: 'Cancel', class: 'btn-secondary', onClick: (close) => close() },
          { label: confirmLabel || 'Confirm', class: danger ? 'btn-danger' : '', onClick: (close) => { result = true; close(); } }
        ]
      });
    });
  }

  /** Disables a button and shows a spinner while fn runs. */
  async function busy(btn, fn) {
    if (btn.disabled) return;
    const original = Array.from(btn.childNodes);
    btn.disabled = true;
    btn.setAttribute('aria-busy', 'true');
    clear(btn).appendChild(h('span', { class: 'spinner' }));
    btn.appendChild(document.createTextNode(' Working…'));
    try {
      return await fn();
    } finally {
      if (btn.isConnected) {
        clear(btn);
        original.forEach((n) => btn.appendChild(n));
        btn.disabled = false;
        btn.removeAttribute('aria-busy');
      }
    }
  }

  // ---------- forms ----------
  /** field({label, name, type, value, required, hint, options, attrs}) -> .field wrapper; control has id = name. */
  function field(o) {
    const id = o.id || ('f-' + o.name + '-' + Math.random().toString(36).slice(2, 7));
    let control;
    const attrs = Object.assign({ id, name: o.name }, o.attrs || {});
    if (o.required) attrs.required = true;
    if (o.type === 'textarea') {
      control = h('textarea', attrs);
      if (o.value != null) control.value = o.value;
    } else if (o.type === 'select') {
      control = h('select', attrs, (o.options || []).map((opt) =>
        h('option', { value: opt.value, selected: String(opt.value) === String(o.value ?? '') }, opt.label)));
    } else {
      attrs.type = o.type || 'text';
      control = h('input', attrs);
      if (o.value != null) control.value = o.value;
    }
    const errId = id + '-err';
    control.setAttribute('aria-describedby', errId);
    return h('div', { class: 'field' + (o.class ? ' ' + o.class : ''), dataset: { field: o.name } },
      h('label', { for: id, class: o.required ? 'req' : null }, o.label),
      control,
      o.hint ? h('div', { class: 'hint' }, o.hint) : null,
      h('div', { class: 'error', id: errId, 'aria-live': 'polite' }));
  }

  function clearErrors(form) {
    form.querySelectorAll('.field.invalid').forEach((f) => f.classList.remove('invalid'));
    form.querySelectorAll('.field .error').forEach((e) => { e.textContent = ''; });
    const alert = form.querySelector('.form-alert');
    if (alert) { alert.hidden = true; alert.textContent = ''; }
  }

  /** Shows an ApiError on a form: field errors next to fields, the message in .form-alert. */
  function showErrors(form, err) {
    clearErrors(form);
    const fe = (err && err.fieldErrors) || {};
    let first = null;
    Object.entries(fe).forEach(([name, msg]) => {
      const f = form.querySelector('[data-field="' + CSS.escape(name) + '"]');
      if (f) {
        f.classList.add('invalid');
        const e = f.querySelector('.error');
        if (e) e.textContent = msg;
        if (!first) first = f.querySelector('input, select, textarea');
      }
    });
    const alert = form.querySelector('.form-alert');
    if (alert) {
      alert.className = 'alert alert-error form-alert';
      alert.textContent = (err && err.message) || 'Something went wrong.';
      alert.hidden = false;
    } else {
      toast((err && err.message) || 'Something went wrong.', 'error');
    }
    if (first) first.focus();
  }

  function formAlert() {
    return h('div', { class: 'alert alert-error form-alert', role: 'alert', hidden: true });
  }

  function val(form, name) {
    const el = form.elements[name];
    if (!el) return null;
    if (el.type === 'checkbox') return el.checked;
    return el.value;
  }

  function intOrNull(v) {
    if (v === null || v === undefined || String(v).trim() === '') return null;
    const n = Number(v);
    return Number.isFinite(n) ? Math.trunc(n) : null;
  }

  function copyText(text) {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(() => toast('Copied to clipboard', 'success'), () => legacyCopy(text));
    } else {
      legacyCopy(text); // e.g. plain http on a LAN address, where the async clipboard API is unavailable
    }
  }

  function legacyCopy(text) {
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.readOnly = true;
    ta.setAttribute('aria-hidden', 'true');
    // CSSOM properties (not a style attribute), which the Content-Security-Policy allows.
    Object.assign(ta.style, { position: 'fixed', top: '0', left: '0', opacity: '0' });
    document.body.appendChild(ta);
    ta.select();
    let ok = false;
    try { ok = document.execCommand('copy'); } catch (e) { ok = false; }
    ta.remove();
    toast(ok ? 'Copied to clipboard' : 'Copy failed. Select the code and copy it manually.', ok ? 'success' : 'error');
  }

  function params() {
    return new URLSearchParams(window.location.search);
  }

  window.UI = {
    h, append, clear, icon, fmtDate, fmtDateTime, relative, dueLabel, toLocalInput, fromLocalInput, fileSize, ext,
    marks, initials, statusBadge, loading, empty, errorBox, toast, modal, confirm, busy, field, clearErrors,
    showErrors, formAlert, val, intOrNull, copyText, params,
    STATUS_LABELS, CATEGORY_LABELS, DECISION_LABELS, WORKSPACE_LABELS
  };
})();
