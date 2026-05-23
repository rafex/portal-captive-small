export function mountPortal(rootNode, options = {}) {
  const apiBaseUrl = options.apiBaseUrl || "";
  const boot = window.__PORTAL_CONFIG__ || {};
  const selectedTemplate = String(boot.selectedTemplate || "hotel").toLowerCase();
  const templateData = (boot.templates && boot.templates[selectedTemplate]) || {};
  const templateSpec = templateData.fields || [];

  rootNode.innerHTML = `
    <main>
      <header class="portal-hero">
        ${templateData.logo ? `<img class="portal-logo" src="${templateData.logo}" alt="${templateData.title || selectedTemplate} logo" />` : ""}
        <h1>${templateData.title || "Portal Cautivo"}</h1>
      </header>
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
    firstName: "Nombre",
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
    termsAccepted: "Acepto términos y condiciones"
  };

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
      last_name: "lastName"
    };
    return map[normalized] || null;
  }

  function setStatus(msg, err = false) {
    statusNode.textContent = msg;
    statusNode.style.color = err ? "#b91c1c" : "#0f766e";
  }

  async function postJson(path, payload) {
    const response = await fetch(`${apiBaseUrl}${path}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
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

  if (templateData.background) {
    rootNode.style.backgroundImage = `linear-gradient(rgba(255,255,255,.84), rgba(255,255,255,.84)), url('${templateData.background}')`;
    rootNode.style.backgroundSize = "cover";
    rootNode.style.backgroundPosition = "center";
  }

  if (templateData.primaryColor) {
    rootNode.style.setProperty("--portal-primary", templateData.primaryColor);
  }

  const fields = templateSpec.length > 0 ? templateSpec : [
    { field: "name", mode: "required" },
    { field: "lastname", mode: "required" },
    { field: "email", mode: "required" },
    { field: "password", mode: "required" }
  ];

  for (const item of fields) {
    const apiField = mapTomlFieldToApiField(item.field);
    if (!apiField) continue;
    if (apiField === "termsAccepted") {
      const checkbox = document.createElement("label");
      checkbox.innerHTML = `<input type="checkbox" name="termsAccepted" ${String(item.mode).toLowerCase() === "required" ? "required" : ""}> ${labels.termsAccepted}`;
      registerForm.appendChild(checkbox);
      continue;
    }
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

  loginForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const data = Object.fromEntries(new FormData(loginForm).entries());
    try {
      const result = await postJson("/auth/login", {
        identifier: data.identifier,
        password: data.password
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
        template: selectedTemplate
      });
      setStatus(`Registro correcto: ${result.userId}`);
    } catch (error) {
      setStatus(`Registro falló: ${error.message}`, true);
    }
  });
}
