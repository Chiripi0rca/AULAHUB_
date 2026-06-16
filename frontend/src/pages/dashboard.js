import { dashboardApi, actividadLogApi } from "../api/gestion.js";
import { getCurrentUser, hasRole, isAdminCentral } from "../api/auth-session.js";
import { escapeHtml, mostrarToast } from "../layout.js";
import { soloAdmin } from "../route-guard.js";
import { reportesApi } from "../api/reportes.js";
import { aulasApi, facultadesApi } from "../api/catalogos.js";
import { isoToBackendFecha } from "../api/mappers.js";

// Dashboard: estadísticas y gestión administrativa según rol del usuario.
soloAdmin();

document.addEventListener("DOMContentLoaded", async () => {
  const user = getCurrentUser();
  if (!user) return;

  ajustarTitulo();
  mostrarAccionesRapidas();
  await Promise.all([cargarEstadisticas(user), cargarLog(user)]);
  configurarSecciones(user);
});

function mostrarAccionesRapidas() {
  const cont = document.getElementById("acciones-rapidas");
  if (!cont) return;

  const link = (href, icono, label, clase = "") =>
    `<a href="${href}" class="accion-rapida-btn ${clase}">
       <span class="material-symbols-outlined">${icono}</span>${label}
     </a>`;

  let html = "";
  if (isAdminCentral()) {
    html = link("solicitudes.html", "task_alt", "Solicitudes") +
           link("personas.html",    "group",    "Personas") +
           link("gestion-catalogos.html", "folder", "Catálogos") +
           link("facultades.html",  "account_balance", "Facultades");
  } else if (hasRole("SUPER_ADMIN")) {
    html = link("solicitudes.html",        "task_alt",  "Solicitudes") +
           link("personas.html",           "group",     "Personas") +
           link("gestion-catalogos.html",  "folder",    "Catálogos");
  } else {
    // ADMIN
    html = link("solicitudes.html", "task_alt", "Ver solicitudes pendientes", "accion-primary");
  }

  cont.innerHTML = html;
  cont.style.display = "flex";
}

// ── Título dinámico según rol ──────────────────────────────────────────────

function ajustarTitulo() {
  const titulo = document.getElementById("dashboard-titulo");
  if (!titulo) return;
  if (isAdminCentral())          titulo.textContent = "Dashboard — Admin Central";
  else if (hasRole("SUPER_ADMIN")) titulo.textContent = "Dashboard — Super Admin";
  else                             titulo.textContent = "Dashboard — Admin";
}

// ── Estadísticas ──────────────────────────────────────────────────────────

async function cargarEstadisticas(user) {
  const loading = document.getElementById("stats-loading");
  const grid    = document.getElementById("stats-grid");
  try {
    let datos;
    if (isAdminCentral())            datos = await dashboardApi.adminCentral();
    else if (hasRole("SUPER_ADMIN")) datos = await dashboardApi.superAdmin(user.id);
    else                             datos = await dashboardApi.admin(user.id);

    loading.style.display = "none";
    grid.innerHTML = construirCards(datos);
    grid.style.display = "grid";
  } catch (err) {
    loading.textContent = "No se pudieron cargar las estadísticas.";
    console.error(err);
  }
}

function card(numero, etiqueta, variante = "") {
  return `<div class="stat-card ${variante}">
    <div class="stat-numero">${numero ?? "—"}</div>
    <div class="stat-label">${etiqueta}</div>
  </div>`;
}

function construirCards(d) {
  if (isAdminCentral()) {
    return [
      card(d.totalFacultades,          "Facultades"),
      card(d.totalAulasActivas,        "Aulas activas"),
      card(d.totalUsuariosActivos,     "Usuarios activos"),
      card(d.totalReservasPendientes,  "Pendientes",  "stat-aviso"),
      card(d.totalReservasAceptadas,   "Aceptadas",   "stat-exito"),
      card(d.totalReservasHoy,         "Hoy"),
    ].join("");
  }
  if (hasRole("SUPER_ADMIN")) {
    return [
      card(`${d.totalAulasActivas ?? "—"}/${d.totalAulas ?? "—"}`, "Aulas activas / total"),
      card(d.totalUsuariosActivos,     "Usuarios activos"),
      card(d.totalReservasPendientes,  "Pendientes",  "stat-aviso"),
      card(d.totalReservasAceptadas,   "Aceptadas",   "stat-exito"),
      card(d.totalReservasHoy,         "Hoy"),
    ].join("");
  }
  // ADMIN
  return [
    card(d.totalAulasAsignadas,      "Aulas asignadas"),
    card(d.totalReservasPendientes,  "Pendientes",  "stat-aviso"),
    card(d.totalReservasAceptadas,   "Aceptadas",   "stat-exito"),
    card(d.totalReservasHoy,         "Hoy"),
  ].join("");
}

// ── Log de actividad ──────────────────────────────────────────────────────

async function cargarLog(user) {
  const loading   = document.getElementById("log-loading");
  const vacio     = document.getElementById("log-vacio");
  const container = document.getElementById("log-container");
  const body      = document.getElementById("log-body");

  try {
    let respuesta;
    if (isAdminCentral())            respuesta = await actividadLogApi.adminCentral();
    else if (hasRole("SUPER_ADMIN")) respuesta = await actividadLogApi.superAdmin(user.id);
    else                             respuesta = await actividadLogApi.admin(user.id);

    // El backend devuelve PageResponseDTO — los datos están en .contenido
    const logs = respuesta?.contenido ?? respuesta ?? [];

    loading.style.display = "none";

    if (!logs?.length) { vacio.style.display = "block"; return; }

    body.innerHTML = logs.map(l => {
      const fecha = l.createdAt
        ? new Date(l.createdAt).toLocaleString("es-MX", { dateStyle: "short", timeStyle: "short" })
        : "—";
      return `<tr>
        <td>${escapeHtml(l.usuarioNombre) || "—"}</td>
        <td><span class="log-accion">${escapeHtml(l.accion) || "—"}</span></td>
        <td>${escapeHtml(l.detalle) || "—"}</td>
        <td class="log-fecha">${fecha}</td>
      </tr>`;
    }).join("");

    container.style.display = "block";
  } catch {
    loading.textContent = "Error al cargar la actividad.";
  }
}

// ── Secciones extras por rol ──────────────────────────────────────────────

// Lee los date inputs y los convierte al formato dd-MM-yyyy que espera el backend.
function getFechasReporte() {
  const desde = document.getElementById("reporte-desde")?.value;
  const hasta = document.getElementById("reporte-hasta")?.value;
  return {
    desde: desde ? isoToBackendFecha(desde) : undefined,
    hasta: hasta ? isoToBackendFecha(hasta) : undefined,
  };
}

function configurarSecciones(user) {
  const esSuperAdmin    = hasRole("SUPER_ADMIN");
  const esAdminCentral  = isAdminCentral();

  // Sección registrar usuario (solo enlace a la página dedicada)
  if (esSuperAdmin || esAdminCentral) {
    document.getElementById("seccion-crear-usuario").style.display = "block";
  }

  // Exportar
  if (esSuperAdmin || esAdminCentral) {
    document.getElementById("seccion-exportar").style.display = "block";

    document.getElementById("btn-limpiar-fechas")?.addEventListener("click", () => {
      const desdeInput = document.getElementById("reporte-desde");
      const hastaInput = document.getElementById("reporte-hasta");
      if (desdeInput) desdeInput.value = "";
      if (hastaInput) hastaInput.value = "";
    });
  }

  if (esAdminCentral) {
    const btnExcel = document.getElementById("btn-excel-global");
    btnExcel.style.display = "";
    btnExcel.addEventListener("click", async () => {
      try {
        mostrarToast("Generando Excel...", "");
        await reportesApi.excelGlobal(getFechasReporte());
      } catch (err) {
        mostrarToast("Error al exportar: " + err.message, "error");
      }
    });
  }

  if (esSuperAdmin || esAdminCentral) {
    const btnPdf = document.getElementById("btn-pdf-aula");
    btnPdf.style.display = "";
    btnPdf.addEventListener("click", () => abrirModalPdfAula(esAdminCentral));
  }
}

// ── Modal: reporte PDF por aula (selector de facultad/aula + filtro de fechas) ──

function crearModalPdf() {
  const div = document.createElement("div");
  div.id = "modal-pdf-aula";
  div.style.cssText = "display:none; position:fixed; inset:0; background:rgba(0,0,0,.45); z-index:1000; align-items:center; justify-content:center;";
  div.innerHTML = `
    <div style="background:#fff; border-radius:12px; padding:26px; max-width:460px; width:90%; box-shadow:0 8px 32px rgba(0,0,0,.18);">
      <h3 style="margin:0 0 4px; font-size:17px; color:#111827;">Reporte PDF por aula</h3>
      <p style="margin:0 0 18px; font-size:13px; color:#6b7280;">Elige el aula y, opcionalmente, un rango de fechas.</p>

      <div id="pdf-row-facultad" class="form-field" style="margin-bottom:14px;">
        <label for="pdf-facultad">Facultad</label>
        <select id="pdf-facultad"><option value="">Cargando...</option></select>
      </div>

      <div class="form-field" style="margin-bottom:14px;">
        <label for="pdf-aula">Aula</label>
        <select id="pdf-aula"><option value="">Selecciona una facultad primero</option></select>
      </div>

      <div style="display:flex; gap:12px; margin-bottom:18px;">
        <div class="form-field" style="flex:1;">
          <label for="pdf-desde">Desde (opcional)</label>
          <input type="date" id="pdf-desde">
        </div>
        <div class="form-field" style="flex:1;">
          <label for="pdf-hasta">Hasta (opcional)</label>
          <input type="date" id="pdf-hasta">
        </div>
      </div>

      <div style="display:flex; gap:10px; justify-content:flex-end;">
        <button id="pdf-cancelar" style="padding:9px 18px; border:1px solid #d1d5db; border-radius:8px; background:#f9fafb; cursor:pointer; font-size:14px;">Cancelar</button>
        <button id="pdf-descargar" class="btn-action" style="padding:9px 18px;">
          <span class="material-symbols-outlined" style="font-size:18px;">picture_as_pdf</span> Descargar
        </button>
      </div>
    </div>`;
  document.body.appendChild(div);
  div.addEventListener("click", (e) => { if (e.target === div) div.style.display = "none"; });
  div.querySelector("#pdf-cancelar").onclick = () => div.style.display = "none";
  return div;
}

async function abrirModalPdfAula(esAdminCentral) {
  const modal = document.getElementById("modal-pdf-aula") || crearModalPdf();
  const selFacultad = modal.querySelector("#pdf-facultad");
  const selAula     = modal.querySelector("#pdf-aula");
  const rowFacultad = modal.querySelector("#pdf-row-facultad");

  modal.style.display = "flex";

  // Carga las aulas de una facultad (o todas las del super admin) en el dropdown de aulas
  const cargarAulas = async (facultadId) => {
    selAula.innerHTML = `<option value="">Cargando aulas...</option>`;
    try {
      const aulas = await aulasApi.listar(facultadId);
      selAula.innerHTML = (aulas || []).length
        ? `<option value="">Selecciona un aula</option>` +
          aulas.map(a => `<option value="${a.id}">${escapeHtml(a.codigo)} — ${escapeHtml(a.nombre)}</option>`).join("")
        : `<option value="">Esta facultad no tiene aulas</option>`;
    } catch {
      selAula.innerHTML = `<option value="">Error al cargar aulas</option>`;
    }
  };

  if (esAdminCentral) {
    // ADMIN_CENTRAL: muestra selector de facultad → al elegir, carga sus aulas
    rowFacultad.style.display = "";
    selFacultad.innerHTML = `<option value="">Cargando...</option>`;
    try {
      const facs = (await facultadesApi.listar() || []).filter(f => f.activa);
      selFacultad.innerHTML = `<option value="">Selecciona una facultad</option>` +
        facs.map(f => `<option value="${f.id}">${escapeHtml(f.nombre)}</option>`).join("");
    } catch {
      selFacultad.innerHTML = `<option value="">Error al cargar facultades</option>`;
    }
    selFacultad.onchange = () => {
      const fid = selFacultad.value;
      if (fid) cargarAulas(fid);
      else selAula.innerHTML = `<option value="">Selecciona una facultad primero</option>`;
    };
  } else {
    // SUPER_ADMIN: sin selector de facultad; carga directo sus aulas (RLS las filtra)
    rowFacultad.style.display = "none";
    await cargarAulas();
  }

  modal.querySelector("#pdf-descargar").onclick = async () => {
    const aulaId = selAula.value;
    if (!aulaId) { mostrarToast("Selecciona un aula.", "aviso"); return; }
    const desde = modal.querySelector("#pdf-desde").value;
    const hasta = modal.querySelector("#pdf-hasta").value;
    try {
      mostrarToast("Generando PDF...", "");
      await reportesApi.aulaPdf(aulaId, {
        desde: desde ? isoToBackendFecha(desde) : undefined,
        hasta: hasta ? isoToBackendFecha(hasta) : undefined,
      });
      modal.style.display = "none";
    } catch (err) {
      mostrarToast("Error: " + (err.message || "no se pudo generar el PDF"), "error");
    }
  };
}

