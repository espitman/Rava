package ir.rava.runtimeprobe;

import java.net.URI;

final class GeminiAuthProtocol {
    private GeminiAuthProtocol() {}

    static boolean isAllowedAuthorizationUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equals(uri.getScheme())
                    && "accounts.google.com".equals(uri.getHost())
                    && uri.getRawUserInfo() == null;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }
}
