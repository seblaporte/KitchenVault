describe('Menu de la semaine', () => {
  beforeEach(() => {
    // Intercept spécifique au plan hebdomadaire (pathname exact, pas suggestions ni history)
    cy.intercept({ method: 'GET', pathname: '/api/v1/menu-plan' }, { fixture: 'week-plan-empty.json' }).as('weekPlan');
  });

  it('affiche les 7 jours avec les créneaux vides', () => {
    cy.visit('/menu');
    cy.wait('@weekPlan');

    cy.get('h1').should('contain.text', 'Menu de la semaine');
    cy.get('[aria-label="Ajouter une recette"]').should('have.length', 14);
    cy.get('[aria-label="Suggérer des recettes"]').should('have.length', 14);
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

    cy.get('[aria-label="Ajouter une recette"]').first().click();
    cy.get('[role="dialog"][aria-label="Sélectionner une recette"]').should('be.visible');

    cy.get('[aria-label="Fermer"]').click();
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
    cy.get('[aria-label="Ajouter une recette"]').first().click();
    cy.get('[role="dialog"]').should('be.visible');

    // Rechercher et sélectionner une recette dans la dialog
    cy.get('[role="dialog"] [aria-label="Rechercher une recette"]').type('Poulet');
    cy.wait('@pickerSearch');
    cy.get('[role="dialog"]').contains('Poulet rôti').click();

    // Vérifier que le créneau est rempli après rechargement
    cy.wait('@upsert');
    cy.get('[role="dialog"]').should('not.exist');
    cy.wait('@weekPlanUpdated');
    cy.contains('Poulet rôti').should('be.visible');
    cy.get('[aria-label="Supprimer ce repas"]').should('have.length.at.least', 1);
  });

  it('suggère des recettes pour un créneau et en accepte une', () => {
    cy.intercept(
      { method: 'GET', pathname: '/api/v1/menu-plan' },
      { fixture: 'week-plan-partial.json' },
    ).as('weekPlanUpdated');
    cy.intercept(
      { method: 'GET', pathname: '/api/v1/menu-plan', times: 1 },
      { fixture: 'week-plan-empty.json' },
    ).as('weekPlanInitial');

    cy.intercept('GET', '**/api/v1/menu-plan/suggestions*', { fixture: 'suggestions.json' }).as('suggestions');
    cy.intercept('PUT', '**/api/v1/menu-plan/entries/**', {
      statusCode: 200,
      body: { id: 10, recipeId: 'recipe-1', recipeName: 'Poulet rôti', recipeTotalTimeMinutes: 60 },
    }).as('upsert');

    cy.visit('/menu');
    cy.wait('@weekPlanInitial');

    cy.get('[aria-label="Suggérer des recettes"]').first().click();
    cy.wait('@suggestions');

    cy.contains('Poulet rôti').should('be.visible');
    cy.contains('Tarte aux pommes').should('be.visible');

    cy.get('[aria-label="Ajouter cette suggestion"]').first().click();
    cy.wait('@upsert');
    cy.wait('@weekPlanUpdated');
    cy.contains('Poulet rôti').should('be.visible');
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

    cy.get('[aria-label*="Suggérer des recettes pour tous"]').click();
    cy.wait('@weekPlanReloaded');

    cy.get('[aria-label="Supprimer ce repas"]').should('have.length.at.least', 1);
  });
});
