import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import ffmpegPath from 'ffmpeg-static';
import sharp from 'sharp';

const screenshotsDir = join(import.meta.dirname, '..', 'cypress', 'screenshots');
const outputDir = join(import.meta.dirname, '..', '..', 'docs', 'modules', 'ROOT', 'assets', 'images', 'e2e');

// Chaque étape est un screenshot pris par Cypress lui-même à chaque commande d'action
// significative du test (voir cypress/support/e2e.ts, captureSteps). On assemble ces images
// en vidéo avec une durée fixe par frame, plutôt que d'inférer les moments intéressants
// depuis une vidéo continue (peu fiable : bruit d'animation, changements localisés manqués).
// Sortie en mp4 plutôt qu'en GIF : au-delà de ~10s un GIF n'apporte plus rien (pas de pause/
// retour arrière possible) et devient strictement inférieur à une vidéo avec lecteur natif.
const FRAME_DELAY_SECONDS = 0.9;
const FRAME_WIDTH = 960;
const SCENARIO_BAND_HEIGHT = 40;
const ACTION_BAND_HEIGHT = 30;
const HEADER_HEIGHT = SCENARIO_BAND_HEIGHT + ACTION_BAND_HEIGHT;
const HEADER_BACKGROUND = '#111827';
const MAX_TEXT_LENGTH = 100;
const CURSOR_SIZE = 24;

if (!existsSync(screenshotsDir)) {
  console.error(`Aucun dossier de screenshots trouvé: ${screenshotsDir}`);
  console.error('Lancez d\'abord "npm run e2e:video" avec le frontend démarré (npm start).');
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

function escapeXml(text) {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

function truncate(text) {
  return text.length > MAX_TEXT_LENGTH ? `${text.slice(0, MAX_TEXT_LENGTH - 1)}…` : text;
}

function headerSvg(scenario, action) {
  return Buffer.from(`
    <svg width="${FRAME_WIDTH}" height="${HEADER_HEIGHT}" xmlns="http://www.w3.org/2000/svg">
      <text x="16" y="${SCENARIO_BAND_HEIGHT / 2 + 6}" font-family="sans-serif" font-size="18" fill="#f9fafb">${escapeXml(truncate(scenario))}</text>
      <line x1="0" y1="${SCENARIO_BAND_HEIGHT}" x2="${FRAME_WIDTH}" y2="${SCENARIO_BAND_HEIGHT}" stroke="#374151" stroke-width="1" />
      <text x="16" y="${SCENARIO_BAND_HEIGHT + ACTION_BAND_HEIGHT / 2 + 5}" font-family="sans-serif" font-size="14" fill="#6ee7b7">${escapeXml(truncate(action))}</text>
    </svg>
  `);
}

function cursorSvg() {
  const r = CURSOR_SIZE / 2;
  return Buffer.from(`
    <svg width="${CURSOR_SIZE}" height="${CURSOR_SIZE}" xmlns="http://www.w3.org/2000/svg">
      <circle cx="${r}" cy="${r}" r="${r - 3}" fill="rgba(220,38,38,.55)" stroke="#fff" stroke-width="2" />
      <circle cx="${r}" cy="${r}" r="${r - 1}" fill="none" stroke="rgba(220,38,38,.9)" stroke-width="1.5" />
    </svg>
  `);
}

async function composeLabeledFrame(sourcePath, entry, destPath) {
  const native = await sharp(sourcePath).metadata();
  const scale = FRAME_WIDTH / native.width;

  const overlays = [{ input: headerSvg(entry.scenario, entry.action), top: 0, left: 0 }];
  if (entry.cursor) {
    const left = Math.round(entry.cursor.x * scale - CURSOR_SIZE / 2);
    const top = Math.round(entry.cursor.y * scale - CURSOR_SIZE / 2) + HEADER_HEIGHT;
    overlays.push({ input: cursorSvg(), top, left });
  }

  await sharp(sourcePath)
    .resize({ width: FRAME_WIDTH })
    .extend({ top: HEADER_HEIGHT, bottom: 0, left: 0, right: 0, background: HEADER_BACKGROUND })
    .composite(overlays)
    .toFile(destPath);
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

  const manifestPath = join(specPath, 'manifest.json');
  const manifest = existsSync(manifestPath) ? JSON.parse(readFileSync(manifestPath, 'utf8')) : [];
  const entryByFile = new Map(manifest.map((entry) => [entry.file, entry]));

  console.log(`Génération de ${specName}.mp4 (${steps.length} étapes)...`);

  const tempDir = mkdtempSync(join(tmpdir(), `kv-video-${specName}-`));
  const labeledPaths = [];
  for (const file of steps) {
    const entry = entryByFile.get(file) ?? { scenario: '', action: '' };
    const destPath = join(tempDir, file);
    await composeLabeledFrame(join(specPath, file), entry, destPath);
    labeledPaths.push(destPath);
  }

  const concatListPath = join(tempDir, 'concat.txt');
  const concatLines = labeledPaths.map((path) => `file '${path.replace(/'/g, "'\\''")}'\nduration ${FRAME_DELAY_SECONDS}`);
  // Quirk ffmpeg concat demuxer : la durée de la dernière entrée est ignorée, il faut répéter le fichier.
  concatLines.push(`file '${labeledPaths[labeledPaths.length - 1].replace(/'/g, "'\\''")}'`);
  writeFileSync(concatListPath, concatLines.join('\n'));

  const videoPath = join(outputDir, `${specName}.mp4`);

  execFileSync(ffmpegPath, [
    '-y',
    '-f', 'concat', '-safe', '0', '-i', concatListPath,
    '-vsync', 'vfr',
    '-frames:v', String(labeledPaths.length), // le concat demuxer répète le dernier fichier pour lui donner sa durée, sans que ça compte comme une frame en plus
    // yuv420p exige des dimensions paires, quelle que soit la hauteur produite par sharp
    '-vf', 'crop=trunc(iw/2)*2:trunc(ih/2)*2',
    '-pix_fmt', 'yuv420p',
    '-c:v', 'libx264',
    '-crf', '23',
    '-movflags', '+faststart',
    videoPath,
  ]);

  rmSync(tempDir, { recursive: true });
}

console.log(`${specDirs.length} vidéo(s) généré(s) dans ${outputDir}`);
