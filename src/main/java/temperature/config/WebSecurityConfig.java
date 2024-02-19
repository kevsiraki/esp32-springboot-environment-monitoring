package temperature.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpMethod;

import temperature.repository.UserRepository;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

	@Autowired
	private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

	@Autowired
	private UserDetailsService jwtUserDetailsService;

	@Autowired
	private JwtRequestFilter jwtRequestFilter;

	@Autowired
	private ObjectMapper objectMapper; // Inject ObjectMapper

	@Autowired
	private UserRepository userRepository; // Inject UserRepository

	@Override
	public void configure(AuthenticationManagerBuilder auth) throws Exception {
		// configure AuthenticationManager so that it knows from where to load
		// user for matching credentials
		// Use BCryptPasswordEncoder
		auth.userDetailsService(jwtUserDetailsService).passwordEncoder(passwordEncoder());
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	@Override
	public AuthenticationManager authenticationManagerBean() throws Exception {
		return super.authenticationManagerBean();
	}

	@Bean
	@Autowired // Inject ObjectMapper bean
	public ApiKeyValidationFilter apiKeyValidationFilter(ObjectMapper objectMapper, UserRepository userRepository) {
		return new ApiKeyValidationFilter(objectMapper, userRepository);
	}

	@Override
	protected void configure(HttpSecurity httpSecurity) throws Exception {
		// We don't need CSRF for this example
		httpSecurity.csrf().disable()
				// Permit access to /authenticate, /api/login, and /api/register without
				// authentication
				.authorizeRequests()
				.antMatchers(HttpMethod.POST, "/authenticate", "/api/login", "/api/register").permitAll()
				.antMatchers(HttpMethod.GET, "/swagger-ui.html/**", "/swagger-ui/**", "/webjars/**", "/swagger-resources/**", "/v2/api-docs/**").permitAll()
				// Require authentication for PUT and DELETE requests
				.antMatchers(HttpMethod.PUT, "/temperature/**").authenticated()
				.antMatchers(HttpMethod.DELETE, "/temperatures/**").authenticated()
				// Require valid API key for accessing /devices/** and /temperatures/**
				.antMatchers(HttpMethod.GET, "/devices/**", "/temperatures/**").authenticated()
				// all other requests need to be authenticated
				.anyRequest().authenticated()
				.and()
				// make sure we use stateless session; session won't be used to
				// store user's state.
				.exceptionHandling().authenticationEntryPoint(jwtAuthenticationEntryPoint)
				.and().sessionManagement()
				.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
				.and()
				// Add custom filter to validate API key in headers for GET and POST requests to
				// /temperatures/** and /devices/**
				.addFilterBefore(apiKeyValidationFilter(objectMapper, userRepository),
						UsernamePasswordAuthenticationFilter.class)
				// Disable the default security filter for login endpoint
				.formLogin().disable();
	}

}
