import { usuariosApi } from "../api/usuarios.js";
import { getCurrentUser } from "../api/auth-session.js";
import { assetUrl, mostrarToast } from "../layout.js";
import { protegerRuta } from "../route-guard.js";

// Perfil de usuario: carga datos desde el backend y permite actualizar la foto.
protegerRuta();

document.addEventListener("DOMContentLoaded", async () => {
  await cargarPerfil();
  configurarSubidaFoto();
});

async function cargarPerfil() {
  try {
    const data = await usuariosApi.getPerfil();
    renderPerfil(data);
  } catch {
    // Si falla el backend, mostramos lo que hay en la sesion local
    const local = getCurrentUser();
    if (local) renderPerfil(local);
    mostrarToast("No se pudo conectar con el servidor. Mostrando datos locales.", "aviso");
  }
}

function renderPerfil(data) {
  const set = (id, valor) => {
    const el = document.getElementById(id);
    if (el) el.textContent = valor ?? "—";
  };

  const fotoUrl = data.fotoUrl
    || localStorage.getItem("user_photo")
    || assetUrl("assets/images/usuario.png");

  const foto = document.getElementById("perfil-foto");
  if (foto) foto.src = fotoUrl;

  set("perfil-nombre", data.nombre || data.name);
  set("perfil-email", data.email);
  set("perfil-activo", data.activo !== false ? "Activo" : "Inactivo");

  if (data.createdAt) {
    set("perfil-desde", new Date(data.createdAt).toLocaleDateString("es-MX", {
      year: "numeric", month: "long", day: "numeric",
    }));
  }

  const roles = data.roles ?? getCurrentUser()?.roles ?? [];
  const rolesEl = document.getElementById("perfil-roles");
  if (rolesEl) {
    rolesEl.innerHTML = roles.length
      ? roles.map(r => `<span class="role-badge">${r}</span>`).join("")
      : "<span>Sin roles asignados</span>";
  }
}

function configurarSubidaFoto() {
  const fileInput = document.getElementById("foto-input");
  const btnSubir  = document.getElementById("btn-subir-foto");
  const imgPerfil = document.getElementById("perfil-foto");

  // Aplica la nueva foto en la página, el header y localStorage.
  const aplicarFoto = (url) => {
    if (!url) return;
    localStorage.setItem("user_photo", url);
    if (imgPerfil) imgPerfil.src = url;
    const headerAvatar = document.getElementById("preview");
    if (headerAvatar) headerAvatar.src = url;
  };

  // Subida por archivo (multipart) — el backend guarda la imagen en la BD como base64.
  // Es el único dato que el usuario puede cambiar por sí mismo.
  btnSubir?.addEventListener("click", () => fileInput?.click());

  fileInput?.addEventListener("change", async (e) => {
    const file = e.target.files?.[0];
    if (!file || !imgPerfil) return;

    // Preview local inmediato antes de que responda el servidor
    const reader = new FileReader();
    reader.onload = () => { imgPerfil.src = reader.result; };
    reader.readAsDataURL(file);

    try {
      const res = await usuariosApi.uploadFoto(file);
      aplicarFoto(res?.fotoUrl || res?.url);
      mostrarToast("Foto de perfil actualizada.", "exito");
    } catch (err) {
      // Restaura la foto anterior si falla
      imgPerfil.src = getCurrentUser()?.fotoUrl
        || localStorage.getItem("user_photo")
        || assetUrl("assets/images/usuario.png");
      mostrarToast("No se pudo subir la foto: " + err.message, "error");
    } finally {
      e.target.value = ""; // permite seleccionar el mismo archivo de nuevo
    }
  });
}
