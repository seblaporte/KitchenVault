describe('Bilan hebdomadaire', () => {
  it('se déclenche uniquement après un clic explicite depuis Mes listes (pas de bannière automatique)', () => {
    cy.intercept('GET', '**/api/v1/recipe-lists', { fixture: 'recipe-lists-overview.json' }).as('overview');

    cy.visit('/lists');
    cy.wait('@overview');

    // Aucun appel au bilan tant que l'utilisateur n'a pas cliqué explicitement.
    cy.get('@overview.all').should('have.length', 1);
    cy.contains('a', 'Faire le bilan hebdomadaire').should('be.visible');
  });

  it('affiche les recettes à découvrir planifiées la semaine dernière', () => {
    cy.intercept('GET', '**/api/v1/weekly-reviews/*', { fixture: 'weekly-review.json' }).as('review');

    cy.visit('/lists/weekly-review');
    cy.wait('@review');

    cy.get('h1').should('contain.text', 'Bilan de la semaine');
    cy.contains('Boeuf bourguignon').should('be.visible');
    cy.contains('Tarte aux pommes').should('be.visible');
    cy.contains('Planifiée 2 fois cette semaine-là').should('be.visible');
  });

  it('affiche un message quand aucune recette à découvrir n\'était planifiée', () => {
    cy.intercept('GET', '**/api/v1/weekly-reviews/*', { body: { weekStart: '2026-04-27', items: [] } }).as('review');

    cy.visit('/lists/weekly-review');
    cy.wait('@review');

    cy.contains('Aucune recette à découvrir n\'était planifiée la semaine dernière.').should('be.visible');
  });

  it('vote pouce haut et pouce bas puis valide le bilan', () => {
    cy.intercept('GET', '**/api/v1/weekly-reviews/*', { fixture: 'weekly-review.json' }).as('review');
    cy.intercept('POST', '**/api/v1/weekly-reviews/*/votes', {
      body: { moved: [
        { recipeId: 'recipe-2', newRole: 'FAVORITES' },
        { recipeId: 'recipe-3', newRole: 'REJECTED' },
      ], failed: [] },
    }).as('submit');

    cy.visit('/lists/weekly-review');
    cy.wait('@review');

    // Le bouton de validation est désactivé tant qu'aucun vote n'est enregistré.
    cy.contains('button', 'Valider mes votes').should('be.disabled');

    cy.get('[aria-label="Pouce vers le haut pour Boeuf bourguignon"]').click();
    cy.get('[aria-label="Pouce vers le bas pour Tarte aux pommes"]').click();

    cy.contains('button', 'Valider mes votes (2)').should('not.be.disabled').click();

    cy.wait('@submit').its('request.body').should('deep.equal', {
      votes: [
        { recipeId: 'recipe-2', vote: 'UP' },
        { recipeId: 'recipe-3', vote: 'DOWN' },
      ],
    });

    cy.url().should('include', '/lists').and('not.include', '/weekly-review');
  });

  it('un échec partiel n\'empêche pas la confirmation des autres votes', () => {
    cy.intercept('GET', '**/api/v1/weekly-reviews/*', { fixture: 'weekly-review.json' }).as('review');
    cy.intercept('POST', '**/api/v1/weekly-reviews/*/votes', { fixture: 'weekly-review-result.json' }).as('submit');
    cy.intercept('GET', '**/api/v1/recipe-lists', { fixture: 'recipe-lists-overview.json' });

    cy.visit('/lists/weekly-review');
    cy.wait('@review');

    cy.get('[aria-label="Pouce vers le haut pour Boeuf bourguignon"]').click();
    cy.get('[aria-label="Pouce vers le bas pour Tarte aux pommes"]').click();
    cy.contains('button', 'Valider mes votes (2)').click();

    cy.wait('@submit');
    cy.get('[role="status"]').should('contain.text', 'Bilan partiellement enregistré');
  });

  it('variante mobile : cibles tactiles suffisantes et pas de scroll horizontal', () => {
    cy.viewport(390, 844);
    cy.intercept('GET', '**/api/v1/weekly-reviews/*', { fixture: 'weekly-review.json' }).as('review');

    cy.visit('/lists/weekly-review');
    cy.wait('@review');

    cy.get('[aria-label="Pouce vers le haut pour Boeuf bourguignon"]')
      .should('be.visible')
      .then($btn => {
        expect($btn.outerHeight()).to.be.at.least(40);
        expect($btn.outerWidth()).to.be.at.least(40);
      });

    cy.document().then(doc => {
      expect(doc.documentElement.scrollWidth).to.be.at.most(doc.documentElement.clientWidth + 1);
    });
  });
});
