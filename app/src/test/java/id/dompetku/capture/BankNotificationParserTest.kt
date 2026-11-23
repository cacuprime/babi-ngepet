package id.dompetku.capture

import id.dompetku.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BankNotificationParserTest {
    private val at = Instant.parse("2026-11-18T05:00:00Z")
    private fun tx(title: String?, text: String) =
        (BankNotificationParser.parse(title, text, at) as BankNotificationParser.Parsed.Tx).draft

    @Test fun qrisPaymentIgnoresSaldo() {
        val d = tx("Pembayaran QRIS Berhasil", "Pembayaran QRIS sebesar Rp25.000 ke WARUNG BU SRI berhasil. Saldo Anda Rp1.234.567")
        assertEquals(TxType.EXPENSE, d.type)
        assertEquals(25_000L, d.amount)
        assertEquals("WARUNG BU SRI", d.merchant)
        assertEquals(1_234_567L, d.balanceAfter)
        assertEquals(at, d.date)
    }

    @Test fun saldoFirstThenAmount() {
        val d = tx(null, "Sisa saldo Rp900.000. Kamu baru saja bayar QRIS Rp15.000 di PARKIR MALL")
        assertEquals(15_000L, d.amount)
        assertEquals("PARKIR MALL", d.merchant)
    }

    @Test fun merchantStopsAtAmount() {
        val d = tx("Livin'", "Kamu berhasil bayar QRIS ke INDOMARET PUSAT Rp87.500")
        assertEquals(87_500L, d.amount)
        assertEquals("INDOMARET PUSAT", d.merchant)
    }

    @Test fun incomingTransfer() {
        val d = tx("Dana Masuk", "Anda menerima transfer Rp1.250.000,00 dari BUDI SANTOSO")
        assertEquals(TxType.INCOME, d.type)
        assertEquals(1_250_000L, d.amount)
        assertEquals("BUDI SANTOSO", d.merchant)
    }

    @Test fun englishStyleAmount() {
        val d = tx("Transaksi", "Transaksi debit IDR 50,000 di TOKO ABC")
        assertEquals(TxType.EXPENSE, d.type)
        assertEquals(50_000L, d.amount)
        assertEquals("TOKO ABC", d.merchant)
    }

    @Test fun otpIsIgnored() {
        assertEquals(BankNotificationParser.Parsed.Ignore,
            BankNotificationParser.parse("Kode OTP", "Kode OTP Anda 123456 untuk transaksi Rp100.000. Jangan berikan ke siapapun.", at))
    }

    @Test fun promoIsIgnored() {
        assertEquals(BankNotificationParser.Parsed.Ignore,
            BankNotificationParser.parse("Promo!", "Dapatkan cashback hingga Rp50.000 pakai QRIS", at))
    }

    @Test fun failedIsIgnored() {
        assertEquals(BankNotificationParser.Parsed.Ignore,
            BankNotificationParser.parse(null, "Transaksi QRIS Rp30.000 di KOPI KENANGAN gagal", at))
    }

    @Test fun noAmountIsIgnored() {
        assertEquals(BankNotificationParser.Parsed.Ignore,
            BankNotificationParser.parse("wondr", "Ada pembaruan aplikasi untukmu", at))
    }

    @Test fun onlySaldoIsUnknown() {
        assertEquals(BankNotificationParser.Parsed.Unknown,
            BankNotificationParser.parse(null, "Saldo Anda saat ini Rp500.000", at))
    }

    @Test fun amountWithoutDirectionIsUnknown() {
        assertTrue(BankNotificationParser.parse(null, "Rp20.000 TOKO XYZ 18/11", at) is BankNotificationParser.Parsed.Unknown)
    }

    @Test fun bankAppByPackageOrLabel() {
        assertEquals(BankApp.WONDR, BankApp.of("id.bni.wondr", null))
        assertEquals(BankApp.LIVIN, BankApp.of("id.bmri.livin", null))
        assertEquals(BankApp.SEABANK, BankApp.of("com.other", "SeaBank"))
        assertEquals(null, BankApp.of("com.whatsapp", "WhatsApp"))
    }
}
