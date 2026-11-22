package com.perm.admin.common;

/**
 * 业务异常：携带 HTTP 状态码与中文错误消息，由全局异常处理器转成 {error: msg}。
 */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(400, message);
    }

    public int getStatus() {
        return status;
    }
}
