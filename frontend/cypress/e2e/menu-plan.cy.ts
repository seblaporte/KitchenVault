describe('Menu de la semaine', () => {
  beforeEach(() => {
    // Intercept spécifique au plan hebdomadaire (pathname exact, pas suggestions ni history)
    cy.intercept({ method: 'GET', pathname: '/api/v1/menu-plan' }, { fixture: 'week-plan-empty.json' }).as('weekPlan');
  });

  it('affiche les 7 jours avec les créneaux vides', () => {
    cy.visit('/menu');
    cy.wait('@weekPlan');

    cy.get('h1').should('contain.text', 'Menu de la semaine');
    // La vue mobile (masquée en CSS à ce viewport) et la vue desktop coexistent dans le DOM ;
    // on scope sur la grille desktop pour ne compter qu'une occurrence par créneau.
    cy.get('[data-testid="desktop-calendar"] [aria-label^="Ajouter une recette pour"]').should('have.length', 14);
    cy.get('[data-testid="desktop-calendar"] [aria-label^="Suggérer une recette IA pour"]').should('have.length', 14);
  });

  it('charge la semaine précédente à la navigation', () => {
    cy.visit('/menu');
    cy.wait('@weekPlan');

    cy.intercept({ method: 'GET', pathname: '/api/v1/menu-plan' }, { fixture: 'week-plan-empty.json' }).as('prevWeekPlan');
    cy.get('[aria-label="Semaine précédente"]').click();
    cy.wait('@prevWeekPlan');

    cy.get('h1').should('contain.text', 'Menu de la semaine');
  });

  it('ouvre la dialog de sélection puis la ferme', () => {
    cy.visit('/menu');
    cy.wait('@weekPlan');

    cy.get('[data-testid="desktop-calendar"] [aria-label^="Ajouter une recette pour"]').first().click();
    cy.get('[role="dialog"][aria-label="Sélectionner une recette"]').should('be.visible');

    // "Fermer" existe aussi (masqué) sur le drawer de planification IA, toujours dans le DOM :
    // on scope sur la dialog ouverte pour cibler le bon bouton.
    cy.get('[role="dialog"] [aria-label="Fermer"]').click();
    cy.get('[role="dialog"]').should('not.exist');
  });

  it('ajoute une recette via la dialog de sélection', () => {
    // Le second appel à getWeekPlan (après upsert) doit retourner le plan partiel.
    // On utilise times:1 pour que le premier appel retourne le plan vide,
    // et le suivant utilise l'intercept enregistré juste avant (LIFO).
    cy.intercept(
      { method: 'GET', pathname: '/api/v1/menu-plan' },
      { fixture: 'week-plan-partial.json' },
    ).as('weekPlanUpdated');
    cy.intercept(
      { method: 'GET', pathname: '/api/v1/menu-plan', times: 1 },
      { fixture: 'week-plan-empty.json' },
    ).as('weekPlanInitial');

    cy.intercept('GET', '**/api/v1/recipes*', { fixture: 'recipes-picker.json' }).as('pickerSearch');
    cy.intercept('PUT', '**/api/v1/menu-plan/entries/**', {
      statusCode: 200,
      body: { id: 1, recipeId: 'recipe-1', recipeName: 'Poulet rôti', recipeTotalTimeMinutes: 60 },
    }).as('upsert');

    cy.visit('/menu');
    cy.wait('@weekPlanInitial');

    // Ouvrir le picker sur le premier créneau vide
    cy.get('[data-testid="desktop-calendar"] [aria-label^="Ajouter une recette pour"]').first().click();
    cy.get('[role="dialog"]').should('be.visible');

    // Rechercher et sélectionner une recette dans la dialog
    cy.get('[role="dialog"] [aria-label="Rechercher une recette"]').type('Poulet');
    cy.wait('@pickerSearch');
    cy.get('[role="dialog"]').contains('Poulet rôti').click();

    // Vérifier que le créneau est rempli après rechargement
    cy.wait('@upsert');
    cy.get('[role="dialog"]').should('not.exist');
    cy.wait('@weekPlanUpdated');
    cy.get('[data-testid="desktop-calendar"]').contains('Poulet rôti').should('be.visible');
    cy.get('[aria-label="Supprimer ce repas"]').should('have.length.at.least', 1);
  });

  it('suggère une recette via l\'assistant IA pour un créneau et applique la suggestion', () => {
    // La suggestion par créneau passe désormais par l'assistant IA conversationnel
    // (modale de chat), plus par un simple GET /suggestions affiché en liste.
    cy.intercept(
      { method: 'GET', pathname: '/api/v1/menu-plan' },
      { fixture: 'week-plan-partial.json' },
    ).as('weekPlanUpdated');
    cy.intercept(
      { method: 'GET', pathname: '/api/v1/menu-plan', times: 1 },
      { fixture: 'week-plan-empty.json' },
    ).as('weekPlanInitial');

    cy.intercept('POST', '**/api/v1/chat/recipe', {
      statusCode: 200,
      body: {
        reply: 'Je vous suggère ce plat.',
        suggestions: [{ id: 'recipe-1', name: 'Poulet rôti', totalTimeMinutes: 60 }],
      },
    }).as('chat');
    cy.intercept('PUT', '**/api/v1/menu-plan/entries/**', {
      statusCode: 200,
      body: { id: 10, recipeId: 'recipe-1', recipeName: 'Poulet rôti', recipeTotalTimeMinutes: 60 },
    }).as('upsert');

    cy.visit('/menu');
    cy.wait('@weekPlanInitial');

    cy.get('[data-testid="desktop-calendar"] [aria-label^="Suggérer une recette IA pour"]').first().click();
    cy.get('[role="dialog"][aria-label="Assistant IA culinaire"]').should('be.visible');

    cy.get('[aria-label="Envoyer"]').click();
    cy.wait('@chat');

    cy.contains('Poulet rôti').should('be.visible');
    cy.contains('button', 'Appliquer la suggestion').click();

    cy.wait('@upsert');
    cy.get('[role="dialog"]').should('not.exist');
    cy.wait('@weekPlanUpdated');
    cy.get('[data-testid="desktop-calendar"]').contains('Poulet rôti').should('be.visible');
  });

  it('suggère des recettes pour toute la semaine vide', () => {
    cy.intercept('GET', '**/api/v1/menu-plan/suggestions*', { fixture: 'suggestions.json' });
    cy.intercept('PUT', '**/api/v1/menu-plan/entries/**', {
      statusCode: 200,
      body: { id: 1, recipeId: 'recipe-1', recipeName: 'Poulet rôti', recipeTotalTimeMinutes: 60 },
    });
    cy.intercept(
      { method: 'GET', pathname: '/api/v1/menu-plan' },
      { fixture: 'week-plan-partial.json' },
    ).as('weekPlanReloaded');
    cy.intercept(
      { method: 'GET', pathname: '/api/v1/menu-plan', times: 1 },
      { fixture: 'week-plan-empty.json' },
    ).as('weekPlanInitial');

    cy.visit('/menu');
    cy.wait('@weekPlanInitial');

    cy.get('[aria-label="Suggérer des recettes aléatoires pour la semaine"]').click();
    cy.wait('@weekPlanReloaded');

    cy.get('[aria-label="Supprimer ce repas"]').should('have.length.at.least', 1);
  });

  it('déplace une recette planifiée vers un créneau vide', () => {
    // LIFO : l'intercept illimité doit être enregistré avant celui à times:1,
    // qui prendra alors la priorité sur le premier appel uniquement.
    cy.intercept(
      { method: 'GET', pathname: '/api/v1/menu-plan' },
      { fixture: 'week-plan-moved.json' },
    ).as('weekPlanUpdated');
    cy.intercept(
      { method: 'GET', pathname: '/api/v1/menu-plan', times: 1 },
      { fixture: 'week-plan-two-recipes.json' },
    ).as('weekPlanInitial');
    cy.intercept('PUT', '**/api/v1/menu-plan/entries/**', {
      statusCode: 200,
      body: { id: 1, recipeId: 'recipe-1', recipeName: 'Poulet rôti', recipeTotalTimeMinutes: 60 },
    }).as('upsert');
    cy.intercept('DELETE', '**/api/v1/menu-plan/entries/**', { statusCode: 204 }).as('remove');

    cy.visit('/menu');
    cy.wait('@weekPlanInitial');

    // On scope sur la grille desktop (vue mobile masquée en CSS à ce viewport mais toujours
    // dans le DOM). Le bouton "Déplacer" n'est visible qu'au survol (opacity-0 par défaut) :
    // on force le clic plutôt que de simuler un hover CSS sur le parent .group.
    cy.get('[data-testid="desktop-calendar"] [aria-label="Déplacer cette recette"]').first().click({ force: true });
    cy.contains('Naviguez entre les semaines puis cliquez le créneau cible').should('be.visible');

    // Premier créneau vide "Dîner" du planning = le dîner du même jour que la recette tenue.
    cy.get('[data-testid="desktop-calendar"] [aria-label*="Déplacer la recette ici — Dîner"]').first().click();

    cy.wait('@upsert').its('request.url').should('include', '/api/v1/menu-plan/entries/2026-04-21/DINNER');
    cy.wait('@remove').its('request.url').should('include', '/api/v1/menu-plan/entries/2026-04-21/LUNCH');
    cy.wait('@weekPlanUpdated');

    cy.contains('Recette déplacée').should('be.visible');
    cy.contains('Naviguez entre les semaines puis cliquez le créneau cible').should('not.exist');
  });

  it('échange deux recettes planifiées en déplaçant l\'une vers le créneau de l\'autre', () => {
    cy.intercept(
      { method: 'GET', pathname: '/api/v1/menu-plan' },
      { fixture: 'week-plan-swapped.json' },
    ).as('weekPlanUpdated');
    cy.intercept(
      { method: 'GET', pathname: '/api/v1/menu-plan', times: 1 },
      { fixture: 'week-plan-two-recipes.json' },
    ).as('weekPlanInitial');
    cy.intercept('POST', '**/api/v1/menu-plan/entries/bulk', { statusCode: 200, body: [] }).as('bulkUpsert');

    cy.visit('/menu');
    cy.wait('@weekPlanInitial');

    cy.get('[data-testid="desktop-calendar"] [aria-label="Déplacer cette recette"]').first().click({ force: true });
    cy.get('[data-testid="desktop-calendar"] [aria-label="Échanger avec Tarte aux pommes"]').first().click();

    cy.wait('@bulkUpsert').its('request.body.entries').should('deep.equal', [
      { date: '2026-04-22', mealType: 'LUNCH', recipeId: 'recipe-1' },
      { date: '2026-04-21', mealType: 'LUNCH', recipeId: 'recipe-3' },
    ]);
    cy.wait('@weekPlanUpdated');

    cy.contains('Recettes échangées').should('be.visible');
  });
});
