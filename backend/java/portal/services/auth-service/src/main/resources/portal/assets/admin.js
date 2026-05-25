const root = document.getElementById('admin-app');

function esc(v) {
  return String(v ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}

function setStatus(msg, isErr = false) {
  const el = document.getElementById('status');
  if (!el) return;
  el.textContent = msg || '';
  el.style.color = isErr ? '#b91c1c' : '#0f172a';
}

function renderLogin() {
  root.innerHTML = `
    <section>
      <h2>Portal Admin</h2>
      <div id="status"></div>
      <form id="login-form">
        <label>Usuario
          <input name="username" required />
        </label>
        <label>Contraseña
          <input name="password" type="password" required />
        </label>
        <button type="submit">Entrar</button>
      </form>
    </section>
  `;
  document.getElementById('login-form')?.addEventListener('submit', doLogin);
}

function renderPanel(token, role) {
  root.innerHTML = `
    <section>
      <h2>Administración</h2>
      <p>Rol: <b>${esc(role)}</b></p>
      <div id="status"></div>
      <div style="display:flex; gap:.5rem; margin-bottom:.75rem">
        <button id="reload-btn" type="button">Recargar registrados</button>
        ${role === 'admin' ? '<button id="create-btn" type="button">Crear usuario admin/viewer</button>' : ''}
        <button id="logout-btn" type="button" class="secondary" style="color:#111">Salir</button>
      </div>
      <div id="create-box" hidden>
        <form id="create-form">
          <label>Usuario
            <input name="username" required />
          </label>
          <label>Contraseña
            <input name="password" type="password" required />
          </label>
          <label>Rol
            <select name="role">
              <option value="viewer">viewer</option>
              <option value="admin">admin</option>
            </select>
          </label>
          <button type="submit">Guardar</button>
        </form>
      </div>
      <pre id="users-box" class="portal-policy"></pre>
    </section>
  `;

  document.getElementById('reload-btn')?.addEventListener('click', () => loadUsers(token));
  document.getElementById('logout-btn')?.addEventListener('click', () => {
    localStorage.removeItem('portal_admin_token');
    localStorage.removeItem('portal_admin_role');
    renderLogin();
  });
  document.getElementById('create-btn')?.addEventListener('click', () => {
    const box = document.getElementById('create-box');
    if (!box) return;
    box.hidden = !box.hidden;
  });
  document.getElementById('create-form')?.addEventListener('submit', (ev) => createAdminUser(ev, token));
  loadUsers(token);
}

async function doLogin(ev) {
  ev.preventDefault();
  const form = new FormData(ev.currentTarget);
  setStatus('Autenticando...');
  try {
    const res = await fetch('/admin/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username: String(form.get('username') || '').trim(),
        password: String(form.get('password') || '')
      })
    });
    const body = await res.json().catch(() => ({}));
    if (!res.ok) {
      throw new Error(body.error || `http_${res.status}`);
    }
    localStorage.setItem('portal_admin_token', body.token);
    localStorage.setItem('portal_admin_role', body.role || 'viewer');
    renderPanel(body.token, body.role || 'viewer');
  } catch (e) {
    setStatus(`Error: ${e.message}`, true);
  }
}

async function loadUsers(token) {
  setStatus('Cargando usuarios registrados...');
  try {
    const res = await fetch('/admin/users/registered?limit=200', {
      headers: { Authorization: `Bearer ${token}` }
    });
    const body = await res.json().catch(() => ({}));
    if (!res.ok) {
      throw new Error(body.error || `http_${res.status}`);
    }
    document.getElementById('users-box').textContent = JSON.stringify(body, null, 2);
    setStatus(`Usuarios: ${body.count ?? 0}`);
  } catch (e) {
    setStatus(`Error: ${e.message}`, true);
  }
}

async function createAdminUser(ev, token) {
  ev.preventDefault();
  const form = new FormData(ev.currentTarget);
  setStatus('Creando usuario...');
  try {
    const res = await fetch('/admin/users', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`
      },
      body: JSON.stringify({
        username: String(form.get('username') || '').trim(),
        password: String(form.get('password') || ''),
        role: String(form.get('role') || 'viewer').trim()
      })
    });
    const body = await res.json().catch(() => ({}));
    if (!res.ok) {
      throw new Error(body.error || `http_${res.status}`);
    }
    setStatus(`Creado: ${body.username} (${body.role})`);
    ev.currentTarget.reset();
  } catch (e) {
    setStatus(`Error: ${e.message}`, true);
  }
}

const token = localStorage.getItem('portal_admin_token');
const role = localStorage.getItem('portal_admin_role') || 'viewer';
if (token) {
  renderPanel(token, role);
} else {
  renderLogin();
}

