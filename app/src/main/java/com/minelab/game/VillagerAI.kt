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
            Perso("Krust", "punk", 0.3f, 0.9f)
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
        "tresor" to listOf("une salle secrete", "un tresor englouti", "une porte scellee", "de l'or")
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
    private fun expanser(modele: String, r: Random): String {
        var t = modele
        repeat(8) {
            if (!t.contains('{')) return@repeat
            t = RX.replace(t) { m ->
                val pool = G[m.groupValues[1]]
                if (pool.isNullOrEmpty()) m.value else pool[r.nextInt(pool.size)]
            }
        }
        return t.replaceFirstChar { it.uppercase() }
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
