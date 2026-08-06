package ambient_invisible_intelligence.rest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.ApiVersionInserter;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.HttpServiceGroup.ClientType;
import org.springframework.web.service.registry.ImportHttpServices;

@Configuration
@ImportHttpServices(
		group = "objects",
		clientType = ClientType.REST_CLIENT,
		types = { ObjectApi.class })
public class ObjectApiConfig {

	private String apiHeader;
	private String defaultVersion;
	private String baseUrl;

	@Value("${spring.mvc.apiversion.use.header:API-Version}")
	public void setApiHeader(String apiHeader) {
		this.apiHeader = apiHeader;
	}

	@Value("${spring.mvc.apiversion.default:1.4}")
	public void setDefaultVersion(String defaultVersion) {
		this.defaultVersion = defaultVersion;
	}

	@Value("${server.port:8084}")
	public void setPort(int port) {
		this.baseUrl = "http://localhost:" + port + "/ambient-invisible-intelligence/objects";
	}

	@Bean
	public RestClientHttpServiceGroupConfigurer objectsGroupConfigurer() {
		return groups -> {
			groups
				.filterByName("objects")
				.forEachClient(
						(_, clientBuilder) ->
						clientBuilder
							.baseUrl(this.baseUrl)
							.apiVersionInserter(ApiVersionInserter.useHeader(apiHeader))
							.defaultApiVersion(this.defaultVersion)
							.build()
						);
		};
	}
}
