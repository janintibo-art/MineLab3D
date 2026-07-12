# MineLab 3D 🗡️💣

Jeu Android natif (Kotlin) : un démineur en 3D, vue à la 3e personne.
Le sol est un véritable plateau de démineur : des dalles grises à sonder, qui se retournent
en affichant leur chiffre. Un petit héros armé d'une épée (visible à l'écran) doit désamorcer
les mines, résoudre les énigmes des portes, combattre les monstres et atteindre le portail vert.

## Gameplay
- **Croix directionnelle** (gauche) : avancer / reculer / tourner.
- **SONDER** : analyse la dalle surlignée en jaune devant le héros. Si elle est sûre, elle se
  retourne et affiche au sol son nombre de mines adjacentes (avec cascade automatique quand c'est 0,
  comme au démineur). Si c'est une mine, un drapeau rouge se plante dessus.
- **DÉSAMORCER** : neutralise une mine détectée (drapeau) devant vous.
- **ÉPÉE !** : frappe le monstre en face de vous (portée courte). Tuer un monstre rend 15 PV.
- Marcher sur une mine non sondée = **BOUM**, -25 PV.
- Les portes violettes posent une **énigme** : mauvaise réponse = -10 PV.
- Les dalles non révélées sont en **relief gris**, les dalles révélées sont **plates et claires**
  avec leur chiffre coloré écrit au sol (1 bleu, 2 vert, 3 rouge...).
- La **mini-carte** en haut à droite donne une vue d'ensemble.
- Le labyrinthe, les mines, les salles et les monstres sont **générés aléatoirement** à chaque partie.

## Compiler l'APK avec GitHub Actions
1. Poussez ce projet sur GitHub (voir commandes Termux ci-dessous).
2. Le workflow `.github/workflows/build.yml` se lance automatiquement à chaque push.
3. Sur GitHub : onglet **Actions** → cliquez sur le dernier run → section **Artifacts**
   → téléchargez **MineLab3D-debug** (un zip contenant `app-debug.apk`).
4. Installez l'APK sur votre téléphone (autorisez les "sources inconnues").

## Commandes Termux
```bash
pkg update -y
pkg install -y git gh unzip
termux-setup-storage        # autoriser l'accès au stockage (une seule fois)

# Dézipper (adaptez le chemin du zip)
unzip ~/storage/downloads/MineLab3D.zip -d ~/MineLab3D
cd ~/MineLab3D

# Dépôt git
git init
git add .
git commit -m "MineLab 3D - premier commit"

# Connexion GitHub puis création + envoi du dépôt
gh auth login               # GitHub.com → HTTPS → Login with a web browser
gh repo create MineLab3D --public --source=. --remote=origin --push
```

### Sans `gh` (méthode manuelle)
Créez un dépôt vide sur github.com, générez un token (Settings → Developer settings →
Personal access tokens, portée `repo`), puis :
```bash
git remote add origin https://github.com/VOTRE_PSEUDO/MineLab3D.git
git branch -M main
git push -u origin main     # utilisateur = pseudo, mot de passe = le token
```

## Structure
```
MineLab3D/
├── .github/workflows/build.yml      # CI : compile l'APK debug
├── settings.gradle / build.gradle / gradle.properties
└── app/
    ├── build.gradle
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/ (icône, strings)
        └── java/com/minelab/game/
            ├── MainActivity.kt      # Activité plein écran
            ├── World.kt             # Génération labyrinthe, mines, salles, énigmes
            └── GameView.kt          # Moteur 3D raycasting, combat, HUD tactile
```
