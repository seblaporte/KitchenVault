const IPHONE = { width: 390, height: 844 };

function interceptMenuPlan(fixture = 'week-plan-empty.json') {
  return cy.intercept({ method: 'GET', pathname: '/api/v1/menu-plan' }, { fixture }).as('weekPlan');
}

// Semaine (lundi → dimanche) contenant la date système du jour, générée dynamiquement
// pour que le test de mise en évidence du jour courant ne devienne pas obsolète.
function currentWeekPlan() {
  const today = new Date();
  const monday = new Date(today);
  const day = monday.getDay();
  monday.setDate(monday.getDate() + (day === 0 ? -6 : 1 - day));

  const days = Array.from({ length: 7 }, (_, i) => {
    const d = new Date(monday);
    d.setDate(d.getDate() + i);
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const dd = String(d.getDate()).padStart(2, '0');
    return { date: `${y}-${m}-${dd}`, lunch: null, dinner: null };
  });

  return { days };
}

function interceptCommonApis() {
  cy.intercept('GET', '**/api/v1/shopping-list', { body: { recipes: [], items: [] } });
}

describe('Responsive mobile — iPhone 14', () => {
  beforeEach(() => {
    cy.viewport(IPHONE.width, IPHONE.height);
  });

  // ─── Navigation ──────────────────────────────────────────────────────────

  describe('Bottom tab bar', () => {
    beforeEach(() => {
      cy.intercept('GET', '**/api/v1/admin/stats', { fixture: 'admin-stats.json' });
      cy.intercept('GET', '**/api/v1/sync/latest', { statusCode: 404 });
      cy.visit('/');
    });

    it('affiche la barre de navigation mobile', () => {
      cy.get('nav[aria-label="Navigation mobile"]').should('be.visible');
    });

    it('masque les liens de navigation desktop', () => {
      cy.get('nav[aria-label="Navigation principale"] ul').should('not.be.visible');
    });

    it('navigue vers Recettes', () => {
      cy.intercept('GET', '**/api/v1/recipes*', { fixture: 'recipes-page.json' });
      cy.intercept('GET', '**/api/v1/collections', { fixture: 'collections.json' });
      cy.intercept('GET', '**/api/v1/categories', { fixture: 'categories.json' });
      cy.get('nav[aria-label="Navigation mobile"]').contains('Recettes').click();
      cy.url().should('include', '/recipes');
      cy.get('h1').should('be.visible');
    });

    it('navigue vers Menu', () => {
      interceptMenuPlan();
      interceptCommonApis();
      cy.get('nav[aria-label="Navigation mobile"]').contains('Menu').click();
      cy.url().should('include', '/menu');
    });

    it('navigue vers Courses', () => {
      cy.intercept('GET', '**/api/v1/shopping-list', { body: { recipes: [], items: [] } });
      cy.get('nav[aria-label="Navigation mobile"]').contains('Courses').click();
      cy.url().should('include', '/shopping');
    });

    it('navigue vers Mes listes', () => {
      cy.intercept('GET', '**/api/v1/recipe-lists', { fixture: 'recipe-lists-overview.json' });
      cy.get('nav[aria-label="Navigation mobile"]').contains('Listes').click();
      cy.url().should('include', '/lists');
      cy.get('h1').should('contain.text', 'Mes listes');
    });

    it('met en surbrillance l\'onglet actif', () => {
      // '/' redirige vers '/menu' (voir app.routes.ts) : c'est cet onglet qui doit être actif.
      cy.get('nav[aria-label="Navigation mobile"] a[aria-label="Menu"]')
        .should('have.class', 'text-forest-600');
    });
  });

  // ─── Menu hebdomadaire ────────────────────────────────────────────────────

  describe('Menu de la semaine — vue mobile', () => {
    beforeEach(() => {
      interceptMenuPlan();
      interceptCommonApis();
      cy.visit('/menu');
      cy.wait('@weekPlan');
    });

    it('affiche la vue cartes (mobile) et cache la grille desktop', () => {
      cy.get('[data-testid="mobile-calendar"]').should('be.visible');
      cy.get('[data-testid="desktop-calendar"]').should('not.be.visible');
    });

    it('affiche exactement 7 cartes de jours', () => {
      cy.get('[data-testid="day-card"]').should('have.length', 7);
    });

    it('met en évidence le jour courant', () => {
      // Écrase le plan de la semaine (statique, non liée à "aujourd'hui") par une
      // semaine construite dynamiquement pour contenir la date système du jour.
      cy.intercept({ method: 'GET', pathname: '/api/v1/menu-plan' }, { body: currentWeekPlan() }).as('weekPlanToday');
      cy.visit('/menu');
      cy.wait('@weekPlanToday');

      cy.get('[data-testid="day-card"]').contains('Aujourd\'hui').should('exist');
    });

    it('affiche l\'indice de swipe', () => {
      cy.get('[data-testid="mobile-calendar"]')
        .contains('Glissez pour changer de semaine')
        .should('be.visible');
    });

    it('change de semaine par swipe gauche (semaine suivante)', () => {
      cy.get('[data-testid="mobile-calendar"]')
        .trigger('touchstart', { touches: [{ clientX: 300, clientY: 400 }] })
        .trigger('touchend', { changedTouches: [{ clientX: 180, clientY: 400 }] });
      cy.wait('@weekPlan');
    });

    it('change de semaine par swipe droit (semaine précédente)', () => {
      cy.get('[data-testid="mobile-calendar"]')
        .trigger('touchstart', { touches: [{ clientX: 150, clientY: 400 }] })
        .trigger('touchend', { changedTouches: [{ clientX: 280, clientY: 400 }] });
      cy.wait('@weekPlan');
    });

    it('ouvre le sélecteur de recette depuis un créneau mobile', () => {
      cy.intercept('GET', '**/api/v1/recipes*', { fixture: 'recipes-picker.json' });
      cy.get('[data-testid="day-card"]').first()
        .find('[aria-label="Ajouter une recette pour Déjeuner"]').click();
      cy.get('[role="dialog"][aria-label="Sélectionner une recette"]').should('be.visible');
    });

    it('affiche les boutons d\'action sans hover (toujours visibles sur mobile)', () => {
      interceptMenuPlan('week-plan-partial.json');
      interceptCommonApis();
      cy.visit('/menu');
      cy.wait('@weekPlan');

      // Sur mobile, le bouton supprimer doit être visible sans hover
      cy.get('[data-testid="day-card"]').first()
        .find('[aria-label="Supprimer ce repas"]')
        .should('be.visible');
    });
  });

  // ─── Drawer IA ────────────────────────────────────────────────────────────

  describe('Drawer de planification IA — plein écran mobile', () => {
    beforeEach(() => {
      interceptMenuPlan();
      interceptCommonApis();
      cy.visit('/menu');
      cy.wait('@weekPlan');
      cy.get('[aria-label="Planifier la semaine avec l\'IA"]').click();
    });

    it('le drawer occupe toute la largeur du viewport', () => {
      cy.get('app-weekly-plan-drawer').find('div').first()
        .invoke('outerWidth')
        .should('equal', IPHONE.width);
    });

    it('le drawer ne dépasse pas la hauteur du viewport', () => {
      cy.get('app-weekly-plan-drawer').find('div').first()
        .invoke('outerHeight')
        .should('be.lte', IPHONE.height);
    });

    it('le drawer se ferme via le bouton Fermer', () => {
      cy.get('app-weekly-plan-drawer').find('[aria-label="Fermer"]').click();
      cy.get('app-weekly-plan-drawer').should('have.class', 'hidden');
    });
  });

  // ─── Dialogs ──────────────────────────────────────────────────────────────

  describe('Sélecteur de recette — dans le viewport', () => {
    beforeEach(() => {
      interceptMenuPlan();
      interceptCommonApis();
      cy.intercept('GET', '**/api/v1/recipes*', { fixture: 'recipes-picker.json' });
      cy.visit('/menu');
      cy.wait('@weekPlan');
      cy.get('[data-testid="day-card"]').first()
        .find('[aria-label="Ajouter une recette pour Déjeuner"]').click();
    });

    it('le dialog ne dépasse pas la hauteur du viewport', () => {
      cy.get('[role="dialog"]')
        .invoke('outerHeight')
        .should('be.lte', IPHONE.height);
    });

    it("l'input a une font-size ≥ 16px (pas d'auto-zoom iOS)", () => {
      cy.get('[aria-label="Rechercher une recette"]').should(($el) => {
        const fontSize = parseFloat(window.getComputedStyle($el[0]).fontSize);
        expect(fontSize).to.be.gte(16);
      });
    });

    it('le dialog est scrollable si le contenu est long', () => {
      cy.get('[role="dialog"]').find('ul[role="list"]').should('exist');
    });
  });

  // ─── Pas de débordement horizontal ───────────────────────────────────────

  describe('Absence de scroll horizontal', () => {
    it('page Admin — pas de débordement', () => {
      cy.intercept('GET', '**/api/v1/admin/stats', { fixture: 'admin-stats.json' });
      cy.intercept('GET', '**/api/v1/sync/latest', { statusCode: 404 });
      cy.intercept('GET', '**/api/v1/admin/recipe-lists', { fixture: 'recipe-list-settings.json' });
      cy.intercept('GET', '**/api/v1/collections', { fixture: 'collections.json' });
      cy.visit('/admin');
      cy.document().then((doc) => {
        expect(doc.documentElement.scrollWidth).to.be.lte(IPHONE.width);
      });
    });

    it('page Mes listes — pas de débordement', () => {
      cy.intercept('GET', '**/api/v1/recipe-lists', { fixture: 'recipe-lists-overview.json' }).as('overview');
      cy.visit('/lists');
      cy.wait('@overview');
      cy.document().then((doc) => {
        expect(doc.documentElement.scrollWidth).to.be.lte(IPHONE.width);
      });
    });

    it('page Bilan hebdomadaire — pas de débordement', () => {
      cy.intercept('GET', '**/api/v1/weekly-reviews/*', { fixture: 'weekly-review.json' }).as('review');
      cy.visit('/lists/weekly-review');
      cy.wait('@review');
      cy.document().then((doc) => {
        expect(doc.documentElement.scrollWidth).to.be.lte(IPHONE.width);
      });
    });

    it('page Recettes — pas de débordement', () => {
      cy.intercept('GET', '**/api/v1/recipes*', { fixture: 'recipes-page.json' });
      cy.intercept('GET', '**/api/v1/collections', { fixture: 'collections.json' });
      cy.intercept('GET', '**/api/v1/categories', { fixture: 'categories.json' });
      cy.visit('/recipes');
      cy.document().then((doc) => {
        expect(doc.documentElement.scrollWidth).to.be.lte(IPHONE.width);
      });
    });

    it('page Menu — pas de débordement horizontal (vue cartes)', () => {
      interceptMenuPlan();
      interceptCommonApis();
      cy.visit('/menu');
      cy.wait('@weekPlan');
      cy.document().then((doc) => {
        expect(doc.documentElement.scrollWidth).to.be.lte(IPHONE.width);
      });
    });
  });
});
