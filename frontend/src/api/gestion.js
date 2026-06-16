import { api } from "./interceptor.js";

// Endpoints administrativos: usuarios, roles, dashboard y actividad log.
export const usuariosApi = {
  listar: () => api.get("/usuarios"),
  obtener: (id) => api.get(`/usuarios/${id}`),
  actualizar: (id, payload) => api.put(`/usuarios/${id}`, payload), // { nombre }
  desactivar: (id) => api.put(`/usuarios/${id}/desactivar`),
  activar: (id) => api.put(`/usuarios/${id}/activar`),
};

export const rolesApi = {
  listar: () => api.get("/roles"),
  misRoles: () => api.get("/roles/mis-roles"),
  listarPorUsuario:  (usuarioId)  => api.get(`/roles/usuario/${usuarioId}`),
  listarPorFacultad: (facultadId) => api.get(`/roles/facultad/${facultadId}`),
  asignar: (payload) => api.post("/roles", payload),
  eliminar: (id) => api.delete(`/roles/${id}`),
  eliminarAula: (rolId, aulaId) => api.delete(`/roles/${rolId}/aulas/${aulaId}`),
};

export const dashboardApi = {
  admin: (usuarioId) => api.get(`/dashboard/admin/${usuarioId}`),
  superAdmin: (usuarioId) => api.get(`/dashboard/super-admin/${usuarioId}`),
  adminCentral: () => api.get("/dashboard/admin-central"),
};

// filtros = { q, accion, entidad, desde, hasta } — buildUrl ignora los valores vacios.
export const actividadLogApi = {
  admin:        (usuarioId, page = 0, size = 25, filtros = {}) => api.get(`/actividad-log/admin/${usuarioId}`,        { page, size, ...filtros }),
  superAdmin:   (usuarioId, page = 0, size = 25, filtros = {}) => api.get(`/actividad-log/super-admin/${usuarioId}`,  { page, size, ...filtros }),
  adminCentral: (           page = 0, size = 25, filtros = {}) => api.get("/actividad-log/admin-central",             { page, size, ...filtros }),
};
