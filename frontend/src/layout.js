import { notificacionesApi } from "./api/notificaciones.js";
import { getCurrentUser, hasRole, isAdminCentral } from "./api/auth-session.js";

// Utilidades compartidas para rutas relativas, header, menu y notificaciones.
export function pageUrl(page) {
    const inPages = window.location.pathname.includes("/pages/");
    if (page === "index.html") return inPages ? "../index.html" : "index.html";
    return inPages ? page : `pages/${page}`;
}

export function assetUrl(path) {
    const inPages = window.location.pathname.includes("/pages/");
    return inPages ? `../${path}` : path;
}

let currentUid = null;

export async function cargarHeader() {
    cargarFavicon();

    const headerElement = document.querySelector('header');
    if (!headerElement) return;

    const html = await fetch(assetUrl('assets/partials/header.html'))
        .then(r => r.text())
        .catch(() => null);

    if (html) headerElement.innerHTML = html;

    // ── Crea e inyecta el sidebar en el body ─────────────────────────────
    if (!document.getElementById('sidebar')) {
        const aside = document.createElement('aside');
        aside.id        = 'sidebar';
        aside.className = 'sidebar';
        aside.innerHTML = `<nav class="sidebar-nav"><ul id="menu-lista" class="sidebar-menu"></ul></nav>`;
        document.body.appendChild(aside);

        const backdrop = document.createElement('div');
        backdrop.id        = 'sidebar-backdrop';
        backdrop.className = 'sidebar-backdrop';
        document.body.appendChild(backdrop);

        document.body.classList.add('has-sidebar');
    }

    inicializarEventosHeader();
}

// Estado del sondeo (polling) de notificaciones: badge + toast de nuevas.
let ultimoConteoNoLeidas = null;    // null = aun no hay primera lectura
let intervaloNotificaciones = null;
const INTERVALO_NOTIS_MS = 25000;   // revisa cada 25 segundos

export async function iniciarEscuchaNotificaciones(uid) {
    currentUid = uid;
    const btnNoti = document.getElementById('btnNotificacion');
    if (!btnNoti || !uid) return;

    // Primera lectura: solo pinta el badge, SIN toast (no avisar de las que ya existian).
    await revisarNoLeidas(uid, false);

    // Polling: revisa periodicamente si llegaron nuevas y avisa con un toast.
    if (intervaloNotificaciones) clearInterval(intervaloNotificaciones);
    intervaloNotificaciones = setInterval(() => revisarNoLeidas(uid, true), INTERVALO_NOTIS_MS);
}

// Consulta el conteo de no leidas y actualiza el badge. Si avisar=true y el numero
// AUMENTO respecto a la ultima revision, muestra un toast con cuantas llegaron.
async function revisarNoLeidas(uid, avisar) {
    const btnNoti = document.getElementById('btnNotificacion');
    if (!btnNoti) return;
    try {
        const res = await notificacionesApi.noLeidas(uid);
        const noLeidas = res?.noLeidas ?? 0;

        btnNoti.classList.toggle('con-novedades', noLeidas > 0);

        if (avisar && ultimoConteoNoLeidas !== null && noLeidas > ultimoConteoNoLeidas) {
            const nuevas = noLeidas - ultimoConteoNoLeidas;
            mostrarToast(`Tienes ${nuevas} notificación${nuevas === 1 ? '' : 'es'} nueva${nuevas === 1 ? '' : 's'} 🔔`, 'aviso');
        }
        ultimoConteoNoLeidas = noLeidas;
    } catch (error) {
        console.warn("No se pudieron cargar notificaciones.", error);
        btnNoti.classList.remove('con-novedades');
    }
}

async function cargarYMostrarNotificaciones() {
    const lista = document.getElementById('listaNotificaciones');
    if (!currentUid || !lista) return;

    lista.innerHTML = '<li class="noti-vacio">Cargando...</li>';

    try {
        // listarPorUsuario devuelve respuesta paginada: { contenido: [...], ... }
        const respuesta = await notificacionesApi.listarPorUsuario(currentUid);
        const notificaciones = respuesta?.contenido ?? respuesta ?? [];

        if (!notificaciones.length) {
            lista.innerHTML = '<li class="noti-vacio">Sin notificaciones recientes</li>';
            return;
        }

        lista.innerHTML = notificaciones.map((noti) => {
            const fecha      = noti.createdAt || noti.created_at || noti.fecha;
            const fechaTexto = fecha ? new Date(fecha).toLocaleDateString("es-MX") : "";
            const claseNoLeido = !noti.leido ? 'sin-leer' : '';

            return `
            <li class="noti-item ${claseNoLeido}" id="noti-${noti.id}">
                <div class="noti-contenido">
                    <span class="noti-titulo">${escapeHtml(noti.titulo || "Aviso")}</span>
                    <span class="noti-mensaje">${escapeHtml(noti.mensaje || "")}</span>
                    <span class="noti-fecha">${escapeHtml(fechaTexto)}</span>
                </div>
                <button class="noti-btn-borrar" data-id="${noti.id}" title="Eliminar notificación" aria-label="Eliminar notificación">
                    <span class="material-symbols-outlined" style="font-size:16px;">close</span>
                </button>
            </li>`;
        }).join('');

        // Conecta los botones de borrar
        lista.querySelectorAll('.noti-btn-borrar').forEach(btn => {
            btn.addEventListener('click', async (e) => {
                e.stopPropagation(); // no cierra el menú
                const id = btn.dataset.id;
                try {
                    await notificacionesApi.eliminar(id);
                    document.getElementById(`noti-${id}`)?.remove();
                    // Si ya no quedan notificaciones muestra mensaje vacío
                    if (!lista.querySelector('.noti-item')) {
                        lista.innerHTML = '<li class="noti-vacio">Sin notificaciones recientes</li>';
                    }
                } catch {
                    // Si falla el borrado, no hace nada visible — no interrumpe al usuario
                }
            });
        });

        await notificacionesApi.marcarTodasLeidas(currentUid).catch(console.warn);
        document.getElementById('btnNotificacion')?.classList.remove('con-novedades');
        ultimoConteoNoLeidas = 0; // al abrir el panel se marcan leidas: resetea la base del toast
    } catch (error) {
        console.error("Error cargando notificaciones web:", error);
        lista.innerHTML = '<li class="noti-vacio">Error al cargar. Revisa la consola.</li>';
    }
}

function inicializarEventosHeader() {
    const sidebar  = document.getElementById('sidebar');
    const backdrop = document.getElementById('sidebar-backdrop');
    const toggleBtn = document.getElementById('sidebar-toggle');

    // ── Toggle sidebar (siempre visible en desktop; drawer en móvil) ──────
    const abrirSidebar  = () => { sidebar?.classList.add('sidebar-open');    backdrop?.classList.add('show'); };
    const cerrarSidebar = () => { sidebar?.classList.remove('sidebar-open'); backdrop?.classList.remove('show'); };

    toggleBtn?.addEventListener('click', (e) => {
        e.stopPropagation();
        sidebar?.classList.contains('sidebar-open') ? cerrarSidebar() : abrirSidebar();
    });

    backdrop?.addEventListener('click', cerrarSidebar);

    // En móvil, cierra el sidebar al navegar
    sidebar?.addEventListener('click', (e) => {
        if (e.target.closest('a') && window.innerWidth <= 768) cerrarSidebar();
    });

    // ── Menú de usuario (avatar) ──────────────────────────────────────────
    const menuUsuario = document.querySelector('.subusuario');
    const abrirUsuario = document.querySelector('.usuario');
    if (abrirUsuario && menuUsuario) {
        abrirUsuario.addEventListener('click', (e) => {
            e.stopPropagation();
            menuUsuario.classList.toggle('show');
            document.getElementById('menuNotificaciones')?.classList.remove('show');
        });
    }

    // ── Notificaciones ────────────────────────────────────────────────────
    const btnNoti  = document.getElementById('btnNotificacion');
    const menuNoti = document.getElementById('menuNotificaciones');

    if (btnNoti && menuNoti) {
        btnNoti.addEventListener('click', (e) => {
            e.stopPropagation();
            const abierto = menuNoti.classList.contains('show');
            menuUsuario?.classList.remove('show');
            if (abierto) {
                menuNoti.classList.remove('show');
            } else {
                menuNoti.classList.add('show');
                cargarYMostrarNotificaciones();
            }
        });
    }

    // ── Cierre global al hacer click fuera ────────────────────────────────
    document.addEventListener('click', (e) => {
        if (menuUsuario && !menuUsuario.contains(e.target) && !abrirUsuario?.contains(e.target))
            menuUsuario.classList.remove('show');
        if (menuNoti && !menuNoti.contains(e.target) && !btnNoti?.contains(e.target))
            menuNoti.classList.remove('show');
    });
}

function cargarFavicon() {
    let link = document.querySelector("link[rel~='icon']");
    if (!link) {
        link = document.createElement('link');
        link.rel = 'icon';
        document.head.appendChild(link);
    }
    link.href = assetUrl('assets/images/logofic.png');
    link.type = 'image/png';
}

export function actualizarMenu(esAdmin) {
    const menuLista = document.getElementById('menu-lista');
    if (!menuLista) return;

    // Pagina actual para resaltar el item activo
    const paginaActual = window.location.pathname.split('/').pop() || 'index.html';
    const activa = (page) => page === paginaActual;

    // Helpers para construir el menu. Cada enlace lleva icono y se marca si es la pagina actual.
    const item = (page, label, icono) =>
        `<li><a class="menu-subitem${activa(page) ? ' activo' : ''}" href="${pageUrl(page)}">
            <span class="material-symbols-outlined">${icono}</span>${label}
         </a></li>`;

    const directo = (page, label, icono) =>
        `<li class="menu-directo"><a class="${activa(page) ? 'activo' : ''}" href="${pageUrl(page)}">
            <span class="material-symbols-outlined">${icono}</span>${label}
         </a></li>`;

    // Un grupo arranca abierto si contiene la pagina actual
    const grupo = (titulo, icono, items, abierto = false) => `
        <li class="menu-grupo${abierto ? ' abierto' : ''}">
            <button type="button" class="menu-grupo-toggle">
                <span class="menu-grupo-titulo"><span class="material-symbols-outlined">${icono}</span>${titulo}</span>
                <span class="material-symbols-outlined menu-grupo-chevron">expand_more</span>
            </button>
            <ul class="menu-grupo-items">${items}</ul>
        </li>`;

    const algunaActiva = (...pages) => pages.some(p => activa(p));

    let html = '';
    html += directo('aulas.html', 'Inicio', 'home');

    if (esAdmin) {
        html += grupo('Reservas', 'event',
            item('dashboard.html', 'Dashboard', 'dashboard') +
            item('solicitudes.html', 'Solicitudes', 'inbox') +
            item('actividad-log.html', 'Actividad', 'history') +
            item('mis-reservas.html', 'Historial', 'event_note'),
            algunaActiva('dashboard.html', 'solicitudes.html', 'actividad-log.html', 'mis-reservas.html'));

        if (hasRole("SUPER_ADMIN") || isAdminCentral()) {
            html += `<li class="menu-seccion-titulo">Gestion</li>`;
            // Facultades es exclusivo de ADMIN_CENTRAL (igual que su tarjeta en Inicio).
            if (isAdminCentral()) html += directo('facultades.html', 'Facultades', 'apartment');
            html += directo('gestion-catalogos.html', 'Catálogos', 'folder');
            html += directo('personas.html', 'Usuarios', 'group');
            html += directo('asignar-rol.html', 'Modificar usuario', 'badge');
            html += directo('asignar-materias.html', 'Asignar materias', 'assignment_ind');
        }

        html += grupo('Mi cuenta', 'person',
            item('perfil.html', 'Mi perfil', 'account_circle') +
            item('reglamento.html', 'Reglamento', 'gavel') +
            item('configuraciones.html', 'Configuración', 'settings'),
            algunaActiva('perfil.html', 'reglamento.html', 'configuraciones.html'));
    } else {
        html += directo('mis-reservas.html', 'Mis reservas', 'event_note');

        html += grupo('Mi cuenta', 'person',
            item('perfil.html', 'Mi perfil', 'account_circle') +
            item('configuraciones.html', 'Configuración', 'settings'),
            algunaActiva('perfil.html', 'configuraciones.html'));

        html += grupo('Información', 'help',
            item('ayuda.html', 'Ayuda', 'help') +
            item('reglamento.html', 'Reglamento', 'gavel'),
            algunaActiva('ayuda.html', 'reglamento.html'));
    }

    html += `<li class="menu-directo menu-logout"><a href="#" id="logout"><span class="material-symbols-outlined">logout</span>Cerrar sesión</a></li>`;
    menuLista.innerHTML = html;

    // Acordeon: cada encabezado abre/cierra su seccion. stopPropagation evita que el clic
    // burbujee al .abrirmenu y cierre todo el menu.
    menuLista.querySelectorAll('.menu-grupo-toggle').forEach(btn => {
        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            btn.closest('.menu-grupo').classList.toggle('abierto');
        });
    });
}

export function cargarFooter() {
    const footerElement = document.querySelector('footer');
    if (footerElement) {
        const currentYear = new Date().getFullYear();
        footerElement.innerHTML = `Facultad de Informatica Culiacan - ${currentYear}`;
    }
}

// Escapa caracteres HTML para evitar XSS al usar innerHTML con datos del backend o del usuario.
// Usar siempre que se inserte texto de usuario/backend via innerHTML.
export function escapeHtml(str) {
    if (str === null || str === undefined) return "";
    return String(str)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

// tipo: 'exito' | 'error' | 'aviso' | '' (azul por defecto)
export function mostrarToast(mensaje, tipo = '') {
    let contenedor = document.querySelector('.toasts');
    if (!contenedor) {
        contenedor = document.createElement('div');
        contenedor.classList.add('toasts');
        document.body.appendChild(contenedor);
    }
    const noti = document.createElement('div');
    noti.classList.add('toast');
    if (tipo) noti.classList.add(`toast-${tipo}`);
    noti.innerText = mensaje;
    contenedor.appendChild(noti);
    setTimeout(() => {
        noti.style.opacity = '0';
        noti.style.transform = 'translateX(100%)';
        setTimeout(() => noti.remove(), 300);
    }, 3000);
}

export function pintarUsuarioActual() {
    const user = getCurrentUser();
    const userInfo = document.getElementById("userinfo");
    const preview = document.getElementById("preview");
    if (userInfo) userInfo.textContent = user?.email || "Invitado";
    if (preview) preview.src = user?.fotoUrl || localStorage.getItem("user_photo") || assetUrl("assets/images/usuario.png");
}
