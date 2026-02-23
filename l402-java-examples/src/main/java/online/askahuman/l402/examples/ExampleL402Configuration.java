package online.askahuman.l402.examples;

import online.askahuman.l402.L402Service;
import online.askahuman.l402.MacaroonService;
import online.askahuman.l402.spring.L402AuthFilter;
import online.askahuman.l402.spring.PaymentContextLoader;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Example Spring configuration wiring the L402 library beans.
 */
@Configuration
public class ExampleL402Configuration {

    @Bean
    public PaymentContextLoader paymentContextLoader(InMemoryPaymentStore store) {
        return store::load;
    }

    @Bean
    public L402Service l402Service(MacaroonService macaroonService, MockLightningClient lightningClient) {
        return new L402Service(macaroonService, lightningClient);
    }

    @Bean
    public L402AuthFilter l402AuthFilter(L402Service l402Service, MacaroonService macaroonService,
                                          PaymentContextLoader contextLoader) {
        return new L402AuthFilter(l402Service, macaroonService, contextLoader);
    }

    @Bean
    public FilterRegistrationBean<L402AuthFilter> l402FilterRegistration(L402AuthFilter filter) {
        FilterRegistrationBean<L402AuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false); // Managed by Spring Security, not the servlet container
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, L402AuthFilter l402AuthFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .addFilterBefore(l402AuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
