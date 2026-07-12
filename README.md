# MineLab 2D 🚩💣🗝️

Jeu Android natif (Kotlin) : un **grand plateau de démineur (41×41) intégré dans un labyrinthe**,
vu de dessus. Le petit bonhomme **se déplace tout seul** vers la dalle que vous touchez.

## Règles
- **Touchez une dalle inconnue** → le héros y va et la **sonde**. Sûre → elle se retourne avec son
  chiffre (cascade automatique sur les zones à 0). Mine → BOUM, ‑20 PV.
- **Appui long** (ou bouton DRAPEAU) → poser / retirer un **drapeau** 🚩.
- **Touchez une dalle marquée** → le héros la **désamorce sans aucun risque**. On n'est donc
  **jamais obligé de faire exploser une mine** : marquez, puis désamorcez.
- **Glissez le doigt** pour déplacer la carte. Boutons **−/+** (zoom), **◎** (recentrer),
  **⟳** (rejouer), **?** (aide).

## Énigme (salle en bas à droite)
1. Placez le héros à côté d'un **bloc**, puis touchez-le pour le **pousser** (façon Sokoban).
2. Poussez les **3 blocs** sur les **3 dalles de pression**.
3. Le **coffre** se déverrouille → touchez-le pour récupérer la **clé** 🗝️.
4. La clé ouvre la **porte violette** → derrière : l'**étoile verte** = victoire !

Le combat contre les monstres sera ajouté ensuite.

## Compiler l'APK
Poussez sur GitHub : le workflow `.github/workflows/build.yml` compile l'APK.
Onglet **Actions** → dernier run → **Artifacts** → `MineLab3D-debug` (contient `app-debug.apk`).

## Commandes Termux
```bash
cd ~
unzip -o ~/storage/downloads/MineLab3D.zip -d ~/tmpml
cp -r ~/tmpml/MineLab3D/. ~/MineLab3D/
rm -rf ~/tmpml
cd ~/MineLab3D
git add -A
git commit -m "Carte agrandie, deplacement au doigt, salle a enigme (blocs/coffre/cle/porte)"
git push
```
