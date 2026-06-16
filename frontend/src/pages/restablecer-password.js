import { resetPassword } from "../api/auth.js";

// Página pública: recibe el token por URL (o se pega manualmente) y crea una nueva contraseña.
document.addEventListener("DOMContentLoaded", () => {
  const tokenUrl  = new URLSearchParams(window.location.search).get("token");
  const btn       = document.getElementById("btn-guardar");
  const inputTok  = document.getElementById("input-token");
  const inputPass = document.getElementById("input-password");
  const inputConf = document.getElementById("input-confirmar");
  const form      = document.getElementById("form-restablecer");
  const msgExito  = document.getElementById("msg-exito");
  const msgError  = document.getElementById("msg-error");

  function mostrarError(texto) {
    msgError.textContent = texto;
    msgError.style.display = "block";
  }

  // Si el token NO viene en la URL, mostramos el campo para pegarlo manualmente.
  // Así el flujo sigue funcionando aunque el enlace del correo apunte a otro host/puerto.
  if (!tokenUrl) {
    inputTok.style.display = "block";
  }

  // ENTER en el formulario: dispara el botón en vez de recargar la página
  form?.addEventListener("submit", (e) => {
    e.preventDefault();
    btn?.click();
  });

  btn?.addEventListener("click", async () => {
    msgError.style.display = "none";

    // El token de la URL tiene prioridad; si no hay, se usa el que el usuario pegó.
    const token     = tokenUrl || inputTok.value.trim();
    const password  = inputPass.value;
    const confirmar = inputConf.value;

    if (!token) {
      mostrarError("Falta el token. Pégalo desde el correo que recibiste.");
      inputTok.style.display = "block";
      inputTok.focus();
      return;
    }
    if (!password || !confirmar) {
      mostrarError("Completa ambos campos de contraseña.");
      return;
    }
    if (password !== confirmar) {
      mostrarError("Las contraseñas no coinciden.");
      return;
    }
    // Misma regla que valida el backend (ResetPasswordRequestDTO):
    // mínimo 8, una mayúscula, una minúscula, un número y un carácter especial.
    const reglaPassword = /^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).{8,}$/;
    if (!reglaPassword.test(password)) {
      mostrarError("La contraseña debe tener al menos 8 caracteres, una mayúscula, una minúscula, un número y un carácter especial (@#$%^&+=!).");
      return;
    }

    btn.disabled = true;
    btn.textContent = "Guardando...";

    try {
      await resetPassword(token, password);
      form.style.display = "none";
      msgExito.style.display = "block";
    } catch (error) {
      mostrarError(error.message || "No se pudo actualizar la contraseña. El enlace pudo haber expirado.");
      btn.disabled = false;
      btn.textContent = "Guardar contraseña";
    }
  });
});
