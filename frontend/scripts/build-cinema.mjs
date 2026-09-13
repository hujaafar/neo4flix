import { build } from 'esbuild';
import { copyFile, mkdir } from 'node:fs/promises';
await mkdir('public/experience/vendor', { recursive: true });
await build({
  entryPoints: ['src/cinema/reel-world.js'],
  outfile: 'public/experience/reel-world.js',
  bundle: true,
  format: 'esm',
  platform: 'browser',
  target: ['es2022'],
  minify: true,
  legalComments: 'eof',
});
await copyFile('node_modules/three/LICENSE', 'public/experience/vendor/THREE-LICENSE');
console.log('Built the shared cinema renderer.');
