describe('Recettes — mode navigation', () => {
  beforeEach(() => {
    cy.intercept('GET', '**/api/v1/collections', { fixture: 'collections.json' }).as('collections');
    cy.intercept('GET', '**/api/v1/categories', { fixture: 'categories.json' });
    cy.intercept('GET', '**/api/v1/recipes*', { fixture: 'recipes-page.json' });
  });

  it('affiche les collections et leurs recettes', () => {
    cy.visit('/recipes');
    cy.wait('@collections');

    cy.get('h1').should('contain.text', 'Recettes');
    cy.contains('Plats du quotidien').should('be.visible');
    cy.contains('Desserts & Pâtisseries').should('be.visible');
    cy.get('[aria-label="Poulet rôti"]').should('be.visible');
    cy.get('[aria-label="Tarte aux pommes"]').should('be.visible');
  });

  it('navigue vers le détail d\'une recette au clic sur une tuile', () => {
    cy.intercept('GET', '**/api/v1/recipes/recipe-1', { fixture: 'recipe-detail.json' });
    cy.intercept('GET', '**/api/v1/menu-plan/history*', { body: { recipeId: 'recipe-1', dates: [] } });

    cy.visit('/recipes');
    cy.wait('@collections');

    cy.get('[aria-label="Poulet rôti"]').first().click();
    cy.url().should('include', '/recipes/recipe-1');
  });

  it('affiche un message quand aucune recette n\'est disponible', () => {
    cy.intercept('GET', '**/api/v1/collections', { body: [] });

    cy.visit('/recipes');
    cy.contains('Aucune recette disponible').should('be.visible');
    cy.contains('Lancez une synchronisation').should('be.visible');
  });
});
