# MineLab 3D 🗡️💣

Jeu Android natif (Kotlin) : un démineur dans un labyrinthe 3D vu à la première personne.
Un petit héros armé d'une épée doit sonder le sol, désamorcer les mines, résoudre les
énigmes des portes magiques, combattre les monstres des salles... et atteindre le
portail vert de sortie.

## Gameplay
- **Croix directionnelle** (gauche) : avancer / reculer / tourner.
- **SONDER** : analyse la case juste devant. Si elle est sûre, la mini-carte affiche
  le nombre de mines adjacentes (comme au démineur). Si c'est une mine, un drapeau rouge apparaît.
- **DÉSAMORCER** : neutralise une mine détectée (drapeau) devant vous.
- **ÉPÉE !** : frappe le monstre en face de vous (portée courte). Tuer un monstre rend 15 PV.
- Marcher sur une mine non sondée = **BOUM**, -25 PV.
- Les portes violettes posent une **énigme** : mauvaise réponse = -10 PV.
- La **mini-carte** en haut à droite montre les cases révélées et leurs chiffres.
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
