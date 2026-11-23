# Cara memasang Dompetku di HP Android

File yang dipasang: **`Dompetku-v1.apk`** (ada di folder `finance-tracker`).

## 1. Pindahkan file ke HP
Pilih salah satu cara:
- **Kabel USB:** sambungkan HP ke laptop, pilih "Transfer file", lalu salin `Dompetku-v1.apk` ke folder *Download* di HP.
- **Google Drive:** unggah `Dompetku-v1.apk` ke Google Drive milikmu, lalu buka Drive di HP dan unduh file tersebut.

## 2. Pasang aplikasinya
1. Di HP, buka aplikasi **Files / File Manager**, lalu masuk ke folder *Download*.
2. Ketuk `Dompetku-v1.apk`.
3. Jika muncul pesan "Untuk keamanan, HP Anda tidak diizinkan memasang aplikasi dari sumber ini", ketuk **Setelan** dan aktifkan **Izinkan dari sumber ini**, lalu kembali.
4. Ketuk **Pasang**. Jika Google Play Protect bertanya, pilih **Tetap pasang**, karena aplikasi ini dibuat sendiri dan tidak ada di Play Store.
5. Buka **Dompetku**.

## 3. Pertama kali dibuka
- Pada langkah "Akun dan saldo awal", ketuk **Pakai data awal** untuk memasukkan saldo semua rekening, emas, dan Bibit sekaligus. Periksa angkanya. Jika ada yang salah, hapus lalu tambahkan secara manual.
- Aktifkan **Kunci aplikasi** jika ingin Dompetku meminta sidik jari atau PIN.

## 4. Memperbarui aplikasi nanti
Pasang file APK versi baru di atas versi lama. Data akan **tetap ada**.

Kunci tanda tangan aplikasi (`*.jks` dan `keystore.properties`) tidak ikut di repo ini; buat sendiri dengan `keytool` dan simpan salinannya di tempat aman.
**Salin folder itu ke Google Drive atau flashdisk.** Jika kunci ini hilang, versi baru tidak bisa dipasang di atas versi lama. Kamu harus menghapus aplikasi lalu memulihkan data dari cadangan.

## 5. Cadangan
Buka **Pengaturan → Buat cadangan** secara rutin dan simpan filenya (misalnya ke Google Drive). File cadangan dikunci dengan kata sandi yang kamu buat. **Jika kata sandi itu lupa, cadangan tidak bisa dibuka.**
