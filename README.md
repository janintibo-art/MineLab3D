# MineLab 2D 🚩💣

Jeu Android natif (Kotlin) : un **grand plateau de démineur intégré dans un labyrinthe**, vu de dessus.
Le petit bonhomme **se déplace tout seul** vers la dalle que vous touchez, en empruntant le plus court
chemin par les dalles déjà déminées.

## Règles
- **Touchez une dalle inconnue** → le héros marche jusqu'à une case voisine sûre, puis la **sonde**.
  - Si elle est sûre, elle se retourne et affiche son **chiffre** (nombre de mines autour), avec
    cascade automatique quand c'est 0, comme au démineur classique.
  - Si c'est une **mine** : BOUM, -20 PV (mais la dalle devient praticable).
- **Appui long** (ou bouton **DRAPEAU : ON**) → poser / retirer un **drapeau** sur une dalle suspecte.
- **Touchez une dalle marquée d'un drapeau** → le héros s'y rend et la **désamorce** proprement (0 dégât).
  C'est la bonne façon d'ouvrir un couloir piégé.
- **Touchez une dalle déjà révélée** → le héros s'y déplace.
- Atteignez l'**étoile verte** (la sortie) pour gagner.
- Boutons du bas : mode drapeau, **−** / **+** pour zoomer, **⟳** pour relancer une partie.

Le labyrinthe (31×31), les salles et les mines sont générés aléatoirement à chaque partie.
Le combat contre les monstres sera ajouté dans une prochaine version.

## Compiler l'APK avec GitHub Actions
1. Poussez ce projet sur GitHub (commandes Termux ci-dessous).
2. Le workflow `.github/workflows/build.yml` se lance à chaque push.
3. Onglet **Actions** → dernier run → section **Artifacts** → téléchargez **MineLab3D-debug**
   (zip contenant `app-debug.apk`), puis installez-le sur le téléphone.

## Commandes Termux
```bash
cd ~
unzip -o ~/storage/downloads/MineLab3D.zip -d ~/tmpml
cp -r ~/tmpml/MineLab3D/. ~/MineLab3D/
rm -rf ~/tmpml
cd ~/MineLab3D
git add -A
git commit -m "Version 2D : plateau de demineur dans le labyrinthe"
git push
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
        ├── res/
        └── java/com/minelab/game/
            ├── MainActivity.kt      # Activité plein écran (portrait)
            ├── World.kt             # Labyrinthe, mines, cascade, recherche de chemin (BFS)
            └── GameView.kt          # Plateau 2D, héros auto-déplacé, HUD tactile
```
