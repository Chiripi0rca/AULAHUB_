#!/bin/sh
# Genera /usr/share/nginx/html/config.js a partir de la variable API_BASE_URL.
# Lo corre la imagen oficial de nginx en cada arranque del contenedor, antes de
# iniciar el servidor. 
set -e

: "${API_BASE_URL:=http://localhost:8080/api}"

cat > /usr/share/nginx/html/config.js <<EOF
window.AULAHUB_CONFIG = {
    API_BASE_URL: "${API_BASE_URL}"
};
EOF

echo "[aulahub] config.js generado -> API_BASE_URL=${API_BASE_URL}"
