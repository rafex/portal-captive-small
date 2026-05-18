const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://127.0.0.1:8080";
const OSM_ENDPOINT = "https://nominatim.openstreetmap.org/search";

const TEMPLATE_RULES = {
  hotel: ["address", "mobile"],
  restaurante: ["address", "phone"],
  escuela: ["email", "age"],
  casa: ["mobile"],
  personalizado: [],
};

const loginForm = document.querySelector("#login-form");
const registerForm = document.querySelector("#register-form");
const statusNode = document.querySelector("#status");
const templateNode = document.querySelector("#template");
const addressNode = document.querySelector("#address");
const addressOptionsNode = document.querySelector("#address-options");

function setStatus(message, isError = false) {
  if (!statusNode) return;
  statusNode.textContent = message;
  statusNode.style.color = isError ? "#b91c1c" : "#0f766e";
}

async function postJson(path, payload) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
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

function applyTemplateRules() {
  if (!registerForm || !templateNode) return;
  const selected = templateNode.value;
  const requiredFields = new Set(["firstName", "lastName", "password", ...TEMPLATE_RULES[selected]]);

  Array.from(registerForm.elements)
    .filter((el) => el instanceof HTMLInputElement)
    .forEach((input) => {
      input.required = requiredFields.has(input.name);
    });
}

let addressTimer = null;
async function lookupAddressSuggestions(query) {
  if (!addressOptionsNode || !query || query.length < 4) {
    if (addressOptionsNode) addressOptionsNode.innerHTML = "";
    return;
  }

  const url = `${OSM_ENDPOINT}?format=jsonv2&limit=5&q=${encodeURIComponent(query)}`;
  const response = await fetch(url, {
    headers: { "Accept-Language": "es" },
  });

  if (!response.ok) {
    return;
  }

  const items = await response.json();
  addressOptionsNode.innerHTML = "";
  for (const item of items) {
    const option = document.createElement("option");
    option.value = item.display_name;
    addressOptionsNode.appendChild(option);
  }
}

templateNode?.addEventListener("change", applyTemplateRules);
applyTemplateRules();

addressNode?.addEventListener("input", () => {
  clearTimeout(addressTimer);
  addressTimer = setTimeout(() => {
    lookupAddressSuggestions(addressNode.value).catch(() => {});
  }, 250);
});

loginForm?.addEventListener("submit", async (event) => {
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

registerForm?.addEventListener("submit", async (event) => {
  event.preventDefault();
  const data = Object.fromEntries(new FormData(registerForm).entries());
  try {
    const result = await postJson("/auth/register", {
      template: data.template,
      firstName: data.firstName,
      lastName: data.lastName,
      age: data.age,
      email: data.email,
      phone: data.phone,
      mobile: data.mobile,
      address: data.address,
      socialFacebook: data.socialFacebook,
      socialInstagram: data.socialInstagram,
      socialTiktok: data.socialTiktok,
      socialX: data.socialX,
      password: data.password,
    });
    setStatus(`Registro correcto: ${result.userId}`);
  } catch (error) {
    setStatus(`Registro falló: ${error.message}`, true);
  }
});
