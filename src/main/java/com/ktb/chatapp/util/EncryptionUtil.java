package com.ktb.chatapp.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * AES-256-CBC 암호화 유틸리티
 */
@Slf4j
@Component
public class EncryptionUtil {

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String KEY_ALGORITHM = "AES";
    
    @Value("${app.encryption.key}")
    private String encryptionKey;
    
    @Value("${app.password.salt}")
    private String passwordSalt;

    /**
     * 이메일 암호화 (AES-256-CBC)
     */
    public String encrypt(String plainText) {
        try {
            // 키 생성 (32바이트 = 256비트)
            byte[] key = getKey();
            
            // 랜덤 IV 생성 (16바이트)
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            
            SecretKeySpec secretKey = new SecretKeySpec(key, KEY_ALGORITHM);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
            
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            
            String ivHex = bytesToHex(iv);
            String encryptedHex = bytesToHex(encrypted);
            return ivHex + ":" + encryptedHex;
            
        } catch (Exception e) {
            log.error("Encryption error", e);
            return null;
        }
    }

    /**
     * 이메일 복호화 (AES-256-CBC)
     */
    public String decrypt(String encryptedText) {
        try {
            // iv:encrypted 형식 파싱
            String[] parts = encryptedText.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid encrypted text format");
            }
            
            byte[] iv = hexToBytes(parts[0]);
            byte[] encrypted = hexToBytes(parts[1]);
            byte[] key = getKey();
            
            SecretKeySpec secretKey = new SecretKeySpec(key, KEY_ALGORITHM);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
            
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            log.error("Decryption error", e);
            return null;
        }
    }

    /**
     * 32바이트 키 생성 (SHA-256 해시 사용)
     */
    private byte[] getKey() throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] key = sha.digest(encryptionKey.getBytes(StandardCharsets.UTF_8));
        return Arrays.copyOf(key, 32); // 256비트 = 32바이트
    }

    /**
     * byte 배열을 hex 문자열로 변환
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * hex 문자열을 byte 배열로 변환
     */
    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
