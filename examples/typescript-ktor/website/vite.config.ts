import {defineConfig} from 'vite'
import path from 'path'

export default defineConfig({
    root: 'src',
    build: {
        outDir: '../build',
        emptyOutDir: true,
        sourcemap: true,
        minify: 'esbuild', // Use esbuild for minification (default)
        assetsDir: 'assets',
        rollupOptions: {
            input: path.resolve(__dirname, 'src/index.html'),
            output: {
                assetFileNames: 'assets/[name]-[hash][extname]' // Hashing for cache busting
            }
        }
    },
    resolve: {
        alias: {
            '@': path.resolve(__dirname, 'src'), // Optional alias for cleaner imports
        }
    },
    define: {
        'process.env.NODE_ENV': '"production"' // Inject environment variables
    },
    server: {
        proxy: {
            '/rpc': {
                target: 'http://localhost:8080'
            }
        }
    }
})
