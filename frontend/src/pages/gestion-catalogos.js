// Catálogos unificados: aulas, materias, grupos y tipos de reserva en una sola página con tabs.
// Cada tab usa el motor interno initCatalogo con un prefijo de ID para evitar colisiones.
import { aulasApi, materiasApi, tiposReservaApi, gruposApi, facultadesApi } from "../api/catalogos.js";
import { hasRole, isAdminCentral } from "../api/auth-session.js";
import { rolesApi } from "../api/gestion.js";
import { escapeHtml, mostrarToast, pageUrl } from "../layout.js";
import { soloAdmin } from "../route-guard.js";

// El listado de aulas omite la imagen por defecto; aqui la pedimos (conImagen=true)
// para mostrar el thumbnail por fila. El resto de metodos se conservan via spread.
const aulasConImagen = { ...aulasApi, listar: (facId) => aulasApi.listar(facId, true) };

soloAdmin();
if (!hasRole("SUPER_ADMIN") && !isAdminCentral()) {
  window.location.replace(pageUrl("dashboard.html"));
}

// ── Tab switching ─────────────────────────────────────────────────────────────

const tabsIniciados = new Set();

document.addEventListener("DOMContentLoaded", () => {
  document.querySelectorAll(".tab-btn").forEach(btn => {
    btn.addEventListener("click", () => activarTab(btn.dataset.tab));
  });
  // Inicializa el primer tab de inmediato
  activarTab("aulas");
});

function activarTab(nombre) {
  document.querySelectorAll(".tab-btn").forEach(b => b.classList.toggle("tab-activo", b.dataset.tab === nombre));
  document.querySelectorAll(".tab-panel").forEach(p => p.style.display = (p.id === `tab-${nombre}` ? "" : "none"));

  if (!tabsIniciados.has(nombre)) {
    tabsIniciados.add(nombre);
    inicializarTab(nombre);
  }
}

async function inicializarTab(nombre) {
  switch (nombre) {
    case "aulas":    return initCatalogo("aulas-",    aulasConImagen, "aula",              "meeting_room", camposAulas(), {
      // Subir/cambiar la imagen del aula (la subida necesita que el aula ya exista)
      icono: "image",
      title: "Imagen del aula",
      label: "Imagen",
      onClick: (aula, recargar) => subirImagenAula(aula, recargar),
    });
    case "materias": return initCatalogo("materias-", materiasApi,    "materia",           "menu_book",    camposMaterias());
    case "grupos":   return initCatalogo("grupos-",   gruposApi,      "grupo",             "groups",       camposGrupos());
    case "materias-grupo": return initMateriasGrupo();
    case "tipos":    return initCatalogo("tipos-",    tiposReservaApi,"tipo de reserva",   "event",        camposTipos());
  }
}

// ── Motor de catálogo con prefijo de IDs ─────────────────────────────────────

async function initCatalogo(prefix, api, entidad, icono, campos, accionExtra = null) {
  const $  = id => document.getElementById(prefix + id);
  let facultades = [];
  const itemsPorId = new Map();

  // Carga facultades filtradas por rol
  async function cargarFacultades() {
    const select = $("campo-facultad");
    try {
      let todas = ((await facultadesApi.listar()) || []).filter(f => f.activa);
      if (hasRole("SUPER_ADMIN") && !isAdminCentral()) {
        const misRoles = await rolesApi.misRoles().catch(() => []);
        const rolSA    = (misRoles || []).find(r => r.tipo === "SUPER_ADMIN");
        if (rolSA?.facultadId) todas = todas.filter(f => Number(f.id) === Number(rolSA.facultadId));
      }
      facultades = todas;
      select.innerHTML = facultades.map(f => `<option value="${f.id}">${escapeHtml(f.nombre)}</option>`).join("");
    } catch {
      select.innerHTML = `<option value="">Error al cargar</option>`;
    }
  }

  // Construye los campos del formulario
  function construirFormulario() {
    const cont = $("form-campos");
    cont.innerHTML = campos.map(c => {
      const ctrl = c.type === "select"
        ? `<select id="${prefix}campo-${c.name}">${(c.options || []).map(o => `<option value="${o.value}">${o.label}</option>`).join("")}</select>`
        : `<input type="${c.type || "text"}" id="${prefix}campo-${c.name}" placeholder="${c.placeholder || ""}"
               ${c.maxlength ? `maxlength="${c.maxlength}"` : ""}
               ${c.min != null ? `min="${c.min}"` : ""}
               ${c.max != null ? `max="${c.max}"` : ""}
               ${c.upper ? 'style="text-transform:uppercase"' : ""}>`;
      return `<div class="form-field">
        <label>${c.label} <span class="req">*</span></label>
        ${ctrl}
        ${c.hint ? `<span class="password-hint">${c.hint}</span>` : ""}
        <span class="field-error" id="err-${prefix}campo-${c.name}"></span>
      </div>`;
    }).join("");

    campos.filter(c => c.default != null).forEach(c => {
      document.getElementById(`${prefix}campo-${c.name}`).value = c.default;
    });
    campos.filter(c => c.upper).forEach(c => {
      document.getElementById(`${prefix}campo-${c.name}`)
        .addEventListener("input", e => { e.target.value = e.target.value.toUpperCase(); });
    });
  }

  // Carga la lista de ítems
  async function cargarLista() {
    const cont = $("lista-items");
    try {
      const items = await api.listar();
      itemsPorId.clear();
      (items || []).forEach(it => itemsPorId.set(Number(it.id), it));

      if (!items?.length) {
        cont.innerHTML = `
          <div class="empty-state">
            <span class="material-symbols-outlined empty-state-icon">${icono}</span>
            <h4>Aún no hay ${entidad}s</h4>
            <p>Crea ${entidad === "aula" ? "la primera aula" : "el primer registro"} con el formulario de arriba.</p>
          </div>`;
        return;
      }

      const nombreFac = id => facultades.find(f => Number(f.id) === Number(id))?.nombre || `Facultad ${id}`;

      cont.innerHTML = items.map(item => {
        const activo  = item["activa"] !== false && item["activo"] !== false;
        const titulo  = item[campos[0]?.name] || "—";
        const resto   = campos.slice(1)
          .filter(c => item[c.name] != null && item[c.name] !== "")
          .map(c => c.formato ? c.formato(item[c.name]) : item[c.name]).join(" · ");
        const detalle = [resto, nombreFac(item.facultadId)].filter(Boolean).join(" · ");

        const imagen = item.imagenUrl
          ? `<img src="${item.imagenUrl}" alt="" style="width:36px;height:36px;border-radius:6px;object-fit:cover;">`
          : `<span class="material-symbols-outlined" style="color:#164B8A;font-size:22px;">${icono}</span>`;

        const dataAttrs = campos.map(c => `data-${c.name}="${item[c.name] ?? ""}"`).join(" ");

        // Botón extra opcional (ej. "Materias del grupo") — con texto, no solo icono
        const botonExtra = accionExtra
          ? `<button class="btn-fila btn-fila--neutral btn-accion-extra" data-id="${item.id}"><span class="material-symbols-outlined">${accionExtra.icono}</span>${accionExtra.label || accionExtra.title}</button>`
          : "";

        return `<div class="gestion-fila">
          <div class="gestion-fila-info">
            ${imagen}
            <div>
              <strong style="display:block;color:#1f2937;">${escapeHtml(titulo)}</strong>
              <span style="font-size:13px;color:#6b7280;">${escapeHtml(detalle)}</span>
            </div>
            <span class="badge" style="font-size:11px;padding:3px 10px;border-radius:999px;font-weight:600;background:${activo?"#d1fae5":"#fee2e2"};color:${activo?"#065f46":"#991b1b"};">
              ${activo ? "Activo" : "Inactivo"}
            </span>
          </div>
          <div class="gestion-fila-acciones">
            ${botonExtra}
            <button class="btn-fila btn-fila--edit btn-icono-editar" data-id="${item.id}" data-facultad="${item.facultadId}" ${dataAttrs}>
              <span class="material-symbols-outlined">edit</span>Editar
            </button>
            ${activo
              ? `<button class="btn-fila btn-fila--danger btn-desactivar" data-id="${item.id}"><span class="material-symbols-outlined">block</span>Desactivar</button>`
              : `<button class="btn-fila btn-fila--success btn-activar" data-id="${item.id}"><span class="material-symbols-outlined">check_circle</span>Activar</button>`}
          </div>
        </div>`;
      }).join("");

      cont.querySelectorAll(".btn-icono-editar").forEach(b =>
        b.addEventListener("click", () => iniciarEdicion(itemsPorId.get(Number(b.dataset.id)), b.dataset)));
      cont.querySelectorAll(".btn-desactivar").forEach(b =>
        b.addEventListener("click", () => cambiarEstado(Number(b.dataset.id), false)));
      cont.querySelectorAll(".btn-activar").forEach(b =>
        b.addEventListener("click", () => cambiarEstado(Number(b.dataset.id), true)));
      if (accionExtra) {
        cont.querySelectorAll(".btn-accion-extra").forEach(b =>
          b.addEventListener("click", () => accionExtra.onClick(itemsPorId.get(Number(b.dataset.id)), cargarLista)));
      }
    } catch {
      cont.innerHTML = `<p class="status-message">Error al cargar.</p>`;
    }
  }

  // Guardar (crear / editar)
  $("btn-guardar").addEventListener("click", async () => {
    limpiarErrores();
    const id         = $("item-id").value;
    const facultadId = Number($("campo-facultad").value);
    let ok = true;
    if (!facultadId) { setError("campo-facultad", "Selecciona una facultad."); ok = false; }

    const valores = {};
    for (const c of campos) {
      const val = document.getElementById(`${prefix}campo-${c.name}`).value.trim();
      const msg = c.validar ? c.validar(val) : (val ? null : `${c.label} es obligatorio.`);
      if (msg) { setError(`campo-${c.name}`, msg); ok = false; }
      valores[c.name] = c.parse ? c.parse(val) : val;
    }
    if (!ok) return;

    const payload = { facultadId, ...valores };
    const btn = $("btn-guardar");
    btn.disabled = true;
    try {
      if (id) await api.actualizar(Number(id), payload);
      else    await api.crear(payload);
      $("item-exito").style.display = "block";
      cancelarEdicion();
      await cargarLista();
    } catch (err) {
      const el = $("item-error");
      el.textContent = err.message || `Error al guardar.`;
      el.style.display = "block";
    } finally {
      btn.disabled = false;
    }
  });

  $("btn-cancelar").addEventListener("click", cancelarEdicion);

  function iniciarEdicion(item, data) {
    if (!item) return;
    $("item-id").value = item.id;
    $("campo-facultad").value = item.facultadId;
    campos.forEach(c => {
      document.getElementById(`${prefix}campo-${c.name}`).value = item[c.name] ?? (c.default ?? "");
    });
    $("form-titulo").textContent = `Editar ${entidad}`;
    $("btn-cancelar").style.display = "block";
    limpiarErrores();
    $("form-catalogo").scrollIntoView({ behavior: "smooth", block: "start" });
  }

  function cancelarEdicion() {
    $("item-id").value = "";
    $("campo-facultad").selectedIndex = 0;
    campos.forEach(c => {
      document.getElementById(`${prefix}campo-${c.name}`).value = c.default ?? "";
    });
    $("form-titulo").textContent = `Nuevo ${entidad}`;
    $("btn-cancelar").style.display = "none";
    $("item-exito").style.display  = "none";
    $("item-error").style.display  = "none";
  }

  async function cambiarEstado(id, activar) {
    if (!activar && !confirm(`¿Desactivar esta ${entidad}?`)) return;
    try {
      if (activar) await api.activar(id); else await api.desactivar(id);
      mostrarToast(`${entidad.charAt(0).toUpperCase() + entidad.slice(1)} ${activar ? "activada" : "desactivada"}.`, "exito");
      await cargarLista();
    } catch (err) {
      mostrarToast(err.message || "Error al cambiar estado.", "error");
    }
  }

  function setError(name, msg) {
    document.getElementById(`err-${prefix}${name}`)?.classList.add("input-error");
    const el = document.getElementById(`err-${prefix}${name}`);
    if (el) el.textContent = msg;
  }

  function limpiarErrores() {
    ["campo-facultad", ...campos.map(c => `campo-${c.name}`)].forEach(n => {
      document.getElementById(`${prefix}${n}`)?.classList.remove("input-error");
      const el = document.getElementById(`err-${prefix}${n}`);
      if (el) el.textContent = "";
    });
    $("item-exito").style.display = "none";
    $("item-error").style.display = "none";
  }

  construirFormulario();
  // Facultades PRIMERO: la lista necesita sus nombres para mostrarlos (si no, sale "Facultad {id}").
  await cargarFacultades();
  await cargarLista();
}

// ── Configuraciones de campos por catálogo ────────────────────────────────────

function camposAulas() {
  return [
    { name: "codigo", label: "Código", placeholder: "Ej: labA", maxlength: 50,
      validar: v => !v ? "El código es obligatorio." : !/^[a-zA-Z0-9-]+$/.test(v) ? "Solo letras, números y guiones." : null },
    { name: "nombre", label: "Nombre", placeholder: "Ej: Laboratorio de Redes", maxlength: 255,
      validar: v => v ? null : "El nombre es obligatorio." },
    { name: "capacidad", label: "Capacidad", type: "number", min: 1, placeholder: "Ej: 30",
      parse: v => v ? Number(v) : null,
      validar: v => (!v || Number(v) > 0) ? null : "Debe ser un número positivo.",
      formato: v => v ? `Cap: ${v}` : "" },
  ];
}

function camposMaterias() {
  return [
    { name: "nombre", label: "Nombre", placeholder: "Ej: Redes de Computadoras", maxlength: 255,
      validar: v => v ? null : "El nombre es obligatorio." },
  ];
}

function camposGrupos() {
  return [
    { name: "nombre", label: "Nombre del grupo", placeholder: "Ej: 4-1", maxlength: 50,
      validar: v => !v ? "El nombre es obligatorio." : !/^[a-zA-Z0-9-]+$/.test(v) ? "Solo letras, números y guiones." : null },
    // Turno del grupo: al reservar autorrellena y bloquea el turno (un grupo vespertino
    // no puede apartar horario matutino).
    { name: "turno", label: "Turno", type: "select", default: "MATUTINO",
      options: [
        { value: "MATUTINO",   label: "Matutino" },
        { value: "VESPERTINO", label: "Vespertino" },
      ],
      formato: v => String(v).toUpperCase() === "VESPERTINO" ? "Vespertino" : "Matutino" },
  ];
}

function camposTipos() {
  return [
    { name: "clave", label: "Clave", placeholder: "Ej: EXAMEN", maxlength: 50, upper: true,
      validar: v => !v ? "La clave es obligatoria." : !/^[A-Z_]+$/.test(v) ? "Solo mayúsculas y guion bajo." : null },
    { name: "nombre", label: "Nombre", placeholder: "Ej: Examen Parcial", maxlength: 100,
      validar: v => v ? null : "El nombre es obligatorio." },
    { name: "rolMinimo", label: "Rol mínimo", type: "select", default: "PROFESOR",
      options: [
        { value: "PROFESOR",      label: "Profesor (todos)" },
        { value: "ADMIN",         label: "Admin y superiores" },
        { value: "SUPER_ADMIN",   label: "Super Admin y superiores" },
        { value: "ADMIN_CENTRAL", label: "Solo Admin Central" },
      ],
      formato: v => ({ PROFESOR: "Todos", ADMIN: "Admin+", SUPER_ADMIN: "SuperAdmin+", ADMIN_CENTRAL: "Central" }[v] || v) },
    { name: "prioridad", label: "Prioridad (1–5)", type: "number", default: "1", min: 1, max: 5,
      hint: "El número MAYOR es el más prioritario: 5 = máxima prioridad, 1 = mínima. El más alto gana cuando dos reservas compiten por el mismo horario.",
      parse: v => Math.min(5, Math.max(1, Number(v) || 1)),
      validar: v => { const n = Number(v); return (!v || isNaN(n) || n < 1 || n > 5) ? "Debe ser 1–5." : null; },
      formato: v => `Prioridad: ${v}` },
  ];
}

// ── Imagen del aula ───────────────────────────────────────────────────────────

// Abre un selector de archivo y sube la imagen del aula. Requiere que el aula exista.
function subirImagenAula(aula, recargar) {
  const input = document.createElement("input");
  input.type = "file";
  input.accept = "image/jpeg,image/png,image/webp,image/gif";
  input.onchange = async () => {
    const file = input.files?.[0];
    if (!file) return;
    try {
      mostrarToast("Subiendo imagen...", "");
      await aulasApi.subirImagen(aula.id, file);
      mostrarToast(`Imagen de ${aula.nombre} actualizada.`, "exito");
      if (recargar) await recargar();
    } catch (e) {
      mostrarToast("Error al subir la imagen: " + (e.message || ""), "error");
    }
  };
  input.click();
}

// ── Tab: Materias por grupo (curricula) ───────────────────────────────────────
// Reemplaza al modal anterior: se elige un grupo y se administran las materias que
// se imparten en el, agregando o quitando una a la vez (cada cambio se guarda solo).
async function initMateriasGrupo() {
  const selGrupo = document.getElementById("mg-grupo");
  const detalle  = document.getElementById("mg-detalle");
  const selDisp  = document.getElementById("mg-disponibles");
  const btnAgr   = document.getElementById("mg-agregar");
  const cont     = document.getElementById("mg-asignadas");
  const countEl  = document.getElementById("mg-count");

  let gruposPorId = new Map();   // id -> grupo { id, nombre, facultadId }
  let materiasFac = [];          // materias activas de la facultad del grupo elegido
  let asignadas   = [];          // [{ id, nombre }] materias actuales del grupo
  let grupoActual = null;

  // Carga los grupos en el selector (RLS: el SUPER_ADMIN solo ve los de su facultad).
  selGrupo.innerHTML = `<option value="">Cargando grupos...</option>`;
  try {
    const grupos = ((await gruposApi.listar()) || []).filter(g => g.activo !== false);
    gruposPorId = new Map(grupos.map(g => [Number(g.id), g]));
    selGrupo.innerHTML = `<option value="">Selecciona un grupo...</option>` +
      grupos.map(g => `<option value="${g.id}">${escapeHtml(g.nombre)}</option>`).join("");
  } catch {
    selGrupo.innerHTML = `<option value="">Error al cargar grupos</option>`;
    return;
  }

  selGrupo.onchange = () => {
    const id = Number(selGrupo.value);
    grupoActual = id ? gruposPorId.get(id) : null;
    if (!grupoActual) { detalle.style.display = "none"; return; }
    cargarDetalle();
  };

  btnAgr.onclick = () => {
    const id = Number(selDisp.value);
    if (!id || !grupoActual) return;
    guardar([...asignadas.map(m => m.id), id], "Materia agregada al grupo.");
  };

  // Trae las materias del grupo y las materias disponibles de su facultad.
  async function cargarDetalle() {
    detalle.style.display = "block";
    cont.innerHTML = `<p class="status-message">Cargando...</p>`;
    selDisp.innerHTML = "";
    try {
      const [todas, delGrupo] = await Promise.all([
        materiasApi.listar(),
        gruposApi.materias(grupoActual.id),
      ]);
      materiasFac = (todas || []).filter(m =>
        m.activa !== false && Number(m.facultadId) === Number(grupoActual.facultadId));
      asignadas = (delGrupo || []).map(m => ({
        id: Number(m.id),
        nombre: m.nombre ?? materiasFac.find(x => Number(x.id) === Number(m.id))?.nombre ?? `Materia ${m.id}`,
      }));
      render();
    } catch {
      cont.innerHTML = `<p class="status-message">Error al cargar las materias del grupo.</p>`;
    }
  }

  function render() {
    const asignadasIds = new Set(asignadas.map(m => m.id));

    // Lista de materias asignadas, cada una con boton para quitarla.
    countEl.textContent = asignadas.length;
    cont.innerHTML = asignadas.length
      ? asignadas.map(m => `
          <div class="gestion-fila">
            <div class="gestion-fila-info"><strong>${escapeHtml(m.nombre)}</strong></div>
            <div class="gestion-fila-acciones">
              <button class="btn-fila btn-fila--danger mg-quitar" data-id="${m.id}">
                <span class="material-symbols-outlined">remove_circle</span> Quitar
              </button>
            </div>
          </div>`).join("")
      : `<p class="status-message">Este grupo aun no tiene materias. Agrega una con el selector de arriba.</p>`;

    cont.querySelectorAll(".mg-quitar").forEach(b =>
      b.addEventListener("click", () =>
        guardar(asignadas.filter(m => m.id !== Number(b.dataset.id)).map(m => m.id), "Materia quitada del grupo.")));

    // Disponibles = materias activas de la facultad que aun no estan en el grupo.
    const disp = materiasFac.filter(m => !asignadasIds.has(Number(m.id)));
    selDisp.innerHTML = disp.length
      ? disp.map(m => `<option value="${m.id}">${escapeHtml(m.nombre)}</option>`).join("")
      : `<option value="">No hay materias disponibles</option>`;
    btnAgr.disabled = disp.length === 0;
  }

  // Persiste el conjunto completo de materias del grupo (el backend reemplaza la lista).
  async function guardar(ids, okMsg) {
    btnAgr.disabled = true;
    try {
      await gruposApi.setMaterias(grupoActual.id, ids);
      mostrarToast(okMsg, "exito");
      await cargarDetalle();
    } catch (e) {
      mostrarToast("Error al guardar: " + (e.message || ""), "error");
      btnAgr.disabled = false;
    }
  }
}
