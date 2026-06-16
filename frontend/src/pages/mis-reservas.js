import { reservasApi } from "../api/reservas.js";
import { aulasApi, facultadesApi } from "../api/catalogos.js";
import { escapeHtml, mostrarToast } from "../layout.js";
import { getCurrentUser, hasRole, isAdminCentral, isAdminUser, isAuthenticated } from "../api/auth-session.js";
import { normalizeReserva } from "../api/mappers.js";

// Historial de reservas con filtros locales y exportacion a PDF.
let allReservas = [];
let filteredReservas = [];
let isAdmin = false;
let esCentral = false;   // ADMIN_CENTRAL: filtra por facultad + aula
let esSuper = false;     // SUPER_ADMIN: filtra por aula

// Mapas para enriquecer cada reserva (el backend solo devuelve aulaId).
const aulaPorId = new Map();      // aulaId -> { facultadId, codigo, nombre }
const facultadPorId = new Map();  // facultadId -> nombre

document.addEventListener("DOMContentLoaded", async () => {
    inicializarSemestres();
    configurarEventos();

    if (!isAuthenticated()) return;
    await detectarRolYCargar();
    aplicarFiltros();
});

function inicializarSemestres() {
    const sel = document.getElementById('semestre-val');
    if (!sel) return;
    const year = new Date().getFullYear();
    for (let y = year - 1; y <= year + 1; y++) {
        const optA = document.createElement('option');
        optA.value = `${y}-A`;
        optA.textContent = `Ene - Jun ${y}`;
        sel.appendChild(optA);

        const optB = document.createElement('option');
        optB.value = `${y}-B`;
        optB.textContent = `Jul - Dic ${y}`;
        sel.appendChild(optB);
    }
    const semActual = new Date().getMonth() < 6 ? 'A' : 'B';
    sel.value = `${year}-${semActual}`;
}

function configurarEventos() {
    const periodo = document.getElementById('filtro-periodo');
    const inputsCondicionales = ['input-dia', 'input-semana', 'input-mes', 'input-semestre', 'input-rango'];
    const mapaInputs = {
        dia: 'input-dia',
        semana: 'input-semana',
        mes: 'input-mes',
        semestre: 'input-semestre',
        rango: 'input-rango'
    };

    periodo?.addEventListener('change', () => {
        inputsCondicionales.forEach(id => {
            const input = document.getElementById(id);
            if (input) input.style.display = 'none';
        });
        if (mapaInputs[periodo.value]) {
            document.getElementById(mapaInputs[periodo.value]).style.display = 'flex';
        }
    });

    document.getElementById('btn-aplicar')?.addEventListener('click', aplicarFiltros);
    document.getElementById('btn-pdf')?.addEventListener('click', exportarPDF);
    document.getElementById('btn-todos-maestros')?.addEventListener('click', () => {
        Array.from(document.getElementById('filtro-maestro').options).forEach(o => o.selected = true);
    });
    document.getElementById('btn-ninguno-maestros')?.addEventListener('click', () => {
        Array.from(document.getElementById('filtro-maestro').options).forEach(o => o.selected = false);
    });

    // Admin central: al cambiar de facultad se repuebla el selector de aulas.
    document.getElementById('filtro-facultad')?.addEventListener('change', (e) => {
        const facultadId = e.target.value ? Number(e.target.value) : null;
        poblarAulas(facultadId);
    });
}

async function detectarRolYCargar() {
    isAdmin = isAdminUser();
    esCentral = isAdminCentral();
    esSuper = hasRole('SUPER_ADMIN') && !esCentral;
    const user = getCurrentUser();
    const loading = document.getElementById('loading');
    if (loading) loading.style.display = 'block';

    try {
        // Admin carga todas; profesor solo las suyas via endpoint dedicado.
        // Los admins además cargan aulas y facultades para mapear y filtrar.
        const [data] = await Promise.all([
            isAdmin ? reservasApi.listar() : reservasApi.listarPorUsuario(user.id),
            isAdmin ? cargarCatalogos() : Promise.resolve(),
        ]);

        allReservas = (data || []).map(normalizeReserva).map(enriquecerReserva);

        if (isAdmin) {
            document.getElementById('grupo-maestro').style.display = 'flex';
            const maestros = [...new Set(allReservas.map(r => r.profesorName).filter(Boolean))].sort();
            const sel = document.getElementById('filtro-maestro');
            sel.innerHTML = '';
            maestros.forEach(m => {
                const opt = document.createElement('option');
                opt.value = m;
                opt.textContent = m;
                opt.selected = true;
                sel.appendChild(opt);
            });

            montarFiltrosAula();
        }
    } catch (error) {
        console.error("Error cargando reservas:", error);
    } finally {
        if (loading) loading.style.display = 'none';
    }
}

// Carga aulas y facultades y construye los mapas de apoyo.
async function cargarCatalogos() {
    try {
        const [aulas, facultades] = await Promise.all([
            aulasApi.listar().catch(() => []),
            facultadesApi.listar().catch(() => []),
        ]);
        (facultades || []).forEach(f => facultadPorId.set(Number(f.id), f.nombre));
        (aulas || []).forEach(a => aulaPorId.set(Number(a.id), {
            facultadId: a.facultadId != null ? Number(a.facultadId) : null,
            codigo: a.codigo,
            nombre: a.nombre,
        }));
    } catch (error) {
        console.warn("No se pudieron cargar aulas/facultades:", error);
    }
}

// Completa cada reserva con datos del aula y su facultad (el DTO solo trae aulaId).
function enriquecerReserva(r) {
    const info = aulaPorId.get(Number(r.aulaId));
    if (info) {
        r.aula = info.nombre || r.aula;
        r.aulaCodigo = info.codigo || r.aulaCodigo;
        r.facultadId = info.facultadId;
        r.facultadNombre = facultadPorId.get(info.facultadId) || '';
    }
    return r;
}

// Muestra y puebla los selectores de facultad (central) y aula (super/central),
// limitados a las aulas/facultades que realmente aparecen en las reservas visibles.
function montarFiltrosAula() {
    if (esCentral) {
        const grupo = document.getElementById('grupo-facultad');
        const select = document.getElementById('filtro-facultad');
        if (grupo && select) {
            grupo.style.display = 'flex';
            const idsFacultades = [...new Set(
                allReservas.map(r => r.facultadId).filter(v => v != null)
            )].sort((a, b) => (facultadPorId.get(a) || '').localeCompare(facultadPorId.get(b) || ''));

            select.innerHTML = '<option value="">Todas las facultades</option>' +
                idsFacultades.map(id => `<option value="${id}">${facultadPorId.get(id) || `Facultad ${id}`}</option>`).join('');
        }
    }

    if (esSuper || esCentral) {
        document.getElementById('grupo-aula').style.display = 'flex';
        poblarAulas(null);
    }
}

// Llena el selector de aulas con las aulas presentes en las reservas visibles,
// opcionalmente acotadas a una facultad (caso admin central).
function poblarAulas(facultadIdFiltro) {
    const select = document.getElementById('filtro-aula');
    if (!select) return;

    const aulasVisibles = new Map(); // aulaId -> etiqueta
    allReservas.forEach(r => {
        if (r.aulaId == null) return;
        if (facultadIdFiltro && r.facultadId !== facultadIdFiltro) return;
        if (!aulasVisibles.has(r.aulaId)) {
            const etiqueta = r.aulaCodigo ? `${r.aulaCodigo} — ${r.aula}` : r.aula;
            aulasVisibles.set(r.aulaId, etiqueta);
        }
    });

    const ordenadas = [...aulasVisibles.entries()].sort((a, b) => a[1].localeCompare(b[1]));
    select.innerHTML = '<option value="">Todas las aulas</option>' +
        ordenadas.map(([id, label]) => `<option value="${id}">${label}</option>`).join('');
}

function parseFecha(str) {
    if (!str) return null;
    const [y, m, d] = str.split('-').map(Number);
    if (!y || !m || !d) return null;
    return new Date(y, m - 1, d);
}

function getRangoDelPeriodo() {
    const periodo = document.getElementById('filtro-periodo')?.value;
    let inicio = null, fin = null;

    if (periodo === 'dia') {
        const val = document.getElementById('fecha-dia').value;
        if (val) {
            inicio = new Date(val + 'T00:00:00');
            fin = new Date(val + 'T23:59:59');
        }
    } else if (periodo === 'semana') {
        const val = document.getElementById('fecha-semana').value;
        if (val) {
            const [yearStr, weekStr] = val.split('-W');
            const year = parseInt(yearStr);
            const week = parseInt(weekStr);
            const jan4 = new Date(year, 0, 4);
            const monday = new Date(jan4);
            monday.setDate(jan4.getDate() - ((jan4.getDay() + 6) % 7) + (week - 1) * 7);
            inicio = monday;
            fin = new Date(monday);
            fin.setDate(fin.getDate() + 6);
            fin.setHours(23, 59, 59);
        }
    } else if (periodo === 'mes') {
        const val = document.getElementById('fecha-mes').value;
        if (val) {
            const [y, m] = val.split('-').map(Number);
            inicio = new Date(y, m - 1, 1);
            fin = new Date(y, m, 0, 23, 59, 59);
        }
    } else if (periodo === 'semestre') {
        const val = document.getElementById('semestre-val').value;
        if (val) {
            const [yearStr, sem] = val.split('-');
            const y = parseInt(yearStr);
            inicio = sem === 'A' ? new Date(y, 0, 1) : new Date(y, 6, 1);
            fin = sem === 'A' ? new Date(y, 5, 30, 23, 59, 59) : new Date(y, 11, 31, 23, 59, 59);
        }
    } else if (periodo === 'rango') {
        const start = document.getElementById('rango-inicio').value;
        const end = document.getElementById('rango-fin').value;
        if (start) inicio = new Date(start + 'T00:00:00');
        if (end) fin = new Date(end + 'T23:59:59');
    }

    return { inicio, fin };
}

function getMaestrosSeleccionados() {
    if (!isAdmin) return null;
    const sel = document.getElementById('filtro-maestro');
    const seleccionados = Array.from(sel.selectedOptions).map(o => o.value);
    if (seleccionados.length === 0 || seleccionados.length === sel.options.length) return null;
    return new Set(seleccionados);
}

// Facultad seleccionada (solo admin central). null = todas.
function getFacultadSeleccionada() {
    if (!esCentral) return null;
    const val = document.getElementById('filtro-facultad')?.value;
    return val ? Number(val) : null;
}

// Aula seleccionada (super admin y admin central). null = todas.
function getAulaSeleccionada() {
    if (!esSuper && !esCentral) return null;
    const val = document.getElementById('filtro-aula')?.value;
    return val ? Number(val) : null;
}

function aplicarFiltros() {
    const { inicio, fin } = getRangoDelPeriodo();
    const maestros = getMaestrosSeleccionados();
    const facultadId = getFacultadSeleccionada();
    const aulaId = getAulaSeleccionada();

    filteredReservas = allReservas.filter(r => {
        if (maestros && !maestros.has(r.profesorName)) return false;
        if (facultadId && r.facultadId !== facultadId) return false;
        if (aulaId && Number(r.aulaId) !== aulaId) return false;
        if (inicio || fin) {
            const fecha = parseFecha(r.fecha);
            if (!fecha) return false;
            if (inicio && fecha < inicio) return false;
            if (fin && fecha > fin) return false;
        }
        return true;
    });

    filteredReservas.sort((a, b) => (b.fecha || '').localeCompare(a.fecha || ''));
    mostrarInfo();
    renderReservas(filteredReservas);
}

function mostrarInfo() {
    const info = document.getElementById('resultados-info');
    if (!info) return;
    if (filteredReservas.length === 0) {
        info.style.display = 'none';
        return;
    }
    info.style.display = 'block';
    info.textContent = `${filteredReservas.length} registro${filteredReservas.length !== 1 ? 's' : ''} encontrado${filteredReservas.length !== 1 ? 's' : ''}`;
}

function formatFecha(str) {
    if (!str) return 'N/A';
    const [y, m, d] = str.split('-');
    if (y && m && d) return `${d}/${m}/${y}`;
    return str;
}

function extraerHora(horario) {
    if (!horario) return 'N/A';
    const idx = horario.indexOf(' ');
    return idx !== -1 ? horario.slice(idx + 1) : horario;
}

function renderReservas(reservas) {
    const container = document.getElementById('reservas-container');
    const emptyMsg = document.getElementById('empty-message');

    if (reservas.length === 0) {
        if (container) container.innerHTML = '';
        if (emptyMsg) emptyMsg.style.display = 'block';
        return;
    }

    if (emptyMsg) emptyMsg.style.display = 'none';

    container.innerHTML = reservas.map((data) => {
        const statusClass = getStatusClass(data.status);
        const icono = data.aula && data.aula.toLowerCase().includes("auditorio") ? "podium" : "computer";
        const hora  = extraerHora(data.horario);
        const puedeCantelar = !isAdmin && data.status === "Pendiente";

        return `
            <div class="card reserva-card" id="reserva-card-${data.id}">
                <div class="reserva-header">
                    <div class="aula-icon-small">
                        <span class="material-symbols-outlined">${icono}</span>
                    </div>
                    <div class="reserva-title">
                        <h3>${escapeHtml(data.materia || "Sin materia")}</h3>
                        <span>${escapeHtml(data.aula || "Aula desconocida")}</span>
                    </div>
                    <span class="status-badge ${statusClass}">${data.status || "Pendiente"}</span>
                </div>
                <div class="reserva-details">
                    ${isAdmin ? `<p><strong>Maestro:</strong> ${escapeHtml(data.profesorName || 'N/A')}</p>` : ''}
                    <p><strong>Fecha:</strong> ${formatFecha(data.fecha)}</p>
                    <p><strong>Horario:</strong> ${hora}</p>
                    <p><strong>Grupo:</strong> ${escapeHtml(data.grupo || 'N/A')}</p>
                    <p><strong>Turno:</strong> ${data.turno || 'N/A'}</p>
                    ${data.motivoRechazo ? `<p><strong>Motivo rechazo:</strong> ${escapeHtml(data.motivoRechazo)}</p>` : ''}
                </div>
                ${puedeCantelar ? `
                <div class="reserva-actions">
                    <button class="btn-cancelar" data-id="${data.id}">
                        <span class="material-symbols-outlined" style="font-size:16px;vertical-align:middle">cancel</span>
                        Cancelar reserva
                    </button>
                </div>` : ''}
            </div>`;
    }).join('');

    // Eventos de cancelación
    container.querySelectorAll('.btn-cancelar').forEach(btn => {
        btn.addEventListener('click', () => cancelarReserva(btn.dataset.id, btn));
    });
}

function exportarPDF() {
    if (filteredReservas.length === 0) {
        mostrarToast('No hay registros para exportar con el filtro actual.', 'aviso');
        return;
    }

    const { jsPDF } = window.jspdf;
    const pdf = new jsPDF({ orientation: 'landscape', unit: 'mm', format: 'letter' });

    let columnas = ['Fecha', 'Materia', 'Aula', 'Grupo', 'Turno', 'Horario', 'Estado'];
    if (isAdmin)   columnas = ['Maestro', ...columnas];
    if (esCentral) columnas = ['Facultad', ...columnas];

    const filas = filteredReservas.map(r => {
        let fila = [formatFecha(r.fecha), r.materia || 'N/A', r.aula || 'N/A', r.grupo || 'N/A', r.turno || 'N/A', extraerHora(r.horario), r.status || 'Pendiente'];
        if (isAdmin)   fila = [r.profesorName || 'N/A', ...fila];
        if (esCentral) fila = [r.facultadNombre || 'N/A', ...fila];
        return fila;
    });

    pdf.text('Historial de Reservas - AulaHub', 14, 16);
    pdf.autoTable({ head: [columnas], body: filas, startY: 24 });
    pdf.save(`reservas_${new Date().toISOString().slice(0, 10)}.pdf`);
}

function getStatusClass(status) {
    if (status === "Aceptada") return "status-success";
    if (status === "Rechazada") return "status-error";
    if (status === "Cancelada") return "status-error";
    return "status-pending";
}

async function cancelarReserva(id, btn) {
    btn.disabled = true;
    btn.textContent = "Cancelando...";
    try {
        await reservasApi.cancelar(id);
        mostrarToast("Reserva cancelada.", "exito");
        // Recarga la lista respetando los filtros activos
        await detectarRolYCargar();
        aplicarFiltros();
    } catch (err) {
        mostrarToast("Error al cancelar: " + err.message, "error");
        btn.disabled = false;
        btn.innerHTML = '<span class="material-symbols-outlined" style="font-size:16px;vertical-align:middle">cancel</span> Cancelar reserva';
    }
}
