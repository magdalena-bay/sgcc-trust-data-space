import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  server: {
    host: process.env.VITE_DEV_HOST ?? "127.0.0.1",
    port: Number(process.env.VITE_DEV_PORT ?? 5173),
    strictPort: true
  },
  preview: {
    host: process.env.VITE_PREVIEW_HOST ?? "127.0.0.1",
    port: Number(process.env.VITE_PREVIEW_PORT ?? 4173),
    strictPort: true
  }
});
