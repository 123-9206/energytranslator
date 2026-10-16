import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig(({ mode }) => {
  // This prefix is used only by the dev server, not exposed to browser code.
  const env = loadEnv(mode, process.cwd(), 'DEV_')
  return {
    plugins: [
      vue(),
      AutoImport({ resolvers: [ElementPlusResolver()], imports: ['vue', 'vue-router', 'pinia'], dts: false }),
      Components({ resolvers: [ElementPlusResolver()], dts: false }),
    ],
    server: {
      host: '127.0.0.1',
      port: 5173,
      strictPort: true,
      proxy: {
        '/api': { target: env.DEV_API_PROXY_TARGET || 'http://127.0.0.1:8080', changeOrigin: true },
      },
    },
    base: '/admin/',
    build: { outDir: 'dist', assetsDir: 'assets' },
  }
})
