import { login, logout } from "./api/auth.js";
import { getCurrentUser, isAdminUser, isAuthenticated } from "./api/auth-session.js";
import { usuariosApi } from "./api/usuarios.js";
import { actualizarMenu, assetUrl, cargarFooter, cargarHeader, iniciarEscuchaNotificaciones, mostrarToast, pageUrl, pintarUsuarioActual } from "./layout.js";
import { protegerRuta, redirigirSiAutenticado } from "./route-guard.js";
import { iniciarPush } from "./push.js";

// Guard: se ejecuta al cargar el modulo, antes del DOMContentLoaded.
const enLoginPage = Boolean(document.getElementById("login-email"));
if (enLoginPage) {
    redirigirSiAutenticado();
} else {
    protegerRuta();
}

// Evita que ENTER en cualquier formulario recargue la página.
// La lógica de envío vive en los listeners de click/keypress de cada página.
document.addEventListener("submit", (e) => e.preventDefault());

// Script comun: arranca login, header, footer, sesion y acciones del usuario.
document.addEventListener("DOMContentLoaded", async () => {
    await cargarHeader();
    cargarFooter();

    const loginBtn = document.getElementById("login");
    const emailInput = document.getElementById("login-email");
    const passInput = document.getElementById("login-password");

    if (loginBtn && emailInput && passInput) {
        const realizarLogin = async () => {
            const email = emailInput.value;
            const pass = passInput.value;
            if (!email || !pass) return mostrarToast("Ingresa correo y contraseña.", 'aviso');

            loginBtn.innerText = "Entrando...";
            loginBtn.disabled = true;

            try {
                await login(email, pass);
                window.location.href = pageUrl("aulas.html");
            } catch (error) {
                mostrarToast("Error: " + error.message, 'error');
                loginBtn.innerText = "Entrar";
                loginBtn.disabled = false;
            }
        };

        loginBtn.addEventListener("click", realizarLogin);
        const verificarEnter = (e) => { if (e.key === "Enter") realizarLogin(); };
        emailInput.addEventListener("keypress", verificarEnter);
        passInput.addEventListener("keypress", verificarEnter);
    }

    const user = getCurrentUser();
    const configEmail = document.getElementById("user-email");
    if (configEmail) configEmail.textContent = user?.email || "No conectado";

    actualizarMenu(isAdminUser());
    pintarUsuarioActual();
    activarLogout();

    if (user?.id) iniciarEscuchaNotificaciones(user.id);
    if (user?.id) iniciarPush(); // registra el dispositivo para push (solo en la app movil)
    aplicarRestricciones(isAdminUser());
    configurarFotoPerfilLocal();

});

function activarLogout() {
    const btnLogout = document.getElementById("logout");
    if (!btnLogout) return;

    const newBtn = btnLogout.cloneNode(true);
    btnLogout.parentNode?.replaceChild(newBtn, btnLogout);

    newBtn.addEventListener("click", async (e) => {
        e.preventDefault();
        await logout();
        window.location.replace(pageUrl("index.html"));
    });
}

function configurarFotoPerfilLocal() {
    const fileInput = document.getElementById('fileInput');
    const btnSubir  = document.getElementById('btnSubirFoto');
    const btnBorrar = document.getElementById('btnBorrarFoto');
    const imgPreview = document.getElementById('preview');

    btnSubir?.addEventListener('click', () => fileInput?.click());

    fileInput?.addEventListener('change', async (event) => {
        const file = event.target.files?.[0];
        if (!file || !imgPreview) return;

        // Preview inmediato mientras sube
        const reader = new FileReader();
        reader.onload = () => { imgPreview.src = reader.result; };
        reader.readAsDataURL(file);

        try {
            const res = await usuariosApi.uploadFoto(file);
            const fotoUrl = res?.fotoUrl || res?.url;
            if (fotoUrl) {
                localStorage.setItem("user_photo", fotoUrl);
                imgPreview.src = fotoUrl;
            }
            mostrarToast("Foto actualizada correctamente.", 'exito');
        } catch (error) {
            mostrarToast("No se pudo subir la foto: " + error.message, 'error');
        }
    });

    btnBorrar?.addEventListener('click', () => {
        localStorage.removeItem("user_photo");
        if (imgPreview) imgPreview.src = assetUrl("assets/images/usuario.png");
    });
}

function aplicarRestricciones() {
    const cards = document.querySelectorAll(".aula-list .card");
    cards.forEach((card) => card.style.visibility = "visible");
}
