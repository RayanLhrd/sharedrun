# SharedRun

Mod Minecraft Fabric pour **rusher l'Ender Dragon en coop** avec une équipe d'amis.
Pensé pour des soirées LAN où tout le monde partage les HP et court contre la montre.

> Compatible **Minecraft 1.21.11** / Fabric Loader ≥ 0.17.0 / Fabric API + placeholder-api

## ✨ Features

### Core
- **HP partagés** entre tous les joueurs (cascade quand un meurt = run terminée)
- **Hunger / status effects** synchronisés
- **Timer global** (90 min par défaut) avec checkpoints sonores (30 / 15 / 5 / 1 min)
- **Swaps aléatoires** des positions de joueurs (intensifie la fin de run)
- **Pré-game lobby** avec world border réduite + countdown 5 secondes

### Progression visuelle
- **HUD timer** + coordonnées en haut à droite
- **HUD objectifs** à droite : 9 milestones du speedrun (food → fer → diamond → nether → blaze → pearl → eye → portail → dragon)
- **Announces chat** quand un milestone est atteint (avec le pseudo du joueur)
- **14 achievements vanilla-like** trackés (premier diamant, premier set fer, etc.)

### Fin de run
- **Cinématique de mort** : titre "VOUS ÊTES MORT" zoom doré, suspense "À cause de…", reveal 3D du skin du joueur mort, raison de la mort (chair putride, lave, squelette…), son *Mario Game Over*
- **Cinématique de victoire** : "VICTOIRE !" doré + confettis + lineup 3D de tous les participants avec MVP couronné, son *Mario Victory Theme*
- **Animation vanilla d'explosion du dragon préservée** (delay 10s avant la cinematic)
- **Récap stats riche** (RunSummaryScreen) en grille 2 colonnes :
  - Top dégâts pris (leaderboard top 5)
  - ⭐ MVP / 🐲 Coup fatal
  - 🍗 Top chef / 🤢 Chair putride
  - 🔀 Swaps / 👥 Joueurs
- **5 boutons TP debrief** (Overworld / Nether / Forteresse / Stronghold / End) — en spectator, sans déconnexion. Avec locate vanilla si la destination n'a pas été visitée.
- **Menu chat cliquable persistant** post-run (via `ClickEvent.Custom` + mixin → pas de popup de confirmation)

## 🛠️ Build

```bash
./gradlew build
```

Le jar final est dans `build/libs/sharedrun-*.jar`.

Pour un dev environment :
```bash
./gradlew genSources
./gradlew runClient
```

## 🎮 Commandes

| Commande | Effet |
|---|---|
| `/sharedrun start` | Démarre une run (countdown 5s puis GO!) |
| `/sharedrun pause` / `resume` / `stop` / `reset` | Contrôle du timer |
| `/sharedrun status` | État courant de la run |
| `/sharedrun settings ...` | Configuration (timer, HP sync, swap, knockback, lobby) |
| `/sharedrun stats <show\|reset>` | Stats persistantes (hearts caused) |
| `/sharedrun debrief` | Rouvre l'écran de récap |
| `/sharedrun tp <overworld\|nether\|stronghold\|end>` | TP debrief manuel |
| `/sharedrun save` | Force la sauvegarde NBT |

Alias court : `/sr`.

## 📦 Stack technique

- **Fabric Loom** 1.13.6
- **Yarn mappings** (build.4)
- **Mixins** :
  - `LivingEntityAccessor` — accès au champ `deathTime` (pour reset le skin en pose normale dans la cinematic)
  - `CustomClickActionMixin` — intercepte les `ClickEvent.Custom` du chat (bypass popup confirmation)
  - `ConsumableComponentMixin` — hook sur `ConsumableComponent.finishConsumption` (tracking bonus/malus food)
- **Custom packets** : `TimerSyncPayload`, `ProgressSyncPayload`, `RunSummaryPayload`, `DebriefTpPayload`
- **Custom sounds** : `death_gameover.ogg`, `victory_theme.ogg`
- **Persistence NBT** : `sharedrun_state.dat` dans le dossier monde

## 📄 Licence

MIT — voir [LICENSE](LICENSE).

Les fichiers audio dans `assets/sharedrun/sounds/cinematic/` sont des extraits de Super Mario (Nintendo) utilisés à titre personnel/privé uniquement. **Ne pas redistribuer commercialement.**
