const REDIRECT_URL = 'https://theworldofrafex.blog';
const DEVICE_UUID_KEY = 'portal_device_uuid';
const COOKIE_CONSENT_KEY = 'portal_cookie_consent';

const UI_FALLBACK = {
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
  statusLoggedIn: 'Autenticado correctamente. Redirigiendo...'
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
  if (globalThis.crypto?.subtle?.digest) {
    const enc = new TextEncoder().encode(input);
    const digest = await globalThis.crypto.subtle.digest('SHA-256', enc);
    return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('');
  }

  // Fallback para HTTP no seguro: hash determinista (no criptográfico).
  let h1 = 0x811c9dc5;
  let h2 = 0x01000193;
  for (let i = 0; i < input.length; i += 1) {
    const c = input.charCodeAt(i);
    h1 ^= c;
    h1 = Math.imul(h1, 0x01000193);
    h2 ^= (c << (i % 8));
    h2 = Math.imul(h2, 0x45d9f3b);
  }
  const p1 = (h1 >>> 0).toString(16).padStart(8, '0');
  const p2 = (h2 >>> 0).toString(16).padStart(8, '0');
  return `${p1}${p2}${p1}${p2}`;
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

function labelsFor(bootstrap, lang, field) {
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
  return fieldI18n(bootstrap, lang, field, 'label', map[field] || field);
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
  if (field === 'room') return ['address', v];
  if (field === 'social') return ['socialInstagram', v];
  return [field, v];
}

function parseBootstrap() {
  return globalThis.__PORTAL_CONFIG__ || { selectedTemplate: 'hotel', templates: {}, i18n: { fields: {}, messages: {} } };
}

function templateList(bootstrap) {
  return Object.keys(bootstrap.templates || {}).sort();
}

function resolveLang(bootstrap) {
  const preferred = (navigator.language || '').replace('-', '_');
  const supported = bootstrap.supportedLangs || [];
  if (supported.includes(preferred)) return preferred;
  const short = preferred.split('_')[0];
  const byPrefix = supported.find((x) => x.toLowerCase().startsWith(short.toLowerCase()));
  if (byPrefix) return byPrefix;
  if (bootstrap.defaultLang) return bootstrap.defaultLang;
  return supported[0] || 'es_MX';
}

function uiText(bootstrap, lang, key) {
  const messages = bootstrap?.i18n?.messages?.[lang] || {};
  if (key === 'loginBtn' && messages.login_button) return messages.login_button;
  if (key === 'continueTitle' && messages.welcome) return messages.welcome;
  return UI_FALLBACK[key] || key;
}

function fieldI18n(bootstrap, lang, field, prop, fallback) {
  const fields = bootstrap?.i18n?.fields?.[lang] || {};
  return fields?.[field]?.[prop] || fallback;
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

function renderConsentModals(root, templates, texts) {
  const policy = policyText(templates);
  root.insertAdjacentHTML('beforeend', `
    <div id="cookie-modal" class="portal-modal-backdrop" hidden>
      <div class="portal-modal">
        <h3>${escapeHtml(texts.cookiesTitle)}</h3>
        <p>Usamos cookies/localStorage para sesión del portal cautivo, consentimiento y continuidad de acceso.</p>
        <div class="portal-actions">
          <button id="cookie-accept" type="button">${escapeHtml(texts.cookiesAccept)}</button>
          <button id="cookie-reject" type="button" class="secondary" style="color:#111">${escapeHtml(texts.cookiesReject)}</button>
        </div>
      </div>
    </div>
    <div id="policy-modal" class="portal-modal-backdrop" hidden>
      <div class="portal-modal">
        <h3>${escapeHtml(texts.policyTitle)}</h3>
        <pre class="portal-policy">${escapeHtml(policy)}</pre>
        <div class="portal-actions">
          <button id="policy-accept" type="button">${escapeHtml(texts.policyAccept)}</button>
          <button id="policy-reject" type="button" class="secondary" style="color:#111">${escapeHtml(texts.policyReject)}</button>
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

function renderContinue(panel, remainingSeconds, texts) {
  panel.innerHTML = `
    <h2>${escapeHtml(texts.continueTitle)}</h2>
    <p>Tu registro sigue vigente por ${formatDuration(remainingSeconds)}.</p>
    <button id="continue-btn" type="button">${escapeHtml(texts.continueBtn)}</button>
  `;
  panel.querySelector('#continue-btn').onclick = () => {
    location.href = REDIRECT_URL;
  };
}

function renderLogin(panel, texts) {
  panel.innerHTML = `
    <h2>${escapeHtml(texts.loginTitle)}</h2>
    <form id="login-form">
      <input name="identifier" placeholder="${escapeHtml(texts.identifier)}" required />
      <input type="password" name="password" placeholder="${escapeHtml(texts.password)}" required />
      <button type="submit">${escapeHtml(texts.loginBtn)}</button>
    </form>
    <p style="margin:.75rem 0 0 0">
      ¿Usuario nuevo?
      <a href="#" id="go-register">Ir a registro</a>
    </p>
  `;
}

function renderRegister(panel, template, bootstrap, lang, texts) {
  const rows = (template?.fields || [])
    .filter((f) => f.field !== 'terms')
    .map((f) => `
      <label>${escapeHtml(labelsFor(bootstrap, lang, f.field))}
        <input
          name="${escapeHtml(f.field)}"
          type="${escapeHtml(inputType(f.field))}"
          ${f.mode === 'required' ? 'required' : ''}
          placeholder="${escapeHtml(fieldI18n(bootstrap, lang, f.field, 'placeholder', labelsFor(bootstrap, lang, f.field)))}"
        />
      </label>
    `)
    .join('');

  const termsRequired = (template?.fields || []).some((f) => f.field === 'terms' && f.mode === 'required');
  panel.innerHTML = `
    <h2>${escapeHtml(texts.registerTitle)}</h2>
    <form id="register-form">
      ${rows}
      <label>
        <input name="terms" type="checkbox" ${termsRequired ? 'required' : ''} />
        ${escapeHtml(texts.acceptTerms)}
      </label>
      <button type="submit">${escapeHtml(texts.registerBtn)}</button>
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
  const lang = resolveLang(bootstrap);
  const texts = {
    loginTitle: uiText(bootstrap, lang, 'loginTitle'),
    registerTitle: uiText(bootstrap, lang, 'registerTitle'),
    continueTitle: uiText(bootstrap, lang, 'continueTitle'),
    loginBtn: uiText(bootstrap, lang, 'loginBtn'),
    registerBtn: uiText(bootstrap, lang, 'registerBtn'),
    continueBtn: uiText(bootstrap, lang, 'continueBtn'),
    identifier: uiText(bootstrap, lang, 'identifier'),
    password: uiText(bootstrap, lang, 'password'),
    acceptTerms: uiText(bootstrap, lang, 'acceptTerms'),
    policyTitle: uiText(bootstrap, lang, 'policyTitle'),
    policyAccept: uiText(bootstrap, lang, 'policyAccept'),
    policyReject: uiText(bootstrap, lang, 'policyReject'),
    cookiesTitle: uiText(bootstrap, lang, 'cookiesTitle'),
    cookiesAccept: uiText(bootstrap, lang, 'cookiesAccept'),
    cookiesReject: uiText(bootstrap, lang, 'cookiesReject'),
    denied: uiText(bootstrap, lang, 'denied'),
    statusLoggedIn: uiText(bootstrap, lang, 'statusLoggedIn')
  };

  root.classList.add('portal-app');
  applyTheme(root, template);
  renderShell(root, template);
  renderConsentModals(root, templateList(bootstrap), texts);

  const consented = await ensureConsent(root);
  if (!consented) {
    setStatus(root, texts.denied, true);
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
        renderContinue(panel, state.remainingSeconds || 0, texts);
        return;
      }
    } catch (_) {
      // continue to registration
    }
  }

  if (authNeeded) {
    renderLogin(panel, texts);
    panel.querySelector('#go-register')?.addEventListener('click', (ev) => {
      ev.preventDefault();
      renderRegister(panel, template, bootstrap, lang, texts);
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
        setStatus(root, texts.statusLoggedIn);
        location.href = REDIRECT_URL;
      } catch (e) {
        setStatus(root, `Error: ${e.message}`, true);
      }
    });
    return;
  }

  renderRegister(panel, template, bootstrap, lang, texts);
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
