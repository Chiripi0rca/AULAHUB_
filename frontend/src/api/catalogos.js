import { api, apiRequest } from "./interceptor.js";

// Catalogos administrables usados por reservas y pantallas de gestion.
export const aulasApi = {
  // conImagen=true solo lo usan las pantallas de gestion (thumbnail por fila).
  // El resto recibe el listado ligero, sin la imagen base64 de cada aula.
  listar: (facultadId, conImagen) => {
    const params = {};
    if (facultadId) params.facultadId = facultadId;
    if (conImagen) params.conImagen = true;
    return api.get("/aulas", params);
  },
  obtener: (id) => api.get(`/aulas/${id}`),
  crear: (payload) => api.post("/aulas", payload),
  actualizar: (id, payload) => api.put(`/aulas/${id}`, payload),
  desactivar: (id) => api.put(`/aulas/${id}/desactivar`),
  activar: (id) => api.put(`/aulas/${id}/activar`),
  // Sube la imagen del aula (multipart). El backend la guarda como base64 en la BD.
  subirImagen: (id, file) => {
    const formData = new FormData();
    formData.append("file", file);
    return apiRequest(`/aulas/${id}/imagen`, { method: "PUT", body: formData });
  },
};

export const materiasApi = {
  listar: () => api.get("/materias"),
  obtener: (id) => api.get(`/materias/${id}`),
  crear: (payload) => api.post("/materias", payload),
  actualizar: (id, payload) => api.put(`/materias/${id}`, payload),
  desactivar: (id) => api.put(`/materias/${id}/desactivar`),
  activar: (id) => api.put(`/materias/${id}/activar`),
};

export const tiposReservaApi = {
  listar: () => api.get("/tipos-reserva"),
  // Solo los tipos que el rol del usuario actual puede usar — para el dropdown de reserva.
  disponibles: () => api.get("/tipos-reserva/disponibles"),
  obtener: (id) => api.get(`/tipos-reserva/${id}`),
  crear: (payload) => api.post("/tipos-reserva", payload),
  actualizar: (id, payload) => api.put(`/tipos-reserva/${id}`, payload),
  desactivar: (id) => api.put(`/tipos-reserva/${id}/desactivar`),
  activar: (id) => api.put(`/tipos-reserva/${id}/activar`),
};

export const gruposApi = {
  listar: (facultadId) => api.get("/grupos", facultadId ? { facultadId } : {}),
  obtener: (id) => api.get(`/grupos/${id}`),
  // Materias que se imparten en el grupo (para filtrar al asignar al maestro).
  materias: (id) => api.get(`/grupos/${id}/materias`),
  crear: (payload) => api.post("/grupos", payload),
  actualizar: (id, payload) => api.put(`/grupos/${id}`, payload),
  // Reemplaza el conjunto de materias del grupo.
  setMaterias: (id, materiaIds) => api.put(`/grupos/${id}/materias`, { materiaIds }),
  desactivar: (id) => api.put(`/grupos/${id}/desactivar`),
  activar: (id) => api.put(`/grupos/${id}/activar`),
};

export const facultadesApi = {
  listar: () => api.get("/facultades"),
  obtener: (id) => api.get(`/facultades/${id}`),
  crear: (payload) => api.post("/facultades", payload),
  actualizar: (id, payload) => api.put(`/facultades/${id}`, payload),
  desactivar: (id) => api.put(`/facultades/${id}/desactivar`),
  activar: (id) => api.put(`/facultades/${id}/activar`),
};
