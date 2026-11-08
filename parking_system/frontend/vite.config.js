import process from "node:process";
import react from "@vitejs/plugin-react";
import { defineConfig, loadEnv } from "vite";
import tailwindcss from "@tailwindcss/vite";

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  return {
    plugins: [react(), tailwindcss()],
    server: {
      port: Number(env.PORT ?? 5172),
      strictPort: true,
      watch: {
        usePolling: env.VITE_USE_POLLING === "true",
      },
      proxy: {
        "/api": {
          target: env.API_PROXY_TARGET ?? "http://localhost:8082",
          changeOrigin: true,
        },
      },
    },
  };
});
