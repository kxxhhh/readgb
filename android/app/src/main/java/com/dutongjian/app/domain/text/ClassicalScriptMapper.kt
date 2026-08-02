package com.dutongjian.app.domain.text

import com.dutongjian.app.domain.model.TextScript

/** Presentation-only mapping. Stored source text is never rewritten. */
object ClassicalScriptMapper {
    private val traditionalToSimplified = mapOf(
        '學' to '学', '國' to '国', '體' to '体', '書' to '书', '經' to '经',
        '觀' to '观', '變' to '变', '時' to '时', '歲' to '岁', '東' to '东',
        '邊' to '边', '軍' to '军', '將' to '将', '從' to '从', '後' to '后',
        '與' to '与', '為' to '为', '於' to '于', '發' to '发', '見' to '见',
        '開' to '开', '關' to '关', '門' to '门', '問' to '问', '聞' to '闻',
        '無' to '无', '數' to '数', '處' to '处', '實' to '实', '義' to '义',
        '賢' to '贤', '國' to '国', '漢' to '汉', '廣' to '广', '長' to '长',
        '東' to '东', '華' to '华', '從' to '从', '專' to '专', '業' to '业',
        '錢' to '钱', '銀' to '银', '貿' to '贸', '稅' to '税', '糧' to '粮',
        '農' to '农', '貴' to '贵', '寶' to '宝', '鑄' to '铸', '鐵' to '铁',
        '鹽' to '盐', '馬' to '马', '魚' to '鱼', '鳥' to '鸟', '龍' to '龙',
        '鳳' to '凤', '戰' to '战', '勝' to '胜', '敗' to '败', '敵' to '敌',
        '殺' to '杀', '爲' to '为', '晉' to '晋', '趙' to '赵', '韓' to '韩',
        '魏' to '魏', '諸' to '诸', '侯' to '侯', '禮' to '礼', '書' to '书',
        '則' to '则', '號' to '号', '稱' to '称', '當' to '当', '應' to '应',
        '爾' to '尔', '並' to '并', '獨' to '独', '復' to '复', '幾' to '几',
        '給' to '给', '質' to '质', '處' to '处', '憂' to '忧', '樂' to '乐',
        '從' to '从', '義' to '义', '說' to '说', '讀' to '读', '讓' to '让',
        '請' to '请', '誰' to '谁', '詔' to '诏', '議' to '议', '論' to '论',
        '記' to '记', '識' to '识', '護' to '护', '勢' to '势', '權' to '权',
        '處' to '处', '擇' to '择', '據' to '据', '職' to '职', '級' to '级',
        '歷' to '历', '縣' to '县', '鄉' to '乡', '號' to '号', '歲' to '岁',
    )
    private val simplifiedToTraditional = traditionalToSimplified.entries
        .associate { (traditional, simplified) -> simplified to traditional }
    private val variant = mapOf(
        '体' to '體', '国' to '國', '学' to '學', '书' to '書', '经' to '經',
        '礼' to '禮', '义' to '義', '见' to '見', '听' to '聽', '说' to '說',
        '读' to '讀', '为' to '爲', '后' to '後', '发' to '發', '马' to '馬',
        '龙' to '龍', '凤' to '鳳', '门' to '門', '东' to '東', '军' to '軍',
        '将' to '將', '战' to '戰', '胜' to '勝', '败' to '敗', '钱' to '錢',
        '铁' to '鐵', '盐' to '鹽', '粮' to '糧', '县' to '縣', '乡' to '鄉',
    )

    fun transform(text: String, script: TextScript): String = when (script) {
        TextScript.SIMPLIFIED -> text.map { traditionalToSimplified[it] ?: it }.joinToString("")
        TextScript.TRADITIONAL -> text.map { simplifiedToTraditional[it] ?: it }.joinToString("")
        TextScript.VARIANT -> text.map { variant[it] ?: it }.joinToString("")
    }
}
