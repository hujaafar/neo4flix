import * as THREE from 'three';
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js';

const clamp = (n, a = 0, b = 1) => Math.max(a, Math.min(b, n));
const mix = (a, b, t) => a + (b - a) * t;
const ease = (t) => {
  t = clamp(t);
  return t * t * (3 - 2 * t);
};
const range = (p, a, b) => ease((p - a) / (b - a));

/** A real, disposable 3D cinema scene. Shared by the entrance and Angular. */
export function mountCinemaWorld(host, { target = '#collection' } = {}) {
  const stage = host.querySelector('.reel-stage');
  const surface = host.querySelector('.reel-surface');
  const pauseButton = host.querySelector('[data-reel-pause]');
  const skipButton = host.querySelector('[data-reel-skip]');
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  const fine = matchMedia('(hover: hover) and (pointer: fine)');
  let renderer, environment, pmrem, envTarget;
  let disposed = false,
    failed = false,
    visible = true,
    frame = 0,
    progress = 0,
    targetProgress = 0;
  let pointerX = 0,
    pointerY = 0,
    mouseX = 0,
    mouseY = 0,
    paused = reduced.matches;
  let width = 1,
    height = 1,
    phone = false,
    lastTime = 0,
    activeTime = 0,
    frames = 0;
  const born = performance.now();
  const geometries = new Set(),
    materials = new Set(),
    textures = new Set(),
    images = [];
  const geometry = (g) => (geometries.add(g), g);
  const material = (m) => (materials.add(m), m);
  const texture = (t) => (textures.add(t), t);
  const mode = () => {
    host.classList.toggle('reel-still', paused || failed);
    host.dataset.motion = failed ? 'fallback' : paused ? 'paused' : 'running';
    if (pauseButton) {
      pauseButton.disabled = failed;
      pauseButton.textContent = paused ? 'Play motion' : 'Pause motion';
      pauseButton.setAttribute('aria-pressed', String(paused));
    }
  };
  function fallBack() {
    failed = true;
    host.classList.remove('reel-live');
    host.classList.add('reel-fallback');
    host.style.setProperty('--reel-p', '0');
    cancelAnimationFrame(frame);
    frame = 0;
    mode();
  }
  try {
    renderer = new THREE.WebGLRenderer({
      alpha: true,
      antialias: true,
      powerPreference: 'high-performance',
      preserveDrawingBuffer: false,
    });
  } catch {
    fallBack();
    return {
      destroy() {
        disposed = true;
      },
    };
  }
  renderer.setClearColor(0x000000, 0);
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.12;
  renderer.domElement.setAttribute('aria-hidden', 'true');
  surface.append(renderer.domElement);
  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(38, 1, 0.04, 80);
  try {
    environment = new RoomEnvironment();
    pmrem = new THREE.PMREMGenerator(renderer);
    envTarget = pmrem.fromScene(environment, 0.035);
    scene.environment = envTarget.texture;
    scene.environmentIntensity = 1.25;
    environment.dispose();
    environment = null;
    pmrem.dispose();
    pmrem = null;
  } catch {
    /* Direct lighting remains a complete rendering path. */
  }
  const hemi = new THREE.HemisphereLight(0xe6eadd, 0x1b2526, 2.2);
  scene.add(hemi);
  const key = new THREE.DirectionalLight(0xfff3d7, 4.5);
  key.position.set(-4, 5, 7);
  scene.add(key);
  const rim = new THREE.DirectionalLight(0xcce9f1, 3.5);
  rim.position.set(5, 2, -3);
  scene.add(rim);
  const fill = new THREE.DirectionalLight(0xdce77e, 1.0);
  fill.position.set(-3, -3, 2);
  scene.add(fill);
  const reel = new THREE.Group();
  scene.add(reel);
  const silver = material(
    new THREE.MeshPhysicalMaterial({
      color: 0xc6cbbc,
      metalness: 0.96,
      roughness: 0.27,
      clearcoat: 0.4,
      clearcoatRoughness: 0.2,
    }),
  );
  const edge = material(
    new THREE.MeshStandardMaterial({ color: 0xf0f0d7, metalness: 1, roughness: 0.18 }),
  );
  const brass = material(
    new THREE.MeshStandardMaterial({ color: 0xc4ce80, metalness: 0.9, roughness: 0.28 }),
  );
  const carbon = material(
    new THREE.MeshStandardMaterial({ color: 0x151918, metalness: 0.38, roughness: 0.4 }),
  );
  const winding = material(
    new THREE.MeshStandardMaterial({ color: 0x656756, metalness: 0.68, roughness: 0.42 }),
  );
  function disc(radius, hole, depth, holes = false) {
    const shape = new THREE.Shape();
    shape.absarc(0, 0, radius, 0, Math.PI * 2, false);
    const centre = new THREE.Path();
    centre.absarc(0, 0, hole, 0, Math.PI * 2, true);
    shape.holes.push(centre);
    if (holes)
      for (let i = 0; i < 6; i++) {
        const angle = (i * Math.PI) / 3;
        const cut = new THREE.Path();
        cut.absarc(Math.cos(angle) * 1.2, Math.sin(angle) * 1.2, 0.49, 0, Math.PI * 2, true);
        shape.holes.push(cut);
      }
    return geometry(
      new THREE.ExtrudeGeometry(shape, {
        depth,
        bevelEnabled: true,
        bevelSegments: 3,
        steps: 1,
        bevelSize: 0.025,
        bevelThickness: 0.018,
        curveSegments: 40,
      }),
    );
  }
  const faceGeo = disc(2, 0.41, 0.065, true);
  for (const z of [-0.32, 0.255]) {
    const face = new THREE.Mesh(faceGeo, silver);
    face.position.z = z;
    reel.add(face);
  }
  const core = new THREE.Mesh(disc(1.92, 0.44, 0.49), carbon);
  core.position.z = -0.24;
  reel.add(core);
  const ringGeo = geometry(new THREE.TorusGeometry(1.97, 0.036, 10, 128));
  const axleGeo = geometry(new THREE.TorusGeometry(0.435, 0.045, 12, 72));
  for (const z of [-0.335, 0.335]) {
    const ring = new THREE.Mesh(ringGeo, edge);
    ring.position.z = z;
    reel.add(ring);
    const axle = new THREE.Mesh(axleGeo, brass);
    axle.position.z = z;
    reel.add(axle);
  }
  // Concentric film windings are visible through the six actual cutouts.
  for (let radius = 0.67; radius < 1.85; radius += 0.064) {
    const ring = new THREE.Mesh(geometry(new THREE.TorusGeometry(radius, 0.005, 3, 96)), winding);
    ring.position.z = 0.252;
    reel.add(ring);
  }
  const boltGeo = geometry(new THREE.CylinderGeometry(0.041, 0.041, 0.026, 6));
  for (let i = 0; i < 12; i++) {
    const a = (i * Math.PI) / 6;
    const bolt = new THREE.Mesh(boltGeo, brass);
    bolt.rotation.x = Math.PI / 2;
    bolt.position.set(Math.cos(a) * 1.82, Math.sin(a) * 1.82, 0.345);
    reel.add(bolt);
  }
  // The near film strip is a curved mesh, with real transparent sprocket holes.
  const filmCanvas = document.createElement('canvas');
  filmCanvas.width = 4096;
  filmCanvas.height = 512;
  const ctx = filmCanvas.getContext('2d');
  const filmTexture = texture(new THREE.CanvasTexture(filmCanvas));
  filmTexture.colorSpace = THREE.SRGBColorSpace;
  filmTexture.wrapS = THREE.RepeatWrapping;
  filmTexture.anisotropy = Math.min(renderer.capabilities.getMaxAnisotropy(), 4);
  const art = [
    'interstellar',
    'arrival',
    'dune',
    'blade-runner',
    'moonlight',
    'inception',
    'parasite',
    'spirited-away',
  ];
  function paintFilm() {
    ctx.clearRect(0, 0, 4096, 512);
    ctx.fillStyle = '#101712';
    ctx.fillRect(0, 0, 4096, 512);
    ctx.strokeStyle = '#b7bd81';
    ctx.lineWidth = 3;
    ctx.strokeRect(0, 64, 4096, 384);
    for (let x = 15; x < 4096; x += 58) {
      ctx.clearRect(x, 14, 28, 30);
      ctx.clearRect(x, 468, 28, 30);
    }
    for (let i = 0; i < 12; i++) {
      const x = i * 344 + 18,
        img = images[i % images.length];
      ctx.fillStyle = ['#31483f', '#716042', '#22383d'][i % 3];
      ctx.fillRect(x, 76, 308, 360);
      if (img?.complete && img.naturalWidth) ctx.drawImage(img, 0, 80, 400, 435, x, 76, 308, 360);
      ctx.strokeStyle = '#899779';
      ctx.lineWidth = 2;
      ctx.strokeRect(x, 76, 308, 360);
    }
    filmTexture.needsUpdate = true;
  }
  paintFilm();
  for (const id of art) {
    const img = new Image();
    images.push(img);
    img.onload = () => {
      if (!disposed) {
        paintFilm();
        wake();
      }
    };
    img.src = '/art/' + id + '.svg';
  }
  const filmCurve = new THREE.CatmullRomCurve3([
    new THREE.Vector3(-6, -1.5, -0.3),
    new THREE.Vector3(-3.7, -1.8, 1.4),
    new THREE.Vector3(-0.5, -1.75, 2.2),
    new THREE.Vector3(2.7, -1.0, 1.8),
    new THREE.Vector3(3.25, 1.3, 0.1),
    new THREE.Vector3(5.4, 2.6, -2.8),
  ]);
  const points = [],
    uv = [],
    indices = [];
  const segments = 160;
  for (let i = 0; i <= segments; i++) {
    const t = i / segments,
      c = filmCurve.getPoint(t),
      tangent = filmCurve.getTangent(t);
    const up = new THREE.Vector3(0, 1, 0).applyAxisAngle(tangent, Math.sin(t * Math.PI * 2) * 0.4);
    for (const side of [-1, 1]) {
      const v = c.clone().addScaledVector(up, side * 0.42);
      points.push(v.x, v.y, v.z);
      uv.push(t, side === -1 ? 0 : 1);
    }
    if (i < segments) {
      const n = i * 2;
      indices.push(n, n + 1, n + 2, n + 1, n + 3, n + 2);
    }
  }
  const filmGeo = geometry(new THREE.BufferGeometry());
  filmGeo.setAttribute('position', new THREE.Float32BufferAttribute(points, 3));
  filmGeo.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
  filmGeo.setIndex(indices);
  filmGeo.computeVertexNormals();
  const film = new THREE.Mesh(
    filmGeo,
    material(
      new THREE.MeshStandardMaterial({
        map: filmTexture,
        side: THREE.DoubleSide,
        roughness: 0.36,
        metalness: 0.15,
        alphaTest: 0.35,
      }),
    ),
  );
  scene.add(film);
  // Sparse atmosphere, in front of the independent landscape plate.
  const dustData = [];
  let seed = 81;
  const random = () => {
    seed = (seed * 16807) % 2147483647;
    return seed / 2147483647;
  };
  for (let i = 0; i < 190; i++)
    dustData.push((random() - 0.5) * 28, (random() - 0.5) * 15, -random() * 16 - 3);
  const dustGeo = geometry(new THREE.BufferGeometry());
  dustGeo.setAttribute('position', new THREE.Float32BufferAttribute(dustData, 3));
  const dust = new THREE.Points(
    dustGeo,
    material(
      new THREE.PointsMaterial({
        color: 0xe4e9ce,
        size: 0.024,
        transparent: true,
        opacity: 0.62,
        depthWrite: false,
      }),
    ),
  );
  scene.add(dust);

  function measure() {
    if (disposed) return;
    const box = stage.getBoundingClientRect();
    width = Math.max(box.width, 1);
    height = Math.max(box.height, 1);
    phone = width < 700;
    renderer.setPixelRatio(Math.min(devicePixelRatio || 1, phone ? 1.4 : 1.7));
    renderer.setSize(width, height);
    camera.aspect = width / height;
    camera.fov = phone ? 48 : 38;
    camera.updateProjectionMatrix();
    read();
    wake();
  }
  function read() {
    if (disposed) return;
    const r = host.getBoundingClientRect();
    targetProgress = paused ? 0 : clamp(-r.top / Math.max(1, r.height - height));
    if (visible) wake();
  }
  function paint(now) {
    frame = 0;
    if (disposed || failed || !visible || document.hidden) return;
    const dt = Math.min((now - (lastTime || now)) / 1000, 0.06);
    lastTime = now;
    if (!paused) activeTime += dt;
    progress = paused ? 0 : mix(progress, targetProgress, 1 - Math.exp(-dt * 10));
    if (Math.abs(progress - targetProgress) < 0.0001) progress = targetProgress;
    mouseX = mix(mouseX, paused ? 0 : pointerX, 0.09);
    mouseY = mix(mouseY, paused ? 0 : pointerY, 0.09);
    const p = progress,
      turn = range(p, 0.03, 0.5),
      centre = range(p, 0.25, 0.62),
      through = range(p, 0.58, 0.91);
    const reveal = paused ? 1 : ease((now - born) / 1400);
    const openingX = phone ? 0 : 2.05;
    reel.position.set(mix(openingX, 0, centre), mix(phone ? -1.05 : 0.0, 0, centre), 0);
    reel.rotation.set(
      mix(0.18, 0, through) + mouseY * 0.1 * (1 - through),
      mix(-0.55, -Math.PI * 2, turn) + mouseX * 0.17 * (1 - through),
      mix(-0.26, 0, through) + (paused ? 0 : Math.sin(activeTime * 0.24) * 0.025) * (1 - through),
    );
    reel.rotation.z += (!paused ? (1 - reveal) * -0.5 : 0) * (1 - through);
    reel.scale.setScalar(phone ? mix(0.86, 1, centre) : 1);
    camera.position.set(
      mouseX * 0.12 * (1 - through),
      phone ? mix(0.6, 0, centre) : 0,
      mix(phone ? 8.7 : 8.2, 0.12, through),
    );
    camera.lookAt(0, 0, 0);
    film.position.set(
      mix(0.7, 0, centre),
      mix(phone ? -0.8 : 0, -1.5, through),
      mix(-0.3, -2, through),
    );
    film.rotation.set(0, mix(-0.1, -0.4, turn), mix(-0.06, 0.2, turn));
    filmTexture.offset.x = paused ? 0 : activeTime * 0.006 + p * 0.42;
    film.visible = p < 0.91;
    dust.rotation.z = paused ? 0 : activeTime * 0.006;
    dust.position.z = through * 2;
    host.style.setProperty('--reel-p', p.toFixed(4));
    host.style.setProperty('--reel-enter', reveal.toFixed(4));
    host.style.setProperty('--reel-pointer-x', mouseX.toFixed(4));
    host.style.setProperty('--reel-pointer-y', mouseY.toFixed(4));
    // Preserve readable HTML when the large object passes through its plane.
    const first = host.querySelector('.reel-copy-first');
    const second = host.querySelector('.reel-copy-second');
    if (first) first.inert = p > 0.32;
    if (second) second.inert = p < 0.91;
    try {
      renderer.render(scene, camera);
    } catch {
      fallBack();
      return;
    }
    host.classList.add('reel-live');
    host.dataset.reelReady = 'true';
    host.dataset.reelFrames = String(++frames);
    host.dataset.scVerifyState = [
      reel.rotation.y.toFixed(2),
      camera.position.z.toFixed(2),
      filmTexture.offset.x.toFixed(2),
      p.toFixed(3),
    ].join(',');
    host.dataset.scVerifyHold = String(paused);
    if (!paused) frame = requestAnimationFrame(paint);
  }
  function wake() {
    if (!frame && !disposed && !failed && visible && !document.hidden)
      frame = requestAnimationFrame(paint);
  }
  function pointer(event) {
    if (!fine.matches || paused) return;
    const box = stage.getBoundingClientRect();
    pointerX = clamp((event.clientX - box.left) / box.width, 0, 1) * 2 - 1;
    pointerY = clamp((event.clientY - box.top) / box.height, 0, 1) * 2 - 1;
    wake();
  }
  function leave() {
    pointerX = 0;
    pointerY = 0;
    wake();
  }
  function toggle() {
    paused = !paused;
    host.classList.toggle('reel-user-play', !paused);
    progress = 0;
    mode();
    measure();
  }
  function motionChange() {
    paused = reduced.matches;
    host.classList.remove('reel-user-play');
    progress = 0;
    mode();
    measure();
  }
  function skip(event) {
    const destination = document.querySelector(target);
    if (destination) {
      event.preventDefault();
      destination.scrollIntoView({ behavior: 'instant', block: 'start' });
      destination.focus({ preventScroll: true });
    }
  }
  function visibility() {
    lastTime = 0;
    if (document.hidden) {
      cancelAnimationFrame(frame);
      frame = 0;
    } else wake();
  }
  function contextLost(event) {
    event.preventDefault();
    fallBack();
  }
  const observer = new IntersectionObserver(
    (entries) => {
      visible = entries.some((e) => e.isIntersecting);
      if (!visible) {
        cancelAnimationFrame(frame);
        frame = 0;
        lastTime = 0;
      } else {
        read();
        wake();
      }
    },
    { rootMargin: '80px' },
  );
  observer.observe(host);
  const resizeObserver = new ResizeObserver(measure);
  resizeObserver.observe(stage);
  window.addEventListener('scroll', read, { passive: true });
  stage.addEventListener('pointermove', pointer, { passive: true });
  stage.addEventListener('pointerleave', leave);
  document.addEventListener('visibilitychange', visibility);
  reduced.addEventListener('change', motionChange);
  renderer.domElement.addEventListener('webglcontextlost', contextLost);
  pauseButton?.addEventListener('click', toggle);
  skipButton?.addEventListener('click', skip);
  if (pauseButton) pauseButton.disabled = false;
  mode();
  measure();
  return {
    destroy() {
      if (disposed) return;
      disposed = true;
      cancelAnimationFrame(frame);
      observer.disconnect();
      resizeObserver.disconnect();
      window.removeEventListener('scroll', read);
      stage.removeEventListener('pointermove', pointer);
      stage.removeEventListener('pointerleave', leave);
      document.removeEventListener('visibilitychange', visibility);
      reduced.removeEventListener('change', motionChange);
      pauseButton?.removeEventListener('click', toggle);
      skipButton?.removeEventListener('click', skip);
      renderer.domElement.removeEventListener('webglcontextlost', contextLost);
      for (const img of images) img.onload = null;
      for (const g of geometries) g.dispose();
      for (const m of materials) m.dispose();
      for (const t of textures) t.dispose();
      envTarget?.dispose();
      environment?.dispose();
      pmrem?.dispose();
      renderer.dispose();
      renderer.forceContextLoss();
      renderer.domElement.remove();
      host.dataset.motion = 'disposed';
    },
  };
}
