package id.dompetku.data.db

import androidx.sqlite.db.SupportSQLiteDatabase

/** PRD §5.1 default categories, exactly as written there. */
object DefaultCategories {
    val expense = listOf(
        "Makanan & Minuman" to "restaurant",
        "Transportasi" to "directions_car",
        "Belanja" to "shopping_bag",
        "Kebutuhan Rumah" to "home",
        "Tagihan & Utilitas" to "receipt",
        "Kesehatan" to "favorite",
        "Hiburan" to "movie",
        "Pendidikan" to "school",
        "Keluarga / Orang Tua" to "family",
        "Donasi" to "volunteer",
        "Cicilan & Utang" to "credit",
        "Investasi" to "trending_up",
        "Lain-lain" to "more",
    )
    val income = listOf(
        "Gaji" to "work",
        "Bonus" to "star",
        "Freelance" to "laptop",
        "Hasil Investasi" to "trending_up",
        "Penghasilan Tambahan" to "add",
        "Pengembalian Dana / Refund" to "undo",
        "Lain-lain" to "more",
    )

    private val palette = listOf(
        0xFFC6F062, 0xFF7FB3FF, 0xFFFFB86B, 0xFFFF8FA3, 0xFFB79CFF, 0xFF6EDCC4, 0xFFFFD66B,
    )

    fun seed(db: SupportSQLiteDatabase) {
        fun insert(list: List<Pair<String, String>>, type: String) = list.forEachIndexed { i, (name, icon) ->
            db.execSQL(
                "INSERT INTO categories (category_name, category_type, icon, color, is_default) VALUES (?, ?, ?, ?, 1)",
                arrayOf<Any>(name, type, icon, palette[i % palette.size]),
            )
        }
        insert(expense, "EXPENSE")
        insert(income, "INCOME")
        setBuckets(db)
    }

    /** Default budget buckets by name; everything not listed stays null (= WANTS). Never overrides a chosen bucket. */
    val needs = listOf(
        "Makanan & Minuman", "Transportasi", "Kebutuhan Rumah", "Tagihan & Utilitas", "Kesehatan",
        "Pendidikan", "Keluarga / Orang Tua", "Cicilan & Utang",
    )
    val savings = listOf("Investasi")

    fun setBuckets(db: SupportSQLiteDatabase) {
        fun set(names: List<String>, bucket: String) = names.forEach {
            db.execSQL(
                "UPDATE categories SET bucket = ? WHERE category_name = ? AND category_type = 'EXPENSE' AND bucket IS NULL",
                arrayOf<Any>(bucket, it),
            )
        }
        set(needs, "NEEDS")
        set(savings, "SAVINGS")
    }
}
