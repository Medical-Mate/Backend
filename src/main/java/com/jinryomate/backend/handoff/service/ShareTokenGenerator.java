package com.jinryomate.backend.handoff.service;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * 공유 토큰 생성기.
 *
 * <p>128비트 난수를 Base64URL 로 적어 22자가 된다. 인증 없이 열리는 주소라
 * <b>추측 불가능성이 전부</b>다. {@link SecureRandom} 이 아닌 난수를 쓰면
 * 시드를 알아내 다음 토큰을 맞힐 수 있다.
 *
 * <p>URL 과 QR 에 그대로 들어가므로 {@code +} {@code /} 가 없는 URL-safe 알파벳을 쓰고,
 * 패딩({@code =})은 뺀다.
 */
@Component
public class ShareTokenGenerator {

    /** 128비트. UUID v4(122비트)보다 충분하고 22자로 더 짧다. */
    private static final int TOKEN_BYTES = 16;

    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return encoder.encodeToString(bytes);
    }
}
