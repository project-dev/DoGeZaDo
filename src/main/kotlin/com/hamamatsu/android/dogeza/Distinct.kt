package com.hamamatsu.android.dogeza

/**
 * 移動方向列挙型
 * @author 日本Androidの会 浜松支部
 */
enum class Distinct(val dist: Int) {
    Unknow(0xFFFF),
    ToDown(0x0001),
    ToUp(0x0002),
    ToLeft(0x0101),
    ToRight(0x0102)
}

