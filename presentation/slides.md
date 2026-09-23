---
theme: seriph
title: Retour d'expérience sur le dev 100% agentique d'une application Spring Boot / Angular que j'utilise au quotidien
class: text-center
drawings:
  persist: false
transition: fade
mdc: true
fonts:
  sans: 'Inter'
  mono: 'Fira Code'
---

# Chapter dev - 23/09/2026

## Retour d'expérience sur le dev 100% agentique d'une application Spring Boot / Angular que j'utilise au quotidien

---
layout: section
---

# Le projet

## KitchenVault

<div class="pt-10 text-xl italic opacity-80 max-w-xl mx-auto leading-relaxed">
« Chaque semaine, la même question revient : qu'est-ce qu'on mange ?
<br>Et une fois qu'on y a répondu : qu'est-ce qu'il faut au marché et aux courses ? »
</div>

---

# KitchenVault en deux mots

Application auto-hébergée de gestion de recettes synchronisée avec l'application **Cookidoo**.

<div class="grid grid-cols-2 gap-8 pt-6">
<div>

**Stack applicative**

- Backend : Spring Boot 4 - Java 25
- Frontend : Angular 21
- Microservice Cookidoo : Python FastAPI 
- Base de données : PostgreSQL + pgvector
- Approche contract first pour l'API REST

</div>
<div>

**Fonctionnalités principales**

- Catalogue de recettes synchronisé de façon bidirectionnelle avec Cookidoo
- Gestion d'un planning hebdomadaire
- Proposition de recettes et création d'un menu complet par un assistant IA
- Création de liste de courses consolidée par IA

</div>
</div>

---
layout: statement
---

# Ce dont on ne va **pas** parler aujourd'hui

L'assistant IA de planification de menus, LangChain4J, les agents spécialisés.

<div class="pt-4 text-xl opacity-80">
Aujourd'hui : uniquement <strong>comment</strong> j'ai développé
</div>

---

# Le projet en chiffres

<div class="grid grid-cols-3 gap-4 pt-6 text-center">
<div class="p-4 rounded border border-gray-500/30">

**123**
<br><span class="text-sm opacity-70">commits</span>

</div>
<div class="p-4 rounded border border-gray-500/30">

**16**
<br><span class="text-sm opacity-70">PRs mergées</span>

</div>
<div class="p-4 rounded border border-gray-500/30">

**10**
<br><span class="text-sm opacity-70">releases</span>

</div>
<div class="p-4 rounded border border-gray-500/30">

**~70**
<br><span class="text-sm opacity-70">builds d'images Docker (CI, 3 workflows)</span>

</div>
<div class="p-4 rounded border border-gray-500/30">

**~13 800**
<br><span class="text-sm opacity-70">lignes de code (Java + TypeScript + Python)</span>

</div>
<div class="p-4 rounded border border-gray-500/30">

**~240**
<br><span class="text-sm opacity-70">tests automatisés</span>

</div>
</div>

---

# Du code à la CI...

Publication d'une **release GitHub** → 3 workflows GitHub Actions construisent et publient les images Docker sur GHCR

<div class="grid grid-cols-2 gap-6 pt-4 text-sm">
<div>

**3 workflows, un par image**
- `docker-native.yml` — backend Spring Boot natif GraalVM
- `docker-frontend.yml` — frontend Angular
- `docker-cookidoo.yml` — microservice Python

</div>
<div>

**Déclenchement**
- `release: published` → tags semver
- `workflow_dispatch` → tag manuel

</div>
</div>

```mermaid {scale: 0.65}
flowchart LR
    release(["Release GitHub\npubliée"]) --> native["docker-native"]
    release --> frontend["docker-frontend"]
    release --> cookidoo["docker-cookidoo"]
    native --> ghcr[("ghcr.io")]
    frontend --> ghcr
    cookidoo --> ghcr
```

<div class="pt-4 text-sm opacity-60">
+ un 4e workflow (<code>docs.yml</code>) déploie la documentation Antora sur GitHub Pages à chaque push sur <code>main</code> touchant <code>docs/</code>.
</div>

---

# ...jusqu'au déploiement

Le déploiement sur Kubernetes (repo séparé `k3s-at-home`)

```mermaid {scale: 0.48}
flowchart LR
    user((Utilisateur)) -->|Cloudflare| cfd
    subgraph cluster["Cluster k3s — FluxCD + chart app-template"]
        direction LR
        subgraph network["namespace network"]
            direction TB
            cfd["cloudflared\ntunnel"] --> traefik["Traefik"]
            traefik -.-> crowdsec["CrowdSec"]
            traefik -.-> authelia["Authelia"]
        end
        subgraph prod["namespace kitchenvault-prod"]
            direction LR
            fp["frontend\nAngular"] --> bp["backend\nSpring Boot"]
            bp --> cp["cookidoo-service\nPython"]
            bp --> pp[("PostgreSQL\npgvector")]
            cp --> pp
        end
        subgraph dev["namespace kitchenvault-dev"]
            direction LR
            fd["frontend\nAngular"] --> bd["backend\nSpring Boot"]
            bd --> cd["cookidoo-service\nPython"]
            bd --> pd[("PostgreSQL\npgvector")]
            cd --> pd
        end
        traefik --> fp
        traefik --> fd
    end
```

---
layout: section
---

# Le développement du projet

## Phase par phase

---

# 29 mars — le bootstrap, en 1 jour

<div class="text-lg pb-2">
Repo vide le matin, application complète le soir : <strong>~30 commits</strong> le même jour.
</div>

- `docker-compose` — PostgreSQL, pgAdmin, `cookidoo-service`
- Microservice Python FastAPI autour de `cookidoo-api`
- Architecture multi-module Maven — `contracts` (OpenAPI) + `backend`
- Backend Spring Boot : entités JPA, migrations Liquibase, client HTTP Cookidoo
- Tests unitaires et d'intégration
- Frontend Angular 21 + TailwindCSS 4, premier écran d'admin
- Documentation Antora, README, `CLAUDE.md`

<div class="pt-8 text-sm opacity-60">
Le socle complet — 4 couches, tests et documentation compris.
</div>

<br>

<div class="pt-4 p-4 rounded border-l-4 border-blue-400 bg-blue-400/10">
Objectif : Socle technique et documentaire.
</div>

---

# 30 mars → 21 avril — features et industrialisation

<div class="grid grid-cols-2 gap-6 text-sm pt-4">
<div>

**Premières features métier**
<br>Recherche et consultation des recettes, filtres par collection et par ingrédient, affichage nutritionnel

**Documentation technique sur GitHub Pages**
<br>Documentation Antora déployée automatiquement sur GitHub Pages

</div>
<div>

**Compilation native GraalVM**
<br>Migration Spring Boot 3.5.13 → 4.0.5
<br>Support de la compilation native, premier build sans JVM au démarrage

**3 workflows Docker**
<br>GitHub Actions pour les 3 images : backend natif, `cookidoo-service`, frontend

</div>
</div>

<br>

<div class="pt-4 p-4 rounded border-l-4 border-blue-400 bg-blue-400/10">
Objectifs : Socle fonctionnel nécessaire à l'assistant IA et déploiement sur environnements.
</div>

---

# 23-24 avril — Issue → PR

- Premier vrai cycle complet - <strong>Issue #1 → PR #3</strong> : le planning hebdomadaire de menus.
- Mise en place de Cypress et premier test E2E

<br>

<div class="pt-4 p-4 rounded border-l-4 border-blue-400 bg-blue-400/10">
Objectifs : Évolution de la méthode de spécification / réalisation.
</div>

---

# 24 avril → 6 mai — branding et assistant IA

- **PR #5** : Renommage en KitchenVault
- **PR #6** : Dark mode et identité visuelle
- **PR #8** - Assistant IA culinaire : LangChain4J, embeddings de recettes, planification IA hebdomadaire
- **4-5 mai** - ~22 commits en 2 jours pour stabiliser le build natif GraalVM

<br>

<div class="pt-4 p-4 rounded border-l-4 border-blue-400 bg-blue-400/10">
Objectifs : Mise en place de l'assistant IA.
</div>

---

# 13-25 mai — un sprint dense

- **PR #10** : Liste de courses + consolidation IA
- **25 mai** - 4 PRs mergées le même jour :
  - **PR #11** : Synchro du planning vers Cookidoo
  - **PR #12** : Responsive mobile, PWA
  - **PR #13** : Export email de la liste
  - **PR #14** : Polish UX liste de courses
- Puis silence sur `main` jusqu'au 12 août — pause estivale

<br>

<div class="pt-4 p-4 rounded border-l-4 border-blue-400 bg-blue-400/10">
Objectifs : Rendre l'application utilisable au quotidien.
</div>

---

# 13 → 30 août — la reprise

- **PR #20** : Déplacer une recette planifiée
- **PR #21** : Synchro descendante Cookidoo
- **PR #22-24** : Corrections de synchro montante
- **PR #25** : Listes de recettes personnalisables
- **PR #26** : Copier les ingrédients dans le presse-papier
- Documentation Antora réalignée avec le code

<br>

<div class="pt-4 p-4 rounded border-l-4 border-blue-400 bg-blue-400/10">
Objectifs : Nouvelles features et consolidation, avec une méthode désormais rodée.
</div>

---
layout: section
---

# Retour d'expérience sur la méthode utilisée

---

# Les bases de la méthode

<div class="pt-2 grid grid-cols-2 gap-3 text-sm">
<div class="px-4 py-2 rounded border border-gray-500/30">

**Le mode Plan**
<br>Cadrage fonctionnel avant tout code

</div>
<div class="px-4 py-2 rounded border border-gray-500/30">

**Un worktree = une tâche**
<br>Isolation, jamais un fil qui dérive

</div>
<div class="px-4 py-2 rounded border border-gray-500/30">

**Build / review séparés**
<br>Un reviewer en contexte neuf

</div>
<div class="px-4 py-2 rounded border border-gray-500/30">

**Autonomie sur la boucle de rétroaction**
<br>Donner les outils et CLI plutôt qu'un copier-coller

</div>
<div class="px-4 py-2 rounded border border-gray-500/30">

**Méthode spec first**
<br>Cadrer via une issue GitHub ou une page de documentation, point d'entrée d'une nouvelle
session dédiée à l'implémentation

</div>
<div class="px-4 py-2 rounded border border-gray-500/30">

**Documentation à jour**
<br>Vérifier la doc réelle d'une lib avant de l'utiliser, plutôt que la mémoire d'entraînement du modèle

</div>
</div>

---
layout: center
---

# Le mode Plan n'est pas seulement un moyen d'économiser des tokens

## C'est un outil de cadrage **fonctionnel**

<div class="pt-6 text-xl opacity-80 max-w-2xl mx-auto">
Savoir exactement ce qu'on veut faire — pas seulement comment le coder —
<strong>avant</strong> d'écrire la moindre ligne.
</div>

---

# Rester en mode plan et itérer

- Rester en mode plan tant que l'objectif et la méthode ne sont pas **cadrés précisément**
- Utiliser la **technique de l'entonnoir**
- **Challenger ses propres idées** : l'IA questionne et co-construit la spécification, elle ne se contente pas de valider un plan.
- Pour aller plus loin dans le détail : demander à passer en **mode interview**

---

# Un exemple de pattern


- Cadrer une feature en mode plan jusqu'à produire une <strong>issue GitHub</strong>
- Challenger cette issue dans une <strong>nouvelle session</strong>
- Démarrer une <strong>nouvelle session</strong> qui lit l'issue pour produire un plan d'implémentation et itérer sur ce plan au besoin pour finir par l'implémentation

<div class="flex items-center gap-1 pt-6 text-xs">
<div class="flex-1 p-3 rounded border border-gray-500/30 text-center">

**Session 1<br>Mode Plan**
<br>Cadrage

</div>
<div class="shrink-0 text-center opacity-60 px-1">
→<br>produit
</div>
<div class="flex-1 p-3 rounded border border-gray-500/30 text-center">

**Issue GitHub**
<br>Spécifications structurées

</div>
<div class="shrink-0 text-center opacity-60 px-1">
→<br>nouvelle session
</div>
<div class="flex-1 p-3 rounded border border-gray-500/30 text-center">

**Session 2**
<br>Challenge de l'issue

</div>
<div class="shrink-0 text-center opacity-60 px-1">
→<br>issue amendée
</div>
<div class="flex-1 p-3 rounded border border-gray-500/30 text-center">

**Session 3<br>Mode Plan**
<br>Plan d'implémentation<br>↻ itérations

</div>
<div class="shrink-0 text-center opacity-60 px-1">
→<br>plan validé
</div>
<div class="flex-1 p-3 rounded border border-gray-500/30 text-center">

**Session 3**
<br>Implémentation

</div>
</div>

<div class="pt-4 text-sm opacity-70">
Sépare nettement <strong>décider quoi faire et comment</strong> de <strong>l'écrire</strong>.
</div>

---

# Même entonnoir, à plus grande échelle

<div class="text-lg pb-2">
La même méthode que l'issue #1, mais sur la plus grosse feature du projet — l'assistant IA.
</div>

<div class="flex items-stretch gap-1 pt-2 text-xs">
<div class="flex-[2] p-2 rounded border border-gray-500/30">
<div class="text-center pb-1">

**Issue #2**
<br>Spécifications initiales

</div>
<div class="grid grid-cols-2 gap-2">
<div class="px-2 py-1 rounded bg-gray-500/10 text-center">

**Challenge de spécification**
<br><span class="opacity-70">8 points arbitrés</span>

</div>
<div class="px-2 py-1 rounded bg-gray-500/10 text-center">

**Complément de spécifications**
<br><span class="opacity-70">approfondissement fonctionnel</span>

</div>
</div>
</div>
<div class="shrink-0 self-center text-center opacity-60 px-1">
→<br>reconsolidation
</div>
<div class="flex-1 self-center p-2 rounded border border-gray-500/30 text-center">

**Issue #7**
<br>Spécification consolidée
<br><span class="opacity-70">remplace l'issue #2</span>

</div>
<div class="shrink-0 self-center text-center opacity-60 px-1">
→<br>implémentation
</div>
<div class="flex-1 self-center p-2 rounded border border-blue-400/60 bg-blue-400/10 text-center">

**PR #8**
<br><span class="opacity-70">71 fichiers</span>
<br><span class="text-green-500">+5204</span> / <span class="text-red-400">-290</span>

</div>
</div>

<div class="mt-4 p-3 rounded border-l-4 border-blue-400 bg-blue-400/10">
La spécification est challengée et enrichie <strong>dans l'issue</strong>, puis reconsolidée avant la moindre ligne de code.
</div>

---

# Les mêmes accès qu'un dev humain pour le harness

<div class="text-lg pb-4">
Chaque fois qu'une information existe ailleurs — CI, cluster, API tierce — donner l'outil pour aller la chercher, plutôt que copier-coller un log dans le chat.
</div>

<div class="grid grid-cols-2 gap-6 text-sm pt-4">
<div>

**Utilisés dans le projet**
<br><code>gh</code> CLI (PR, issues), extension Chrome (maquettes, capture visuelle), MCP de design.

</div>
<div>

**Le même principe, ailleurs**
<br><code>gh run</code> pour inspecter un run GitHub Actions qui échoue, <code>kubectl</code> pour lire
les logs d'un pod sur l'environnement de dev.

</div>
</div>

---

# S'appuyer sur la documentation, pas sur la mémoire du modèle

<div class="text-lg pb-4">
Le modèle a une date de coupure, les librairies évoluent : Context7 (MCP) récupère la doc à jour
avant de l'utiliser.
</div>

<div class="pt-2 p-4 rounded border-l-4 border-blue-400 bg-blue-400/10">
Sur ce projet : la mise en place de LangChain4J (PR #8) s'est appuyée sur Context7 pour
vérifier la doc à jour du framework — plutôt que sur les connaissances d'entraînement du modèle.
</div>

<div class="pt-8 text-sm opacity-60">
Une règle globale (<code>~/.claude/rules/context7.md</code>) l'impose pour toute lib, framework ou
SDK cité — même ceux qu'on croit bien connaître.
</div>

---

# Les autres piliers

<br>

- **Un worktree = une tâche**
<br>Chaque session sérieuse dans un worktree git dédié. Isolation, parallélisation possible.

- **Build et review, sessions séparées**
<br>Un reviewer en contexte neuf n'a que le diff et les critères, pas le raisonnement qui a produit le changement.

---
layout: section
---

# Cas concrets
## 2 features, une revue

---
layout: section
---

# Cas A

## De la maquette au code

---

# PR #20 — Déplacer une recette planifiée

13 août 2026 · mode "Attraper" dans le planning hebdomadaire

<div class="grid grid-cols-2 gap-6 pt-2 items-center">
<div>

1. **Maquette d'abord, dans Claude Design** — chat + prévisualisation live, itérée
   avant d'ouvrir Claude Code
2. **Implémentation complète** : endpoint `PATCH` atomique dédié (backend), mode "Attraper"
   (frontend)
3. **Session de revue dédiée séparée**, avant merge

</div>
<div>

<img src="/images/Claude%20Design.png" class="rounded border border-gray-500/30" />
<div class="text-xs opacity-60 pt-2 text-center">Claude Design — maquette KitchenVault avant passage à Claude Code</div>

</div>
</div>

<div class="pt-6 p-4 rounded border-l-4 border-blue-400 bg-blue-400/10">
L'agent part d'un artefact visuel comme le ferait un dev humain qui reçoit une maquette
Figma — pas seulement d'un ticket texte.
</div>

---
layout: section
---

# Cas B

## L'étude de faisabilité avant le code

---

# PR #21 — Synchro descendante Cookidoo

1. **Session d'étude de faisabilité** à part entière, avant d'écrire la moindre ligne —
   explorer l'API tierce Cookidoo, valider que la sync inverse (pull) est possible
2. **Implémentation complète multi-couches, en une session** : microservice Python →
   contrats OpenAPI → backend Spring (delegate, mapping, persistance) → frontend Angular
3. Mise en production

---
layout: center
---

# Sur un sujet ambigu ou risqué...

## ...une phase de faisabilité dédiée *avant* l'implémentation change la donne

<div class="pt-6 text-lg opacity-80 max-w-2xl mx-auto">
L'agent explore et rapporte, l'humain valide l'approche, puis l'implémentation part sur des rails clairs.
</div>

---
layout: section
---

# Revue de code

## La review : un rôle à part entière

---

# Revue de la PR #25

<div class="pt-4 text-lg">

Une session dédiée exécute la skill <code>/review</code> sur la PR #25 (3 listes de
recettes) et applique les corrections identifiées — dans une session **distincte** de
celle qui a construit la feature.

</div>

<div class="pt-8 p-4 rounded border-l-4 border-blue-400 bg-blue-400/10">
La revue de code est un <strong>rôle agentique à part entière</strong>, pas une passe de
relecture confondue avec l'implémentation — même agent, casquette différente, session
différente.
</div>

---
layout: section
---

# Takeaway

---

# Takeaway

- **Mode Plan** pour cadrer le quoi avant le comment — pas juste avant du code compliqué,
  avant tout ce qui compte
- **Mode Plan pour challenger ses idées** : l'IA questionne et co-construit la spec, elle ne
  se contente pas de la valider
- **Un worktree par tâche** : pour paralléliser les tâches
- Donner les mêmes outils au harness qu'à un humain (CLI, MCP) : boucle de rétroaction autonome.
- **Documentation à jour** (Context7/MCP) : vérifier plutôt que faire confiance à la mémoire du modèle
- **Sur les sujets complexes ou ambigus** : cadrer en entonnoir, voire lancer une phase de faisabilité dédiée avant d'implémenter
- **La revue comme session séparée**, pas une relecture confondue avec le build

---
layout: statement
---

# La suite ?

## Utiliser l'IA dans une application

<div class="pt-4 text-lg opacity-70">
Assistant IA, RAG, agents métier, ...
</div>

---
layout: center
class: text-center
---

# Merci
## Des questions ?
