// Asignación de (grupo + materia) a maestros — SUPER_ADMIN / ADMIN_CENTRAL.
// Flujo de checkboxes: eliges grupo → marcas las materias que imparte el maestro en ese grupo.
// Los cambios se acumulan en memoria (todos los grupos) y se guardan juntos.
import { usuariosAdminApi } from "../api/usuarios.js";
import { gruposApi } from "../api/catalogos.js";
import { rolesApi } from "../api/gestion.js";
import { hasRole } from "../api/auth-session.js";
import { escapeHtml, mostrarToast, pageUrl } from "../layout.js";
import { soloAdmin } from "../route-guard.js";

if (soloAdmin() && !hasRole("SUPER_ADMIN", "ADMIN_CENTRAL")) {
  window.location.replace(pageUrl("dashboard.html"));
}

let grupos = [];
// Set de claves "grupoId-materiaId" → conjunto de asignaciones del maestro (todos los grupos)
let asignadas = new Set();
// Metadatos para el resumen: clave → { grupoNombre, materiaNombre }
let meta = new Map();

document.addEventListener("DOMContentLoaded", async () => {
  // Los grupos NO se cargan al inicio: se cargan al elegir maestro, filtrados por SU facultad.
  await cargarMaestros();
  document.getElementById("select-maestro").addEventListener("change", onSeleccionarMaestro);
  document.getElementById("select-grupo").addEventListener("change", renderChecklistMaterias);
  document.getElementById("btn-guardar").addEventListener("click", guardar);
});

const clave = (g, m) => `${g}-${m}`;

// Marca como activos los pasos del stepper hasta pasoMax (1, 2 o 3).
function actualizarStepper(pasoMax) {
  document.querySelectorAll(".stepper .step").forEach(el => {
    el.classList.toggle("activo", Number(el.dataset.step) <= pasoMax);
  });
}

// ── Carga inicial ─────────────────────────────────────────────────────────────
async function cargarMaestros() {
  const select = document.getElementById("select-maestro");
  try {
    const profesores = await usuariosAdminApi.listarProfesores();
    if (!profesores?.length) {
      select.innerHTML = `<option value="">No hay maestros registrados</option>`;
      return;
    }
    select.innerHTML = `<option value="">— Selecciona un maestro —</option>` +
      profesores
        .sort((a, b) => (a.nombre || "").localeCompare(b.nombre || ""))
        .map(p => `<option value="${p.id}">${escapeHtml(p.nombre)} (${escapeHtml(p.email)})</option>`)
        .join("");
  } catch (err) {
    select.innerHTML = `<option value="">Error al cargar maestros</option>`;
    mostrarToast("No se pudieron cargar los maestros: " + err.message, "error");
  }
}

// Carga SOLO los grupos de las facultades donde el maestro tiene rol PROFESOR.
async function cargarGruposDeFacultades(facultadIds) {
  const select = document.getElementById("select-grupo");

  if (!facultadIds.length) {
    grupos = [];
    select.innerHTML = `<option value="">El maestro no tiene facultad asignada</option>`;
    return;
  }

  select.innerHTML = `<option value="">Cargando grupos...</option>`;
  try {
    // Un profesor puede tener varias facultades: trae los grupos de cada una y combina.
    const listas = await Promise.all(
      facultadIds.map(fid => gruposApi.listar(fid).catch(() => []))
    );
    const vistos = new Set();
    grupos = listas.flat()
      .filter(g => g.activo !== false)
      .filter(g => { if (vistos.has(g.id)) return false; vistos.add(g.id); return true; });

    if (!grupos.length) {
      select.innerHTML = `<option value="">No hay grupos en la facultad del maestro</option>`;
      return;
    }
    select.innerHTML = `<option value="">Selecciona un grupo</option>` +
      grupos
        .sort((a, b) => (a.nombre || "").localeCompare(b.nombre || ""))
        .map(g => `<option value="${g.id}">${escapeHtml(g.nombre)}</option>`)
        .join("");
  } catch {
    select.innerHTML = `<option value="">Error al cargar grupos</option>`;
  }
}

// Devuelve los IDs de facultad donde el usuario tiene rol PROFESOR.
async function facultadesDelMaestro(usuarioId) {
  const roles = await rolesApi.listarPorUsuario(usuarioId).catch(() => []);
  return [...new Set(
    (roles || [])
      .filter(r => r.tipo === "PROFESOR" && r.facultadId != null)
      .map(r => r.facultadId)
  )];
}

// ── Al elegir maestro: cargar sus asignaciones actuales ─────────────────────────
async function onSeleccionarMaestro(e) {
  const usuarioId = e.target.value;
  const panel = document.getElementById("panel-asignaciones");
  if (!usuarioId) { panel.style.display = "none"; actualizarStepper(1); return; }

  panel.style.display = "block";
  actualizarStepper(2);
  asignadas = new Set();
  meta = new Map();

  try {
    // Carga en paralelo: asignaciones actuales del maestro + sus facultades
    const [actuales, facultadIds] = await Promise.all([
      usuariosAdminApi.obtenerAsignaciones(usuarioId),
      facultadesDelMaestro(usuarioId),
    ]);

    (actuales || []).forEach(a => {
      const k = clave(a.grupoId, a.materiaId);
      asignadas.add(k);
      meta.set(k, { grupoNombre: a.grupoNombre, materiaNombre: a.materiaNombre });
    });

    // Los grupos del dropdown se filtran a la(s) facultad(es) del maestro
    await cargarGruposDeFacultades(facultadIds);
  } catch (err) {
    mostrarToast("No se pudieron cargar los datos del maestro: " + err.message, "error");
  }

  // Resetea la checklist
  document.getElementById("materias-checklist").innerHTML =
    `<p class="status-message">Selecciona un grupo para ver sus materias.</p>`;
  renderResumen();
}

// ── Checklist de materias del grupo elegido ─────────────────────────────────────
async function renderChecklistMaterias() {
  const grupoSel = document.getElementById("select-grupo");
  const cont = document.getElementById("materias-checklist");
  const grupoId = Number(grupoSel.value);
  const grupoNombre = grupoSel.selectedOptions[0]?.textContent || "";

  if (!grupoId) {
    cont.innerHTML = `<p class="status-message">Selecciona un grupo para ver sus materias.</p>`;
    return;
  }

  cont.innerHTML = `<p class="status-message">Cargando materias...</p>`;
  try {
    const materias = (await gruposApi.materias(grupoId)) || [];
    if (!materias.length) {
      cont.innerHTML = `<p class="status-message">Este grupo no imparte materias. Asígnalas primero en Catálogos → Grupos.</p>`;
      return;
    }

    cont.innerHTML = `
      <p style="font-size:13px; color:#6b7280; margin:0 0 8px;">Materias que se imparten en <strong>${escapeHtml(grupoNombre)}</strong>:</p>
      <div style="display:flex; flex-direction:column; gap:8px;">
        ${materias.map(m => {
          const k = clave(grupoId, m.id);
          const checked = asignadas.has(k) ? "checked" : "";
          return `
            <label style="display:flex; align-items:center; gap:10px; padding:8px 12px; border:1px solid #e5e7eb; border-radius:8px; cursor:pointer;">
              <input type="checkbox" data-grupo="${grupoId}" data-grupo-nombre="${escapeHtml(grupoNombre)}"
                     data-materia="${m.id}" data-materia-nombre="${escapeHtml(m.nombre)}" ${checked}>
              <span>${escapeHtml(m.nombre)}</span>
            </label>`;
        }).join("")}
      </div>`;

    cont.querySelectorAll("input[type=checkbox]").forEach(cb =>
      cb.addEventListener("change", () => toggleMateria(cb)));
  } catch {
    cont.innerHTML = `<p class="status-message">Error al cargar materias.</p>`;
  }
}

// Marca/desmarca una materia para el grupo actual y actualiza el resumen.
function toggleMateria(cb) {
  const g = Number(cb.dataset.grupo);
  const m = Number(cb.dataset.materia);
  const k = clave(g, m);
  if (cb.checked) {
    asignadas.add(k);
    meta.set(k, { grupoNombre: cb.dataset.grupoNombre, materiaNombre: cb.dataset.materiaNombre });
  } else {
    asignadas.delete(k);
    meta.delete(k);
  }
  renderResumen();
}

// ── Resumen de TODAS las asignaciones (todos los grupos) ────────────────────────
function renderResumen() {
  const cont = document.getElementById("resumen-asignaciones");
  document.getElementById("contador").textContent = `(${asignadas.size})`;

  // Si ya hay al menos una asignación, el paso 3 (Guardar) queda disponible
  actualizarStepper(asignadas.size > 0 ? 3 : 2);

  if (asignadas.size === 0) {
    cont.innerHTML = `<p class="status-message">Sin asignaciones todavía.</p>`;
    return;
  }

  // Agrupa por grupoId → { grupoNombre, items:[{clave, materiaNombre}] }.
  // La clave es "grupoId-materiaId", de ahí se extrae el grupoId.
  const porGrupo = new Map();
  asignadas.forEach(k => {
    const info = meta.get(k);
    if (!info) return;
    const grupoId = k.split("-")[0];
    if (!porGrupo.has(grupoId)) porGrupo.set(grupoId, { grupoNombre: info.grupoNombre, items: [] });
    porGrupo.get(grupoId).items.push({ clave: k, materiaNombre: info.materiaNombre });
  });

  // Cada grupo es una tarjeta con su botón para quitar el salón completo.
  cont.innerHTML = [...porGrupo.entries()].map(([grupoId, data]) => `
    <div style="border:1px solid #e5e7eb; border-radius:10px; padding:12px 14px; margin-bottom:10px; background:#fff;">
      <div style="display:flex; justify-content:space-between; align-items:center; gap:10px; margin-bottom:8px;">
        <strong style="font-size:14px; color:#111827; display:flex; align-items:center; gap:6px;">
          <span class="material-symbols-outlined" style="font-size:18px; color:#164B8A;">groups</span>
          ${escapeHtml(data.grupoNombre)}
        </strong>
        <button type="button" class="grupo-borrar" data-grupo="${grupoId}" title="Quitar este grupo completo"
          style="display:flex; align-items:center; gap:4px; background:#fee2e2; color:#991b1b; border:none; border-radius:7px; padding:5px 10px; cursor:pointer; font-size:12px; font-weight:600; flex-shrink:0;">
          <span class="material-symbols-outlined" style="font-size:15px;">delete</span> Quitar grupo
        </button>
      </div>
      <div style="display:flex; flex-wrap:wrap; gap:6px;">
        ${data.items.map(it => `
          <span style="display:inline-flex; align-items:center; gap:6px; background:#eff6ff; color:#1e40af; font-size:12px; padding:3px 4px 3px 10px; border-radius:999px;">
            ${escapeHtml(it.materiaNombre)}
            <button type="button" class="chip-borrar" data-clave="${it.clave}" title="Quitar esta materia"
              style="display:flex; align-items:center; justify-content:center; background:#dc2626; color:#fff; border:none; border-radius:50%; width:18px; height:18px; cursor:pointer; padding:0; line-height:1;">
              <span class="material-symbols-outlined" style="font-size:13px; color:#fff;">close</span>
            </button>
          </span>`).join("")}
      </div>
    </div>`).join("");

  cont.querySelectorAll(".chip-borrar").forEach(b =>
    b.addEventListener("click", () => quitarAsignacion(b.dataset.clave)));
  cont.querySelectorAll(".grupo-borrar").forEach(b =>
    b.addEventListener("click", () => quitarGrupo(b.dataset.grupo)));
}

// Quita UNA materia de la lista en memoria. Se confirma al pulsar "Guardar".
function quitarAsignacion(clave) {
  asignadas.delete(clave);
  meta.delete(clave);
  renderResumen();

  const [grupoId, materiaId] = clave.split("-");
  const cb = document.querySelector(
    `#materias-checklist input[data-grupo="${grupoId}"][data-materia="${materiaId}"]`);
  if (cb) cb.checked = false;
}

// Quita TODO un grupo (todas sus materias) — cuando el profe ya no dará ese salón.
function quitarGrupo(grupoId) {
  [...asignadas].forEach(k => {
    if (k.split("-")[0] === String(grupoId)) {
      asignadas.delete(k);
      meta.delete(k);
    }
  });
  renderResumen();

  // Si ese grupo está abierto en la checklist, desmarca todos sus checkboxes
  document.querySelectorAll(`#materias-checklist input[data-grupo="${grupoId}"]`)
    .forEach(cb => { cb.checked = false; });
}

// ── Guardar ──────────────────────────────────────────────────────────────────
async function guardar() {
  const usuarioId = document.getElementById("select-maestro").value;
  if (!usuarioId) return;

  const btn = document.getElementById("btn-guardar");
  btn.disabled = true;
  btn.innerHTML = `<span class="material-symbols-outlined">hourglass_top</span> Guardando...`;

  try {
    // Reconstruye la lista de pares desde el Set de claves "grupoId-materiaId"
    const asignaciones = [...asignadas].map(k => {
      const [grupoId, materiaId] = k.split("-").map(Number);
      return { grupoId, materiaId };
    });
    await usuariosAdminApi.asignarAsignaciones(usuarioId, asignaciones);
    mostrarToast("Asignaciones guardadas correctamente.", "exito");
  } catch (err) {
    mostrarToast("Error al guardar: " + (err.message || ""), "error");
  } finally {
    btn.disabled = false;
    btn.innerHTML = `<span class="material-symbols-outlined">save</span> Guardar asignación`;
  }
}
