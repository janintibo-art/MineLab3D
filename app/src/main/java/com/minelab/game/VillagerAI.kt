package com.minelab.game

import kotlin.random.Random

/**
 * L'ame des villageois -- portage Kotlin de la "mini IA PNJ" (cerveau.py /
 * dialogue.py) adaptee a la vie de village :
 *
 *  * PERSONNALITE : chaque villageois a un nom, un metier et des traits
 *    (bavardage, humeur, curiosite) qui colorent tout ce qu'il dit.
 *  * MEMOIRE : il se souvient du joueur (nombre de rencontres, s'il a ete
 *    bouscule, le temps passe depuis la derniere visite) et ses repliques
 *    changent en consequence -- exactement le principe de la "rancune".
 *  * GRAMMAIRE PROCEDURALE : les repliques sont des modeles troues de
 *    {symboles}, remplaces en cascade par des mots tires au hasard.
 *    Quelques lignes -> des centaines de phrases differentes.
 */
object VillagerAI {

    // ------------------------------------------------------------ memoire

    class Memoire {
        var rencontres = 0            // combien de fois on lui a parle
        var derniereRencontre = 0f    // horloge de jeu de la derniere discussion
        var bouscule = 0              // le heros lui a fonce dedans (rancune legere)
        var vexe = 0                  // moqueries recentes : trop, et c'est la COLERE
        var connaitExploits = false   // a entendu parler du boss vaincu
    }

    // ------------------------------------------------------------ personnalites

    class Perso(
        val nom: String,
        val metier: String,          // cle de sa grammaire de metier
        val bavard: Float,           // 0 muet .. 1 moulin a paroles
        val grognon: Float           // 0 adorable .. 1 rabat-joie
    ) {
        val memoire = Memoire()
    }

    /** Les 10 habitants de l'ile, dans l'ordre des sprites npc1..npc10. */
    fun creerVillageois(seed: Long): List<Perso> {
        val r = Random(seed)
        val bases = listOf(
            Perso("Marthe", "aubergiste", 0.9f, 0.2f),
            Perso("Bran", "forgeron", 0.4f, 0.8f),
            Perso("Lila", "herboriste", 0.7f, 0.1f),
            Perso("Tomas", "pecheur", 0.6f, 0.4f),
            Perso("Agathe", "doyenne", 0.8f, 0.3f),
            Perso("Milo", "gamin", 1.0f, 0.0f),
            Perso("Rosa", "fermiere", 0.5f, 0.5f),
            Perso("Ulric", "garde", 0.3f, 0.7f),
            Perso("Nina", "couturiere", 0.7f, 0.2f),
            Perso("Pip", "reveur", 0.9f, 0.1f),
            Perso("Kaos", "punk", 0.8f, 0.9f),
            // La bande du PUNK CLUB (sprites punk1..7)
            Perso("Riff", "punk", 0.7f, 0.5f),
            Perso("Vex", "punk", 0.8f, 0.4f),
            Perso("Sid", "punk", 0.6f, 0.6f),
            Perso("Brik", "punk", 0.4f, 0.8f),
            Perso("Nox", "punk", 0.3f, 0.7f),
            Perso("Patch", "punk", 1.0f, 0.1f),
            Perso("Krust", "punk", 0.3f, 0.9f),
            // Les 10 heros de la GUILDE (indices 18 a 27)
            Perso("Sire Aldric", "chevalier", 0.6f, 0.3f),
            Perso("Morgane", "magicienne", 0.5f, 0.4f),
            Perso("Thorin", "guerrier", 0.4f, 0.7f),
            Perso("Elara", "archere", 0.7f, 0.2f),
            Perso("Barnabe", "barde", 1.0f, 0.0f),
            Perso("Ysolde", "paladine", 0.6f, 0.3f),
            Perso("Gareth", "rodeur", 0.3f, 0.5f),
            Perso("Luna", "aventuriere", 0.8f, 0.2f),
            Perso("Cedric", "moine", 0.7f, 0.1f),
            Perso("Freya", "valkyrie", 0.5f, 0.6f),
            // Le professeur de magie du Grand Arbre (indice 28)
            Perso("Maitre Zephyrin", "magicien", 1.0f, 0.6f)
        )
        // Le hasard de la graine module legerement les traits : chaque partie
        // a des villageois un peu differents.
        return bases.map {
            Perso(
                it.nom, it.metier,
                (it.bavard + r.nextFloat() * 0.2f - 0.1f).coerceIn(0f, 1f),
                (it.grognon + r.nextFloat() * 0.2f - 0.1f).coerceIn(0f, 1f)
            )
        }
    }

    // ------------------------------------------------------------ grammaire

    /**
     * La grammaire commune : modeles par situation + pools de mots.
     * Convention identique au dialogue.py d'origine : {symbole} = tirage
     * dans la liste du meme nom, en cascade.
     */
    private val G: Map<String, List<String>> by lazy { mapOf(
        // --- situations (choisies par le "cerveau" selon la memoire)
        "premiere" to listOf(
            "{salut} ! {qui_es_tu} ?",
            "{salut}, {etranger} ! {qui_es_tu} ?",
            "Oh ! {un_visiteur} ! {salut} !"
        ),
        "retrouvailles" to listOf(
            "{salut}, {surnom} ! {content} !",
            "Te revoila, {surnom} ! {content} !",
            "{content}, {surnom} ! {question_sympa} ?"
        ),
        "habitue" to listOf(
            "{surnom} ! {question_sympa} ?",
            "Encore toi, {surnom} ! {taquin}",
            "{salut} ! {potin}"
        ),
        "longtemps" to listOf(
            "Ca faisait longtemps, {surnom} ! {content} !",
            "On te croyait perdu dans le donjon, {surnom} !",
            "Te voila enfin ! {potin}"
        ),
        "bouscule" to listOf(
            "He, regarde ou tu vas, {etranger} !",
            "Doucement ! {grognement} !",
            "{grognement} ! On ne court pas comme ca ici !"
        ),
        "heros" to listOf(
            "Alors c'est TOI qui as vaincu le monstre ! {admiration} !",
            "{admiration} ! Tout le village parle de ton exploit !",
            "Le heros du donjon ! {admiration} !"
        ),
        // --- pools communs
        "salut" to listOf("Bonjour", "Salut", "Bien le bonjour", "Hola", "Bienvenue"),
        "etranger" to listOf("voyageur", "etranger", "aventurier", "ami"),
        "un_visiteur" to listOf("un visiteur", "une tete nouvelle", "du monde"),
        "qui_es_tu" to listOf(
            "tu viens du portail", "d'ou sors-tu donc", "qui es-tu donc",
            "que viens-tu chercher ici"
        ),
        "surnom" to listOf("l'ami", "l'aventurier", "champion", "voyageur"),
        "content" to listOf(
            "ca fait plaisir de te voir", "quelle bonne surprise",
            "content de te revoir", "tu tombes bien"
        ),
        "question_sympa" to listOf(
            "belle journee, non", "tout va comme tu veux",
            "le donjon ne t'a pas trop malmene", "tu restes un peu"
        ),
        "taquin" to listOf(
            "Tu vas finir par habiter ici !", "Le village te plait, hein !",
            "Toujours a trainer par ici !"
        ),
        "grognement" to listOf("Nom d'un chien", "Sapristi", "Par ma barbe", "Tudieu"),
        "admiration" to listOf(
            "quel courage", "incroyable", "tu es une legende",
            "je n'en reviens pas"
        ),
        // --- potins : la vie du village (partages par tous)
        "potin" to listOf(
            "Il parait que {untel} a vu {truc_bizarre} pres de {lieu}.",
            "{untel} raconte que {truc_bizarre} rode la nuit...",
            "Entre nous : {untel} {secret}.",
            "On dit que le donjon cache encore {tresor}."
        ),
        "untel" to listOf("Marthe", "Bran", "le petit Milo", "la doyenne", "Tomas", "Rosa"),
        "truc_bizarre" to listOf(
            "une lumiere etrange", "un monstre", "un fantome",
            "des bruits sous la terre", "une ombre"
        ),
        "lieu" to listOf("la plage", "les bois", "le vieux rocher", "les barques", "la source"),
        "secret" to listOf(
            "cache un tresor sous son plancher", "ne dort plus depuis la pleine lune",
            "parle tout seul aux mouettes", "a peur du noir"
        ),
        "tresor" to listOf("une salle secrete", "un tresor englouti", "une porte scellee", "de l'or"),
        // --- la CONVERSATION : reactions aux reponses du heros
        "relance" to listOf(
            "Puisque ca t'interesse... {potin}",
            "Ah, enfin quelqu'un qui ecoute ! {potin}",
            "Alors ouvre grand tes oreilles : {potin}",
            "{grognement}, un public ! Bon... {potin}",
            "Tu veux TOUT savoir, hein ? {potin}"
        ),
        "vexe" to listOf(
            "{grognement} ! On ne se moque pas de {moi_meme} !",
            "Tres drole. Vraiment. {boude}",
            "Repete un peu, pour voir ?!",
            "{grognement} ! Et c'est TOI qui dis ca ?!",
            "Pff. {boude}"
        ),
        "furieux" to listOf(
            "CA SUFFIT ! {grognement} de {grognement} !!",
            "DEHORS ! ... Enfin non, reste. MAIS QUAND MEME !",
            "{grognement} !!! Je compte jusqu'a dix. UN. DEUX. TROIS...",
            "AAARGH ! Tu me fais sortir de mes gonds, la !!"
        ),
        "moi_meme" to listOf("moi", "quelqu'un de mon age", "un honnete artisan", "les gens d'ici"),
        "boude" to listOf("Je boude.", "Na.", "Je ne dis plus rien.", "Tu me vexes, la."),
        "aurevoir" to listOf(
            "A la prochaine, {surnom} !",
            "Bonne route, {surnom} !",
            "File, et reviens vite !",
            "{salut} bien, et gare aux mines !",
            "Tu sais ou me trouver, {surnom}."
        )
    ) }

    /** Les grammaires de metier : chaque villageois a SES sujets a lui. */
    private val METIERS: Map<String, List<String>> by lazy { mapOf(
        "aubergiste" to listOf(
            "Une bonne soupe, ca te dirait ?",
            "Mon auberge ouvrira bientot, promis !",
            "Tu as une mine affamee, toi."
        ),
        "forgeron" to listOf(
            "Ta lame merite un bon affutage.",
            "Le metier rentre, comme le clou.",
            "Rapporte-moi du minerai du donjon, un jour."
        ),
        "herboriste" to listOf(
            "Les fleurs bleues soignent, retiens-le.",
            "Je cherche des herbes rares, ouvre l'oeil.",
            "Ton coeur bat fort, bois une tisane."
        ),
        "pecheur" to listOf(
            "La mer est bonne, aujourd'hui.",
            "Un jour j'attraperai LE poisson.",
            "Mes barques ont vu des choses, crois-moi."
        ),
        "doyenne" to listOf(
            "J'ai connu ce village avant le portail...",
            "Les anciens parlaient d'une porte scellee.",
            "Respecte l'ile, elle te le rendra."
        ),
        "gamin" to listOf(
            "T'as une VRAIE epee ?! Montre !",
            "Un jour j'irai dans le donjon, moi aussi !",
            "On joue a chat ? T'es le chat !"
        ),
        "fermiere" to listOf(
            "Les betes sentent l'orage venir.",
            "Le potager donne bien cette annee.",
            "Tu n'aurais pas vu ma poule ?"
        ),
        "garde" to listOf(
            "Rien a signaler. Comme toujours.",
            "Je garde ce village, portail ou pas.",
            "Pas de grabuge ici, compris ?"
        ),
        "couturiere" to listOf(
            "Ta cape est dechiree, passe me voir.",
            "Le rouge te va bien, tres heroique.",
            "Je broderais bien ton blason."
        ),
        "reveur" to listOf(
            "J'ai encore reve de la mer qui parle...",
            "Les nuages dessinent des dragons, regarde.",
            "Et si le portail menait aux etoiles ?"
        ),
        "magicien" to listOf(
            "Les runes me chatouillent. Bon signe. Ou une allergie.",
            "J'ai transforme une mouette en theiere. Elle vole ENCORE.",
            "CHUT !! ... Pardon, je parlais a l'arbre. Il raconte sa vie.",
            "L'important en magie, c'est le CHAPEAU. J'ai perdu le mien en 1372.",
            "Ne touche pas aux fioles. Surtout la verte. SURTOUT la verte.",
            "Mon maitre disait : un sort rate est une lecon. J'ai BEAUCOUP appris.",
            "Cet arbre a 3000 ans et il fait encore des feuilles. MOTIVANT, non ?"
        ),
        "punk" to listOf(
            "Je boycotte ce dialogue. ... Bon, ok, salut.",
            "No futur ! Enfin sauf pour toi, t'es cool.",
            "Le systeme du donjon nous exploite, reveille-toi !",
            "J'ai tague le sous-sol. Ils ont rien vu.",
            "Les coffres appartiennent a tout le monde !",
            "Ni dieu, ni maitre, ni mini-demineur.",
            "Ma bombe Rebel Ink traine chez moi, sers-toi.",
            "Le distributeur ? 2,50 la canette. L'arnaque.",
            "Goute mes champis : tu verras l'invisible.",
            "L'ile cache une autre entree. Ouvre les yeux."
        )
    ) }

    private val RX by lazy { Regex("\\{(\\w+)\\}") }

    /** Remplace chaque {symbole} en cascade (garde-fou anti-boucle). */
    private fun expanser(modele: String, r: Random, dict: Map<String, List<String>> = G): String {
        var t = modele
        repeat(8) {
            if (!t.contains('{')) return@repeat
            t = RX.replace(t) { m ->
                val pool = dict[m.groupValues[1]]
                if (pool.isNullOrEmpty()) m.value else pool[r.nextInt(pool.size)]
            }
        }
        return t.replaceFirstChar { it.uppercase() }
    }

    // ------------------------------------------ les histoires du distributeur

    /** La grammaire du DISTRIBUTEUR 8.6 : des histoires de punk a chien. */
    private val GP: Map<String, List<String>> by lazy {
        mapOf(
            "histoire" to listOf(
                "GRZZT... Y'a {punknom} et son chien {chien} : ils ont {exploit}. {chute}",
                "Un soir au {lieupunk}, {punknom} a {exploit}. {chute}",
                "{punknom} m'a jure que son chien {chien} {exploitchien}. {chute}",
                "Legende du {lieupunk} : {punknom} a {exploit}, pendant que {chien} {reactionchien}. {chute}",
                "Il parait que {punknom} a echange {objet} contre {objet2}. Son chien {chien} en rit encore. {chute}",
                "GRZZT... {punknom} dit que ma monnaie sert la revolution. {chute}"
            ),
            "punknom" to listOf(
                "Crado", "La Teigne", "Vomito", "Zonzon", "Kro",
                "La Fouine", "Gribouille", "Steplait"
            ),
            "chien" to listOf(
                "Mastoc", "Clebs", "Bieraubeurre", "Puce", "Rototo", "Kilo", "Ta-Gueule"
            ),
            "lieupunk" to listOf(
                "squat de la gare", "concert des Rats Crades", "parking du supermarche",
                "vieux pont", "festival de la boue"
            ),
            "exploit" to listOf(
                "echange sa crete contre trois canettes",
                "dormi dans la benne du boulanger",
                "monte un groupe avec deux casseroles",
                "fait la manche en jonglant avec des canettes vides",
                "traverse le pays en stop avec un panneau AILLEURS",
                "gagne un concours de cri contre une mouette"
            ),
            "exploitchien" to listOf(
                "ouvre les canettes avec les dents",
                "aboie uniquement sur les videurs",
                "dort dans un etui de basse",
                "porte un bandana plus propre que son maitre",
                "fait la tournee des squats tout seul"
            ),
            "reactionchien" to listOf(
                "gardait la couronne de capsules", "remuait la queue en rythme",
                "dormait sur l'ampli", "comptait les canettes"
            ),
            "objet" to listOf(
                "sa veste a clous", "son unique lacet",
                "sa collection de capsules", "son mediator porte-bonheur"
            ),
            "objet2" to listOf(
                "un solo de basse", "deux concerts gratuits",
                "un shampoing pour chien", "une carte du monde dessinee a la main"
            ),
            "chute" to listOf(
                "Punk's not dead, il sent juste fort.",
                "Moralite : jamais sans son chien.",
                "Et la canette etait meme pas fraiche.",
                "No futur, mais quel present !",
                "8.6 raisons d'y croire.",
                "Le chien, lui, avait tout compris."
            )
        )
    }

    /** Une histoire de punk a chien racontee par la machine. */
    fun histoireDistributeur(r: Random): String {
        val pool = GP["histoire"] ?: return "GRZZT... 2,50... GRZZT..."
        return expanser(pool[r.nextInt(pool.size)], r, GP)
    }

    // ------------------------------------------------------------ le "cerveau"

    /**
     * Choisit la situation selon la memoire (le coeur de la mini-IA),
     * puis genere la replique. gameTime sert a mesurer "ca faisait longtemps".
     */
    fun parler(p: Perso, gameTime: Float, bossVaincu: Boolean, r: Random): String {
        val m = p.memoire
        val depuis = gameTime - m.derniereRencontre
        val situation = when {
            m.bouscule > 0 && p.grognon > 0.4f -> { m.bouscule--; "bouscule" }
            bossVaincu && !m.connaitExploits -> { m.connaitExploits = true; "heros" }
            m.rencontres == 0 -> "premiere"
            depuis > 180f -> "longtemps"
            m.rencontres < 3 -> "retrouvailles"
            else -> "habitue"
        }
        m.rencontres++
        m.derniereRencontre = gameTime

        // Un villageois bavard glisse parfois son sujet de metier a la place
        val metierLignes = METIERS[p.metier] ?: emptyList()
        val ligne = if (situation == "habitue" && metierLignes.isNotEmpty() &&
            r.nextFloat() < 0.35f + p.bavard * 0.3f
        ) {
            metierLignes[r.nextInt(metierLignes.size)]
        } else {
            val pool = G[situation] ?: G["habitue"]!!
            expanser(pool[r.nextInt(pool.size)], r)
        }
        return ligne
    }

    /**
     * Le heros REPOND (choix multiple) : 0 = relancer, 1 = se moquer,
     * 2 = prendre conge. La moquerie s'accumule dans la memoire (vexe) et,
     * selon le caractere, finit en vraie COLERE.
     */
    fun repondre(p: Perso, choix: Int, r: Random): String {
        val m = p.memoire
        return when (choix) {
            0 -> {
                m.vexe = (m.vexe - 1).coerceAtLeast(0)
                expanser(G["relance"]!!.let { it[r.nextInt(it.size)] }, r)
            }
            1 -> {
                m.vexe = (m.vexe + 1).coerceAtMost(4)
                val pool = if (m.vexe >= 2 || p.grognon > 0.7f) G["furieux"]!! else G["vexe"]!!
                expanser(pool[r.nextInt(pool.size)], r)
            }
            else -> expanser(G["aurevoir"]!!.let { it[r.nextInt(it.size)] }, r)
        }
    }

    /** Petit mot lance sans qu'on lui parle (les bavards seulement). */
    fun marmonner(p: Perso, r: Random): String? {
        if (r.nextFloat() > p.bavard * 0.5f) return null
        val metierLignes = METIERS[p.metier] ?: return null
        return metierLignes[r.nextInt(metierLignes.size)]
    }

    /** Le heros lui a fonce dedans. */
    fun bousculer(p: Perso) {
        p.memoire.bouscule = (p.memoire.bouscule + 1).coerceAtMost(3)
    }
}
