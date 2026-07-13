# MineLab 🚩💣🗝️📦

Jeu Android natif (Kotlin), vue de dessus. Trois zones, sans labyrinthe :

## 1. La grande salle de démineur
Un vrai plateau de démineur ouvert. Touchez une dalle : le héros y va tout seul et la sonde.
Les **chiffres sont figés sur la disposition d'origine des mines** — ils ne changent jamais,
même après un désamorçage. Traversez jusqu'au passage de droite.

- Appui long (ou bouton DRAPEAU) = marquer une dalle 🚩
- Retoucher une dalle marquée = le héros la **désamorce sans risque**, mais le **drapeau est consommé**
- Vous avez exactement autant de drapeaux que de mines : un drapeau gaspillé = une mine à faire
  sauter plus tard (‑20 PV)

## 2. La salle du coffre
Poussez les 3 blocs sur les 3 dalles orange (placez-vous à côté, touchez le bloc).
Le **coffre** se met à briller, s'ouvre, et une **clé en or** s'en échappe (animation).
La clé ouvre la **porte violette**.

## 3. La salle de rangement (Sokoban à solution unique)
4 caisses à ranger sur les 4 dalles bleues au fond d'une **alcôve en cul-de-sac**.
Les caisses ne peuvent monter que par **le puits du milieu**, et le héros ne peut les contourner
que par **le puits de droite** : il n'existe donc qu'**une seule solution**, et il faut ranger
la caisse **la plus profonde en premier**. Les 4 rangées → la **trappe s'ouvre** = victoire.
En cas de blocage : Menu ☰ → *Réinitialiser les caisses*.

## Écran de présentation
Nom du héros · Difficulté (FACILE / NORMAL / DIFFICILE) · **VIE ILLIMITÉE** (mode test) ·
Nouvelle partie · Continuer (sauvegarde automatique).

## Menu en jeu (☰)
Reprendre · Inventaire · Sauvegarder · Réinitialiser les caisses · Aide · Nouvelle partie · Menu principal.

## Commandes Termux
```bash
cd ~
unzip -o ~/storage/downloads/MineLab3D.zip -d ~/tmpml
cp -r ~/tmpml/MineLab3D/. ~/MineLab3D/
rm -rf ~/tmpml
cd ~/MineLab3D
git add -A
git commit -m "Fix chiffres demineur, suppression labyrinthe, graphismes, sokoban unique"
git push
```
Puis onglet **Actions** → dernier run → **Artifacts** → `MineLab3D-debug`.
