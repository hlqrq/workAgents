package com.qiyi.util;

public enum HunyuanLanguage {
    CHINESE("zh", "中文"),
    ENGLISH("en", "英语"),
    FRENCH("fr", "法语"),
    PORTUGUESE("pt", "葡萄牙语"),
    SPANISH("es", "西班牙语"),
    JAPANESE("ja", "日语"),
    TURKISH("tr", "土耳其语"),
    RUSSIAN("ru", "俄语"),
    ARABIC("ar", "阿拉伯语"),
    KOREAN("ko", "韩语"),
    THAI("th", "泰语"),
    ITALIAN("it", "意大利语"),
    GERMAN("de", "德语"),
    VIETNAMESE("vi", "越南语"),
    MALAY("ms", "马来语"),
    INDONESIAN("id", "印尼语"),
    FILIPINO("tl", "菲律宾语"),
    HINDI("hi", "印地语"),
    TRADITIONAL_CHINESE("zh-Hant", "繁体中文"),
    POLISH("pl", "波兰语"),
    CZECH("cs", "捷克语"),
    DUTCH("nl", "荷兰语"),
    KHMER("km", "高棉语"),
    BURMESE("my", "缅甸语"),
    PERSIAN("fa", "波斯语"),
    GUJARATI("gu", "古吉拉特语"),
    URDU("ur", "乌尔都语"),
    TELUGU("te", "泰卢固语"),
    MARATHI("mr", "马拉地语"),
    HEBREW("he", "希伯来语"),
    BENGALI("bn", "孟加拉语"),
    TAMIL("ta", "泰米尔语"),
    UKRAINIAN("uk", "乌克兰语"),
    TIBETAN("bo", "藏语"),
    KAZAKH("kk", "哈萨克语"),
    MONGOLIAN("mn", "蒙古语"),
    UYGHUR("ug", "维吾尔语"),
    CANTONESE("yue", "粤语");

    private final String code;
    private final String chineseName;

    HunyuanLanguage(String code, String chineseName) {
        this.code = code;
        this.chineseName = chineseName;
    }

    public String getCode() {
        return code;
    }

    public String getChineseName() {
        return chineseName;
    }
}
