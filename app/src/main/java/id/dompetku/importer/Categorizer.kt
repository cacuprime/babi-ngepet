package id.dompetku.importer

import id.dompetku.data.db.CategorizationRuleEntity
import id.dompetku.data.db.CategoryEntity
import id.dompetku.domain.JAKARTA
import id.dompetku.domain.Tx
import id.dompetku.domain.TxType

data class Suggestion(val categoryId: Long, val confidence: Float)

/**
 * Suggests a category for a draft from keyword rules (PRD §5.2E). Only rules whose category type
 * matches the draft type apply. Learned rules beat built-ins; then the longest keyword wins.
 */
class Categorizer(private val rules: List<CategorizationRuleEntity>, categories: List<CategoryEntity>) {
    private val typeOf = categories.associate { it.id to it.type }

    fun suggest(d: Draft): Suggestion? {
        val wanted = when (d.type) { TxType.EXPENSE -> "EXPENSE"; TxType.INCOME -> "INCOME"; else -> return null }
        val text = " " + listOfNotNull(d.merchant, d.description).joinToString(" ").lowercase() + " "
        val best = rules.asSequence()
            .filter { typeOf[it.suggestedCategoryId] == wanted && it.keyword.isNotBlank() && text.contains(it.keyword) }
            .sortedWith(compareByDescending<CategorizationRuleEntity> { it.source == "LEARNED" }.thenByDescending { it.keyword.length })
            .firstOrNull() ?: return null
        return Suggestion(best.suggestedCategoryId, best.confidence).takeIf { it.confidence >= THRESHOLD }
    }

    companion object {
        /** Below this the review screen asks the owner to pick a category. */
        const val THRESHOLD = 0.6f

        /** Keyword for a learned rule: lowercase letters only, first three words. */
        fun normalize(s: String): String =
            s.lowercase().replace(Regex("[^a-z ]"), " ").split(' ').filter { it.length > 1 }.take(3).joinToString(" ")
    }
}

object Duplicates {
    /**
     * Id of an existing transaction that is probably the same as [d]: same reference number,
     * or same account, type and amount on the same day (Jakarta). Null if none.
     */
    fun find(d: Draft, accountId: Long, existing: List<Tx>, refs: Map<String, Long>): Long? {
        d.reference?.let { r -> refs[r]?.let { return it } }
        val day = d.date.atZone(JAKARTA).toLocalDate()
        return existing.firstOrNull {
            it.sourceAccountId == accountId && it.type == d.type && it.amount == d.amount &&
                it.date.atZone(JAKARTA).toLocalDate() == day
        }?.id
    }
}
