package com.bocsoft.sqleditor.optimization;

/** Safe classification only: never propagate upstream messages or secret material. */
public class OptimizationFailure extends RuntimeException {
    private final String code;
    public OptimizationFailure(String code) { super("扫描未完成，请重新登录后再次扫描"); this.code = code; }
    public String getCode() { return code; }
}
