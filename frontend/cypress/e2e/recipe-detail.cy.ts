describe('Détail d\'une recette', () => {
  beforeEach(() => {
    cy.intercept('GET', '**/api/v1/recipes/recipe-1', { fixture: 'recipe-detail.json' }).as('recipeDetail');
    cy.intercept('GET', '**/api/v1/menu-plan/history*', { body: { recipeId: 'recipe-1', dates: [] } });
  });

  it('affiche les informations de la recette', () => {
    cy.visit('/recipes/recipe-1');
    cy.wait('@recipeDetail');

    cy.get('h1').should('contain.text', 'Poulet rôti');
    cy.contains('60 min au total').should('be.visible');
    cy.contains('20 min actives').should('be.visible');
    cy.contains('4 personnes').should('be.visible');
    cy.contains('Facile').should('be.visible');
    cy.contains('Poulet entier').should('be.visible');
    cy.contains('Beurre').should('be.visible');
  });

  it('retourne à la liste en cliquant sur "Retour"', () => {
    cy.intercept('GET', '**/api/v1/collections', { fixture: 'collections.json' });
    cy.intercept('GET', '**/api/v1/categories', { fixture: 'categories.json' });
    cy.intercept('GET', '**/api/v1/recipes*', { fixture: 'recipes-page.json' });

    cy.visit('/recipes/recipe-1');
    cy.wait('@recipeDetail');

    cy.get('[aria-label="Retour à la liste des recettes"]').click();
    cy.url().should('match', /\/recipes$/);
  });

  it('affiche une erreur pour une recette introuvable', () => {
    cy.intercept('GET', '**/api/v1/recipes/recipe-404', { statusCode: 404 });
    cy.intercept('GET', '**/api/v1/menu-plan/history*', { body: { recipeId: 'recipe-404', dates: [] } });

    cy.visit('/recipes/recipe-404');

    cy.get('[role="alert"]').should('be.visible');
  });
});
