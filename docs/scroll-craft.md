# Scroll-craft cinema

The public entrance is `/experience/`; authenticated Discover has the same live 3D reel. Scroll to rotate the reel and pass through its centre, or use Browse films/the collection to reach the catalogue directly. Mobile keeps the camera journey. Pause motion and the system reduced-motion preference return a stable composition.

The shared renderer source is `frontend/src/cinema/reel-world.js`. `npm run build` first bundles it with esbuild to `public/experience/reel-world.js`, then Angular copies the entrance and its local assets into the production output. `npm start` also prepares the renderer. Angular loads the module outside change detection and disposes it when leaving the feature. WebGL failure/context loss falls back to captured desktop/mobile images; the public entrance remains readable without JavaScript.

The unmodified upstream ScrollCraft engine runs once in the standalone entrance and animates the remaining film collection and genre sections. It is never repeatedly mounted in Angular because upstream has no teardown API. The shared Three.js renderer has its own complete lifecycle. Authentication destinations, genre choices, database-backed films and existing account features remain connected to their real APIs.

## Source and licensing

- Scroll-craft: https://github.com/nateherkai/scroll-craft at `0b816225945e45380397d6a0487efa3c98916858`.
- Complete skill: `.agents/skills/scroll-craft/`; unchanged runtime and MIT license: `frontend/public/experience/vendor/`.
- Three.js 0.186.0: https://threejs.org/ ; MIT license copied as `vendor/THREE-LICENSE` by the build script.
- Original artwork and asset provenance: `scrollcraft/builds/neo4flix/REPORT.md`.
- Design correction following the user's video: `scrollcraft/builds/neo4flix/REFERENCE-REVISION.md`.

Run `npm run test:e2e` from `frontend/` against the local Docker stack. See `validation.md` for verification and limits. Browser runtime resources are local and work under the existing CSP. HTTPS security was preserved.
