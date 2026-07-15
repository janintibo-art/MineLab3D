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
        // --- les VRAIS souvenirs ---
        var confidences = 0           // gentillesses recues : la relation grandit
        var moqueries = 0             // moqueries subies, JAMAIS oubliees
        var potinEnCours = ""         // la suite du potin, si le heros veut la connaitre
        var dernierSujet = ""         // de quoi parlait-on la derniere fois ?
        var derniereLigne = ""        // anti-radotage
        val racontes = HashSet<Int>() // les potins deja racontes a CE heros
        val faitsDits = HashSet<String>()  // les exploits du heros deja commentes

        /** Le niveau de relation : les moqueries pesent double. */
        fun relation() = confidences - moqueries * 2
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
        "hostile" to listOf(
            "Ah. Toi.",
            "{grognement}... qu'est-ce que tu veux ENCORE ?",
            "Tiens. L'insolent. Fais vite.",
            "On ne t'a pas assez vu, peut-etre ?"
        ),
        "confident_salut" to listOf(
            "MON ami ! {content} !",
            "{surnom} ! Viens la, j'ai des choses a te dire !",
            "Enfin quelqu'un de confiance ! {content} !"
        ),
        "apaise" to listOf(
            "Bon... excuses acceptees. {question_sympa} ?",
            "Hmph. N'en parlons plus. {question_sympa} ?",
            "Ca va pour cette fois. Mais je n'oublie RIEN, moi."
        ),
        "flatte" to listOf(
            "Ah, tu me flattes ! Continue, ca ne fait pas de mal.",
            "Heh. On dit que je suis {qualite}, c'est vrai.",
            "Voila quelqu'un qui a du gout !"
        ),
        "merci_confiance" to listOf(
            "Merci... ca compte, tu sais. {question_sympa} ?",
            "Je savais que je pouvais compter sur toi.",
            "Toi, tu es de la famille maintenant."
        ),
        "qualite" to listOf("d'excellente compagnie", "trop genereux", "le meilleur du village", "irremplacable"),
        "rappel" to listOf(
            "La derniere fois, on parlait de {dernier_sujet}, non ? Eh bien j'ai du NOUVEAU :",
            "Toujours curieux de {dernier_sujet} ? Alors ecoute :",
            "Tu te souviens, {dernier_sujet} ? L'histoire continue :"
        ),
        "consequence" to listOf(
            "il dort avec une lampe", "il ne passe plus par la",
            "il salue les rochers, au cas ou", "il refuse d'en reparler"
        ),
        "untel2" to listOf("Ulric", "Nina", "Pip", "la doyenne", "Franki"),
        "chute_potin" to listOf(
            "Motus, hein !", "Je n'ai RIEN dit.", "Tu ne tiens pas ca de moi.",
            "Le village est petit, les oreilles sont grandes."
        ),
        "objet_dispute" to listOf(
            "de tarte aux algues", "de chaise empruntee", "de poule voyageuse",
            "de record de peche conteste"
        ),
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
        "chevalier" to listOf(
            "Par mon honneur, ce village sera defendu !",
            "Mon armure grince. C'est le sel marin. Ou l'age.",
            "Un chevalier sans dragon, c'est long, les journees."
        ),
        "magicienne" to listOf(
            "Zephyrin m'a tout appris. Sauf le rangement.",
            "Je lis l'avenir dans les vagues. Il sera mouille.",
            "La magie, c'est 10% de talent et 90% de sourcils fronces."
        ),
        "guerrier" to listOf(
            "Mon marteau s'ennuie. Trouve-lui du travail.",
            "Dans ma montagne, on saluait plus fort que ca.",
            "La biere d'ici est etrange. 8.6, tu connais ?"
        ),
        "archere" to listOf(
            "Je peux toucher une pomme a cent pas. Les mouettes le savent.",
            "Le vent d'ici ment. Mes fleches s'en souviennent.",
            "Vise toujours plus haut. Sauf au plafond."
        ),
        "barde" to listOf(
            "Je compose une ballade sur le SLIP legendaire !",
            "Tu veux un couplet ? Tout le monde veut un couplet.",
            "La rime en 'ectoplasme' me resiste encore."
        ),
        "paladine" to listOf(
            "La lumiere me guide. Le phare aussi, la nuit.",
            "J'ai jure de proteger la veuve, l'orphelin et le distributeur.",
            "Mon serment m'interdit de mentir. Ta cape est affreuse."
        ),
        "rodeur" to listOf(
            "J'ai piste un ecureuil trois jours. Il m'a eu.",
            "Les traces ne mentent jamais. Les mouettes, si.",
            "Je dors d'un oeil. L'autre surveille Barnabe."
        ),
        "aventuriere" to listOf(
            "J'ai deja ouvert des coffres plus gros que toi.",
            "Le danger, c'est mon metier. Les impots aussi, helas.",
            "Cette ile cache un secret. Je le SENS. La, sous mes bottes."
        ),
        "moine" to listOf(
            "Om... pardon, tu disais ?",
            "La paix interieure, ca se muscle. Comme les mollets.",
            "J'ai fait voeu de silence, une fois. Douze minutes."
        ),
        "valkyrie" to listOf(
            "SKAL ! Pardon, reflexe.",
            "Dans mon pays, la mer est SOLIDE l'hiver. Imagine.",
            "Ton epee est correcte. Ton cri de guerre, a travailler."
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

    /** Les potins en deux temps : l'accroche... et la suite si on la reclame. */
    private val POTINS: List<Pair<String, String>> by lazy { listOf(
        "Il parait que {untel} a vu {truc_bizarre} pres de {lieu}..." to
            "Eh bien {untel} jure que ca lui a PARLE. Depuis, {consequence} ! {chute_potin}",
        "Entre nous : {untel} {secret}..." to
            "Et le pire ? Tout le village le sait SAUF {untel2} ! {chute_potin}",
        "On raconte que le donjon cache encore {tresor}..." to
            "Le garde dit que c'est faux. C'est EXACTEMENT ce qu'il dirait pour le garder, non ?",
        "{untel} ne parle plus a {untel2} depuis la derniere fete..." to
            "La raison ? Une histoire {objet_dispute}. Je n'invente RIEN.",
        "Une barque a disparu l'autre nuit, sans un bruit..." to
            "Revenue au matin, pleine de sable NOIR. Personne n'ose demander a qui.",
        "Le distributeur aurait raconte une histoire VRAIE, une fois..." to
            "Verifiee par {untel} : le punk et son chien EXISTENT. Ils passeraient au large, certaines nuits.",
        "{untel} s'entraine en cachette derriere {lieu}..." to
            "A quoi ? A lever des tornades en agitant les bras ! Comme un poulet ! Je ne juge pas.",
        "Le Grand Arbre de l'ile lointaine aurait BOUGE de trois pas..." to
            "Trois pas vers la mer ! Le mage dit que c'est pour la vue. Moi je dis qu'il FUIT quelque chose."
    ) }

    /** Les aveux de confidence, par metier (avec un fonds commun). */
    private val AVEUX: Map<String, List<String>> by lazy { mapOf(
        "magicien" to listOf(
            "la mouette-theiere... c'etait mon CHAPEAU. Le sort a rate deux fois.",
            "je n'ai jamais eu 400 ans. J'en ai 397. La coquetterie, tu comprends."
        ),
        "punk" to listOf(
            "je range ma chambre. EN CACHETTE. Si ca se sait, je suis fini.",
            "le systeme, tout ca... mais le distributeur me fait credit. CHUT."
        ),
        "gamin" to listOf(
            "j'ai deja vu l'entree secrete, au nord-ouest. J'ai EU PEUR.",
            "je dors encore avec mon doudou-mouette. Tu le dis, t'es mort."
        ),
        "doyenne" to listOf(
            "la porte scellee du donjon... c'est nous qui l'avons fermee. Il y a longtemps.",
            "je triche aux cartes contre Ulric. Depuis quarante ans."
        ),
        "garde" to listOf(
            "je n'ai jamais degaine cette epee. Elle est peut-etre rouillee DEDANS.",
            "la nuit, je compte les mouettes pour m'endormir. Un garde, oui."
        ),
        "commun" to listOf(
            "j'ai peur du noir du donjon. La, c'est dit.",
            "je parle aux mouettes. ELLES, au moins, ecoutent.",
            "mon plus grand reve ? Voir ce qu'il y a DERRIERE la mer."
        )
    ) }

    /** Ce que le village raconte sur les exploits du heros. */
    private val FAITS_LIGNES: Map<String, String> by lazy { mapOf(
        "heros" to "Alors c'est TOI qui as vaincu le monstre du donjon ! {admiration} !",
        "slip" to "C'est toi qui as repeche le slip de Pierre ?! {admiration} ! Quelle epopee !",
        "peche" to "On dit que tu peches comme un vieux loup de mer, maintenant !",
        "tags" to "J'ai vu les tags du sous-sol... c'est toi, hein ? Je ne dirai rien. Beau travail.",
        "magie" to "Il parait que le mage de l'arbre t'apprend la MAGIE ! Montre ! ... Non, pas ici, pas ici !",
        "marin" to "On t'a vu RAMER vers l'ile lointaine ! Plus personne n'y allait depuis des lunes."
    ) }

    /** Les sujets PERSONNELS : chaque habitant a ses obsessions a lui. */
    private val PERSOS: Map<String, List<String>> by lazy { mapOf(
        "Marthe" to listOf(
            "Je teste une soupe aux algues. Les courageux sont invites.",
            "Mon auberge aura des lits moelleux comme la mie fraiche.",
            "Un jour, ce village sentira le pain chaud jusqu'a la mer."
        ),
        "Bran" to listOf(
            "Ce que je forge tient cent ans. Comme mes rancunes.",
            "Le feu, ca ne se commande pas. Ca se merite.",
            "Ton epee ? Je l'entends grincer d'ici. Reviens me voir."
        ),
        "Lila" to listOf(
            "Les champignons de Kaos m'obsedent. Scientifiquement, hein.",
            "Je note TOUT dans mon herbier. Meme toi, tu y es.",
            "Sens cette fleur. Non ? Tant pis pour toi."
        ),
        "Tomas" to listOf(
            "Pierre et Franki pechent MAL. Mais ne leur dis pas.",
            "Mes bottes-pots-de-fleurs, c'est de l'ART, compris ?",
            "La mer donne, la mer reprend. Surtout les slips, parait-il."
        ),
        "Agathe" to listOf(
            "De mon temps, le portail n'existait pas. On s'ennuyait FERME.",
            "Mes genoux predisent la pluie. Fiables a 40%.",
            "Approche, que je te regarde. Hm. Tu manges assez ?"
        ),
        "Milo" to listOf(
            "J'ai une cachette SECRETE ! ... Oups.",
            "Quand je serai grand, je serai HEROS. Ou mouette.",
            "T'as vu l'entree scellee au nord-ouest ?! Moi OUI !"
        ),
        "Rosa" to listOf(
            "Mes poules ont des prenoms. Ne t'attache pas a Josette.",
            "Le punk, ca fait pondre. C'est prouve. Par moi.",
            "Un potager, c'est un donjon en plus lent."
        ),
        "Ulric" to listOf(
            "Je surveille cette ile depuis douze ans. Zero invasion. Efficace.",
            "L'entree scellee, au nord-ouest ? Je la surveille AUSSI.",
            "Un garde ne dort jamais. Il repose ses paupieres."
        ),
        "Nina" to listOf(
            "Je reve d'une cape en fibres d'algues. Ne ris pas.",
            "Tes coutures sont une honte. Passe me voir, gratuit.",
            "La mode d'ici a cent ans de retard. Je compte bien aider."
        ),
        "Pip" to listOf(
            "Cette nuit, j'ai reve que l'arbre marchait...",
            "Kaos dit que je squatte. Moi je dis que j'habite POETIQUEMENT.",
            "Si tu ecoutes les coquillages, ils racontent la meteo d'hier."
        ),
        "Kaos" to listOf(
            "Le village m'appelle 'le punk'. Ils ont RAISON.",
            "Mes champis ? Bio. Locaux. Revolutionnaires.",
            "Un jour je monterai un groupe. Nom provisoire : LES MINES."
        )
    ) }

    /** Les tics de langage : la petite phrase qui signe chaque personnage. */
    private val SIGNATURE: Map<String, List<String>> by lazy { mapOf(
        "Marthe" to listOf("Comme dirait ma soupe.", "Ca creuse, tout ca !"),
        "Bran" to listOf("Hmph.", "*fait sonner l'enclume*"),
        "Lila" to listOf("Tisane ?", "*note dans l'herbier*"),
        "Tomas" to listOf("Comme la maree.", "*regarde ses bottes avec fierte*"),
        "Agathe" to listOf("De mon temps, deja.", "*hoche la tete lentement*"),
        "Milo" to listOf("TROP BIEN !!", "*sautille*"),
        "Rosa" to listOf("Mes poules en gloussent.", "*compte ses poules du regard*"),
        "Ulric" to listOf("R.A.S.", "*scrute l'horizon*"),
        "Nina" to listOf("Ca s'assortirait avec ta cape.", "*mesure du regard*"),
        "Pip" to listOf("...", "*regarde les nuages*"),
        "Kaos" to listOf("No futur.", "Oi !"),
        "Maitre Zephyrin" to listOf("*etincelles*", "PAS la fiole verte !"),
        "Sire Aldric" to listOf("Par mon honneur !", "*salue*"),
        "Barnabe" to listOf("*gratte trois accords*", "Ca rime, en plus !"),
        "Freya" to listOf("SKAL !", "*frappe son bouclier*"),
        "Cedric" to listOf("Om.", "*inspire longuement*"),
        "Riff" to listOf("Oi oi oi !", "*ajuste sa crete*")
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
    fun parler(p: Perso, gameTime: Float, bossVaincu: Boolean, r: Random): String =
        discuter(p, gameTime, if (bossVaincu) setOf("heros") else emptySet(), r).texte

    // ------------------------------------------------ la CONVERSATION

    const val EF_INTERESSE = 0    // "et ensuite ?" / "un potin ?"
    const val EF_MOQUE = 1        // moquerie (memorisee !)
    const val EF_BYE = 2          // prendre conge
    const val EF_EXCUSE = 3       // demander pardon
    const val EF_COMPLIMENT = 4   // flatter (la relation grandit)
    const val EF_PROMESSE = 5     // jurer le secret (grande confiance)
    const val EF_TRAHISON = 6     // menacer de tout repeter
    const val EF_METIER = 7       // parle-moi de ton metier
    const val EF_SECRET = 8       // confie-moi un secret (confidents seulement)

    class Reponse(val texte: String, val effet: Int)
    class Replique(val texte: String, val reponses: List<Reponse>)

    /** Les reponses proposees selon le TYPE de la replique entendue. */
    private fun reponsesPour(type: String, p: Perso, r: Random): List<Reponse> {
        fun pick(vararg v: String) = v[r.nextInt(v.size)]
        val bye = Reponse(pick("Bonne journee !", "A plus tard !", "Faut que j'y aille."), EF_BYE)
        return when (type) {
            "premiere" -> listOf(
                Reponse(pick("Je viens du portail.", "Un aventurier, tout simplement."), EF_INTERESSE),
                Reponse("Ca ne te regarde pas.", EF_MOQUE),
                bye
            )
            "hostile" -> listOf(
                Reponse(pick("Pardon pour tout...", "Faisons la paix, tu veux ?"), EF_EXCUSE),
                Reponse("Toujours aussi aimable !", EF_MOQUE),
                Reponse("Je repasse plus tard.", EF_BYE)
            )
            "potin" -> listOf(
                Reponse(pick("Et ensuite ?!", "Raconte la suite !!"), EF_INTERESSE),
                Reponse(pick("Je n'y crois pas une seconde.", "N'importe quoi..."), EF_MOQUE),
                bye
            )
            "potin_suite" -> listOf(
                Reponse(pick("Incroyable !", "Quelle histoire !"), EF_COMPLIMENT),
                Reponse("Tu inventes TOUT !", EF_MOQUE),
                Reponse(pick("Motus, promis.", "Ca restera entre nous."), EF_PROMESSE)
            )
            "metier" -> listOf(
                Reponse(pick("Fascinant, continue !", "Et ca marche ?"), EF_METIER),
                Reponse("(baillement discret)", EF_MOQUE),
                bye
            )
            "colere" -> listOf(
                Reponse(pick("Pardon, pardon !!", "Je retire, je retire !"), EF_EXCUSE),
                Reponse("C'etait pour rire...", EF_MOQUE),
                Reponse("(s'eclipser discretement)", EF_BYE)
            )
            "secret" -> listOf(
                Reponse(pick("Motus et bouche cousue.", "Ta confiance m'honore."), EF_PROMESSE),
                Reponse("HA ! Tout le village le saura !", EF_TRAHISON),
                Reponse("Merci de me faire confiance.", EF_COMPLIMENT)
            )
            "fait" -> listOf(
                Reponse(pick("Tout est vrai. TOUT.", "C'est bien moi !"), EF_COMPLIMENT),
                Reponse(pick("On exagere beaucoup...", "Les nouvelles vont vite !"), EF_INTERESSE),
                bye
            )
            else -> {   // papotage libre : selon la relation, le secret se debloque
                val base = mutableListOf(
                    Reponse(pick("Raconte-moi un potin !", "Quoi de neuf au village ?"), EF_INTERESSE),
                    Reponse(pick("Parle-moi de ton metier.", "Et le travail, ca va ?"), EF_METIER)
                )
                if (p.memoire.relation() >= 6) {
                    base.add(Reponse("Confie-moi un secret...", EF_SECRET))
                } else {
                    base.add(bye)
                }
                base
            }
        }
    }

    /** Fabrique une replique en evitant de radoter. */
    private fun fab(p: Perso, texteBrut: String, type: String, r: Random): Replique {
        val m = p.memoire
        var t = texteBrut
        if (t == m.derniereLigne && t.length < 80) t = "$t ... Oui, je me repete. L'age !"
        m.derniereLigne = texteBrut
        val sig = SIGNATURE[p.nom]
        if (sig != null && t.length < 95 && r.nextFloat() < 0.32f) {
            t = "$t ${sig[r.nextInt(sig.size)]}"
        }
        return Replique(t, reponsesPour(type, p, r))
    }

    /** Un nouveau potin jamais raconte a CE heros (memoire !). */
    private fun potinNouveau(p: Perso, r: Random): Replique {
        val m = p.memoire
        var prefixe = ""
        if (m.racontes.size >= POTINS.size) {
            m.racontes.clear()
            prefixe = "Je te les ai TOUS racontes ! Tant pis, je recommence : "
        }
        var k = r.nextInt(POTINS.size)
        var essais = 0
        while (k in m.racontes && essais < 16) { k = r.nextInt(POTINS.size); essais++ }
        m.racontes.add(k)
        m.potinEnCours = POTINS[k].second
        m.dernierSujet = "des potins"
        return fab(p, prefixe + expanser(POTINS[k].first, r), "potin", r)
    }

    /** Une ligne de metier OU un sujet personnel : chacun ses obsessions. */
    private fun ligneMetier(p: Perso, r: Random): Replique {
        val perso = PERSOS[p.nom]
        val lignes = if (perso != null && r.nextFloat() < 0.5f) {
            p.memoire.dernierSujet = "ses passions"
            perso
        } else {
            p.memoire.dernierSujet = "son metier"
            METIERS[p.metier] ?: perso ?: listOf("Le travail, toujours le travail.")
        }
        return fab(p, lignes[r.nextInt(lignes.size)], "metier", r)
    }

    /**
     * LE COEUR DE LA DISCUSSION : la premiere replique quand on aborde
     * quelqu'un. Elle depend de la memoire (relation, exploits du heros,
     * dernier sujet) et propose des reponses CONTEXTUELLES.
     */
    fun discuter(p: Perso, gameTime: Float, faits: Set<String>, r: Random): Replique {
        val m = p.memoire
        val depuis = gameTime - m.derniereRencontre
        m.rencontres++
        m.derniereRencontre = gameTime

        // 1. Un exploit du heros dont il n'a pas encore parle ? Priorite !
        val nouveaux = faits.filter { it !in m.faitsDits }
        if (nouveaux.isNotEmpty() && r.nextFloat() < 0.65f) {
            val f = nouveaux[r.nextInt(nouveaux.size)]
            m.faitsDits.add(f)
            if (f == "heros") m.connaitExploits = true
            m.dernierSujet = "tes exploits"
            return fab(p, expanser(FAITS_LIGNES[f] ?: "{admiration} !", r), "fait", r)
        }

        // 2. La situation, selon la memoire et la RELATION
        val rel = m.relation()
        val situation = when {
            m.bouscule > 0 && p.grognon > 0.4f -> { m.bouscule--; "bouscule" }
            rel <= -3 -> "hostile"
            m.rencontres <= 1 -> "premiere"
            depuis > 180f -> "longtemps"
            rel >= 6 -> "confident_salut"
            m.rencontres < 3 -> "retrouvailles"
            else -> "habitue"
        }

        // Il se souvient du dernier sujet, parfois
        if (situation == "habitue" && m.dernierSujet.isNotEmpty() && r.nextFloat() < 0.3f) {
            val rappel = expanser(G["rappel"]!![r.nextInt(G["rappel"]!!.size)], r)
                .replace("{dernier_sujet}", m.dernierSujet)
            val suite = potinNouveau(p, r)
            return Replique("$rappel ${suite.texte}", suite.reponses)
        }

        // Un bavard glisse son metier
        if (situation == "habitue" && r.nextFloat() < 0.35f + p.bavard * 0.3f) {
            return ligneMetier(p, r)
        }

        val type = when (situation) {
            "premiere" -> "premiere"
            "hostile" -> "hostile"
            else -> "libre"
        }
        val pool = G[situation] ?: G["habitue"]!!
        return fab(p, expanser(pool[r.nextInt(pool.size)], r), type, r)
    }

    /** Le heros a choisi une reponse : la conversation CONTINUE. */
    fun reagir(p: Perso, effet: Int, r: Random): Replique {
        val m = p.memoire
        return when (effet) {
            EF_INTERESSE -> if (m.potinEnCours.isNotEmpty()) {
                val suite = m.potinEnCours
                m.potinEnCours = ""
                fab(p, expanser(suite, r), "potin_suite", r)
            } else potinNouveau(p, r)
            EF_METIER -> ligneMetier(p, r)
            EF_MOQUE -> {
                m.vexe = (m.vexe + 1).coerceAtMost(4)
                m.moqueries = (m.moqueries + 1).coerceAtMost(9)
                val pool = if (m.vexe >= 2 || p.grognon > 0.7f) G["furieux"]!! else G["vexe"]!!
                fab(p, expanser(pool[r.nextInt(pool.size)], r), "colere", r)
            }
            EF_EXCUSE -> {
                m.vexe = 0
                fab(p, expanser(G["apaise"]!![r.nextInt(G["apaise"]!!.size)], r), "libre", r)
            }
            EF_COMPLIMENT -> {
                m.confidences = (m.confidences + 1).coerceAtMost(30)
                fab(p, expanser(G["flatte"]!![r.nextInt(G["flatte"]!!.size)], r), "libre", r)
            }
            EF_PROMESSE -> {
                m.confidences = (m.confidences + 2).coerceAtMost(30)
                fab(p, expanser(G["merci_confiance"]!![r.nextInt(G["merci_confiance"]!!.size)], r), "libre", r)
            }
            EF_TRAHISON -> {
                m.moqueries = (m.moqueries + 2).coerceAtMost(9)
                m.vexe = 3
                fab(p, expanser(G["furieux"]!![r.nextInt(G["furieux"]!!.size)], r), "colere", r)
            }
            EF_SECRET -> {
                val pool = AVEUX[p.metier] ?: AVEUX["commun"]!!
                val aveu = pool[r.nextInt(pool.size)]
                m.dernierSujet = "un secret"
                fab(p, "Bon... a TOI je peux le dire : $aveu", "secret", r)
            }
            else -> {   // EF_BYE : au revoir, gentil ou glacial selon la relation
                val pool = if (m.relation() <= -3 || m.vexe >= 2)
                    listOf("C'est ca, file.", "Bon vent. Loin.", "Enfin une bonne idee.")
                else G["aurevoir"]!!.map { expanser(it, r) }
                Replique(pool[r.nextInt(pool.size)], emptyList())
            }
        }
    }

    /** Les choix generiques quand le jeu a lui-meme redige la replique. */
    fun choixLibres(p: Perso, r: Random): List<Reponse> = reponsesPour("libre", p, r)

    // ------------------------------------------ persistance des souvenirs

    /** Serialise les souvenirs de tous (sauvegarde entre les sessions). */
    fun serialiser(persos: List<Perso>): String = persos.joinToString(";") { pp ->
        val m = pp.memoire
        listOf(
            m.rencontres, m.bouscule, m.vexe, m.confidences, m.moqueries,
            if (m.connaitExploits) 1 else 0,
            m.racontes.joinToString("|"),
            m.faitsDits.joinToString("|")
        ).joinToString(",")
    }

    /** Restaure les souvenirs sauvegardes (dans l'ordre des persos). */
    fun restaurer(persos: List<Perso>, data: String?) {
        if (data.isNullOrEmpty()) return
        val parts = data.split(";")
        for (i in persos.indices) {
            val f = parts.getOrNull(i)?.split(",") ?: continue
            val m = persos[i].memoire
            m.rencontres = f.getOrNull(0)?.toIntOrNull() ?: 0
            m.bouscule = f.getOrNull(1)?.toIntOrNull() ?: 0
            m.vexe = f.getOrNull(2)?.toIntOrNull() ?: 0
            m.confidences = f.getOrNull(3)?.toIntOrNull() ?: 0
            m.moqueries = f.getOrNull(4)?.toIntOrNull() ?: 0
            m.connaitExploits = f.getOrNull(5) == "1"
            f.getOrNull(6)?.split("|")?.forEach { it.toIntOrNull()?.let { k -> m.racontes.add(k) } }
            f.getOrNull(7)?.split("|")?.forEach { if (it.isNotEmpty()) m.faitsDits.add(it) }
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
