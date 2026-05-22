package com.zhuxiangcun.budgetapp.service;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class WeChatCategoryMapper {

    private static final String NEEDS_REFINE = "NEEDS_REFINE";

    private static final List<String> BEVERAGE_KEYWORDS =
            List.of("咖啡", "奶茶", "星巴克", "瑞幸", "茶颜悦色", "喜茶", "coco", "一点点", "luckin");

    private static final List<String> DINING_KEYWORDS = List.of(
            "外卖", "美团", "饿了么", "麦当劳", "肯德基", "餐", "饭", "火锅",
            "hungrypanda", "fantuan", "deliveroo", "ubereats", "doordash", "grubhub",
            "美团外卖");

    private static final List<String> TRANSPORT_KEYWORDS = List.of(
            "地铁", "公交", "滴滴", "打车", "出租车", "高铁", "火车", "机票", "加油",
            "uber", "lyft", "grab", "曹操出行", "高德打车");

    private static final List<String> MEDICAL_KEYWORDS =
            List.of("医院", "药店", "药房", "诊所");

    private static final List<String> UTILITY_KEYWORDS =
            List.of("电费", "水费", "燃气", "物业");

    public String classify(String transactionType, String merchant, String productDesc) {
        String coarseCategory = classifyTransactionType(clean(transactionType));
        if (!NEEDS_REFINE.equals(coarseCategory)) {
            return coarseCategory;
        }

        String text = (clean(merchant) + " " + clean(productDesc)).toLowerCase(Locale.ROOT);
        if (containsAny(text, BEVERAGE_KEYWORDS)) {
            return "饮品";
        }
        if (containsAny(text, DINING_KEYWORDS)) {
            return "餐饮";
        }
        if (containsAny(text, TRANSPORT_KEYWORDS)) {
            return "交通";
        }
        if (containsAny(text, MEDICAL_KEYWORDS)) {
            return "医疗";
        }
        if (containsAny(text, UTILITY_KEYWORDS)) {
            return "其他";
        }
        return "购物";
    }

    public boolean shouldSkipTransactionType(String transactionType) {
        String type = clean(transactionType);
        return "退款".equals(type)
                || "零钱提现".equals(type)
                || "信用卡还款".equals(type)
                || "理财通购买".equals(type)
                || "零钱通转出".equals(type);
    }

    private String classifyTransactionType(String transactionType) {
        return switch (transactionType) {
            case "商户消费" -> NEEDS_REFINE;
            case "转账", "群收款", "微信红包", "二维码收款" -> "其他";
            default -> "其他";
        };
    }

    private boolean containsAny(String value, List<String> keywords) {
        return keywords.stream().anyMatch(value::contains);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
