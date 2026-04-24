describe('Application bootstrap', () => {
  it('loads on /admin and renders the administration page', () => {
    cy.visit('/');

    cy.url().should('include', '/admin');
    cy.title().should('eq', 'KitchenVault');
    cy.get('nav[aria-label="Navigation principale"]').should('be.visible');
    cy.get('h1').should('contain.text', 'Administration');
  });
});
