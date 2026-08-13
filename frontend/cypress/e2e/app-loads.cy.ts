describe('Application bootstrap', () => {
  it('loads on / and renders the weekly menu page', () => {
    cy.visit('/');

    // Route par défaut : '' redirige vers 'menu' (voir app.routes.ts).
    cy.url().should('include', '/menu');
    cy.title().should('eq', 'KitchenVault');
    cy.get('nav[aria-label="Navigation principale"]').should('be.visible');
    cy.get('h1').should('contain.text', 'Menu de la semaine');
  });
});
