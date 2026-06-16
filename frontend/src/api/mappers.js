const AULA_NOMBRES = {
  labA: "Laboratorio de Computo A",
  labB: "Laboratorio de Computo B",
  centro: "Centro de Computo",
  auditorio: "Auditorio FIC",
  labCD: "Laboratorio de Ciencia de Datos",
  labP: "Laboratorio de Posgrado",
};

// El backend habla fechas en formato mexicano dd-MM-yyyy.
// El frontend trabaja internamente en ISO yyyy-MM-dd (para ordenar y comparar como string,
// y para que coincida con los data-fecha de las celdas del calendario).
// Estas dos funciones traducen únicamente en la frontera con la API.
export function isoToBackendFecha(iso) {
  if (!iso) return iso;
  const [y, m, d] = String(iso).split("-");
  if (!y || !m || !d) return iso;
  return `${d}-${m}-${y}`;
}

export function backendFechaToISO(fecha) {
  if (!fecha) return fecha;
  const partes = String(fecha).split("-");
  if (partes.length !== 3) return fecha;
  // Si el primer bloque tiene 4 dígitos ya viene en ISO (compatibilidad) → se deja igual
  if (partes[0].length === 4) return fecha;
  const [d, m, y] = partes;
  return `${y}-${m}-${d}`;
}

// Convierte respuestas del backend al formato que esperan las pantallas actuales.
export function normalizeStatus(status) {
  const value = String(status || "PENDIENTE").toUpperCase();
  if (value === "ACEPTADA") return "Aceptada";
  if (value === "RECHAZADA") return "Rechazada";
  if (value === "CANCELADA") return "Cancelada";
  return "Pendiente";
}

export function toBackendStatus(status) {
  const value = String(status || "").toLowerCase();
  if (value.includes("acept")) return "ACEPTADA";
  if (value.includes("rechaz")) return "RECHAZADA";
  if (value.includes("cancel")) return "CANCELADA";
  return "PENDIENTE";
}

export function normalizeTurno(turno) {
  const value = String(turno || "MATUTINO").toUpperCase();
  return value.startsWith("VES") ? "Vespertino" : "Matutino";
}

export function toBackendTurno(turno) {
  return String(turno || "").toLowerCase().startsWith("ves") ? "VESPERTINO" : "MATUTINO";
}

export function aulaNombreFromCodigo(codigo) {
  return AULA_NOMBRES[codigo] || codigo || "Aula desconocida";
}

export function normalizeAula(aula) {
  if (!aula) return {};
  return {
    id: aula.id,
    codigo: aula.codigo,
    nombre: aula.nombre || aulaNombreFromCodigo(aula.codigo),
    activa: aula.activa !== false,
    capacidad: aula.capacidad ?? null,   // Integer, puede ser null
    imagenUrl: aula.imagenUrl ?? null,   // Data URI base64, puede ser null
    raw: aula,
  };
}

export function normalizeReserva(reserva) {
  // El backend devuelve campos planos (IDs). Se soportan tambien objetos anidados por si el ORM los incluye.
  const aulaObj      = reserva.aula && typeof reserva.aula === "object" ? reserva.aula : null;
  const materiaObj   = reserva.materia && typeof reserva.materia === "object" ? reserva.materia : null;
  const solicObj     = reserva.solicitante && typeof reserva.solicitante === "object" ? reserva.solicitante
                     : reserva.usuario && typeof reserva.usuario === "object" ? reserva.usuario : null;

  const horaInicio = reserva.horaInicio || reserva.hora_inicio || "";
  const horaFin    = reserva.horaFin    || reserva.hora_fin    || "";
  // El backend manda dd-MM-yyyy; se normaliza a ISO para el resto del front.
  const fechaISO   = backendFechaToISO(reserva.fecha || "");
  const horario    = `${fechaISO || ""} ${horaInicio}${horaFin ? ` - ${horaFin}` : ""}`.trim();

  return {
    id:             reserva.id,
    ProfesorUID:    solicObj?.id    ?? reserva.solicitanteId ?? null,
    profesorName:   solicObj?.nombre ?? reserva.solicitanteNombre ?? reserva.profesorName ?? reserva.nombreProfesor ?? "Profesor",
    aulaId:         aulaObj?.id     ?? reserva.aulaId,
    aulaCodigo:     aulaObj?.codigo ?? reserva.aulaCodigo   ?? null,
    aula:           aulaObj?.nombre ?? reserva.aulaNombre   ?? reserva.nombreAula ?? `Aula ${reserva.aulaId ?? "?"}`,
    tipoReservaId:  reserva.tipoReservaId ?? reserva.tipoReserva?.id ?? null,
    tipoNombre:     reserva.tipoReservaNombre ?? reserva.tipoReserva?.nombre ?? "",
    tipoClave:      reserva.tipoReservaClave  ?? reserva.tipoReserva?.clave  ?? "",
    tipoPrioridad:  Number(reserva.tipoReservaPrioridad ?? reserva.tipoReserva?.prioridad ?? 0),
    materiaId:      materiaObj?.id  ?? reserva.materiaId,
    materia:        materiaObj?.nombre ?? reserva.materiaNombre ?? reserva.nombreMateria
                      ?? (reserva.materiaId ? `Materia ${reserva.materiaId}` : ""),
    grupo:          reserva.grupo   || "",
    turno:          normalizeTurno(reserva.turno),
    fecha:          fechaISO,
    horaInicio,
    horaFin,
    horario,
    status:         normalizeStatus(reserva.status || reserva.estado),
    motivoRechazo:  reserva.motivoRechazo || null,
    esExterno:      reserva.esExterno || false,
    createdAt:      reserva.createdAt || reserva.created_at || null,
    raw:            reserva,
  };
}

// El backend obtiene el solicitanteId del JWT — no se manda en el body.
export function buildReservaPayload({ aula, tipoReservaId, materiaId, grupo, turno, fecha, hora }) {
  const [horaInicio, horaFin] = String(hora || "").split(" - ");
  return {
    aulaId: aula?.id,
    tipoReservaId,
    materiaId: materiaId || null,
    // grupo vacío va como null: el backend valida el formato y rechazaría una cadena vacía.
    grupo: grupo && String(grupo).trim() ? String(grupo).trim() : null,
    turno: toBackendTurno(turno),
    fecha: isoToBackendFecha(fecha), // el backend espera dd-MM-yyyy
    horaInicio,
    horaFin,
    esExterno: false,
  };
}
