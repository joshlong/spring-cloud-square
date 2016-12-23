/*
 * Copyright 2013-2016 the original author or authors.
 *
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
 */

package org.springframework.cloud.retrofit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.web.HttpMessageConverters;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.netflix.ribbon.RibbonClient;
import org.springframework.cloud.netflix.ribbon.StaticServerList;
import org.springframework.cloud.square.okhttp.OkHttpRibbonInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.ConversionService;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;
import static org.springframework.web.bind.annotation.RequestMethod.GET;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.http.GET;

/**
 * @author Spencer Gibb
 */
@RunWith(SpringRunner.class)
@SpringBootTest(properties = { "spring.application.name=retrofitribbonnoannotationtest",
				"logging.level.org.springframework.cloud.retrofit=DEBUG",
				"retrofit.reactor.enabled=false",
				"okhttp.ribbon.enabled=false",
		 }, webEnvironment = DEFINED_PORT)
@DirtiesContext
public class RetrofitRibbonNoAnnotationTests {

	protected static final String HELLO_WORLD_1 = "hello world 1";

	@Autowired
	private TestClient testClient;

	protected interface TestClient {
		@GET("/hello")
		Call<Hello> getHello();
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@RestController
	@RibbonClient(name = "localapp", configuration = RetrofitClientRibbonTests.LocalRibbonClientConfiguration.class)
	@SuppressWarnings("unused")
	protected static class Application {

		@Bean
		public TestClient testClient(LoadBalancerClient loadBalancerClient,
									 ConversionService conversionService,
									 ObjectFactory<HttpMessageConverters> messageConverters) {
			HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
			loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);


			return new Retrofit.Builder().baseUrl("http://localapp")
					.client(new OkHttpClient.Builder()
							.addInterceptor(loggingInterceptor)
							.addInterceptor(new OkHttpRibbonInterceptor(loadBalancerClient))
							.build())
					.addConverterFactory(new SpringConverterFactory(messageConverters, conversionService))
					.build()
					.create(TestClient.class);
		}

		@RequestMapping(method = GET, path = "/hello")
		public Hello getHello() {
			return new Hello(HELLO_WORLD_1);
		}

		public static void main(String[] args) {
			new SpringApplicationBuilder(Application.class)
					.properties("spring.application.name=retrofitribbonnoannotationtest",
							"management.contextPath=/admin",
							"retrofit.client.url.tests.url=http://localhost:8080")
					.run(args);
		}
	}

	@Test
	public void testSimpleType() throws Exception {
		Response<Hello> response = this.testClient.getHello().execute();
		assertThat(response).isNotNull();
		assertThat(response.isSuccessful()).as("checks response successful, code %d", response.code()).isTrue();
		assertThat(response.body()).isEqualTo(new Hello(HELLO_WORLD_1));
	}

	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class Hello {
		private String message;
	}

	// Load balancer with fixed server list for "local" pointing to localhost
	public static class LocalRibbonClientConfiguration {

		@LocalServerPort
		private int port = 0;

		@Bean
		public ServerList<Server> ribbonServerList() {
			return new StaticServerList<>(new Server("localhost", this.port));
		}

	}
}