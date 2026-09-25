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
        "ما در کوهستان‌های بی‌آروی ستاره‌های تازه‌ای پیدا کردیم."
    )

    private val enSentences = listOf(
        "My young man makes a perfect cake for everyone in the family.",
        "The books we keep are the most amazing memory of our team.",
        "Everyone says my jokes make the city a happy place.",
        "Each young man keeps a map and a pen in his perfect bag."
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

    private fun shuffled(hunk: MutableList<String>, seed: Long): MutableList<String> {
        val r = Rnd(seed)
        for (i in hunk.size - 1 downTo 1) {
            val j = r.below(i + 1)
            val t = hunk[i]
            hunk[i] = hunk[j]
            hunk[j] = t
        }
        return hunk
    }

    fun generate(requiredBits: Int, robustOnly: Boolean): String {
        val need = if (requiredBits < 640) 640 else requiredBits
        val sb = StringBuilder()
        var guard = 0
        while (sb.length < 3_000_000) {
            val pool = ArrayList<String>()
            pool.addAll(faSentences)
            pool.addAll(enSentences)
            pool.addAll(ruSentences)
            shuffled(pool, System.nanoTime())
            val pool2 = pool + pool + pool
            for (s in pool2) {
                sb.append(s)
                sb.append(' ')
            }
            guard++
            if (guard >= 16) {
                guard = 0
                if (StegoEngine.capacityBits(sb.toString(), robustOnly) >= need) break
            }
        }
        return sb.toString().trim()
    }
}