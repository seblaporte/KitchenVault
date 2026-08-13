import './commands';

// Commandes qui ciblent un élément précis : on y superpose un repère visuel
// (Cypress ne pilote pas de vrai curseur système en headless, les clics sont des
// événements DOM synthétiques — voir cypress-io/cypress#22764, aucune solution native
// ou plugin maintenu n'existe pour visualiser ça). La position est calculée puis stockée
// dans le manifest pour être dessinée en post-traitement (voir generate-videos.mjs),
// plutôt qu'injectée dans le DOM de l'appli testée.
const POINTER_COMMANDS = new Set(['click', 'dblclick', 'rightclick', 'check', 'uncheck', 'trigger']);
const CHILD_ACTION_COMMANDS = [
  'click', 'dblclick', 'rightclick', 'type', 'clear',
  'check', 'uncheck', 'select', 'trigger', 'submit',
] as const;

interface ManifestEntry {
  file: string;
  scenario: string;
  action: string;
  cursor?: { x: number; y: number };
}

let stepCounter = 0; // portée module : incrémente sur toute la durée du spec (tous les it() confondus)
let currentScenario = '';
const manifest: ManifestEntry[] = [];

function targetLabel(subject: JQuery | undefined): string {
  if (!subject?.length) return '';
  return (
    subject.attr('aria-label') ||
    subject.attr('placeholder') ||
    subject.attr('name') ||
    subject.attr('data-testid') ||
    // Dernier recours seulement : sur un gros conteneur (ex. cy.trigger sur toute une
    // zone de swipe), .text() concatène tout le texte descendant sans séparateur —
    // illisible. On ne l'utilise que si le résultat ressemble à du texte normal (espaces).
    (subject.text()?.trim().includes(' ') ? subject.text().trim().slice(0, 40) : '') ||
    ''
  );
}

function describeAction(name: string, subject: JQuery | undefined, args: unknown[]): string {
  const label = targetLabel(subject);
  switch (name) {
    case 'click': return label ? `Clic : ${label}` : 'Clic';
    case 'dblclick': return label ? `Double-clic : ${label}` : 'Double-clic';
    case 'rightclick': return label ? `Clic droit : ${label}` : 'Clic droit';
    case 'type': return `Saisie${label ? ` (${label})` : ''} : « ${String(args[0]).slice(0, 40)} »`;
    case 'clear': return `Effacement${label ? ` : ${label}` : ''}`;
    case 'check': return `Coche${label ? ` : ${label}` : ''}`;
    case 'uncheck': return `Décoche${label ? ` : ${label}` : ''}`;
    case 'select': return `Sélection${label ? ` : ${label}` : ''} → ${args[0]}`;
    case 'submit': return 'Envoi du formulaire';
    default: return `${name}${label ? ` : ${label}` : ''}`;
  }
}

function captureStep(action: string, cursor?: { x: number; y: number }) {
  stepCounter += 1;
  const step = `step-${String(stepCounter).padStart(3, '0')}`;
  manifest.push({ file: `${step}.png`, scenario: currentScenario, action, cursor });
  cy.screenshot(step, { capture: 'viewport', overwrite: true });
}

if (Cypress.env('captureSteps')) {
  beforeEach(function () {
    currentScenario = this.currentTest?.fullTitle() ?? '';
  });

  // La capture doit faire partie de la chaîne de la commande elle-même : la déclencher
  // depuis l'événement `command:end` l'enverrait en réalité en fin de queue (le corps du
  // test empile déjà TOUTES ses commandes de façon synchrone avant qu'aucune ne s'exécute),
  // donc systématiquement après l'état final du test plutôt que juste après chaque action.
  // On surcharge donc directement chaque commande d'action.
  // Cypress interdit de renvoyer la chaîne de la commande d'origine dès lors qu'on invoque
  // d'autres commandes cy à l'intérieur (erreur "invoked cy commands but returned a
  // different value") : il faut terminer par cy.wrap() du sujet voulu, qui devient alors
  // la dernière commande invoquée et donc la valeur renvoyée à la chaîne appelante.
  CHILD_ACTION_COMMANDS.forEach((name) => {
    Cypress.Commands.overwrite(name, (originalFn, subject, ...args) => {
      // Calculé AVANT l'appel à originalFn : l'élément est garanti attaché au DOM à cet
      // instant (après, une navigation déclenchée par le clic peut déjà l'avoir détaché).
      const cursor = POINTER_COMMANDS.has(name) && subject?.length
        ? (() => {
          const rect = (subject[0] as HTMLElement).getBoundingClientRect();
          return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 };
        })()
        : undefined;

      originalFn(subject, ...args);
      captureStep(describeAction(name, subject, args), cursor);
      return cy.wrap(subject, { log: false });
    });
  });

  // Pas de capture sur `visit` : la commande se termine au chargement de la page, avant
  // qu'Angular n'ait eu le temps de rendre le contenu (souvent en attente d'un cy.wait()
  // sur un intercept juste après) — l'image serait presque toujours blanche. L'état réel
  // de la page est de toute façon capturé par la prochaine action ou par le afterEach.

  afterEach(() => {
    captureStep('→ Résultat');
  });

  after(() => {
    cy.writeFile(`cypress/screenshots/${Cypress.spec.name}/manifest.json`, manifest);
  });
}
