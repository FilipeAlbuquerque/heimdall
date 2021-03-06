package br.com.conductor.heimdall.gateway.configuration;

/*-
 * =========================LICENSE_START==================================
 * heimdall-gateway
 * ========================================================================
 * Copyright (C) 2018 Conductor Tecnologia SA
 * ========================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ==========================LICENSE_END===================================
 */

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import br.com.conductor.heimdall.core.environment.Property;
import br.com.conductor.heimdall.gateway.trace.Trace;
import br.com.conductor.heimdall.gateway.trace.TraceContextHolder;
import lombok.extern.slf4j.Slf4j;

/**
 * Class responsible for configuring the Trace.
 *
 * @author Filipe Germano
 * @author Thiago Sampaio
 *
 */
@Configuration
@Slf4j
public class TraceConfiguration {

	@Value("${info.app.profile}")
	private String profile;

	@Value("${management.context-path}")
	private String managerPath;

	@Autowired
	private Property prop;

	/**
	 * {@inheritDoc}
	 */
	public class TraceFilter implements Filter {

		@Override
		public void destroy() {
		}

		@Override
		public void doFilter(ServletRequest request, ServletResponse res, FilterChain chain) throws IOException, ServletException {

			Trace trace = null;

			HttpServletResponse response = (HttpServletResponse) res;
			try {

				trace = TraceContextHolder.getInstance().init(prop.getTrace().isPrintAllTrace(), profile, request, prop.getMongo().getEnabled());
				if (shouldDisableTrace(request)) {
					trace.setShouldPrint(false);
				}

				if ("OPTIONS".equalsIgnoreCase(((HttpServletRequest) request).getMethod())) {
					response.setHeader("Access-Control-Allow-Origin", "*");
					response.setHeader("Access-Control-Allow-Credentials", "true");
					response.setHeader("Access-Control-Allow-Methods", "POST, GET, PUT, PATCH, DELETE, OPTIONS");
					response.setHeader("Access-Control-Allow-Headers", "origin, content-type, accept, authorization, x-requested-with, X-AUTH-TOKEN, access_token, client_id, device_id, credential");
					response.setHeader("Access-Control-Max-Age", "3600");
					response.setStatus(200);
				} else {
					chain.doFilter(request, response);
				}
				

			} catch (Exception e) {

				log.error("Error {} during request {} exception {}", e.getMessage(), ((HttpServletRequest) request).getRequestURL(), e);
			} finally {

				if (trace != null) {
					if (trace.isShouldPrint()) {
						trace.write(response);
					} else {
						TraceContextHolder.getInstance().clearActual();
					}
				}

				TraceContextHolder.getInstance().unset();
			}
		}

		@Override
		public void init(FilterConfig arg0) throws ServletException {
			
		}

	}

	/**
	 * Checks if it should disable the Trace from a c.
	 * 
	 * @param request
	 *            {@link ServletRequest}
	 * @return True if trace should be disable, false otherwise
	 */
	public boolean shouldDisableTrace(ServletRequest request) {

		String uri = ((HttpServletRequest) request).getRequestURI();
		return (uri.equalsIgnoreCase("/") || uri.startsWith(managerPath));
	}

	/**
	 * Configures and returns the {@link FilterRegistrationBean}.
	 * 
	 * @return {@link FilterRegistrationBean}
	 */
	@Bean
	public FilterRegistrationBean filterRegistrationBean() {

		FilterRegistrationBean filterRegistrationBean = new FilterRegistrationBean();
		filterRegistrationBean.setFilter(new TraceFilter());
		filterRegistrationBean.addUrlPatterns("/*");
		filterRegistrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
		filterRegistrationBean.setName("traceFilter");

		return filterRegistrationBean;
	}

}
