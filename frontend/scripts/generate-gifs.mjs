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
// en GIF avec une durée fixe par frame, plutôt que d'inférer les moments intéressants
// depuis une vidéo continue (peu fiable : bruit d'animation, changements localisés manqués).
const FRAME_DELAY_SECONDS = 0.9;
const GIF_WIDTH = 960;
const BANNER_HEIGHT = 44;
const BANNER_BACKGROUND = '#111827';
const MAX_SCENARIO_LENGTH = 110;

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

function escapeXml(text) {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}

function truncate(text) {
  return text.length > MAX_SCENARIO_LENGTH ? `${text.slice(0, MAX_SCENARIO_LENGTH - 1)}…` : text;
}

async function composeLabeledFrame(sourcePath, scenario, destPath) {
  const resized = sharp(sourcePath).resize({ width: GIF_WIDTH });
  const { height } = await resized.metadata();
  const banner = Buffer.from(`
    <svg width="${GIF_WIDTH}" height="${BANNER_HEIGHT}" xmlns="http://www.w3.org/2000/svg">
      <text x="16" y="${BANNER_HEIGHT / 2 + 6}" font-family="sans-serif" font-size="18" fill="#f9fafb">${escapeXml(truncate(scenario))}</text>
    </svg>
  `);

  await resized
    .extend({ top: BANNER_HEIGHT, bottom: 0, left: 0, right: 0, background: BANNER_BACKGROUND })
    .composite([{ input: banner, top: 0, left: 0 }])
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
  const scenarioByFile = new Map(manifest.map((entry) => [entry.file, entry.scenario]));

  console.log(`Génération de ${specName}.gif (${steps.length} étapes)...`);

  const tempDir = mkdtempSync(join(tmpdir(), `kv-gif-${specName}-`));
  const labeledPaths = [];
  for (const file of steps) {
    const scenario = scenarioByFile.get(file) ?? '';
    const destPath = join(tempDir, file);
    await composeLabeledFrame(join(specPath, file), scenario, destPath);
    labeledPaths.push(destPath);
  }

  const concatListPath = join(tempDir, 'concat.txt');
  const concatLines = labeledPaths.map((path) => `file '${path.replace(/'/g, "'\\''")}'\nduration ${FRAME_DELAY_SECONDS}`);
  // Quirk ffmpeg concat demuxer : la durée de la dernière entrée est ignorée, il faut répéter le fichier.
  concatLines.push(`file '${labeledPaths[labeledPaths.length - 1].replace(/'/g, "'\\''")}'`);
  writeFileSync(concatListPath, concatLines.join('\n'));

  const palettePath = join(tempDir, 'palette.png');
  const gifPath = join(outputDir, `${specName}.gif`);

  const inputArgs = ['-f', 'concat', '-safe', '0', '-i', concatListPath];

  execFileSync(ffmpegPath, [
    '-y',
    ...inputArgs,
    '-vf', 'palettegen=stats_mode=diff',
    '-update', '1',
    '-frames:v', '1',
    palettePath,
  ]);

  execFileSync(ffmpegPath, [
    '-y',
    ...inputArgs,
    '-i', palettePath,
    '-lavfi', '[0:v][1:v]paletteuse=dither=bayer',
    '-vsync', 'vfr',
    '-frames:v', String(labeledPaths.length), // le concat demuxer répète le dernier fichier pour lui donner sa durée, sans que ça compte comme une frame en plus
    gifPath,
  ]);

  rmSync(tempDir, { recursive: true });
}

console.log(`${specDirs.length} GIF(s) généré(s) dans ${outputDir}`);
