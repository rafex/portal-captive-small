const loginForm = document.querySelector("#login-form");
const registerForm = document.querySelector("#register-form");

loginForm?.addEventListener("submit", (event) => {
  event.preventDefault();
  const data = Object.fromEntries(new FormData(loginForm).entries());
  console.log("login payload", data);
});

registerForm?.addEventListener("submit", (event) => {
  event.preventDefault();
  const data = Object.fromEntries(new FormData(registerForm).entries());
  console.log("register payload", data);
});
