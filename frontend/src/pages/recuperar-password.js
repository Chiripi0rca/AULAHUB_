import { forgotPassword } from "../api/auth.js";

// Página pública: solicita el email para enviar enlace de restablecimiento de contraseña.
document.addEventListener("DOMContentLoaded", () => {
  const btn       = document.getElementById("btn-enviar");
  const input     = document.getElementById("input-email");
  const form      = document.getElementById("form-recuperar");
  const msgExito  = document.getElementById("msg-exito");
  const msgError  = document.getElementById("msg-error");

  function mostrarError(texto) {
    msgError.textContent = texto;
    msgError.style.display = "block";
  }

  function ocultarError() {
    msgError.style.display = "none";
  }

  btn?.addEventListener("click", async () => {
    ocultarError();
    const email = input?.value?.trim();
    if (!email) {
      input.style.borderColor = "#ef4444";
      mostrarError("Ingresa tu correo institucional.");
      return;
    }

    btn.disabled = true;
    btn.textContent = "Enviando...";

    try {
      await forgotPassword(email);
      form.style.display = "none";
      msgExito.style.display = "block";
    } catch (error) {
      mostrarError(error.message || "No se pudo enviar el correo. Intenta de nuevo.");
      btn.disabled = false;
      btn.textContent = "Enviar enlace";
    }
  });

  input?.addEventListener("input", () => {
    input.style.borderColor = "";
    ocultarError();
  });

  // ENTER en el formulario: dispara el botón en vez de recargar la página
  form?.addEventListener("submit", (e) => {
    e.preventDefault();
    btn?.click();
  });
});
