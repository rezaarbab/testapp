package com.stegatext.app

object StoryGenerator {

    private val faSentences = listOf(
        "شب ستاره‌ها آمدند و بچه‌ها پشت پنجره نشستند تا آرزوی بزرگ‌ترین سفر را بنویسند.",
        "عمو رضا نمی‌دانست که خانه‌های کوچک ما آرزوی مهربان‌ترین‌هاست.",
        "کتاب‌های کوهستان را با یار کوچک‌تر خواندیم و داستان‌های عالی‌تر گفتیم.",
        "کیکی شیرین را با بینی کوچک برای همه‌ی همسایه‌ها آورد.",
        "پسرک که سریع‌تر از همه می‌دوید گفت این بازی‌ها برای ما بهترین‌هاست.",
        "بچه‌های حیاط آرزوی بزرگ‌ترین جاها را در دل داشتند و نمی‌خواستند تنها بمانند.",
        "عمو رضا گفت تازه‌ترین کلمه‌های خوب هنوز نوشته نشده‌اند.",
        "ما در کوهستان‌های بی‌آروی ستاره‌های تازه‌ای پیدا کردیم.",
        "خاطره‌های کوچک همیشه در دفتر کسانی می‌مانند که سفر را برای همه‌ی ما ساده‌تر می‌کردند.",
        "بچه‌های کوچک‌تر از همه درباره‌ی آسمان و ستاره‌های تازه پرسیدند."
    )

    private val enSentences = listOf(
        "My young man makes a perfect cake for everyone in the family.",
        "The books we keep are the most amazing memory of our team.",
        "Everyone says my jokes make the city a happy place.",
        "Each young man keeps a map and a pen in his perfect bag.",
        "The dark window and a happy cake made our day so easy.",
        "Everyone keeps a paper book and a memory of the map game."
    )

    private val ruSentences = listOf(
        "Это просто история про дом и моих друзей.",
        "Мы говорим спасибо большое за всё."
    )

    private class Rnd(seed: Long) {
        private var state: Long = (seed xor (-0x61C8864680B583EBL)).let { if (it == 0L) 0xB504F32DL else it }

        private fun next(): Long {
            var x = state
            x = x xor (x shl 13)
            x = x xor (x ushr 7)
            x = x xor (x shl 17)
            state = x
            return x * 0x2545F4914F6CDD1DL
        }

        fun below(bound: Int): Int {
            if (bound <= 1) return 0
            val lim = (0x80000000L / bound) * bound
            var v: Long
            do { v = next() and 0x7FFFFFFFFFFFFFFFL } while (v >= lim)
            return (v % bound).toInt()
        }
    }

    private fun hunk(robustOnly: Boolean, seed: Long): List<String> {
        val pool = ArrayList<String>()
        if (robustOnly) {
            pool.addAll(enSentences)
            pool.addAll(enSentences)
            pool.addAll(enSentences)
            pool.addAll(faSentences.subList(0, 4))
        } else {
            pool.addAll(faSentences)
            pool.addAll(enSentences)
            pool.addAll(ruSentences)
        }
        val r = Rnd(seed)
        for (i in pool.size - 1 downTo 1) {
            val j = r.below(i + 1)
            val t = pool[i]
            pool[i] = pool[j]
            pool[j] = t
        }
        return pool
    }

    fun generate(requiredBits: Int, robustOnly: Boolean): String {
        val need = if (requiredBits < 640) 640 else requiredBits
        val sb = StringBuilder()
        val seed = System.nanoTime()
        val first = hunk(robustOnly, seed)
        for (s in first) { sb.append(s); sb.append(' ') }
        val cap0 = StegoEngine.capacityBits(sb.toString(), robustOnly)
        if (cap0 >= need) return sb.toString().trim()
        val rate = if (sb.length > 0) cap0.toDouble() / sb.length else 0.05
        val target = (need / rate * 1.25 + 400).toInt()
        var pass = 1
        while (sb.length < target && pass < 400) {
            val h = hunk(robustOnly, seed + pass * 977)
            for (s in h) { sb.append(s); sb.append(' ') }
            pass++
        }
        var extra = 0
        while (StegoEngine.capacityBits(sb.toString(), robustOnly) < need && extra < 10) {
            val h = hunk(robustOnly, seed + pass * 991)
            for (s in h) { sb.append(s); sb.append(' ') }
            pass++
            extra++
        }
        return sb.toString().trim()
    }
}
