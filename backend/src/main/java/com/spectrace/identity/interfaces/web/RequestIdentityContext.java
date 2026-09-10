package com.spectrace.identity.interfaces.web;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class RequestIdentityContext {

    private String authProvider;
    private String externalSubject;

    public void setIdentity(String authProvider, String externalSubject) {
        this.authProvider = authProvider;
        this.externalSubject = externalSubject;
    }

    public String authProvider() {
        return authProvider;
    }

    public String externalSubject() {
        return externalSubject;
    }

    public boolean isPresent() {
        return authProvider != null
                && !authProvider.isBlank()
                && externalSubject != null
                && !externalSubject.isBlank();
    }
}