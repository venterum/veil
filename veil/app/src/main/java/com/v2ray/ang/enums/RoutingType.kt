package com.v2ray.ang.enums

enum class RoutingType(val fileName: String) {
    GLOBAL("routing_preset_global"),
    RU_DIRECT("routing_preset_ru_direct"),
    CN_DIRECT("routing_preset_cn_direct"),
    IR_DIRECT("routing_preset_ir_direct");

    companion object {
        const val BLOCK_ADS_MODULE_FILE = "routing_preset_block_ads"

        fun fromIndex(index: Int): RoutingType {
            return entries.getOrElse(index) { GLOBAL }
        }
    }
}
