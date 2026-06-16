import { api } from "./interceptor.js";

// Notificaciones del usuario autenticado.
// ⚠️ listarPorUsuario devuelve respuesta PAGINADA: { contenido, paginaActual, totalPaginas, ... }
export const notificacionesApi = {
  listarPorUsuario: (usuarioId, page = 0, size = 20) =>
    api.get(`/notificaciones/usuario/${usuarioId}`, { page, size }),

  // Conteo de no leídas para el badge — responde { noLeidas: N }
  noLeidas: (usuarioId) => api.get(`/notificaciones/usuario/${usuarioId}/no-leidas`),

  marcarLeida:      (id)        => api.put(`/notificaciones/${id}/leer`),
  marcarTodasLeidas:(usuarioId) => api.put(`/notificaciones/usuario/${usuarioId}/leer-todas`),
  eliminar:         (id)        => api.delete(`/notificaciones/${id}`),
};
