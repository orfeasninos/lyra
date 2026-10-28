package app.lyra.player.data

import java.text.Collator
import java.text.Normalizer

/**
 * Αλφαβητική σειρά για ονόματα σε οποιαδήποτε γλώσσα: τα τονισμένα γράμματα πάνε δίπλα
 * στα άτονα (É με E, Ά με Α) και κάθε αλφάβητο ταξινομείται σωστά.
 * Νέο Collator σε κάθε κλήση, γιατί δεν είναι thread-safe.
 */
fun <T> byName(selector: (T) -> String): Comparator<T> {
    val collator = Collator.getInstance().apply { strength = Collator.SECONDARY }
    return compareBy(collator, selector)
}

private val DIACRITICS = Regex("[\\u0300-\\u036f]+")

/**
 * Κλειδί αναζήτησης: πεζά, χωρίς τόνους/διαλυτικά και με ενιαία μορφή χαρακτήρων
 * (π.χ. full-width γράμματα). Έτσι το "beyonce" βρίσκει το "Beyoncé" και το "αλεξιου" το "Αλεξίου".
 */
fun searchKey(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFKD)
        .replace(DIACRITICS, "")
        .lowercase()
        .replace('ς', 'σ')
        .trim()
