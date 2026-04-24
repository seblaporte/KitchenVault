describe('Recettes — mode recherche et filtres', () => {
  beforeEach(() => {
    cy.intercept('GET', '**/api/v1/collections', { fixture: 'collections.json' }).as('collections');
    cy.intercept('GET', '**/api/v1/categories', { fixture: 'categories.json' });
    cy.intercept('GET', '**/api/v1/recipes*', { fixture: 'recipes-page.json' }).as('recipes');

    cy.visit('/recipes');
    cy.wait('@collections');
  });

  it('passe en mode recherche au saisie et affiche les résultats', () => {
    cy.get('[aria-label="Rechercher une recette"]').type('Poulet');
    cy.wait('@recipes');

    cy.contains('3 recettes trouvées').should('be.visible');
    cy.get('[aria-label="Poulet rôti"]').should('be.visible');
  });

  it('revient au mode navigation en effaçant la recherche', () => {
    cy.get('[aria-label="Rechercher une recette"]').type('Poulet');
    cy.wait('@recipes');

    cy.get('[aria-label="Effacer la recherche"]').click();

    cy.contains('Plats du quotidien').should('be.visible');
    cy.contains('3 recettes trouvées').should('not.exist');
  });

  it('filtre par collection et indique le filtre actif', () => {
    cy.get('[aria-label="Filtrer par collection"]')
      .contains('button', 'Plats du quotidien')
      .click();
    cy.wait('@recipes');

    cy.get('[aria-label="Filtrer par collection"]')
      .contains('button', 'Plats du quotidien')
      .should('have.attr', 'aria-pressed', 'true');

    cy.contains('1 filtre actif').should('be.visible');
  });

  it('filtre par temps total', () => {
    cy.get('[aria-label="Filtrer par temps total"]')
      .contains('button', '≤ 20 min')
      .click();
    cy.wait('@recipes');

    cy.get('[aria-label="Filtrer par temps total"]')
      .contains('button', '≤ 20 min')
      .should('have.attr', 'aria-pressed', 'true');
  });

  it('réinitialise tous les filtres avec "Tout effacer"', () => {
    // Activer 2 filtres
    cy.get('[aria-label="Filtrer par collection"]')
      .contains('button', 'Plats du quotidien')
      .click();
    cy.wait('@recipes');
    cy.get('[aria-label="Filtrer par temps total"]')
      .contains('button', '≤ 40 min')
      .click();
    cy.wait('@recipes');

    cy.contains('2 filtres actifs').should('be.visible');

    cy.contains('button', 'Tout effacer').click();

    // Retour au mode navigation : collections visibles, compteur effacé
    cy.contains('Plats du quotidien').should('be.visible');
    cy.contains('filtres actifs').should('not.exist');
  });
});
