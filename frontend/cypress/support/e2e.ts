import './commands';

// Commandes qui ciblent un élément précis : on y superpose un indicateur visuel
// (Cypress ne pilote pas de vrai curseur système en headless, les clics sont des
// événements DOM synthétiques — voir cypress-io/cypress#22764, aucune solution native
// ou plugin maintenu n'existe pour visualiser ça, d'où l'injection DOM ci-dessous).
const POINTER_COMMANDS = new Set(['click', 'dblclick', 'rightclick', 'check', 'uncheck', 'trigger']);
const CHILD_ACTION_COMMANDS = [
  'click', 'dblclick', 'rightclick', 'type', 'clear',
  'check', 'uncheck', 'select', 'trigger', 'submit',
] as const;

let stepCounter = 0; // portée module : incrémente sur toute la durée du spec (tous les it() confondus)
let currentScenario = '';
const manifest: { file: string; scenario: string }[] = [];

function captureStep() {
  stepCounter += 1;
  const step = `step-${String(stepCounter).padStart(3, '0')}`;
  manifest.push({ file: `${step}.png`, scenario: currentScenario });
  cy.screenshot(step, { capture: 'viewport', overwrite: true });
}

function showFakeCursorOn(subject: JQuery | undefined) {
  if (!subject?.length) return;
  const el = subject[0] as HTMLElement;
  const rect = el.getBoundingClientRect();
  const cursor = el.ownerDocument.createElement('div');
  cursor.setAttribute('data-cy-fake-cursor', '');
  cursor.style.cssText = `position:fixed;left:${rect.left + rect.width / 2 - 9}px;top:${rect.top + rect.height / 2 - 9}px;width:18px;height:18px;border-radius:50%;background:rgba(220,38,38,.55);border:2px solid #fff;box-shadow:0 0 0 2px rgba(220,38,38,.9);z-index:2147483647;pointer-events:none;`;
  el.ownerDocument.body.appendChild(cursor);
}

if (Cypress.env('captureSteps')) {
  beforeEach(function () {
    currentScenario = this.currentTest?.fullTitle() ?? '';
  });

  // La capture doit faire partie de la chaîne de la commande elle-même : la déclencher
  // depuis l'événement `command:end` l'enverrait en réalité en fin de queue (le corps du
  // test empile déjà TOUTES ses commandes de façon synchrone avant qu'aucune ne s'exécute),
  // donc systématiquement après l'état final du test plutôt que juste après chaque action.
  // On surcharge donc directement chaque commande d'action pour chaîner la capture dans
  // son propre .then(), avant que Cypress n'avance à la commande suivante déjà empilée.
  // Cypress interdit de renvoyer la chaîne de la commande d'origine dès lors qu'on invoque
  // d'autres commandes cy à l'intérieur (erreur "invoked cy commands but returned a
  // different value") : il faut terminer par cy.wrap() du sujet voulu, qui devient alors
  // la dernière commande invoquée et donc la valeur renvoyée à la chaîne appelante.
  CHILD_ACTION_COMMANDS.forEach((name) => {
    Cypress.Commands.overwrite(name, (originalFn, subject, ...args) => {
      if (POINTER_COMMANDS.has(name)) showFakeCursorOn(subject);
      originalFn(subject, ...args);
      captureStep();
      // La suppression doit elle aussi être mise en queue (via cy.then), sans quoi elle
      // s'exécute en JS synchrone immédiatement — donc AVANT que cy.screenshot() n'ait
      // réellement capturé les pixels — et le curseur n'apparaîtrait jamais sur l'image.
      if (POINTER_COMMANDS.has(name) && subject?.length) {
        const doc = (subject[0] as HTMLElement).ownerDocument;
        cy.then(() => {
          doc.querySelector('[data-cy-fake-cursor]')?.remove();
        });
      }
      return cy.wrap(subject, { log: false });
    });
  });

  Cypress.Commands.overwrite('visit', (originalFn, url, options) => {
    originalFn(url, options);
    captureStep();
    return cy.window({ log: false });
  });

  afterEach(() => {
    captureStep();
  });

  after(() => {
    cy.writeFile(`cypress/screenshots/${Cypress.spec.name}/manifest.json`, manifest);
  });
}
