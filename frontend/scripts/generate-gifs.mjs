import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readdirSync, rmSync } from 'node:fs';
import { basename, join } from 'node:path';
import ffmpegPath from 'ffmpeg-static';

const videosDir = join(import.meta.dirname, '..', 'cypress', 'videos');
const outputDir = join(import.meta.dirname, '..', '..', 'docs', 'modules', 'ROOT', 'assets', 'images', 'e2e');

// Un test Cypress s'exécute en quelques secondes, bien trop vite pour être suivi à l'œil.
// Plutôt qu'un échantillonnage à fps fixe, on ne retient que les frames où l'image change
// significativement (détection de scène ffmpeg) et on les affiche chacune pendant FRAME_DELAY_SECONDS,
// ce qui donne un GIF "pas à pas" lisible quelle que soit la vitesse réelle du test.
const SCENE_THRESHOLD = 0.15;
const FRAME_DELAY_SECONDS = 0.7;
const GIF_WIDTH = 960;

const selectFilter = `select='eq(n,0)+gt(scene,${SCENE_THRESHOLD})',setpts=N/(${1 / FRAME_DELAY_SECONDS}*TB),scale=${GIF_WIDTH}:-1:flags=lanczos`;

if (!existsSync(videosDir)) {
  console.error(`Aucun dossier de vidéos trouvé: ${videosDir}`);
  console.error('Lancez d\'abord "npm run e2e:gif" avec le frontend démarré (npm start).');
  process.exit(1);
}

mkdirSync(outputDir, { recursive: true });

const videos = readdirSync(videosDir).filter((file) => file.endsWith('.mp4'));

if (videos.length === 0) {
  console.error(`Aucune vidéo .mp4 trouvée dans ${videosDir}`);
  process.exit(1);
}

for (const video of videos) {
  const specName = basename(video, '.cy.ts.mp4');
  const inputPath = join(videosDir, video);
  const palettePath = join(outputDir, `${specName}.palette.png`);
  const gifPath = join(outputDir, `${specName}.gif`);

  console.log(`Génération de ${specName}.gif...`);

  execFileSync(ffmpegPath, [
    '-y',
    '-i', inputPath,
    '-vf', `${selectFilter},palettegen=stats_mode=diff`,
    palettePath,
  ]);

  execFileSync(ffmpegPath, [
    '-y',
    '-i', inputPath,
    '-i', palettePath,
    '-lavfi', `${selectFilter}[x];[x][1:v]paletteuse=dither=bayer`,
    '-vsync', 'vfr',
    gifPath,
  ]);

  rmSync(palettePath);
}

console.log(`${videos.length} GIF(s) généré(s) dans ${outputDir}`);
