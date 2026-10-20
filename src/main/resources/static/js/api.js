/*
 * CollabBoard API client. Same-origin fetch with the session cookie; state-changing requests send the
 * CSRF token from the XSRF-TOKEN cookie in the X-XSRF-TOKEN header.
 */
(function () {
  'use strict';

  class ApiError extends Error {
    constructor(status, code, message, fieldErrors) {
      super(message || 'Request failed');
      this.status = status;
      this.code = code;
      this.fieldErrors = fieldErrors || {};
    }
  }

  function getCookie(name) {
    const parts = document.cookie ? document.cookie.split('; ') : [];
    for (const p of parts) {
      const i = p.indexOf('=');
      if (p.substring(0, i) === name) return decodeURIComponent(p.substring(i + 1));
    }
    return null;
  }

  async function ensureCsrf() {
    if (!getCookie('XSRF-TOKEN')) {
      await fetch('/api/auth/csrf', { credentials: 'same-origin' });
    }
    return getCookie('XSRF-TOKEN');
  }

  async function parse(res) {
    if (res.status === 204) return null;
    const ct = res.headers.get('content-type') || '';
    if (ct.includes('application/json')) {
      try { return await res.json(); } catch (e) { return null; }
    }
    return null;
  }

  function toError(status, data) {
    const fallback = status === 0 ? 'Network error. Check your connection.' :
      status >= 500 ? 'Something went wrong on the server. Please try again.' : 'Request failed.';
    return new ApiError(status, data && data.code, (data && data.message) || fallback, data && data.fieldErrors);
  }

  async function request(method, url, body, opts) {
    opts = opts || {};
    const headers = { 'Accept': 'application/json' };
    if (method !== 'GET') headers['X-XSRF-TOKEN'] = await ensureCsrf();
    let payload;
    if (body !== undefined && body !== null) {
      if (body instanceof FormData) {
        payload = body;
      } else {
        headers['Content-Type'] = 'application/json';
        payload = JSON.stringify(body);
      }
    }
    let res;
    try {
      res = await fetch(url, { method, headers, body: payload, credentials: 'same-origin' });
    } catch (e) {
      throw toError(0, null);
    }
    const data = await parse(res);
    if (!res.ok) {
      if (res.status === 403 && data && data.code === 'CSRF_INVALID' && !opts.retried) {
        await fetch('/api/auth/csrf', { credentials: 'same-origin' });
        return request(method, url, body, { retried: true });
      }
      if (res.status === 401 && Api.onUnauthorized && !opts.skipAuthRedirect) Api.onUnauthorized();
      throw toError(res.status, data);
    }
    return data;
  }

  /** Multipart upload with progress callback (0..100). */
  async function upload(url, formData, onProgress) {
    const token = await ensureCsrf();
    return new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      xhr.open('POST', url);
      xhr.withCredentials = true;
      xhr.setRequestHeader('X-XSRF-TOKEN', token);
      xhr.setRequestHeader('Accept', 'application/json');
      if (xhr.upload && onProgress) {
        xhr.upload.onprogress = (e) => { if (e.lengthComputable) onProgress(Math.round((e.loaded / e.total) * 100)); };
      }
      xhr.onload = () => {
        let data = null;
        try { data = xhr.responseText ? JSON.parse(xhr.responseText) : null; } catch (e) { data = null; }
        if (xhr.status >= 200 && xhr.status < 300) resolve(data);
        else {
          if (xhr.status === 401 && Api.onUnauthorized) Api.onUnauthorized();
          reject(toError(xhr.status, data));
        }
      };
      xhr.onerror = () => reject(toError(0, null));
      xhr.send(formData);
    });
  }

  function qs(params) {
    const p = new URLSearchParams();
    Object.entries(params || {}).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') p.append(k, v); });
    const s = p.toString();
    return s ? '?' + s : '';
  }

  const Api = {
    ApiError,
    onUnauthorized: null,
    get: (url, opts) => request('GET', url, undefined, opts),
    post: (url, body, opts) => request('POST', url, body === undefined ? {} : body, opts),
    put: (url, body) => request('PUT', url, body),
    del: (url) => request('DELETE', url),
    upload,
    qs,
    ensureCsrf
  };

  window.Api = Api;
})();
