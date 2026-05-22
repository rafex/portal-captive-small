import { mountPortal } from "./portal-core.js";

const root = document.querySelector("#app");
if (root) {
  mountPortal(root, {
    apiBaseUrl: import.meta.env.VITE_API_BASE_URL || "http://127.0.0.1:8080",
    template: "hotel",
  });
}
