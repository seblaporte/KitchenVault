describe('Mes listes', () => {
  beforeEach(() => {
    cy.intercept('GET', '**/api/v1/recipe-lists', { fixture: 'recipe-lists-overview.json' }).as('overview');
  });

  it('affiche les 3 listes avec leurs libellés configurés', () => {
    cy.visit('/lists');
    cy.wait('@overview');

    cy.get('h1').should('contain.text', 'Mes listes');
    // Les libellés viennent de la fixture, jamais codés en dur dans l'UI ni le test.
    cy.contains('Nos favoris').should('be.visible');
    cy.contains('Miam').should('be.visible');
    cy.contains('Pas ouf').should('be.visible');

    cy.contains('Poulet rôti').should('be.visible');
    cy.contains('Boeuf bourguignon').should('be.visible');
  });

  it('affiche un message quand une liste est vide', () => {
    cy.visit('/lists');
    cy.wait('@overview');

    cy.contains('Aucune recette dans cette liste pour le moment.').should('be.visible');
  });

  it('déplace une recette vers une autre liste', () => {
    cy.intercept('PUT', '**/api/v1/recipes/recipe-2/list-membership', {
      body: { role: 'FAVORITES' },
    }).as('move');
    cy.intercept('GET', '**/api/v1/recipe-lists', { fixture: 'recipe-lists-overview.json', times: 1 }).as(
      'overviewInitial',
    );

    cy.visit('/lists');
    cy.wait('@overviewInitial');

    cy.intercept('GET', '**/api/v1/recipe-lists', {
      body: [
        { role: 'FAVORITES', displayLabel: 'Nos favoris', recipes: [
          { id: 'recipe-1', name: 'Poulet rôti', totalTimeMinutes: 60 },
          { id: 'recipe-2', name: 'Boeuf bourguignon', totalTimeMinutes: 120 },
        ] },
        { role: 'DISCOVERY', displayLabel: 'Miam', recipes: [] },
        { role: 'REJECTED', displayLabel: 'Pas ouf', recipes: [] },
      ],
    }).as('overviewAfterMove');

    cy.contains('div', 'Boeuf bourguignon')
      .find('select')
      .select('FAVORITES', { force: true });

    cy.wait('@move');
    cy.wait('@overviewAfterMove');

    cy.get('[role="status"]').should('contain.text', 'Recette déplacée');
  });

  it('affiche une erreur si le déplacement échoue', () => {
    cy.intercept('PUT', '**/api/v1/recipes/recipe-2/list-membership', { statusCode: 422 }).as('moveFailed');

    cy.visit('/lists');
    cy.wait('@overview');

    cy.contains('div', 'Boeuf bourguignon')
      .find('select')
      .select('FAVORITES', { force: true });

    cy.wait('@moveFailed');
    cy.get('[role="status"]').should('contain.text', 'Échec du déplacement');
  });

  it('le bouton "Faire le bilan hebdomadaire" navigue vers l\'écran de bilan', () => {
    cy.visit('/lists');
    cy.wait('@overview');

    cy.intercept('GET', '**/api/v1/weekly-reviews/*', { fixture: 'weekly-review.json' });
    cy.contains('a', 'Faire le bilan hebdomadaire').click();

    cy.url().should('include', '/lists/weekly-review');
  });
});
