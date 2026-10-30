// ResuMatch shared client-side helpers

const api = {
  async request(method, url, body, isForm) {
    const opts = { method, credentials: 'same-origin', headers: {} };
    if (body && !isForm) {
      opts.headers['Content-Type'] = 'application/json';
      opts.body = JSON.stringify(body);
    } else if (body && isForm) {
      opts.body = body;
    }
    const r = await fetch(url, { ...opts, cache: 'no-store' });
    let data = null;
    try { data = await r.json(); } catch (_) { data = { success: false, message: 'Bad response' }; }
    if (!r.ok || (data && data.success === false)) {
      const msg = (data && (data.message || (data.errors && data.errors.join('; ')))) || 'Request failed';
      // If the server says the session's user no longer exists, the cookie
      // is stale (e.g. admin wiped the data and got re-created). Send the
      // user back to the login page so they can re-authenticate.
      if (r.status === 401 || /user not found|session|unauthor/i.test(msg)) {
        toast('Your session expired. Please sign in again.', 'err');
        // Send the user back to the home page — it has all three login links
        // (seeker / recruiter / admin) so they can pick the right one.
        setTimeout(() => { window.location.href = '/'; }, 600);
        throw new Error(msg);
      }
      toast(msg, 'err');
      throw new Error(msg);
    }
    return data;
  },
  get(url) { return this.request('GET', url); },
  post(url, body) { return this.request('POST', url, body, false); },
  put(url, body) { return this.request('PUT', url, body, false); },
  patch(url, body) { return this.request('PATCH', url, body, false); },
  del(url) { return this.request('DELETE', url); },
  upload(url, form) { return this.request('POST', url, form, true); }
};

function toast(msg, type) {
  let host = document.querySelector('.toast');
  if (!host) {
    host = document.createElement('div');
    host.className = 'toast';
    document.body.appendChild(host);
  }
  const el = document.createElement('div');
  el.className = 'item ' + (type || 'ok');
  el.textContent = msg;
  host.appendChild(el);
  setTimeout(() => el.remove(), 4500);
}

function escapeHtml(s) {
  if (s === null || s === undefined) return '';
  return String(s).replace(/[&<>"']/g, c => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[c]));
}

function fmtPct(v) { return (Math.round((v || 0) * 10) / 10).toFixed(1); }
function fmtDate(iso) {
  if (!iso) return '';
  try { return new Date(iso).toLocaleString(); } catch (_) { return iso; }
}

async function requireAuth(role) {
  try {
    const me = await api.get('/api/auth/me');
    if (role && me.data.role !== role && me.data.role !== 'ADMIN') {
      window.location.href = '/';
      return null;
    }
    if (me.data) loadNotifications();
    return me.data;
  } catch (_) {
    // /api/auth/me failed — usually means no session. Send the user home
    // so they can pick the right login. The login pages themselves
    // will also redirect, but `/` is a safe default.
    window.location.href = '/';
    return null;
  }
}

function scoreRing(pct) {
  const v = Math.max(0, Math.min(100, Number(pct) || 0));
  return `<div class="score-ring" style="--pct:${v}">
    <div style="text-align:center">
      <div class="num">${fmtPct(v)}</div>
      <div class="lbl">/ 100</div>
    </div>
  </div>`;
}

// Animated score ring — tweens from 0 to target over 800ms.
// Returns the element so the caller can attach it to the DOM.
function animatedScoreRing(pct, durationMs) {
  durationMs = durationMs || 900;
  const target = Math.max(0, Math.min(100, Number(pct) || 0));
  const el = document.createElement('div');
  el.className = 'score-ring';
  el.style.setProperty('--pct', 0);
  el.innerHTML = `<div style="text-align:center">
    <div class="num">0</div>
    <div class="lbl">/ 100</div>
  </div>`;
  const numEl = el.querySelector('.num');
  const start = performance.now();
  function frame(now) {
    const t = Math.min(1, (now - start) / durationMs);
    // ease-out cubic
    const eased = 1 - Math.pow(1 - t, 3);
    const v = target * eased;
    el.style.setProperty('--pct', v);
    numEl.textContent = fmtPct(v);
    if (t < 1) requestAnimationFrame(frame);
  }
  requestAnimationFrame(frame);
  return el;
}

async function loadNotifications() {
  try {
    const r = await api.get('/api/notifications');
    const bell = document.getElementById('notifBell');
    if (!bell) return;
    const unread = r.data.unread;
    bell.dataset.unread = unread;
    let badge = bell.querySelector('.bell-badge');
    if (unread > 0) {
      if (!badge) {
        badge = document.createElement('span');
        badge.className = 'bell-badge';
        bell.appendChild(badge);
      }
      badge.textContent = unread > 9 ? '9+' : unread;
    } else if (badge) {
      badge.remove();
    }
  } catch (_) {}
}

function attachBellHandler() {
  const bell = document.getElementById('notifBell');
  if (!bell) return;
  bell.addEventListener('click', async (e) => {
    e.preventDefault();
    const list = bell.parentElement.querySelector('.notif-popup');
    if (list) { list.remove(); return; }
    const r = await api.get('/api/notifications');
    const popup = document.createElement('div');
    popup.className = 'notif-popup card';
    popup.style.cssText = 'position:absolute; right:0; top:100%; width:360px; max-height:480px; overflow:auto; z-index:1000; margin-top:8px;';
    const items = r.data.items;
    if (!items.length) {
      popup.innerHTML = '<p class="muted" style="padding:12px">No notifications yet.</p>';
    } else {
      popup.innerHTML = `
        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:8px;">
          <strong>Notifications</strong>
          <button class="btn small" id="markAll">Mark all read</button>
        </div>
        ${items.map(n => `
          <div class="notif-item ${n.isRead ? '' : 'unread'}">
            <div class="sub">${fmtDate(n.createdAt)}</div>
            <div><b>${escapeHtml(n.subject || '(no subject)')}</b></div>
            <div class="muted" style="font-size:0.85rem; margin-top:4px;">${escapeHtml((n.body || '').slice(0, 120))}${n.body && n.body.length > 120 ? '…' : ''}</div>
          </div>
        `).join('')}
      `;
    }
    bell.parentElement.style.position = 'relative';
    bell.parentElement.appendChild(popup);
    popup.querySelector('#markAll')?.addEventListener('click', async () => {
      await api.post('/api/notifications/read-all', {});
      popup.remove();
      loadNotifications();
    });
    // close on outside click
    setTimeout(() => {
      const close = (ev) => { if (!popup.contains(ev.target) && ev.target !== bell) { popup.remove(); document.removeEventListener('click', close); } };
      document.addEventListener('click', close);
    }, 0);
  });
}
document.addEventListener('DOMContentLoaded', attachBellHandler);

// On every public page, fetch an unauthenticated ping to decide whether to show
// the "first time here? ask admin to seed" banner. Cheap because it's a single
// boolean, cached on the server.
(async function maybeShowFirstRunBanner() {
  const banner = document.getElementById('firstRunBanner');
  if (!banner) return;
  try {
    const r = await fetch('/api/public/needs-seed', { cache: 'no-store' });
    const d = await r.json();
    if (d && d.empty) banner.style.display = 'flex';
  } catch (_) {}
})();

// Inject a professional multi-column footer into every page that doesn't already
// have one. The site-footer is a small piece of HTML so we ship it inline.
(function injectFooter() {
  if (document.querySelector('.site-footer')) return;
  const footer = document.createElement('footer');
  footer.className = 'site-footer';
  footer.innerHTML = `
    <div class="footer-inner">
      <div class="footer-col footer-brand">
        <div class="footer-mark">R</div>
        <p class="footer-tagline">The resume score, shown to both sides.</p>
      </div>
      <div class="footer-col">
        <h4>Product</h4>
        <ul>
          <li><a href="/#how">Try the formula</a></li>
          <li><a href="/#formula">The scoring algorithm</a></li>
          <li><a href="/pages/how-it-works.html">Documentation</a></li>
        </ul>
      </div>
      <div class="footer-col">
        <h4>For</h4>
        <ul>
          <li><a href="/pages/seeker-register.html">Job seekers</a></li>
          <li><a href="/pages/recruiter-register.html">Recruiters</a></li>
          <li><a href="/pages/admin-login.html">Administrators</a></li>
        </ul>
      </div>
      <div class="footer-col">
        <h4>Company</h4>
        <ul>
          <li><a href="/pages/how-it-works.html#what-we-dont-do">What we don't do</a></li>
          <li><a href="/pages/seeker-login.html">Sign in</a></li>
        </ul>
      </div>
    </div>
    <div class="footer-bottom">
      <span>© 2026 ResuMatch</span>
      <span>Rule-based · Explainable · Zero-cost</span>
    </div>
  `;
  document.body.appendChild(footer);
})();
