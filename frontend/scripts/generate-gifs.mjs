import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import ffmpegPath from 'ffmpeg-static';

const screenshotsDir = join(import.meta.dirname, '..', 'cypress', 'screenshots');
const outputDir = join(import.meta.dirname, '..', '..', 'docs', 'modules', 'ROOT', 'assets', 'images', 'e2e');

// Chaque étape est un screenshot pris par Cypress lui-même à chaque commande d'action
// significative du test (voir cypress/support/e2e.ts, captureSteps). On assemble ces images
// en GIF avec une durée fixe par frame, plutôt que d'inférer les moments intéressants
// depuis une vidéo continue (peu fiable : bruit d'animation, changements localisés manqués).
const FRAME_DELAY_SECONDS = 0.9;
const GIF_WIDTH = 960;

if (!existsSync(screenshotsDir)) {
  console.error(`Aucun dossier de screenshots trouvé: ${screenshotsDir}`);
  console.error('Lancez d\'abord "npm run e2e:gif" avec le frontend démarré (npm start).');
  process.exit(1);
}

mkdirSync(outputDir, { recursive: true });

const specDirs = readdirSync(screenshotsDir, { withFileTypes: true })
  .filter((entry) => entry.isDirectory())
  .map((entry) => entry.name);

if (specDirs.length === 0) {
  console.error(`Aucun dossier de spec trouvé dans ${screenshotsDir}`);
  process.exit(1);
}

for (const specDir of specDirs) {
  const specName = specDir.replace(/\.cy\.ts$/, '');
  const specPath = join(screenshotsDir, specDir);

  // Numérotation potentiellement non-contiguë (un screenshot injecté peut ne pas s'exécuter
  // si la commande précédente échoue et interrompt la queue Cypress) : on liste les fichiers
  // réellement présents plutôt que de s'appuyer sur un pattern %03d qui s'arrêterait au premier trou.
  const steps = readdirSync(specPath)
    .filter((file) => /^step-\d+\.png$/.test(file))
    .sort();

  if (steps.length === 0) {
    console.warn(`Aucun screenshot d'étape trouvé pour ${specDir}, ignoré.`);
    continue;
  }

  const concatListPath = join(outputDir, `${specName}.concat.txt`);
  const concatLines = steps.map((file) => {
    const absPath = join(specPath, file).replace(/'/g, "'\\''");
    return `file '${absPath}'\nduration ${FRAME_DELAY_SECONDS}`;
  });
  // Quirk ffmpeg concat demuxer : la durée de la dernière entrée est ignorée, il faut répéter le fichier.
  const lastAbsPath = join(specPath, steps[steps.length - 1]).replace(/'/g, "'\\''");
  concatLines.push(`file '${lastAbsPath}'`);
  writeFileSync(concatListPath, concatLines.join('\n'));

  const palettePath = join(outputDir, `${specName}.palette.png`);
  const gifPath = join(outputDir, `${specName}.gif`);

  console.log(`Génération de ${specName}.gif (${steps.length} étapes)...`);

  const inputArgs = ['-f', 'concat', '-safe', '0', '-i', concatListPath];

  execFileSync(ffmpegPath, [
    '-y',
    ...inputArgs,
    '-vf', `scale=${GIF_WIDTH}:-1:flags=lanczos,palettegen=stats_mode=diff`,
    palettePath,
  ]);

  execFileSync(ffmpegPath, [
    '-y',
    ...inputArgs,
    '-i', palettePath,
    '-lavfi', `scale=${GIF_WIDTH}:-1:flags=lanczos[x];[x][1:v]paletteuse=dither=bayer`,
    '-vsync', 'vfr',
    '-frames:v', String(steps.length), // le concat demuxer répète le dernier fichier pour lui donner sa durée, sans que ça compte comme une frame en plus
    gifPath,
  ]);

  rmSync(palettePath);
  rmSync(concatListPath);
}

console.log(`${specDirs.length} GIF(s) généré(s) dans ${outputDir}`);
