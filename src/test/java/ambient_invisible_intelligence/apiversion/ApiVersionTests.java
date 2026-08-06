package ambient_invisible_intelligence.apiversion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;

import ambient_invisible_intelligence.boundaries.UserBoundary;
import ambient_invisible_intelligence.support.BaseIntegrationTest;
import ambient_invisible_intelligence.support.TestConstants;

/**
 * Sprint 4 coverage for API-version enforcement. Uses a raw HttpClient instead
 * of the declarative REST clients: version resolution happens in Spring's
 * request-mapping layer before any controller code runs, and the declarative
 * clients (rest/*ApiConfig.java) hardcode the version header at the
 * client-builder level, so they can't send a missing/wrong version per call.
 */
class ApiVersionTests extends BaseIntegrationTest {

	private static final String BASE_URL = "http://localhost:8084/ambient-invisible-intelligence";

	private HttpResponse<String> rawSearchByType(String apiVersionHeaderOrNull, UserBoundary operator)
			throws IOException, InterruptedException {
		String url = BASE_URL + "/objects/search/byType/Bin"
				+ "?userSystemID=" + operator.getUserId().getSystemID()
				+ "&userEmail=" + operator.getUserId().getEmail()
				+ "&userPassword=" + TestConstants.DEFAULT_PASSWORD
				+ "&size=20&page=0";

		HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.GET();
		if (apiVersionHeaderOrNull != null) {
			requestBuilder.header("API-Version", apiVersionHeaderOrNull);
		}

		return HttpClient.newHttpClient().send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
	}

	@Test
	void testRequestWithMissingApiVersionHeaderStillSucceedsViaServerDefault() throws Exception {
		UserBoundary operator = createOperator("operator1@example.com");

		HttpResponse<String> response = rawSearchByType(null, operator);

		assertThat(response.statusCode()).isEqualTo(200);
	}

	@Test
	void testRequestWithUnsupportedApiVersionHeaderReturns400() throws Exception {
		UserBoundary operator = createOperator("operator2@example.com");

		HttpResponse<String> response = rawSearchByType("1.2", operator);

		assertThat(response.statusCode()).isEqualTo(400);
	}

	@Test
	void testRequestWithMalformedApiVersionHeaderReturns400() throws Exception {
		UserBoundary operator = createOperator("operator3@example.com");

		HttpResponse<String> response = rawSearchByType("not-a-version", operator);

		assertThat(response.statusCode()).isEqualTo(400);
	}
}
