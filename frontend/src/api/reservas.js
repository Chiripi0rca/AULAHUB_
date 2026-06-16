import { api } from "./interceptor.js";

// El backend devuelve estos listados paginados ({ contenido: [...] }).
// Solicitudes e historial filtran en memoria, asi que pedimos un lote grande
// y desempacamos a un array plano para que las paginas reciban siempre una lista.
const TAM_LOTE = 2000;
const desempacar = (resp) =>
  Array.isArray(resp) ? resp : (resp?.contenido ?? resp?.content ?? resp?.data ?? []);

// Flujo principal de reservas: listar, crear y cambiar estado.
export const reservasApi = {
  listar: async ()              => desempacar(await api.get("/reservas", { size: TAM_LOTE })),
  listarPorUsuario: async (id)  => desempacar(await api.get(`/reservas/usuario/${id}`, { size: TAM_LOTE })),
  listarPorAula: async (id)     => desempacar(await api.get(`/reservas/aula/${id}`, { size: TAM_LOTE })),
  // Reservas activas (PENDIENTE/ACEPTADA) de un aula en un rango. desde/hasta en dd-MM-yyyy.
  calendario: (id, desde, hasta) => api.get(`/reservas/aula/${id}/calendario`, { desde, hasta }),
  obtener: (id)           => api.get(`/reservas/${id}`),
  crear: (payload)        => api.post("/reservas", payload),
  aceptar: (id)           => api.put(`/reservas/${id}/aceptar`),
  rechazar: (id, motivo)  => api.put(`/reservas/${id}/rechazar`, { motivo }),
  cancelar: (id)          => api.put(`/reservas/${id}/cancelar`),
};
