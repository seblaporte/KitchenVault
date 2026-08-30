describe('Détail d\'une recette', () => {
  beforeEach(() => {
    cy.intercept('GET', '**/api/v1/recipes/recipe-1', { fixture: 'recipe-detail.json' }).as('recipeDetail');
    cy.intercept('GET', '**/api/v1/menu-plan/history*', { body: { recipeId: 'recipe-1', dates: [] } });
    cy.intercept('GET', '**/api/v1/recipes/*/list-membership', {
      fixture: 'recipe-list-membership-none.json',
    }).as('listMembership');
    cy.intercept('GET', '**/api/v1/admin/recipe-lists', { fixture: 'recipe-list-settings.json' });
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

  it('copie la liste des ingrédients dans le presse-papier', () => {
    cy.visit('/recipes/recipe-1');
    cy.wait('@recipeDetail');

    cy.window().then(win => {
      cy.stub(win.navigator.clipboard, 'writeText').resolves().as('writeText');
    });

    cy.get('[aria-label="Copier la liste des ingrédients"]').click();

    cy.get('@writeText').should(
      'have.been.calledWith',
      'Poulet rôti\n\n• 1,5 kg Poulet entier\n• 50 g Beurre',
    );
    cy.get('[role="status"]').should('contain.text', 'Ingrédients copiés');
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

  describe('Liste de recettes (badge + déplacement)', () => {
    it('affiche "Dans aucune liste" quand la recette n\'appartient à aucune liste', () => {
      cy.visit('/recipes/recipe-1');
      cy.wait('@recipeDetail');
      cy.wait('@listMembership');

      cy.contains('Dans aucune liste').should('be.visible');
    });

    it('affiche le libellé configuré du rôle courant', () => {
      cy.intercept('GET', '**/api/v1/recipes/recipe-1/list-membership', {
        fixture: 'recipe-list-membership-discovery.json',
      }).as('listMembership');

      cy.visit('/recipes/recipe-1');
      cy.wait('@recipeDetail');
      cy.wait('@listMembership');

      // Le libellé vient de la fixture recipe-list-settings (jamais codé en dur).
      cy.contains('Miam').should('be.visible');
    });

    it('demande une confirmation avant de déplacer la recette vers une autre liste', () => {
      cy.intercept('PUT', '**/api/v1/recipes/recipe-1/list-membership', {
        body: { role: 'FAVORITES' },
      }).as('move');

      cy.visit('/recipes/recipe-1');
      cy.wait('@recipeDetail');
      cy.wait('@listMembership');

      cy.get('[aria-label="Déplacer cette recette vers une autre liste"]').click();
      cy.contains('button', 'Déplacer vers « Favoris »').click();

      // Le clic sur une liste cible ouvre une étape de confirmation, sans appel API immédiat.
      cy.get('@move.all').should('have.length', 0);
      cy.contains('Déplacer « Poulet rôti » vers « Favoris » ?').should('be.visible');

      cy.contains('button', 'Confirmer').click();

      cy.wait('@move');
      cy.get('[role="status"]').should('contain.text', 'Recette déplacée');
    });

    it('permet d\'annuler avant de confirmer le déplacement', () => {
      cy.intercept('PUT', '**/api/v1/recipes/recipe-1/list-membership', {
        body: { role: 'FAVORITES' },
      }).as('move');

      cy.visit('/recipes/recipe-1');
      cy.wait('@recipeDetail');
      cy.wait('@listMembership');

      cy.get('[aria-label="Déplacer cette recette vers une autre liste"]').click();
      cy.contains('button', 'Déplacer vers « Favoris »').click();
      cy.contains('button', 'Annuler').click();

      cy.contains('button', 'Déplacer vers « Favoris »').should('be.visible');
      cy.get('@move.all').should('have.length', 0);
    });

    it('affiche une erreur si le déplacement échoue (aucune collection rattachée)', () => {
      cy.intercept('PUT', '**/api/v1/recipes/recipe-1/list-membership', { statusCode: 422 }).as('moveFailed');

      cy.visit('/recipes/recipe-1');
      cy.wait('@recipeDetail');
      cy.wait('@listMembership');

      cy.get('[aria-label="Déplacer cette recette vers une autre liste"]').click();
      cy.contains('button', 'Déplacer vers « Favoris »').click();
      cy.contains('button', 'Confirmer').click();

      cy.wait('@moveFailed');
      cy.get('[role="status"]').should('contain.text', 'Échec du déplacement');
    });
  });
});
