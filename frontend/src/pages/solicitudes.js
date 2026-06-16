import { reservasApi } from "../api/reservas.js";
import { getCurrentUser } from "../api/auth-session.js";
import { normalizeReserva, toBackendStatus } from "../api/mappers.js";
import { escapeHtml, mostrarToast } from "../layout.js";
import { soloAdmin } from "../route-guard.js";

// Bandeja de solicitudes para administradores.
soloAdmin();

let currentTurnoAdmin = "";

// Estado interno del modal de rechazo
let _pendingRechazarId = null;

document.addEventListener("DOMContentLoaded", () => {
    currentTurnoAdmin = getCurrentUser()?.raw?.turno || "";
    cargarSolicitudes("Pendiente");

    const filterSelect = document.getElementById("statusFilter");
    filterSelect?.addEventListener("change", () => cargarSolicitudes(filterSelect.value));

    // ── Modal de rechazo ──────────────────────────────────────────────────
    const modal        = document.getElementById("modal-rechazo");
    const inputMotivo  = document.getElementById("input-motivo");
    const btnCancelar  = document.getElementById("btn-cancelar-rechazo");
    const btnConfirmar = document.getElementById("btn-confirmar-rechazo");

    btnCancelar?.addEventListener("click", cerrarModal);

    // Cierra el modal al hacer clic fuera del panel
    modal?.addEventListener("click", (e) => { if (e.target === modal) cerrarModal(); });

    btnConfirmar?.addEventListener("click", async () => {
        const motivo = inputMotivo?.value.trim();
        if (!motivo) {
            inputMotivo?.classList.add("input-error");
            inputMotivo?.focus();
            return;
        }
        cerrarModal();
        await ejecutarRechazo(_pendingRechazarId, motivo);
    });
});

function abrirModal(reservaId) {
    _pendingRechazarId = reservaId;
    const modal       = document.getElementById("modal-rechazo");
    const inputMotivo = document.getElementById("input-motivo");
    if (inputMotivo) { inputMotivo.value = ""; inputMotivo.classList.remove("input-error"); }
    if (modal) { modal.style.display = "flex"; inputMotivo?.focus(); }
}

function cerrarModal() {
    const modal = document.getElementById("modal-rechazo");
    if (modal) modal.style.display = "none";
    _pendingRechazarId = null;
}

async function cargarSolicitudes(statusFilter) {
    const container = document.getElementById('solicitudes-container');
    const loading   = document.getElementById('loading');
    const emptyMsg  = document.getElementById('empty-message');

    if (container) container.innerHTML = "";
    if (emptyMsg)  emptyMsg.style.display = 'none';
    if (loading)   loading.style.display = 'block';

    try {
        const reservas = (await reservasApi.listar())
            .map(normalizeReserva)
            .filter((r) => r.status === statusFilter)
            .filter((r) => !currentTurnoAdmin || r.turno.toUpperCase() === String(currentTurnoAdmin).toUpperCase());

        if (loading) loading.style.display = 'none';

        if (!reservas.length) {
            if (emptyMsg) emptyMsg.style.display = 'block';
            return;
        }

        container.innerHTML = reservas.map((r) => {
            const icono = r.aula && r.aula.toLowerCase().includes("auditorio") ? "podium" : "computer";
            let botonesHtml = "";

            if (statusFilter === "Pendiente") {
                botonesHtml = `
                    <div class="solicitud-actions">
                        <button data-reserva-id="${r.id}" data-action="rechazar" class="btn-rechazar">Rechazar</button>
                        <button data-reserva-id="${r.id}" data-action="aceptar"  class="btn-aceptar">Aceptar</button>
                    </div>`;
            } else {
                const colorClass = statusFilter === "Aceptada" ? "text-green" : "text-red";
                botonesHtml = `<div class="solicitud-actions"><span class="${colorClass}">Estado: ${escapeHtml(statusFilter)}</span></div>`;
            }

            return `
            <div class="card solicitud-card" id="card-${r.id}">
                <div class="solicitud-header">
                    <div class="aula-icon-small"><span class="material-symbols-outlined">${icono}</span></div>
                    <div class="solicitud-info">
                        <h3>${escapeHtml(r.materia || "Sin materia")}</h3>
                        <span class="profesor-name">${escapeHtml(r.profesorName || "Profesor")}</span>
                    </div>
                </div>
                <div class="solicitud-body">
                    <p><strong>Aula:</strong> ${escapeHtml(r.aula)}</p>
                    <p><strong>Grupo:</strong> ${escapeHtml(r.grupo)}</p>
                    <p><strong>Horario:</strong> ${escapeHtml(r.horario)}</p>
                    <p><strong>Fecha:</strong> ${escapeHtml(r.fecha || "N/A")}</p>
                </div>
                ${botonesHtml}
            </div>`;
        }).join("");

        container.querySelectorAll("button[data-reserva-id]").forEach((btn) => {
            btn.addEventListener("click", () => {
                const id = btn.dataset.reservaId;
                if (btn.dataset.action === "rechazar") {
                    abrirModal(id);
                } else {
                    responderSolicitud(id, "Aceptada");
                }
            });
        });
    } catch (error) {
        console.error("Error:", error);
        if (loading) loading.style.display = 'none';
        if (emptyMsg) emptyMsg.style.display = 'block';
    }
}

async function responderSolicitud(id, nuevoStatus, motivo = "") {
    const card = document.getElementById(`card-${id}`);
    if (card) { card.style.opacity = "0.5"; card.style.pointerEvents = "none"; }

    try {
        if (toBackendStatus(nuevoStatus) === "ACEPTADA") {
            await reservasApi.aceptar(id);
        } else {
            await reservasApi.rechazar(id, motivo);
        }
        const filterSelect = document.getElementById("statusFilter");
        cargarSolicitudes(filterSelect ? filterSelect.value : "Pendiente");
    } catch (error) {
        console.error(error);
        mostrarToast("Error al procesar la solicitud.", 'error');
        if (card) { card.style.opacity = "1"; card.style.pointerEvents = "all"; }
    }
}

async function ejecutarRechazo(id, motivo) {
    await responderSolicitud(id, "Rechazada", motivo);
}
