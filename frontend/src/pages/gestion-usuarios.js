import { usuariosApi } from "../api/gestion.js";
import { hasRole, isAdminCentral } from "../api/auth-session.js";
import { escapeHtml, mostrarToast, pageUrl } from "../layout.js";
import { protegerRuta } from "../route-guard.js";

// Solo SUPER_ADMIN y ADMIN_CENTRAL pueden gestionar usuarios.
protegerRuta();
if (!hasRole("SUPER_ADMIN") && !isAdminCentral()) {
  window.location.replace(pageUrl("aulas.html"));
}

let _todosLosUsuarios = [];

document.addEventListener("DOMContentLoaded", async () => {
  await cargarUsuarios();
  configurarBuscador();
  configurarFormularioEdicion();
});

// ── Carga y renderizado ────────────────────────────────────────────────────

async function cargarUsuarios() {
  const lista = document.getElementById("lista-usuarios");
  lista.innerHTML = '<p class="status-message">Cargando usuarios...</p>';

  try {
    const respuesta = await usuariosApi.listar();
    // Soporta respuesta array directa o paginada
    _todosLosUsuarios = Array.isArray(respuesta)
      ? respuesta
      : (respuesta?.contenido ?? respuesta?.content ?? respuesta?.data ?? []);

    renderLista(_todosLosUsuarios);
  } catch (err) {
    lista.innerHTML = '<p class="status-message">Error al cargar usuarios.</p>';
    console.error(err);
  }
}

function renderLista(usuarios) {
  const lista    = document.getElementById("lista-usuarios");
  const contador = document.getElementById("usuarios-contador");

  if (contador) contador.textContent = `${usuarios.length} usuario${usuarios.length !== 1 ? "s" : ""}`;

  if (!usuarios.length) {
    lista.innerHTML = `
      <div class="status-message empty-message">
        <span class="material-symbols-outlined empty-icon">person_off</span>
        Sin resultados.
      </div>`;
    return;
  }

  lista.innerHTML = usuarios.map(u => {
    const activo     = u.activo !== false;
    const badgeClass = activo ? "badge-activo" : "badge-inactivo";
    const badgeText  = activo ? "Activo" : "Inactivo";
    const btnToggle  = activo
      ? `<button class="btn-toggle btn-desactivar" data-id="${u.id}" title="Desactivar usuario">
           <span class="material-symbols-outlined">person_off</span> Desactivar
         </button>`
      : `<button class="btn-toggle btn-activar" data-id="${u.id}" title="Activar usuario">
           <span class="material-symbols-outlined">person_check</span> Activar
         </button>`;

    return `
    <div class="item-row" id="usuario-row-${u.id}" style="display:flex; align-items:center; gap:12px; padding:14px 0; border-bottom:1px solid #f3f4f6; flex-wrap:wrap;">
      <div style="flex:1; min-width:180px;">
        <div style="font-weight:600; font-size:14px; color:#111827;">${escapeHtml(u.nombre || "Sin nombre")}</div>
        <div style="font-size:12px; color:#6b7280; margin-top:2px;">${escapeHtml(u.email)}</div>
      </div>
      <span class="badge ${badgeClass}" style="font-size:11px; padding:3px 10px; border-radius:999px; font-weight:600;
        background:${activo ? "#d1fae5" : "#fee2e2"}; color:${activo ? "#065f46" : "#991b1b"};">
        ${badgeText}
      </span>
      <div style="display:flex; gap:8px; flex-wrap:wrap;">
        <button class="btn-editar" data-id="${u.id}" data-nombre="${escapeHtml(u.nombre || "")}"
          style="padding:6px 14px; border:1px solid #d1d5db; border-radius:8px; background:#fff; cursor:pointer; font-size:13px; display:flex; align-items:center; gap:4px;">
          <span class="material-symbols-outlined" style="font-size:16px;">edit</span> Editar
        </button>
        ${btnToggle}
      </div>
    </div>`;
  }).join("");

  // Eventos de botones
  lista.querySelectorAll(".btn-editar").forEach(btn => {
    btn.addEventListener("click", () => abrirEdicion(btn.dataset.id, btn.dataset.nombre));
  });

  lista.querySelectorAll(".btn-activar, .btn-desactivar").forEach(btn => {
    btn.addEventListener("click", () => toggleEstado(btn.dataset.id, btn.classList.contains("btn-activar")));
  });
}

// ── Buscador en memoria ────────────────────────────────────────────────────

function configurarBuscador() {
  const input = document.getElementById("buscador-usuarios");
  if (!input) return;

  input.addEventListener("input", () => {
    const txt = input.value.toLowerCase().trim();
    if (!txt) {
      renderLista(_todosLosUsuarios);
      return;
    }
    const filtrados = _todosLosUsuarios.filter(u =>
      (u.nombre || "").toLowerCase().includes(txt) ||
      (u.email  || "").toLowerCase().includes(txt)
    );
    renderLista(filtrados);
  });
}

// ── Formulario edición de nombre ───────────────────────────────────────────

function abrirEdicion(id, nombreActual) {
  const seccion  = document.getElementById("seccion-editar");
  const inputId  = document.getElementById("edit-usuario-id");
  const inputNom = document.getElementById("edit-nombre");
  const exito    = document.getElementById("edit-exito");
  const error    = document.getElementById("edit-error");

  inputId.value  = id;
  inputNom.value = nombreActual;
  exito.style.display = "none";
  error.style.display = "none";
  seccion.style.display = "block";
  seccion.scrollIntoView({ behavior: "smooth", block: "start" });
  inputNom.focus();
}

function configurarFormularioEdicion() {
  const btnGuardar  = document.getElementById("btn-guardar-nombre");
  const btnCancelar = document.getElementById("btn-cancelar-edicion");

  btnCancelar?.addEventListener("click", () => {
    document.getElementById("seccion-editar").style.display = "none";
  });

  btnGuardar?.addEventListener("click", async () => {
    const id      = document.getElementById("edit-usuario-id")?.value;
    const nombre  = document.getElementById("edit-nombre")?.value.trim();
    const errSpan = document.getElementById("err-edit-nombre");
    const exito   = document.getElementById("edit-exito");
    const error   = document.getElementById("edit-error");

    exito.style.display = "none";
    error.style.display = "none";
    if (errSpan) errSpan.textContent = "";

    if (!nombre) {
      if (errSpan) errSpan.textContent = "El nombre no puede estar vacío.";
      document.getElementById("edit-nombre")?.focus();
      return;
    }

    btnGuardar.disabled = true;
    btnGuardar.textContent = "Guardando...";

    try {
      await usuariosApi.actualizar(id, { nombre });

      // Actualiza el cache local
      const u = _todosLosUsuarios.find(x => String(x.id) === String(id));
      if (u) u.nombre = nombre;

      exito.style.display = "block";
      // Refresca el row sin recargar toda la lista
      const row = document.getElementById(`usuario-row-${id}`);
      if (row) {
        const nombreDiv = row.querySelector("div > div");
        if (nombreDiv) nombreDiv.textContent = nombre;
        // Actualiza el data-nombre del botón editar
        const btnEdit = row.querySelector(".btn-editar");
        if (btnEdit) btnEdit.dataset.nombre = escapeHtml(nombre);
      }
    } catch (err) {
      error.style.display = "block";
      error.textContent = "Error: " + (err.message || "No se pudo actualizar.");
    } finally {
      btnGuardar.disabled = false;
      btnGuardar.innerHTML = '<span class="material-symbols-outlined">save</span> Guardar nombre';
    }
  });
}

// ── Activar / Desactivar usuario ───────────────────────────────────────────

async function toggleEstado(id, activar) {
  const accion   = activar ? "activar" : "desactivar";
  const mensaje  = activar
    ? "¿Activar este usuario? Podrá volver a iniciar sesión."
    : "¿Desactivar este usuario? No podrá iniciar sesión hasta que sea reactivado.";

  if (!confirm(mensaje)) return;

  const row = document.getElementById(`usuario-row-${id}`);
  if (row) { row.style.opacity = "0.5"; row.style.pointerEvents = "none"; }

  try {
    if (activar) {
      await usuariosApi.activar(id);
    } else {
      await usuariosApi.desactivar(id);
    }

    // Actualiza el cache local
    const u = _todosLosUsuarios.find(x => String(x.id) === String(id));
    if (u) u.activo = activar;

    mostrarToast(`Usuario ${activar ? "activado" : "desactivado"} correctamente.`, "exito");

    // Re-renderiza la lista completa para reflejar el cambio de estado
    const buscador = document.getElementById("buscador-usuarios");
    const txt = buscador?.value.toLowerCase().trim();
    const visibles = txt
      ? _todosLosUsuarios.filter(u =>
          (u.nombre || "").toLowerCase().includes(txt) ||
          (u.email  || "").toLowerCase().includes(txt))
      : _todosLosUsuarios;
    renderLista(visibles);
  } catch (err) {
    mostrarToast("Error: " + (err.message || `No se pudo ${accion} el usuario.`), "error");
    if (row) { row.style.opacity = "1"; row.style.pointerEvents = "all"; }
  }
}
