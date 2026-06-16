import { API_BASE_URL } from "./api-config.js";
import { getAccessToken } from "./auth-session.js";

// Descarga de reportes binarios (PDF/Excel). Usa fetch directo porque el interceptor no maneja blobs.
// params: { desde?, hasta? } — fechas opcionales en formato dd-MM-yyyy
async function descargarArchivo(ruta, nombreArchivo, params = {}) {
  const token = getAccessToken();
  const qs = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null && v !== "")
    .map(([k, v]) => `${k}=${encodeURIComponent(v)}`)
    .join("&");
  const url = `${API_BASE_URL}${ruta}${qs ? `?${qs}` : ""}`;

  const response = await fetch(url, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) throw new Error(`Error al descargar el reporte (${response.status})`);

  const blob = await response.blob();

  // En la app movil (Capacitor) el WebView NO descarga blobs con <a download>.
  // Guardamos el archivo y abrimos el menu de compartir (abrir en visor / guardar en Files/Drive).
  if (window.Capacitor?.isNativePlatform?.()) {
    await guardarYCompartirMovil(blob, nombreArchivo);
    return;
  }

  // Navegador web: descarga normal.
  const objectUrl = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = objectUrl;
  a.download = nombreArchivo;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(objectUrl);
}

// Movil: escribe el archivo en cache y lo comparte para que el usuario lo abra o guarde.
async function guardarYCompartirMovil(blob, nombreArchivo) {
  const { Filesystem, Share } = window.Capacitor.Plugins;
  const base64 = await blobABase64(blob);

  await Filesystem.writeFile({
    path: nombreArchivo,
    data: base64,
    directory: "CACHE",
    recursive: true,
  });
  const { uri } = await Filesystem.getUri({ path: nombreArchivo, directory: "CACHE" });

  try {
    await Share.share({ title: nombreArchivo, url: uri });
  } catch (e) {
    // El usuario cerro/cancelo el menu de compartir; no es un error real.
  }
}

// Convierte un Blob a base64 (sin el prefijo "data:...;base64,").
function blobABase64(blob) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onloadend = () => resolve(String(reader.result).split(",")[1] || "");
    reader.onerror = reject;
    reader.readAsDataURL(blob);
  });
}

// desde / hasta opcionales — formato dd-MM-yyyy. Sin parámetros exporta todo el historial.
export const reportesApi = {
  aulaPdf:     (aulaId,    { desde, hasta } = {}) =>
    descargarArchivo(`/reportes/aula/${aulaId}/pdf`,        `reservas-aula-${aulaId}.pdf`,        { desde, hasta }),
  profesorPdf: (usuarioId, { desde, hasta } = {}) =>
    descargarArchivo(`/reportes/profesor/${usuarioId}/pdf`, `reservas-profesor-${usuarioId}.pdf`, { desde, hasta }),
  excelGlobal: (           { desde, hasta } = {}) =>
    descargarArchivo(`/reportes/excel`,                     `reservas-global.xlsx`,               { desde, hasta }),
};
