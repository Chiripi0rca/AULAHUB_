import { apiRequest } from "./interceptor.js";
import { api } from "./interceptor.js";

// API de perfil de usuario: lectura, actualización y subida de foto al servidor.
export const usuariosApi = {
  getPerfil: () =>
    api.get("/usuarios/perfil"),

  updatePerfil: (data) =>
    api.put("/usuarios/perfil", data),

  // Actualiza la foto de perfil con una URL ya hospedada (forma soportada por el backend).
  updateFotoUrl: (fotoUrl) =>
    api.put("/usuarios/perfil", { fotoUrl }),

  // Subida por archivo (multipart). Formatos: jpg, png, webp, gif. Máx 5 MB.
  uploadFoto: (file) => {
    const formData = new FormData();
    formData.append("file", file);
    return apiRequest("/usuarios/perfil/foto", { method: "PUT", body: formData });
  },

  // Asignaciones (grupo, materia) del usuario logueado — para la reserva del profesor.
  misAsignaciones: () => api.get("/usuarios/mis-asignaciones"),
};

// Endpoints administrativos de usuarios (solo SUPER_ADMIN / ADMIN_CENTRAL).
export const usuariosAdminApi = {
  listar: () => api.get("/usuarios"),

  // Maestros (usuarios con rol PROFESOR).
  listarProfesores: () => api.get("/usuarios/profesores"),

  // Materias asignadas a un maestro.
  obtenerMaterias: (usuarioId) => api.get(`/usuarios/${usuarioId}/materias`),

  // Reemplaza el conjunto de materias del maestro por la lista dada.
  asignarMaterias: (usuarioId, materiaIds) =>
    api.put(`/usuarios/${usuarioId}/materias`, { materiaIds }),

  // Asignaciones (grupo, materia) del maestro.
  obtenerAsignaciones: (usuarioId) => api.get(`/usuarios/${usuarioId}/asignaciones`),

  // Reemplaza las asignaciones (grupo, materia). asignaciones = [{ grupoId, materiaId }, ...].
  asignarAsignaciones: (usuarioId, asignaciones) =>
    api.put(`/usuarios/${usuarioId}/asignaciones`, { asignaciones }),
};
