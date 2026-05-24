const REDIRECT_URL = 'https://theworldofrafex.blog';
const DEVICE_UUID_KEY = 'portal_device_uuid';
const COOKIE_CONSENT_KEY = 'portal_cookie_consent';

const I18N = {
  es: {
    loginTitle: 'Iniciar sesión',
    registerTitle: 'Registro',
    continueTitle: 'Acceso vigente',
    loginBtn: 'Entrar',
    registerBtn: 'Registrarme',
    continueBtn: 'Continuar',
    identifier: 'Correo o teléfono',
    password: 'Contraseña',
    acceptTerms: 'Acepto términos y condiciones',
    policyTitle: 'Términos, privacidad y uso',
    policyAccept: 'Aceptar y continuar',
    policyReject: 'Rechazar',
    cookiesTitle: 'Preferencias de cookies',
    cookiesAccept: 'Aceptar cookies',
    cookiesReject: 'Rechazar cookies',
    denied: 'Sin aceptación de cookies y términos no se habilita acceso a navegación.',
    activeFmt: (sec) => `Tu registro sigue vigente por ${formatDuration(sec)}.`,
    statusReady: 'Completa el formulario para continuar.',
    statusLoggedIn: 'Autenticado correctamente. Redirigiendo...'
  }
};

const MIT_LICENSE = `MIT License\n\nCopyright (c) 2026 Portal Captive Small contributors\n\nPermission is hereby granted, free of charge, to any person obtaining a copy\nof this software and associated documentation files (the \"Software\"), to deal\nin the Software without restriction, including without limitation the rights\nto use, copy, modify, merge, publish, distribute, sublicense, and/or sell\ncopies of the Software, and to permit persons to whom the Software is\nfurnished to do so, subject to the following conditions:\n\nThe above copyright notice and this permission notice shall be included in all\ncopies or substantial portions of the Software.\n\nTHE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR\nIMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,\nFITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE\nAUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER\nLIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,\nOUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE\nSOFTWARE.`;

function formatDuration(seconds) {
  const total = Math.max(0, Number(seconds) || 0);
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  if (h > 0) return `${h}h ${m}m`;
  if (m > 0) return `${m}m ${s}s`;
  return `${s}s`;
}

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}

function getOrCreateDeviceUuid() {
  const saved = localStorage.getItem(DEVICE_UUID_KEY);
  if (saved) return saved;
  const uuid = globalThis.crypto?.randomUUID
    ? globalThis.crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  localStorage.setItem(DEVICE_UUID_KEY, uuid);
  return uuid;
}

async function sha256hex(input) {
  const enc = new TextEncoder().encode(input);
  const digest = await crypto.subtle.digest('SHA-256', enc);
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

async function computeDeviceFingerprint() {
  const parts = [
    navigator.userAgent || '',
    navigator.language || '',
    `${screen.width}x${screen.height}x${screen.colorDepth}`,
    Intl.DateTimeFormat().resolvedOptions().timeZone || '',
    navigator.platform || '',
    navigator.hardwareConcurrency || '',
    navigator.deviceMemory || ''
  ];
  return sha256hex(parts.join('|'));
}

function fieldRequested(template, names) {
  const set = new Set((template?.fields || []).map((f) => f.field));
  return names.some((n) => set.has(n));
}

function requiresAuth(template) {
  const hasPassword = fieldRequested(template, ['password']);
  const hasIdentifier = fieldRequested(template, ['email', 'phone', 'mobile']);
  return hasPassword && hasIdentifier;
}

function labelsFor(field) {
  const map = {
    name: 'Nombre',
    lastname: 'Apellidos',
    email: 'Correo',
    phone: 'Teléfono',
    mobile: 'Celular',
    password: 'Contraseña',
    room: 'Habitación',
    address: 'Dirección',
    nickname: 'Apodo',
    social: 'Red social',
    terms: 'Acepto términos y condiciones'
  };
  return map[field] || field;
}

function inputType(field) {
  if (field === 'email') return 'email';
  if (field === 'password') return 'password';
  if (field === 'phone' || field === 'mobile') return 'tel';
  return 'text';
}

function mapFieldToPayload(field, value) {
  const v = value?.trim?.() ?? value;
  if (field === 'name') return ['firstName', v];
  if (field === 'lastname') return ['lastName', v];
  if (field === 'social') return ['socialInstagram', v];
  return [field, v];
}

function parseBootstrap() {
  return globalThis.__PORTAL_CONFIG__ || { selectedTemplate: 'hotel', templates: {} };
}

function templateList(bootstrap) {
  return Object.keys(bootstrap.templates || {}).sort();
}

function setStatus(root, text, isError = false) {
  const el = root.querySelector('#status');
  if (!el) return;
  el.textContent = text || '';
  el.style.color = isError ? '#b91c1c' : '#0f172a';
}

function applyTheme(root, template) {
  root.style.setProperty('--portal-primary', template?.primaryColor || '#0f766e');
  if (template?.background) {
    root.style.backgroundImage = `linear-gradient(rgba(255,255,255,.92), rgba(255,255,255,.92)), url('${template.background}')`;
    root.style.backgroundSize = 'cover';
    root.style.backgroundPosition = 'center';
  }
}

function policyText(templates) {
  return `Este portal cautivo usa software open source bajo licencia MIT para entornos de conectividad: ${templates.join(', ')}.\n\n` +
    `Datos capturados para operación y seguridad: IP de dispositivo, User-Agent, UUID local, huella técnica (SHA-256),` +
    ` identificador de registro (correo/teléfono si aplica), timestamps y estado de sesión TTL.\n\n` +
    `Propósito: control de acceso temporal, prevención de abuso, trazabilidad operativa y habilitación de navegación.\n\n` +
    `Si rechazas cookies o términos, no se habilita navegación.\n\n${MIT_LICENSE}`;
}

function renderConsentModals(root, templates) {
  const policy = policyText(templates);
  root.insertAdjacentHTML('beforeend', `
    <div id="cookie-modal" class="portal-modal-backdrop" hidden>
      <div class="portal-modal">
        <h3>Preferencias de cookies</h3>
        <p>Usamos cookies/localStorage para sesión del portal cautivo, consentimiento y continuidad de acceso.</p>
        <div class="portal-actions">
          <button id="cookie-accept" type="button">Aceptar cookies</button>
          <button id="cookie-reject" type="button" class="secondary" style="color:#111">Rechazar cookies</button>
        </div>
      </div>
    </div>
    <div id="policy-modal" class="portal-modal-backdrop" hidden>
      <div class="portal-modal">
        <h3>Términos, privacidad y uso</h3>
        <pre class="portal-policy">${escapeHtml(policy)}</pre>
        <div class="portal-actions">
          <button id="policy-accept" type="button">Aceptar y continuar</button>
          <button id="policy-reject" type="button" class="secondary" style="color:#111">Rechazar</button>
        </div>
      </div>
    </div>
  `);
}

async function ensureConsent(root) {
  const cookieModal = root.querySelector('#cookie-modal');
  const policyModal = root.querySelector('#policy-modal');
  const saved = localStorage.getItem(COOKIE_CONSENT_KEY);

  const waitChoice = (modal, acceptId, rejectId, onAccept, onReject) => new Promise((resolve) => {
    const acceptBtn = root.querySelector(`#${acceptId}`);
    const rejectBtn = root.querySelector(`#${rejectId}`);
    if (!modal || !acceptBtn || !rejectBtn) {
      resolve(false);
      return;
    }

    const finish = (ok) => {
      modal.hidden = true;
      onAccept && ok && onAccept();
      onReject && !ok && onReject();
      resolve(ok);
    };

    const onAcceptClick = (ev) => {
      ev.preventDefault();
      ev.stopPropagation();
      finish(true);
    };
    const onRejectClick = (ev) => {
      ev.preventDefault();
      ev.stopPropagation();
      finish(false);
    };

    modal.hidden = false;
    acceptBtn.style.cursor = 'pointer';
    rejectBtn.style.cursor = 'pointer';
    acceptBtn.addEventListener('click', onAcceptClick, { once: true });
    rejectBtn.addEventListener('click', onRejectClick, { once: true });
  });

  const askCookies = () => waitChoice(
    cookieModal,
    'cookie-accept',
    'cookie-reject',
    () => localStorage.setItem(COOKIE_CONSENT_KEY, 'accepted'),
    () => localStorage.setItem(COOKIE_CONSENT_KEY, 'rejected')
  );

  const askPolicy = () => waitChoice(
    policyModal,
    'policy-accept',
    'policy-reject'
  );

  const policyAccepted = await askPolicy();
  if (!policyAccepted) return false;
  const cookiesAccepted = saved === 'accepted' ? true : (saved === 'rejected' ? false : await askCookies());
  return cookiesAccepted;
}

function renderShell(root, template) {
  root.innerHTML = `
    <div class="portal-hero">
      <img class="portal-logo" src="${escapeHtml(template?.logo || '/assets/logo.png')}" alt="logo" />
      <h1>${escapeHtml(template?.title || 'Portal WiFi')}</h1>
    </div>
    <div id="status"></div>
    <section id="panel"></section>
  `;
}

function renderContinue(panel, remainingSeconds) {
  panel.innerHTML = `
    <h2>Acceso vigente</h2>
    <p>Tu registro sigue vigente por ${formatDuration(remainingSeconds)}.</p>
    <button id="continue-btn" type="button">Continuar</button>
  `;
  panel.querySelector('#continue-btn').onclick = () => {
    location.href = REDIRECT_URL;
  };
}

function renderLogin(panel) {
  panel.innerHTML = `
    <h2>Iniciar sesión</h2>
    <form id="login-form">
      <input name="identifier" placeholder="Correo o teléfono" required />
      <input type="password" name="password" placeholder="Contraseña" required />
      <button type="submit">Entrar</button>
    </form>
    <p style="margin:.75rem 0 0 0">
      ¿Usuario nuevo?
      <a href="#" id="go-register">Ir a registro</a>
    </p>
  `;
}

function renderRegister(panel, template) {
  const rows = (template?.fields || [])
    .filter((f) => f.field !== 'terms')
    .map((f) => `
      <label>${escapeHtml(labelsFor(f.field))}
        <input
          name="${escapeHtml(f.field)}"
          type="${escapeHtml(inputType(f.field))}"
          ${f.mode === 'required' ? 'required' : ''}
          placeholder="${escapeHtml(labelsFor(f.field))}"
        />
      </label>
    `)
    .join('');

  const termsRequired = (template?.fields || []).some((f) => f.field === 'terms' && f.mode === 'required');
  panel.innerHTML = `
    <h2>Registro</h2>
    <form id="register-form">
      ${rows}
      <label>
        <input name="terms" type="checkbox" ${termsRequired ? 'required' : ''} />
        Acepto términos y condiciones
      </label>
      <button type="submit">Registrarme</button>
    </form>
  `;
}

async function fetchJson(url, options) {
  const res = await fetch(url, options);
  const text = await res.text();
  let body = {};
  try { body = text ? JSON.parse(text) : {}; } catch (_) {}
  if (!res.ok) {
    const msg = body?.error || `http_${res.status}`;
    throw new Error(msg);
  }
  return body;
}

export async function mountPortal(root, opts = {}) {
  const apiBaseUrl = opts.apiBaseUrl || '';
  const bootstrap = parseBootstrap();
  const template = bootstrap.templates?.[bootstrap.selectedTemplate] || Object.values(bootstrap.templates || {})[0] || {};

  root.classList.add('portal-app');
  applyTheme(root, template);
  renderShell(root, template);
  renderConsentModals(root, templateList(bootstrap));

  const consented = await ensureConsent(root);
  if (!consented) {
    setStatus(root, 'Sin aceptación de cookies y términos no se habilita acceso a navegación.', true);
    const panel = root.querySelector('#panel');
    panel.innerHTML = '<section><p>Acceso denegado por política de consentimiento.</p></section>';
    return;
  }

  const deviceUuid = getOrCreateDeviceUuid();
  const deviceFingerprint = await computeDeviceFingerprint();
  const userAgent = navigator.userAgent || '';

  const headers = {
    'Content-Type': 'application/json',
    'X-Terms-Accepted': 'true',
    'X-Cookies-Accepted': 'true',
    'X-Device-UUID': deviceUuid,
    'X-Device-Fingerprint': deviceFingerprint,
    'X-Device-UA': userAgent
  };

  const panel = root.querySelector('#panel');
  const authNeeded = requiresAuth(template);

  if (!authNeeded) {
    try {
      const state = await fetchJson(`${apiBaseUrl}/auth/access/state`, { method: 'GET' });
      if (state.active) {
        renderContinue(panel, state.remainingSeconds || 0);
        return;
      }
    } catch (_) {
      // continue to registration
    }
  }

  if (authNeeded) {
    renderLogin(panel);
    panel.querySelector('#go-register')?.addEventListener('click', (ev) => {
      ev.preventDefault();
      renderRegister(panel, template);
      wireRegisterForm(panel, root, template, bootstrap, apiBaseUrl, headers);
    });
    panel.querySelector('#login-form').addEventListener('submit', async (ev) => {
      ev.preventDefault();
      setStatus(root, 'Autenticando...');
      const form = new FormData(ev.currentTarget);
      try {
        await fetchJson(`${apiBaseUrl}/auth/login`, {
          method: 'POST',
          headers,
          body: JSON.stringify({
            identifier: String(form.get('identifier') || '').trim(),
            password: String(form.get('password') || '')
          })
        });
        setStatus(root, 'Autenticado correctamente. Redirigiendo...');
        location.href = REDIRECT_URL;
      } catch (e) {
        setStatus(root, `Error: ${e.message}`, true);
      }
    });
    return;
  }

  renderRegister(panel, template);
  wireRegisterForm(panel, root, template, bootstrap, apiBaseUrl, headers);
}

function wireRegisterForm(panel, root, template, bootstrap, apiBaseUrl, headers) {
  panel.querySelector('#register-form')?.addEventListener('submit', async (ev) => {
    ev.preventDefault();
    setStatus(root, 'Registrando...');
    const form = new FormData(ev.currentTarget);
    const payload = { template: bootstrap.selectedTemplate };
    for (const f of template.fields || []) {
      if (f.field === 'terms') continue;
      const raw = String(form.get(f.field) || '').trim();
      if (!raw) continue;
      const [key, value] = mapFieldToPayload(f.field, raw);
      payload[key] = value;
    }
    try {
      await fetchJson(`${apiBaseUrl}/auth/register`, {
        method: 'POST',
        headers,
        body: JSON.stringify(payload)
      });
      setStatus(root, 'Registro exitoso. Redirigiendo...');
      location.href = REDIRECT_URL;
    } catch (e) {
      setStatus(root, `Error: ${e.message}`, true);
    }
  });
}
