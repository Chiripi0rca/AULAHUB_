// Log de actividad — cada rol ve solo su scope: ADMIN (sus aulas), SUPER_ADMIN (su facultad), ADMIN_CENTRAL (todo).
import { actividadLogApi } from "../api/gestion.js";
import { getCurrentUser, hasRole, isAdminCentral } from "../api/auth-session.js";
import { escapeHtml } from "../layout.js";
import { soloAdmin } from "../route-guard.js";

soloAdmin();

const PAGE_SIZE = 25;
let paginaActual = 0;
let totalPaginas  = 1;

// Vocabulario de acciones agrupado por entidad (refleja lo que registra el backend).
const ACCIONES_POR_ENTIDAD = {
    aula:         ["CREAR_AULA", "ACTUALIZAR_AULA", "DESACTIVAR_AULA", "ACTIVAR_AULA", "SUBIR_IMAGEN_AULA"],
    reserva:      ["CREAR_RESERVA", "APROBAR_RESERVA", "RECHAZAR_RESERVA", "CANCELAR_RESERVA", "RECHAZO_AUTOMATICO"],
    usuario:      ["CREAR_USUARIO", "ACTUALIZAR_USUARIO", "DESACTIVAR_USUARIO", "ACTIVAR_USUARIO", "ASIGNAR_MATERIAS", "ASIGNAR_GRUPO_MATERIAS"],
    materia:      ["CREAR_MATERIA", "ACTUALIZAR_MATERIA", "DESACTIVAR_MATERIA", "ACTIVAR_MATERIA"],
    grupo:        ["CREAR_GRUPO", "ACTUALIZAR_GRUPO", "DESACTIVAR_GRUPO", "ACTIVAR_GRUPO", "ASIGNAR_MATERIAS_GRUPO"],
    tipo_reserva: ["CREAR_TIPO_RESERVA", "ACTUALIZAR_TIPO_RESERVA", "DESACTIVAR_TIPO_RESERVA", "ACTIVAR_TIPO_RESERVA"],
    facultad:     ["CREAR_FACULTAD", "ACTUALIZAR_FACULTAD", "DESACTIVAR_FACULTAD", "ACTIVAR_FACULTAD"],
    rol:          ["ASIGNAR_ROL", "ELIMINAR_ROL"],
};

// Etiqueta legible de cada entidad para los dropdowns.
const ENTIDAD_LABEL = {
    aula: "Aula", reserva: "Reserva", usuario: "Usuario", materia: "Materia",
    grupo: "Grupo", tipo_reserva: "Tipo de reserva", facultad: "Facultad", rol: "Rol",
};

document.addEventListener("DOMContentLoaded", async () => {
    ajustarTitulo();
    poblarEntidades();
    poblarAcciones("");          // arranca con todas las acciones
    wireFiltros();
    await cargarLogs(0);

    document.getElementById("btn-anterior")
        .addEventListener("click", () => cargarLogs(paginaActual - 1));
    document.getElementById("btn-siguiente")
        .addEventListener("click", () => cargarLogs(paginaActual + 1));
});

// ── Título dinámico según rol ─────────────────────────────────────────────────

function ajustarTitulo() {
    const titulo = document.getElementById("log-titulo");
    if (!titulo) return;
    if (isAdminCentral())            titulo.textContent = "Actividad — Todo el sistema";
    else if (hasRole("SUPER_ADMIN")) titulo.textContent = "Actividad — Mi facultad";
    else                             titulo.textContent = "Actividad — Mis aulas";
}

// ── Filtros ───────────────────────────────────────────────────────────────────

function poblarEntidades() {
    const sel = document.getElementById("f-entidad");
    if (!sel) return;
    Object.keys(ACCIONES_POR_ENTIDAD).forEach(ent => {
        const opt = document.createElement("option");
        opt.value = ent;
        opt.textContent = ENTIDAD_LABEL[ent] || ent;
        sel.appendChild(opt);
    });
}

// Rellena el dropdown de accion. Con una entidad dada solo muestra sus acciones;
// sin entidad muestra todas agrupadas por entidad (optgroup).
function poblarAcciones(entidad) {
    const sel = document.getElementById("f-accion");
    if (!sel) return;
    sel.innerHTML = '<option value="">Todas</option>';
    const entidades = entidad ? [entidad] : Object.keys(ACCIONES_POR_ENTIDAD);
    entidades.forEach(ent => {
        if (!ACCIONES_POR_ENTIDAD[ent]) return;
        const grupo = document.createElement("optgroup");
        grupo.label = ENTIDAD_LABEL[ent] || ent;
        ACCIONES_POR_ENTIDAD[ent].forEach(acc => {
            const opt = document.createElement("option");
            opt.value = acc;
            opt.textContent = acc;
            grupo.appendChild(opt);
        });
        sel.appendChild(grupo);
    });
}

function wireFiltros() {
    const entidadSel = document.getElementById("f-entidad");
    // Al cambiar la entidad, se acotan las acciones disponibles a esa entidad.
    if (entidadSel) entidadSel.addEventListener("change", () => poblarAcciones(entidadSel.value));

    const aplicar = document.getElementById("btn-aplicar");
    if (aplicar) aplicar.addEventListener("click", () => cargarLogs(0));

    const limpiar = document.getElementById("btn-limpiar");
    if (limpiar) limpiar.addEventListener("click", () => {
        ["f-q", "f-entidad", "f-accion", "f-desde", "f-hasta"].forEach(id => {
            const el = document.getElementById(id);
            if (el) el.value = "";
        });
        poblarAcciones("");
        cargarLogs(0);
    });

    const q = document.getElementById("f-q");
    if (q) q.addEventListener("keydown", e => { if (e.key === "Enter") cargarLogs(0); });
}

// Lee los filtros actuales del DOM. Se mantienen al paginar (no se borran al cambiar de pagina).
function getFiltros() {
    return {
        q:       document.getElementById("f-q")?.value.trim() || "",
        accion:  document.getElementById("f-accion")?.value   || "",
        entidad: document.getElementById("f-entidad")?.value  || "",
        desde:   document.getElementById("f-desde")?.value    || "",
        hasta:   document.getElementById("f-hasta")?.value    || "",
    };
}

// ── Carga de logs ─────────────────────────────────────────────────────────────

async function cargarLogs(page) {
    const loading    = document.getElementById("log-loading");
    const vacio      = document.getElementById("log-vacio");
    const container  = document.getElementById("log-container");
    const body       = document.getElementById("log-body");
    const paginacion = document.getElementById("paginacion");

    loading.style.display    = "block";
    loading.textContent      = "Cargando actividad...";
    vacio.style.display      = "none";
    container.style.display  = "none";
    paginacion.style.display = "none";

    try {
        const user = getCurrentUser();
        const filtros = getFiltros();
        let respuesta;

        if (isAdminCentral())            respuesta = await actividadLogApi.adminCentral(page, PAGE_SIZE, filtros);
        else if (hasRole("SUPER_ADMIN")) respuesta = await actividadLogApi.superAdmin(user.id, page, PAGE_SIZE, filtros);
        else                             respuesta = await actividadLogApi.admin(user.id, page, PAGE_SIZE, filtros);

        const logs        = respuesta?.contenido ?? respuesta ?? [];
        totalPaginas      = respuesta?.totalPaginas  ?? 1;
        paginaActual      = respuesta?.paginaActual  ?? 0;
        const totalItems  = respuesta?.totalElementos ?? logs.length;

        loading.style.display = "none";

        if (!logs.length) {
            const hayFiltros = Object.values(filtros).some(v => v);
            vacio.innerHTML = `<span class="material-symbols-outlined empty-icon">event_note</span> ` +
                (hayFiltros ? "No hay actividad que coincida con los filtros."
                            : "Sin actividad registrada aún.");
            vacio.style.display = "block";
            return;
        }

        body.innerHTML = logs.map(l => {
            const fecha = l.createdAt
                ? new Date(l.createdAt).toLocaleString("es-MX", { dateStyle: "short", timeStyle: "short" })
                : "—";
            return `<tr>
                <td>${escapeHtml(l.usuarioNombre) || "—"}</td>
                <td><span class="log-accion">${escapeHtml(l.accion) || "—"}</span></td>
                <td style="max-width:320px; white-space:normal">${escapeHtml(l.detalle) || "—"}</td>
                <td class="log-fecha">${fecha}</td>
            </tr>`;
        }).join("");

        container.style.display = "block";

        // Paginación — visible siempre que haya resultados (antes solo si había >1 página).
        paginacion.style.display = "flex";
        document.getElementById("info-pagina").textContent =
            `Página ${paginaActual + 1} de ${totalPaginas} (${totalItems} registros)`;
        document.getElementById("btn-anterior").disabled = paginaActual === 0;
        document.getElementById("btn-siguiente").disabled = paginaActual >= totalPaginas - 1;

    } catch {
        loading.textContent = "Error al cargar la actividad. Intenta de nuevo.";
        loading.style.display = "block";
    }
}
