package org.example.consent;

public class PolicyModule {
    private final String codeSystem;
    private final String code;
    private final String display;

    public PolicyModule(String codeSystem, String code, String display) {
        this.codeSystem = codeSystem;
        this.code = code;
        this.display = display;
    }

    public String getCodeSystem() { return codeSystem; }
    public String getCode() { return code; }
    public String getDisplay() { return display; }
}
