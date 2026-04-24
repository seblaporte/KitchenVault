describe('Navigation principale', () => {
  beforeEach(() => {
    cy.intercept('GET', '**/api/v1/admin/stats', { fixture: 'admin-stats.json' });
    cy.intercept('GET', '**/api/v1/sync/latest', { statusCode: 404 });
    cy.intercept('GET', '**/api/v1/collections', { fixture: 'collections.json' });
    cy.intercept('GET', '**/api/v1/categories', { fixture: 'categories.json' });
    cy.intercept('GET', '**/api/v1/recipes*', { fixture: 'recipes-page.json' });
    cy.intercept('GET', '**/api/v1/menu-plan*', { fixture: 'week-plan-empty.json' });
  });

  it('accède à la page Recettes depuis la nav', () => {
    cy.visit('/');
    cy.get('nav[aria-label="Navigation principale"]').contains('Recettes').click();
    cy.url().should('include', '/recipes');
    cy.get('h1').should('contain.text', 'Recettes');
  });

  it('accède à la page Menu depuis la nav', () => {
    cy.visit('/');
    cy.get('nav[aria-label="Navigation principale"]').contains('Menu').click();
    cy.url().should('include', '/menu');
    cy.get('h1').should('contain.text', 'Menu de la semaine');
  });

  it('revient à Administration depuis Recettes', () => {
    cy.visit('/recipes');
    cy.get('nav[aria-label="Navigation principale"]').contains('Administration').click();
    cy.url().should('include', '/admin');
    cy.get('h1').should('contain.text', 'Administration');
  });
});
