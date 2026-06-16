import { cambiarPassword } from "../api/auth.js";
import { mostrarToast } from "../layout.js";
import { protegerRuta } from "../route-guard.js";

// Página de configuraciones del usuario autenticado.
protegerRuta();

// Misma regla que valida el backend (CambiarPasswordRequestDTO).
const REGLA_PASSWORD = /^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).{8,}$/;

document.addEventListener("DOMContentLoaded", () => {
  const btn       = document.getElementById("btnCambiarPassword");
  const inputAct  = document.getElementById("pass-actual");
  const inputNew  = document.getElementById("pass-nueva");
  const inputConf = document.getElementById("pass-confirmar");
  const errSpan   = document.getElementById("err-password");
  const form      = document.getElementById("form-password");
  if (!btn) return;

  const mostrarError = (txt) => { if (errSpan) errSpan.textContent = txt; };

  btn.addEventListener("click", async () => {
    mostrarError("");
    const actual    = inputAct?.value || "";
    const nueva     = inputNew?.value || "";
    const confirmar = inputConf?.value || "";

    if (!actual || !nueva || !confirmar) {
      mostrarError("Completa los tres campos.");
      return;
    }
    if (nueva !== confirmar) {
      mostrarError("La nueva contraseña y su confirmación no coinciden.");
      return;
    }
    if (actual === nueva) {
      mostrarError("La nueva contraseña debe ser diferente a la actual.");
      return;
    }
    if (!REGLA_PASSWORD.test(nueva)) {
      mostrarError("La nueva contraseña debe tener al menos 8 caracteres, una mayúscula, una minúscula, un número y un carácter especial (@#$%^&+=!).");
      return;
    }

    btn.disabled = true;
    btn.textContent = "Guardando...";

    try {
      await cambiarPassword(actual, nueva);
      form.reset();
      mostrarToast("Contraseña actualizada correctamente.", "exito");
    } catch (error) {
      // El backend responde 401 si la contraseña actual es incorrecta
      mostrarError(error.message || "No se pudo cambiar la contraseña.");
    } finally {
      btn.disabled = false;
      btn.textContent = "Guardar nueva contraseña";
    }
  });
});
