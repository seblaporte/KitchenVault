import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readdirSync, rmSync } from 'node:fs';
import { basename, join } from 'node:path';
import ffmpegPath from 'ffmpeg-static';

const videosDir = join(import.meta.dirname, '..', 'cypress', 'videos');
const outputDir = join(import.meta.dirname, '..', '..', 'docs', 'modules', 'ROOT', 'assets', 'images', 'e2e');

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
    '-vf', 'fps=10,scale=960:-1:flags=lanczos,palettegen',
    palettePath,
  ]);

  execFileSync(ffmpegPath, [
    '-y',
    '-i', inputPath,
    '-i', palettePath,
    '-filter_complex', 'fps=10,scale=960:-1:flags=lanczos[x];[x][1:v]paletteuse',
    gifPath,
  ]);

  rmSync(palettePath);
}

console.log(`${videos.length} GIF(s) généré(s) dans ${outputDir}`);
