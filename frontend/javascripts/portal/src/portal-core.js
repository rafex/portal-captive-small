export function mountPortal(rootNode, options = {}) {
  const apiBaseUrl = options.apiBaseUrl || "";
  const template = (options.template || "hotel").toLowerCase();

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

  const templateFields = {
    hotel: ["firstName", "lastName", "email", "phone", "mobile", "address", "socialFacebook", "socialInstagram", "socialTiktok", "socialX", "password"],
    restaurante: ["firstName", "lastName", "email", "phone", "mobile", "address", "socialFacebook", "socialInstagram", "socialTiktok", "socialX", "password"],
    escuela: ["firstName", "lastName", "age", "email", "phone", "mobile", "socialFacebook", "socialInstagram", "socialTiktok", "socialX", "password"],
    casa: ["firstName", "lastName", "email", "phone", "mobile", "socialFacebook", "socialInstagram", "socialTiktok", "socialX", "password"],
    personalizado: ["firstName", "lastName", "email", "phone", "mobile", "address", "socialFacebook", "socialInstagram", "socialTiktok", "socialX", "password"],
  };

  const required = {
    baseline: ["firstName", "lastName", "password"],
    hotel: ["address", "mobile"],
    restaurante: ["address", "phone"],
    escuela: ["email", "age"],
    casa: ["mobile"],
    personalizado: [],
  };

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

  const fields = templateFields[template] || templateFields.personalizado;
  const requiredSet = new Set([...(required.baseline || []), ...(required[template] || [])]);

  for (const name of fields) {
    const input = document.createElement("input");
    input.name = name;
    input.placeholder = labels[name] || name;
    input.type = name === "password" ? "password" : (name === "email" ? "email" : (name === "age" ? "number" : "text"));
    if (name === "age") {
      input.min = "0";
    }
    input.required = requiredSet.has(name);
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
        template,
      });
      setStatus(`Registro correcto: ${result.userId}`);
    } catch (error) {
      setStatus(`Registro falló: ${error.message}`, true);
    }
  });
}
