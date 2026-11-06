package com.fongmi.android.tv.utils;

import java.net.URI;
import java.util.List;

/** Only open a QR challenge on the selected provider's own HTTPS domains. */
public final class MobileLoginLink {
    private MobileLoginLink() {}

    /** 能直接打开登录链接、并带着已登录账号确认的网盘 App（它们本身就是浏览器）；其他网盘返回 null。 */
    public static String appPackage(String disk) {
        if (disk == null) return null;
        return switch (disk) {
            case "夸克" -> "com.quark.browser";
            case "UC" -> "com.UCMobile";
            default -> null;
        };
    }

    public static boolean isAllowed(String disk, String value) {
        if (disk == null || value == null) return false;
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                    || uri.getHost() == null || (uri.getPort() != -1 && uri.getPort() != 443)) return false;
            List<String> domains = switch (disk) {
                case "夸克" -> List.of("quark.cn");
                case "UC" -> List.of("uc.cn");
                case "百度" -> List.of("baidu.com");
                case "阿里" -> List.of("alipan.com", "aliyundrive.com");
                default -> List.of();
            };
            String host = uri.getHost().toLowerCase(java.util.Locale.ROOT);
            return domains.stream().anyMatch(domain -> host.equals(domain) || host.endsWith("." + domain));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
