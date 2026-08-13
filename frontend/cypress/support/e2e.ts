import './commands';

const ACTION_COMMANDS = new Set([
  'click', 'dblclick', 'rightclick', 'type', 'clear',
  'check', 'uncheck', 'select', 'trigger', 'submit', 'visit',
]);

let stepCounter = 0; // portée module : incrémente sur toute la durée du spec (tous les it() confondus)

if (Cypress.env('captureSteps')) {
  Cypress.on('command:end', (command) => {
    if (!ACTION_COMMANDS.has(command.get('name'))) return;
    stepCounter += 1;
    cy.screenshot(`step-${String(stepCounter).padStart(3, '0')}`, { capture: 'viewport', overwrite: true });
  });

  afterEach(() => {
    stepCounter += 1;
    cy.screenshot(`step-${String(stepCounter).padStart(3, '0')}`, { capture: 'viewport', overwrite: true });
  });
}
