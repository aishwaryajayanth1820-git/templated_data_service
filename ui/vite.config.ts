import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// Dev server proxies the API to Spring Boot (ADR-0009); `mvn -Pui package` bundles dist/ into the jar.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  build: {
    rolldownOptions: {
      output: {
        // Framework code changes rarely: Mantine and the other libraries get their own long-cached chunks.
        codeSplitting: {
          groups: [
            { name: 'mantine', test: /[\\/]node_modules[\\/]@mantine[\\/]/ },
            { name: 'vendor', test: /[\\/]node_modules[\\/]/ },
          ],
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/setupTests.ts'],
    css: false,
  },
})
