package ru.cmpas.voice.ui.components

/**
 * Чек-ин пишется только при касании слайдера пользователем; без касания — null
 * (`docs/PRODUCT.md` §5/§7, `docs/QA-TESTPLAN.md` §5/§7, `docs/PRIVACY-DPO.md` — потенциально
 * данные о здоровье, дефолт без действия пользователя недопустим).
 */
fun checkInToRecord(value: Int, touched: Boolean): Int? = if (touched) value else null
