import { aulasApi, tiposReservaApi } from "../api/catalogos.js";
import { reservasApi } from "../api/reservas.js";
import { usuariosApi } from "../api/usuarios.js";
import { rolesApi } from "../api/gestion.js";
import { getCurrentUser, isAdminCentral, isAdminUser, isAuthenticated } from "../api/auth-session.js";
import { buildReservaPayload, isoToBackendFecha, normalizeAula, normalizeReserva, normalizeTurno } from "../api/mappers.js";
import { escapeHtml, mostrarToast, pageUrl } from "../layout.js";

// Pantalla de reserva: calendario semanal, seleccion de horarios y aprobacion admin.
const HORAS_MATUTINO = ["07:00 - 08:00", "08:00 - 09:00", "09:00 - 10:00", "10:00 - 11:00", "11:00 - 12:00", "12:00 - 13:00"];
const HORAS_VESPERTINO = ["14:00 - 15:00", "15:00 - 16:00", "16:00 - 17:00", "17:00 - 18:00", "18:00 - 19:00"];

// Placeholder inline (SVG) para aulas sin foto. Es un data-URI: no genera peticion
// ni puede dar 404, por lo que reemplaza al archivo default.jpg que no existia.
const DEFAULT_AULA_IMG = "data:image/svg+xml," + encodeURIComponent(
    "<svg xmlns='http://www.w3.org/2000/svg' width='400' height='300' viewBox='0 0 400 300'>" +
    "<rect width='400' height='300' fill='#eef1f5'/>" +
    "<g fill='#b3bdca'>" +
    "<rect x='130' y='90' width='140' height='95' rx='8'/>" +
    "<circle cx='165' cy='120' r='12' fill='#eef1f5'/>" +
    "<path d='M145 178l38-42 28 30 22-18 22 30z' fill='#eef1f5'/>" +
    "</g>" +
    "<text x='200' y='228' font-family='Arial,sans-serif' font-size='17' fill='#8a94a3' text-anchor='middle'>Aula sin foto</text>" +
    "</svg>"
);

let aulaNombreReal = "";
let aulaCodigo = "";
let aulaId = null;
let aulaActual = null;
let aulaIdParam = null;
let aulaCodigoParam = null;
let isUserAdmin = false;
let currentUserId = null;
let puedeGestionarAula = false; // el admin solo gestiona (acepta/rechaza) en SUS aulas
let adminAulaCodigo = "Todas";
let aulasCache = [];
let tiposReservaCache = [];

const calendarBody = document.getElementById('calendarBody');
const currentMonthElement = document.getElementById('currentMonth');
const selectedDatesElement = document.getElementById('selectedDates');
const prevBtn = document.getElementById('prevBtn');
const nextBtn = document.getElementById('nextBtn');
const selectTurno = document.getElementById('turno');
const aulaDisplay = document.getElementById('aulaDisplay');
const selectMateria = document.getElementById('materia');
const selectGrupo = document.getElementById('grupo');
const selectTipoReserva = document.getElementById('tipoReserva');
const weekContainer = document.getElementById('weekContainer');
const weekRangeText = document.getElementById('weekRangeText');
const weekTable = document.getElementById('weekTable');
const prevWeekButton = document.getElementById('prevWeekButton');
const nextWeekButton = document.getElementById('nextWeekButton');

const currentDate = new Date();
let currentMonth = currentDate.getMonth();
let currentYear = currentDate.getFullYear();
let startDate = null;
let endDate = null;
let currentWeekStart = getStartOfWeek(new Date());
const selectedSlots = new Set();
// Reservas activas agrupadas por celda "fecha|hora" → permite varias solicitudes pendientes por slot.
let reservasPorCelda = {};

document.addEventListener('DOMContentLoaded', async () => {
    const urlParams = new URLSearchParams(window.location.search);
    aulaIdParam = urlParams.get('aulaId');
    aulaCodigoParam = urlParams.get('aula'); // compatibilidad con enlaces antiguos

    crearModalAdmin();
    crearModalCancelar();
    await inicializarDatos();   // carga catálogos y resuelve el aula dinámica
    renderCalendar();
    if (weekContainer) weekContainer.style.display = 'block';
    renderSemana();
});

// Resuelve el aula seleccionada contra el catálogo cargado de la BD,
// usando el id de la URL (o el código por compatibilidad), y pinta nombre e imagen.
function resolverAula() {
    let aula = null;
    if (aulaIdParam) aula = aulasCache.find(a => String(a.id) === String(aulaIdParam));
    if (!aula && aulaCodigoParam) aula = aulasCache.find(a => a.codigo === aulaCodigoParam);

    if (aula) {
        aulaActual = aula;
        aulaId = aula.id;
        aulaCodigo = aula.codigo;
        aulaNombreReal = aula.nombre;
    } else {
        aulaNombreReal = "Aula desconocida";
    }

    if (aulaDisplay) aulaDisplay.value = aulaNombreReal;

    const aulaImg = document.getElementById('aulaImg');
    if (aulaImg) {
        aulaImg.alt = aulaNombreReal;
        // onerror endurecido: se anula a si mismo para no poder reintentar en bucle.
        aulaImg.onerror = () => { aulaImg.onerror = null; aulaImg.src = DEFAULT_AULA_IMG; };
        // Por defecto el placeholder inline (cero peticiones). La foto real ya no
        // viene en el listado; se pide SOLO la de esta aula (1 peticion) mas abajo.
        aulaImg.src = DEFAULT_AULA_IMG;
        if (aula?.id) cargarImagenAula(aula.id, aulaImg);
    }
}

// Trae la imagen base64 unicamente del aula seleccionada (GET /aulas/{id}),
// en vez de descargar las fotos de todas las aulas via el listado.
async function cargarImagenAula(id, imgEl) {
    try {
        const aula = await aulasApi.obtener(id);
        if (aula?.imagenUrl) imgEl.src = aula.imagenUrl;
    } catch {
        // Si falla, se queda el placeholder inline.
    }
}

async function inicializarDatos() {
    if (!isAuthenticated()) {
        if(selectMateria) selectMateria.innerHTML = '<option>Inicia sesion...</option>';
        return;
    }

    isUserAdmin = isAdminUser();
    currentUserId = getCurrentUser()?.id ?? null;
    await cargarCatalogos();
    resolverAula();
    cargarTiposReserva();

    // El admin (y superiores) aparta casos especiales (Mantenimiento/Evento) sin materia/grupo.
    // El profesor solo ve los grupos y materias que TIENE asignados.
    if (isUserAdmin) {
        if(selectMateria) selectMateria.closest('.input-group').style.display = 'none';
        if(selectGrupo) selectGrupo.closest('.input-group').style.display = 'none';
        await calcularPuedeGestionarAula();
    } else {
        await cargarMisAsignaciones();
    }
    aplicarVisibilidadMateria();
}

// Determina si el admin autenticado puede gestionar (aceptar/rechazar) reservas de ESTA aula.
// ADMIN_CENTRAL: siempre. SUPER_ADMIN: si el aula es de su facultad. ADMIN: si tiene el aula asignada.
async function calcularPuedeGestionarAula() {
    if (isAdminCentral()) { puedeGestionarAula = true; return; }
    try {
        const roles = await rolesApi.misRoles().catch(() => []);
        const facultadAula = aulaActual?.raw?.facultadId ?? null;

        // SUPER_ADMIN de la facultad del aula
        const esSuper = (roles || []).some(r =>
            r.tipo === "SUPER_ADMIN" && Number(r.facultadId) === Number(facultadAula));
        if (esSuper) { puedeGestionarAula = true; return; }

        // ADMIN con esta aula asignada
        puedeGestionarAula = (roles || []).some(r =>
            r.tipo === "ADMIN" && (r.aulas || []).some(a => Number(a.id) === Number(aulaId)));
    } catch {
        puedeGestionarAula = false;
    }
}

async function cargarCatalogos() {
    const [aulas, tiposReserva] = await Promise.all([
        aulasApi.listar().catch(() => []),
        // Solo los tipos que el rol del usuario puede usar (ej. Mantenimiento solo para admin+)
        tiposReservaApi.disponibles().catch(() => []),
    ]);
    aulasCache = (aulas || []).map(normalizeAula);
    tiposReservaCache = tiposReserva || [];
}

async function resolverSolicitud(id, status, btn) {
    const originalText = btn.innerText;
    btn.innerText = "...";
    btn.disabled = true;

    try {
        if (status === "Aceptada") await reservasApi.aceptar(id);
        else await reservasApi.rechazar(id, "Solicitud rechazada por el administrador");

        mostrarToast(`Solicitud ${status}.`, 'exito');
        cerrarModalAdmin();
        renderSemana();
    } catch (error) {
        console.error(error);
        mostrarToast("Error al actualizar la solicitud: " + (error.message || ""), 'error');
        btn.innerText = originalText;
        btn.disabled = false;
    }
}

function crearModalAdmin() {
    const div = document.createElement('div');
    div.id = 'adminActionModal';
    div.className = 'admin-modal-overlay';
    div.innerHTML = `
        <div class="admin-card">
            <span class="material-symbols-outlined admin-modal-icon">verified_user</span>
            <h3>Gestionar Solicitudes</h3>
            <p id="admSubtitulo" style="color:#6b7280; font-size:13px; margin:4px 0 12px;"></p>
            <div id="admLista"></div>
            <div class="admin-actions">
                <button class="btn-admin btn-close" id="btnAdminClose">Cerrar</button>
            </div>
        </div>
    `;
    document.body.appendChild(div);
    document.getElementById('btnAdminClose').onclick = cerrarModalAdmin;
}

// Abre el modal con TODAS las solicitudes pendientes que compiten por un mismo slot.
// El admin acepta una (las demás se rechazan en el backend) o rechaza individualmente.
function abrirModalAdmin(lista) {
    const pendientes = (lista || [])
        .filter(r => r.status === "Pendiente")
        // Las de tipo con mayor prioridad se muestran primero; a igualdad, la más antigua.
        .sort((a, b) => (b.tipoPrioridad || 0) - (a.tipoPrioridad || 0)
            || String(a.createdAt || a.id).localeCompare(String(b.createdAt || b.id)));
    if (!pendientes.length) return;

    const modal = document.getElementById('adminActionModal');
    document.getElementById('admSubtitulo').textContent = pendientes.length > 1
        ? `${pendientes.length} solicitudes compiten por este horario. Al aceptar una, las demás se rechazan.`
        : "1 solicitud pendiente para este horario.";

    // Si el admin NO gestiona esta aula, solo puede VER las solicitudes (sin botones).
    const aviso = puedeGestionarAula ? "" : `
        <p style="grid-column:1/-1; background:#fffbeb; color:#92400e; border:1px solid #fde68a;
           border-radius:8px; padding:10px 12px; font-size:13px; margin:0 0 4px;">
          Solo lectura: esta aula no está bajo tu gestión, no puedes aceptar ni rechazar aquí.
        </p>`;

    const acciones = (r) => puedeGestionarAula ? `
            <div class="admin-actions">
                <button class="btn-admin btn-reject" data-accion="Rechazada" data-id="${r.id}">Rechazar</button>
                <button class="btn-admin btn-approve" data-accion="Aceptada" data-id="${r.id}">Aceptar</button>
            </div>` : "";

    const cont = document.getElementById('admLista');
    cont.innerHTML = aviso + pendientes.map(r => `
        <div class="sol-mini">
            <div class="sol-mini-top">
                <span class="sol-mini-prof">${escapeHtml(r.profesorName || "—")}</span>
                <span class="sol-mini-tipo">${escapeHtml(r.tipoNombre || "—")}</span>
            </div>
            <div class="sol-mini-datos">
                <span><span class="material-symbols-outlined">book</span>${escapeHtml(r.materia || "Sin materia")}</span>
                <span><span class="material-symbols-outlined">groups</span>Grupo ${escapeHtml(r.grupo || "—")}</span>
                <span><span class="material-symbols-outlined">calendar_today</span>${isoToBackendFecha(r.fecha) || "—"}</span>
                <span><span class="material-symbols-outlined">schedule</span>${r.horaInicio || ""} - ${r.horaFin || ""}</span>
            </div>
            ${acciones(r)}
        </div>
    `).join("");

    cont.querySelectorAll('button[data-accion]').forEach(btn => {
        btn.addEventListener('click', () => {
            // Rechazar pide motivo (modal); aceptar es directo.
            if (btn.dataset.accion === "Rechazada") abrirModalMotivo(btn.dataset.id);
            else resolverSolicitud(btn.dataset.id, btn.dataset.accion, btn);
        });
    });

    modal.classList.add('active');
}

// ── Modal de motivo de rechazo (desde el calendario) ───────────────────────────

let _rechazoPendienteId = null;

function crearModalMotivo() {
    const div = document.createElement('div');
    div.id = 'motivoModal';
    div.className = 'admin-modal-overlay';
    div.style.zIndex = '7000'; // por encima del modal admin (z-index 6000)
    div.innerHTML = `
        <div class="admin-card" style="max-width:440px;">
            <span class="material-symbols-outlined admin-modal-icon">edit_note</span>
            <h3>Motivo del rechazo</h3>
            <p style="color:#6b7280; font-size:13px; margin:4px 0 12px;">
                Explica por qué se rechaza. El profesor recibirá esta razón por correo.
            </p>
            <textarea id="motivoTexto" rows="3" placeholder="Ej: El aula tiene mantenimiento ese día..."
                style="width:100%; box-sizing:border-box; padding:10px 12px; border:1px solid #d1d5db; border-radius:8px; font-family:inherit; font-size:14px; resize:vertical;"></textarea>
            <div class="admin-actions" style="margin-top:12px;">
                <button class="btn-admin btn-close" id="motivoCancelar">Cancelar</button>
                <button class="btn-admin btn-reject" id="motivoConfirmar">Rechazar</button>
            </div>
        </div>`;
    document.body.appendChild(div);
    document.getElementById('motivoCancelar').onclick = () => div.classList.remove('active');
    return div;
}

function abrirModalMotivo(id) {
    const modal = document.getElementById('motivoModal') || crearModalMotivo();
    _rechazoPendienteId = id;
    const txt = document.getElementById('motivoTexto');
    txt.value = '';
    txt.style.borderColor = '#d1d5db';
    modal.classList.add('active');
    txt.focus();

    document.getElementById('motivoConfirmar').onclick = async () => {
        const motivo = txt.value.trim();
        if (!motivo) { txt.style.borderColor = '#ef4444'; txt.focus(); return; }
        modal.classList.remove('active');
        try {
            await reservasApi.rechazar(_rechazoPendienteId, motivo);
            mostrarToast('Solicitud rechazada.', 'exito');
            cerrarModalAdmin();
            renderSemana();
        } catch (error) {
            mostrarToast('Error al rechazar: ' + (error.message || ''), 'error');
        }
    };
}

// Modal para cancelar/eliminar una reserva (admin sobre cualquiera; profesor sobre la suya).
function crearModalCancelar() {
    const div = document.createElement('div');
    div.id = 'cancelarReservaModal';
    div.className = 'admin-modal-overlay';
    div.innerHTML = `
        <div class="admin-card">
            <span class="material-symbols-outlined admin-modal-icon">event_busy</span>
            <h3>Detalle de la reserva</h3>
            <div class="admin-info">
                <p><strong>Profesor:</strong> <span id="canProf"></span></p>
                <p><strong>Materia:</strong> <span id="canMateria"></span></p>
                <p><strong>Grupo:</strong> <span id="canGrupo"></span></p>
                <p><strong>Tipo:</strong> <span id="canTipo"></span></p>
                <p><strong>Fecha apartada:</strong> <span id="canFecha"></span></p>
                <p><strong>Hora apartada:</strong> <span id="canHora"></span></p>
            </div>
            <div class="admin-actions">
                <button class="btn-admin btn-close" id="btnCancelarClose">Cerrar</button>
                <button class="btn-admin btn-reject" id="btnEliminarReserva">Eliminar reserva</button>
            </div>
        </div>
    `;
    document.body.appendChild(div);

    document.getElementById('btnCancelarClose').onclick = () => div.classList.remove('active');
    document.getElementById('btnEliminarReserva').onclick = async () => {
        const id = div.dataset.currentId;
        if (!confirm("¿Desea cancelar esta reserva?")) return;

        const btn = document.getElementById('btnEliminarReserva');
        const original = btn.innerText;
        btn.innerText = "...";
        btn.disabled = true;
        try {
            await reservasApi.cancelar(id);
            mostrarToast("Reserva cancelada. El espacio quedó libre.", 'exito');
            div.classList.remove('active');
            renderSemana();
        } catch (error) {
            console.error(error);
            mostrarToast("No se pudo cancelar la reserva: " + error.message, 'error');
        } finally {
            btn.innerText = original;
            btn.disabled = false;
        }
    };
}

function abrirModalCancelar(id, prof, mat, grupo, fecha, hora, tipo) {
    const modal = document.getElementById('cancelarReservaModal');
    modal.dataset.currentId = id;
    document.getElementById('canProf').textContent = prof || "—";
    document.getElementById('canMateria').textContent = mat || "—";
    document.getElementById('canGrupo').textContent = grupo || "—";
    document.getElementById('canTipo').textContent = tipo || "—";
    document.getElementById('canFecha').textContent = fecha || "—";
    document.getElementById('canHora').textContent = hora || "—";
    modal.classList.add('active');
}

function cerrarModalAdmin() {
    document.getElementById('adminActionModal').classList.remove('active');
}

// Colores por tipo de reserva para el badge y el borde de la tarjeta.
// Los tipos vienen de la BD, asi que para los conocidos usamos un color fijo
// y para cualquier otro generamos uno consistente a partir del nombre.
const COLORES_TIPO = {
    "clase": "#2563eb",
    "evento": "#7c3aed",
    "mantenimiento": "#d97706",
    "examen": "#0891b2",
    "reunion": "#4f46e5",
    "reunión": "#4f46e5",
};
function colorTipo(nombre) {
    const key = String(nombre || "").trim().toLowerCase();
    if (COLORES_TIPO[key]) return COLORES_TIPO[key];
    let hash = 0;
    for (let i = 0; i < key.length; i++) hash = key.charCodeAt(i) + ((hash << 5) - hash);
    // HSL con luminosidad fija para garantizar contraste con el texto blanco del badge.
    return `hsl(${Math.abs(hash) % 360}, 62%, 42%)`;
}

// Arma el contenido (tarjeta flotante + badge de tipo + iconos) de una celda ACEPTADA.
// Reserva de profesor (tiene materia y grupo): nombre, materia y grupo.
// Apartado de admin (evento/mantenimiento, sin materia/grupo): solo nombre.
function contenidoCelda(ref) {
    const nombre = escapeHtml(ref.profesorName || "Reservado");
    const tipo = escapeHtml(ref.tipoNombre || "Reserva");
    const color = colorTipo(ref.tipoNombre);
    const esClase = ref.materia && ref.grupo;

    const lineasClase = esClase ? `
        <span class="celda-row celda-materia"><span class="material-symbols-outlined">book</span>${escapeHtml(ref.materia)}</span>
        <span class="celda-row celda-meta"><span class="material-symbols-outlined">groups</span>Grupo ${escapeHtml(ref.grupo)}</span>` : "";

    return `<div class="celda-card" style="border-top-color:${color}">
        <span class="celda-badge" style="background:${color}">${tipo}</span>
        <span class="celda-row celda-nombre"><span class="material-symbols-outlined">person</span>${nombre}</span>
        ${lineasClase}
    </div>`;
}

// Contenido de una celda PENDIENTE (solo la ve el admin).
// No muestra nombres porque un mismo slot puede tener muchas solicitudes compitiendo;
// solo indica cuantas hay. El admin abre el modal para verlas y gestionarlas todas.
function contenidoPendiente(n) {
    const texto = n === 1 ? "1 solicitud" : `${n} solicitudes`;
    return `<div class="celda-card celda-pendiente">
        <span class="celda-badge celda-badge-pendiente">Pendiente</span>
        <span class="celda-row celda-nombre"><span class="material-symbols-outlined">pending_actions</span>${texto}</span>
    </div>`;
}

async function cargarHorariosOcupados(fechaInicio, fechaFin) {
    if (!aulaNombreReal || !aulaId) return;
    // El endpoint /calendario ya filtra por aula, rango de fechas y estados activos
    // (PENDIENTE/ACEPTADA) en el servidor. desde/hasta van en dd-MM-yyyy.
    const desde = isoToBackendFecha(fechaToISO(fechaInicio));
    const hasta = isoToBackendFecha(fechaToISO(fechaFin));

    try {
        // Un profesor solo ve como bloqueadas las ACEPTADAS; el admin también ve las PENDIENTES.
        const estados = isUserAdmin ? ["Aceptada", "Pendiente"] : ["Aceptada"];
        const reservas = (await reservasApi.calendario(aulaId, desde, hasta))
            .map(normalizeReserva)
            .filter((reserva) => estados.includes(reserva.status));

        // Limpia celdas y reagrupa.
        reservasPorCelda = {};
        document.querySelectorAll('.time-slot-cell').forEach(cell => {
            cell.classList.remove('busy', 'pending-slot');
            cell.dataset.reservaId = "";
            cell.title = "";
            cell.innerHTML = ""; // quita la info de una reserva anterior
        });

        // Agrupa por celda: una celda puede tener 1 aceptada y/o varias pendientes que compiten.
        reservas.forEach(data => {
            const fecha = data.fecha;
            let horaSolo = data.horario || "";
            if (horaSolo.includes(fecha)) horaSolo = horaSolo.replace(fecha + " ", "").trim();
            if (!horaSolo && data.horaInicio) horaSolo = `${data.horaInicio} - ${data.horaFin}`;
            const key = `${fecha}|${horaSolo}`;
            (reservasPorCelda[key] ||= []).push(data);
            selectedSlots.delete(key);
        });

        // Pinta cada celda según su estado agregado.
        Object.entries(reservasPorCelda).forEach(([key, lista]) => {
            const [fecha, horaSolo] = key.split('|');
            const cell = document.querySelector(`.time-slot-cell[data-fecha="${fecha}"][data-hora="${horaSolo}"]`);
            if (!cell) return;
            cell.classList.remove('selected');

            const aceptada = lista.find(r => r.status === "Aceptada");
            const pendientes = lista.filter(r => r.status === "Pendiente");
            const ref = aceptada || pendientes[0];

            cell.dataset.reservaId = ref.id;
            cell.dataset.solicitanteId = ref.ProfesorUID ?? "";
            cell.dataset.profesor = ref.profesorName || "Profesor";
            cell.dataset.materia = ref.materia || "Sin materia";
            cell.dataset.grupo = ref.grupo || "";
            cell.dataset.tipo = ref.tipoNombre || "";
            // Fecha (dd-MM-yyyy) y hora por separado para mostrarlas en el modal de detalle.
            cell.dataset.fechaApartada = isoToBackendFecha(ref.fecha) || "";
            cell.dataset.horaApartada = `${ref.horaInicio || ""} - ${ref.horaFin || ""}`;

            if (aceptada) {
                cell.classList.add('busy');
                cell.title = `Ocupado por: ${aceptada.profesorName}`;
                cell.innerHTML = contenidoCelda(aceptada);
            } else if (pendientes.length) {
                // Un slot puede tener muchas solicitudes compitiendo: solo mostramos el conteo.
                cell.classList.add('pending-slot');
                cell.title = pendientes.length > 1
                    ? `${pendientes.length} solicitudes pendientes`
                    : `Solicitud pendiente: ${pendientes[0].profesorName}`;
                cell.innerHTML = contenidoPendiente(pendientes.length);
            }
        });
    } catch (error) {
        console.error("Error cargando horarios:", error);
    }
}

function handleCellClick(td, key) {
    if (td.classList.contains('past')) { mostrarToast("Esta fecha u hora ya pasó.", 'aviso'); return; }

    const ocupada = td.classList.contains('busy');
    const pendiente = td.classList.contains('pending-slot');

    if (ocupada || pendiente) {
        const esPropia = currentUserId != null && String(td.dataset.solicitanteId) === String(currentUserId);

        // El admin gestiona TODAS las solicitudes pendientes que compiten por este slot.
        if (pendiente && isUserAdmin) {
            abrirModalAdmin(reservasPorCelda[key] || []);
            return;
        }
        // Admin (cualquier reserva) o el dueño (su propia reserva) → puede cancelarla/eliminarla.
        if (isUserAdmin || esPropia) {
            abrirModalCancelar(td.dataset.reservaId, td.dataset.profesor, td.dataset.materia, td.dataset.grupo, td.dataset.fechaApartada, td.dataset.horaApartada, td.dataset.tipo);
            return;
        }
        mostrarToast(ocupada
            ? `Horario ocupado por: ${td.dataset.profesor}`
            : "Este horario tiene una solicitud pendiente.", 'aviso');
        return;
    }

    // Slot libre → selección para reservar (profesores y admins).
    if (selectedSlots.has(key)) { selectedSlots.delete(key); td.classList.remove('selected'); }
    else { selectedSlots.add(key); td.classList.add('selected'); }
    updateSelectedDatesFromSlots();
}

// Asignaciones del profe logueado: [{grupoId, grupoNombre, materiaId, materiaNombre}].
// Es la fuente de verdad: el profe solo puede reservar en los grupos/materias que imparte.
let misAsignaciones = [];

async function cargarMisAsignaciones() {
    try {
        misAsignaciones = (await usuariosApi.misAsignaciones()) || [];
    } catch {
        misAsignaciones = [];
    }
    poblarGruposAsignados();
}

// Llena el dropdown de grupo con los grupos donde el profe tiene asignaciones (sin repetir).
function poblarGruposAsignados() {
    if (!selectGrupo) return;

    // Grupos únicos por id, conservando nombre y turno (el turno define el horario del grupo).
    const gruposUnicos = new Map();
    misAsignaciones.forEach(a => {
        if (!gruposUnicos.has(a.grupoId)) {
            gruposUnicos.set(a.grupoId, { nombre: a.grupoNombre, turno: a.grupoTurno ?? a.turno ?? null });
        }
    });

    if (gruposUnicos.size === 0) {
        selectGrupo.innerHTML = '<option value="">No tienes materias asignadas</option>';
        if (selectMateria) selectMateria.innerHTML = '<option value="">—</option>';
        return;
    }

    selectGrupo.innerHTML = '<option value="">Selecciona un grupo</option>';
    gruposUnicos.forEach(({ nombre, turno }, id) => {
        const option = document.createElement('option');
        option.value        = nombre;   // el backend guarda el NOMBRE del grupo en la reserva
        option.dataset.grupoId = id;    // para filtrar las materias de ESE grupo
        // Turno del grupo: al elegirlo se autorrellena y bloquea el dropdown de turno.
        if (turno) option.dataset.turno = turno;
        option.textContent  = nombre;
        selectGrupo.appendChild(option);
    });

    if (selectMateria) selectMateria.innerHTML = '<option value="">Selecciona un grupo primero</option>';

    selectGrupo.removeEventListener('change', _onGrupoAsignadoChange);
    selectGrupo.addEventListener('change', _onGrupoAsignadoChange);
}

function _onGrupoAsignadoChange() {
    const grupoId = Number(selectGrupo.selectedOptions[0]?.dataset.grupoId) || null;
    poblarMateriasAsignadas(grupoId);
    aplicarTurnoDelGrupo(selectGrupo.selectedOptions[0]?.dataset.turno);
}

// Autorrellena y bloquea el turno según el grupo elegido: un grupo vespertino solo puede
// apartar horario vespertino. Si el grupo no tiene turno definido, deja el dropdown libre.
function aplicarTurnoDelGrupo(turnoRaw) {
    if (!selectTurno) return;
    if (!turnoRaw) {
        // Grupo sin turno (o ninguno seleccionado): se reabre el dropdown.
        selectTurno.disabled = false;
        selectTurno.removeAttribute('title');
        return;
    }
    const turno = normalizeTurno(turnoRaw); // "Matutino" | "Vespertino"
    selectTurno.disabled = false; // habilitar temporalmente para poder fijar el valor
    if (selectTurno.value !== turno) {
        selectTurno.value = turno;
        // Dispara el re-render del calendario con las horas del turno correcto.
        selectTurno.dispatchEvent(new Event('change'));
    }
    selectTurno.disabled = true; // bloqueado: el turno lo manda el grupo
    selectTurno.title = `El grupo es de turno ${turno.toLowerCase()}.`;
}

// Llena el dropdown de materia SOLO con las materias que el profe imparte en ese grupo.
function poblarMateriasAsignadas(grupoId) {
    if (!selectMateria) return;
    if (!grupoId) {
        selectMateria.innerHTML = '<option value="">Selecciona un grupo primero</option>';
        return;
    }
    const materias = misAsignaciones.filter(a => a.grupoId === grupoId);
    if (!materias.length) {
        selectMateria.innerHTML = '<option value="">Sin materias en este grupo</option>';
        return;
    }

    // Si el profe solo imparte UNA materia en este grupo, se selecciona sola (sin placeholder).
    // Si imparte varias, se muestra el placeholder para que elija cuál.
    const unaSola = materias.length === 1;
    selectMateria.innerHTML = unaSola ? '' : '<option value="">Selecciona una materia</option>';
    materias.forEach(a => {
        const option = document.createElement('option');
        option.value = a.materiaId;
        option.dataset.nombre = a.materiaNombre;
        option.textContent = a.materiaNombre;
        selectMateria.appendChild(option);
    });
}

// Llena el dropdown de tipo de reserva con los tipos activos, dejando "Clase" por defecto.
// Los admins (y superiores) apartan solo casos especiales, por eso NO ven "Clase".
function cargarTiposReserva() {
    if (!selectTipoReserva) return;

    // Solo los tipos de reserva de la facultad a la que pertenece el aula que se reserva.
    const facultadIdAula = aulaActual?.raw?.facultadId ?? null;

    const disponibles = (tiposReservaCache || [])
        .filter((tipo) => tipo.activo !== false)
        .filter((tipo) => !(isUserAdmin && tipo.clave === "CLASE"))
        .filter((tipo) => facultadIdAula == null || Number(tipo.facultadId) === Number(facultadIdAula));
    if (!disponibles.length) {
        selectTipoReserva.innerHTML = '<option value="">Sin tipos de reserva</option>';
        return;
    }

    selectTipoReserva.innerHTML = disponibles
        .map((tipo) => `<option value="${tipo.id}" data-clave="${escapeHtml(tipo.clave || "")}">${escapeHtml(tipo.nombre)}</option>`)
        .join("");

    // Default "Clase" (clave CLASE) para profesores; si no existe (o es admin), queda el primero.
    const clase = disponibles.find((tipo) => tipo.clave === "CLASE");
    if (clase) selectTipoReserva.value = String(clase.id);
}

// Tipos que NO requieren materia ni grupo (reservas no académicas).
const TIPOS_SIN_MATERIA = ["EVENTO", "MANTENIMIENTO"];

function claveTipoActual() {
    return selectTipoReserva?.selectedOptions[0]?.dataset.clave || "";
}

function tipoActualNecesitaMateria() {
    return !TIPOS_SIN_MATERIA.includes(claveTipoActual());
}

// Muestra u oculta materia/grupo según el tipo elegido.
// Para admins materia/grupo siempre van ocultas (apartan solo casos especiales).
function aplicarVisibilidadMateria() {
    if (isUserAdmin) return;
    const necesita = tipoActualNecesitaMateria();
    [selectMateria, selectGrupo].forEach((sel) => {
        const grupo = sel?.closest('.input-group');
        if (grupo) grupo.style.display = necesita ? '' : 'none';
    });
    // Si el grupo se oculta (tipo sin materia: evento/mantenimiento) ya no hay
    // restricción de turno → se reabre. Si vuelve a mostrarse, se reaplica el del grupo.
    aplicarTurnoDelGrupo(necesita ? selectGrupo?.selectedOptions[0]?.dataset.turno : null);
}

function renderCalendar() {
    if (!calendarBody || !currentMonthElement) return;
    const jsFirstDay = new Date(currentYear, currentMonth, 1).getDay();
    const firstDayIndex = (jsFirstDay - 1 + 7) % 7;
    const daysInMonth = new Date(currentYear, currentMonth + 1, 0).getDate();
    currentMonthElement.textContent = new Date(currentYear, currentMonth, 1).toLocaleDateString('es-MX', {month: 'long', year: 'numeric'});
    let days = '';
    for (let i = 0; i < firstDayIndex; i++) days += `<div class="calendar-day empty"></div>`;
    for (let i = 1; i <= daysInMonth; i++) {
        const date = new Date(currentYear, currentMonth, i);
        days += `<div class="calendar-day ${getDayClassName(date)}" onclick="selectDate(${i})">${i}</div>`;
    }
    calendarBody.innerHTML = days;
}

function getDayClassName(date) {
    const today = new Date(); today.setHours(0, 0, 0, 0);
    if (date < today) return 'past';
    if (startDate && date.toDateString() === startDate.toDateString()) return 'selected';
    if (endDate && date.toDateString() === endDate.toDateString()) return 'selected';
    if (startDate && endDate && date > startDate && date < endDate) return 'range';
    return '';
}

function getStartOfWeek(date) {
    const d = new Date(date);
    const day = d.getDay();
    const diff = (day === 0 ? -6 : 1 - day);
    d.setDate(d.getDate() + diff);
    d.setHours(0, 0, 0, 0);
    return d;
}

function fechaToISO(date) {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2,'0');
    const d = String(date.getDate()).padStart(2,'0');
    return `${y}-${m}-${d}`;
}

function getHorasTurnoActual() {
    const turno = selectTurno ? selectTurno.value : "Matutino";
    return turno === "Matutino" ? HORAS_MATUTINO : HORAS_VESPERTINO;
}

function renderSemana() {
    if (!weekTable || !weekRangeText) return;
    const horas = getHorasTurnoActual();
    const start = new Date(currentWeekStart);
    const end = new Date(start);
    end.setDate(end.getDate() + 6);

    weekRangeText.textContent = `${start.toLocaleDateString('es-ES', {day:'2-digit', month:'short'})} - ${end.toLocaleDateString('es-ES', {day:'2-digit', month:'short'})}`;

    weekTable.innerHTML = '';
    const diasLabels = ['Lun', 'Mar', 'Mie', 'Jue', 'Vie'];
    const thead = document.createElement('thead');
    const headerRow = document.createElement('tr');
    headerRow.appendChild(document.createElement('th'));
    diasLabels.forEach((label, idx) => {
        const th = document.createElement('th');
        const fechaCol = new Date(start);
        fechaCol.setDate(start.getDate() + idx);
        th.textContent = `${label} ${fechaCol.getDate()}`;
        headerRow.appendChild(th);
    });
    thead.appendChild(headerRow);
    weekTable.appendChild(thead);

    const tbody = document.createElement('tbody');
    horas.forEach(hora => {
        const tr = document.createElement('tr');
        const thHora = document.createElement('th');
        thHora.textContent = hora;
        tr.appendChild(thHora);
        diasLabels.forEach((_, idx) => {
            const td = document.createElement('td');
            td.classList.add('time-slot-cell');
            const fechaCelda = new Date(start);
            fechaCelda.setDate(start.getDate() + idx);
            const fechaStr = fechaToISO(fechaCelda);
            td.dataset.fecha = fechaStr;
            td.dataset.hora = hora;
            const key = `${fechaStr}|${hora}`;

            const todayMidnight = new Date();
            todayMidnight.setHours(0, 0, 0, 0);
            const isDatePast = fechaCelda < todayMidnight;
            const isTodayDate = fechaToISO(fechaCelda) === fechaToISO(new Date());
            const slotStartHour = parseInt(hora.split(' - ')[0].split(':')[0], 10);
            const isHourPast = isTodayDate && new Date().getHours() >= slotStartHour;

            if (isDatePast || isHourPast) td.classList.add('past');
            else if (selectedSlots.has(key)) td.classList.add('selected');

            td.addEventListener('click', () => handleCellClick(td, key));
            tr.appendChild(td);
        });
        tbody.appendChild(tr);
    });
    weekTable.appendChild(tbody);
    cargarHorariosOcupados(start, end);
}

function updateSelectedDatesFromSlots() {
    if (!selectedDatesElement) return;
    const slots = Array.from(selectedSlots).map(k => { const [f, h] = k.split('|'); return {fecha: f, hora: h}; });
    if (slots.length === 0) {
        selectedDatesElement.textContent = `Selecciona una fecha`;
        startDate = null;
        endDate = null;
        renderCalendar();
        return;
    }
    const fechasUnicas = [...new Set(slots.map(s => s.fecha))].sort();
    const parse = (s) => { const [y,m,d]=s.split('-').map(Number); return new Date(y,m-1,d); };
    startDate = parse(fechasUnicas[0]);
    endDate = fechasUnicas.length > 1 ? parse(fechasUnicas[fechasUnicas.length - 1]) : null;
    const fmt = (d) => `${String(d.getDate()).padStart(2,'0')}-${String(d.getMonth()+1).padStart(2,'0')}-${d.getFullYear()}`;
    selectedDatesElement.textContent = !endDate ? `Fecha: ${fmt(startDate)}` : `Fechas: ${fmt(startDate)} al ${fmt(endDate)}`;
    renderCalendar();
}

window.selectDate = function(day) {
    const clickedDate = new Date(currentYear, currentMonth, day);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    if (clickedDate < today) {
        mostrarToast("Esta fecha ya pasó.", 'aviso');
        return;
    }
    currentWeekStart = getStartOfWeek(clickedDate);
    renderCalendar();
    renderSemana();
};

prevBtn?.addEventListener('click', () => { currentMonth--; if (currentMonth < 0) { currentMonth = 11; currentYear--; } renderCalendar(); });
nextBtn?.addEventListener('click', () => { currentMonth++; if (currentMonth > 11) { currentMonth = 0; currentYear++; } renderCalendar(); });
prevWeekButton?.addEventListener('click', () => { currentWeekStart.setDate(currentWeekStart.getDate() - 7); renderSemana(); });
nextWeekButton?.addEventListener('click', () => { currentWeekStart.setDate(currentWeekStart.getDate() + 7); renderSemana(); });
selectTurno?.addEventListener('change', () => { selectedSlots.clear(); updateSelectedDatesFromSlots(); renderSemana(); });
selectTipoReserva?.addEventListener('change', aplicarVisibilidadMateria);

const btnPreReservar = document.getElementById('btnPreReservar');
const modal = document.getElementById('modalConfirmacion');
const btnCancelar = document.getElementById('btnCancelar');
const btnEnviar = document.getElementById('btnEnviarReserva');

btnPreReservar?.addEventListener('click', () => {
    const turno = selectTurno.value;
    const materia = selectMateria.selectedOptions[0]?.dataset.nombre || selectMateria.selectedOptions[0]?.textContent || "";
    const grupo = selectGrupo.value;
    const slots = Array.from(selectedSlots).map(k => { const [f, h] = k.split('|'); return {fecha: f, hora: h}; }).sort((a,b) => a.fecha.localeCompare(b.fecha) || a.hora.localeCompare(b.hora));
    if (!aulaNombreReal) { mostrarToast("Error: aula no detectada.", 'error'); return; }
    // Materia y grupo solo son obligatorios para tipos académicos (Clase, Examen, Tutoría) y no para admins.
    const requiereMateria = !isUserAdmin && tipoActualNecesitaMateria();
    if (requiereMateria && (!materia || !grupo)) { mostrarToast("Selecciona materia y grupo antes de continuar.", 'aviso'); return; }
    if (slots.length === 0) { mostrarToast("Selecciona al menos un horario en la tabla.", 'aviso'); return; }

    // Materia y grupo solo se muestran si aplican (tipo académico y no admin).
    const filaMateria = document.getElementById('confMateriaRow');
    const filaGrupo = document.getElementById('confGrupoRow');
    if (filaMateria) filaMateria.style.display = requiereMateria ? '' : 'none';
    if (filaGrupo) filaGrupo.style.display = requiereMateria ? '' : 'none';

    document.getElementById('confAula').innerText = aulaNombreReal;
    document.getElementById('confMateria').innerText = materia || "—";
    document.getElementById('confGrupo').innerText = grupo || "—";
    document.getElementById('confTipo').innerText = selectTipoReserva?.selectedOptions[0]?.textContent || "Clase";
    document.getElementById('confTurno').innerText = turno;
    document.getElementById('confHoras').innerText = [...new Set(slots.map(s => s.hora))].join(", ");
    // Se muestran en formato mexicano dd-MM-yyyy aunque internamente se manejen en ISO.
    document.getElementById('confFechas').innerText = [...new Set(slots.map(s => s.fecha))].sort().map(isoToBackendFecha).join(", ");
    modal?.classList.add('active');
});

btnCancelar?.addEventListener('click', () => modal?.classList.remove('active'));

btnEnviar?.addEventListener('click', async () => {
    if (!isAuthenticated()) { mostrarToast("Inicia sesión para continuar.", 'aviso'); return; }
    btnEnviar.innerText = "Enviando...";
    btnEnviar.disabled = true;
    try {
        const user = getCurrentUser();
        const turno = selectTurno.value;
        const materiaOption = selectMateria.selectedOptions[0];
        const materia = materiaOption?.dataset.nombre || materiaOption?.textContent || selectMateria.value;
        const materiaId = Number(selectMateria.value) || null;
        const grupo = selectGrupo.value;
        const slots = Array.from(selectedSlots).map(k => { const [f, h] = k.split('|'); return {fecha: f, hora: h}; });
        const aula = aulaActual || { id: aulaId, codigo: aulaCodigo };
        // Tipo de reserva elegido en el dropdown; si por algo no hay valor, cae a CLASE.
        const tipoReservaId = Number(selectTipoReserva?.value)
            || (tiposReservaCache.find((tipo) => tipo.clave === "CLASE") || tiposReservaCache[0] || {}).id
            || 1;

        await Promise.all(slots.map(({fecha, hora}) => reservasApi.crear(buildReservaPayload({
            user,
            aula,
            tipoReservaId,
            materiaId,
            materia,
            grupo,
            turno,
            fecha,
            hora,
        }))));
        const cuantas = slots.length;
        const msg = isUserAdmin
            ? `¡Reserva${cuantas > 1 ? "s" : ""} confirmada${cuantas > 1 ? "s" : ""}! Quedó registrada en el calendario. ✅`
            : `¡Reserva${cuantas > 1 ? "s" : ""} enviada${cuantas > 1 ? "s" : ""}! Espera la aprobación del administrador. ✅`;
        mostrarToast(msg, 'exito');
        // Pequeña pausa para que el mensaje sea visible antes de salir de la página
        setTimeout(() => { window.location.href = pageUrl("aulas.html"); }, 1600);
    } catch (error) {
        console.error(error);
        mostrarToast("Error al guardar las reservas: " + (error.message || "intenta de nuevo."), 'error');
        btnEnviar.innerText = "Confirmar";
        btnEnviar.disabled = false;
    }
});

function urlCodigoToAdminCodigo(urlCodigo) {
    if (urlCodigo === "labA" || urlCodigo === "labB") return "AB";
    if (urlCodigo === "centro") return "C";
    if (urlCodigo === "auditorio") return "Auditorio";
    if (urlCodigo === "labCD") return "CD";
    if (urlCodigo === "labP") return "Posgrado";
    return "";
}

