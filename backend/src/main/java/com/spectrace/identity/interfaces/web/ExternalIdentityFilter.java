package com.spectrace.identity.interfaces.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@ConditionalOnProperty(
        name = "spectrace.dev-external-auth.enabled",
        havingValue = "true"
)
public class ExternalIdentityFilter extends OncePerRequestFilter {

    private static final String AUTH_PROVIDER_HEADER = "X-Auth-Provider";
    private static final String EXTERNAL_SUBJECT_HEADER = "X-External-Subject";

    private final RequestIdentityContext identityContext;

    public ExternalIdentityFilter(RequestIdentityContext identityContext) {
        this.identityContext = identityContext;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authProvider = request.getHeader(AUTH_PROVIDER_HEADER);
        String externalSubject = request.getHeader(EXTERNAL_SUBJECT_HEADER);

        if (authProvider != null || externalSubject != null) {
            identityContext.setIdentity(
                    authProvider,
                    externalSubject
            );
        }

        filterChain.doFilter(request, response);
    }
}