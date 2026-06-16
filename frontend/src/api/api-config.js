export const API_BASE_URL =
  window.AULAHUB_CONFIG?.API_BASE_URL ||
  localStorage.getItem("aulahub_api_base_url") ||
  "http://localhost:8080/api";
