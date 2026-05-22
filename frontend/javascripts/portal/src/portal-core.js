export function mountPortal(rootNode, options = {}) {
  const apiBaseUrl = options.apiBaseUrl || "";
  const configuredTemplate = (options.template || "hotel").toLowerCase();

  rootNode.innerHTML = `
    <main>
      <h1>Portal Cautivo</h1>
      <p id="status" aria-live="polite"></p>

      <section>
        <h2>Iniciar sesión</h2>
        <form id="login-form">
          <input name="identifier" placeholder="Correo o teléfono" required />
          <input name="password" type="password" placeholder="Contraseña" required />
          <button type="submit">Entrar</button>
        </form>
      </section>

      <section>
        <h2>Registro</h2>
        <form id="register-form"></form>
      </section>
    </main>
  `;

  const statusNode = rootNode.querySelector("#status");
  const loginForm = rootNode.querySelector("#login-form");
  const registerForm = rootNode.querySelector("#register-form");

  const labels = {
    firstName: "Nombres",
    lastName: "Apellidos",
    age: "Edad",
    email: "Correo",
    phone: "Teléfono",
    mobile: "Celular",
    address: "Dirección",
    socialFacebook: "Facebook usuario",
    socialInstagram: "Instagram usuario",
    socialTiktok: "TikTok usuario",
    socialX: "X usuario",
    password: "Contraseña",
  };

  function setStatus(msg, err = false) {
    statusNode.textContent = msg;
    statusNode.style.color = err ? "#b91c1c" : "#0f766e";
  }

  async function postJson(path, payload) {
    const response = await fetch(`${apiBaseUrl}${path}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });

    let body = {};
    try {
      body = await response.json();
    } catch (_) {
      body = {};
    }

    if (!response.ok) {
      throw new Error(body.error || `http_${response.status}`);
    }

    return body;
  }

  function parseTemplateSpecFromToml(tomlText, fallbackTemplate) {
    const templateRegex = /^\s*\[templates\.([A-Za-z0-9_\-]+)\]\s*$/;
    const lines = (tomlText || "").split(/\r?\n/);
    const templates = {};
    let sectionTemplate = "";
    let inRegistration = false;
    let registrationTemplate = fallbackTemplate;

    for (let i = 0; i < lines.length; i += 1) {
      const line = lines[i].trim();
      if (!line || line.startsWith("#")) continue;

      const sectionMatch = line.match(templateRegex);
      if (sectionMatch) {
        sectionTemplate = sectionMatch[1].toLowerCase();
        inRegistration = false;
        if (!templates[sectionTemplate]) templates[sectionTemplate] = [];
        continue;
      }
      if (/^\s*\[/.test(line)) {
        sectionTemplate = "";
        inRegistration = line === "[registration]";
      }
      if (inRegistration && /^\s*template\s*=/.test(line)) {
        const m = line.match(/=\s*"([^"]+)"/);
        if (m && m[1]) registrationTemplate = m[1].toLowerCase();
      }
      if (sectionTemplate && line.startsWith("fields_enabled")) {
        let block = line;
        while (!block.includes("]") && i < lines.length - 1) {
          i += 1;
          block += lines[i];
        }
        const pairs = block.match(/\[\s*"([^"]+)"\s*,\s*"([^"]+)"\s*]/g) || [];
        templates[sectionTemplate] = pairs.map((pair) => {
          const m = pair.match(/\[\s*"([^"]+)"\s*,\s*"([^"]+)"\s*]/);
          return { field: m[1], mode: m[2] };
        });
      }
    }
    return { templates, registrationTemplate };
  }

  function mapTomlFieldToApiField(name) {
    const normalized = String(name || "").toLowerCase();
    const map = {
      name: "firstName",
      lastname: "lastName",
      nickname: "firstName",
      room: "address",
      social: "socialX",
      terms: "termsAccepted",
      email: "email",
      password: "password",
      phone: "phone",
      mobile: "mobile",
      address: "address",
      age: "age",
      first_name: "firstName",
      last_name: "lastName",
    };
    return map[normalized] || null;
  }

  function renderRegisterForm(templateSpec) {
    const fields = templateSpec && templateSpec.length > 0 ? templateSpec : [
      { field: "name", mode: "required" },
      { field: "lastname", mode: "required" },
      { field: "email", mode: "required" },
      { field: "password", mode: "required" },
    ];

    for (const item of fields) {
      const apiField = mapTomlFieldToApiField(item.field);
      if (!apiField) continue;
      const input = document.createElement("input");
      input.name = apiField;
      input.placeholder = labels[apiField] || item.field;
      input.type = apiField === "password" ? "password" : (apiField === "email" ? "email" : (apiField === "age" ? "number" : "text"));
      if (apiField === "age") input.min = "0";
      input.required = String(item.mode || "").toLowerCase() === "required";
      registerForm.appendChild(input);
    }

    const registerButton = document.createElement("button");
    registerButton.type = "submit";
    registerButton.textContent = "Registrarme";
    registerForm.appendChild(registerButton);
  }

  (async () => {
    try {
      const response = await fetch(`${apiBaseUrl}/portal/config/toml`);
      const toml = await response.text();
      const parsed = parseTemplateSpecFromToml(toml, configuredTemplate);
      const selectedTemplate = configuredTemplate || parsed.registrationTemplate;
      renderRegisterForm(parsed.templates[selectedTemplate]);
    } catch (_) {
      renderRegisterForm(null);
    }
  })();

  loginForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const data = Object.fromEntries(new FormData(loginForm).entries());
    try {
      const result = await postJson("/auth/login", {
        identifier: data.identifier,
        password: data.password,
      });
      setStatus(`Login correcto: ${result.userId} (${result.reason})`);
    } catch (error) {
      setStatus(`Login falló: ${error.message}`, true);
    }
  });

  registerForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const data = Object.fromEntries(new FormData(registerForm).entries());
    try {
      const result = await postJson("/auth/register", {
        ...data,
        template: configuredTemplate,
      });
      setStatus(`Registro correcto: ${result.userId}`);
    } catch (error) {
      setStatus(`Registro falló: ${error.message}`, true);
    }
  });
}
