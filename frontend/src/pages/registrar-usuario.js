import { registrar } from "../api/auth.js";
import { aulasApi, facultadesApi } from "../api/catalogos.js";
import { usuariosApi as adminUsuariosApi, rolesApi } from "../api/gestion.js";
import { getCurrentUser, hasRole, isAdminCentral } from "../api/auth-session.js";
import { escapeHtml, mostrarToast, pageUrl } from "../layout.js";
import { protegerRuta } from "../route-guard.js";

// Página exclusiva para SUPER_ADMIN y ADMIN_CENTRAL.
protegerRuta();
if (!hasRole("SUPER_ADMIN") && !isAdminCentral()) {
  window.location.replace(pageUrl("aulas.html"));
}

// ── Reglas de visibilidad de campos según el rol que se va a crear ────────
const REGLAS = {
  PROFESOR:     { facultad: true,  facultadObl: true,  turno: false, aulas: false },
  ADMIN:        { facultad: true,  facultadObl: true,  turno: true,  aulas: true  },
  SUPER_ADMIN:  { facultad: true,  facultadObl: true,  turno: false, aulas: false },
  ADMIN_CENTRAL:{ facultad: false, facultadObl: false, turno: false, aulas: false },
};

// ── Inicialización ────────────────────────────────────────────────────────

document.addEventListener("DOMContentLoaded", async () => {
  // ── Sección "Crear cuenta nueva" — solo se inicializa si su formulario existe ──
  if (document.getElementById("form-registrar")) {
    poblarRoles();
    await cargarFacultades();
    actualizarCamposCondicionales();

    document.getElementById("reg-tipo")?.addEventListener("change", actualizarCamposCondicionales);
    document.getElementById("reg-facultad")?.addEventListener("change", () => cargarAulasParaRegistro());
    document.getElementById("btn-registrar")?.addEventListener("click", manejarSubmit);

    ["reg-nombre", "reg-apellidos", "reg-email", "reg-password", "reg-facultad", "reg-turno"]
      .forEach(id => document.getElementById(id)?.addEventListener("input", () => limpiarErrorCampo(id)));
  }

  // ── Sección "Asignar rol" — solo se inicializa si su formulario existe ──
  if (document.getElementById("form-asignar")) {
    await cargarUsuarios();
    poblarRolesAsignar();
    actualizarCamposAsignar();
    await cargarFacultadesAsignar();  // carga propia, no depende de la otra sección

    document.getElementById("asig-tipo")?.addEventListener("change", actualizarCamposAsignar);
    document.getElementById("asig-facultad")?.addEventListener("change", () => cargarAulasParaAsignar());
    document.getElementById("btn-asignar")?.addEventListener("click", manejarAsignar);
  }
});

// ── Poblar roles según quien está logueado ────────────────────────────────

function poblarRoles() {
  const select = document.getElementById("reg-tipo");
  if (!select) return;

  const roles = isAdminCentral()
    ? [
        { value: "PROFESOR",      label: "Profesor"      },
        { value: "ADMIN",         label: "Admin"         },
        { value: "SUPER_ADMIN",   label: "Super Admin"   },
        { value: "ADMIN_CENTRAL", label: "Admin Central" },
      ]
    : [
        { value: "PROFESOR", label: "Profesor" },
        { value: "ADMIN",    label: "Admin"    },
      ];

  select.innerHTML = roles.map(r =>
    `<option value="${r.value}">${r.label}</option>`
  ).join("");
}

// ── Cargar facultades desde API ───────────────────────────────────────────

async function cargarFacultades() {
  const select = document.getElementById("reg-facultad");
  if (!select) return;

  try {
    let lista = (await facultadesApi.listar()) || [];

    // SUPER_ADMIN solo puede registrar usuarios en su propia facultad
    if (hasRole("SUPER_ADMIN") && !isAdminCentral()) {
      const misRoles = await rolesApi.misRoles().catch(() => []);
      const rolSA    = (misRoles || []).find(r => r.tipo === "SUPER_ADMIN");
      const miFacId  = rolSA?.facultadId;
      if (miFacId) lista = lista.filter(f => Number(f.id) === Number(miFacId));
    }

    select.innerHTML = `<option value="">— Selecciona una facultad —</option>`
      + lista.map(f => `<option value="${f.id}">${escapeHtml(f.nombre)}</option>`).join("");
  } catch {
    select.innerHTML = `<option value="">Error al cargar facultades</option>`;
    mostrarToast("No se pudieron cargar las facultades.", "aviso");
  }
}

// ── Mostrar/ocultar campos condicionales ──────────────────────────────────

function actualizarCamposCondicionales() {
  const tipo   = document.getElementById("reg-tipo")?.value || "PROFESOR";
  const reglas = REGLAS[tipo] || REGLAS.PROFESOR;

  const campFacultad = document.getElementById("campo-facultad");
  const campTurno    = document.getElementById("campo-turno");
  const campAulas    = document.getElementById("campo-aulas");
  const labelReq     = document.getElementById("facultad-req");

  if (campFacultad) campFacultad.style.display = reglas.facultad ? "" : "none";
  if (campTurno)    campTurno.style.display    = reglas.turno    ? "" : "none";
  if (campAulas)    campAulas.style.display    = reglas.aulas    ? "" : "none";

  if (labelReq) {
    labelReq.textContent = reglas.facultadObl ? "*" : "(opcional)";
    labelReq.className   = reglas.facultadObl ? "req" : "";
  }

  if (!reglas.facultad) limpiarErrorCampo("reg-facultad");
  if (!reglas.turno)    limpiarErrorCampo("reg-turno");
  if (!reglas.aulas)    document.getElementById("reg-aulas-lista") &&
                        (document.getElementById("reg-aulas-lista").innerHTML = "");

  if (reglas.aulas) cargarAulasParaRegistro();
}

// ── Validaciones ─────────────────────────────────────────────────────────

const REGEX_LETRAS   = /^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\s]+$/;
const REGEX_EMAIL    = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const REGEX_PASSWORD = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@#$%^&+=!]).{8,}$/;

function validarTodo() {
  limpiarTodosLosErrores();

  const nombre     = val("reg-nombre");
  const apellidos  = val("reg-apellidos");
  const email      = val("reg-email");
  const password   = val("reg-password");
  const tipo       = val("reg-tipo");
  const facultadId = val("reg-facultad");
  const reglas     = REGLAS[tipo] || REGLAS.PROFESOR;

  let ok = true;

  if (!nombre) {
    setError("reg-nombre", "Campo obligatorio."); ok = false;
  } else if (!REGEX_LETRAS.test(nombre)) {
    setError("reg-nombre", "Solo letras y espacios."); ok = false;
  }

  if (!apellidos) {
    setError("reg-apellidos", "Campo obligatorio."); ok = false;
  } else if (!REGEX_LETRAS.test(apellidos)) {
    setError("reg-apellidos", "Solo letras y espacios."); ok = false;
  }

  if (!email) {
    setError("reg-email", "Campo obligatorio."); ok = false;
  } else if (!REGEX_EMAIL.test(email)) {
    setError("reg-email", "Formato de correo inválido."); ok = false;
  }

  if (!password) {
    setError("reg-password", "Campo obligatorio."); ok = false;
  } else if (!REGEX_PASSWORD.test(password)) {
    setError("reg-password", "Mínimo 8 caracteres: mayúscula, minúscula, número y carácter especial."); ok = false;
  }

  if (reglas.facultad && reglas.facultadObl && !facultadId) {
    setError("reg-facultad", "Selecciona una facultad."); ok = false;
  }

  return ok;
}

// ── Envío del formulario ──────────────────────────────────────────────────

async function manejarSubmit() {
  if (!validarTodo()) return;

  const tipo   = val("reg-tipo");
  const reglas = REGLAS[tipo] || REGLAS.PROFESOR;

  const payload = {
    nombre:    val("reg-nombre"),
    apellidos: val("reg-apellidos"),
    email:     val("reg-email"),
    password:  val("reg-password"),
    tipo,
  };

  if (reglas.facultad) {
    const fid = val("reg-facultad");
    if (fid) payload.facultadId = Number(fid);
  }

  if (reglas.turno) {
    payload.turno = val("reg-turno");
  }

  if (reglas.aulas) {
    const aulaIds = getAulasSeleccionadas("reg-aulas-lista");
    if (aulaIds.length > 0) payload.aulaIds = aulaIds;
  }

  const btn = document.getElementById("btn-registrar");
  btn.disabled = true;
  btn.textContent = "Creando...";

  // Oculta mensajes anteriores
  ocultar("reg-exito");
  ocultar("reg-error-global");

  try {
    await registrar(payload);
    mostrar("reg-exito");
    document.getElementById("form-registrar").reset();
    actualizarCamposCondicionales();
    poblarRoles(); // Restaura el dropdown tras el reset
    await cargarFacultades();
  } catch (err) {
    manejarError(err);
  } finally {
    btn.disabled = false;
    btn.innerHTML = '<span class="material-symbols-outlined">person_add</span> Crear usuario';
  }
}

// ── Manejo de errores HTTP ────────────────────────────────────────────────

function manejarError(err) {
  const status = err.status;
  const data   = err.data;

  // 409 — Correo duplicado
  if (status === 409) {
    setError("reg-email", "Este correo ya está registrado.");
    return;
  }

  // 422 — Regla de negocio (ej. SUPER_ADMIN no puede crear ADMIN_CENTRAL)
  if (status === 422) {
    mostrarErrorGlobal(err.message || "Operación no permitida por las reglas del sistema.");
    return;
  }

  // 400 — Validación de campos del backend
  if (status === 400 && data) {
    const erroresCampo = data.errors || data.fieldErrors || {};
    let mostroAlguno = false;

    Object.entries(erroresCampo).forEach(([campo, msg]) => {
      const inputId = `reg-${campo.toLowerCase()}`;
      if (document.getElementById(inputId)) {
        setError(inputId, msg);
        mostroAlguno = true;
      }
    });

    if (!mostroAlguno) {
      mostrarErrorGlobal(err.message || "Error de validación en el servidor.");
    }
    return;
  }

  // Error genérico
  mostrarErrorGlobal(err.message || "Error inesperado. Intenta de nuevo.");
}

// ── Helpers ───────────────────────────────────────────────────────────────

const val  = (id) => document.getElementById(id)?.value?.trim() ?? "";
const ocultar = (id) => { const el = document.getElementById(id); if (el) el.style.display = "none"; };
const mostrar = (id) => { const el = document.getElementById(id); if (el) el.style.display = "block"; };

function setError(inputId, mensaje) {
  const input  = document.getElementById(inputId);
  const errId  = inputId.replace("reg-", "err-");
  const errEl  = document.getElementById(errId);
  if (input) input.classList.add("input-error");
  if (errEl) errEl.textContent = mensaje;
}

function limpiarErrorCampo(inputId) {
  const input = document.getElementById(inputId);
  const errId = inputId.replace("reg-", "err-");
  const errEl = document.getElementById(errId);
  if (input) input.classList.remove("input-error");
  if (errEl) errEl.textContent = "";
}

function limpiarTodosLosErrores() {
  document.querySelectorAll(".field-error").forEach(el => (el.textContent = ""));
  document.querySelectorAll(".input-error").forEach(el => el.classList.remove("input-error"));
  ocultar("reg-error-global");
  ocultar("reg-exito");
}

function mostrarErrorGlobal(mensaje) {
  const el = document.getElementById("reg-error-global");
  if (!el) return;
  el.textContent = mensaje;
  el.style.display = "block";
  el.scrollIntoView({ behavior: "smooth", block: "nearest" });
}

// ══════════════════════════════════════════════════════════════════════════
// SECCIÓN 2 — Asignar facultad / rol a usuario existente
// Endpoint: POST /api/roles  { usuarioId, tipo, facultadId?, turno? }
// ══════════════════════════════════════════════════════════════════════════

// ── Buscador de usuarios ──────────────────────────────────────────────────
// position:fixed garantiza que el dropdown flote sobre todo, sin importar
// el overflow ni el z-index de los contenedores padre.

let _usuariosCache = [];

async function cargarUsuarios() {
  try {
    const respuesta = await adminUsuariosApi.listar();

    // El backend puede devolver array directo o objeto paginado { content: [...] }
    const lista = Array.isArray(respuesta)
      ? respuesta
      : (respuesta?.content ?? respuesta?.usuarios ?? respuesta?.data ?? []);

    _usuariosCache = lista.map(u => ({
      id:    u.id,
      label: [[u.nombre, u.apellidos].filter(Boolean).join(" "), u.email]
               .filter(Boolean).join(" — "),
    }));

    inicializarBuscador();
  } catch (err) {
    mostrarToast("No se pudo cargar la lista de usuarios.", "aviso");
  }
}

function inicializarBuscador() {
  const inputBusq = document.getElementById("asig-usuario-search");
  const inputHide = document.getElementById("asig-usuario");
  if (!inputBusq || !inputHide) return;

  // Limpia una instancia anterior si existe
  document.getElementById("asig-usuario-lista")?.remove();

  // Crea el dropdown y lo monta DIRECTAMENTE en <body>
  // Así escapa cualquier overflow, transform o stacking context del formulario
  const lista = document.createElement("ul");
  lista.id        = "asig-usuario-lista";
  lista.className = "busqueda-lista";
  document.body.appendChild(lista);

  const posicionar = () => {
    const r        = inputBusq.getBoundingClientRect();
    lista.style.top   = (r.bottom + window.scrollY + 4) + "px";
    lista.style.left  = (r.left   + window.scrollX)     + "px";
    lista.style.width = r.width   + "px";
  };

  const renderizar = (filtro = "") => {
    const txt       = filtro.toLowerCase();
    const filtrados = _usuariosCache.filter(u => u.label.toLowerCase().includes(txt));

    lista.innerHTML = filtrados.length
      ? filtrados.map(u => `<li data-id="${escapeHtml(String(u.id))}">${escapeHtml(u.label)}</li>`).join("")
      : `<li class="sin-res">Sin resultados</li>`;

    lista.querySelectorAll("li[data-id]").forEach(li => {
      li.addEventListener("mousedown", e => {
        e.preventDefault();             // evita blur antes de registrar el click
        inputHide.value = li.dataset.id;
        inputBusq.value = li.textContent;
        lista.style.display = "none";
        inputBusq.classList.remove("input-error");
        document.getElementById("err-asig-usuario").textContent = "";
        cargarRolesUsuario(li.dataset.id);   // muestra los roles actuales del usuario elegido
      });
    });
  };

  const abrir = () => {
    posicionar();
    renderizar(inputBusq.value);
    lista.style.display = "block";
  };

  const cerrar = () => { lista.style.display = "none"; };

  inputBusq.addEventListener("focus", abrir);

  inputBusq.addEventListener("input", () => {
    inputHide.value = "";
    posicionar();
    renderizar(inputBusq.value);
    lista.style.display = "block";
  });

  inputBusq.addEventListener("blur", () => setTimeout(cerrar, 150));

  window.addEventListener("scroll", () => {
    if (lista.style.display === "block") posicionar();
  }, true);

  window.addEventListener("resize", () => {
    if (lista.style.display === "block") posicionar();
  });
}

function poblarRolesAsignar() {
  const select = document.getElementById("asig-tipo");
  if (!select) return;

  const roles = isAdminCentral()
    ? [
        { value: "PROFESOR",      label: "Profesor"      },
        { value: "ADMIN",         label: "Admin"         },
        { value: "SUPER_ADMIN",   label: "Super Admin"   },
        { value: "ADMIN_CENTRAL", label: "Admin Central" },
      ]
    : [
        { value: "PROFESOR", label: "Profesor" },
        { value: "ADMIN",    label: "Admin"    },
      ];

  select.innerHTML = roles.map(r =>
    `<option value="${r.value}">${r.label}</option>`
  ).join("");
}

function actualizarCamposAsignar() {
  const tipo   = document.getElementById("asig-tipo")?.value || "PROFESOR";
  const reglas = REGLAS[tipo] || REGLAS.PROFESOR;

  // Si ya hay un usuario elegido y un rol seleccionado, el paso 3 (Asigna) queda disponible
  if (document.getElementById("asig-usuario")?.value) actualizarStepperAsignar(3);

  const campFacultad = document.getElementById("asig-campo-facultad");
  const campTurno    = document.getElementById("asig-campo-turno");
  const campAulas    = document.getElementById("asig-campo-aulas");
  const labelReq     = document.getElementById("asig-facultad-req");

  if (campFacultad) campFacultad.style.display = reglas.facultad ? "" : "none";
  if (campTurno)    campTurno.style.display    = reglas.turno    ? "" : "none";
  if (campAulas)    campAulas.style.display    = reglas.aulas    ? "" : "none";

  if (labelReq) {
    labelReq.textContent = reglas.facultadObl ? "*" : "(opcional)";
    labelReq.className   = reglas.facultadObl ? "req" : "";
  }

  if (!reglas.aulas) {
    const lista = document.getElementById("asig-aulas-lista");
    if (lista) lista.innerHTML = `<span style="color:#9ca3af; font-size:13px;">Selecciona una facultad primero</span>`;
  }

  if (reglas.aulas) cargarAulasParaAsignar();
}

async function manejarAsignar() {
  // Limpia mensajes anteriores
  ocultar("asig-exito");
  ocultar("asig-error-global");
  document.querySelectorAll("[id^='err-asig-']").forEach(el => (el.textContent = ""));
  document.querySelectorAll("#form-asignar .input-error").forEach(el => el.classList.remove("input-error"));

  const usuarioId = val("asig-usuario");
  const tipo      = val("asig-tipo");
  const facultadId = val("asig-facultad");
  const turno     = val("asig-turno");
  const reglas    = REGLAS[tipo] || REGLAS.PROFESOR;

  // Validaciones básicas
  let ok = true;

  if (!usuarioId) {
    setErrorAsig("asig-usuario", "Selecciona un usuario."); ok = false;
  }
  if (reglas.facultad && reglas.facultadObl && !facultadId) {
    setErrorAsig("asig-facultad", "Selecciona una facultad."); ok = false;
  }
  if (!ok) return;

  // Construir payload
  const payload = { usuarioId: Number(usuarioId), tipo };
  if (reglas.facultad && facultadId) payload.facultadId = Number(facultadId);
  if (reglas.turno && turno)         payload.turno = turno;
  if (reglas.aulas) {
    const aulaIds = getAulasSeleccionadas("asig-aulas-lista");
    if (aulaIds.length > 0) payload.aulaIds = aulaIds;
  }

  const btn = document.getElementById("btn-asignar");
  btn.disabled = true;
  btn.textContent = "Asignando...";

  try {
    await rolesApi.asignar(payload);
    mostrar("asig-exito");
    // Se mantiene el usuario seleccionado para poder seguir gestionando sus roles.
    poblarRolesAsignar();
    actualizarCamposAsignar();
    await cargarRolesUsuario(usuarioId);
  } catch (err) {
    const msg = err.status === 422
      ? err.message
      : err.status === 409
        ? "Este usuario ya tiene ese rol asignado."
        : err.message || "Error al asignar. Intenta de nuevo.";

    const errEl = document.getElementById("asig-error-global");
    if (errEl) { errEl.textContent = msg; errEl.style.display = "block"; }
  } finally {
    btn.disabled = false;
    btn.innerHTML = '<span class="material-symbols-outlined">assignment_ind</span> Asignar';
  }
}

function setErrorAsig(inputId, mensaje) {
  // Para el buscador custom, el input visible es asig-usuario-search
  const visibleId = inputId === "asig-usuario" ? "asig-usuario-search" : inputId;
  const input = document.getElementById(visibleId);
  const errEl = document.getElementById(`err-${inputId}`);
  if (input) input.classList.add("input-error");
  if (errEl) errEl.textContent = mensaje;
}

// ══════════════════════════════════════════════════════════════════════════
// Roles actuales del usuario — ver y revocar (DELETE /api/roles/{id})
// Permite corregir un rol asignado por error.
// ══════════════════════════════════════════════════════════════════════════

const NOMBRE_ROL = {
  PROFESOR: "Profesor", ADMIN: "Admin",
  SUPER_ADMIN: "Super Admin", ADMIN_CENTRAL: "Admin Central",
};

const ICONO_ROL = {
  PROFESOR: "📚", ADMIN: "🛡️", SUPER_ADMIN: "⭐", ADMIN_CENTRAL: "🌐",
};

const NOMBRE_TURNO = {
  MATUTINO: "Matutino", VESPERTINO: "Vespertino", AMBOS: "Ambos turnos",
};

function nombreFacultadPorId(id) {
  if (id == null) return "";
  // Lee del select que exista en la página (registrar-usuario o asignar-rol)
  const opt = document.querySelector(`#reg-facultad option[value="${id}"]`)
           || document.querySelector(`#asig-facultad option[value="${id}"]`);
  return opt ? opt.textContent : `Facultad ${id}`;
}

// Marca como activos los pasos del stepper de asignación hasta pasoMax.
function actualizarStepperAsignar(pasoMax) {
  document.querySelectorAll(".dashboard-section .stepper .step").forEach(el => {
    el.classList.toggle("activo", Number(el.dataset.step) <= pasoMax);
  });
}

async function cargarRolesUsuario(usuarioId) {
  const panel = document.getElementById("asig-panel");
  const cont  = document.getElementById("asig-roles-actuales");
  const lista = document.getElementById("asig-roles-lista");
  if (!cont || !lista) return;

  if (!usuarioId) {
    cont.style.display = "none";
    if (panel) panel.style.display = "none";
    actualizarStepperAsignar(1);
    return;
  }

  // Al elegir usuario: muestra el panel de configuración y avanza el stepper al paso 2
  if (panel) panel.style.display = "block";
  actualizarStepperAsignar(2);

  cont.style.display = "";
  lista.innerHTML = `<span style="color:#6b7280; font-size:13px;">Cargando...</span>`;

  try {
    const roles = await rolesApi.listarPorUsuario(usuarioId);
    renderRolesActuales(roles || []);
  } catch {
    lista.innerHTML = `<span style="color:#6b7280; font-size:13px;">No se pudieron cargar los roles.</span>`;
  }
}

function renderRolesActuales(roles) {
  const lista = document.getElementById("asig-roles-lista");
  if (!lista) return;

  if (roles.length === 0) {
    lista.innerHTML = `<span style="color:#6b7280; font-size:13px;">Sin roles asignados.</span>`;
    return;
  }

  lista.innerHTML = roles.map(r => {
    const icono    = ICONO_ROL[r.tipo]    || "👤";
    const nombre   = NOMBRE_ROL[r.tipo]   || r.tipo;
    const facultad = nombreFacultadPorId(r.facultadId);
    const turno    = NOMBRE_TURNO[r.turno] || "";

    // Línea de subtítulo: facultad · turno (lo que aplique)
    const subtitulo = [facultad, turno].filter(Boolean).join(" · ");

    // Aulas (solo ADMIN)
    const aulasHtml = (r.aulas && r.aulas.length > 0)
      ? `<div class="rol-card-aulas">
           ${r.aulas.map(a => `<span class="aula-tag">💻 ${escapeHtml(a.nombre)}</span>`).join("")}
         </div>`
      : "";

    return `
      <div class="rol-card">
        <div class="rol-card-header">
          <span class="rol-card-icono">${icono}</span>
          <div class="rol-card-info">
            <strong>${nombre}</strong>
            ${subtitulo ? `<span>${escapeHtml(subtitulo)}</span>` : ""}
          </div>
          <button type="button" class="btn-icono btn-icono-danger btn-revocar-rol"
                  data-id="${r.id}" title="Quitar rol">
            <span class="material-symbols-outlined" style="font-size:16px;">close</span>
          </button>
        </div>
        ${aulasHtml}
      </div>`;
  }).join("");

  lista.querySelectorAll(".btn-revocar-rol").forEach(btn =>
    btn.addEventListener("click", () => eliminarRol(Number(btn.dataset.id))));
}

async function eliminarRol(rolId) {
  if (!confirm("¿Quitar este rol al usuario?")) return;
  try {
    await rolesApi.eliminar(rolId);
    mostrarToast("Rol revocado.", "exito");
    await cargarRolesUsuario(document.getElementById("asig-usuario").value);
  } catch (err) {
    mostrarToast("Error al revocar el rol: " + err.message, "error");
  }
}

// ── Aulas para sección 1 (crear usuario) ─────────────────────────────────────

async function cargarAulasParaRegistro() {
  const facultadId = val("reg-facultad");
  const contenedor = document.getElementById("reg-aulas-lista");
  if (!contenedor) return;

  if (!facultadId) {
    contenedor.innerHTML = `<span style="color:#9ca3af; font-size:13px;">Selecciona una facultad primero</span>`;
    return;
  }
  renderCheckboxesAulas(facultadId, contenedor);
}

// ── Aulas para sección 2 (asignar rol) ───────────────────────────────────────

async function cargarAulasParaAsignar() {
  const facultadId = val("asig-facultad");
  const contenedor = document.getElementById("asig-aulas-lista");
  if (!contenedor) return;

  if (!facultadId) {
    contenedor.innerHTML = `<span style="color:#9ca3af; font-size:13px;">Selecciona una facultad primero</span>`;
    return;
  }
  renderCheckboxesAulas(facultadId, contenedor);
}

async function renderCheckboxesAulas(facultadId, contenedor) {
  contenedor.innerHTML = `<span style="color:#9ca3af; font-size:13px;">Cargando aulas...</span>`;
  try {
    const aulas = await aulasApi.listar(Number(facultadId));
    if (!aulas || aulas.length === 0) {
      contenedor.innerHTML = `<span style="color:#9ca3af; font-size:13px;">No hay aulas en esta facultad.</span>`;
      return;
    }
    contenedor.innerHTML = aulas.map(a => `
      <label style="display:inline-flex; align-items:center; gap:6px; padding:6px 10px;
                    background:#f3f4f6; border-radius:8px; font-size:13px; cursor:pointer;">
        <input type="checkbox" class="aula-check" value="${a.id}" style="cursor:pointer;">
        ${escapeHtml(a.nombre)}
      </label>
    `).join("");
  } catch {
    contenedor.innerHTML = `<span style="color:#ef4444; font-size:13px;">Error al cargar aulas.</span>`;
  }
}

function getAulasSeleccionadas(listaId) {
  return Array.from(document.querySelectorAll(`#${listaId} .aula-check:checked`))
              .map(cb => Number(cb.value));
}

// Reutiliza las facultades que ya se cargaron para la sección 1
// Carga las facultades directamente en el select de asignación (independiente de la otra sección).
// SUPER_ADMIN solo ve su propia facultad.
async function cargarFacultadesAsignar() {
  const destino = document.getElementById("asig-facultad");
  if (!destino) return;

  try {
    let lista = (await facultadesApi.listar()) || [];
    if (hasRole("SUPER_ADMIN") && !isAdminCentral()) {
      const misRoles = await rolesApi.misRoles().catch(() => []);
      const rolSA    = (misRoles || []).find(r => r.tipo === "SUPER_ADMIN");
      const miFacId  = rolSA?.facultadId;
      if (miFacId) lista = lista.filter(f => Number(f.id) === Number(miFacId));
    }
    destino.innerHTML = `<option value="">— Selecciona una facultad —</option>`
      + lista.map(f => `<option value="${f.id}">${escapeHtml(f.nombre)}</option>`).join("");
  } catch {
    destino.innerHTML = `<option value="">Error al cargar facultades</option>`;
  }
}
