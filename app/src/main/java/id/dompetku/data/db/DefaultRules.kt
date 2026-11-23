package id.dompetku.data.db

import androidx.sqlite.db.SupportSQLiteDatabase

/** Built-in categorisation keywords (PRD §5.2E). Each points at a default category by name and type. */
object DefaultRules {
    private val rules: List<Triple<String, String, String>> = buildList {
        fun add(cat: String, type: String, vararg words: String) = words.forEach { add(Triple(it, cat, type)) }
        add("Transportasi", "EXPENSE", "gojek", "goride", "gocar", "grab", "maxim", "bluebird", "krl", "mrt", "transjakarta", "tol", "pertamina", "shell", "parkir")
        add("Belanja", "EXPENSE", "indomaret", "alfamart", "alfamidi", "superindo", "hypermart", "shopee", "tokopedia", "lazada", "blibli")
        add("Tagihan & Utilitas", "EXPENSE", "pln", "token listrik", "pdam", "indihome", "telkomsel", "xl axiata", "pulsa", "paket data", "bpjs", "biznet", "first media")
        add("Hiburan", "EXPENSE", "spotify", "netflix", "youtube", "disney", "google play", "steam", "vidio", "cinema", "xxi", "cgv")
        add("Makanan & Minuman", "EXPENSE", "kfc", "mcd", "mcdonald", "starbucks", "kopi", "warung", "nasi", "resto", "padang", "gofood", "grabfood", "shopeefood", "bakso", "sate", "cafe")
        add("Kesehatan", "EXPENSE", "apotek", "kimia farma", "klinik", "rumah sakit", "halodoc", "guardian")
        add("Pendidikan", "EXPENSE", "gramedia", "udemy", "coursera", "sekolah", "kursus")
        add("Donasi", "EXPENSE", "kitabisa", "infaq", "sedekah", "zakat", "donasi")
        add("Lain-lain", "EXPENSE", "pajak bunga", "pajak", "biaya admin", "admin bank")
        add("Hasil Investasi", "INCOME", "bunga tabungan", "bunga deposito", "bunga", "dividen", "imbal hasil")
        add("Gaji", "INCOME", "gaji", "payroll", "salary")
        add("Pengembalian Dana / Refund", "INCOME", "refund", "pengembalian dana", "cashback")
    }

    fun seed(db: SupportSQLiteDatabase) {
        rules.forEach { (keyword, cat, type) ->
            db.execSQL(
                "INSERT OR IGNORE INTO categorization_rules (keyword_or_merchant, suggested_category_id, account_id_optional, " +
                    "confidence, source, usage_count, created_at, updated_at) " +
                    "SELECT ?, id, NULL, 0.7, 'BUILTIN', 0, 0, 0 FROM categories WHERE category_name = ? AND category_type = ? LIMIT 1",
                arrayOf<Any>(keyword, cat, type),
            )
        }
    }
}
