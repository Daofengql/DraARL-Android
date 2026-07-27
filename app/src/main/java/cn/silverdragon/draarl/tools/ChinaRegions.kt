package cn.silverdragon.draarl.tools

import android.content.Context
import cn.silverdragon.draarl.R
import org.json.JSONArray

data class RegionOption(val code: String, val name: String)

object ChinaRegions {
    val provinces = listOf(
        RegionOption("11", "北京市"), RegionOption("12", "天津市"), RegionOption("13", "河北省"),
        RegionOption("14", "山西省"), RegionOption("15", "内蒙古自治区"), RegionOption("21", "辽宁省"),
        RegionOption("22", "吉林省"), RegionOption("23", "黑龙江省"), RegionOption("31", "上海市"),
        RegionOption("32", "江苏省"), RegionOption("33", "浙江省"), RegionOption("34", "安徽省"),
        RegionOption("35", "福建省"), RegionOption("36", "江西省"), RegionOption("37", "山东省"),
        RegionOption("41", "河南省"), RegionOption("42", "湖北省"), RegionOption("43", "湖南省"),
        RegionOption("44", "广东省"), RegionOption("45", "广西壮族自治区"), RegionOption("46", "海南省"),
        RegionOption("50", "重庆市"), RegionOption("51", "四川省"), RegionOption("52", "贵州省"),
        RegionOption("53", "云南省"), RegionOption("54", "西藏自治区"), RegionOption("61", "陕西省"),
        RegionOption("62", "甘肃省"), RegionOption("63", "青海省"), RegionOption("64", "宁夏回族自治区"),
        RegionOption("65", "新疆维吾尔自治区"), RegionOption("71", "台湾省"),
        RegionOption("81", "香港特别行政区"), RegionOption("82", "澳门特别行政区"),
    )

    fun cities(context: Context, provinceCode: String): List<RegionOption> = runCatching {
        val text = context.resources.openRawResource(R.raw.china_cities).bufferedReader().use { it.readText() }
        val array = JSONArray(text)
        buildList {
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                if (item.optString("provinceCode") == provinceCode) {
                    add(RegionOption(item.optString("code"), item.optString("name")))
                }
            }
        }
    }.getOrDefault(emptyList())
}
