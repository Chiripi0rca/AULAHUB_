# ============================================================
#  Imagen del FRONTEND — Nginx sirviendo los archivos estaticos.
#  NO se copia config.js: se genera al arrancar el contenedor a
#  partir de la variable API_BASE_URL (ver deploy/30-genera-config.sh).
# ============================================================
FROM nginx:1.27-alpine

# Solo los archivos web .
COPY index.html /usr/share/nginx/html/index.html
COPY assets/ /usr/share/nginx/html/assets/
COPY pages/  /usr/share/nginx/html/pages/
COPY src/    /usr/share/nginx/html/src/

# Config de Nginx .
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf

# Script que genera config.js con la URL del backend al arrancar.
COPY deploy/30-genera-config.sh /docker-entrypoint.d/30-genera-config.sh
RUN sed -i 's/\r$//' /docker-entrypoint.d/30-genera-config.sh \
    && chmod +x /docker-entrypoint.d/30-genera-config.sh

EXPOSE 80
