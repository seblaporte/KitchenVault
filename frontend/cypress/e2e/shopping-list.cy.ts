describe('Page Courses — export email', () => {
  beforeEach(() => {
    cy.intercept('GET', '**/api/v1/shopping-list', { fixture: 'shopping-list-consolidated.json' }).as('shoppingList');
    cy.visit('/shopping');
    cy.wait('@shoppingList');
  });

  it('affiche le bouton email sur la liste consolidée', () => {
    cy.contains('button', 'Consolidée').click();
    cy.get('[data-testid="email-export-btn"]').should('be.visible');
  });

  it('le lien email contient la liste formatée', () => {
    cy.contains('button', 'Consolidée').click();
    cy.get('[data-testid="email-export-btn"]')
      .should('have.attr', 'href')
      .and('include', 'mailto:')
      .and('include', 'Liste%20de%20courses')
      .and('include', 'Carottes')
      .and('include', 'Poulet');
  });

  it("n'affiche pas le bouton email quand la liste est vide", () => {
    cy.intercept('GET', '**/api/v1/shopping-list', { body: { recipes: [], items: [] } }).as('emptyList');
    cy.visit('/shopping');
    cy.wait('@emptyList');
    cy.contains('button', 'Consolidée').click();
    cy.get('[data-testid="email-export-btn"]').should('not.exist');
  });
});
